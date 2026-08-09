import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Badge, Button, Icon, IconButton, Logo, Skeleton } from '../../components/ui';
import { getMyNotifications, markNotificationRead } from '../../api/notificationApi';
import { getTokenPayload, getDisplayName } from '../../utils/tokenUtils';
import './notifications.css';

const ROLE_LABEL = { STUDENT: 'Student', PLACEMENT_OFFICER: 'Placement officer', RECRUITER: 'Recruiter', ORG_ADMIN: 'Org admin', SUPER_ADMIN: 'Super admin', MENTOR: 'Mentor' };

const TYPE_BADGE = {
  RECOMMENDATION: { tone: 'accent', label: 'CAREER MATCH' },
  SUBSCRIPTION: { tone: 'info', label: 'BILLING' },
};
function typeBadge(type) {
  if (TYPE_BADGE[type]) return TYPE_BADGE[type];
  const label = String(type || '').replace(/[._-]+/g, ' ').trim().toUpperCase() || 'UPDATE';
  return { tone: 'default', label };
}

function pad2(n) { return String(n).padStart(2, '0'); }
function fmtClock(d) { return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`; }
const DAY_ABBR = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTH_ABBR = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
function dayStart(d) { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }

function formatWhen(iso, now) {
  const d = new Date(iso);
  const diffMin = Math.floor((now - d) / 60000);
  const sameDay = d.toDateString() === now.toDateString();
  if (diffMin < 60 && diffMin >= 0) return diffMin <= 0 ? 'Just now' : `${diffMin} minute${diffMin === 1 ? '' : 's'} ago`;
  if (sameDay) return `Today at ${fmtClock(d)}`;
  const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000);
  if (diffDays < 7) return `${DAY_ABBR[d.getDay()]} at ${fmtClock(d)}`;
  return `${d.getDate()} ${MONTH_ABBR[d.getMonth()]} at ${fmtClock(d)}`;
}
function groupKey(iso, now) {
  const d = new Date(iso);
  if (d.toDateString() === now.toDateString()) return 'today';
  const diffDays = Math.round((dayStart(now) - dayStart(d)) / 86400000);
  return diffDays < 7 ? 'thisWeek' : 'earlier';
}
function delay(ms) { return new Promise((res) => setTimeout(res, ms)); }

const GROUP_ORDER = [['today', 'Today'], ['thisWeek', 'This week'], ['earlier', 'Earlier']];

export default function NotificationsPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [markAllLoading, setMarkAllLoading] = useState(false);
  const [markingIds, setMarkingIds] = useState({});
  const [expandedIds, setExpandedIds] = useState({});
  const [rowsMounted, setRowsMounted] = useState(false);
  const role = getTokenPayload()?.role || 'STUDENT';
  const isStudent = role === 'STUDENT';
  const roleLabel = ROLE_LABEL[role] || 'Account';
  const userName = getDisplayName(roleLabel);

  const loadAll = useCallback(() => {
    setLoading(true); setError(false);
    getMyNotifications().then((list) => {
      setNotifications(list);
      setLoading(false);
      setTimeout(() => setRowsMounted(true), 160);
    }).catch(() => {
      setLoading(false); setError(true);
    });
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const markRead = useCallback((n) => {
    if (n.isRead === false) {
      if (markingIds[n.id]) return;
      setMarkingIds((m) => ({ ...m, [n.id]: true }));
      Promise.all([markNotificationRead(n.id).catch(() => null), delay(230)]).then(() => {
        setNotifications((list) => list.map((x) => (x.id === n.id ? { ...x, isRead: true, readAt: x.readAt || new Date().toISOString() } : x)));
        setMarkingIds((m) => { const next = { ...m }; delete next[n.id]; return next; });
      });
    } else {
      setExpandedIds((e) => ({ ...e, [n.id]: !e[n.id] }));
    }
  }, [markingIds]);

  const onMarkAllRead = useCallback(async () => {
    if (markAllLoading) return;
    const unreadIds = notifications.filter((n) => n.isRead === false).map((n) => n.id);
    if (!unreadIds.length) return;
    setMarkAllLoading(true);
    for (const id of unreadIds) {
      setMarkingIds((m) => ({ ...m, [id]: true }));
      // eslint-disable-next-line no-await-in-loop
      await markNotificationRead(id).catch(() => null);
      // eslint-disable-next-line no-await-in-loop
      await delay(50);
    }
    const now = new Date().toISOString();
    setNotifications((list) => list.map((n) => (unreadIds.includes(n.id) ? { ...n, isRead: true, readAt: n.readAt || now } : n)));
    setMarkingIds({}); setMarkAllLoading(false);
  }, [markAllLoading, notifications]);

  const now = new Date();
  const unreadCount = notifications.filter((n) => n.isRead === false && !markingIds[n.id]).length;

  // Keeps the old count visible during the badge's exit animation instead of snapping to blank.
  const lastUnreadLabel = useRef('0');
  if (unreadCount > 0) lastUnreadLabel.current = String(unreadCount);

  const sorted = [...notifications].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const groups = { today: [], thisWeek: [], earlier: [] };
  sorted.forEach((n) => groups[groupKey(n.createdAt, now)].push(n));

  const feedItems = [];
  let rowIndex = 0;
  GROUP_ORDER.forEach(([key, label]) => {
    const rows = groups[key];
    if (!rows.length) return;
    feedItems.push({ isHeader: true, key: 'h-' + key, label, delayMs: rowIndex * 30 + 40 });
    rows.forEach((n) => {
      feedItems.push({ isHeader: false, key: n.id, n, delayMs: rowIndex * 30 });
      rowIndex += 1;
    });
  });

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-no-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" variant="secondary" onClick={() => navigate(-1)} />
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
            <div className="cb-no-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{userName}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>{roleLabel}</span>
            </div>
          </div>
        </div>
      </header>

      <main style={{ minWidth: 0, background: 'var(--surface-page)' }}>
        <div style={{ maxWidth: 1160, margin: '0 auto' }}>

          <div className="cb-no-topbar" style={{ background: 'var(--surface-card)', borderBottom: '1px solid var(--line-hairline)', padding: '28px 32px', display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Notifications</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <h1 style={{ fontFamily: 'var(--font-display)', fontWeight: 400, fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0 }}>Inbox</h1>
                {(unreadCount > 0) && (
                  <span style={{ display: 'inline-flex', transition: 'transform 220ms cubic-bezier(.2,0,.2,1), opacity 220ms cubic-bezier(.2,0,.2,1)' }}>
                    <Badge tone="danger">{unreadCount > 99 ? '99+' : unreadCount}</Badge>
                  </span>
                )}
              </div>
            </div>
            {unreadCount > 0 && (
              <Button variant="ghost" size="sm" onClick={onMarkAllRead} disabled={markAllLoading} style={{ color: 'var(--ink-500)' }}>
                {markAllLoading ? 'Marking…' : 'Mark all read'}
              </Button>
            )}
          </div>

          {error && (
            <div style={{ padding: '20px 32px 0' }}>
              <div style={{ position: 'relative' }}>
                <Alert tone="danger" title="Could not load notifications" message="Pull down to refresh." />
                <button type="button" onClick={loadAll} style={{ position: 'absolute', top: 14, right: 16, border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 12, fontWeight: 500, color: 'var(--status-danger)', textDecoration: 'underline' }}>Retry</button>
              </div>
            </div>
          )}

          <div style={{ padding: '0 32px 64px' }}>

            {loading && (
              <div style={{ display: 'flex', flexDirection: 'column', marginTop: 1 }}>
                {[0, 1, 2, 3].map((i) => (
                  <div key={i} style={{ height: 72, borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', padding: '0 24px', boxSizing: 'border-box' }}>
                    <div style={{ width: '60%' }}><Skeleton height={36} /></div>
                  </div>
                ))}
              </div>
            )}

            {!loading && !error && notifications.length === 0 && (
              <div style={{ padding: '72px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, textAlign: 'center' }}>
                <Icon name="bell" size={48} style={{ color: 'var(--ink-300)' }} />
                <span style={{ fontFamily: 'var(--font-display)', fontSize: 24, color: 'var(--ink-600)' }}>Nothing here yet</span>
                <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-400)', maxWidth: 400, margin: 0 }}>
                  {isStudent
                    ? 'Your career match and welcome notifications will appear here once your assessment is complete.'
                    : 'Updates relevant to your account will appear here.'}
                </p>
                {isStudent && (
                  <Button variant="secondary" size="md" iconAfter="arrow-right" to="/assessment" style={{ marginTop: 8 }}>Take assessment</Button>
                )}
              </div>
            )}

            {!loading && !error && notifications.length > 0 && (
              <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)' }}>
                {feedItems.map((item) => item.isHeader ? (
                  <div key={item.key} style={{ padding: '8px 24px', background: 'var(--bone-100)', borderBottom: '1px solid var(--line-hairline)', fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)', opacity: rowsMounted ? 1 : 0, transform: rowsMounted ? 'translateY(0)' : 'translateY(4px)', transition: `opacity 140ms cubic-bezier(.2,0,.2,1) ${item.delayMs}ms, transform 140ms cubic-bezier(.2,0,.2,1) ${item.delayMs}ms` }}>{item.label}</div>
                ) : (
                  <NotificationRow key={item.key} n={item.n} now={now} delayMs={item.delayMs} marking={!!markingIds[item.n.id]} expanded={item.n.isRead === true && !!expandedIds[item.n.id]} rowsMounted={rowsMounted} onClick={() => markRead(item.n)} />
                ))}
              </div>
            )}

          </div>
        </div>
      </main>
    </div>
  );
}

function NotificationRow({ n, now, delayMs, marking, expanded, rowsMounted, onClick }) {
  const visuallyRead = n.isRead === true || marking;
  const badge = typeBadge(n.notificationType);
  return (
    <div onClick={onClick} style={{ position: 'relative', padding: '16px 24px 16px 48px', borderBottom: '1px solid var(--line-hairline)', display: 'flex', gap: 16, alignItems: 'flex-start', justifyContent: 'space-between', cursor: 'pointer', background: visuallyRead ? 'var(--bone-100)' : 'var(--surface-card)', opacity: rowsMounted ? 1 : 0, transition: `background 220ms cubic-bezier(.2,0,.2,1), opacity 140ms cubic-bezier(.2,0,.2,1) ${delayMs}ms` }}>
      <span style={{ position: 'absolute', left: 24, top: 22, width: 8, height: 8, borderRadius: '50%', background: 'var(--taupe-500)', opacity: visuallyRead ? 0 : 1, transition: 'opacity 140ms cubic-bezier(.2,0,.2,1)' }} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0, flex: 1 }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: visuallyRead ? 'var(--ink-600)' : 'var(--ink-900)', transition: 'color 220ms cubic-bezier(.2,0,.2,1)' }}>{n.title}</span>
        <p style={{ fontSize: 13, lineHeight: 1.5, color: 'var(--ink-500)', margin: 0, display: '-webkit-box', WebkitLineClamp: expanded ? 'unset' : 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{n.message}</p>
        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{formatWhen(n.createdAt, now)}</span>
        <div style={{ maxHeight: expanded ? 200 : 0, opacity: expanded ? 1 : 0, overflow: 'hidden', transition: 'max-height 220ms cubic-bezier(.2,0,.2,1), opacity 220ms cubic-bezier(.2,0,.2,1)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 6, paddingTop: 10, borderTop: '1px solid var(--line-hairline)' }}>
            {expanded && n.recommendationId != null && (
              <Link to="/recommendations" onClick={(e) => e.stopPropagation()} style={{ display: 'flex', alignItems: 'center', gap: 6, border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 13, fontWeight: 500, color: 'var(--taupe-700)', width: 'fit-content' }}>
                <Icon name="arrow-right" size={14} style={{ color: 'var(--taupe-600)' }} />
                View your career match
              </Link>
            )}
            {expanded && n.readAt && (
              <span style={{ fontSize: 11, fontStyle: 'italic', color: 'var(--ink-400)' }}>Read {formatWhen(n.readAt, now)}</span>
            )}
          </div>
        </div>
      </div>
      <Badge tone={badge.tone} style={{ flexShrink: 0 }}>{badge.label}</Badge>
    </div>
  );
}
