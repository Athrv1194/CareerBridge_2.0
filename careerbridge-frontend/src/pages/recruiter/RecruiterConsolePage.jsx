import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Field, Icon, IconButton, Input, Logo, revealStyle, Select, Skeleton, StatTile, Tag, Textarea,
} from '../../components/ui';
import {
  createCompany, getMyCompanies, updateCompany,
  createJob, getMyJobs, updateJob, deactivateJob, deleteJob,
  getJobApplications, updateApplicationStatus, extendOffer,
  scheduleInterview, getMyInterviews, updateInterview,
  getCandidates, getMyPlacementStats,
} from '../../api/recruiterApi';
import { getUnreadCount } from '../../api/notificationApi';
import { getCandidateAvatarBlobUrl } from '../../api/studentApi';
import { getTokenPayload, getDisplayName, clearTokens } from '../../utils/tokenUtils';
import './recruiter-console.css';

const WORKMODE_LABEL = { REMOTE: 'Remote', HYBRID: 'Hybrid', ON_SITE: 'On-site' };
const JOBTYPE_LABEL = { FULL_TIME: 'Full time', PART_TIME: 'Part time', INTERNSHIP: 'Internship', CONTRACT: 'Contract' };
const STATUS_TONE = { APPLIED: 'default', SHORTLISTED: 'info', INTERVIEWED: 'accent', OFFERED: 'accent', REJECTED: 'danger' };
const KANBAN_STATUSES = ['APPLIED', 'SHORTLISTED', 'INTERVIEWED', 'OFFERED'];
const INTERVIEW_MODE_OPTIONS = ['ONLINE', 'IN_PERSON'];
const INTERVIEW_STATUS_OPTIONS = ['SCHEDULED', 'COMPLETED', 'CANCELLED'];

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
function parseSkills(v) {
  if (Array.isArray(v)) return v.filter(Boolean).map((s) => String(s).trim()).filter(Boolean);
  return String(v || '').split(',').map((s) => s.trim()).filter(Boolean);
}
function scoreBand(score) { return score < 40 ? 'danger' : score < 70 ? 'warning' : 'success'; }
const scoreColor = { danger: 'var(--status-danger)', warning: 'var(--status-warning)', success: 'var(--status-success)' };

