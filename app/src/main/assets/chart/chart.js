/* global LightweightCharts */
(function () {
  'use strict';

  var THEMES = {
    dark: {
      background: '#131722',
      text: '#D1D4DC',
      muted: '#787B87',
      grid: '#2A2E39',
      up: '#26A69A',
      down: '#EF5350',
      border: '#2A2E39',
      panel: '#1E222D'
    },
    light: {
      background: '#FFFFFF',
      text: '#131722',
      muted: '#6A6D78',
      grid: '#E0E3EB',
      up: '#26A69A',
      down: '#EF5350',
      border: '#E0E3EB',
      panel: '#F8F9FD'
    }
  };

  var container = document.getElementById('chart');
  var theme = THEMES.dark;
  var dark = true;

  function layoutOptions(t) {
    return {
      layout: {
        background: { color: t.background },
        textColor: t.text,
        attributionLogo: false,
        panes: { separatorColor: t.border, separatorHoverColor: 'rgba(41,98,255,0.4)' }
      },
      grid: { vertLines: { color: t.grid }, horzLines: { color: t.grid } },
      rightPriceScale: { borderColor: t.border },
      timeScale: { borderColor: t.border }
    };
  }

  var chart = LightweightCharts.createChart(container, Object.assign({
    width: container.clientWidth,
    height: container.clientHeight,
    crosshair: {
      mode: LightweightCharts.CrosshairMode.Normal,
      vertLine: { color: '#9598A1', width: 1, style: LightweightCharts.LineStyle.LargeDashed, labelBackgroundColor: '#363A45' },
      horzLine: { color: '#9598A1', width: 1, style: LightweightCharts.LineStyle.LargeDashed, labelBackgroundColor: '#363A45' }
    },
    handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: true },
    handleScale: { axisPressedMouseMove: true, mouseWheel: true, pinch: true },
    timeScale: { timeVisible: true, secondsVisible: false, rightOffset: 6, barSpacing: 8 }
  }, layoutOptions(theme)));

  var candles = chart.addSeries(LightweightCharts.CandlestickSeries, {
    upColor: theme.up, downColor: theme.down,
    borderUpColor: theme.up, borderDownColor: theme.down,
    wickUpColor: theme.up, wickDownColor: theme.down
  });

  var volume = chart.addSeries(LightweightCharts.HistogramSeries, {
    priceFormat: { type: 'volume' },
    priceScaleId: 'vol',
    lastValueVisible: false,
    priceLineVisible: false
  });
  chart.priceScale('vol').applyOptions({ scaleMargins: { top: 0.85, bottom: 0 } });

  // =====================================================================
  // Drawings. Trend lines and boxes are painted on an SVG overlay that is
  // re-projected from data space (time/price) to pixels on every render, so
  // they survive pan/zoom/timeframe/replay. Horizontal levels use native
  // price lines, which also gives the built-in drag-to-move behaviour.
  // =====================================================================
  var overlay = document.getElementById('overlay');
  var NS = 'http://www.w3.org/2000/svg';
  var drawings = [];
  var priceLines = {}; // id -> IPriceLine

  function lineStyleOf(d) {
    return {
      stroke: d.color,
      'stroke-width': String(d.width || 2),
      fill: 'none'
    };
  }

  function make(tag, attrs) {
    var el = document.createElementNS(NS, tag);
    for (var k in attrs) el.setAttribute(k, attrs[k]);
    return el;
  }

  function clearOverlay() {
    while (overlay.firstChild) overlay.removeChild(overlay.firstChild);
  }

  function anchorPoint(a) {
    var x = chart.timeScale().timeToCoordinate(a.time);
    var y = candles.priceToCoordinate(a.price);
    if (x === null || y === null || x < -2000 || y < -2000) return null;
    return [x, y];
  }

  function renderDrawings() {
    clearOverlay();
    drawings.forEach(function (d) {
      if (d.kind === 'Horizontal') return; // native price line
      var pts = (d.points || []).map(anchorPoint);
      if (pts.some(function (p) { return p === null; })) return;
      if (d.kind === 'TrendLine' && pts.length >= 2) {
        var attrs = lineStyleOf(d);
        if (d.dashed) attrs['stroke-dasharray'] = '7 5';
        var l = make('line', Object.assign({ x1: pts[0][0], y1: pts[0][1], x2: pts[1][0], y2: pts[1][1] }, attrs));
        overlay.appendChild(l);
        addHandles(pts, d);
      } else if (d.kind === 'Rectangle' && pts.length >= 2) {
        var x = Math.min(pts[0][0], pts[1][0]);
        var y = Math.min(pts[0][1], pts[1][1]);
        var w = Math.abs(pts[0][0] - pts[1][0]);
        var h = Math.abs(pts[0][1] - pts[1][1]);
        var r = make('rect', {
          x: x, y: y, width: w, height: h,
          stroke: d.color, 'stroke-width': String(d.width || 2),
          fill: d.filled ? hexAlpha(d.color, 0.18) : 'none'
        });
        if (d.dashed) r.setAttribute('stroke-dasharray', '7 5');
        overlay.appendChild(r);
        addHandles(pts, d);
      }
    });
  }

  function hexAlpha(hex, alpha) {
    var h = hex.replace('#', '');
    if (h.length !== 6) return 'rgba(41,98,255,' + alpha + ')';
    var n = parseInt(h, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + alpha + ')';
  }

  function addHandles(pts, d) {
    if (!d.selected) return;
    pts.forEach(function (p) {
      overlay.appendChild(make('circle', {
        cx: p[0], cy: p[1], r: 4, fill: '#FFFFFF', stroke: d.color, 'stroke-width': 2
      }));
    });
  }

  function syncPriceLines() {
    drawings.forEach(function (d) {
      if (d.kind !== 'Horizontal' || !d.points.length) return;
      var existing = priceLines[d.id];
      if (existing) {
        existing.applyOptions({
          price: d.points[0].price,
          color: d.color,
          lineWidth: d.width || 2,
          lineStyle: d.dashed ? LightweightCharts.LineStyle.Dashed : LightweightCharts.LineStyle.Solid,
          title: ''
        });
        return;
      }
      priceLines[d.id] = candles.createPriceLine({
        price: d.points[0].price,
        color: d.color,
        lineWidth: d.width || 2,
        lineStyle: d.dashed ? LightweightCharts.LineStyle.Dashed : LightweightCharts.LineStyle.Solid,
        axisLabelVisible: true,
        title: ''
      });
    });
    // drop stale ones
    Object.keys(priceLines).forEach(function (id) {
      var still = drawings.some(function (d) { return d.id === id && d.kind === 'Horizontal'; });
      if (!still) { candles.removePriceLine(priceLines[id]); delete priceLines[id]; }
    });
  }

  function applyDrawings(list) {
    drawings = list || [];
    syncPriceLines();
    renderDrawings();
  }

  // --- indicator state -------------------------------------------------
  var overlaySeries = [];
  var paneEntries = [];

  function clearIndicators() {
    overlaySeries.forEach(function (s) { chart.removeSeries(s); });
    overlaySeries = [];
    for (var i = chart.panes().length - 1; i >= 1; i--) chart.removePane(i);
    paneEntries = [];
  }

  function lineOptions(entry) {
    return {
      color: entry.color,
      lineWidth: entry.width || 2,
      priceLineVisible: false,
      lastValueVisible: false,
      crosshairMarkerVisible: false
    };
  }

  function applyIndicators(payload) {
    clearIndicators();
    if (!payload) return;
    (payload.overlays || []).forEach(function (entry) {
      var s = chart.addSeries(LightweightCharts.LineSeries, lineOptions(entry));
      s.setData(entry.data || []);
      overlaySeries.push(s);
    });
    (payload.panes || []).forEach(function (pane) {
      var paneIndex = chart.addPane().paneIndex();
      (pane.series || []).forEach(function (entry) {
        var opts = lineOptions(entry);
        if (pane.fixedRange) {
          opts.autoscaleInfoProvider = function () {
            return { priceRange: { minValue: pane.fixedRange.min, maxValue: pane.fixedRange.max } };
          };
        }
        var s;
        if (entry.kind === 'histogram') {
          s = chart.addSeries(LightweightCharts.HistogramSeries, {
            color: entry.color, priceLineVisible: false, lastValueVisible: false
          }, paneIndex);
        } else {
          s = chart.addSeries(LightweightCharts.LineSeries, opts, paneIndex);
        }
        s.setData(entry.data || []);
        paneEntries.push(s);
      });
    });
  }

  function refreshPaneStretch() {
    var panes = chart.panes();
    if (panes.length > 1) {
      panes[0].setStretchFactor(3);
      for (var i = 1; i < panes.length; i++) panes[i].setStretchFactor(1);
    }
  }

  // --- resize / events -------------------------------------------------
  function resize() {
    chart.applyOptions({ width: container.clientWidth, height: container.clientHeight });
    renderDrawings();
  }
  window.addEventListener('resize', resize);

  // Re-project whenever the viewport changes (pan, zoom, replay, new bars).
  chart.subscribeCrosshairMove(function (param) {
    if (!window.AndroidBridge) return;
    if (!param || !param.time || !param.seriesData) { window.AndroidBridge.onCrosshairGone(); return; }
    var bar = param.seriesData.get(candles);
    if (!bar) { window.AndroidBridge.onCrosshairGone(); return; }
    window.AndroidBridge.onCrosshairMove(param.time, bar.close);
  });

  chart.timeScale().subscribeVisibleLogicalRangeChange(function () { renderDrawings(); });

  // Lightweight-charts consumes touch gestures and suppresses synthetic `click` on mobile
  // WebViews. Detect a genuine tap from pointer/touch positions: short press, no drag.
  var press = null;
  var lastTapAt = 0;
  function handleTap(clientX, clientY) {
    if (!window.AndroidBridge || !window.AndroidBridge.onTap) return;
    var now = Date.now();
    if (now - lastTapAt < 350) return; // ignore synthesized duplicate events
    lastTapAt = now;
    var rect = container.getBoundingClientRect();
    var x = clientX - rect.left;
    var y = clientY - rect.top;
    var time = chart.timeScale().coordinateToTime(x);
    var price = candles.coordinateToPrice(y);
    if (time === null || time === undefined || price === null || price === undefined) return;
    window.AndroidBridge.onTap(Number(time), price);
  }
  function tapFromEvent(ev) {
    if (!press) return;
    var dx = ev.clientX - press.x;
    var dy = ev.clientY - press.y;
    var moved = Math.sqrt(dx * dx + dy * dy);
    var held = Date.now() - press.t;
    press = null;
    if (moved <= 12 && held <= 700) handleTap(ev.clientX, ev.clientY);
  }
  container.addEventListener('pointerdown', function (ev) {
    press = { x: ev.clientX, y: ev.clientY, t: Date.now() };
  });
  container.addEventListener('pointerup', tapFromEvent);
  container.addEventListener('pointercancel', function () { press = null; });
  // Fallback for older WebViews that do not dispatch PointerEvents.
  container.addEventListener('touchend', function (ev) {
    if (!ev.changedTouches || !ev.changedTouches.length) return;
    var p = ev.changedTouches[0];
    var dx = press ? p.clientX - press.x : 99;
    var dy = press ? p.clientY - press.y : 99;
    if (Math.sqrt(dx * dx + dy * dy) <= 12) handleTap(p.clientX, p.clientY);
    press = null;
  });

  function notifyReady() {
    if (window.AndroidBridge && window.AndroidBridge.onChartReady) window.AndroidBridge.onChartReady();
  }

  // --- public API ------------------------------------------------------
  var lastIndicators = null;

  window.chartApi = {
    setData: function (list) {
      candles.setData((list || []).map(function (c) {
        return { time: c.time, open: c.open, high: c.high, low: c.low, close: c.close };
      }));
      volume.setData((list || []).map(function (c) {
        return {
          time: c.time, value: c.volume || 0,
          color: c.close >= c.open ? 'rgba(38,166,154,0.4)' : 'rgba(239,83,80,0.4)'
        };
      }));
      renderDrawings();
    },
    update: function (c) {
      if (!c) return;
      candles.update({ time: c.time, open: c.open, high: c.high, low: c.low, close: c.close });
      volume.update({
        time: c.time, value: c.volume || 0,
        color: c.close >= c.open ? 'rgba(38,166,154,0.4)' : 'rgba(239,83,80,0.4)'
      });
    },
    setIndicators: function (payload) {
      lastIndicators = payload;
      applyIndicators(payload);
      refreshPaneStretch();
      renderDrawings();
    },
    setDrawings: function (list) { applyDrawings(list); },
    setLogScale: function (on) { candles.priceScale().applyOptions({ mode: on ? 1 : 0 }); renderDrawings(); },
    setTheme: function (isDark) {
      dark = !!isDark;
      theme = dark ? THEMES.dark : THEMES.light;
      chart.applyOptions(layoutOptions(theme));
      candles.applyOptions({
        upColor: theme.up, downColor: theme.down,
        borderUpColor: theme.up, borderDownColor: theme.down,
        wickUpColor: theme.up, wickDownColor: theme.down
      });
      if (lastIndicators) applyIndicators(lastIndicators);
      renderDrawings();
    },
    fitContent: function () { chart.timeScale().fitContent(); renderDrawings(); },
    /** Visible price span, used by Kotlin for zoom-aware hit tolerance. */
    visibleSpan: function () {
      try {
        var range = chart.timeScale().getVisibleRange();
        if (!range) return 0;
        var data = candles.data();
        var lo = Infinity, hi = -Infinity;
        for (var i = 0; i < data.length; i++) {
          var c = data[i];
          if (c.time < range.from || c.time > range.to) continue;
          if (c.low < lo) lo = c.low;
          if (c.high > hi) hi = c.high;
        }
        return (hi > lo && isFinite(hi)) ? (hi - lo) : 0;
      } catch (e) { return 0; }
    }
  };

  resize();
  notifyReady();
})();
