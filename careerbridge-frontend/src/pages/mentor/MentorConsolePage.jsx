import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge, Button, Field, Icon, IconButton, Input, Logo, revealStyle, Skeleton, Switch, Tag, Textarea,
} from '../../components/ui';
import {
  getMyMentorProfile, createMentorProfile, updateMentorProfile,
  getMySessionsAsMentor, respondToSession, completeSession, cancelSession,
} from '../../api/mentorApi';
import { getUnreadCount } from '../../api/notificationApi';
import { getTokenPayload, getDisplayName, clearTokens } from '../../utils/tokenUtils';
import './mentor-console.css';

const STATUS_BADGE = {
  REQUESTED: { plain: false, tone: 'warning', label: 'New Request' },
  ACCEPTED: { plain: false, tone: 'inverse', label: 'Confirmed' },
  COMPLETED: { plain: false, tone: 'default', label: 'Completed' },
  DECLINED: { plain: true, text: 'Declined' },
  CANCELLED: { plain: true, text: 'Cancelled' },
};
const EMPTY_MESSAGES = { REQUESTED: 'No new session requests.', ACCEPTED: 'No confirmed sessions.', HISTORY: 'No past sessions yet.' };
const emptyDraft = () => ({
  firstName: '', lastName: '', bio: '', currentCompany: '', currentRole: '', yearsOfExperience: '0',
  expertiseAreas: [], expertiseInput: '', careerPaths: [], careerInput: '', linkedinUrl: '', isAvailable: true,
});
function draftFromProfile(p) {
  return {
    firstName: p.firstName || '', lastName: p.lastName || '', bio: p.bio || '',
    currentCompany: p.currentCompany || '', currentRole: p.currentRole || '',
    yearsOfExperience: String(p.yearsOfExperience ?? 0),
    expertiseAreas: p.expertiseAreas ? p.expertiseAreas.slice() : [], expertiseInput: '',
    careerPaths: p.careerPaths ? p.careerPaths.slice() : [], careerInput: '',
    linkedinUrl: p.linkedinUrl || '', isAvailable: p.isAvailable !== false,
  };
}

function fmtScheduled(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
    + ' · ' + d.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
}
function initials(first, last) { return `${(first || '')[0] || ''}${(last || '')[0] || ''}`.toUpperCase() || 'M'; }
function clampYears(v) { const n = Number(v); return Number.isNaN(n) ? 0 : Math.max(0, Math.min(50, n)); }

function ChipField({ label, hint, required, input, chips, onInputChange, onKeyDown, onRemove, placeholder }) {
  return (
    <Field label={label} hint={hint}>
      <Input value={input} onChange={onInputChange} onKeyDown={onKeyDown} placeholder={placeholder} />
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
        {chips.map((label2, i) => <Tag key={label2 + i} selected onRemove={() => onRemove(i)}>{label2}</Tag>)}
      </div>
      {required && chips.length === 0 && <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>Required — press Enter or comma to add.</span>}
    </Field>
  );
}