// Square tile matches this page's existing candidate-card style -- initials until (if) the real
// photo loads, never a loading spinner, since most candidates have no avatar at all.
function CandidateAvatar({ studentId, hasAvatar, firstName, lastName, size = 40 }) {
  const [src, setSrc] = useState(null);
  useEffect(() => {
    setSrc(null);
    if (!hasAvatar) return undefined;
    let cancelled = false;
    getCandidateAvatarBlobUrl(studentId).then((url) => { if (!cancelled && url) setSrc(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [studentId, hasAvatar]);
  return (
    <div style={{ width: size, height: size, background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: size * 0.35, fontWeight: 600, flexShrink: 0, overflow: 'hidden' }}>
      {src ? <img src={src} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : `${(firstName?.[0] || '')}${(lastName?.[0] || '')}`.toUpperCase()}
    </div>
  );
}

function Tabs({ items, value, onChange }) {
  return (
    <div className="cb-scroll-x" style={{ display: 'flex', gap: 4, overflowX: 'auto' }}>
      {items.map((it) => (
        <button
          key={it.value}
          type="button"
          onClick={() => onChange(it.value)}
          style={{
            border: 0, borderBottom: `2px solid ${value === it.value ? 'var(--ink-900)' : 'transparent'}`,
            background: 'none', padding: '12px 16px', fontSize: 13.5, fontWeight: 500, cursor: 'pointer',
            color: value === it.value ? 'var(--ink-900)' : 'var(--ink-400)', whiteSpace: 'nowrap',
          }}
        >
          {it.label}
        </button>
      ))}
    </div>
  );
}

function EmptyState({ icon, title, message, action }) {
  return (
    <div style={{ padding: '48px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center' }}>
      {icon && <Icon name={icon} size={20} />}
      {title && <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{title}</span>}
      {message && <span style={{ fontSize: 12.5, color: 'var(--ink-400)' }}>{message}</span>}
      {action}
    </div>
  );
}

// Shown only when the real list for that job/console comes back empty, so a fresh account isn't a
// wall of blank tabs. Ids are prefixed 'sample-' so click handlers can refuse to act on them --
// this console is writable, unlike Placement Console's read-only sample fallback, so a fake row
// must never reach a real PATCH/DELETE call.
const SAMPLE_APPS = [
  { id: 'sample-1', studentId: 1001, status: 'APPLIED', offeredCtc: null, offerOutcome: null, appliedAt: new Date(Date.now() - 86400000).toISOString() },
  { id: 'sample-2', studentId: 1002, status: 'SHORTLISTED', offeredCtc: null, offerOutcome: null, appliedAt: new Date(Date.now() - 4 * 86400000).toISOString() },
  { id: 'sample-3', studentId: 1003, status: 'INTERVIEWED', offeredCtc: null, offerOutcome: null, appliedAt: new Date(Date.now() - 6 * 86400000).toISOString() },
  { id: 'sample-4', studentId: 1004, status: 'OFFERED', offeredCtc: 9.5, offerOutcome: 'ACCEPTED', appliedAt: new Date(Date.now() - 10 * 86400000).toISOString() },
];
const SAMPLE_INTERVIEWS = [
  { id: 'sample-iv-1', applicationId: 'sample-1', scheduledDate: new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10), timeSlot: '11:00 AM – 11:45 AM', mode: 'ONLINE', meetingLink: 'https://meet.careerbridge.io/sample', status: 'SCHEDULED' },
  { id: 'sample-iv-2', applicationId: 'sample-3', scheduledDate: new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 10), timeSlot: '3:00 PM – 3:30 PM', mode: 'IN_PERSON', meetingLink: '', status: 'SCHEDULED' },
];
const isSampleId = (id) => typeof id === 'string' && id.startsWith('sample');

const emptyCompanyDraft = { name: '', industry: '', website: '', description: '', logoUrl: '' };
const emptyJobDraft = { title: '', description: '', requiredSkills: '', location: '', workMode: 'REMOTE', jobType: 'FULL_TIME', salaryMin: '', salaryMax: '', applicationDeadline: '' };
const emptyIvDraft = { applicationId: '', scheduledDate: '', timeSlot: '', mode: 'ONLINE', meetingLink: '', status: 'SCHEDULED', feedback: '' };

export default function RecruiterConsolePage() {
  const navigate = useNavigate();
  const [recruiterName, setRecruiterName] = useState('');
  const [unreadCount, setUnreadCount] = useState(0);
  const [activeTab, setActiveTab] = useState('COMPANY');

  const [company, setCompany] = useState(null);
  const [companyLoaded, setCompanyLoaded] = useState(false);
  const [companyForm, setCompanyForm] = useState(false);
  const [companyDraft, setCompanyDraft] = useState(emptyCompanyDraft);
  const [companySaving, setCompanySaving] = useState(false);
  const [companyError, setCompanyError] = useState('');

  const [jobs, setJobs] = useState([]);
  const [jobsLoaded, setJobsLoaded] = useState(false);
  const [selectedJobId, setSelectedJobId] = useState(null);
  const [jobForm, setJobForm] = useState(false);
  const [jobDraft, setJobDraft] = useState(emptyJobDraft);
  const [jobEditingId, setJobEditingId] = useState(null);
  const [jobSaving, setJobSaving] = useState(false);
  const [jobError, setJobError] = useState('');

  const [appsJobId, setAppsJobId] = useState(null);
  const [apps, setApps] = useState([]);
  const [appsLoading, setAppsLoading] = useState(false);
  const [selectedAppId, setSelectedAppId] = useState(null);
  const [offerCtc, setOfferCtc] = useState('');
  const [appActionBusy, setAppActionBusy] = useState(false);

  const [candidates, setCandidates] = useState([]);
  const [candLoading, setCandLoading] = useState(false);
  const [candLoaded, setCandLoaded] = useState(false);
  const [candQuery, setCandQuery] = useState('');
  const [candMin, setCandMin] = useState('');
  const [candMax, setCandMax] = useState('');
  const [candDept, setCandDept] = useState('');
  const candTimer = useRef(null);

  const [interviews, setInterviews] = useState([]);
  const [ivLoaded, setIvLoaded] = useState(false);
  const [ivPanel, setIvPanel] = useState(false);
  const [ivDraft, setIvDraft] = useState(emptyIvDraft);
  const [ivEditingId, setIvEditingId] = useState(null);
  const [ivSaving, setIvSaving] = useState(false);
  const [ivError, setIvError] = useState('');

  const [statsOpen, setStatsOpen] = useState(false);
  const [stats, setStats] = useState(null);
  const [companyIn, setCompanyIn] = useState(false);
  const [jobsIn, setJobsIn] = useState(false);
  const [candIn, setCandIn] = useState(false);
  const [ivIn, setIvIn] = useState(false);

  useEffect(() => {
    const payload = getTokenPayload();
    if (!payload) {
      navigate('/login', { replace: true });
      return;
    }
    if (payload.role !== 'RECRUITER') {
      navigate('/login', { replace: true });
      return;
    }
    setRecruiterName(getDisplayName('Recruiter'));
    getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    getMyCompanies().then((list) => { setCompany(list[0] || null); setCompanyLoaded(true); setTimeout(() => setCompanyIn(true), 20); }).catch(() => setCompanyLoaded(true));
  }, [navigate]);

  const loadJobs = useCallback(() => {
    setJobsLoaded(false);
    getMyJobs().then((data) => { setJobs(data); setJobsLoaded(true); if (data[0]) setSelectedJobId((cur) => cur || data[0].id); setTimeout(() => setJobsIn(true), 20); }).catch(() => setJobsLoaded(true));
  }, []);

  const loadApps = useCallback((jobId) => {
    if (!jobId) { setApps([]); return; }
    setAppsLoading(true);
    getJobApplications(jobId).then((data) => setApps(data)).catch(() => setApps([])).finally(() => setAppsLoading(false));
  }, []);

  const loadCandidates = useCallback((skillsStr, min, max, department) => {
    setCandLoading(true);
    setCandIn(false);
    getCandidates({ skills: skillsStr, minScore: min, maxScore: max, department })
      .then((data) => setCandidates(data)).catch(() => setCandidates([]))
      .finally(() => { setCandLoading(false); setCandLoaded(true); setTimeout(() => setCandIn(true), 20); });
  }, []);

  const loadInterviews = useCallback(() => {
    getMyInterviews().then((data) => { setInterviews(data); setIvLoaded(true); setTimeout(() => setIvIn(true), 20); }).catch(() => setIvLoaded(true));
  }, []);

  // The Applications tab's job select defaults its VALUE to jobs[0] before appsJobId is ever set --
  // without this, that default is purely visual and the first job's applications are never fetched.
  useEffect(() => {
    if (activeTab === 'APPLICATIONS' && appsJobId == null && jobs.length > 0) {
      setAppsJobId(jobs[0].id);
      loadApps(jobs[0].id);
    }
  }, [activeTab, appsJobId, jobs, loadApps]);

  const goToTab = (tab) => {
    setActiveTab(tab);
    if (tab === 'JOBS' && !jobsLoaded) loadJobs();
    if (tab === 'APPLICATIONS') {
      if (!jobsLoaded) loadJobs();
      if (appsJobId) loadApps(appsJobId);
    }
    if (tab === 'CANDIDATES' && !candLoaded) loadCandidates('', null, null, null);
    if (tab === 'INTERVIEWS' && !ivLoaded) loadInterviews();
  };

  // --- Company ---
  const startEditCompany = () => { setCompanyDraft({ ...emptyCompanyDraft, ...company }); setCompanyError(''); setCompanyForm(true); };
  const startCreateCompany = () => { setCompanyDraft(emptyCompanyDraft); setCompanyError(''); setCompanyForm(true); };
  const submitCompany = () => {
    if (!companyDraft.name.trim() || !companyDraft.industry.trim()) { setCompanyError('Name and industry are required.'); return; }
    setCompanySaving(true); setCompanyError('');
    const body = { name: companyDraft.name, industry: companyDraft.industry, website: companyDraft.website || undefined, description: companyDraft.description || undefined, logoUrl: companyDraft.logoUrl || undefined };
    const req = company ? updateCompany(company.id, body) : createCompany(body);
    req.then((c) => { setCompany(c); setCompanyForm(false); setCompanySaving(false); })
      .catch((err) => { setCompanyError(err.message); setCompanySaving(false); });
  };

  // --- Jobs ---
  const startCreateJob = () => { setJobDraft(emptyJobDraft); setJobEditingId(null); setJobError(''); setJobForm(true); };
  const startEditJob = (job) => {
    setJobDraft({
      title: job.title, description: job.description || '', requiredSkills: job.requiredSkills || '',
      location: job.location || '', workMode: job.workMode, jobType: job.jobType,
      salaryMin: job.salaryMin ?? '', salaryMax: job.salaryMax ?? '', applicationDeadline: job.applicationDeadline || '',
    });
    setJobEditingId(job.id); setJobError(''); setJobForm(true);
  };
  const submitJob = () => {
    if (!jobDraft.title.trim() || !jobDraft.description.trim()) { setJobError('Title and description are required.'); return; }
    setJobSaving(true); setJobError('');
    const body = {
      title: jobDraft.title, description: jobDraft.description, requiredSkills: jobDraft.requiredSkills || undefined,
      location: jobDraft.location || undefined, workMode: jobDraft.workMode, jobType: jobDraft.jobType,
      salaryMin: jobDraft.salaryMin === '' ? undefined : Number(jobDraft.salaryMin),
      salaryMax: jobDraft.salaryMax === '' ? undefined : Number(jobDraft.salaryMax),
      applicationDeadline: jobDraft.applicationDeadline || undefined,
    };
    const req = jobEditingId ? updateJob(jobEditingId, body) : createJob({ ...body, companyId: company.id });
    req.then((j) => {
      setJobs((prev) => (jobEditingId ? prev.map((x) => (x.id === j.id ? j : x)) : [j, ...prev]));
      setSelectedJobId(j.id); setJobForm(false); setJobSaving(false);
    }).catch((err) => { setJobError(err.message); setJobSaving(false); });
  };
  const onDeactivateJob = (id) => { deactivateJob(id).then(() => loadJobs()).catch(() => {}); };
  const onDeleteJob = (id) => { deleteJob(id).then(() => { setSelectedJobId(null); loadJobs(); }).catch((err) => setJobError(err.message)); };

  // --- Applications ---
  const onAppsJobChange = (e) => { const id = Number(e.target.value); setAppsJobId(id); loadApps(id); setSelectedAppId(null); };
  const usingSampleApps = !appsLoading && appsJobId != null && apps.length === 0;
  const displayApps = usingSampleApps ? SAMPLE_APPS : apps;
  const kanbanColumns = useMemo(() => KANBAN_STATUSES.map((st) => ({
    status: st, label: st.charAt(0) + st.slice(1).toLowerCase(), items: displayApps.filter((a) => a.status === st),
  })), [displayApps]);
  const selectedApp = selectedAppId ? displayApps.find((a) => a.id === selectedAppId) : null;
  const moveStatus = (status) => {
    if (!selectedApp || appActionBusy) return;
    setAppActionBusy(true);
    updateApplicationStatus(selectedApp.id, status)
      .then((updated) => setApps((prev) => prev.map((a) => (a.id === updated.id ? updated : a))))
      .catch(() => {}).finally(() => setAppActionBusy(false));
  };
  const onExtendOffer = () => {
    if (!selectedApp || !offerCtc || appActionBusy) return;
    setAppActionBusy(true);
    extendOffer(selectedApp.id, Number(offerCtc))
      .then((updated) => { setApps((prev) => prev.map((a) => (a.id === updated.id ? updated : a))); setOfferCtc(''); })
      .catch(() => {}).finally(() => setAppActionBusy(false));
  };

  // --- Candidates ---
  const onCandQueryChange = (e) => {
    setCandQuery(e.target.value);
    clearTimeout(candTimer.current);
    candTimer.current = setTimeout(() => loadCandidates(e.target.value, candMin || null, candMax || null, candDept || null), 400);
  };
  const searchCandidates = () => { clearTimeout(candTimer.current); loadCandidates(candQuery, candMin || null, candMax || null, candDept || null); };
  const clearCandidateFilters = () => { setCandQuery(''); setCandMin(''); setCandMax(''); setCandDept(''); loadCandidates('', null, null, null); };

  // --- Interviews ---
  const openScheduleInterview = (applicationId) => {
    setIvDraft({ ...emptyIvDraft, applicationId: applicationId || '' });
    setIvEditingId(null); setIvError(''); setIvPanel(true);
  };
  const openEditInterview = (iv) => {
    setIvDraft({ applicationId: iv.applicationId, scheduledDate: iv.scheduledDate, timeSlot: iv.timeSlot, mode: iv.mode, meetingLink: iv.meetingLink || '', status: iv.status, feedback: iv.feedback || '' });
    setIvEditingId(iv.id); setIvError(''); setIvPanel(true);
  };
  const submitInterview = () => {
    if (ivDraft.mode === 'ONLINE' && !ivDraft.meetingLink.trim()) { setIvError('Meeting link is required for an online interview.'); return; }
    setIvSaving(true); setIvError('');
    if (ivEditingId) {
      updateInterview(ivEditingId, { scheduledDate: ivDraft.scheduledDate, timeSlot: ivDraft.timeSlot, mode: ivDraft.mode, meetingLink: ivDraft.meetingLink || undefined, status: ivDraft.status, feedback: ivDraft.feedback || undefined })
        .then(() => { setIvPanel(false); setIvSaving(false); loadInterviews(); })
        .catch((err) => { setIvError(err.message); setIvSaving(false); });
    } else {
      scheduleInterview(Number(ivDraft.applicationId), { scheduledDate: ivDraft.scheduledDate, timeSlot: ivDraft.timeSlot, mode: ivDraft.mode, meetingLink: ivDraft.meetingLink || undefined })
        .then(() => { setIvPanel(false); setIvSaving(false); loadInterviews(); })
        .catch((err) => { setIvError(err.message); setIvSaving(false); });
    }
  };

  const usingSampleInterviews = ivLoaded && interviews.length === 0;
  const displayInterviews = usingSampleInterviews ? SAMPLE_INTERVIEWS : interviews;
  const interviewGroups = useMemo(() => {
    const groups = {};
    displayInterviews.forEach((iv) => { (groups[iv.scheduledDate] ||= []).push(iv); });
    return Object.entries(groups).sort(([a], [b]) => a.localeCompare(b)).map(([date, items]) => ({ date, items }));
  }, [displayInterviews]);

  const openStats = () => {
    setStatsOpen(true);
    if (!stats) getMyPlacementStats().then(setStats).catch(() => {});
  };

  const selectedJob = selectedJobId ? jobs.find((j) => j.id === selectedJobId) : null;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <header className="cb-rc-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--bone-100)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 24, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <span className="cb-rc-eyebrow-sep" style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)', paddingLeft: 24, borderLeft: '1px solid var(--line-hairline)' }}>Recruiter</span>
        <div style={{ flex: 1 }} />
        <div className="cb-app-header-actions" style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
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
            <div className="cb-rc-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{recruiterName}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Recruiter</span>
            </div>
          </div>
          <Button variant="ghost" size="sm" onClick={() => { clearTokens(); navigate('/login'); }}>Log out</Button>
        </div>
      </header>

      <div style={{ position: 'sticky', top: 64, zIndex: 30, background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div className="cb-rc-tabs-row" style={{ maxWidth: 1320, margin: '0 auto', padding: '0 32px', boxSizing: 'border-box', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap' }}>
          <Tabs
            value={activeTab}
            onChange={goToTab}
            items={[{ value: 'COMPANY', label: 'Company' }, { value: 'JOBS', label: 'Jobs' }, { value: 'APPLICATIONS', label: 'Applications' }, { value: 'CANDIDATES', label: 'Candidates' }, { value: 'INTERVIEWS', label: 'Interviews' }]}
          />
          <Button variant="ghost" size="sm" iconAfter="arrow-right" onClick={openStats} style={{ flexShrink: 0 }}>Placement stats</Button>
        </div>
      </div>

      <main className="cb-rc-main" style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 90px', boxSizing: 'border-box' }}>

        {activeTab === 'COMPANY' && (
          <div>
            {!companyLoaded && <Skeleton height={300} />}

            {companyLoaded && !company && !companyForm && (
              <EmptyState icon="building-2" title="Set up your company" message="You need a company profile before posting jobs." action={<Button variant="primary" size="sm" onClick={startCreateCompany} style={{ marginTop: 4 }}>Create company</Button>} />
            )}

            {companyLoaded && company && !companyForm && (
              <div style={{ maxWidth: 760, display: 'flex', flexDirection: 'column', gap: 22, ...revealStyle(companyIn, 0, { distance: 20 }) }}>
                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 20 }}>
                  {company.logoUrl ? (
                    <img src={company.logoUrl} alt="" style={{ width: 64, height: 64, objectFit: 'cover', flexShrink: 0, background: 'var(--bone-100)' }} />
                  ) : (
                    <div style={{ width: 64, height: 64, background: 'var(--bone-200)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <Icon name="building-2" size={24} />
                    </div>
                  )}
                  <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
                    <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
                      <h1 className="cb-rc-company-title" style={{ fontFamily: 'var(--font-display)', fontSize: 44, lineHeight: 1.06, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{company.name}</h1>
                      <Button variant="ghost" size="sm" onClick={startEditCompany}>Edit</Button>
                    </div>
                    <span style={{ fontSize: 14, color: 'var(--ink-400)' }}>{company.industry}</span>
                    {company.website && (
                      <a href={company.website} target="_blank" rel="noreferrer" style={{ fontSize: 13, color: 'var(--taupe-700)', display: 'inline-flex', alignItems: 'center', gap: 5, border: 0 }}>
                        {company.website}<Icon name="external-link" size={12} />
                      </a>
                    )}
                  </div>
                </div>
                {company.description && <p style={{ fontSize: 14, lineHeight: 1.65, color: 'var(--ink-600)', margin: 0, maxWidth: 720 }}>{company.description}</p>}

                <div style={{ maxWidth: 760, display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', marginTop: 14 }}>
                  <StatTile value={jobs.filter((j) => j.isActive !== false).length || (jobsLoaded ? 0 : '—')} label="Active jobs" />
                  <StatTile value={apps.length || '—'} label="Applications on view" />
                  <StatTile value={jobs.filter((j) => j.title).length} label="Total jobs posted" />
                </div>

                <div style={{ marginTop: 14 }}>
                  <Button variant="secondary" size="sm" iconAfter="arrow-right" onClick={() => goToTab('JOBS')}>Manage jobs</Button>
                </div>
              </div>
            )}

            {companyForm && (
              <div style={{ maxWidth: 560, background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 34, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 18 }}>
                <div>
                  <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{company ? 'Edit company' : 'Create your company'}</h2>
                  {!company && <p style={{ fontSize: 14, color: 'var(--ink-400)', margin: '8px 0 0' }}>You need a company profile before posting jobs.</p>}
                </div>
                <Field label="Company name" required><Input value={companyDraft.name} onChange={(e) => setCompanyDraft((d) => ({ ...d, name: e.target.value }))} placeholder="Finzo Technologies" /></Field>
                <Field label="Industry" required><Input value={companyDraft.industry} onChange={(e) => setCompanyDraft((d) => ({ ...d, industry: e.target.value }))} placeholder="Financial services" /></Field>
                <Field label="Website"><Input value={companyDraft.website} onChange={(e) => setCompanyDraft((d) => ({ ...d, website: e.target.value }))} placeholder="https://" /></Field>
                <Field label="Description"><Textarea rows={4} value={companyDraft.description} onChange={(e) => setCompanyDraft((d) => ({ ...d, description: e.target.value }))} placeholder="What the company does, and who it's hiring for." /></Field>
                <Field label="Logo URL"><Input value={companyDraft.logoUrl} onChange={(e) => setCompanyDraft((d) => ({ ...d, logoUrl: e.target.value }))} placeholder="https://logo-url" /></Field>
                {companyError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{companyError}</span>}
                <div style={{ display: 'flex', gap: 10 }}>
                  <Button variant="primary" size="md" fullWidth={!company} disabled={companySaving} onClick={submitCompany}>{companySaving ? 'Saving…' : company ? 'Save changes' : 'Create company'}</Button>
                  {company && <Button variant="ghost" size="md" onClick={() => setCompanyForm(false)}>Cancel</Button>}
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === 'JOBS' && (
          <>
            {companyLoaded && !company && (
              <EmptyState icon="building-2" title="Set up your company first" message="You need a company profile before posting jobs." action={<Button variant="primary" size="sm" onClick={() => { setActiveTab('COMPANY'); startCreateCompany(); }} style={{ marginTop: 4 }}>Set up company</Button>} />
            )}
            {companyLoaded && company && (
              <div className="cb-rc-jobs-grid" style={{ display: 'grid', gap: 28, alignItems: 'start' }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12, maxHeight: 'calc(100vh - 220px)', overflowY: 'auto' }}>
                  <Button variant="primary" size="md" fullWidth icon="plus" onClick={startCreateJob}>Post new job</Button>
                  {!jobsLoaded && <Skeleton height={200} />}
                  {jobsLoaded && jobs.length === 0 && <EmptyState icon="briefcase" title="No jobs posted yet" />}
                  {jobs.map((job, idx) => (
                    <button
                      key={job.id}
                      type="button"
                      onClick={() => { setSelectedJobId(job.id); setJobForm(false); }}
                      style={{
                        display: 'flex', flexDirection: 'column', gap: 6, padding: 14, boxSizing: 'border-box', background: selectedJobId === job.id && !jobForm ? 'var(--bone-200)' : 'var(--bone-50)', border: '1px solid var(--line-hairline)', borderLeft: selectedJobId === job.id ? '2px solid var(--ink-900)' : '1px solid var(--line-hairline)', cursor: 'pointer', textAlign: 'left', font: 'inherit',
                        ...revealStyle(jobsIn, idx, { step: 40, distance: 12, duration: 450 }),
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        {job.isActive !== false && <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--status-success)', flexShrink: 0 }} />}
                        <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)', flex: 1, minWidth: 0 }}>{job.title}</span>
                      </div>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        <Badge tone="neutral">{WORKMODE_LABEL[job.workMode] || job.workMode}</Badge>
                        <Badge tone="neutral">{JOBTYPE_LABEL[job.jobType] || job.jobType}</Badge>
                        {job.isActive === false && <Badge tone="warning">Inactive</Badge>}
                      </div>
                      <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtSalary(job.salaryMin, job.salaryMax)}</span>
                      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
                        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>Closes {fmtDate(job.applicationDeadline)}</span>
                      </div>
                    </button>
                  ))}
                </div>

                <div style={{ background: 'var(--bone-100)', minHeight: 420 }}>
                  {jobForm && (
                    <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 32, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 640 }}>
                      <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{jobEditingId ? 'Edit job' : 'Post new job'}</h2>
                      <Field label="Title" required><Input value={jobDraft.title} onChange={(e) => setJobDraft((d) => ({ ...d, title: e.target.value }))} placeholder="Data Analyst" /></Field>
                      <Field label="Description" required><Textarea rows={6} value={jobDraft.description} onChange={(e) => setJobDraft((d) => ({ ...d, description: e.target.value }))} placeholder="What the role owns, and who it reports to." /></Field>
                      <Field label="Required skills" hint="Comma-separated — stored and matched as plain text."><Input value={jobDraft.requiredSkills} onChange={(e) => setJobDraft((d) => ({ ...d, requiredSkills: e.target.value }))} placeholder="Python, SQL, Machine Learning" /></Field>
                      <Field label="Location"><Input value={jobDraft.location} onChange={(e) => setJobDraft((d) => ({ ...d, location: e.target.value }))} placeholder="Bengaluru" /></Field>
                      <div className="cb-stack-sm" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                        <Field label="Work mode" required><Select options={['REMOTE', 'HYBRID', 'ON_SITE']} value={jobDraft.workMode} onChange={(e) => setJobDraft((d) => ({ ...d, workMode: e.target.value }))} /></Field>
                        <Field label="Job type" required><Select options={['FULL_TIME', 'PART_TIME', 'INTERNSHIP', 'CONTRACT']} value={jobDraft.jobType} onChange={(e) => setJobDraft((d) => ({ ...d, jobType: e.target.value }))} /></Field>
                      </div>
                      <div className="cb-stack-sm" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                        <Field label="Salary min (₹ LPA)"><Input type="number" value={jobDraft.salaryMin} onChange={(e) => setJobDraft((d) => ({ ...d, salaryMin: e.target.value }))} placeholder="8" /></Field>
                        <Field label="Salary max (₹ LPA)"><Input type="number" value={jobDraft.salaryMax} onChange={(e) => setJobDraft((d) => ({ ...d, salaryMax: e.target.value }))} placeholder="14" /></Field>
                      </div>
                      <Field label="Application deadline"><Input type="date" value={jobDraft.applicationDeadline} onChange={(e) => setJobDraft((d) => ({ ...d, applicationDeadline: e.target.value }))} /></Field>
                      {jobError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{jobError}</span>}
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                        <Button variant="primary" size="md" disabled={jobSaving} onClick={submitJob}>{jobSaving ? 'Saving…' : jobEditingId ? 'Save changes' : 'Post job'}</Button>
                        <Button variant="ghost" size="md" onClick={() => setJobForm(false)}>Cancel</Button>
                        {jobEditingId && (
                          <>
                            <div style={{ flex: 1 }} />
                            <Button variant="ghost" size="sm" onClick={() => onDeactivateJob(jobEditingId)} style={{ color: 'var(--status-danger)' }}>Deactivate</Button>
                            <a href="javascript:void(0)" onClick={() => onDeleteJob(jobEditingId)} style={{ fontSize: 12, color: 'var(--status-danger)', border: 0 }}>Delete</a>
                          </>
                        )}
                      </div>
                    </div>
                  )}

                  {!jobForm && selectedJob && (
                    <div style={{ padding: '8px 4px', display: 'flex', flexDirection: 'column', gap: 22 }}>
                      <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 44, lineHeight: 1.06, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{selectedJob.title}</h2>
                      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <Badge tone="neutral">{WORKMODE_LABEL[selectedJob.workMode] || selectedJob.workMode}</Badge>
                        <Badge tone="neutral">{JOBTYPE_LABEL[selectedJob.jobType] || selectedJob.jobType}</Badge>
                        {selectedJob.location && <Badge tone="neutral" icon="map-pin">{selectedJob.location}</Badge>}
                      </div>
                      <div className="cb-stack-sm" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                        <StatTile value={fmtSalary(selectedJob.salaryMin, selectedJob.salaryMax)} label="Salary band" />
                        <StatTile value={fmtDateFull(selectedJob.applicationDeadline)} label="Deadline" />
                        <StatTile value={selectedJob.isActive === false ? 'Inactive' : 'Active'} label="Status" />
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <span className="cb-eyebrow">Required skills</span>
                        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                          {parseSkills(selectedJob.requiredSkills).map((s) => <Tag key={s} selected>{s}</Tag>)}
                        </div>
                      </div>
                      <p style={{ fontSize: 14, lineHeight: 1.65, color: 'var(--ink-600)', margin: 0, maxWidth: 680 }}>{selectedJob.description}</p>
                      <div style={{ display: 'flex', gap: 10 }}>
                        <Button variant="ghost" size="md" onClick={() => startEditJob(selectedJob)}>Edit job</Button>
                        <Button variant="primary" size="md" iconAfter="arrow-right" onClick={() => { setAppsJobId(selectedJob.id); loadApps(selectedJob.id); setActiveTab('APPLICATIONS'); }}>View applications</Button>
                      </div>
                    </div>
                  )}

                  {!jobForm && !selectedJob && <EmptyState icon="briefcase" title="No job selected" message="Pick a posting on the left, or post a new one." />}
                </div>
              </div>
            )}
          </>
        )}

        {activeTab === 'APPLICATIONS' && (
          <>
            {jobsLoaded && jobs.length === 0 && (
              <EmptyState icon="briefcase" title="No jobs posted yet" message="Post a role first — applications appear here once candidates apply." />
            )}
            {jobs.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
                <div style={{ maxWidth: 340 }}>
                  <Field label="Viewing applications for">
                    <Select
                      options={jobs.map((j) => ({ value: String(j.id), label: j.title || `Job #${j.id}` }))}
                      value={String(appsJobId || jobs[0].id)}
                      onChange={onAppsJobChange}
                    />
                  </Field>
                </div>

                {usingSampleApps && (
                  <Alert tone="info" title="Showing sample data" message="No real applications for this job yet — this is what the board looks like once candidates apply." />
                )}
                {appsLoading && <Skeleton height={300} />}
                {!appsLoading && (
                  <div className="cb-rc-kanban cb-scroll-x" style={{ display: 'grid', gap: 14, overflowX: 'auto' }}>
                    {kanbanColumns.map((col) => (
                      <div key={col.status} style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                          <span className="cb-eyebrow">{col.label}</span>
                          <Badge tone="neutral">{col.items.length}</Badge>
                        </div>
                        <div style={{ height: 2, background: 'var(--ink-900)' }} />
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minHeight: 60 }}>
                          {col.items.map((app) => (
                            <button
                              key={app.id}
                              type="button"
                              disabled={isSampleId(app.id)}
                              onClick={() => { setSelectedAppId(app.id); setOfferCtc(''); }}
                              style={{ display: 'flex', flexDirection: 'column', gap: 6, padding: 12, background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', cursor: isSampleId(app.id) ? 'default' : 'pointer', textAlign: 'left', font: 'inherit', opacity: isSampleId(app.id) ? 0.75 : 1 }}
                            >
                              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>Student #{app.studentId}</span>
                              <span className="cb-num" style={{ fontSize: 11, color: 'var(--ink-400)' }}>{fmtDate(app.appliedAt)}</span>
                              {app.offeredCtc != null && !app.offerOutcome && <span className="cb-num" style={{ fontSize: 13, fontWeight: 600, color: 'var(--taupe-600)' }}>{fmtCtc(app.offeredCtc)}</span>}
                              {app.offerOutcome === 'ACCEPTED' && <Badge tone="success">Accepted</Badge>}
                              {app.offerOutcome === 'DECLINED' && <Badge tone="danger">Declined</Badge>}
                            </button>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {selectedApp && (
              <>
                <div onClick={() => setSelectedAppId(null)} style={{ position: 'fixed', inset: 0, zIndex: 90, background: 'rgba(0,0,0,.25)' }} />
                <div style={{ position: 'fixed', top: 0, right: 0, height: '100vh', width: 340, maxWidth: '92vw', background: 'var(--bone-50)', borderLeft: '1px solid var(--line-hairline)', zIndex: 91, padding: 26, boxSizing: 'border-box', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 }}>
                    <div>
                      <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>Student #{selectedApp.studentId}</span>
                      <div className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 4 }}>{fmtDateFull(selectedApp.appliedAt)}</div>
                    </div>
                    <IconButton icon="x" label="Close" onClick={() => setSelectedAppId(null)} />
                  </div>
                  <Badge tone={STATUS_TONE[selectedApp.status]}>{selectedApp.status}</Badge>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <span className="cb-eyebrow">Move status</span>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                      <Button variant="ghost" size="sm" disabled={appActionBusy || selectedApp.status === 'SHORTLISTED'} onClick={() => moveStatus('SHORTLISTED')}>Shortlist</Button>
                      <Button variant="ghost" size="sm" disabled={appActionBusy || selectedApp.status === 'INTERVIEWED'} onClick={() => moveStatus('INTERVIEWED')}>Interview</Button>
                      <Button variant="ghost" size="sm" disabled={appActionBusy || selectedApp.status === 'REJECTED'} onClick={() => moveStatus('REJECTED')} style={{ color: 'var(--status-danger)' }}>Reject</Button>
                    </div>
                  </div>

                  {(selectedApp.status === 'INTERVIEWED' || selectedApp.status === 'OFFERED') && !selectedApp.offerOutcome && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, borderTop: '1px solid var(--line-hairline)', paddingTop: 16 }}>
                      <span className="cb-eyebrow">{selectedApp.status === 'OFFERED' ? 'Correct offer' : 'Extend offer'}</span>
                      <Input type="number" value={offerCtc} onChange={(e) => setOfferCtc(e.target.value)} placeholder="e.g. 8.5 for ₹8.5 LPA" />
                      <Button variant="primary" size="sm" disabled={appActionBusy} onClick={onExtendOffer}>{selectedApp.status === 'OFFERED' ? 'Update offer' : 'Extend offer'}</Button>
                    </div>
                  )}

                  {selectedApp.offerOutcome && (
                    <div style={{ borderTop: '1px solid var(--line-hairline)', paddingTop: 16 }}>
                      <span className="cb-eyebrow">Offer outcome</span>
                      <div style={{ marginTop: 8 }}>
                        {selectedApp.offerOutcome === 'ACCEPTED' && <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--status-success)' }}>Accepted ✓</span>}
                        {selectedApp.offerOutcome === 'DECLINED' && <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--status-danger)' }}>Declined</span>}
                      </div>
                    </div>
                  )}

                  <a href="javascript:void(0)" onClick={() => { setSelectedAppId(null); openScheduleInterview(selectedApp.id); }} style={{ fontSize: 13, color: 'var(--taupe-700)', border: 0 }}>Schedule interview →</a>
                </div>
              </>
            )}
          </>
        )}

        {activeTab === 'CANDIDATES' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <Input icon="search" placeholder="Search by skill — e.g. Python, SQL" value={candQuery} onChange={onCandQueryChange} />
            <div style={{ display: 'flex', alignItems: 'end', gap: 12, flexWrap: 'wrap' }}>
              <Field label="Min PRS score" hint="Excludes unscored candidates."><Input type="number" value={candMin} onChange={(e) => setCandMin(e.target.value)} placeholder="0" /></Field>
              <Field label="Max PRS score" hint="Keeps unscored candidates."><Input type="number" value={candMax} onChange={(e) => setCandMax(e.target.value)} placeholder="100" /></Field>
              <Field label="Department" hint="Exact match, excludes unassigned."><Input value={candDept} onChange={(e) => setCandDept(e.target.value)} placeholder="e.g. Computer Science" /></Field>
              <Button variant="primary" size="sm" onClick={searchCandidates}>Search</Button>
              <Button variant="ghost" size="sm" onClick={clearCandidateFilters}>Clear</Button>
            </div>
            <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Showing {candidates.length} candidate{candidates.length === 1 ? '' : 's'}</span>

            {candLoading && (
              <div className="cb-rc-cand-grid" style={{ display: 'grid', gap: 14 }}>
                {[1, 2, 3, 4].map((k) => <Skeleton key={k} height={180} />)}
              </div>
            )}
            {!candLoading && candLoaded && candidates.length === 0 && (
              <EmptyState icon="users" title="No candidates found" message="Try fewer skill filters or a wider score range." />
            )}
            {!candLoading && candidates.length > 0 && (
              <div className="cb-rc-cand-grid" style={{ display: 'grid', gap: 14 }}>
                {candidates.map((c, idx) => {
                  const unavailable = c.prsScore === -1;
                  const skills = c.skills || [];
                  const shown = skills.slice(0, 3);
                  const more = Math.max(0, skills.length - 3);
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
                        <CandidateAvatar studentId={c.studentId} hasAvatar={c.hasAvatar} firstName={c.firstName} lastName={c.lastName} />
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{c.firstName} {c.lastName}</div>
                          <div style={{ fontSize: 12, color: 'var(--ink-400)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{c.email}</div>
                          {c.department && <div style={{ fontSize: 11, color: 'var(--taupe-700)', marginTop: 2 }}>{c.department}</div>}
                        </div>
                      </div>
                      {unavailable ? (
                        <span style={{ fontSize: 12, color: 'var(--ink-300)', fontStyle: 'italic' }}>Score unavailable</span>
                      ) : (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                          <div style={{ flex: 1, height: 6, background: 'var(--bone-300)' }}><div style={{ height: '100%', width: `${Math.max(0, c.prsScore)}%`, background: scoreColor[band] }} /></div>
                          <span className="cb-num" style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>{c.prsScore}</span>
                        </div>
                      )}
                      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Profile {c.profileCompletionPercentage}% complete</span>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                        {shown.map((s) => <Tag key={s}>{s}</Tag>)}
                        {more > 0 && <span style={{ fontSize: 12, color: 'var(--ink-400)', alignSelf: 'center' }}>+{more} more</span>}
                      </div>
                      <button type="button" onClick={() => navigate(`/candidate/${c.studentId}`, { state: { prsScore: c.prsScore } })} style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 12, color: 'var(--taupe-700)', alignSelf: 'flex-start' }}>View profile →</button>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {activeTab === 'INTERVIEWS' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 26 }}>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="primary" size="md" icon="plus" onClick={() => openScheduleInterview(null)}>Schedule interview</Button>
            </div>
            {usingSampleInterviews && (
              <Alert tone="info" title="Showing sample data" message="No real interviews scheduled yet — this is what the calendar looks like once you schedule one." />
            )}
            {interviewGroups.map((grp, gi) => (
              <div key={grp.date} style={{ display: 'flex', flexDirection: 'column', gap: 2, ...revealStyle(ivIn, gi, { step: 70, distance: 14 }) }}>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, marginBottom: 6 }}>
                  <span className="cb-eyebrow">{fmtDateFull(grp.date)}</span>
                  <div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} />
                </div>
                {grp.items.map((iv) => (
                  <div key={iv.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, padding: '14px 4px', borderBottom: '1px solid var(--line-hairline)' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>Application #{iv.applicationId}</span>
                      <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{iv.timeSlot}</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Badge tone={iv.mode === 'ONLINE' ? 'info' : 'neutral'}>{iv.mode === 'ONLINE' ? 'Online' : 'In person'}</Badge>
                      {iv.meetingLink && <a href={iv.meetingLink} target="_blank" rel="noreferrer" style={{ display: 'flex', border: 0, color: 'var(--status-info)' }}><Icon name="external-link" size={14} /></a>}
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                      <Badge tone={iv.status === 'CANCELLED' ? 'danger' : iv.status === 'COMPLETED' ? 'success' : 'default'}>{iv.status}</Badge>
                      {!isSampleId(iv.id) && iv.status !== 'CANCELLED' && <Button variant="ghost" size="sm" onClick={() => openScheduleInterview(iv.applicationId)}>Add round</Button>}
                      {!isSampleId(iv.id) && <Button variant="ghost" size="sm" onClick={() => openEditInterview(iv)}>Edit</Button>}
                    </div>
                  </div>
                ))}
              </div>
            ))}

            {ivPanel && (
              <>
                <div onClick={() => setIvPanel(false)} style={{ position: 'fixed', inset: 0, zIndex: 90, background: 'rgba(0,0,0,.25)' }} />
                <div style={{ position: 'fixed', top: 0, right: 0, height: '100vh', width: 360, maxWidth: '92vw', background: 'var(--bone-50)', borderLeft: '1px solid var(--line-hairline)', zIndex: 91, padding: 26, boxSizing: 'border-box', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>{ivEditingId ? 'Edit interview' : 'Schedule interview'}</span>
                    <IconButton icon="x" label="Close" onClick={() => setIvPanel(false)} />
                  </div>
                  <Field label="Application ID" required><Input type="number" value={ivDraft.applicationId} onChange={(e) => setIvDraft((d) => ({ ...d, applicationId: e.target.value }))} placeholder="9003" disabled={!!ivEditingId} /></Field>
                  <Field label="Scheduled date" required><Input type="date" value={ivDraft.scheduledDate} onChange={(e) => setIvDraft((d) => ({ ...d, scheduledDate: e.target.value }))} /></Field>
                  <Field label="Time slot" required><Input value={ivDraft.timeSlot} onChange={(e) => setIvDraft((d) => ({ ...d, timeSlot: e.target.value }))} placeholder="10:00 AM – 11:00 AM" /></Field>
                  <Field label="Mode" required><Select options={INTERVIEW_MODE_OPTIONS} value={ivDraft.mode} onChange={(e) => setIvDraft((d) => ({ ...d, mode: e.target.value }))} /></Field>
                  {ivDraft.mode === 'ONLINE' && (
                    <Field label="Meeting link"><Input value={ivDraft.meetingLink} onChange={(e) => setIvDraft((d) => ({ ...d, meetingLink: e.target.value }))} placeholder="https://meet.careerbridge.io/..." /></Field>
                  )}
                  {ivEditingId && (
                    <>
                      <Field label="Status"><Select options={INTERVIEW_STATUS_OPTIONS} value={ivDraft.status} onChange={(e) => setIvDraft((d) => ({ ...d, status: e.target.value }))} /></Field>
                      <Field label="Interview feedback"><Textarea rows={3} value={ivDraft.feedback} onChange={(e) => setIvDraft((d) => ({ ...d, feedback: e.target.value }))} placeholder="Notes for the hiring team…" /></Field>
                    </>
                  )}
                  {ivError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{ivError}</span>}
                  <div style={{ display: 'flex', gap: 10 }}>
                    <Button variant="primary" size="md" disabled={ivSaving} onClick={submitInterview}>{ivSaving ? 'Saving…' : ivEditingId ? 'Save changes' : 'Schedule'}</Button>
                    <Button variant="ghost" size="md" onClick={() => setIvPanel(false)}>Cancel</Button>
                  </div>
                </div>
              </>
            )}
          </div>
        )}

      </main>

      {statsOpen && (
        <>
          <div onClick={() => setStatsOpen(false)} style={{ position: 'fixed', inset: 0, zIndex: 90, background: 'rgba(0,0,0,.25)' }} />
          <div style={{ position: 'fixed', top: 0, right: 0, height: '100vh', width: 380, maxWidth: '92vw', background: 'var(--bone-50)', borderLeft: '1px solid var(--line-hairline)', zIndex: 91, padding: 26, boxSizing: 'border-box', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 16, fontWeight: 600, color: 'var(--ink-900)' }}>Your placement stats</span>
              <IconButton icon="x" label="Close" onClick={() => setStatsOpen(false)} />
            </div>
            {!stats && <Skeleton height={200} />}
            {stats && (
              <>
                <div className="cb-stack-sm" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                  <StatTile value={stats.totalApplications} label="Applications" />
                  <StatTile value={stats.offersExtended} label="Offers extended" />
                  <StatTile value={stats.offersAccepted} label="Offers accepted" />
                  <StatTile value={stats.offersDeclined} label="Offers declined" />
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span className="cb-eyebrow">Placement rate</span>
                  <span style={{ fontFamily: 'var(--font-display)', fontSize: 32, color: 'var(--ink-900)' }}>{Math.round(stats.placementRate || 0)}%</span>
                </div>
                <div style={{ display: 'flex', gap: 20 }}>
                  <div>
                    <span className="cb-eyebrow">Average CTC</span>
                    <div style={{ fontSize: 18, color: 'var(--ink-900)', marginTop: 4 }}>{fmtCtc(stats.averageCtc)}</div>
                  </div>
                  <div>
                    <span className="cb-eyebrow">Highest CTC</span>
                    <div style={{ fontSize: 18, color: 'var(--ink-900)', marginTop: 4 }}>{fmtCtc(stats.highestCtc)}</div>
                  </div>
                </div>
              </>
            )}
          </div>
        </>
      )}
    </div>
  );
}
