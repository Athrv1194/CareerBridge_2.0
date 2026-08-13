import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Field, Icon, IconButton, Input, Logo, Skeleton, Textarea,
} from '../../components/ui';
import {
  browseMentors, bookSession, getMySessionsAsStudent, cancelSession, submitSessionReview,
} from '../../api/mentorApi';
import { getCareerCatalog } from '../../api/recommendationApi';
import { getUnreadCount } from '../../api/notificationApi';
import { getMyProfile, getAvatarBlobUrl } from '../../api/studentApi';
import { clearTokens } from '../../utils/tokenUtils';
import { getNavCollapsed, setNavCollapsed as persistNavCollapsed } from '../../utils/navPrefs';
import { useMaxWidth } from '../../hooks/useBreakpoint';
import './mentors.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'users', label: 'Mentors', to: '/mentors', active: true },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

const SUB_TABS = [
  { value: 'PENDING', label: 'Pending' },
  { value: 'UPCOMING', label: 'Upcoming' },
  { value: 'HISTORY', label: 'History' },
];
const EMPTY_SUB_TAB_MESSAGE = {
  PENDING: 'No pending requests. Browse mentors to get started.',
  UPCOMING: 'No confirmed sessions yet.',
  HISTORY: 'No past sessions.',
};
const DURATION_OPTIONS = [30, 45, 60, 90];

function initialsOf(first, last) { return `${(first || '')[0] || ''}${(last || '')[0] || ''}`.toUpperCase(); }
function starGlyphs(rating) {
  const r = Math.round(rating || 0);
  return { filled: '★'.repeat(r), hollow: '☆'.repeat(5 - r) };
}
function fmtSessionDate(iso) {
  const d = new Date(iso);
  const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  let h = d.getHours(); const ampm = h >= 12 ? 'PM' : 'AM'; h = h % 12 || 12;
  const mins = String(d.getMinutes()).padStart(2, '0');
  return `${days[d.getDay()]}, ${d.getDate()} ${months[d.getMonth()]} ${d.getFullYear()} · ${h}:${mins} ${ampm}`;
}
function toDatetimeLocalMin() {
  const d = new Date(Date.now() + 5 * 60000);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function AnimatedWords({ text, tag: Tag2, style, revealed, className }) {
  return (
    <Tag2 style={style} className={className}>
      {text.split(' ').map((word, i) => (
        // eslint-disable-next-line react/no-array-index-key
        <span key={i} style={{
          display: 'inline-block', opacity: revealed ? 1 : 0, transform: revealed ? 'translateY(0)' : 'translateY(24px)',
          transition: 'opacity 420ms cubic-bezier(.2,0,.2,1), transform 420ms cubic-bezier(.2,0,.2,1)', transitionDelay: `${i * 80}ms`,
        }}
        >
          {word}&nbsp;
        </span>
      ))}
    </Tag2>
  );
}

function MentorCard({ mentor, index, revealed, onBook }) {
  const chips = mentor.expertiseAreas || [];
  const shown = chips.slice(0, 3);
  const extra = chips.length - shown.length;
  const chipList = extra > 0 ? [...shown, `+${extra} more`] : shown;
  const hasRating = mentor.averageRating != null;
  const delay = `${index * 60}ms`;
  return (
    <div style={{
      background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: 24, display: 'flex', flexDirection: 'column', gap: 14,
      opacity: revealed ? 1 : 0, transform: revealed ? 'translateY(0)' : 'translateY(20px)',
      transition: 'opacity 700ms cubic-bezier(.2,0,.2,1), transform 700ms cubic-bezier(.2,0,.2,1)', transitionDelay: delay,
    }}
    >
      <div style={{
        width: 56, height: 56, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-100)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 18, fontWeight: 500,
        transform: revealed ? 'scale(1)' : 'scale(0.6)', transition: 'transform 420ms cubic-bezier(.2,0,.2,1)', transitionDelay: delay,
      }}
      >
        {initialsOf(mentor.firstName, mentor.lastName)}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>{mentor.firstName} {mentor.lastName}</h3>
        <span style={{ fontSize: 13, color: 'var(--ink-700)' }}>{mentor.currentRole} @ {mentor.currentCompany}</span>
        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{mentor.yearsOfExperience} years experience</span>
      </div>
      {hasRating ? (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ position: 'relative', width: 80, height: 16, fontSize: 15, lineHeight: '16px', letterSpacing: 2 }}>
            <div style={{ position: 'absolute', inset: 0, color: 'var(--ink-300)', whiteSpace: 'nowrap' }}>★★★★★</div>
            <div style={{
              position: 'absolute', inset: 0, color: 'var(--ink-900)', whiteSpace: 'nowrap', overflow: 'hidden',
              width: `${Math.min(100, Math.max(0, (mentor.averageRating / 5) * 100))}%`,
              transition: 'width 420ms cubic-bezier(.2,0,.2,1)', transitionDelay: delay,
            }}
            >★★★★★
            </div>
          </div>
          <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>({mentor.sessionsCompleted || 0} sessions)</span>
        </div>
      ) : (
        <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 13, color: 'var(--ink-400)' }}>New mentor</span>
      )}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {chipList.map((chip) => <span key={chip} style={{ background: 'var(--bone-300)', color: 'var(--ink-700)', fontSize: 11, padding: '4px 9px' }}>{chip}</span>)}
      </div>
      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{(mentor.careerPaths || []).join(', ')}</span>
      <Button variant="primary" size="md" fullWidth onClick={() => onBook(mentor)} style={{ marginTop: 'auto' }}>Book a Session</Button>
    </div>
  );
}

