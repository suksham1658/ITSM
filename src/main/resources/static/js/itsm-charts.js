(function () {
  function parseJson(id) {
    var el = document.getElementById(id);
    if (!el) {
      return null;
    }
    var raw = el.textContent || el.innerText || "";
    raw = raw.replace(/&quot;/g, "\"").replace(/&#34;/g, "\"").replace(/&amp;/g, "&");
    if (!raw.trim()) {
      return null;
    }
    try {
      return JSON.parse(raw);
    } catch (e) {
      return null;
    }
  }

  function showEmpty(canvas, message) {
    var box = canvas.parentNode;
    canvas.style.display = "none";
    if (box.querySelector(".chart-empty")) {
      return;
    }
    var div = document.createElement("div");
    div.className = "chart-empty empty-state";
    div.innerHTML = "<i class=\"fa-solid fa-chart-simple\"></i><h3>No data</h3><p>" + message + "</p>";
    box.appendChild(div);
  }

  function maxOf(series) {
    var m = 0;
    for (var s = 0; s < series.length; s++) {
      var data = series[s].data || [];
      for (var i = 0; i < data.length; i++) {
        var n = Number(data[i]) || 0;
        if (n > m) {
          m = n;
        }
      }
    }
    return m;
  }

  function colorsFor(series, i, fallback) {
    if (!series.color) {
      return fallback[i % fallback.length];
    }
    if (series.color.indexOf(",") >= 0) {
      var parts = series.color.split(",");
      return (parts[i] || parts[0]).trim();
    }
    return series.color;
  }

  /** Shortens a label with "…" so it fits under its own bar instead of running into the next one. */
  function fitText(ctx, text, maxW) {
    if (ctx.measureText(text).width <= maxW) {
      return text;
    }
    var t = text;
    while (t.length > 1 && ctx.measureText(t + "…").width > maxW) {
      t = t.slice(0, -1);
    }
    return t + "…";
  }

  /** True for very light #rrggbb colours that need an outline on a white background. */
  function isLight(color) {
    var m = /^#([0-9a-f]{6})$/i.exec(String(color).trim());
    if (!m) {
      return false;
    }
    var n = parseInt(m[1], 16);
    var lum = 0.299 * ((n >> 16) & 255) + 0.587 * ((n >> 8) & 255) + 0.114 * (n & 255);
    return lum > 225;
  }

  function doughnut(ctx, payload, w, h) {
    var data = (payload.series[0] && payload.series[0].data) || [];
    var total = 0;
    for (var i = 0; i < data.length; i++) {
      total += Number(data[i]) || 0;
    }
    if (total <= 0) {
      showEmpty(ctx.canvas, "No tickets in this breakdown.");
      return;
    }
    var cx = w / 2;
    var cy = h / 2;
    var r = Math.min(cx, cy) - 16;
    var start = -Math.PI / 2;
    ctx.clearRect(0, 0, w, h);
    for (var j = 0; j < data.length; j++) {
      var slice = (Number(data[j]) || 0) / total;
      var end = start + slice * Math.PI * 2;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.arc(cx, cy, r, start, end);
      ctx.closePath();
      ctx.fillStyle = colorsFor(payload.series[0], j, ["#2E6FB0", "#0E7C86", "#B8730F", "#C0392B"]);
      ctx.fill();
      start = end;
    }
    ctx.beginPath();
    ctx.fillStyle = "#fff";
    ctx.arc(cx, cy, r * 0.55, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#0B1F33";
    ctx.font = "600 18px 'IBM Plex Sans', sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(String(total), cx, cy);
    legend(ctx.canvas, payload);
  }

  function bar(ctx, payload, w, h) {
    var labels = payload.labels || [];
    var series = payload.series || [];
    var max = maxOf(series) || 1;
    var padL = 36;
    var padB = 36;
    var padT = 12;
    var padR = 12;
    var plotW = w - padL - padR;
    var plotH = h - padT - padB;
    var groups = labels.length || 1;
    var barCount = Math.max(series.length, 1);
    var groupW = plotW / groups;
    var barW = Math.max(6, (groupW - 12) / barCount);
    ctx.clearRect(0, 0, w, h);
    ctx.strokeStyle = "#E2E6EC";
    ctx.beginPath();
    ctx.moveTo(padL, padT);
    ctx.lineTo(padL, padT + plotH);
    ctx.lineTo(padL + plotW, padT + plotH);
    ctx.stroke();
    for (var g = 0; g < labels.length; g++) {
      for (var s = 0; s < series.length; s++) {
        var val = Number((series[s].data || [])[g]) || 0;
        var bh = (val / max) * (plotH - 4);
        var x = padL + g * groupW + 6 + s * barW;
        var y = padT + plotH - bh;
        var fill = colorsFor(series[s], g, [series[s].color || "#0B1F33"]);
        ctx.fillStyle = fill;
        roundRect(ctx, x, y, Math.max(4, barW - 4), bh, 4);
        ctx.fill();
        if (isLight(fill)) {
          // e.g. off-white: outline it so the bar is visible on the white card
          ctx.strokeStyle = "#A89F8C";
          ctx.lineWidth = 1;
          ctx.stroke();
        }
      }
      ctx.fillStyle = "#64748B";
      ctx.font = "11px 'IBM Plex Sans', sans-serif";
      ctx.textAlign = "center";
      ctx.fillText(fitText(ctx, String(labels[g]), groupW - 6), padL + g * groupW + groupW / 2, h - 12);
    }
    legend(ctx.canvas, payload);
  }

  function line(ctx, payload, w, h) {
    var labels = payload.labels || [];
    var series = payload.series || [];
    var max = maxOf(series) || 1;
    var padL = 36;
    var padB = 36;
    var padT = 12;
    var padR = 12;
    var plotW = w - padL - padR;
    var plotH = h - padT - padB;
    ctx.clearRect(0, 0, w, h);
    ctx.strokeStyle = "#E2E6EC";
    ctx.beginPath();
    ctx.moveTo(padL, padT);
    ctx.lineTo(padL, padT + plotH);
    ctx.lineTo(padL + plotW, padT + plotH);
    ctx.stroke();
    var n = Math.max(labels.length - 1, 1);
    for (var s = 0; s < series.length; s++) {
      var data = series[s].data || [];
      ctx.beginPath();
      ctx.strokeStyle = series[s].color || "#0E7C86";
      ctx.lineWidth = 2;
      for (var i = 0; i < data.length; i++) {
        var x = padL + (i / n) * plotW;
        var y = padT + plotH - ((Number(data[i]) || 0) / max) * plotH;
        if (i === 0) {
          ctx.moveTo(x, y);
        } else {
          ctx.lineTo(x, y);
        }
      }
      ctx.stroke();
    }
    ctx.fillStyle = "#64748B";
    ctx.font = "11px 'IBM Plex Sans', sans-serif";
    ctx.textAlign = "center";
    for (var l = 0; l < labels.length; l++) {
      ctx.fillText(String(labels[l]).slice(0, 10), padL + (l / n) * plotW, h - 12);
    }
    legend(ctx.canvas, payload);
  }

  function roundRect(ctx, x, y, w, h, r) {
    var rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rr, y);
    ctx.lineTo(x + w - rr, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + rr);
    ctx.lineTo(x + w, y + h);
    ctx.lineTo(x, y + h);
    ctx.lineTo(x, y + rr);
    ctx.quadraticCurveTo(x, y, x + rr, y);
    ctx.closePath();
  }

  function legend(canvas, payload) {
    if (canvas.getAttribute("data-legend") === "off") {
      return; // the page shows its own list next to the chart
    }
    var box = canvas.parentNode;
    if (box.querySelector(".chart-legend-row")) {
      return;
    }
    var row = document.createElement("div");
    row.className = "chart-legend-row";
    var labels = payload.labels || [];
    var series = payload.series || [];
    if (payload.type === "doughnut" && series[0]) {
      for (var i = 0; i < labels.length; i++) {
        var chip = document.createElement("span");
        chip.className = "legend-chip";
        chip.innerHTML = "<span class=\"legend-dot\" style=\"background:" + colorsFor(series[0], i, ["#2E6FB0"]) + "\"></span>" +
            labels[i] + " · " + (series[0].data[i] || 0);
        row.appendChild(chip);
      }
    } else if (series.length > 1) {
      for (var s = 0; s < series.length; s++) {
        var c = document.createElement("span");
        c.className = "legend-chip";
        c.innerHTML = "<span class=\"legend-dot\" style=\"background:" + (series[s].color || "#0B1F33") + "\"></span>" + series[s].label;
        row.appendChild(c);
      }
    }
    if (row.childNodes.length) {
      box.appendChild(row);
    }
  }

  function paint(canvas, payload) {
    if (!canvas || !payload || payload.empty) {
      if (canvas) {
        showEmpty(canvas, "No rows in this window.");
      }
      return;
    }
    var ratio = window.devicePixelRatio || 1;
    var cssW = canvas.clientWidth || canvas.width;
    var cssH = canvas.getAttribute("height") ? Number(canvas.getAttribute("height")) : 230;
    canvas.width = Math.floor(cssW * ratio);
    canvas.height = Math.floor(cssH * ratio);
    var ctx = canvas.getContext("2d");
    ctx.scale(ratio, ratio);
    canvas.style.width = cssW + "px";
    canvas.style.height = cssH + "px";
    if (payload.type === "doughnut") {
      doughnut(ctx, payload, cssW, cssH);
    } else if (payload.type === "line") {
      line(ctx, payload, cssW, cssH);
    } else {
      bar(ctx, payload, cssW, cssH);
    }
  }

  function boot() {
    var nodes = document.querySelectorAll("canvas[data-chart]");
    for (var i = 0; i < nodes.length; i++) {
      paint(nodes[i], parseJson(nodes[i].getAttribute("data-chart")));
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
  window.addEventListener("resize", boot);
})();
