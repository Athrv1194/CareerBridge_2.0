import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Icon, IconButton, Logo, ProgressMeter, Skeleton, StatTile,
} from '../../components/ui';
import { getMyRoadmap, getMyRoadmaps, activateRoadmap, completeMilestone } from '../../api/roadmapApi';
import { getMyResources } from '../../api/aiCoachApi';
import { getMyProfile } from '../../api/studentApi';
import { getUnreadCount } from '../../api/notificationApi';
import './roadmap.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap', active: true },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

const PACE_OPTIONS = [
  { value: 'steady', label: 'Steady', description: 'I have 2–3 hours a week.' },
  { value: 'ambitious', label: 'Ambitious', description: 'I can do 5–7 hours.' },
  { value: 'aggressive', label: 'Aggressive', description: 'Full focus, 10+ hours.' },
];

const STATUS_TONE = { IN_PROGRESS: 'info', COMPLETED: 'accent' };
const STATUS_LABEL = { IN_PROGRESS: 'In progress', COMPLETED: 'Completed' };

function fmtMonthDay(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

function deriveStats(roadmap) {
  if (!roadmap) return { total: 0, completed: 0, pct: 0, remainingWeeks: 0, weeksSinceStart: 1 };
  const ms = roadmap.milestones || [];
  const total = roadmap.totalMilestones ?? ms.length;
  const completed = roadmap.completedMilestones ?? ms.filter((m) => m.isCompleted).length;
  const incompleteDays = ms.filter((m) => !m.isCompleted).reduce((a, m) => a + (m.estimatedDays || 0), 0);
  const remainingWeeks = total ? Math.ceil(incompleteDays / 5) : 0;
  const started = roadmap.startedAt ? new Date(roadmap.startedAt) : new Date();
  const diffDays = Math.max(0, Math.floor((Date.now() - started.getTime()) / 86400000));
  const weeksSinceStart = Math.max(1, Math.floor(diffDays / 7) + 1);
  return { total, completed, pct: roadmap.completionPercentage ?? 0, remainingWeeks, weeksSinceStart };
}

function cap(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : 'Resource';
}

// Prefers the ai-coach resource catalog (up to 4 links + 1 video); falls back to the seeded resourceUrl.
function ResourceBadges({ resources, fallbackUrl }) {
  const items = (resources && resources.length > 0)
    ? resources
    : (fallbackUrl ? [{ title: 'Resource', url: fallbackUrl, type: 'link', platform: '' }] : []);
  if (items.length === 0) return null;
  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 4 }}>
      {items.map((r) => (
        <a key={r.url} href={r.url} target="_blank" rel="noopener noreferrer" title={r.title} style={{ border: 0, display: 'inline-flex' }}>
          <Badge tone="accent" icon="external-link">{cap(r.type)}</Badge>
        </a>
      ))}
    </div>
  );
}

function MilestoneCircle({ isCompleted, isCurrent, orderIndex }) {
  if (isCompleted) {
    return (
      <div style={{ width: 24, height: 24, borderRadius: '50%', background: 'var(--ink-900)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <Icon name="check" size={14} style={{ color: 'var(--bone-50)' }} />
      </div>
    );
  }
  return (
    <div className="cb-num" style={{
      width: 24, height: 24, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: 12, flexShrink: 0,
      ...(isCurrent
        ? { border: '2px solid var(--ink-900)', color: 'var(--ink-900)' }
        : { background: 'var(--bone-300)', color: 'var(--ink-300)' }),
    }}
    >
      {orderIndex}
    </div>
  );
}

function MilestoneRow({ milestone, index, isCurrent, isUpcoming, expanded, onToggleExpand, resources, onComplete, completing, rowError }) {
  const m = milestone;
  const showDescription = isCurrent || expanded;
  const expandable = m.isCompleted || isUpcoming;

  return (
    <div
      className="cb-rm-row"
      style={{
        display: 'grid', gap: 16, alignItems: 'start', padding: '18px 20px',
        borderBottom: '1px solid var(--line-hairline)',
        background: m.isCompleted ? 'var(--bone-50)' : 'var(--bone-100)',
        borderLeft: `2px solid ${isCurrent ? 'var(--taupe-600)' : 'transparent'}`,
      }}
    >
      <div style={{ width: 28, height: 28, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <MilestoneCircle isCompleted={m.isCompleted} isCurrent={isCurrent} orderIndex={index + 1} />
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: isCurrent ? 6 : 4, minWidth: 0 }}>
        {expandable ? (
          <div onClick={onToggleExpand} style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', width: 'fit-content' }}>
            <span style={{
              fontSize: 14, fontWeight: m.isCompleted ? 500 : 400, color: 'var(--ink-400)',
              textDecoration: m.isCompleted ? 'line-through' : 'none',
            }}
            >
              {m.title}
            </span>
            <Icon name={expanded ? 'chevron-down' : 'chevron-right'} size={14} style={{ color: 'var(--ink-300)' }} />
          </div>
        ) : (
          <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>{m.title}</span>
        )}

        {showDescription && (
          <span style={{ fontSize: isCurrent ? 14 : 13, lineHeight: 1.55, color: isCurrent ? 'var(--ink-600)' : 'var(--ink-300)', maxWidth: 560 }}>
            {m.description}
          </span>
        )}

        {isCurrent && (
          <div style={{ display: 'flex', gap: 14, alignItems: 'center', marginTop: 4, flexWrap: 'wrap' }}>
            <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{m.estimatedDays} days</span>
          </div>
        )}
        {isUpcoming && !expanded && (
          <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-300)' }}>{m.estimatedDays} days</span>
        )}

        {(isCurrent || (expandable && expanded)) && (
          <ResourceBadges resources={resources} fallbackUrl={m.resourceUrl} />
        )}
      </div>

      <div className="cb-rm-row-right" style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 8 }}>
        {m.isCompleted && (
          <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-300)' }}>{fmtMonthDay(m.completedAt)}</span>
        )}
        {isCurrent && (
          <>
            {rowError && (
              <div style={{ width: 230 }}>
                <Alert tone="danger" message={rowError} />
              </div>
            )}
            <Button size="sm" iconAfter={completing ? undefined : 'arrow-right'} disabled={completing} onClick={onComplete}>
              {completing ? 'Marking…' : 'Mark complete'}
            </Button>
          </>
        )}
      </div>
    </div>
  );
}

