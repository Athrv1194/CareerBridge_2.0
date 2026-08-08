import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  LuArrowRight, LuArrowLeft, LuSlidersHorizontal, LuX, LuBuilding2,
  LuTriangleAlert, LuRoute, LuDownload, LuChartColumn,
  LuGraduationCap, LuUser, LuPlus, LuCheck, LuEye, LuEyeOff,
  LuSparkles, LuBriefcase, LuAward, LuRefreshCw, LuFileText, LuUsers,
  LuSun, LuHistory, LuChevronRight, LuChevronDown, LuExternalLink,
  LuBell, LuSearch,
} from 'react-icons/lu';

const iconMap = {
  'sliders-horizontal': LuSlidersHorizontal,
  x: LuX,
  'chart-no-axes-column': LuChartColumn,
  'building-2': LuBuilding2,
  'triangle-alert': LuTriangleAlert,
  route: LuRoute,
  download: LuDownload,
  'arrow-left': LuArrowLeft,
  'graduation-cap': LuGraduationCap,
  user: LuUser,
  plus: LuPlus,
  check: LuCheck,
  sparkles: LuSparkles,
  briefcase: LuBriefcase,
  award: LuAward,
  'refresh-cw': LuRefreshCw,
  'file-text': LuFileText,
  users: LuUsers,
  sun: LuSun,
  history: LuHistory,
  'chevron-right': LuChevronRight,
  'chevron-down': LuChevronDown,
  'external-link': LuExternalLink,
  bell: LuBell,
  search: LuSearch,
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
  children, variant = 'primary', size = 'md', icon, iconAfter, onClick, to,
  fullWidth = false, disabled = false, style,
}) {
  const [hovered, setHovered] = useState(false);
  const v = buttonVariants[variant] || buttonVariants.primary;
  const s = buttonSizes[size] || buttonSizes.md;
  const BeforeIcon = icon ? iconMap[icon] : null;
  const AfterIcon = iconAfter ? (iconAfter === 'arrow-right' ? LuArrowRight : iconMap[iconAfter]) : null;
  const content = (
    <>
      {BeforeIcon && <BeforeIcon size={16} />}
      {children}
      {AfterIcon && <AfterIcon size={16} />}
    </>
  );

  const hoverStyles = {
    primary: { background: 'var(--ink-700)', border: '1px solid var(--ink-700)' },
    secondary: { background: 'var(--ink-900)', color: 'var(--bone-50)', border: '1px solid var(--ink-900)' },
    ghost: { background: 'var(--bone-200)', border: '1px solid var(--bone-200)' },
    quiet: { background: 'var(--bone-200)', border: '1px solid var(--bone-200)' },
  };
  const h = hoverStyles[variant] || {};

  const combined = {
    display: 'inline-flex', alignItems: 'center', justifyContent: 'center', gap: s.gap,
    fontSize: s.fontSize, padding: s.padding, fontWeight: 500, letterSpacing: 0,
    borderRadius: 'var(--radius-sm)', cursor: disabled ? 'not-allowed' : 'pointer',
    transition: 'var(--transition-color)', opacity: disabled ? 0.6 : 1,
    width: fullWidth ? '100%' : 'auto', whiteSpace: 'nowrap', fontFamily: 'var(--font-sans)',
    ...v,
    ...(hovered && !disabled ? h : {}),
    ...style,
  };

  const handlers = {
    onMouseEnter: () => setHovered(true),
    onMouseLeave: () => setHovered(false),
  };

  if (to && !disabled) {
    return <Link to={to} style={{ ...combined, textDecoration: 'none', border: combined.border }} {...handlers}>{content}</Link>;
  }
  return <button type="button" onClick={onClick} disabled={disabled} style={combined} {...handlers}>{content}</button>;
}

export function IconButton({ icon, label, onClick, variant = 'ghost', iconStyle }) {
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
      <IconComp size={20} style={iconStyle} />
    </button>
  );
}

export function Logo({ size = 32, tone = 'default', showText = true }) {
  const inverse = tone === 'inverse';
  return (
    <Link to="/" style={{ display: 'inline-flex', alignItems: 'center', gap: 10, border: 'none' }}>
      <img
        src="/images/logo-monogram.png"
        alt=""
        style={{ height: size, width: 'auto', filter: inverse ? 'brightness(0) invert(1)' : 'none' }}
      />
      {showText && (
        <span style={{ fontFamily: 'var(--font-display)', fontSize: size * 0.5, color: inverse ? 'var(--bone-50)' : 'var(--ink-900)', whiteSpace: 'nowrap' }}>
          CareerBridge
        </span>
      )}
    </Link>
  );
}