function YearsStepper({ value, onChange }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
      <button type="button" onClick={() => onChange(String(clampYears(Number(value) - 1)))} style={{ width: 32, height: 32, flexShrink: 0, border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', color: 'var(--ink-700)', fontSize: 16, cursor: 'pointer' }}>−</button>
      <Input type="number" value={value} onChange={(e) => onChange(String(clampYears(e.target.value)))} />
      <button type="button" onClick={() => onChange(String(clampYears(Number(value) + 1)))} style={{ width: 32, height: 32, flexShrink: 0, border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', color: 'var(--ink-700)', fontSize: 16, cursor: 'pointer' }}>+</button>
    </div>
  );
}

function ProfileForm({ draft, setDraft, onSubmit, saving, error, submitLabel, showNameFields }) {
  const field = (key) => (e) => setDraft((d) => ({ ...d, [key]: e.target.value }));
  const chipHandlers = (key) => ({
    input: draft[key + 'Input'],
    chips: draft[key],
    onInputChange: (e) => setDraft((d) => ({ ...d, [key + 'Input']: e.target.value })),
    onKeyDown: (e) => {
      if (e.key !== 'Enter' && e.key !== ',') return;
      e.preventDefault();
      const raw = (draft[key + 'Input'] || '').replace(/,+$/, '').trim();
      if (!raw || draft[key].includes(raw)) { setDraft((d) => ({ ...d, [key + 'Input']: '' })); return; }
      setDraft((d) => ({ ...d, [key]: [...d[key], raw], [key + 'Input']: '' }));
    },
    onRemove: (idx) => setDraft((d) => ({ ...d, [key]: d[key].filter((_, i) => i !== idx) })),
  });

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {showNameFields && (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
          <Field label="First name" required><Input value={draft.firstName} onChange={field('firstName')} placeholder="Ananya" /></Field>
          <Field label="Last name" required><Input value={draft.lastName} onChange={field('lastName')} placeholder="Rao" /></Field>
        </div>
      )}
      <Field label="Bio" hint="Optional"><Textarea rows={4} value={draft.bio} onChange={field('bio')} placeholder="A sentence or two on what you help students with." /></Field>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
        <Field label="Current company" required><Input value={draft.currentCompany} onChange={field('currentCompany')} placeholder="Zylo Systems" /></Field>
        <Field label="Current role" required><Input value={draft.currentRole} onChange={field('currentRole')} placeholder="Staff Engineer" /></Field>
      </div>
      <Field label="Years of experience" required><YearsStepper value={draft.yearsOfExperience} onChange={(v) => setDraft((d) => ({ ...d, yearsOfExperience: v }))} /></Field>
      <ChipField label="Areas of expertise" required placeholder="System Design" {...chipHandlers('expertiseAreas')} />
      <ChipField label="Career paths" required hint="e.g. Full Stack Developer, Data Scientist" placeholder="Full Stack Developer" {...chipHandlers('careerPaths')} />
      <Field label="LinkedIn URL"><Input type="url" value={draft.linkedinUrl} onChange={field('linkedinUrl')} placeholder="https://linkedin.com/in/..." /></Field>
      {!showNameFields && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0' }}>
          <Switch checked={draft.isAvailable} onChange={(v) => setDraft((d) => ({ ...d, isAvailable: v }))} label="Accepting Session Requests" />
        </div>
      )}
      {error && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{error}</span>}
      <Button variant="primary" size="md" fullWidth={showNameFields} disabled={saving} onClick={onSubmit}>{saving ? 'Saving…' : submitLabel}</Button>
    </div>
  );
}

// Tag in the shared design system takes no onClick -- these two tags in the history view are
// genuinely interactive (open the meeting link, toggle notes), so they're a plain button styled to match.
function ClickTag({ children, onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '6px 12px', fontSize: 13, color: 'var(--ink-900)', background: 'var(--taupe-100)', borderRadius: 'var(--radius-pill)', border: 0, cursor: 'pointer', font: 'inherit' }}
    >
      {children}
    </button>
  );
}

