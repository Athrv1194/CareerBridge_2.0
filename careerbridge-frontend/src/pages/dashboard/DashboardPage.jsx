import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Badge, Button, Icon, IconButton, Input, Logo, MatchScore, ProgressMeter, ScoreRing, Skeleton,
  StatTile, Tag,
} from '../../components/ui';
import { getMyPrs } from '../../api/prsApi';
import { getMyRecommendation, getCareerCatalog } from '../../api/recommendationApi';
import { getMyRoadmap, completeMilestone } from '../../api/roadmapApi';
import { getMyNotifications, getUnreadCount, markNotificationRead } from '../../api/notificationApi';
import { getMyProfile } from '../../api/studentApi';
import './dashboard.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard', active: true },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

const NOTIF_ICON = {
  RECOMMENDATION_READY: 'sparkles',
  ROADMAP_MILESTONE: 'route',
  APPLICATION_STATUS: 'briefcase',
  SUBSCRIPTION: 'award',
  ASSESSMENT_COMPLETED: 'file-text',
};

function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function relativeTime(iso) {
  const d = new Date(iso);
  const hrs = (Date.now() - d.getTime()) / 3600000;
  if (hrs < 1) return 'now';
  if (hrs < 24) return `${Math.round(hrs)}h`;
  const days = Math.round(hrs / 24);
  if (days < 7) return ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getDay()];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${d.getDate()} ${months[d.getMonth()]}`;
}

function toneForScore(v) {
  if (v >= 80) return 'success';
  if (v >= 60) return 'accent';
  return 'warning';
}

// Ranks the four real PRS inputs and names the strongest two and the weakest one -- a genuine
// read of the student's own breakdown, not a canned line. Only meaningful once prs is loaded.
function prsSubtext(prs) {
  const items = [
    { short: 'assessment', full: 'Assessment coverage', score: prs.assessmentScore ?? 0 },
    { short: 'roadmap pace', full: 'Roadmap pace', score: prs.roadmapScore ?? 0 },
    { short: 'profile', full: 'Profile depth', score: prs.profileScore ?? 0 },
    { short: 'résumé', full: 'Résumé strength', score: prs.resumeScore ?? 0 },
  ];
  const sorted = [...items].sort((a, z) => z.score - a.score);
  return `Your ${sorted[0].short} and ${sorted[1].short} are strong. ${sorted[3].full} is the thing holding the score down.`;
}

function CompositionRow({ label, value, weightLabel, tone }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13 }}>
        <span style={{ color: 'var(--ink-800)' }}>{label}</span>
        <span className="cb-num" style={{ color: 'var(--ink-900)' }}>{Math.round(value)}%</span>
      </div>
      <ProgressMeter value={value} max={100} tone={tone} />
      <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>{weightLabel}</span>
    </div>
  );
}

function CareerMatchCard({ rank, careerName, matchPercentage, tags }) {
  const shown = tags.slice(0, 3);
  const overflow = tags.length > 3 ? `+${tags.length - 3} more` : null;
  return (
    <article style={{ background: 'var(--surface-card)', padding: '24px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between' }}>
        <span className="cb-num" style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Rank {String(rank).padStart(2, '0')}</span>
        <Badge tone="accent">AI matched</Badge>
      </div>
      <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)', margin: 0 }}>{careerName}</h3>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {shown.map((t) => <Tag key={t}>{t}</Tag>)}
        {overflow && <Tag>{overflow}</Tag>}
      </div>
      <div style={{ marginTop: 8 }}>
        <MatchScore value={Math.round(matchPercentage)} size="sm" />
      </div>
    </article>
  );
}

function MilestoneSnapshotItem({ title, description, state, days, onComplete, completing }) {
  const isCompleted = state === 'completed';
  const isCurrent = state === 'current';
  return (
    <div style={{
      display: 'flex', alignItems: 'flex-start', gap: 14, padding: '16px 18px',
      borderBottom: '1px solid var(--line-hairline)',
      borderLeft: `2px solid ${isCurrent ? 'var(--taupe-500)' : 'transparent'}`,
    }}
    >
      <div style={{
        width: 22, height: 22, borderRadius: '50%', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', marginTop: 2,
        ...(isCompleted
          ? { background: 'var(--ink-900)' }
          : isCurrent
            ? { border: '2px solid var(--ink-900)' }
            : { background: 'var(--bone-300)' }),
      }}
      >
        {isCompleted && <Icon name="check" size={12} style={{ color: 'var(--bone-50)' }} />}
      </div>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span style={{
          fontSize: 14, fontWeight: isCurrent ? 600 : 500,
          color: isCompleted ? 'var(--ink-400)' : 'var(--ink-900)',
          textDecoration: isCompleted ? 'line-through' : 'none',
        }}
        >
          {title}
        </span>
        {isCurrent && description && (
          <span style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)' }}>{description}</span>
        )}
        <span className="cb-num" style={{ fontSize: 11, color: 'var(--ink-400)' }}>{days} days</span>
      </div>
      {isCurrent && (
        <Button size="sm" disabled={completing} onClick={onComplete}>{completing ? 'Marking…' : 'Mark complete'}</Button>
      )}
    </div>
  );
}

function NotificationRow({ title, message, notificationType, isRead, timeLabel, onRead }) {
  return (
    <div
      onClick={isRead ? undefined : onRead}
      style={{
        display: 'flex', alignItems: 'flex-start', gap: 12, padding: '16px 18px',
        borderBottom: '1px solid var(--line-hairline)', cursor: isRead ? 'default' : 'pointer',
        background: isRead ? 'transparent' : 'var(--taupe-100)',
      }}
    >
      <div style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--bone-200)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <Icon name={NOTIF_ICON[notificationType] || 'sparkles'} size={13} />
      </div>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span style={{ fontSize: 13.5, fontWeight: isRead ? 400 : 600, color: 'var(--ink-900)' }}>{title}</span>
        <span style={{ fontSize: 12.5, lineHeight: 1.5, color: 'var(--ink-500)' }}>{message}</span>
      </div>
      <span className="cb-num" style={{ fontSize: 11, color: 'var(--ink-400)', flexShrink: 0 }}>{timeLabel}</span>
    </div>
  );
}

export default function DashboardPage() {
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [studentName, setStudentName] = useState('');
  const [profile, setProfile] = useState(null);
  const [prs, setPrs] = useState(null);
  const [rec, setRec] = useState(null);
  const [tagsByCareer, setTagsByCareer] = useState({});
  const [roadmap, setRoadmap] = useState(null);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [completingId, setCompletingId] = useState(null);
  const [toast, setToast] = useState({ visible: false, title: '', message: '' });
  const toastTimerRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      getMyPrs(), getMyRecommendation(), getCareerCatalog(), getMyRoadmap(),
      getMyNotifications(), getUnreadCount(), getMyProfile(),
    ]).then(([prsRes, recRes, catalogRes, roadmapRes, notifRes, unreadRes, profileRes]) => {
      if (cancelled) return;
      if (prsRes.status === 'fulfilled') setPrs(prsRes.value);
      if (recRes.status === 'fulfilled') setRec(recRes.value);
      if (catalogRes.status === 'fulfilled') {
        const map = {};
        catalogRes.value.forEach((c) => { map[c.name] = (c.requiredSkills || '').split(',').map((s) => s.trim()).filter(Boolean); });
        setTagsByCareer(map);
      }
      if (roadmapRes.status === 'fulfilled') setRoadmap(roadmapRes.value);
      if (notifRes.status === 'fulfilled') setNotifications(notifRes.value);
      if (unreadRes.status === 'fulfilled') setUnreadCount(unreadRes.value.unreadCount);
      if (profileRes.status === 'fulfilled' && profileRes.value) {
        setProfile(profileRes.value);
        setStudentName(`${profileRes.value.firstName || ''} ${profileRes.value.lastName || ''}`.trim());
      }
      setLoading(false);
    });
    return () => { cancelled = true; };
  }, []);

  useEffect(() => () => clearTimeout(toastTimerRef.current), []);

  const showToast = useCallback((title, message) => {
    clearTimeout(toastTimerRef.current);
    setToast({ visible: true, title, message });
    toastTimerRef.current = setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const scrollToNotifications = useCallback(() => {
    document.getElementById('notif-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, []);

  const handleReadNotification = useCallback((id) => {
    setNotifications((prev) => prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)));
    setUnreadCount((c) => Math.max(0, c - 1));
    markNotificationRead(id).catch(() => {});
  }, []);

  const handleCompleteMilestone = useCallback(async (milestoneId) => {
    setCompletingId(milestoneId);
    try {
      const updated = await completeMilestone(milestoneId);
      setRoadmap(updated);
      showToast('Milestone complete', "We'll update your readiness score within the hour.");
    } catch (e) {
      showToast("Couldn't complete that milestone", e.message);
    } finally {
      setCompletingId(null);
    }
  }, [showToast]);

  const sidebarWidth = navCollapsed ? '60px' : '248px';

  const hasRec = !!rec;
  const hasRoadmap = !!roadmap;
  const topCareerName = rec?.topCareerName;
  const remainingMilestones = roadmap ? Math.max(0, (roadmap.totalMilestones ?? 0) - (roadmap.completedMilestones ?? 0)) : 0;
  const roadmapPct = roadmap ? (roadmap.completionPercentage ?? 0) : 0;
  const topMatchPct = hasRec && rec.topRecommendations?.[0] ? Math.round(rec.topRecommendations[0].matchPercentage) : 0;

  const roadmapMilestones = roadmap?.milestones || [];
  const firstOpenIdx = roadmapMilestones.findIndex((m) => !m.isCompleted);
  const mapped = roadmapMilestones.map((m, idx) => ({
    ...m,
    state: m.isCompleted ? 'completed' : (idx === firstOpenIdx ? 'current' : 'upcoming'),
  }));
  const curIdx = mapped.findIndex((m) => m.state === 'current');
  const winStart = Math.max(0, curIdx < 0 ? 0 : curIdx - 1);
  const milestoneWindow = mapped.slice(winStart, winStart + 3);

  const showProfileAlert = profile && (profile.profileCompletionPercentage ?? 100) < 80;
  const unreadTone = unreadCount === 0 ? 'default' : unreadCount <= 2 ? 'accent' : 'warning';

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-db-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <div className="cb-db-search" style={{ flex: 1, display: 'flex', justifyContent: 'center', padding: '0 20px', minWidth: 0 }}>
          <div style={{ width: '100%', maxWidth: 440 }}>
            <Input placeholder="Search careers, roadmap steps, opportunities" value="" onChange={() => {}} />
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={scrollToNotifications} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-50)', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', lineHeight: 1, pointerEvents: 'none' }}>
                {unreadCount}
              </span>
            )}
          </div>
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Icon name="user" size={15} />
            </div>
            <div className="cb-db-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
        </div>
      </header>

      <div className="cb-db-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside style={{ borderRight: '1px solid var(--line-hairline)', position: 'sticky', top: 64, height: 'calc(100vh - 64px)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${navCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-db-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
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
                {!navCollapsed && <span className="cb-db-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          {!navCollapsed && (
            <div className="cb-db-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>Roadmap pacing and coach follow-ups need Plus.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers your top 3 matches. Upgrade for the full roadmap, unlimited coach sessions and résumé exports.</p>
                <Link to="/" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        <main style={{ minWidth: 0, background: 'var(--surface-page)' }}>
          <div className="cb-db-main-pad" style={{ maxWidth: 1160, margin: '0 auto', padding: '32px 32px 60px', display: 'flex', flexDirection: 'column', gap: 30 }}>

            {loading && <Skeleton height={400} />}

            {!loading && (
              <div className="cb-db-fade" style={{ display: 'flex', flexDirection: 'column', gap: 30 }}>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>
                      Today · {studentName ? `Welcome back, ${studentName.split(' ')[0]}` : 'Welcome back'}
                    </span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span onClick={scrollToNotifications} style={{ cursor: 'pointer' }}>
                        <Badge tone={unreadTone}>{unreadCount} unread</Badge>
                      </span>
                      {hasRoadmap ? (
                        <Button size="sm" iconAfter="arrow-right" to="/roadmap">Continue roadmap</Button>
                      ) : hasRec ? (
                        <Button size="sm" iconAfter="arrow-right" to="/recommendations">Build my roadmap</Button>
                      ) : (
                        <Button size="sm" iconAfter="arrow-right" to="/assessment">Start assessment</Button>
                      )}
                    </div>
                  </div>

                  {hasRoadmap && (
                    <h1 className="cb-db-hero" style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1.04, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                      {remainingMilestones} milestones stand between you and a <i>{topCareerName || roadmap.careerName}</i> shortlist.
                    </h1>
                  )}
                  {!hasRoadmap && hasRec && (
                    <h1 className="cb-db-hero" style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1.04, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                      Your top match is <i>{topCareerName}</i>. Build your roadmap to see what's next.
                    </h1>
                  )}
                  {!hasRoadmap && !hasRec && (
                    <h1 className="cb-db-hero" style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1.04, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                      Take your assessment to find your top career matches.
                    </h1>
                  )}
                  {prs && (
                    <p style={{ fontSize: 14, lineHeight: 1.65, color: 'var(--ink-600)', margin: 0, maxWidth: 640 }}>{prsSubtext(prs)}</p>
                  )}
                </div>

                {showProfileAlert && (
                  <div style={{ display: 'flex', alignItems: 'center', gap: 20, padding: '18px 22px', background: 'var(--status-warning-soft)', border: '1px solid var(--status-warning)', flexWrap: 'wrap' }}>
                    <Icon name="triangle-alert" size={18} style={{ color: 'var(--status-warning)' }} />
                    <div style={{ flex: 1, minWidth: 200 }}>
                      <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>Your profile is {Math.round(profile.profileCompletionPercentage)}% complete</div>
                      <div style={{ fontSize: 13, color: 'var(--ink-600)', marginTop: 2 }}>A fuller profile improves your placement readiness score and how recruiters see you.</div>
                    </div>
                    <Button size="sm" variant="secondary" to="/profile">Complete profile</Button>
                  </div>
                )}

                <section>
                  {!prs && <Skeleton height={220} />}
                  {prs && (
                    <div className="cb-db-prs-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                      <div style={{ background: 'var(--surface-card)', padding: '34px 30px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 16 }}>
                        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Placement readiness</span>
                        <ScoreRing value={Math.round(prs.totalScore)} grade={prs.grade} size="lg" />
                        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>Last updated {fmtDate(prs.lastUpdatedAt)}</span>
                      </div>
                      <div style={{ background: 'var(--surface-card)', padding: '34px 32px', display: 'flex', flexDirection: 'column', gap: 16 }}>
                        <div>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Score composition</span>
                          <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0, margin: '6px 0 0' }} />
                        </div>
                        <CompositionRow label="Assessment" value={prs.assessmentScore ?? 0} tone={toneForScore(prs.assessmentScore ?? 0)} weightLabel={`Weight ${prs.breakdown?.assessmentWeight ?? 40}% of total`} />
                        <CompositionRow label="Roadmap progress" value={prs.roadmapScore ?? 0} tone={toneForScore(prs.roadmapScore ?? 0)} weightLabel={`Weight ${prs.breakdown?.roadmapWeight ?? 30}% of total`} />
                        <CompositionRow label="Profile depth" value={prs.profileScore ?? 0} tone={toneForScore(prs.profileScore ?? 0)} weightLabel={`Weight ${prs.breakdown?.profileWeight ?? 20}% of total`} />
                        <CompositionRow label="Résumé" value={prs.resumeScore ?? 0} tone={toneForScore(prs.resumeScore ?? 0)} weightLabel={`Weight ${prs.breakdown?.resumeWeight ?? 10}% of total`} />
                        <p style={{ fontSize: 12, fontStyle: 'italic', lineHeight: 1.6, color: 'var(--ink-400)', margin: '2px 0 0' }}>Readiness is derived from events, never self-reported. Completing a milestone moves it within the hour.</p>
                      </div>
                    </div>
                  )}
                </section>

                <section>
                  <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 20 }}>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Your top career matches</span>
                    <Link to="/recommendations" style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-700)' }}>All 7 careers →</Link>
                  </div>
                  <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                  {hasRec ? (
                    <div className="cb-db-career-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', borderTop: 0, marginTop: 20 }}>
                      {rec.topRecommendations.slice(0, 3).map((c) => (
                        <CareerMatchCard key={c.careerName} rank={c.rank} careerName={c.careerName} matchPercentage={c.matchPercentage} tags={tagsByCareer[c.careerName] || []} />
                      ))}
                    </div>
                  ) : (
                    <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, marginTop: 20, padding: '40px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center' }}>
                      <span style={{ fontSize: 14, color: 'var(--text-secondary)' }}>No matches yet — finish your assessment to see them.</span>
                      <Button size="sm" to="/assessment" iconAfter="arrow-right">Start my assessment</Button>
                    </div>
                  )}
                </section>

                <section id="notif-panel" className="cb-db-lower-grid" style={{ display: 'grid', alignItems: 'start' }}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 20 }}>
                      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Your roadmap</span>
                      <Link to="/roadmap" style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-700)' }}>Full roadmap →</Link>
                    </div>
                    <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                    {hasRoadmap ? (
                      <div style={{ marginTop: 20, display: 'flex', flexDirection: 'column', gap: 14 }}>
                        <span className="cb-num" style={{ fontSize: 20, fontWeight: 600, color: 'var(--ink-900)' }}>{roadmap.completedMilestones} of {roadmap.totalMilestones}</span>
                        <ProgressMeter value={roadmapPct} max={100} tone="ink" />
                        <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-page)', marginTop: 6 }}>
                          {milestoneWindow.map((m) => (
                            <MilestoneSnapshotItem
                              key={m.id}
                              title={m.title}
                              description={m.description}
                              state={m.state}
                              days={m.estimatedDays}
                              completing={completingId === m.id}
                              onComplete={() => handleCompleteMilestone(m.id)}
                            />
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div style={{ marginTop: 20, padding: '32px 20px', border: '1px solid var(--line-hairline)', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center' }}>
                        <span style={{ fontSize: 13.5, color: 'var(--text-secondary)' }}>
                          {hasRec ? 'No roadmap yet — build one from your matches.' : 'No roadmap yet.'}
                        </span>
                        <Button size="sm" to={hasRec ? '/recommendations' : '/assessment'} iconAfter="arrow-right">
                          {hasRec ? 'See my matches' : 'Start my assessment'}
                        </Button>
                      </div>
                    )}
                  </div>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 20 }}>
                      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Notifications</span>
                      <Badge tone={unreadTone}>{unreadCount} unread</Badge>
                    </div>
                    <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                    {notifications.length === 0 ? (
                      <div style={{ marginTop: 20, padding: '32px 20px', border: '1px solid var(--line-hairline)', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                        <Icon name="bell" size={20} />
                        <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>No notifications yet</span>
                        <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Your first one arrives when your recommendation is ready.</span>
                      </div>
                    ) : (
                      <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', marginTop: 20 }}>
                        {notifications.slice(0, 5).map((n) => (
                          <NotificationRow
                            key={n.id}
                            title={n.title}
                            message={n.message}
                            notificationType={n.notificationType}
                            isRead={n.isRead}
                            timeLabel={relativeTime(n.createdAt)}
                            onRead={() => handleReadNotification(n.id)}
                          />
                        ))}
                      </div>
                    )}
                  </div>
                </section>

              </div>
            )}

          </div>

          {!loading && (
            <section style={{ background: 'var(--ink-900)' }}>
              <div style={{ maxWidth: 1160, margin: '0 auto', padding: '0 32px' }}>
                <div className="cb-db-stat-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)' }}>
                  <StatTile tone="inverse" value={prs ? `${Math.round(prs.totalScore)}%` : '—'} label="Readiness score" />
                  <StatTile tone="inverse" value={hasRoadmap ? `${Math.round(roadmapPct)}%` : '—'} label="Roadmap done" />
                  <StatTile tone="inverse" value={hasRec ? `${topMatchPct}%` : '—'} label="Top match" />
                  <StatTile tone="inverse" value={String(unreadCount)} label="Unread alerts" />
                </div>
              </div>
            </section>
          )}
        </main>
      </div>

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