function PaceDialog({ choice, onChoose, onClose, onSave }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--surface-overlay)', zIndex: 70, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={onClose}>
      <div
        style={{ width: 480, maxWidth: '90vw', background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 28, display: 'flex', flexDirection: 'column', gap: 20 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div>
          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Before we set your schedule</span>
          <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 24, fontWeight: 400, color: 'var(--ink-900)', margin: '6px 0 0' }}>How fast do you want to move?</h2>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {PACE_OPTIONS.map((opt) => (
            <label
              key={opt.value}
              style={{
                display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px', cursor: 'pointer',
                border: `1px solid ${choice === opt.value ? 'var(--ink-900)' : 'var(--line-hairline)'}`,
                background: choice === opt.value ? 'var(--taupe-100)' : 'transparent',
              }}
            >
              <input type="radio" name="pace" checked={choice === opt.value} onChange={() => onChoose(opt.value)} style={{ accentColor: 'var(--ink-900)' }} />
              <div>
                <div style={{ fontSize: 14, fontWeight: 500, color: 'var(--ink-900)' }}>{opt.label}</div>
                <div style={{ fontSize: 13, color: 'var(--ink-500)' }}>{opt.description}</div>
              </div>
            </label>
          ))}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, paddingTop: 16, borderTop: '1px solid var(--line-hairline)' }}>
          <Badge tone="accent">Proposed state</Badge>
          <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Pace planning is coming — this sets your preference for when it ships.</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Button variant="secondary" size="sm" onClick={onClose}>Back</Button>
          <Button size="sm" iconAfter="arrow-right" onClick={onSave}>Set my pace</Button>
        </div>
      </div>
    </div>
  );
}

