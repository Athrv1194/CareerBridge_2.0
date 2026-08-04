import { Link } from 'react-router-dom';
import {
  LuArrowRight, LuSlidersHorizontal, LuX, LuBuilding2,
  LuTriangleAlert, LuRoute, LuDownload, LuChartColumn,
} from 'react-icons/lu';

const iconMap = {
  'sliders-horizontal': LuSlidersHorizontal,
  x: LuX,
  'chart-no-axes-column': LuChartColumn,
  'building-2': LuBuilding2,
  'triangle-alert': LuTriangleAlert,
  route: LuRoute,
  download: LuDownload,
};

export function Icon({ name, size = 18, style }) {
  const IconComp = iconMap[name] || LuChartColumn;
  return <IconComp size={size} style={{ color: 'var(--taupe-700)', ...style }} />;
}

const buttonVariants = {
  primary: { background: 'var(--ink-900)', color: 'var(--bone-50)', border: '1px solid var(--ink-900)' },
  secondary: { background: 'transparent', color: 'var(--ink-900)', border: 'var(--border-ink)' },
  ghost: { background: 'transparent', color: 'var(--ink-700)', border: '1px solid transparent' },
  quiet: { background: 'var(--bone-50)', color: 'var(--ink-900)', border: '1px solid var(--bone-50)' },
};

const buttonSizes = {
  sm: { fontSize: 13, padding: '8px 16px', gap: 6 },
  md: { fontSize: 14, padding: '11px 20px', gap: 8 },
  lg: { fontSize: 15, padding: '13px 24px', gap: 8 },
};

export function Button({
  children, variant = 'primary', size = 'md', iconAfter, onClick, to,
  fullWidth = false, style,
}) {
  const v = buttonVariants[variant] || buttonVariants.primary;
  const s = buttonSizes[size] || buttonSizes.md;
  const content = (
    <>
      {children}
      {iconAfter === 'arrow-right' && <LuArrowRight size={16} />}
    </>
  );
  const combined = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: s.gap,
    fontSize: s.fontSize, padding: s.padding, fontWeight: 500, letterSpacing: 0,
    borderRadius: 'var(--radius-sm)', cursor: 'pointer', transition: 'var(--transition-color)',
    width: fullWidth ? '100%' : 'auto', whiteSpace: 'nowrap', fontFamily: 'var(--font-sans)',
    ...v, ...style,
  };
  if (to) {
    return <Link to={to} style={{ ...combined, textDecoration: 'none', border: v.border }}>{content}</Link>;
  }
  return <button type="button" onClick={onClick} style={combined}>{content}</button>;
}

export function IconButton({ icon, label, onClick, variant = 'ghost' }) {
  const IconComp = iconMap[icon] || LuSlidersHorizontal;
  const v = buttonVariants[variant] || buttonVariants.ghost;
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onClick}
      style={{
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        width: 40, height: 40, borderRadius: 'var(--radius-sm)', cursor: 'pointer',
        ...v,
      }}
    >
      <IconComp size={20} />
    </button>
  );
}

export function Logo({ size = 32 }) {
  return (
    <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 8, border: 'none' }}>
      <img src="/images/logo-monogram.png" alt="CareerBridge" style={{ height: size, width: 'auto' }} />
    </Link>
  );
}

export function Badge({ children, tone = 'default' }) {
  const toneStyle = tone === 'inverse'
    ? { background: 'var(--bone-50)', color: 'var(--ink-900)' }
    : { background: 'var(--taupe-100)', color: 'var(--ink-800)' };
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', padding: '4px 10px',
        fontSize: 11, fontWeight: 500, letterSpacing: '.08em', textTransform: 'uppercase',
        borderRadius: 'var(--radius-pill)', ...toneStyle,
      }}
    >
      {children}
    </span>
  );
}

export function StatTile({ value, label, tone = 'default' }) {
  const inverse = tone === 'inverse';
  return (
    <div
      style={{
        background: inverse ? 'var(--ink-900)' : 'var(--bone-50)', padding: '32px 24px',
        display: 'flex', flexDirection: 'column', gap: 6, justifyContent: 'center', minHeight: 120,
      }}
    >
      <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 36, fontWeight: 400, color: inverse ? 'var(--bone-50)' : 'var(--ink-900)' }}>
        {value}
      </span>
      <span style={{ fontSize: 13, color: inverse ? 'var(--ink-400)' : 'var(--ink-500)' }}>{label}</span>
    </div>
  );
}

export function ProgressMeter({ value, max = 100, tone = 'accent' }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div style={{ height: 6, background: 'var(--bone-300)', borderRadius: 'var(--radius-pill)', overflow: 'hidden' }}>
      <div
        style={{
          height: '100%', width: `${pct}%`,
          background: tone === 'accent' ? 'var(--taupe-700)' : 'var(--ink-900)',
          transition: 'width var(--duration-normal) var(--ease-standard)',
        }}
      />
    </div>
  );
}

export function ScoreRing({ value, grade, size = 'lg', label, caption }) {
  const px = size === 'lg' ? 176 : 128;
  const stroke = 10;
  const r = (px - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (value / 100) * c;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, maxWidth: 280 }}>
      <div style={{ position: 'relative', width: px, height: px }}>
        <svg width={px} height={px} viewBox={`0 0 ${px} ${px}`}>
          <circle cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--bone-300)" strokeWidth={stroke} />
          <circle
            cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--ink-900)" strokeWidth={stroke}
            strokeDasharray={c} strokeDashoffset={offset} strokeLinecap="round"
            transform={`rotate(-90 ${px / 2} ${px / 2})`}
          />
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 44, color: 'var(--ink-900)' }}>{value}</span>
          <span style={{ fontSize: 12, letterSpacing: '.1em', color: 'var(--ink-500)' }}>GRADE {grade}</span>
        </div>
      </div>
      <div style={{ textAlign: 'center' }}>
        <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{label}</div>
        {caption && <div style={{ fontSize: 13, color: 'var(--ink-500)', marginTop: 4 }}>{caption}</div>}
      </div>
    </div>
  );
}

export function ListRow({ leading, title }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '16px 0', borderBottom: 'var(--border-hairline)', minHeight: 56 }}>
      {leading}
      <span style={{ fontSize: 14, color: 'var(--ink-800)' }}>{title}</span>
    </div>
  );
}
