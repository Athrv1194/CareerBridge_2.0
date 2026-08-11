import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Field, Icon, IconButton, Input, Logo, revealStyle, Skeleton, StatTile, Tag,
  useRevealOnMount,
} from '../../components/ui';
import { getOrgApplications, getCandidates, getJobs, getJobDetail } from '../../api/recruiterApi';
import { getUnreadCount } from '../../api/notificationApi';
import { submitJoinRequest } from '../../api/orgJoinApi';
import { getTokenPayload, getDisplayName } from '../../utils/tokenUtils';
import './placement-console.css';

const ROLE_REDIRECT = { STUDENT: '/dashboard', RECRUITER: '/recruiter-console', ORG_ADMIN: '/college-dashboard', SUPER_ADMIN: '/super-admin', MENTOR: '/mentor-console' };
const STATUS_TONE = { APPLIED: 'default', SHORTLISTED: 'info', INTERVIEWED: 'accent', OFFERED: 'accent', REJECTED: 'danger' };
const WORKMODE_LABEL = { REMOTE: 'Remote', HYBRID: 'Hybrid', ON_SITE: 'On-site' };
const JOBTYPE_LABEL = { FULL_TIME: 'Full-time', PART_TIME: 'Part-time', INTERNSHIP: 'Internship', CONTRACT: 'Contract' };
const ORDER = { APPLIED: 0, SHORTLISTED: 1, INTERVIEWED: 2, OFFERED: 3 };
const STEP_LABELS = ['APPLIED', 'SHORTLISTED', 'INTERVIEWED', 'OFFERED', 'OUTCOME'];

function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
}
function fmtDateFull(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}
function fmtCtc(v) { return v == null ? '—' : `₹${Number(v).toFixed(1)}L`; }
function fmtSalary(min, max) {
  if (min == null && max == null) return 'Not disclosed';
  if (min == null) return `Up to ₹${Number(max).toFixed(1)}L`;
  if (max == null) return `From ₹${Number(min).toFixed(1)}L`;
  return `₹${Number(min).toFixed(1)}L–₹${Number(max).toFixed(1)}L`;
}
// Shown only when the real applications list comes back empty, so the console never reads as broken.
const SAMPLE_APPS = [
  { id: 'sample-1', studentId: 1001, jobTitle: 'Backend Engineer', status: 'SHORTLISTED', offeredCtc: null, offerDate: null, offerOutcome: null, appliedAt: new Date(Date.now() - 3 * 86400000).toISOString(), updatedAt: new Date(Date.now() - 86400000).toISOString() },
  { id: 'sample-2', studentId: 1002, jobTitle: 'Data Analyst', status: 'OFFERED', offeredCtc: 8.5, offerDate: new Date(Date.now() - 2 * 86400000).toISOString(), offerOutcome: 'ACCEPTED', appliedAt: new Date(Date.now() - 10 * 86400000).toISOString(), updatedAt: new Date(Date.now() - 86400000).toISOString() },
  { id: 'sample-3', studentId: 1003, jobTitle: 'Frontend Developer', status: 'APPLIED', offeredCtc: null, offerDate: null, offerOutcome: null, appliedAt: new Date(Date.now() - 86400000).toISOString(), updatedAt: new Date(Date.now() - 86400000).toISOString() },
  { id: 'sample-4', studentId: 1004, jobTitle: 'Backend Engineer', status: 'REJECTED', offeredCtc: null, offerDate: null, offerOutcome: null, appliedAt: new Date(Date.now() - 6 * 86400000).toISOString(), updatedAt: new Date(Date.now() - 4 * 86400000).toISOString() },
];

function parseSkills(v) {
  if (Array.isArray(v)) return v.filter(Boolean).map((s) => String(s).trim()).filter(Boolean);
  return String(v || '').split(',').map((s) => s.trim()).filter(Boolean);
}
function scoreBand(score) { return score < 40 ? 'danger' : score < 70 ? 'warning' : 'success'; }
const scoreColor = { danger: 'var(--status-danger)', warning: 'var(--status-warning)', success: 'var(--status-success)' };