const badgeTones = {
  inverse: { background: 'var(--taupe-600)', color: 'var(--bone-50)' },
  dark: { background: 'var(--ink-800)', color: 'var(--bone-50)' },
  accent: { background: 'var(--taupe-300)', color: 'var(--ink-900)' },
  default: { background: 'var(--taupe-100)', color: 'var(--ink-800)' },
  success: { background: 'var(--status-success-soft)', color: 'var(--status-success)' },
  warning: { background: 'var(--status-warning-soft)', color: 'var(--status-warning)' },
  info: { background: 'var(--status-info-soft)', color: 'var(--status-info)' },
};

export function Badge({ children, tone = 'default', icon }) {
  const toneStyle = badgeTones[tone] || badgeTones.default;
  const IconComp = icon ? iconMap[icon] : null;
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 5, alignSelf: 'flex-start', padding: '4px 10px',
        fontSize: 11, fontWeight: 500, letterSpacing: '.08em', textTransform: 'uppercase',
        borderRadius: 'var(--radius-pill)', ...toneStyle,
      }}
    >
      {IconComp && <IconComp size={11} />}
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

const METER_TONE_COLOR = {
  accent: 'var(--taupe-700)', ink: 'var(--ink-900)',
  success: 'var(--status-success)', warning: 'var(--status-warning)', danger: 'var(--status-danger)',
};

export function ProgressMeter({ value, max = 100, tone = 'accent' }) {
  const pct = Math.max(0, Math.min(100, (value / max) * 100));
  return (
    <div style={{ height: 6, background: 'var(--bone-300)', borderRadius: 'var(--radius-pill)', overflow: 'hidden' }}>
      <div
        style={{
          height: '100%', width: `${pct}%`,
          background: METER_TONE_COLOR[tone] || METER_TONE_COLOR.accent,
          transition: 'width var(--duration-normal) var(--ease-standard)',
        }}
      />
    </div>
  );
}

// Same 80/60/40/20 breakpoints as the completion grade (A/B/C/D/F) elsewhere in this app, so the
// ring's color and the letter grade next to it never disagree. The token palette's status colors
// (--status-danger etc.) are muted/dark, tuned for the light page background -- on this ring's
// near-black sidebar they'd be low-contrast, so inverse gets the same brighter equivalents the
// checklist badges already use on this exact background (BADGE_TONE_STYLE's success, for one).
function scoreToColor(value, inverse) {
  if (inverse) {
    if (value >= 80) return '#8FB393';
    if (value >= 60) return '#7FA8C9';
    if (value >= 40) return '#E3C77A';
    return '#D97A66';
  }
  if (value >= 80) return 'var(--status-success)';
  if (value >= 60) return 'var(--status-info)';
  if (value >= 40) return 'var(--status-warning)';
  return 'var(--status-danger)';
}

const SCORE_RING_PX = { lg: 176, md: 128, sm: 64 };

export function ScoreRing({
  value, grade, size = 'lg', label, caption, tone = 'default', colorByScore = false,
}) {
  const px = SCORE_RING_PX[size] || SCORE_RING_PX.md;
  const stroke = size === 'sm' ? 6 : 10;
  const r = (px - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (value / 100) * c;
  const inverse = tone === 'inverse';
  // Deliberately not a CSS filter (e.g. brightness(0) invert(1)) on the whole graphic: that
  // zeroes every pixel to black regardless of its original shade, so the light track and the
  // dark progress arc both end up the exact same white and the fill becomes invisible. Two
  // separate stroke colors, chosen per tone, is the only way both circles stay distinguishable.
  const trackStroke = inverse ? 'rgba(255,255,255,.18)' : 'var(--bone-300)';
  const progressStroke = colorByScore ? scoreToColor(value, inverse) : (inverse ? '#FCFBF9' : 'var(--ink-900)');
  const valueColor = inverse ? '#FCFBF9' : 'var(--ink-900)';
  const gradeColor = inverse ? 'rgba(255,255,255,.55)' : 'var(--ink-500)';
  const labelColor = inverse ? '#FCFBF9' : 'var(--ink-900)';
  const captionColor = inverse ? 'rgba(255,255,255,.55)' : 'var(--ink-500)';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: size === 'sm' ? 0 : 16, maxWidth: 280 }}>
      <div style={{ position: 'relative', width: px, height: px }}>
        <svg width={px} height={px} viewBox={`0 0 ${px} ${px}`}>
          <circle cx={px / 2} cy={px / 2} r={r} fill="none" stroke={trackStroke} strokeWidth={stroke} />
          <circle
            cx={px / 2} cy={px / 2} r={r} fill="none" stroke={progressStroke} strokeWidth={stroke}
            strokeDasharray={c} strokeDashoffset={offset} strokeLinecap="round"
            transform={`rotate(-90 ${px / 2} ${px / 2})`}
          />
        </svg>
        <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
          <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: size === 'sm' ? 16 : 44, color: valueColor }}>{value}</span>
          {grade && <span style={{ fontSize: 12, letterSpacing: '.1em', color: gradeColor }}>GRADE {grade}</span>}
        </div>
      </div>
      {(label || caption) && (
        <div style={{ textAlign: 'center' }}>
          {label && <div style={{ fontSize: 14, fontWeight: 600, color: labelColor }}>{label}</div>}
          {caption && <div style={{ fontSize: 13, color: captionColor, marginTop: 4 }}>{caption}</div>}
        </div>
      )}
    </div>
  );
}

