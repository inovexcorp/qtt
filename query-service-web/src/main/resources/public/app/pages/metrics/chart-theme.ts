import { BarController, BarElement, CategoryScale, Chart, ChartType, Filler, LinearScale, LineController, LineElement,
  Plugin, PointElement, Tooltip, TooltipModel } from 'chart.js';

/**
 * Chart.js look and behaviour shared by the metrics charts: colours from the app's Material theme, hairline recessive
 * axes, an HTML tooltip (value first, line key per series), a crosshair for line charts, and motion that respects
 * prefers-reduced-motion.
 */

/** Only what the metrics charts use, so the rest of Chart.js stays out of the bundle. */
export const CHART_REGISTERABLES = [BarController, LineController, BarElement, LineElement, PointElement, CategoryScale,
  LinearScale, Tooltip, Filler];

/** Light theme values of the --mat-sys-* tokens (canvas can't read CSS variables). */
export const INK = {
  primary: '#7d00fa',
  primarySoft: '#d5baff',      // primary-fixed-dim
  error: '#ba1a1a',
  surface: '#ffffff',          // the chart cards' surface (surface-container-lowest)
  text: '#1d1b1e',             // on-surface
  textMuted: '#49454e',        // on-surface-variant
  grid: '#ece7ee',             // one step off the surface
  other: '#9a949e',            // de-emphasis grey for folded series
};

/**
 * Categorical order for routes, validated with the dataviz palette checker on the white card surface (adjacent CVD
 * ΔE >= 11.8, normal-vision >= 20.5). Slots 2-5 are under 3:1 against white, so the route table and tooltips carry
 * every value as text. Never cycled: a route past the last slot is folded into "other routes" (grey).
 */
export const ROUTE_COLORS = ['#7d00fa', '#f47230', '#00b894', '#f2a600', '#ee7fac', '#008a17', '#257de0'];

export const FONT_FAMILY = 'Roboto, sans-serif';

export const prefersReducedMotion = (): boolean =>
  typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Data updates morph from the old values (a live refresh reads as change, not a redraw). */
export const motion = () => prefersReducedMotion()
  ? { animation: false as const }
  : { animation: { duration: 650, easing: 'easeOutQuart' as const } };

let registered = false;

/** Global Chart.js defaults; once. */
export function applyChartDefaults(): void {
  if (registered) {
    return;
  }
  registered = true;
  Chart.register(...CHART_REGISTERABLES);
  Chart.defaults.font.family = FONT_FAMILY;
  Chart.defaults.font.size = 12;
  Chart.defaults.color = INK.textMuted;
  Chart.defaults.borderColor = INK.grid;
  Chart.defaults.maintainAspectRatio = false;
  Chart.defaults.responsive = true;
  // No Legend plugin is registered: the cards draw their own HTML legends.
}

/** Value axis: hairline grid, no axis line, a handful of clean ticks. */
export function valueScale(format: (v: number) => string, extra: Record<string, unknown> = {}) {
  return {
    beginAtZero: true,
    border: { display: false },
    grid: { color: INK.grid, lineWidth: 1, drawTicks: false },
    ticks: { padding: 8, maxTicksLimit: 6, callback: (v: number | string) => format(Number(v)) },
    ...extra,
  };
}

/** Category axis: no grid, no axis line. */
export function categoryScale(extra: Record<string, unknown> = {}) {
  return {
    border: { display: false },
    grid: { display: false },
    ticks: { padding: 6, color: INK.textMuted },
    ...extra,
  };
}

/**
 * Route names on a category axis, shortened to fit (Chart.js caps the label column's width, and clips what doesn't
 * fit). The tooltip and the table show them in full.
 */
export function routeTick(this: { chart: { width: number }; getLabelForValue(value: number): string },
                          value: number | string): string {
  const label = this.getLabelForValue(Number(value));
  const max = this.chart.width < 480 ? 12 : 22;
  return label.length > max ? `${label.slice(0, max - 1)}…` : label;
}