export default function MentorsPage() {
  const navigate = useNavigate();
  const [navCollapsed, setNavCollapsed] = useState(getNavCollapsed);
  const [unreadCount, setUnreadCount] = useState(0);
  const [studentName, setStudentName] = useState('');
  const [avatarSrc, setAvatarSrc] = useState('');

  const [activeTab, setActiveTab] = useState('browse');
  const [tabOpacity, setTabOpacity] = useState(1);
  const [heroIn, setHeroIn] = useState(false);

  const [careerPathOptions, setCareerPathOptions] = useState([]);
  const [careerPath, setCareerPath] = useState('');
  const [expertise, setExpertise] = useState('');
  const [browseLoading, setBrowseLoading] = useState(true);
  const [browseError, setBrowseError] = useState('');
  const [mentors, setMentors] = useState([]);
  const [mentorsRevealed, setMentorsRevealed] = useState(false);
  const debounceRef = useRef(null);

  const [activeSubTab, setActiveSubTab] = useState('PENDING');
  const [sessionsLoaded, setSessionsLoaded] = useState(false);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessionsError, setSessionsError] = useState('');
  const [sessions, setSessions] = useState([]);
  const [sessionsRevealed, setSessionsRevealed] = useState(false);
  const [cancelConfirmId, setCancelConfirmId] = useState(null);
  const [cancellingId, setCancellingId] = useState(null);
  const [reviewDrafts, setReviewDrafts] = useState({});
  const [reviewSubmitted, setReviewSubmitted] = useState({});
  const [reviewSubmitting, setReviewSubmitting] = useState({});
  const [reviewErrors, setReviewErrors] = useState({});
  const [reviewHoverStar, setReviewHoverStar] = useState({});
  const [now, setNow] = useState(() => Date.now());

  const [bookingOpen, setBookingOpen] = useState(false);
  const [bookingVisible, setBookingVisible] = useState(false);
  const [bookingMentor, setBookingMentor] = useState(null);
  const [bookingTopic, setBookingTopic] = useState('');
  const [bookingScheduledAt, setBookingScheduledAt] = useState('');
  const [bookingDuration, setBookingDuration] = useState(null);
  const [scheduledAtError, setScheduledAtError] = useState('');
  const [bookingSubmitError, setBookingSubmitError] = useState('');
  const [bookingSubmitting, setBookingSubmitting] = useState(false);

  const [toast, setToast] = useState({ visible: false, title: '', message: '' });
  const toastTimerRef = useRef(null);

  const showToast = useCallback((title, message) => {
    clearTimeout(toastTimerRef.current);
    setToast({ visible: true, title, message });
    toastTimerRef.current = setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const loadMentors = useCallback((cp, exp) => {
    setBrowseLoading(true); setBrowseError(''); setMentorsRevealed(false);
    browseMentors({ careerPath: cp, expertise: exp })
      .then((list) => { setMentors(list); setBrowseLoading(false); setTimeout(() => setMentorsRevealed(true), 20); })
      .catch((e) => { setBrowseError(e.message); setBrowseLoading(false); });
  }, []);

  const loadSessions = useCallback(() => {
    setSessionsLoading(true); setSessionsError(''); setSessionsRevealed(false);
    getMySessionsAsStudent()
      .then((list) => { setSessions(list); setSessionsLoaded(true); setSessionsLoading(false); setTimeout(() => setSessionsRevealed(true), 20); })
      .catch((e) => { setSessionsError(e.message); setSessionsLoading(false); });
  }, []);

  useEffect(() => {
    getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    getMyProfile().then((p) => { if (p) setStudentName(`${p.firstName || ''} ${p.lastName || ''}`.trim()); }).catch(() => {});
    getAvatarBlobUrl().then((url) => { if (url) setAvatarSrc(url); }).catch(() => {});
    getCareerCatalog().then((list) => setCareerPathOptions(list.map((c) => c.name))).catch(() => {});
    loadMentors();
    requestAnimationFrame(() => requestAnimationFrame(() => setHeroIn(true)));
    const clock = setInterval(() => setNow(Date.now()), 60000);
    return () => { clearTimeout(debounceRef.current); clearInterval(clock); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => () => clearTimeout(toastTimerRef.current), []);

  const onTabChange = (val) => {
    setTabOpacity(0);
    setTimeout(() => {
      setActiveTab(val); setTabOpacity(1);
      if (val === 'sessions' && !sessionsLoaded) loadSessions();
    }, 220);
  };

  const onCareerPathChange = (e) => {
    const v = e.target.value;
    setCareerPath(v);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => loadMentors(v, ''), 300);
  };
  const onExpertiseChange = (e) => {
    const v = e.target.value;
    setExpertise(v);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => loadMentors('', v), 300);
  };
  const clearFilters = () => { setCareerPath(''); setExpertise(''); loadMentors('', ''); };
  const hasActiveFilter = !!(careerPath || expertise);

  const openBooking = (mentor) => {
    setBookingMentor(mentor); setBookingOpen(true); setBookingVisible(false);
    setBookingTopic(''); setBookingScheduledAt(''); setBookingDuration(null);
    setScheduledAtError(''); setBookingSubmitError('');
    requestAnimationFrame(() => requestAnimationFrame(() => setBookingVisible(true)));
  };
  const closeBooking = () => {
    setBookingVisible(false);
    setTimeout(() => setBookingOpen(false), 420);
  };

  const onSubmitBooking = async () => {
    if (!bookingTopic.trim()) { setBookingSubmitError('Tell your mentor what you want to discuss.'); return; }
    if (!bookingScheduledAt) { setScheduledAtError('Choose a date and time.'); return; }
    if (new Date(bookingScheduledAt).getTime() <= Date.now()) { setScheduledAtError('Please choose a future date and time.'); return; }
    setBookingSubmitting(true); setBookingSubmitError(''); setScheduledAtError('');
    try {
      const newSession = await bookSession({
        mentorProfileId: bookingMentor.id, topic: bookingTopic.trim(),
        scheduledAt: bookingScheduledAt, durationMinutes: bookingDuration,
      });
      setBookingSubmitting(false);
      closeBooking();
      showToast('Session request sent!', "You'll be notified when the mentor responds.");
      if (sessionsLoaded) setSessions((prev) => [newSession, ...prev]);
    } catch (e) {
      setBookingSubmitting(false);
      setBookingSubmitError(e.status === 409 ? "You already have an active session with this mentor. You can cancel it yourself from My Sessions while it's still pending -- once a mentor confirms a time, only they can cancel it." : e.message);
    }
  };

  const askCancel = (id) => setCancelConfirmId(id);
  const dismissCancel = () => setCancelConfirmId(null);
  const confirmCancel = (id) => {
    setCancellingId(id);
    cancelSession(id)
      .then((updated) => {
        setSessions((prev) => prev.map((x) => (x.id === id ? updated : x)));
        setCancellingId(null); setCancelConfirmId(null);
      })
      .catch((e) => { setCancellingId(null); setCancelConfirmId(null); setSessionsError(e.message); });
  };

  const setReviewRating = (id, rating) => setReviewDrafts((s) => ({ ...s, [id]: { ...(s[id] || {}), rating } }));
  const setReviewComment = (id, comment) => setReviewDrafts((s) => ({ ...s, [id]: { ...(s[id] || {}), comment } }));
  const setReviewHover = (id, n) => setReviewHoverStar((s) => ({ ...s, [id]: n }));
  const doSubmitReview = (id) => {
    const draft = reviewDrafts[id] || {};
    if (!draft.rating) { setReviewErrors((s) => ({ ...s, [id]: 'Pick a star rating first.' })); return; }
    setReviewSubmitting((s) => ({ ...s, [id]: true })); setReviewErrors((s) => ({ ...s, [id]: '' }));
    submitSessionReview(id, { rating: draft.rating, comment: draft.comment || null })
      .then(() => {
        setReviewSubmitting((s) => ({ ...s, [id]: false }));
        setReviewSubmitted((s) => ({ ...s, [id]: { rating: draft.rating, comment: draft.comment || '' } }));
        // The mentor's averageRating just changed server-side -- refetch so the card's top-of-row
        // stars (read off the embedded mentorProfile snapshot) stop showing the pre-review number.
        loadSessions();
      })
      .catch((e) => {
        setReviewSubmitting((s) => ({ ...s, [id]: false }));
        if (e.status === 409) setReviewSubmitted((s) => ({ ...s, [id]: { rating: draft.rating, comment: draft.comment || '' } }));
        else setReviewErrors((s) => ({ ...s, [id]: e.message }));
      });
  };

  const sidebarWidth = navCollapsed ? '60px' : '248px';
  const isPhone = useMaxWidth('phone');
  const [drawerOpen, setDrawerOpen] = useState(false);
  // Below 560px the rail is an overlay drawer with room for full labels, so the
  // persisted collapse preference -- a tablet icon-rail affordance -- must not apply.
  const railCollapsed = isPhone ? false : navCollapsed;

  const sessionsForSub = sessions.filter((sess) => {
    if (activeSubTab === 'PENDING') return sess.status === 'REQUESTED';
    if (activeSubTab === 'UPCOMING') return sess.status === 'ACCEPTED';
    return sess.status === 'COMPLETED' || sess.status === 'DECLINED' || sess.status === 'CANCELLED';
  });
  const requestsCount = sessions.filter((x) => x.status === 'REQUESTED').length;
  const upcomingCount = sessions.filter((x) => x.status === 'ACCEPTED').length;
  const historyCount = sessions.filter((x) => ['COMPLETED', 'DECLINED', 'CANCELLED'].includes(x.status)).length;
  const subTabCounts = { PENDING: requestsCount, UPCOMING: upcomingCount, HISTORY: historyCount };

  const STATUS_TONE = { REQUESTED: 'warning', ACCEPTED: 'info', COMPLETED: 'default', DECLINED: 'default', CANCELLED: 'default' };
  const STATUS_LABEL = { REQUESTED: 'Pending Response', ACCEPTED: 'Confirmed', COMPLETED: 'Completed', DECLINED: 'Declined', CANCELLED: 'Cancelled' };

  const bm = bookingMentor || {};
  const bmChips = (bm.expertiseAreas || []).slice(0, 3);
  const bmExtra = (bm.expertiseAreas || []).length - bmChips.length;
  const bmStars = starGlyphs(bm.averageRating);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-mt-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
          <IconButton
            className="cb-app-drawer-toggle"
            icon={drawerOpen ? 'x' : 'sliders-horizontal'}
            label={drawerOpen ? 'Close menu' : 'Open menu'}
            onClick={() => setDrawerOpen((v) => !v)}
          />
          <Logo size={32} />
        </div>
        <div className="cb-app-header-actions" style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--status-danger)', color: '#FCFBF9', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', boxSizing: 'border-box' }}>
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
          <div className="cb-app-header-divider" style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, overflow: 'hidden' }}>
              {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={15} />}
            </div>
            <div className="cb-mt-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
          <div className="cb-app-header-divider" style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <span className="cb-app-header-logout">
            <Button variant="ghost" size="sm" onClick={() => { clearTokens(); navigate('/'); }}>Log out</Button>
          </span>
        </div>
      </header>

      <div className="cb-mt-shell cb-app-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside className={`cb-app-rail${drawerOpen ? ' is-open' : ''}`} style={{ borderRight: '1px solid var(--line-hairline)', position: 'sticky', top: 64, height: 'calc(100vh - 64px)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${railCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-mt-toggle-row" style={{ display: 'flex', justifyContent: railCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
            <IconButton
              icon="chevron-right"
              label={navCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
              onClick={() => setNavCollapsed((v) => { persistNavCollapsed(!v); return !v; })}
              iconStyle={{ transform: navCollapsed ? 'none' : 'rotate(180deg)', transition: 'transform 200ms ease' }}
            />
          </div>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                title={item.label}
                onClick={() => setDrawerOpen(false)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 11,
                  justifyContent: railCollapsed ? 'center' : 'flex-start',
                  padding: railCollapsed ? '10px 0' : '10px 14px', fontSize: 13,
                  letterSpacing: '.06em', textTransform: 'uppercase',
                  background: item.active ? 'var(--ink-900)' : 'transparent',
                  color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)', border: 'none',
                }}
              >
                <Icon name={item.icon} size={16} style={{ color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)' }} />
                {!railCollapsed && <span className="cb-mt-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          <div className="cb-app-drawer-logout" style={{ paddingTop: 12 }}>
            <Button variant="ghost" size="sm" fullWidth onClick={() => { clearTokens(); navigate('/'); }}>Log out</Button>
          </div>
          {!railCollapsed && (
            <div className="cb-mt-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>Unlimited mentor sessions need Plus.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers your top 3 matches. Upgrade for unlimited mentor booking, coach follow-ups and résumé exports.</p>
                <Link to="/plans" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        {drawerOpen && (
          <button type="button" className="cb-app-scrim" aria-label="Close menu" onClick={() => setDrawerOpen(false)} />
        )}

        <main style={{ minWidth: 0, background: 'var(--surface-page)' }}>
          <div className="cb-mt-main-pad" style={{ maxWidth: 1160, margin: '0 auto', padding: '32px 32px 64px', boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 28 }}>

            <div style={{ display: 'flex', gap: 4 }}>
              <button
                type="button"
                onClick={() => onTabChange('browse')}
                style={{ border: 0, borderBottom: `2px solid ${activeTab === 'browse' ? 'var(--ink-900)' : 'transparent'}`, background: 'none', padding: '12px 4px', marginRight: 20, fontSize: 14, fontWeight: 500, cursor: 'pointer', color: activeTab === 'browse' ? 'var(--ink-900)' : 'var(--ink-400)' }}
              >
                Browse Mentors
              </button>
              <button
                type="button"
                onClick={() => onTabChange('sessions')}
                style={{ border: 0, borderBottom: `2px solid ${activeTab === 'sessions' ? 'var(--ink-900)' : 'transparent'}`, background: 'none', padding: '12px 4px', fontSize: 14, fontWeight: 500, cursor: 'pointer', color: activeTab === 'sessions' ? 'var(--ink-900)' : 'var(--ink-400)' }}
              >
                My Sessions
              </button>
            </div>

            <div style={{ opacity: tabOpacity, transition: 'opacity 220ms cubic-bezier(.2,0,.2,1)', minHeight: 400 }}>

              {activeTab === 'browse' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 28 }}>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                    <AnimatedWords text="Find Your Mentor" tag="h1" revealed={heroIn} style={{ fontFamily: 'var(--font-display)', fontSize: 40, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }} className="cb-mt-hero" />
                    <AnimatedWords text="Connect with professionals who've walked your path." tag="p" revealed={heroIn} style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--ink-700)', margin: 0 }} />
                  </div>

                  <div className="cb-mt-filterbar" style={{ position: 'sticky', top: 64, zIndex: 20, background: 'var(--surface-page)', padding: '14px 0', display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', borderBottom: '1px solid var(--line-hairline)' }}>
                    <div style={{ width: 240 }}>
                      <select
                        value={careerPath}
                        onChange={onCareerPathChange}
                        style={{ width: '100%', boxSizing: 'border-box', padding: '9px 10px', fontSize: 13, fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-sm)', outline: 'none', cursor: 'pointer' }}
                      >
                        <option value="">Filter by career path…</option>
                        {careerPathOptions.map((name) => <option key={name} value={name}>{name}</option>)}
                      </select>
                    </div>
                    <div style={{ width: 240 }}>
                      <Input placeholder="Filter by expertise…" value={expertise} onChange={onExpertiseChange} disabled={!!careerPath} />
                    </div>
                    {hasActiveFilter && (
                      <button type="button" onClick={clearFilters} style={{ display: 'flex', alignItems: 'center', gap: 5, border: 0, background: 'none', padding: '4px 6px', cursor: 'pointer', fontSize: 12, color: 'var(--ink-500)' }}>
                        <Icon name="x" size={14} />Clear filters
                      </button>
                    )}
                  </div>

                  {browseError && <Alert tone="danger" title="Couldn't load mentors" message={browseError} />}

                  {browseLoading && (
                    <div className="cb-mt-mentor-grid" style={{ display: 'grid', gap: 20 }}>
                      {[1, 2, 3].map((k) => (
                        <div key={k} style={{ background: 'var(--bone-200)', border: '1px solid var(--line-hairline)', padding: 24, display: 'flex', flexDirection: 'column', gap: 14 }}>
                          <Skeleton height={56} />
                          <Skeleton height={80} />
                          <Skeleton height={38} />
                        </div>
                      ))}
                    </div>
                  )}

                  {!browseLoading && mentors.length === 0 && (
                    <div style={{ padding: '60px 20px', textAlign: 'center' }}>
                      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 20, color: 'var(--ink-400)' }}>No mentors available right now.</span>
                      {hasActiveFilter && <div style={{ marginTop: 8, fontSize: 13, color: 'var(--ink-400)' }}>Try clearing your filters.</div>}
                    </div>
                  )}

                  {!browseLoading && mentors.length > 0 && (
                    <div className="cb-mt-mentor-grid" style={{ display: 'grid', gap: 20 }}>
                      {mentors.map((m, idx) => (
                        <MentorCard key={m.id} mentor={m} index={idx} revealed={mentorsRevealed} onBook={openBooking} />
                      ))}
                    </div>
                  )}

                </div>
              )}

              {activeTab === 'sessions' && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>

                  <div className="cb-scroll-x" style={{ display: 'flex', gap: 4, overflowX: 'auto' }}>
                    {SUB_TABS.map((it) => (
                      <button
                        key={it.value}
                        type="button"
                        onClick={() => setActiveSubTab(it.value)}
                        style={{ border: 0, borderBottom: `2px solid ${activeSubTab === it.value ? 'var(--ink-900)' : 'transparent'}`, background: 'none', padding: '10px 14px', fontSize: 13, fontWeight: 500, cursor: 'pointer', color: activeSubTab === it.value ? 'var(--ink-900)' : 'var(--ink-400)', whiteSpace: 'nowrap', display: 'flex', alignItems: 'center', gap: 8 }}
                      >
                        {it.label}
                        {subTabCounts[it.value] > 0 && <Badge>{subTabCounts[it.value]}</Badge>}
                      </button>
                    ))}
                  </div>

                  {sessionsError && <Alert tone="danger" title="Couldn't load sessions" message={sessionsError} />}

                  {sessionsLoading && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                      {[1, 2, 3].map((k) => (
                        <div key={k} style={{ background: 'var(--bone-200)', border: '1px solid var(--line-hairline)', padding: 20, display: 'flex', gap: 16 }}>
                          <Skeleton height={44} />
                        </div>
                      ))}
                    </div>
                  )}

                  {!sessionsLoading && sessionsForSub.length === 0 && (
                    <div style={{ padding: '60px 20px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                      <span style={{ fontFamily: 'var(--font-display)', fontStyle: 'italic', fontSize: 20, color: 'var(--ink-400)' }}>{EMPTY_SUB_TAB_MESSAGE[activeSubTab]}</span>
                      {activeSubTab === 'PENDING' && (
                        <button type="button" onClick={() => onTabChange('browse')} style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 13, color: 'var(--ink-900)', textDecoration: 'underline' }}>Browse Mentors</button>
                      )}
                    </div>
                  )}

                  {!sessionsLoading && sessionsForSub.length > 0 && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                      {sessionsForSub.map((sess, idx) => {
                        const mp = sess.mentorProfile || {};
                        const stars = starGlyphs(mp.averageRating);
                        const isPending = sess.status === 'REQUESTED';
                        const isUpcoming = sess.status === 'ACCEPTED';
                        const isCompleted = sess.status === 'COMPLETED';
                        const scheduled = new Date(sess.scheduledAt);
                        const diffMs = scheduled.getTime() - now;
                        const diffMin = diffMs / 60000;
                        const startingSoon = isUpcoming && diffMin > 0 && diffMin <= 60;
                        let countdownText = '';
                        if (isUpcoming) {
                          const diffH = diffMs / 3600000; const diffD = diffH / 24;
                          if (diffMs <= 0) countdownText = 'Starting now';
                          else if (diffD >= 1) countdownText = `In ${Math.round(diffD)} day${Math.round(diffD) === 1 ? '' : 's'}`;
                          else countdownText = `In ${Math.max(1, Math.round(diffH))} hour${Math.round(diffH) === 1 ? '' : 's'}`;
                        }
                        const draft = reviewDrafts[sess.id] || {};
                        const hoverN = reviewHoverStar[sess.id] || 0;
                        const submittedInfo = reviewSubmitted[sess.id];
                        const reviewedStars = submittedInfo ? starGlyphs(submittedInfo.rating) : { filled: '', hollow: '' };
                        const showMentorNotes = !!sess.mentorNotes && !isCompleted;

                        return (
                          <div key={sess.id} style={{
                            position: 'relative', background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: '22px 24px', display: 'flex', flexDirection: 'column', gap: 16,
                            opacity: sessionsRevealed ? 1 : 0, transform: sessionsRevealed ? 'translateY(0)' : 'translateY(20px)',
                            transition: 'opacity 700ms cubic-bezier(.2,0,.2,1), transform 700ms cubic-bezier(.2,0,.2,1)', transitionDelay: `${idx * 40}ms`,
                          }}
                          >
                            <div style={{ position: 'absolute', top: 20, right: 22 }}>
                              <Badge tone={STATUS_TONE[sess.status]}>{STATUS_LABEL[sess.status]}</Badge>
                            </div>

                            <div style={{ display: 'flex', gap: 14, alignItems: 'center', paddingRight: 130 }}>
                              <div style={{ width: 44, height: 44, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 500, flexShrink: 0 }}>
                                {initialsOf(mp.firstName, mp.lastName)}
                              </div>
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                                <h4 style={{ fontFamily: 'var(--font-display)', fontSize: 17, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>{mp.firstName || ''} {mp.lastName || ''}</h4>
                                <span style={{ fontSize: 13, color: 'var(--ink-700)' }}>{mp.currentRole || ''} @ {mp.currentCompany || ''}</span>
                              </div>
                              {mp.averageRating != null && (
                                <div style={{ fontSize: 13, letterSpacing: 1, color: 'var(--ink-900)', marginLeft: 'auto', flexShrink: 0 }}>
                                  {stars.filled}<span style={{ color: 'var(--ink-300)' }}>{stars.hollow}</span>
                                </div>
                              )}
                            </div>

                            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                              <span style={{ fontFamily: 'var(--font-display)', fontSize: 17, color: 'var(--ink-900)', fontWeight: 500 }}>{sess.topic}</span>
                              <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap' }}>
                                <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--ink-600)' }}><Icon name="calendar" size={14} />{fmtSessionDate(sess.scheduledAt)}</span>
                                <span style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, color: 'var(--ink-600)' }}><Icon name="history" size={14} />{sess.durationMinutes} min</span>
                              </div>
                            </div>

                            {showMentorNotes && (
                              <div style={{ fontSize: 13, lineHeight: 1.5, color: 'var(--ink-700)', fontStyle: 'italic' }}>
                                <span style={{ color: 'var(--ink-400)', fontStyle: 'normal', textTransform: 'uppercase', fontSize: 10, letterSpacing: '.1em', marginRight: 8 }}>Mentor&apos;s note</span>{sess.mentorNotes}
                              </div>
                            )}

                            {isPending && (
                              <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingTop: 8, borderTop: '1px solid var(--line-hairline)' }}>
                                {cancelConfirmId !== sess.id ? (
                                  <Button variant="ghost" size="sm" onClick={() => askCancel(sess.id)}>Cancel Request</Button>
                                ) : (
                                  <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: 'var(--bone-100)', border: '1px solid var(--line-strong)', padding: '8px 12px' }}>
                                    <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>Cancel this request?</span>
                                    <Button variant="secondary" size="sm" disabled={cancellingId === sess.id} onClick={() => confirmCancel(sess.id)}>Yes</Button>
                                    <Button variant="ghost" size="sm" onClick={dismissCancel}>No</Button>
                                  </div>
                                )}
                              </div>
                            )}

                            {isUpcoming && (
                              <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingTop: 8, borderTop: '1px solid var(--line-hairline)' }}>
                                <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{countdownText}</span>
                                {startingSoon && (
                                  <span style={{ background: 'var(--status-warning-soft)', color: 'var(--status-warning)', fontSize: 11, fontWeight: 500, letterSpacing: '.08em', textTransform: 'uppercase', padding: '4px 10px', borderRadius: 'var(--radius-pill)' }}>
                                    Starting in ~{Math.max(1, Math.round(diffMin))} min
                                  </span>
                                )}
                                {sess.meetingLink && (
                                  <a href={sess.meetingLink} target="_blank" rel="noopener noreferrer" style={{ border: 0, marginLeft: 'auto' }}>
                                    <Button variant="primary" size="sm" style={startingSoon ? { animation: 'cbMtPulse 2s ease-in-out infinite' } : undefined}>Join Meeting</Button>
                                  </a>
                                )}
                              </div>
                            )}

                            {isCompleted && (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, paddingTop: 14, borderTop: '1px solid var(--line-hairline)' }}>
                                {submittedInfo ? (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                      <div style={{ fontSize: 15, letterSpacing: 2, color: 'var(--ink-900)' }}>{reviewedStars.filled}<span style={{ color: 'var(--ink-300)' }}>{reviewedStars.hollow}</span></div>
                                      <span style={{ fontSize: 11, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Reviewed ✓</span>
                                    </div>
                                    {submittedInfo.comment && <p style={{ fontSize: 13, color: 'var(--ink-700)', margin: 0 }}>{submittedInfo.comment}</p>}
                                  </div>
                                ) : (
                                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                                    <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>How was your session?</span>
                                    <div style={{ display: 'flex', gap: 6 }}>
                                      {[1, 2, 3, 4, 5].map((n) => (
                                        <button
                                          key={n}
                                          type="button"
                                          onClick={() => setReviewRating(sess.id, n)}
                                          onMouseEnter={() => setReviewHover(sess.id, n)}
                                          onMouseLeave={() => setReviewHover(sess.id, 0)}
                                          style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 24, lineHeight: 1, color: n <= (hoverN || draft.rating || 0) ? 'var(--ink-900)' : 'var(--ink-300)', transition: 'color 80ms cubic-bezier(.2,0,.2,1)' }}
                                        >
                                          {n <= (hoverN || draft.rating || 0) ? '★' : '☆'}
                                        </button>
                                      ))}
                                    </div>
                                    <div style={{ width: '100%', maxWidth: 420 }}>
                                      <Textarea rows={2} value={draft.comment || ''} onChange={(e) => setReviewComment(sess.id, e.target.value)} placeholder="Share your experience (optional)" />
                                    </div>
                                    {reviewErrors[sess.id] && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{reviewErrors[sess.id]}</span>}
                                    <Button variant="primary" size="sm" disabled={!draft.rating || reviewSubmitting[sess.id]} onClick={() => doSubmitReview(sess.id)} style={{ width: 'fit-content' }}>
                                      {reviewSubmitting[sess.id] ? 'Submitting…' : 'Submit Review'}
                                    </Button>
                                  </div>
                                )}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              )}

            </div>
          </div>
        </main>
      </div>

      {bookingOpen && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 70, background: 'var(--bone-100)', transform: bookingVisible ? 'translateY(0)' : 'translateY(100%)', transition: 'transform 420ms cubic-bezier(.2,0,.2,1)', overflowY: 'auto' }}>
          <div style={{ maxWidth: 640, margin: '0 auto', padding: '32px 32px 80px', display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <IconButton icon="x" label="Close" onClick={closeBooking} />
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{bm.firstName} {bm.lastName}</h2>
              <span style={{ fontSize: 14, color: 'var(--ink-700)' }}>{bm.currentRole} @ {bm.currentCompany}</span>
            </div>

            <div style={{ background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: '18px 20px', display: 'flex', gap: 14, alignItems: 'flex-start' }}>
              <div style={{ width: 44, height: 44, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 14, fontWeight: 500, flexShrink: 0 }}>
                {initialsOf(bm.firstName, bm.lastName)}
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  {bm.averageRating != null && (
                    <span style={{ fontSize: 13, letterSpacing: 1, color: 'var(--ink-900)' }}>{bmStars.filled}<span style={{ color: 'var(--ink-300)' }}>{bmStars.hollow}</span></span>
                  )}
                  <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>({bm.sessionsCompleted || 0} sessions)</span>
                </div>
                <p style={{ fontSize: 13, lineHeight: 1.5, color: 'var(--ink-700)', margin: 0 }}>{bm.bio || ''}</p>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {(bmExtra > 0 ? [...bmChips, `+${bmExtra} more`] : bmChips).map((chip) => (
                    <span key={chip} style={{ background: 'var(--bone-300)', color: 'var(--ink-700)', fontSize: 11, padding: '3px 8px' }}>{chip}</span>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <Field label="What do you want to discuss?">
                <Input value={bookingTopic} onChange={(e) => setBookingTopic(e.target.value)} placeholder="e.g. Breaking into backend engineering" />
              </Field>

              <Field label="Preferred date & time">
                <input
                  type="datetime-local"
                  value={bookingScheduledAt}
                  min={toDatetimeLocalMin()}
                  onChange={(e) => { setBookingScheduledAt(e.target.value); setScheduledAtError(''); }}
                  style={{ width: '100%', fontFamily: 'var(--font-sans)', fontSize: 14, color: 'var(--ink-900)', background: 'var(--surface-card)', border: '1px solid var(--line-strong)', borderRadius: 0, padding: '11px 13px', outline: 'none', boxSizing: 'border-box' }}
                />
              </Field>
              {scheduledAtError && <span style={{ fontSize: 12, color: 'var(--status-danger)', marginTop: -12 }}>{scheduledAtError}</span>}

              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-700)' }}>Duration</span>
                <div style={{ display: 'flex', gap: 8 }}>
                  {DURATION_OPTIONS.map((d) => {
                    const selected = bookingDuration === d;
                    return (
                      <button
                        key={d}
                        type="button"
                        onClick={() => setBookingDuration(d)}
                        style={{ flex: 1, padding: '10px 0', textAlign: 'center', fontSize: 12, letterSpacing: '.04em', cursor: 'pointer', background: selected ? 'var(--ink-900)' : 'var(--bone-200)', color: selected ? 'var(--bone-100)' : 'var(--ink-700)', border: `1px solid ${selected ? 'var(--ink-900)' : 'var(--ink-300)'}`, transition: 'background-color 140ms cubic-bezier(.2,0,.2,1)' }}
                      >
                        {d} min
                      </button>
                    );
                  })}
                </div>
              </div>

              {bookingSubmitError && <Alert tone="danger" message={bookingSubmitError} />}

              <Button variant="primary" size="lg" fullWidth disabled={bookingSubmitting} onClick={onSubmitBooking}>{bookingSubmitting ? 'Sending…' : 'Request Session'}</Button>
            </div>
          </div>
        </div>
      )}

      {toast.visible && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 80, maxWidth: 360 }}>
          <div style={{ background: 'var(--ink-900)', color: 'var(--bone-50)', padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, boxShadow: 'var(--shadow-menu)' }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</span>
            <span style={{ fontSize: 13, color: 'var(--bone-300)' }}>{toast.message}</span>
          </div>
        </div>
      )}

    </div>
  );
}