export default function RoadmapPage() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [roadmap, setRoadmap] = useState(null);
  const [resourcesByTitle, setResourcesByTitle] = useState({});
  const [studentName, setStudentName] = useState('');
  const [expandedIds, setExpandedIds] = useState(() => new Set());
  const [completingId, setCompletingId] = useState(null);
  const [rowErrorId, setRowErrorId] = useState(null);
  const [rowErrorMessage, setRowErrorMessage] = useState('');
  const [paceOpen, setPaceOpen] = useState(false);
  const [paceChoice, setPaceChoice] = useState(() => localStorage.getItem('cb_roadmap_pace') || 'ambitious');
  const [toast, setToast] = useState({ visible: false, title: '', message: '' });
  const [allRoadmaps, setAllRoadmaps] = useState([]);
  const [switching, setSwitching] = useState(false);

  const toastTimerRef = useRef(null);

  useEffect(() => { getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {}); }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getMyRoadmap(), getMyProfile(), getMyResources()]).then(([roadmapRes, profileRes, resourcesRes]) => {
      if (cancelled) return;
      if (roadmapRes.status === 'fulfilled') {
        if (roadmapRes.value === null) setNotFound(true);
        else setRoadmap(roadmapRes.value);
      } else {
        setNotFound(true);
      }
      if (profileRes.status === 'fulfilled' && profileRes.value) {
        setStudentName(`${profileRes.value.firstName || ''} ${profileRes.value.lastName || ''}`.trim());
      }
      if (resourcesRes.status === 'fulfilled') {
        const map = {};
        resourcesRes.value.forEach((entry) => { map[entry.milestoneTitle] = entry.resources || []; });
        setResourcesByTitle(map);
      }
      setLoading(false);
    });
    getMyRoadmaps().then(setAllRoadmaps).catch(() => {});
    return () => { cancelled = true; };
  }, []);

  useEffect(() => () => clearTimeout(toastTimerRef.current), []);

  const switchRoadmap = useCallback((id) => {
    if (switching || (roadmap && roadmap.id === id)) return;
    setSwitching(true);
    activateRoadmap(id)
      .then((next) => { setRoadmap(next); setNotFound(false); })
      .catch(() => showToast('Could not switch', 'Please try again.'))
      .finally(() => setSwitching(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [switching, roadmap]);

  const showToast = useCallback((title, message) => {
    clearTimeout(toastTimerRef.current);
    setToast({ visible: true, title, message });
    toastTimerRef.current = setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const toggleExpand = useCallback((id) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }, []);

  const handleComplete = useCallback(async (milestone) => {
    setCompletingId(milestone.id);
    setRowErrorId(null);
    try {
      const updated = await completeMilestone(milestone.id);
      setRoadmap(updated);
      showToast('Milestone complete', "We'll update your readiness score within the hour.");
    } catch (e) {
      setRowErrorId(milestone.id);
      setRowErrorMessage(e.message);
    } finally {
      setCompletingId(null);
    }
  }, [showToast]);

  const savePace = useCallback(() => {
    localStorage.setItem('cb_roadmap_pace', paceChoice);
    setPaceOpen(false);
  }, [paceChoice]);

  const sidebarWidth = navCollapsed ? '60px' : '248px';
  const stats = deriveStats(roadmap);
  const milestones = roadmap?.milestones || [];
  const firstOpenIdx = milestones.findIndex((m) => !m.isCompleted);
  const isCompletedStatus = roadmap?.status === 'COMPLETED';

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-rm-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--status-danger)', color: '#FCFBF9', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', boxSizing: 'border-box' }}>
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Icon name="user" size={15} />
            </div>
            <div className="cb-rm-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
        </div>
      </header>

      <div className="cb-rm-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside style={{ borderRight: '1px solid var(--line-hairline)', position: 'sticky', top: 64, height: 'calc(100vh - 64px)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${navCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-rm-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
            <IconButton
              icon="chevron-right"
              label={navCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              onClick={() => setNavCollapsed((v) => !v)}
              iconStyle={{ transform: navCollapsed ? 'none' : 'rotate(180deg)', transition: 'transform 200ms ease' }}
            />
          </div>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                title={item.label}
                style={{
                  display: 'flex', alignItems: 'center', gap: 11,
                  justifyContent: navCollapsed ? 'center' : 'flex-start',
                  padding: navCollapsed ? '10px 0' : '10px 14px', fontSize: 13,
                  letterSpacing: '.06em', textTransform: 'uppercase',
                  background: item.active ? 'var(--ink-900)' : 'transparent',
                  color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)', border: 'none',
                }}
              >
                <Icon name={item.icon} size={16} style={{ color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)' }} />
                {!navCollapsed && <span className="cb-rm-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          {!navCollapsed && (
            <div className="cb-rm-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>Roadmap pacing and coach follow-ups need Plus.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers your top 3 matches. Upgrade for the full roadmap, unlimited coach sessions and résumé exports.</p>
                <Link to="/plans" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        <main style={{ minWidth: 0, background: 'var(--surface-page)' }}>
          <div className="cb-rm-main-pad" style={{ maxWidth: 1160, margin: '0 auto', padding: '32px 32px 64px', boxSizing: 'border-box' }}>

            {loading && <Skeleton height={320} />}

            {!loading && notFound && (
              <div className="cb-rm-fade" style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Roadmap</span>
                <div style={{ border: '1px solid var(--line-hairline)', padding: '64px 32px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16, textAlign: 'center' }}>
                  <Icon name="route" size={28} />
                  <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 24, fontWeight: 400, color: 'var(--ink-900)', margin: 0 }}>No roadmap yet</h2>
                  <p style={{ fontSize: 14, color: 'var(--text-secondary)', margin: 0, maxWidth: 420 }}>
                    Your roadmap is generated from your assessment result. Finish one and it appears here.
                  </p>
                  <Button iconAfter="arrow-right" to="/assessment">Start my assessment</Button>
                </div>
              </div>
            )}

            {!loading && !notFound && roadmap && (
              <div className="cb-rm-fade" style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>

                {allRoadmaps.length > 1 && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-400)', marginRight: 4 }}>Your roadmaps</span>
                    {allRoadmaps.map((r) => (
                      <button
                        key={r.id}
                        type="button"
                        disabled={switching}
                        onClick={() => switchRoadmap(r.id)}
                        style={{
                          display: 'inline-flex', alignItems: 'center', gap: 6, padding: '6px 14px',
                          borderRadius: 'var(--radius-pill)', fontSize: 13, cursor: switching ? 'default' : 'pointer',
                          border: `1px solid ${r.id === roadmap.id ? 'var(--ink-900)' : 'var(--line-hairline)'}`,
                          background: r.id === roadmap.id ? 'var(--ink-900)' : 'transparent',
                          color: r.id === roadmap.id ? 'var(--bone-50)' : 'var(--ink-700)',
                        }}
                      >
                        {r.careerName}
                        {r.id === roadmap.id && <Icon name="check" size={13} />}
                      </button>
                    ))}
                  </div>
                )}

                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>
                      Roadmap · {roadmap.careerName}
                    </span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <Badge tone={STATUS_TONE[roadmap.status] || 'default'}>{STATUS_LABEL[roadmap.status] || roadmap.status}</Badge>
                      <Button variant="ghost" size="sm" iconAfter="arrow-right" onClick={() => setPaceOpen(true)}>Change the pace</Button>
                    </div>
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <h1 className="cb-rm-hero" style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1.04, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400, maxWidth: 680 }}>
                      {isCompletedStatus ? (
                        <>You finished. <i>{roadmap.careerName}</i> is within reach.</>
                      ) : (
                        <>{stats.total} steps to a <i>{roadmap.careerName}</i> shortlist. You have done {stats.completed}.</>
                      )}
                    </h1>
                    <p style={{ fontSize: 14, lineHeight: 1.65, color: 'var(--ink-600)', margin: 0, maxWidth: 520 }}>
                      Generated from your assessment result. Milestones are ordered — finishing them out of sequence works, but the estimates assume you do not.
                    </p>
                  </div>
                </div>

                {isCompletedStatus && (
                  <div className="cb-rm-completed-alert" style={{ display: 'flex', alignItems: 'center', gap: 36, padding: '36px 40px', border: '2px solid var(--status-success)', background: 'var(--status-success-soft)' }}>
                    <div style={{ width: 120, height: 120, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--status-success)', flexShrink: 0 }}>
                      <div style={{ width: 100, height: 100, borderRadius: '50%', background: 'var(--status-success-soft)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                        <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 34, color: 'var(--ink-900)' }}>100<span style={{ fontSize: '.45em', verticalAlign: 'super' }}>%</span></span>
                      </div>
                    </div>
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
                      <span style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)' }}>Roadmap complete</span>
                      <span style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-700)', maxWidth: 520 }}>Your roadmap completion score is now 100%. Check your updated placement readiness.</span>
                      <div style={{ marginTop: 8 }}>
                        <Button size="sm" to="/dashboard">See my readiness</Button>
                      </div>
                    </div>
                  </div>
                )}

                <div className="cb-rm-stat-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                  <StatTile value={`${stats.completed} of ${stats.total}`} label="Milestones" />
                  <StatTile value={`${Math.round(stats.pct)}%`} label="Complete" />
                  <StatTile value={`${stats.remainingWeeks} wks`} label="Remaining" />
                  <StatTile value={`Week ${stats.weeksSinceStart}`} label={`Since ${fmtMonthDay(roadmap.startedAt)}`} />
                </div>

                <ProgressMeter value={stats.pct} max={100} tone="accent" />

                <section>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>All milestones</span>
                  <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                  <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, marginTop: 20 }}>
                    {milestones.map((m, idx) => {
                      const isCurrent = !m.isCompleted && idx === firstOpenIdx;
                      const isUpcoming = !m.isCompleted && !isCurrent;
                      return (
                        <MilestoneRow
                          key={m.id}
                          milestone={m}
                          index={idx}
                          isCurrent={isCurrent}
                          isUpcoming={isUpcoming}
                          expanded={expandedIds.has(m.id)}
                          onToggleExpand={() => toggleExpand(m.id)}
                          resources={resourcesByTitle[m.title]}
                          onComplete={() => handleComplete(m)}
                          completing={completingId === m.id}
                          rowError={rowErrorId === m.id ? rowErrorMessage : ''}
                        />
                      );
                    })}
                  </div>
                </section>

              </div>
            )}

          </div>
        </main>
      </div>

      {paceOpen && (
        <PaceDialog choice={paceChoice} onChoose={setPaceChoice} onClose={() => setPaceOpen(false)} onSave={savePace} />
      )}

      {toast.visible && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 60, maxWidth: 360 }}>
          <div style={{ background: 'var(--ink-900)', color: 'var(--bone-50)', padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, boxShadow: 'var(--shadow-menu)' }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</span>
            <span style={{ fontSize: 13, color: 'var(--bone-300)' }}>{toast.message}</span>
          </div>
        </div>
      )}

    </div>
  );
}
