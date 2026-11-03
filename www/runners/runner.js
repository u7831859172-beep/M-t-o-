addEventListener("savePos", (resolve, reject, args) => {
  try { CapacitorKV.set("pos", JSON.stringify(args || {})); resolve(); }
  catch (e) { reject(e); }
});

// ---------- textes de la météo du jour ----------
function lab(c, day) {
  if (c === 0) return day ? "Ciel dégagé" : "Nuit dégagée";
  if (c === 1) return "Plutôt dégagé";
  if (c === 2) return "Partiellement nuageux";
  if (c === 3) return "Couvert";
  if (c === 45 || c === 48) return "Brouillard";
  if (c >= 51 && c <= 57) return "Bruine";
  if (c >= 61 && c <= 67) return "Pluie";
  if (c >= 71 && c <= 77) return "Neige";
  if (c >= 80 && c <= 82) return "Averses";
  if (c === 85 || c === 86) return "Averses de neige";
  if (c >= 95) return "Orage";
  return "Variable";
}
function emo(c, day) {
  if (c === 0 || c === 1) return day ? "☀️" : "🌙";
  if (c === 2) return day ? "⛅" : "☁️";
  if (c === 3) return "☁️";
  if (c === 45 || c === 48) return "🌫️";
  if ((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) return "🌧️";
  if ((c >= 71 && c <= 77) || c === 85 || c === 86) return "🌨️";
  if (c >= 95) return "⛈️";
  return "🌡️";
}
function slot(v, def, lo, hi) {
  if (v === undefined) v = def;
  if (v === "" || v === null) return null;
  const m = /^(\d{1,2}):(\d{2})$/.exec(v);
  const x = m ? (+m[1]) * 60 + (+m[2]) : (+def.slice(0, 2)) * 60 + (+def.slice(3, 5));
  return Math.max(lo, Math.min(hi, x));
}

// Une notification le matin (6h-12h) et une l'après-midi (12h-20h), à l'heure choisie
function briefing(a, p) {
  const cur = a.current, t = cur.time;
  const date = t.slice(0, 10), mins = (+t.slice(11, 13)) * 60 + (+t.slice(14, 16));
  const sm = slot(p.morning, "07:30", 360, 719), sa = slot(p.afternoon, "13:00", 720, 1199);
  const dl = CapacitorKV.get("daily");
  const sent = (dl && dl.value) ? JSON.parse(dl.value) : {};
  let which = null;
  if (sa !== null && mins >= sa && mins < 1200 && sent.a !== date) which = "a";
  else if (sm !== null && mins >= sm && mins < 720 && sent.m !== date) which = "m";
  if (!which) return;

  const h = a.hourly, d = a.daily, day = cur.is_day === 1, c = cur.weather_code;
  const T = Math.round(cur.temperature_2m), A = Math.round(cur.apparent_temperature), V = Math.round(cur.wind_speed_10m);
  let rain = null, snow = null, storm = null;
  for (let i = 0; i < h.time.length; i++) {
    const k = h.weather_code[i], hh = h.time[i].slice(11, 13) + "h";
    if (storm === null && k >= 95) storm = hh;
    if (snow === null && ((k >= 71 && k <= 77) || k === 85 || k === 86)) snow = hh;
    if (rain === null && ((k >= 51 && k <= 67) || (k >= 80 && k <= 82)) &&
        (h.precipitation[i] >= 0.2 || h.precipitation_probability[i] >= 50)) rain = hh;
  }
  const rtxt = storm ? "⚡ Orage possible vers " + storm : snow ? "❄️ Neige vers " + snow :
               rain ? "☔ Pluie vers " + rain : "Pas de pluie prévue";
  const place = p.name || "votre position";
  let title, body;
  if (which === "m") {
    title = "Météo du matin · " + place;
    body = emo(c, day) + " " + lab(c, day) + ", " + T + "° (ressenti " + A + "°). Max " +
      Math.round(d.temperature_2m_max[0]) + "° · min " + Math.round(d.temperature_2m_min[0]) + "°. " + rtxt + ". Vent " + V + " km/h.";
  } else {
    let ev = null;
    for (let i = 0; i < h.time.length; i++) { if (h.time[i].slice(11, 13) === "20") ev = Math.round(h.temperature_2m[i]); }
    title = "Météo de l’après-midi · " + place;
    body = emo(c, day) + " " + lab(c, day) + ", " + T + "°. " + (ev !== null ? "Ce soir " + ev + "° · " : "") + rtxt + ". Demain : " +
      Math.round(d.temperature_2m_min[1]) + "° à " + Math.round(d.temperature_2m_max[1]) + "° " + emo(d.weather_code[1], true) + ".";
  }
  sent[which] = date;
  CapacitorKV.set("daily", JSON.stringify(sent));
  CapacitorNotifications.schedule([{ id: which === "m" ? 2001 : 2002, title: title, body: body, scheduleAt: new Date(Date.now() + 1000) }]);
}

addEventListener("check", async (resolve, reject) => {
  try {
    const r = CapacitorKV.get("pos");
    if (!r || !r.value) return resolve();
    const p = JSON.parse(r.value);
    if (!p.lat) return resolve();

    // position + 8 points sur un cercle : l'alerte couvre tout le rayon choisi
    const R = Math.max(10, Math.min(100, +p.radius || 30));
    const names = ["N", "NE", "E", "SE", "S", "SO", "O", "NO"];
    const pts = [{ lat: p.lat, lon: p.lon, n: "" }];
    for (let i = 0; i < 8; i++) {
      const b = i * Math.PI / 4;
      pts.push({
        lat: p.lat + R / 111.2 * Math.cos(b),
        lon: p.lon + R / (111.2 * Math.cos(p.lat * Math.PI / 180)) * Math.sin(b),
        n: names[i]
      });
    }
    const u = "https://api.open-meteo.com/v1/forecast?latitude=" + pts.map(x => x.lat.toFixed(3)).join(",") +
      "&longitude=" + pts.map(x => x.lon.toFixed(3)).join(",") +
      "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
      "&hourly=temperature_2m,weather_code,precipitation,precipitation_probability" +
      "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_hours=12&forecast_days=2&timezone=auto";
    const j = await (await fetch(u)).json();
    const arr = Array.isArray(j) ? j : [j];
    const h = arr[0].hourly;
    const NH = Math.min(4, h.time.length);

    // météo du matin et de l'après-midi (n'empêche pas les alertes)
    try { briefing(arr[0], p); } catch (e) {}

    const PR = { rain: 1, snow: 2, storm: 3 };
    let kind = null, at = "", where = "";

    // pluie et neige : sur votre position
    for (let i = 0; i < NH; i++) {
      const c = h.weather_code[i];
      let k = null;
      if ((c >= 71 && c <= 77) || c === 85 || c === 86) k = "snow";
      else if (((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) &&
               (h.precipitation[i] >= 0.3 || h.precipitation_probability[i] >= 60)) k = "rain";
      if (k && (!kind || PR[k] > PR[kind])) { kind = k; at = h.time[i].slice(11, 13) + "h"; }
    }
    // orage : n'importe où dans le rayon
    for (let i = 0; i < NH && kind !== "storm"; i++) {
      for (let q = 0; q < arr.length; q++) {
        if (arr[q].hourly.weather_code[i] >= 95) {
          kind = "storm";
          at = h.time[i].slice(11, 13) + "h";
          where = q === 0 ? "sur votre position" : "à ~" + R + " km côté " + pts[q].n;
          break;
        }
      }
    }
    if (!kind) return resolve();

    // pas deux fois la même alerte en moins de 3 h
    const l = CapacitorKV.get("last");
    if (l && l.value) {
      const o = JSON.parse(l.value);
      if (o.k === kind && Date.now() - o.t < 3 * 3600 * 1000) return resolve();
    }
    CapacitorKV.set("last", JSON.stringify({ k: kind, t: Date.now() }));

    const place = p.name || "votre position";
    const M = {
      storm: ["⛈️ Orage prévu", "Orage attendu vers " + at + " " + where + ". Mettez-vous à l’abri."],
      snow: ["❄️ Neige prévue", "De la neige arrive vers " + at + " près de " + place + "."],
      rain: ["🌧️ Pluie prévue", "De la pluie arrive vers " + at + " près de " + place + "."]
    }[kind];
    const ID = { storm: 1001, snow: 1002, rain: 1003 }[kind];
    CapacitorNotifications.schedule([{ id: ID, title: M[0], body: M[1], scheduleAt: new Date(Date.now() + 1000) }]);
    resolve();
  } catch (e) { reject(e); }
});