export function Alert({ tone = 'info', title, message }) {
  const toneStyle = tone === 'danger'
    ? { background: 'var(--status-danger-soft)', color: 'var(--status-danger)' }
    : { background: 'var(--status-info-soft)', color: 'var(--status-info)' };
  return (
    <div style={{ padding: '12px 16px', borderRadius: 'var(--radius-sm)', fontSize: 13.5, lineHeight: 1.5, ...toneStyle }}>
      {message ? <strong style={{ fontWeight: 600 }}>{title}</strong> : title}
      {message && <div style={{ marginTop: 2 }}>{message}</div>}
    </div>
  );
}

export function Field({ label, hint, error, children }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-800)' }}>{label}</span>
      {children}
      {error ? (
        <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{error}</span>
      ) : hint ? (
        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{hint}</span>
      ) : null}
    </label>
  );
}

export function Input({ type = 'text', placeholder, value, onChange, error }) {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  const inputStyle = {
    width: '100%', boxSizing: 'border-box', padding: isPassword ? '9px 40px 9px 12px' : '9px 12px',
    fontSize: 14, fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)',
    border: `1px solid ${error ? 'var(--status-danger)' : 'var(--line-hairline)'}`,
    borderRadius: 'var(--radius-sm)', outline: 'none',
  };

  if (!isPassword) {
    return <input type={type} placeholder={placeholder} value={value} onChange={onChange} style={inputStyle} />;
  }

  return (
    <div style={{ position: 'relative' }}>
      <input type={revealed ? 'text' : 'password'} placeholder={placeholder} value={value} onChange={onChange} style={inputStyle} />
      <button
        type="button"
        onClick={() => setRevealed((r) => !r)}
        aria-label={revealed ? 'Hide password' : 'Show password'}
        style={{
          position: 'absolute', top: '50%', right: 10, transform: 'translateY(-50%)',
          display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          border: 'none', background: 'transparent', padding: 4, cursor: 'pointer', color: 'var(--ink-400)',
        }}
      >
        {revealed ? <LuEyeOff size={17} /> : <LuEye size={17} />}
      </button>
    </div>
  );
}

export function Checkbox({ label, checked, onChange }) {
  return (
    <label style={{ display: 'flex', alignItems: 'flex-start', gap: 10, fontSize: 13, color: 'var(--ink-700)', cursor: 'pointer' }}>
      <input
        type="checkbox"
        checked={checked}
        onChange={onChange}
        style={{ marginTop: 2, width: 15, height: 15, accentColor: 'var(--ink-900)', cursor: 'pointer' }}
      />
      <span>{label}</span>
    </label>
  );
}

export function Textarea({ rows = 4, placeholder, value, onChange }) {
  return (
    <textarea
      rows={rows}
      placeholder={placeholder}
      value={value}
      onChange={onChange}
      style={{
        width: '100%', boxSizing: 'border-box', padding: '9px 12px', fontSize: 14,
        fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)',
        border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-sm)',
        outline: 'none', resize: 'vertical',
      }}
    />
  );
}

export function Select({ options, value, onChange, style }) {
  return (
    <select
      value={value}
      onChange={onChange}
      style={{
        boxSizing: 'border-box', padding: '9px 10px', fontSize: 13,
        fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)',
        border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-sm)',
        outline: 'none', cursor: 'pointer', ...style,
      }}
    >
      {options.map((opt) => (
        <option key={opt} value={opt}>
          {opt.charAt(0) + opt.slice(1).toLowerCase()}
        </option>
      ))}
    </select>
  );
}