function SessionCard({ session, index, revealed, respondOpenId, respondDraft, respondError, respondSaving, declineConfirmId,
  onAcceptClick, onDeclineClick, onDeclineYes, onDeclineNo, onRespondField, onConfirmAccept,
  onMarkComplete, onCancelSession, notesOpenIds, onToggleNotes }) {
  const s = session;
  const badge = STATUS_BADGE[s.status] || STATUS_BADGE.CANCELLED;
  const isRequested = s.status === 'REQUESTED';
  const isAccepted = s.status === 'ACCEPTED';
  const isCompleted = s.status === 'COMPLETED';
  const respondOpen = respondOpenId === s.id;
  const declining = declineConfirmId === s.id;
  const notesOpen = notesOpenIds.has(s.id);

  return (
    <div style={{
      background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: 18, display: 'flex', flexDirection: 'column', gap: 10,
      ...revealStyle(revealed, index),
    }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <span style={{ fontFamily: 'var(--font-display)', fontSize: 19, lineHeight: 1.25, color: 'var(--ink-900)', fontWeight: 400 }}>{s.topic}</span>
        {badge.plain
          ? <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-400)', flexShrink: 0, paddingTop: 2 }}>{badge.text}</span>
          : <Badge tone={badge.tone} style={{ flexShrink: 0 }}>{badge.label}</Badge>}
      </div>
      <span style={{ fontSize: 12, color: 'var(--ink-500)' }}>Student #{s.studentId}</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Icon name="history" size={14} />
        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-600)' }}>{fmtScheduled(s.scheduledAt)}</span>
      </div>
      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{s.durationMinutes} min</span>

      {isRequested && (
        <>
          {declining ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, paddingTop: 6, borderTop: '1px solid var(--line-hairline)', marginTop: 4 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-700)' }}>Decline this request?</span>
              <Button variant="secondary" size="sm" onClick={() => onDeclineYes(s)}>Yes</Button>
              <Button variant="ghost" size="sm" onClick={onDeclineNo}>No</Button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 10, paddingTop: 6, borderTop: '1px solid var(--line-hairline)', marginTop: 4 }}>
              <Button variant="primary" size="sm" onClick={() => onAcceptClick(s)}>Accept</Button>
              <Button variant="ghost" size="sm" onClick={() => onDeclineClick(s)}>Decline</Button>
            </div>
          )}
          {respondOpen && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10, paddingTop: 14 }}>
              <Field label="Meeting link" required><Input type="url" value={respondDraft.meetingLink} onChange={(e) => onRespondField('meetingLink', e)} placeholder="https://meet.careerbridge.io/..." /></Field>
              <Field label="Mentor notes" hint="Optional"><Textarea rows={3} value={respondDraft.mentorNotes} onChange={(e) => onRespondField('mentorNotes', e)} /></Field>
              {respondError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{respondError}</span>}
              <Button variant="primary" size="sm" disabled={respondSaving} onClick={() => onConfirmAccept(s)}>{respondSaving ? 'Confirming…' : 'Confirm'}</Button>
            </div>
          )}
        </>
      )}

      {isAccepted && (
        <div style={{ display: 'flex', gap: 10, paddingTop: 6, borderTop: '1px solid var(--line-hairline)', marginTop: 4 }}>
          <Button variant="primary" size="sm" onClick={() => onMarkComplete(s)}>Mark Complete</Button>
          <Button variant="ghost" size="sm" onClick={() => onCancelSession(s)}>Cancel</Button>
        </div>
      )}

      {isCompleted && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, paddingTop: 6, borderTop: '1px solid var(--line-hairline)', marginTop: 4 }}>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {s.meetingLink && <ClickTag onClick={() => window.open(s.meetingLink, '_blank')}>Meeting Link →</ClickTag>}
            {s.mentorNotes && <ClickTag onClick={() => onToggleNotes(s.id)}>{notesOpen ? 'Notes ▴' : 'Notes ▾'}</ClickTag>}
          </div>
          {notesOpen && s.mentorNotes && <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-700)', margin: '4px 0 0' }}>{s.mentorNotes}</p>}
        </div>
      )}
    </div>
  );
}

