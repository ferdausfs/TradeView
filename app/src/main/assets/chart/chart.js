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

  // --- indicator state -------------------------------------------------
  // overlays: plain LineSeries on the main pane (id: ov-<n>)
  // panes:    one price pane per entry, each holding 1..n series
  var overlaySeries = [];
  var paneEntries = []; // { paneIndex, series: [ISeriesApi], fixedRange }

  function clearIndicators() {
    overlaySeries.forEach(function (s) { chart.removeSeries(s); });
    overlaySeries = [];
    // remove panes from last to first (index 0 is the main pane)
    for (var i = chart.panes().length - 1; i >= 1; i--) {
      chart.removePane(i);
    }
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
          // lightweight-charts has no min/max clamp option, but autoscaleInfoProvider can
          // pin the visible price range (RSI/MFI-style 0..100 panes need this).
          opts.autoscaleInfoProvider = function () {
            return { priceRange: { minValue: pane.fixedRange.min, maxValue: pane.fixedRange.max } };
          };
        }
        var s;
        if (entry.kind === 'histogram') {
          s = chart.addSeries(LightweightCharts.HistogramSeries, {
            color: entry.color,
            priceLineVisible: false,
            lastValueVisible: false
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

  // --- resize / bridge -------------------------------------------------
  function resize() {
    chart.applyOptions({ width: container.clientWidth, height: container.clientHeight });
  }
  window.addEventListener('resize', resize);
  resize();

  function notifyReady() {
    if (window.AndroidBridge && window.AndroidBridge.onChartReady) window.AndroidBridge.onChartReady();
  }

  chart.subscribeCrosshairMove(function (param) {
    if (!window.AndroidBridge || !window.AndroidBridge.onCrosshairMove) return;
    if (!param || !param.time || !param.seriesData) {
      window.AndroidBridge.onCrosshairGone();
      return;
    }
    var bar = param.seriesData.get(candles);
    if (!bar) { window.AndroidBridge.onCrosshairGone(); return; }
    window.AndroidBridge.onCrosshairMove(param.time, bar.close);
  });

  // --- public API ------------------------------------------------------
  var lastData = null;

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
      lastData = payload;
      applyIndicators(payload);
      refreshPaneStretch();
    },
    setLogScale: function (on) {
      candles.priceScale().applyOptions({ mode: on ? 1 : 0 });
    },
    setTheme: function (isDark) {
      dark = !!isDark;
      theme = dark ? THEMES.dark : THEMES.light;
      chart.applyOptions(layoutOptions(theme));
      candles.applyOptions({
        upColor: theme.up, downColor: theme.down,
        borderUpColor: theme.up, borderDownColor: theme.down,
        wickUpColor: theme.up, wickDownColor: theme.down
      });
      if (lastData) applyIndicators(lastData);
    },
    fitContent: function () { chart.timeScale().fitContent(); }
  };

  notifyReady();
})();