export const formatCount = (v: number): string =>
  v >= 10000 ? `${(v / 1000).toFixed(v >= 100000 ? 0 : 1)}K` : Math.round(v).toLocaleString();
export const formatRate = (v: number): string => v < 10 && v % 1 !== 0 ? v.toFixed(1) : formatCount(v);
export const formatMs = (v: number): string =>
  v >= 1000 ? `${(v / 1000).toFixed(v >= 10000 ? 0 : 1)} s` : `${Math.round(v).toLocaleString()} ms`;

// --- HTML tooltip ---------------------------------------------------------------------------------------------

export interface TooltipRow {
  value: string;
  label: string;
  color: string;
  /** How the key is drawn: a short line (line charts) or a dot (markers) or a square (bars). */
  key: 'line' | 'dot' | 'square';
}

/**
 * Chart.js `external` tooltip handler rendering into a positioned element in the chart's card. `rows` turns the
 * active tooltip into rows; text goes in with textContent (route names come from the API).
 */
export function htmlTooltip(rows: (tooltip: TooltipModel<ChartType>) => { title: string; rows: TooltipRow[] }) {
  return (context: { chart: Chart; tooltip: TooltipModel<ChartType> }) => {
    const { chart, tooltip } = context;
    const host = chart.canvas.parentElement!;
    let el = host.querySelector<HTMLElement>(':scope > .chart-tooltip');
    if (!el) {
      el = document.createElement('div');
      el.className = 'chart-tooltip';
      el.setAttribute('role', 'presentation');
      host.appendChild(el);
    }
    if (tooltip.opacity === 0) {
      el.classList.remove('chart-tooltip--visible');
      return;
    }
    const content = rows(tooltip);
    el.replaceChildren();
    const title = document.createElement('div');
    title.className = 'chart-tooltip__title';
    title.textContent = content.title;
    el.appendChild(title);
    for (const row of content.rows) {
      const line = document.createElement('div');
      line.className = 'chart-tooltip__row';
      const key = document.createElement('span');
      key.className = `chart-tooltip__key chart-tooltip__key--${row.key}`;
      key.style.setProperty('--key-color', row.color);
      const value = document.createElement('span');
      value.className = 'chart-tooltip__value';
      value.textContent = row.value;
      const label = document.createElement('span');
      label.className = 'chart-tooltip__label';
      label.textContent = row.label;
      line.append(key, value, label);
      el.appendChild(line);
    }
    // Beside the pointer, flipped to stay inside the card.
    const width = el.offsetWidth;
    const height = el.offsetHeight;
    const gap = 14;
    let left = tooltip.caretX + gap;
    if (left + width > host.clientWidth) {
      left = tooltip.caretX - width - gap;
    }
    const top = Math.min(Math.max(tooltip.caretY - height / 2, 0), Math.max(0, host.clientHeight - height));
    el.style.transform = `translate(${Math.max(0, left)}px, ${top}px)`;
    el.classList.add('chart-tooltip--visible');
  };
}

/** Tooltip settings that hand rendering to {@link htmlTooltip}. */
export function tooltipOptions(render: ReturnType<typeof htmlTooltip>) {
  return { enabled: false, external: render, animation: { duration: 0 } };
}

// --- Crosshair ------------------------------------------------------------------------------------------------

/** A vertical hairline at the hovered x on line charts; the tooltip lists every series there. */
export const crosshairPlugin: Plugin<'line'> = {
  id: 'qttCrosshair',
  afterDatasetsDraw(chart) {
    const active = chart.tooltip?.getActiveElements?.() ?? [];
    if (active.length === 0) {
      return;
    }
    const x = active[0].element.x;
    const { top, bottom } = chart.chartArea;
    const ctx = chart.ctx;
    ctx.save();
    ctx.beginPath();
    ctx.moveTo(Math.round(x) + 0.5, top);
    ctx.lineTo(Math.round(x) + 0.5, bottom);
    ctx.lineWidth = 1;
    ctx.strokeStyle = INK.textMuted;
    ctx.globalAlpha = 0.35;
    ctx.stroke();
    ctx.restore();
  },
};
