/* global LightweightCharts */
(function () {
  'use strict';

  var THEMES = {
    dark: {
      background: '#131722',
      text: '#D1D4DC',
      grid: '#2A2E39',
      up: '#26A69A',
      down: '#EF5350',
      border: '#2A2E39'
    },
    light: {
      background: '#FFFFFF',
      text: '#131722',
      grid: '#E0E3EB',
      up: '#26A69A',
      down: '#EF5350',
      border: '#E0E3EB'
    }
  };

  var container = document.getElementById('chart');
  var theme = THEMES.dark;

  var chart = LightweightCharts.createChart(container, {
    layout: {
      background: { color: theme.background },
      textColor: theme.text,
      attributionLogo: false
    },
    grid: {
      vertLines: { color: theme.grid },
      horzLines: { color: theme.grid }
    },
    rightPriceScale: { borderColor: theme.border },
    timeScale: {
      borderColor: theme.border,
      timeVisible: true,
      secondsVisible: false,
      rightOffset: 4,
      barSpacing: 8
    },
    crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
    handleScroll: { mouseWheel: true, pressedMouseMove: true, horzTouchDrag: true, vertTouchDrag: false },
    handleScale: {
      axisPressedMouseMove: true,
      mouseWheel: true,
      pinch: true
    },
    localization: { priceFormatter: function (p) { return p.toFixed(2); } }
  });

  var candles = chart.addSeries(LightweightCharts.CandlestickSeries, {
    upColor: theme.up,
    downColor: theme.down,
    borderUpColor: theme.up,
    borderDownColor: theme.down,
    wickUpColor: theme.up,
    wickDownColor: theme.down
  });

  var volume = chart.addSeries(LightweightCharts.HistogramSeries, {
    priceFormat: { type: 'volume' },
    priceScaleId: 'volume',
    color: 'rgba(38, 166, 154, 0.35)'
  });
  chart.priceScale('volume').applyOptions({ scaleMargins: { top: 0.82, bottom: 0 } });

  function resize() {
    chart.applyOptions({ width: container.clientWidth, height: container.clientHeight });
  }
  window.addEventListener('resize', resize);
  resize();

  function notifyReady() {
    if (window.AndroidBridge && window.AndroidBridge.onChartReady) {
      window.AndroidBridge.onChartReady();
    }
  }

  chart.subscribeCrosshairMove(function (param) {
    if (!param || !param.time || !param.seriesData) return;
    var bar = param.seriesData.get(candles);
    if (!bar) return;
    var price = typeof bar.close === 'number' ? bar.close : 0;
    var t = typeof param.time === 'number' ? param.time : 0;
    if (window.AndroidBridge && window.AndroidBridge.onCrosshairMove) {
      window.AndroidBridge.onCrosshairMove(t, price);
    }
  });

  chart.timeScale().subscribeVisibleTimeRangeChange(function (range) {
    if (!range || !window.AndroidBridge || !window.AndroidBridge.onVisibleRangeChange) return;
    window.AndroidBridge.onVisibleRangeChange(range.from, range.to);
  });

  window.chartApi = {
    setData: function (list) {
      var bars = (list || []).map(function (c) {
        return { time: c.time, open: c.open, high: c.high, low: c.low, close: c.close };
      });
      candles.setData(bars);
      volume.setData((list || []).map(function (c) {
        return {
          time: c.time,
          value: c.volume || 0,
          color: c.close >= c.open ? 'rgba(38,166,154,0.35)' : 'rgba(239,83,80,0.35)'
        };
      }));
      chart.timeScale().fitContent();
    },
    update: function (c) {
      if (!c) return;
      candles.update({ time: c.time, open: c.open, high: c.high, low: c.low, close: c.close });
      volume.update({
        time: c.time,
        value: c.volume || 0,
        color: c.close >= c.open ? 'rgba(38,166,154,0.35)' : 'rgba(239,83,80,0.35)'
      });
    },
    setTheme: function (dark) {
      theme = dark ? THEMES.dark : THEMES.light;
      chart.applyOptions({
        layout: { background: { color: theme.background }, textColor: theme.text },
        grid: { vertLines: { color: theme.grid }, horzLines: { color: theme.grid } },
        rightPriceScale: { borderColor: theme.border },
        timeScale: { borderColor: theme.border }
      });
      candles.applyOptions({
        upColor: theme.up,
        downColor: theme.down,
        borderUpColor: theme.up,
        borderDownColor: theme.down,
        wickUpColor: theme.up,
        wickDownColor: theme.down
      });
    },
    fitContent: function () { chart.timeScale().fitContent(); }
  };

  // P1 acceptance: the chart must show candles immediately on launch. Load the bundled
  // sample series (P2 replaces this with live Binance data from Kotlin).
  fetch('sample-candles.json')
    .then(function (r) { return r.json(); })
    .then(function (list) {
      window.chartApi.setData(list);
      console.log('[TradeView] sample candles loaded:', list.length);
    })
    .catch(function (e) {
      console.log('[TradeView] sample candle load failed:', e && e.message);
    });

  notifyReady();
})();
