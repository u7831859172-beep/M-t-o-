addEventListener("savePos", (resolve, reject, args) => {
  try { CapacitorKV.set("pos", JSON.stringify(args || {})); resolve(); }
  catch (e) { reject(e); }
});

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
      "&hourly=weather_code,precipitation,precipitation_probability&forecast_hours=4&timezone=auto";
    const j = await (await fetch(u)).json();
    const arr = Array.isArray(j) ? j : [j];
    const h = arr[0].hourly;
    const PR = { rain: 1, snow: 2, storm: 3 };
    let kind = null, at = "", where = "";

    // pluie et neige : sur votre position
    for (let i = 0; i < h.time.length; i++) {
      const c = h.weather_code[i];
      let k = null;
      if ((c >= 71 && c <= 77) || c === 85 || c === 86) k = "snow";
      else if (((c >= 51 && c <= 67) || (c >= 80 && c <= 82)) &&
               (h.precipitation[i] >= 0.3 || h.precipitation_probability[i] >= 60)) k = "rain";
      if (k && (!kind || PR[k] > PR[kind])) { kind = k; at = h.time[i].slice(11, 13) + "h"; }
    }
    // orage : n'importe où dans le rayon
    for (let i = 0; i < h.time.length && kind !== "storm"; i++) {
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