function Tabs({ items, value, onChange }) {
  return (
    <div style={{ display: 'flex', gap: 4 }}>
      {items.map((it) => (
        <button
          key={it.value}
          type="button"
          onClick={() => onChange(it.value)}
          style={{
            border: 0, borderBottom: `2px solid ${value === it.value ? 'var(--ink-900)' : 'transparent'}`,
            background: 'none', padding: '12px 16px', fontSize: 13.5, fontWeight: 500, cursor: 'pointer',
            color: value === it.value ? 'var(--ink-900)' : 'var(--ink-400)',
          }}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}

function EmptyState({ icon, title, message }) {
  return (
    <div style={{ padding: '48px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
      {icon && <Icon name={icon} size={20} />}
      {title && <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{title}</span>}
      {message && <span style={{ fontSize: 12.5, color: 'var(--ink-400)' }}>{message}</span>}
    </div>
  );
}

function PillButton({ label, active, onClick }) {
  return (
    <Button size="sm" variant={active ? 'primary' : 'ghost'} onClick={onClick}>{label}</Button>
  );
}

export default function PlacementConsolePage() {
  const navigate = useNavigate();
  const [officerName, setOfficerName] = useState('');
  const [unreadCount, setUnreadCount] = useState(0);
  const [activeTab, setActiveTab] = useState('APPLICATIONS');

  const [apps, setApps] = useState([]);
  const [appsLoaded, setAppsLoaded] = useState(false);
  const [orgIdMissing, setOrgIdMissing] = useState(false);
  const [joinOrgId, setJoinOrgId] = useState('');
  const [joinSubmitting, setJoinSubmitting] = useState(false);
  const [joinError, setJoinError] = useState('');
  const [joinSubmitted, setJoinSubmitted] = useState(false);
  const [statusFilters, setStatusFilters] = useState(new Set());
  const [outcomeFilter, setOutcomeFilter] = useState('ANY');
  const [appSearch, setAppSearch] = useState('');
  const [selectedAppId, setSelectedAppId] = useState(null);

  const [candidates, setCandidates] = useState([]);
  const [candLoading, setCandLoading] = useState(false);
  const [candLoaded, setCandLoaded] = useState(false);
  const [candQuery, setCandQuery] = useState('');
  const [candMin, setCandMin] = useState('');
  const [candMax, setCandMax] = useState('');
  const candTimer = useRef(null);

  const [jobs, setJobs] = useState([]);
  const [jobsLoaded, setJobsLoaded] = useState(false);
  const [jobSearch, setJobSearch] = useState('');
  const [jobTypeFilter, setJobTypeFilter] = useState('ALL');
  const [workModeFilter, setWorkModeFilter] = useState('ANY');
  const [selectedJobId, setSelectedJobId] = useState(null);
  const [jobDetail, setJobDetail] = useState(null);
  const [appsIn, setAppsIn] = useState(false);
  const [candIn, setCandIn] = useState(false);
  const [jobsIn, setJobsIn] = useState(false);
  const topbarIn = useRevealOnMount();

  useEffect(() => {
    const payload = getTokenPayload();
    if (!payload) {
      navigate('/login', { replace: true });
      return;
    }
    if (payload.role !== 'PLACEMENT_OFFICER') {
      navigate(ROLE_REDIRECT[payload.role] || '/dashboard', { replace: true });
      return;
    }
    setOrgIdMissing(!payload?.organizationId && !payload?.orgId);
    setOfficerName(getDisplayName('Placement officer'));
    getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    getOrgApplications().then((data) => { setApps(data); setAppsLoaded(true); setTimeout(() => setAppsIn(true), 20); }).catch(() => setAppsLoaded(true));
  }, [navigate]);

  const loadCandidates = useCallback((skillsStr, min, max) => {
    setCandLoading(true);
    setCandIn(false);
    getCandidates({ skills: skillsStr, minScore: min, maxScore: max })
      .then((data) => setCandidates(data))
      .catch(() => setCandidates([]))
      .finally(() => { setCandLoading(false); setCandLoaded(true); setTimeout(() => setCandIn(true), 20); });
  }, []);

  const loadJobs = useCallback(() => {
    getJobs().then((data) => { setJobs(data.filter((j) => j.isActive !== false)); setJobsLoaded(true); setTimeout(() => setJobsIn(true), 20); }).catch(() => setJobsLoaded(true));
  }, []);

  const submitJoin = () => {
    const id = Number(joinOrgId.trim());
    if (!joinOrgId.trim() || Number.isNaN(id)) { setJoinError('Enter the numeric organization ID.'); return; }
    setJoinSubmitting(true); setJoinError('');
    submitJoinRequest(id)
      .then(() => setJoinSubmitted(true))
      .catch((err) => setJoinError(err.message))
      .finally(() => setJoinSubmitting(false));
  };

  const goToTab = (tab) => {
    setActiveTab(tab);
    if (tab === 'CANDIDATES' && !candLoaded) loadCandidates(candQuery, candMin || null, candMax || null);
    if (tab === 'JOBS' && !jobsLoaded) loadJobs();
  };

  const onCandQueryChange = (e) => {
    setCandQuery(e.target.value);
    clearTimeout(candTimer.current);
    candTimer.current = setTimeout(() => loadCandidates(e.target.value, candMin || null, candMax || null), 400);
  };
  const searchCandidates = () => { clearTimeout(candTimer.current); loadCandidates(candQuery, candMin || null, candMax || null); };
  const clearCandidateFilters = () => { setCandQuery(''); setCandMin(''); setCandMax(''); clearTimeout(candTimer.current); loadCandidates('', null, null); };

  const selectJob = (id) => {
    setSelectedJobId(id);
    setJobDetail(null);
    getJobDetail(id).then((d) => setJobDetail(d)).catch(() => {});
  };
  const findMatchingCandidates = (skillsStr) => {
    const firstThree = parseSkills(skillsStr).slice(0, 3).join(', ');
    setCandQuery(firstThree);
    setCandMin(''); setCandMax('');
    setActiveTab('CANDIDATES');
    loadCandidates(firstThree, null, null);
  };

  // Real applications win the moment they exist; sample rows only fill an otherwise-empty console.
  const usingSampleApps = appsLoaded && apps.length === 0;
  const displayApps = usingSampleApps ? SAMPLE_APPS : apps;

  const filteredApps = useMemo(() => displayApps.filter((a) => {
    if (statusFilters.size && !statusFilters.has(a.status)) return false;
    if (outcomeFilter !== 'ANY' && a.offerOutcome !== outcomeFilter) return false;
    if (appSearch.trim() && !(a.jobTitle || '').toLowerCase().includes(appSearch.trim().toLowerCase())) return false;
    return true;
  }), [displayApps, statusFilters, outcomeFilter, appSearch]);

  const stats = useMemo(() => ({
    total: displayApps.length,
    shortlisted: displayApps.filter((a) => a.status === 'SHORTLISTED').length,
    offered: displayApps.filter((a) => a.status === 'OFFERED').length,
    accepted: displayApps.filter((a) => a.offerOutcome === 'ACCEPTED').length,
  }), [displayApps]);

  const selApp = selectedAppId ? displayApps.find((a) => a.id === selectedAppId) : null;
  const filteredJobs = useMemo(() => {
    const q = jobSearch.trim().toLowerCase();
    return jobs.filter((j) => {
      if (q && !`${j.title} ${j.companyName}`.toLowerCase().includes(q)) return false;
      if (jobTypeFilter !== 'ALL' && j.jobType !== jobTypeFilter) return false;
      if (workModeFilter !== 'ANY' && j.workMode !== workModeFilter) return false;
      return true;
    });
  }, [jobs, jobSearch, jobTypeFilter, workModeFilter]);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <header className="cb-pc-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--bone-100)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 24, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)', paddingLeft: 24, borderLeft: '1px solid var(--line-hairline)' }}>Placement</span>
        <div style={{ flex: 1 }} />
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
          <div className="cb-pc-avatar-name" style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Icon name="user" size={15} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{officerName}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Placement officer</span>
            </div>
          </div>
        </div>
      </header>

      <div style={{ background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div className="cb-pc-topbar" style={{ maxWidth: 1320, margin: '0 auto', padding: '26px 32px', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap', boxSizing: 'border-box', ...revealStyle(topbarIn, 0, { distance: 16 }) }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Placement officer</span>
            <h1 className="cb-pc-company-title" style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>Placement Console</h1>
            <span style={{ fontSize: 14, color: 'var(--ink-400)' }}>Monitor your institution&apos;s students in the job market.</span>
          </div>
          <Badge tone="default" title="You can view and search — changes are made by recruiters.">Read only</Badge>
        </div>
      </div>

      <div style={{ position: 'sticky', top: 64, zIndex: 30, background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div className="cb-pc-tabs-row" style={{ maxWidth: 1320, margin: '0 auto', padding: '0 32px', boxSizing: 'border-box' }}>
          <Tabs
            value={activeTab}
            onChange={goToTab}
            items={[{ value: 'APPLICATIONS', label: 'Applications' }, { value: 'CANDIDATES', label: 'Candidates' }, { value: 'JOBS', label: 'Job board' }]}
          />
        </div>
      </div>

      <main className="cb-pc-main" style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 90px', boxSizing: 'border-box' }}>

        {activeTab === 'APPLICATIONS' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
            {usingSampleApps && !orgIdMissing && (
              <Alert
                tone="info"
                title="Showing sample data"
                message="No real applications yet — this is what the console looks like once your students start applying."
              />
            )}
            <>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 14 }}>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Applications from your institution</span>
                  <div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} />
                  <span style={{ fontSize: 13, color: 'var(--ink-400)', flexShrink: 0 }}>{displayApps.length} application{displayApps.length === 1 ? '' : 's'} tracked</span>
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    <PillButton label="All" active={statusFilters.size === 0} onClick={() => setStatusFilters(new Set())} />
                    {['APPLIED', 'SHORTLISTED', 'INTERVIEWED', 'OFFERED', 'REJECTED'].map((st) => (
                      <PillButton
                        key={st}
                        label={st.charAt(0) + st.slice(1).toLowerCase()}
                        active={statusFilters.has(st)}
                        onClick={() => setStatusFilters((prev) => { const ns = new Set(prev); if (ns.has(st)) ns.delete(st); else ns.add(st); return ns; })}
                      />
                    ))}
                  </div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                    {[{ k: 'ANY', l: 'Any outcome' }, { k: 'ACCEPTED', l: 'Accepted' }, { k: 'DECLINED', l: 'Declined' }].map((p) => (
                      <PillButton key={p.k} label={p.l} active={outcomeFilter === p.k} onClick={() => setOutcomeFilter(p.k)} />
                    ))}
                  </div>
                  <div style={{ maxWidth: 340 }}>
                    <Input placeholder="Search by job title…" value={appSearch} onChange={(e) => setAppSearch(e.target.value)} />
                  </div>
                </div>

                <div className="cb-pc-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                  <StatTile value={stats.total} label="Total applications" style={revealStyle(appsIn, 0)} />
                  <StatTile value={stats.shortlisted} label="Shortlisted" style={revealStyle(appsIn, 1)} />
                  <StatTile value={stats.offered} label="Offered" style={revealStyle(appsIn, 2)} />
                  <StatTile value={stats.accepted} label="Accepted" style={revealStyle(appsIn, 3)} />
                </div>

                {!appsLoaded && <Skeleton height={300} />}
                {appsLoaded && apps.length > 0 && filteredApps.length === 0 && (
                  <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No applications match your filters.</span>
                )}
                {filteredApps.length > 0 && (
                  <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)' }}>
                    <div className="cb-pc-app-row cb-pc-app-row-head" style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr 0.9fr 0.7fr 0.8fr 0.7fr', gap: 0, padding: '10px 16px', borderBottom: '1px solid var(--line-hairline)', fontSize: 11, fontWeight: 500, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>
                      <span>Student</span><span>Job</span><span style={{ textAlign: 'center' }}>Status</span><span style={{ textAlign: 'right' }}>Offer</span><span style={{ textAlign: 'center' }}>Outcome</span><span style={{ textAlign: 'right' }}>Applied</span>
                    </div>
                    {filteredApps.map((a, idx) => (
                      <button
                        key={a.id}
                        type="button"
                        onClick={() => setSelectedAppId(a.id)}
                        className="cb-pc-app-row"
                        style={{
                          display: 'grid', gridTemplateColumns: '1fr 1.4fr 0.9fr 0.7fr 0.8fr 0.7fr', width: '100%', padding: '14px 16px', border: 0, borderBottom: '1px solid var(--line-hairline)', background: 'none', cursor: 'pointer', textAlign: 'left', font: 'inherit', boxSizing: 'border-box',
                          ...revealStyle(appsIn, idx, { step: 35, distance: 12, duration: 450 }),
                        }}
                      >
                        <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>Student #{a.studentId}</span>
                        <span style={{ fontSize: 13, color: 'var(--ink-700)' }}>{a.jobTitle}</span>
                        <span className="cb-pc-app-row-meta" style={{ textAlign: 'center' }}><Badge tone={STATUS_TONE[a.status]}>{a.status}</Badge></span>
                        <span className="cb-pc-app-row-meta" style={{ fontSize: 13, color: 'var(--ink-700)', textAlign: 'right' }}>{fmtCtc(a.offeredCtc)}</span>
                        <span className="cb-pc-app-row-meta" style={{ textAlign: 'center' }}>{a.offerOutcome ? <Badge tone={a.offerOutcome === 'ACCEPTED' ? 'success' : 'danger'}>{a.offerOutcome}</Badge> : <span style={{ color: 'var(--ink-300)' }}>—</span>}</span>
                        <span className="cb-pc-app-row-meta" style={{ fontSize: 12, color: 'var(--ink-400)', textAlign: 'right' }}>{fmtDate(a.appliedAt)}</span>
                      </button>
                    ))}
                  </div>
                )}
              </>

            {selApp && (
              <>
                <div onClick={() => setSelectedAppId(null)} style={{ position: 'fixed', inset: 0, zIndex: 90, background: 'rgba(0,0,0,.25)' }} />
                <div style={{ position: 'fixed', top: 0, right: 0, height: '100vh', width: 320, maxWidth: '92vw', background: 'var(--bone-50)', borderLeft: '1px solid var(--line-hairline)', zIndex: 91, padding: 26, boxSizing: 'border-box', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                      <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>Student #{selApp.studentId}</span>
                      <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 24, lineHeight: 1.15, letterSpacing: '-.01em', color: 'var(--ink-800)', margin: 0, fontWeight: 400 }}>{selApp.jobTitle}</h2>
                      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Applied {fmtDateFull(selApp.appliedAt)}</span>
                      <span style={{ fontSize: 12, color: 'var(--ink-300)' }}>Last updated {fmtDateFull(selApp.updatedAt)}</span>
                    </div>
                    <IconButton icon="x" label="Close" onClick={() => setSelectedAppId(null)} />
                  </div>

                  <Badge tone={STATUS_TONE[selApp.status]}>{selApp.status}</Badge>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Status timeline</span>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', marginTop: 14 }}>
                      {STEP_LABELS.map((label, i) => {
                        const curIdx = selApp.status === 'REJECTED' ? 4 : (selApp.offerOutcome ? 4 : ORDER[selApp.status]);
                        const finalLabel = selApp.offerOutcome === 'ACCEPTED' ? 'ACCEPTED' : selApp.offerOutcome === 'DECLINED' ? 'DECLINED' : selApp.status === 'REJECTED' ? 'REJECTED' : 'OUTCOME';
                        const lbl = i === 4 ? finalLabel : label;
                        const state_ = i < curIdx ? 'done' : i === curIdx ? 'current' : 'future';
                        return (
                          <div key={label} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, flex: 1, minWidth: 0 }}>
                            <span style={{ width: 10, height: 10, borderRadius: '50%', display: 'block', background: state_ === 'done' ? 'var(--ink-900)' : state_ === 'current' ? 'var(--taupe-500)' : 'var(--bone-300)' }} />
                            <span style={{ fontSize: 10, fontWeight: 500, letterSpacing: '.08em', textAlign: 'center', color: state_ === 'future' ? 'var(--ink-300)' : 'var(--ink-700)' }}>{lbl}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  {selApp.offeredCtc != null && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, borderTop: '1px solid var(--line-hairline)', paddingTop: 16 }}>
                      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-600)' }}>Offer extended</span>
                      <span style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)' }}>{fmtCtc(selApp.offeredCtc)}</span>
                      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtDateFull(selApp.offerDate)}</span>
                      {selApp.offerOutcome && (
                        <div style={{ marginTop: 4 }}><Badge tone={selApp.offerOutcome === 'ACCEPTED' ? 'success' : 'danger'}>{selApp.offerOutcome}</Badge></div>
                      )}
                    </div>
                  )}

                  <p style={{ fontSize: 12, lineHeight: 1.6, color: 'var(--ink-300)', fontStyle: 'italic', margin: 0, borderTop: '1px solid var(--line-hairline)', paddingTop: 16 }}>Status is managed by the recruiting company. Contact the recruiter to follow up.</p>
                </div>
              </>
            )}
          </div>
        )}

        {activeTab === 'CANDIDATES' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Candidate search</span>
              <div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} />
            </div>
            <span style={{ fontSize: 13, color: 'var(--ink-400)', marginTop: -14 }}>All students with public profiles, scored by placement readiness.</span>

            <Input icon="search" placeholder="Filter by skill — e.g. Python, React, SQL" value={candQuery} onChange={onCandQueryChange} />

            <div style={{ display: 'flex', alignItems: 'end', gap: 12, flexWrap: 'wrap' }}>
              <Field label="Min PRS"><Input type="number" value={candMin} onChange={(e) => setCandMin(e.target.value)} placeholder="0" /></Field>
              <Field label="Max PRS"><Input type="number" value={candMax} onChange={(e) => setCandMax(e.target.value)} placeholder="100" /></Field>
              <Button variant="primary" size="sm" onClick={searchCandidates}>Search</Button>
              <Button variant="ghost" size="sm" onClick={clearCandidateFilters}>Clear</Button>
            </div>

            <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Showing {candidates.length} student{candidates.length === 1 ? '' : 's'}</span>

            {candLoading && (
              <div className="cb-pc-cand-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                {[1, 2, 3, 4, 5, 6].map((k) => <Skeleton key={k} height={180} />)}
              </div>
            )}

            {!candLoading && candLoaded && candidates.length === 0 && (
              <EmptyState icon="search" title="No candidates match" message="Try fewer skills or a wider score range." />
            )}

            {!candLoading && candidates.length > 0 && (
              <div className="cb-pc-cand-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                {candidates.map((c, idx) => {
                  const unavailable = c.prsScore === -1;
                  const skills = c.skills || [];
                  const shown = skills.slice(0, 5);
                  const more = Math.max(0, skills.length - 5);
                  const band = unavailable ? 'success' : scoreBand(c.prsScore);
                  return (
                    <div
                      key={c.studentId}
                      style={{
                        background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 16, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 12,
                        ...revealStyle(candIn, idx),
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                        <div style={{ width: 44, height: 44, background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, fontWeight: 600, flexShrink: 0 }}>
                          {`${(c.firstName?.[0] || '')}${(c.lastName?.[0] || '')}`.toUpperCase()}
                        </div>
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.firstName} {c.lastName}</div>
                          <div style={{ fontSize: 12, color: 'var(--ink-400)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.email}</div>
                        </div>
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <span style={{ fontSize: 10, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Placement readiness</span>
                        {unavailable ? (
                          <span style={{ fontSize: 12, color: 'var(--ink-300)', fontStyle: 'italic' }}>Score not yet available</span>
                        ) : (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                            <div style={{ flex: 1, height: 6, background: 'var(--bone-300)' }}><div style={{ height: '100%', background: scoreColor[band], width: `${Math.max(0, c.prsScore)}%` }} /></div>
                            <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', flexShrink: 0 }}>{c.prsScore}</span>
                          </div>
                        )}
                      </div>

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                        <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Profile {c.profileCompletionPercentage}% complete</span>
                        <div style={{ height: 4, background: 'var(--bone-300)' }}><div style={{ height: '100%', background: 'var(--ink-700)', width: `${c.profileCompletionPercentage}%` }} /></div>
                      </div>

                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        {shown.map((skill) => <Tag key={skill}>{skill}</Tag>)}
                        {more > 0 && <span style={{ fontSize: 11, color: 'var(--ink-400)', alignSelf: 'center' }}>+{more} more</span>}
                      </div>

                      {c.profileUrl && <a href={c.profileUrl} target="_blank" rel="noreferrer" style={{ fontSize: 12, color: 'var(--taupe-600)', border: 0 }}>View profile →</a>}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'JOBS' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Active job listings</span>
              <div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} />
              <span style={{ fontSize: 12, color: 'var(--ink-400)', flexShrink: 0 }}>{filteredJobs.length} active role{filteredJobs.length === 1 ? '' : 's'}</span>
            </div>
            <span style={{ fontSize: 13, color: 'var(--ink-400)', marginTop: -14 }}>Live roles posted by recruiters. Share with your students.</span>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ maxWidth: 360 }}><Input icon="search" placeholder="Search by title or company…" value={jobSearch} onChange={(e) => setJobSearch(e.target.value)} /></div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {[{ k: 'ALL', l: 'All' }, ...Object.entries(JOBTYPE_LABEL).map(([k, l]) => ({ k, l }))].map((p) => (
                  <PillButton key={p.k} label={p.l} active={jobTypeFilter === p.k} onClick={() => setJobTypeFilter(p.k)} />
                ))}
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {[{ k: 'ANY', l: 'Any' }, ...Object.entries(WORKMODE_LABEL).map(([k, l]) => ({ k, l }))].map((p) => (
                  <PillButton key={p.k} label={p.l} active={workModeFilter === p.k} onClick={() => setWorkModeFilter(p.k)} />
                ))}
              </div>
            </div>

            <div className="cb-pc-jobs-grid" style={{ display: 'grid', gridTemplateColumns: '360px minmax(0,1fr)', gap: 24, alignItems: 'start' }}>
              <div className="cb-pc-jobs-list" style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 'calc(100vh - 320px)', overflowY: 'auto' }}>
                {!jobsLoaded && <Skeleton height={300} />}
                {jobsLoaded && filteredJobs.length === 0 && <EmptyState icon="briefcase" title="No roles match" message="Try a different search or filter." />}
                {filteredJobs.map((job, idx) => (
                  <button
                    key={job.id}
                    type="button"
                    onClick={() => selectJob(job.id)}
                    style={{
                      display: 'flex', flexDirection: 'column', gap: 6, padding: 14, boxSizing: 'border-box', background: selectedJobId === job.id ? 'var(--bone-200)' : 'var(--bone-50)', border: '1px solid var(--line-hairline)', borderLeft: selectedJobId === job.id ? '2px solid var(--taupe-500)' : '1px solid var(--line-hairline)', cursor: 'pointer', textAlign: 'left', font: 'inherit',
                      ...revealStyle(jobsIn, idx, { step: 40, distance: 12, duration: 450 }),
                    }}
                  >
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{job.title}</span>
                    <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{job.companyName}</span>
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                      <Badge tone="default">{WORKMODE_LABEL[job.workMode] || job.workMode}</Badge>
                      <Badge tone="default">{JOBTYPE_LABEL[job.jobType] || job.jobType}</Badge>
                    </div>
                    <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtSalary(job.salaryMin, job.salaryMax)}</span>
                    {job.location && <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{job.location}</span>}
                    <span style={{ fontSize: 11, color: 'var(--ink-300)' }}>Closes {fmtDate(job.applicationDeadline)}</span>
                  </button>
                ))}
              </div>

              <div style={{ background: 'var(--bone-100)', minHeight: 420 }}>
                {!selectedJobId && <EmptyState icon="briefcase" message="Select a role to view details." />}
                {selectedJobId && !jobDetail && <div style={{ padding: 32 }}><Skeleton height={340} /></div>}
                {jobDetail && (
                  <div style={{ padding: '8px 4px', boxSizing: 'border-box' }}>
                    <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 44, lineHeight: 1.06, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{jobDetail.title}</h2>
                    <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
                      <Badge tone="default">{WORKMODE_LABEL[jobDetail.workMode] || jobDetail.workMode}</Badge>
                      <Badge tone="default">{JOBTYPE_LABEL[jobDetail.jobType] || jobDetail.jobType}</Badge>
                      {jobDetail.location && <Badge tone="default" icon="map-pin">{jobDetail.location}</Badge>}
                    </div>

                    <div className="cb-pc-detail-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', marginTop: 28 }}>
                      <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Salary band</span><div style={{ fontSize: 15, color: 'var(--ink-900)', marginTop: 8 }}>{fmtSalary(jobDetail.salaryMin, jobDetail.salaryMax)}</div></div>
                      <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Closes</span><div style={{ fontSize: 15, color: 'var(--ink-900)', marginTop: 8 }}>{fmtDateFull(jobDetail.applicationDeadline)}</div></div>
                      <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Work mode</span><div style={{ fontSize: 15, color: 'var(--ink-900)', marginTop: 8 }}>{WORKMODE_LABEL[jobDetail.workMode] || jobDetail.workMode}</div></div>
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 28 }}>
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}><span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Required skills</span><div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} /></div>
                      {parseSkills(jobDetail.requiredSkills).length > 0 ? (
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          {parseSkills(jobDetail.requiredSkills).map((skill) => <Tag key={skill} selected>{skill}</Tag>)}
                        </div>
                      ) : <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No specific skills listed.</span>}
                    </div>

                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 28 }}>
                      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}><span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>About the role</span><div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} /></div>
                      <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-600)', margin: 0, maxWidth: 680 }}>{jobDetail.description}</p>
                    </div>

                    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 14, marginTop: 28, maxWidth: 680 }}>
                      <Icon name="user" size={16} style={{ flexShrink: 0, marginTop: 2 }} />
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-500)', fontStyle: 'italic', margin: 0 }}>Share this role with matching students in the Candidates tab.</p>
                        <Button variant="ghost" size="sm" iconAfter="arrow-right" onClick={() => findMatchingCandidates(jobDetail.requiredSkills)} style={{ alignSelf: 'flex-start' }}>Find matching candidates</Button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {orgIdMissing && activeTab === 'APPLICATIONS' && (
          <div style={{ marginTop: 24, maxWidth: 440 }}>
            {joinSubmitted ? (
              <Alert tone="info" title="Request sent" message="Your institution's admin needs to approve it before org-scoped data shows up here." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <Alert tone="warning" title="Your account isn't linked to an institution" message="Ask your admin for their College Dashboard's Organization ID, then enter it below to request access." />
                <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end' }}>
                  <Field label="Organization ID" error={joinError}>
                    <Input type="number" placeholder="e.g. 5" value={joinOrgId} onChange={(e) => setJoinOrgId(e.target.value)} />
                  </Field>
                  <Button variant="primary" size="md" disabled={joinSubmitting} onClick={submitJoin}>{joinSubmitting ? 'Sending…' : 'Request to join'}</Button>
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}