export default function MentorConsolePage() {
  const navigate = useNavigate();
  const [mentorName, setMentorName] = useState('Mentor');
  const [unreadCount, setUnreadCount] = useState(0);
  const [banner, setBanner] = useState('');

  const [profileLoading, setProfileLoading] = useState(true);
  const [profile, setProfile] = useState(null); // null = not loaded/none
  const [hasProfile, setHasProfile] = useState(false);
  const [setupDraft, setSetupDraft] = useState(emptyDraft());
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');

  const [editing, setEditing] = useState(false);
  const [editDraft, setEditDraft] = useState(emptyDraft());
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState('');

  const [sessions, setSessions] = useState([]);
  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('REQUESTED');
  const [respondOpenId, setRespondOpenId] = useState(null);
  const [respondDraft, setRespondDraft] = useState({ meetingLink: '', mentorNotes: '' });
  const [respondSaving, setRespondSaving] = useState(false);
  const [respondError, setRespondError] = useState('');
  const [declineConfirmId, setDeclineConfirmId] = useState(null);
  const [notesOpenIds, setNotesOpenIds] = useState(new Set());
  const [profileIn, setProfileIn] = useState(false);
  const [sessionsIn, setSessionsIn] = useState(false);

  const showBanner = (msg) => setBanner(msg);

  const loadProfile = () => {
    setProfileLoading(true);
    getMyMentorProfile()
      .then((p) => {
        if (p) { setProfile(p); setHasProfile(true); }
        else { setHasProfile(false); setSetupDraft(emptyDraft()); }
      })
      .catch((e) => showBanner(e.message))
      .finally(() => { setProfileLoading(false); setTimeout(() => setProfileIn(true), 20); });
  };

  const loadSessions = () => {
    setSessionsLoading(true);
    getMySessionsAsMentor()
      .then(setSessions)
      .catch((e) => { setSessions([]); showBanner(e.message); })
      .finally(() => { setSessionsLoading(false); setTimeout(() => setSessionsIn(true), 20); });
  };

  useEffect(() => {
    const payload = getTokenPayload();
    if (!payload || payload.role !== 'MENTOR') { navigate('/login', { replace: true }); return; }
    setMentorName(getDisplayName('Mentor'));
    getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    loadProfile();
    loadSessions();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navigate]);

  const onLogout = () => { clearTokens(); navigate('/login'); };

  const submitSetup = () => {
    const d = setupDraft;
    if (!d.firstName || !d.lastName || !d.currentCompany || !d.currentRole || !d.expertiseAreas.length || !d.careerPaths.length) {
      setCreateError('Please fill all required fields, including at least one expertise area and career path.');
      return;
    }
    setCreating(true); setCreateError('');
    createMentorProfile({
      firstName: d.firstName, lastName: d.lastName, bio: d.bio || null,
      currentCompany: d.currentCompany, currentRole: d.currentRole, yearsOfExperience: Number(d.yearsOfExperience),
      expertiseAreas: d.expertiseAreas.join(','), careerPaths: d.careerPaths.join(','), linkedinUrl: d.linkedinUrl || null,
    })
      .then((saved) => { setProfile(saved); setHasProfile(true); setCreating(false); })
      .catch((e) => { setCreateError(e.status === 409 ? 'Profile already exists.' : e.message); setCreating(false); });
  };

  const toggleAvailability = (next) => {
    const prev = profile.isAvailable;
    setProfile((p) => ({ ...p, isAvailable: next }));
    updateMentorProfile({ isAvailable: next })
      .then((saved) => setProfile(saved))
      .catch((e) => { setProfile((p) => ({ ...p, isAvailable: prev })); showBanner(e.message); });
  };

  const startEdit = () => { setEditDraft(draftFromProfile(profile)); setEditError(''); setEditing(true); };
  const submitEdit = () => {
    const d = editDraft;
    if (!d.currentCompany || !d.currentRole) { setEditError('Current company and current role are required.'); return; }
    setEditSaving(true); setEditError('');
    updateMentorProfile({
      bio: d.bio, currentCompany: d.currentCompany, currentRole: d.currentRole, yearsOfExperience: Number(d.yearsOfExperience),
      expertiseAreas: d.expertiseAreas.join(','), careerPaths: d.careerPaths.join(','), linkedinUrl: d.linkedinUrl || null,
      isAvailable: d.isAvailable,
    })
      .then((saved) => { setProfile(saved); setEditing(false); setEditSaving(false); })
      .catch((e) => { setEditError(e.message); setEditSaving(false); });
  };

  const openAccept = (s) => { setRespondOpenId(s.id); setRespondDraft({ meetingLink: '', mentorNotes: '' }); setRespondError(''); };
  const onRespondField = (field, e) => setRespondDraft((d) => ({ ...d, [field]: e.target.value }));
  const confirmAccept = (s) => {
    if (!respondDraft.meetingLink.trim()) { setRespondError('Meeting link is required.'); return; }
    setRespondSaving(true); setRespondError('');
    respondToSession(s.id, { action: 'ACCEPT', meetingLink: respondDraft.meetingLink, mentorNotes: respondDraft.mentorNotes || null })
      .then((updated) => {
        setSessions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
        setRespondOpenId(null); setRespondSaving(false);
      })
      .catch((e) => { setRespondError(e.message); setRespondSaving(false); });
  };
  const declineYes = (s) => {
    setDeclineConfirmId(null);
    respondToSession(s.id, { action: 'DECLINE' })
      .then((updated) => setSessions((prev) => prev.map((x) => (x.id === updated.id ? updated : x))))
      .catch((e) => showBanner(e.message));
  };
  const onMarkComplete = (s) => {
    completeSession(s.id)
      .then((updated) => {
        setSessions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)));
        setProfile((p) => (p ? { ...p, sessionsCompleted: (p.sessionsCompleted || 0) + 1 } : p));
      })
      .catch((e) => showBanner(e.message));
  };
  const onCancelSession = (s) => {
    cancelSession(s.id)
      .then((updated) => setSessions((prev) => prev.map((x) => (x.id === updated.id ? updated : x))))
      .catch((e) => showBanner(e.message));
  };
  const onToggleNotes = (id) => setNotesOpenIds((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  const requestsCount = sessions.filter((x) => x.status === 'REQUESTED').length;
  const upcomingCount = sessions.filter((x) => x.status === 'ACCEPTED').length;
  const historyCount = sessions.filter((x) => ['COMPLETED', 'DECLINED', 'CANCELLED'].includes(x.status)).length;
  const visible = useMemo(() => {
    if (activeTab === 'REQUESTED') return sessions.filter((x) => x.status === 'REQUESTED');
    if (activeTab === 'ACCEPTED') return sessions.filter((x) => x.status === 'ACCEPTED');
    return sessions.filter((x) => ['COMPLETED', 'DECLINED', 'CANCELLED'].includes(x.status));
  }, [sessions, activeTab]);

  useEffect(() => {
    if (sessionsLoading) return;
    setSessionsIn(false);
    const t = setTimeout(() => setSessionsIn(true), 20);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab]);

  // Ratings are 1-5, so a returned 0 (the entity's un-reviewed default) means "no ratings yet" too, same as null.
  const hasRating = profile && profile.averageRating != null && Number(profile.averageRating) > 0;
  const ratingLabel = hasRating ? Number(profile.averageRating).toFixed(1) : null;
  const starsFillPct = hasRating ? (Number(profile.averageRating) / 5) * 100 : 0;

  return (
    <div style={{ minHeight: '100vh', background: '#F5F3EF', color: '#2A2927', fontFamily: 'var(--font-sans)' }}>
      <header className="mc-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: '#F5F3EF', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <span className="mc-eyebrow-sep" style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)', paddingLeft: 24, borderLeft: '1px solid var(--line-hairline)' }}>Mentor</span>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--status-danger)', color: '#FCFBF9', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', boxSizing: 'border-box' }}>
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div className="mc-avatar-name" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 600, flexShrink: 0 }}>
              {initials(mentorName.split(' ')[0], mentorName.split(' ')[1])}
            </div>
            <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{mentorName}</span>
          </div>
          <Button variant="ghost" size="sm" onClick={onLogout}>Log out</Button>
        </div>
      </header>

      {banner && (
        <div style={{ background: 'var(--ink-700)', color: 'var(--bone-100)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, padding: '12px 28px', fontSize: 13 }}>
          <span>{banner}</span>
          <button type="button" onClick={() => setBanner('')} style={{ background: 'none', border: 0, color: 'var(--bone-100)', cursor: 'pointer', fontSize: 18, lineHeight: 1, padding: 0 }}>×</button>
        </div>
      )}

      <main className="mc-main" style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 90px', boxSizing: 'border-box' }}>
        <div className="mc-grid" style={{ display: 'grid', gap: 32, alignItems: 'start' }}>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 20, minWidth: 0 }}>
            {profileLoading && (
              <div style={{ background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: 28, display: 'flex', flexDirection: 'column', gap: 18, alignItems: 'center' }}>
                <Skeleton height={72} />
                <Skeleton height={60} />
              </div>
            )}

            {!profileLoading && !hasProfile && (
              <div style={{
                background: 'var(--bone-200)', border: '1px solid var(--line-strong)', padding: 32, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 16,
                ...revealStyle(profileIn, 0, { distance: 20 }),
              }}
              >
                <div>
                  <h2 className="mc-name-title" style={{ fontFamily: 'var(--font-display)', fontSize: 30, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>Set Up Your Profile</h2>
                  <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: '8px 0 0' }}>Students will find you through your profile.</p>
                </div>
                <ProfileForm draft={setupDraft} setDraft={setSetupDraft} onSubmit={submitSetup} saving={creating} error={createError} submitLabel="Create Profile" showNameFields />
              </div>
            )}

            {!profileLoading && hasProfile && !editing && (
              <div style={{
                background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 28, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 16, alignItems: 'center', textAlign: 'center',
                ...revealStyle(profileIn, 0, { distance: 20 }),
              }}
              >
                <div style={{ width: 72, height: 72, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 24, fontWeight: 600, flexShrink: 0 }}>
                  {initials(profile.firstName, profile.lastName)}
                </div>
                <div>
                  <h2 className="mc-name-title" style={{ fontFamily: 'var(--font-display)', fontSize: 26, lineHeight: 1.15, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{profile.firstName} {profile.lastName}</h2>
                  <p style={{ fontSize: 14, color: 'var(--ink-700)', margin: '6px 0 0' }}>{profile.currentRole} at {profile.currentCompany}</p>
                  <p style={{ fontSize: 12, color: 'var(--ink-400)', margin: '2px 0 0' }}>{profile.yearsOfExperience} years experience</p>
                </div>
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'center' }}>
                  {(profile.expertiseAreas || []).map((tag) => <Tag key={tag}>{tag}</Tag>)}
                </div>
                {!!(profile.careerPaths || []).length && <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{profile.careerPaths.join(', ')}</span>}
                {profile.linkedinUrl && (
                  <a href={profile.linkedinUrl} target="_blank" rel="noreferrer" style={{ fontSize: 12, color: 'var(--taupe-700)', display: 'inline-flex', alignItems: 'center', gap: 5, border: 0 }}>
                    LinkedIn<Icon name="external-link" size={12} />
                  </a>
                )}

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', width: '100%', marginTop: 8 }}>
                  <div style={{ background: 'var(--bone-200)', padding: '16px 10px', display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'center' }}>
                    <span className="cb-eyebrow">Sessions completed</span>
                    <span style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)' }}>{profile.sessionsCompleted || 0}</span>
                  </div>
                  <div style={{ background: 'var(--bone-200)', padding: '16px 10px', display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'center' }}>
                    <span className="cb-eyebrow">Average rating</span>
                    {ratingLabel ? (
                      <>
                        <span style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)' }}>{ratingLabel}</span>
                        <span style={{ fontSize: 14, lineHeight: 1, letterSpacing: 1 }}>
                          {Array.from({ length: 5 }, (_, i) => (
                            // eslint-disable-next-line react/no-array-index-key
                            <span key={i} style={{ color: (i + 1) * 20 <= starsFillPct + 0.001 ? 'var(--ink-900)' : 'var(--bone-400)' }}>★</span>
                          ))}
                        </span>
                      </>
                    ) : <span style={{ fontSize: 13, color: 'var(--ink-400)', fontStyle: 'italic' }}>No ratings yet</span>}
                  </div>
                </div>

                <div style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10, padding: '14px 0', borderTop: '1px solid var(--line-hairline)', borderBottom: '1px solid var(--line-hairline)', marginTop: 4 }}>
                  <Switch checked={profile.isAvailable !== false} onChange={toggleAvailability} label="Accepting Session Requests" />
                </div>

                <Button variant="ghost" size="md" fullWidth onClick={startEdit}>Edit Profile</Button>
              </div>
            )}

            {!profileLoading && hasProfile && editing && (
              <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 24, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <ProfileForm draft={editDraft} setDraft={setEditDraft} onSubmit={submitEdit} saving={editSaving} error={editError} submitLabel="Save" />
                <Button variant="ghost" size="md" onClick={() => setEditing(false)}>Cancel</Button>
              </div>
            )}
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 20, minWidth: 0 }}>
            <div style={{ display: 'flex', gap: 4, overflowX: 'auto' }}>
              {[{ value: 'REQUESTED', label: 'Requests', count: requestsCount }, { value: 'ACCEPTED', label: 'Upcoming', count: upcomingCount }, { value: 'HISTORY', label: 'History', count: historyCount }].map((it) => (
                <button
                  key={it.value}
                  type="button"
                  onClick={() => setActiveTab(it.value)}
                  style={{ border: 0, borderBottom: `2px solid ${activeTab === it.value ? 'var(--ink-900)' : 'transparent'}`, background: 'none', padding: '12px 16px', fontSize: 13.5, fontWeight: 500, cursor: 'pointer', color: activeTab === it.value ? 'var(--ink-900)' : 'var(--ink-400)', whiteSpace: 'nowrap', display: 'flex', alignItems: 'center', gap: 8 }}
                >
                  {it.label}
                  {it.count > 0 && <Badge tone="neutral">{it.count}</Badge>}
                </button>
              ))}
            </div>

            <div style={{ minHeight: 200 }}>
              {sessionsLoading && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  {[1, 2, 3].map((k) => <Skeleton key={k} height={130} />)}
                </div>
              )}

              {!sessionsLoading && visible.length === 0 && (
                <div style={{ padding: '48px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center' }}>
                  <Icon name="history" size={20} />
                  <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{EMPTY_MESSAGES[activeTab]}</span>
                </div>
              )}

              {!sessionsLoading && visible.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
                  {visible.map((s, idx) => (
                    <SessionCard
                      key={s.id}
                      session={s}
                      index={idx}
                      revealed={sessionsIn}
                      respondOpenId={respondOpenId}
                      respondDraft={respondDraft}
                      respondError={respondOpenId === s.id ? respondError : ''}
                      respondSaving={respondOpenId === s.id && respondSaving}
                      declineConfirmId={declineConfirmId}
                      onAcceptClick={openAccept}
                      onDeclineClick={(sess) => setDeclineConfirmId(sess.id)}
                      onDeclineYes={declineYes}
                      onDeclineNo={() => setDeclineConfirmId(null)}
                      onRespondField={onRespondField}
                      onConfirmAccept={confirmAccept}
                      onMarkComplete={onMarkComplete}
                      onCancelSession={onCancelSession}
                      notesOpenIds={notesOpenIds}
                      onToggleNotes={onToggleNotes}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>

        </div>
      </main>
    </div>
  );
}