export function Switch({ label, checked, onChange }) {
  return (
    <label style={{ display: 'inline-flex', alignItems: 'center', gap: 10, fontSize: 13, color: 'var(--ink-800)', cursor: 'pointer' }}>
      <span
        onClick={() => onChange(!checked)}
        style={{
          position: 'relative', width: 36, height: 20, borderRadius: 'var(--radius-pill)',
          background: checked ? 'var(--ink-900)' : 'var(--bone-400)',
          transition: 'var(--transition-color)', flexShrink: 0,
        }}
      >
        <span
          style={{
            position: 'absolute', top: 2, left: checked ? 18 : 2, width: 16, height: 16,
            borderRadius: '50%', background: 'var(--bone-50)',
            transition: 'left var(--duration-normal) var(--ease-standard)',
          }}
        />
      </span>
      {label}
    </label>
  );
}

export function Tag({ children, onRemove }) {
  return (
    <span
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 8,
        padding: onRemove ? '6px 8px 6px 12px' : '6px 12px',
        fontSize: 13, color: 'var(--ink-900)', background: 'var(--taupe-100)',
        borderRadius: 'var(--radius-pill)',
      }}
    >
      {children}
      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          aria-label={`Remove ${children}`}
          style={{
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            width: 18, height: 18, border: 'none', background: 'var(--taupe-300)',
            borderRadius: '50%', cursor: 'pointer', color: 'var(--ink-900)', padding: 0,
          }}
        >
          <LuX size={11} />
        </button>
      )}
    </span>
  );
}

export function OtpInput({ length = 4, value, onChange, error, autoFocus = true }) {
  const digits = value.padEnd(length, ' ').split('').slice(0, length);
  const refs = useRef([]);

  const setDigit = (index, char) => {
    const next = digits.slice();
    next[index] = char;
    onChange(next.join('').replace(/ /g, ''));
  };

  const handleChange = (index, raw) => {
    const char = raw.replace(/\D/g, '').slice(-1);
    if (!char) return;
    setDigit(index, char);
    if (index < length - 1) refs.current[index + 1]?.focus();
  };

  const handleKeyDown = (index, e) => {
    if (e.key === 'Backspace') {
      if (digits[index] !== ' ' && digits[index] !== '') {
        setDigit(index, ' ');
      } else if (index > 0) {
        refs.current[index - 1]?.focus();
        setDigit(index - 1, ' ');
      }
    } else if (e.key === 'ArrowLeft' && index > 0) {
      refs.current[index - 1]?.focus();
    } else if (e.key === 'ArrowRight' && index < length - 1) {
      refs.current[index + 1]?.focus();
    }
  };

  const handlePaste = (e) => {
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length);
    if (!pasted) return;
    e.preventDefault();
    onChange(pasted.padEnd(length, '').trimEnd());
    refs.current[Math.min(pasted.length, length - 1)]?.focus();
  };

  return (
    <div style={{ display: 'flex', gap: 10 }}>
      {digits.map((digit, index) => (
        <input
          // eslint-disable-next-line react/no-array-index-key
          key={index}
          ref={(el) => { refs.current[index] = el; }}
          type="text"
          inputMode="numeric"
          maxLength={1}
          autoFocus={autoFocus && index === 0}
          value={digit === ' ' ? '' : digit}
          onChange={(e) => handleChange(index, e.target.value)}
          onKeyDown={(e) => handleKeyDown(index, e)}
          onPaste={handlePaste}
          style={{
            width: 48, height: 56, textAlign: 'center', fontSize: 22, fontFamily: 'var(--font-numeric)',
            color: 'var(--ink-900)', background: 'var(--bone-50)',
            border: `1px solid ${error ? 'var(--status-danger)' : 'var(--line-hairline)'}`,
            borderRadius: 'var(--radius-sm)', outline: 'none',
          }}
        />
      ))}
    </div>
  );
}

export function Skeleton({ height = 40 }) {
  return (
    <div
      style={{
        width: '100%', height, borderRadius: 'var(--radius-sm)',
        background: 'linear-gradient(90deg, var(--bone-200) 25%, var(--bone-300) 37%, var(--bone-200) 63%)',
        backgroundSize: '400% 100%', animation: 'cbSkeletonShimmer 1.4s ease infinite',
      }}
    />
  );
}

export function MatchScore({ value, size = 'sm' }) {
  const lg = size === 'lg';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, width: 70, flex: 'none' }}>
      <span className="cb-num" style={{ fontSize: lg ? 18 : 14, color: 'var(--ink-900)' }}>{value}%</span>
      <div style={{ width: '100%', height: lg ? 4 : 3, background: 'var(--bone-300)', borderRadius: 'var(--radius-pill)', overflow: 'hidden' }}>
        <div style={{ width: `${Math.max(0, Math.min(100, value))}%`, height: '100%', background: 'var(--taupe-700)' }} />
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
