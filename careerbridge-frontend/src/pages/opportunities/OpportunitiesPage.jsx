import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Field, Icon, IconButton, Input, Logo, MatchScore, revealStyle, Skeleton,
  StatTile, Tag, Textarea,
} from '../../components/ui';
import { getJobs, getJobDetail, getMyApplications, applyToJob } from '../../api/recruiterApi';
import { getMyProfile, getAvatarBlobUrl } from '../../api/studentApi';
import { getMyResumes } from '../../api/resumeApi';
import { getUnreadCount } from '../../api/notificationApi';
import { clearTokens } from '../../utils/tokenUtils';
import { getNavCollapsed, setNavCollapsed as persistNavCollapsed } from '../../utils/navPrefs';
import './opportunities.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities', active: true },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'users', label: 'Mentors', to: '/mentors' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

const JOB_TYPE_LABEL = { FULL_TIME: 'Full time', PART_TIME: 'Part time', INTERNSHIP: 'Internship', CONTRACT: 'Contract' };
const WORK_MODE_LABEL = { REMOTE: 'Remote', HYBRID: 'Hybrid', ON_SITE: 'On-site' };
const STATUS_TONE = { APPLIED: 'default', SHORTLISTED: 'info', INTERVIEWED: 'accent', OFFERED: 'success', REJECTED: 'danger' };
const STATUS_LABEL = { APPLIED: 'Applied', SHORTLISTED: 'Shortlisted', INTERVIEWED: 'Interviewed', OFFERED: 'Offered', REJECTED: 'Rejected' };

// Rough client-side keyword match for the list view; computeExactMatch replaces it once a job's detail loads.
const CAREER_KEYWORDS = {
  'Full Stack Developer': ['Java', 'Spring Boot', 'React', 'JavaScript', 'SQL', 'REST API', 'Git', 'Docker', 'HTML', 'CSS', 'Maven', 'Hibernate'],
  'Backend Developer': ['Java', 'Spring Boot', 'Spring', 'REST API', 'SQL', 'PostgreSQL', 'MySQL', 'Maven', 'Git', 'Hibernate', 'JPA', 'Microservices', 'Docker'],
  'Frontend Developer': ['React', 'JavaScript', 'TypeScript', 'HTML', 'CSS', 'Tailwind', 'Node.js', 'Redux', 'Webpack', 'Vite', 'REST API', 'Git'],
  'Data Scientist': ['Python', 'Machine Learning', 'SQL', 'TensorFlow', 'Pandas', 'NumPy', 'Scikit-learn', 'Jupyter', 'Data Analysis', 'Statistics', 'Power BI', 'Analytics'],
  'DevOps Engineer': ['Docker', 'Kubernetes', 'CI/CD', 'Linux', 'AWS', 'Git', 'Jenkins', 'Ansible', 'Terraform', 'Bash', 'Monitoring'],
  'Mobile Developer': ['Kotlin', 'Java', 'Swift', 'Android SDK', 'iOS', 'Flutter', 'React Native', 'REST API', 'Firebase', 'Git', 'SQLite', 'MVVM'],
  'System Design Engineer': ['System Design', 'Scalability', 'Microservices', 'Distributed Systems', 'Load Balancing', 'Caching', 'Message Queues', 'Docker', 'Kubernetes', 'Database Design', 'API Design'],
};

const BUCKET_BY_TITLE = [
  [/full[\s-]?stack/i, 'Full Stack Developer'],
  [/back[\s-]?end/i, 'Backend Developer'],
  [/front[\s-]?end/i, 'Frontend Developer'],
  [/data (analyst|scientist|science)|analytics/i, 'Data Scientist'],
  [/devops|site reliability|\bsre\b/i, 'DevOps Engineer'],
  [/mobile|android|\bios\b/i, 'Mobile Developer'],
  [/system design|architect/i, 'System Design Engineer'],
];

function pickBucket(title) {
  for (const [re, bucket] of BUCKET_BY_TITLE) if (re.test(title)) return bucket;
  return null;
}

function overlapPct(keywords, skillSet) {
  if (!keywords.length) return 0;
  const matched = keywords.filter((k) => skillSet.has(k.toLowerCase())).length;
  return Math.round((matched / keywords.length) * 100);
}

function computeListMatch(title, skillSet) {
  const bucket = pickBucket(title);
  if (bucket) return overlapPct(CAREER_KEYWORDS[bucket], skillSet);
  let best = 0;
  Object.values(CAREER_KEYWORDS).forEach((kws) => { const p = overlapPct(kws, skillSet); if (p > best) best = p; });
  return best;
}

function computeExactMatch(requiredSkills, skillSet) {
  if (!requiredSkills || !requiredSkills.length) return null;
  const matched = requiredSkills.filter((s) => skillSet.has(s.toLowerCase())).length;
  return Math.round((matched / requiredSkills.length) * 100);
}

function fmtSalary(min, max, jobType) {
  if (min == null && max == null) return 'Not disclosed';
  if (jobType === 'INTERNSHIP') {
    const v = min != null ? min : max;
    return `₹${Math.round(v).toLocaleString('en-IN')}/month`;
  }
  const f = (n) => Number(n).toFixed(1);
  if (min != null && max != null) return `₹${f(min)}L – ₹${f(max)}L`;
  return `₹${f(min != null ? min : max)}L`;
}

function fmtSalaryShort(min, max, jobType) {
  if (min == null && max == null) return '—';
  if (jobType === 'INTERNSHIP') return `₹${Math.round(min != null ? min : max).toLocaleString('en-IN')}`;
  const f = (n) => Number(n).toFixed(1);
  if (min != null && max != null) return `₹${f(min)}–${f(max)}L`;
  return `₹${f(min != null ? min : max)}L`;
}

function fmtDeadline(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(`${dateStr}T00:00:00`);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${d.getDate()} ${months[d.getMonth()]}`;
}

function metaLine(location, workMode, jobTypeLabel) {
  const modeLabel = WORK_MODE_LABEL[workMode];
  const parts = [location];
  if (modeLabel && (location || '').toLowerCase() !== modeLabel.toLowerCase()) parts.push(modeLabel);
  if (jobTypeLabel) parts.push(jobTypeLabel);
  return parts.filter(Boolean).join(' · ');
}

function fmtAppliedDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `Applied ${d.getDate()} ${months[d.getMonth()]}`;
}

function FilterChip({ label, selected, onClick }) {
  return (
    <button type="button" onClick={onClick} style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer' }}>
      <Tag selected={selected}>{label}</Tag>
    </button>
  );
}

function JobRow({
  job, isSelected, onSelect, index, revealed,
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className="cb-op-job-row"
      style={{
        display: 'flex', gap: 14, alignItems: 'flex-start', justifyContent: 'space-between', width: '100%',
        padding: '18px 20px', border: 0, borderBottom: '1px solid var(--line-hairline)',
        borderLeft: `2px solid ${isSelected ? 'var(--ink-900)' : 'transparent'}`,
        background: isSelected ? 'var(--bone-200)' : 'transparent',
        font: 'inherit', cursor: 'pointer', textAlign: 'left', boxSizing: 'border-box',
        ...revealStyle(revealed, index, { step: 40, distance: 14, duration: 500 }),
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
        <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink-900)' }}>{job.title}</span>
        <span style={{ fontSize: 13, color: 'var(--ink-600)' }}>{job.meta}</span>
        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{job.salaryText} · closes {job.deadlineText}</span>
        {job.appliedLabel && (
          <span style={{ marginTop: 2, alignSelf: 'flex-start' }}>
            <Badge tone={job.appliedTone}>{job.appliedLabel}</Badge>
          </span>
        )}
      </div>
      <MatchScore value={job.match} size="md" />
    </button>
  );
}

export default function OpportunitiesPage() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [navCollapsed, setNavCollapsed] = useState(getNavCollapsed);
  const [loading, setLoading] = useState(true);
  const [studentName, setStudentName] = useState('');
  const [avatarSrc, setAvatarSrc] = useState('');
  const [jobs, setJobs] = useState([]);
  const [applications, setApplications] = useState([]);
  const [profileSkills, setProfileSkills] = useState([]);
  const [resumes, setResumes] = useState([]);

  const [query, setQuery] = useState('');
  const [jobTypeFilter, setJobTypeFilter] = useState(null);
  const [workModeFilter, setWorkModeFilter] = useState(null);

  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  // Jobs whose real requiredSkills we've fetched -- once known, use that instead of the title-guess.
  const [detailCache, setDetailCache] = useState({});

  const [coverNote, setCoverNote] = useState('');
  const [applying, setApplying] = useState(false);
  const [bookmarked, setBookmarked] = useState({});

  const [customResumeName, setCustomResumeName] = useState(null);
  const [resumeRemoved, setResumeRemoved] = useState(false);
  const [dragOver, setDragOver] = useState(false);

  const [toast, setToast] = useState({ visible: false, title: '', message: '', tone: 'success' });
  const [contentIn, setContentIn] = useState(false);
  const toastTimerRef = useRef(null);
  const detailTokenRef = useRef(0);

  useEffect(() => { getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {}); }, []);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getJobs(), getMyApplications(), getMyProfile(), getMyResumes()]).then(
      ([jobsRes, appsRes, profileRes, resumesRes]) => {
        if (cancelled) return;
        const jobList = jobsRes.status === 'fulfilled' ? jobsRes.value : [];
        setJobs(jobList);
        if (appsRes.status === 'fulfilled') setApplications(appsRes.value);
        if (profileRes.status === 'fulfilled' && profileRes.value) {
          setProfileSkills(profileRes.value.skills || []);
          setStudentName(`${profileRes.value.firstName || ''} ${profileRes.value.lastName || ''}`.trim());
        }
        if (resumesRes.status === 'fulfilled') setResumes(resumesRes.value);
        setLoading(false);
        setTimeout(() => setContentIn(true), 20);
        const firstActive = jobList.find((j) => j.isActive !== false);
        if (firstActive) selectJob(firstActive.id);
      },
    );
    getAvatarBlobUrl().then((url) => { if (!cancelled && url) setAvatarSrc(url); }).catch(() => {});
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => () => clearTimeout(toastTimerRef.current), []);

  const showToast = useCallback((title, message, tone = 'success') => {
    clearTimeout(toastTimerRef.current);
    setToast({ visible: true, title, message, tone });
    toastTimerRef.current = setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const selectJob = useCallback((id) => {
    const token = ++detailTokenRef.current;
    setSelectedId(id);
    setDetail(null);
    setDetailLoading(true);
    setCoverNote('');
    getJobDetail(id).then((d) => {
      if (token !== detailTokenRef.current) return;
      setDetail(d);
      setDetailLoading(false);
      setDetailCache((prev) => ({ ...prev, [id]: d }));
    }).catch(() => {
      if (token !== detailTokenRef.current) return;
      setDetailLoading(false);
    });
  }, []);

  const handleApply = useCallback(() => {
    if (!selectedId || applying) return;
    const job = jobs.find((j) => j.id === selectedId);
    setApplying(true);
    // Notes are the student's own scratch space, never sent as the coverLetter.
    applyToJob(selectedId, null).then((application) => {
      setApplications((prev) => [application, ...prev]);
      setApplying(false);
      showToast('Application sent', job ? `${job.title} at ${job.companyName}` : 'Application submitted', 'success');
    }).catch((err) => {
      setApplying(false);
      showToast('Could not apply', err.message, 'danger');
    });
  }, [selectedId, applying, jobs, showToast]);

  const clearFilters = useCallback(() => {
    setQuery('');
    setJobTypeFilter(null);
    setWorkModeFilter(null);
  }, []);

  const setCustomResume = useCallback((file) => {
    if (!file) return;
    setCustomResumeName(file.name);
    setResumeRemoved(false);
    setDragOver(false);
  }, []);

  const sidebarWidth = navCollapsed ? '60px' : '248px';

  const skillSet = useMemo(
    () => new Set((profileSkills || []).map((sk) => (sk.skillName || '').toLowerCase()).filter(Boolean)),
    [profileSkills],
  );

  const applicationByJob = useMemo(() => {
    const map = {};
    (applications || []).forEach((a) => {
      if (!map[a.jobId] || new Date(a.appliedAt) > new Date(map[a.jobId].appliedAt)) map[a.jobId] = a;
    });
    return map;
  }, [applications]);

  // Computed once here so typing in search doesn't re-run keyword matching on every keystroke.
  const jobsWithMatch = useMemo(() => (jobs || [])
    .filter((j) => j.isActive !== false)
    .map((j) => {
      const cached = detailCache[j.id];
      const exact = cached ? computeExactMatch(cached.requiredSkills, skillSet) : null;
      return { ...j, match: exact != null ? exact : computeListMatch(j.title, skillSet) };
    }), [jobs, skillSet, detailCache]);

  const filteredJobs = useMemo(() => {
    let list = jobsWithMatch;
    if (jobTypeFilter) list = list.filter((j) => j.jobType === jobTypeFilter);
    if (workModeFilter) list = list.filter((j) => j.workMode === workModeFilter);
    const q = query.trim().toLowerCase();
    if (q) list = list.filter((j) => `${j.title} ${j.companyName} ${j.location}`.toLowerCase().includes(q));
    return list;
  }, [jobsWithMatch, jobTypeFilter, workModeFilter, query]);

  const jobRows = useMemo(() => filteredJobs.map((j) => {
    const app = applicationByJob[j.id];
    return {
      id: j.id, title: j.title,
      meta: metaLine(j.location, j.workMode, JOB_TYPE_LABEL[j.jobType]),
      salaryText: fmtSalary(j.salaryMin, j.salaryMax, j.jobType),
      deadlineText: fmtDeadline(j.applicationDeadline),
      match: j.match,
      appliedTone: app ? STATUS_TONE[app.status] : 'default',
      appliedLabel: app ? STATUS_LABEL[app.status] : '',
    };
  }), [filteredJobs, applicationByJob]);

  const detailView = useMemo(() => {
    if (!detail) return null;
    const requiredSkills = detail.requiredSkills || [];
    const listMatch = computeListMatch(detail.title, skillSet);
    const exactMatch = computeExactMatch(requiredSkills, skillSet);
    const matchValue = exactMatch != null ? exactMatch : listMatch;
    const matchSourceLabel = exactMatch != null ? 'Your match' : 'Estimated match';
    const skillRows = requiredSkills.map((name) => ({ name, have: skillSet.has(name.toLowerCase()) }));
    const missing = skillRows.filter((r) => !r.have).map((r) => r.name);
    let skillNote = '';
    if (requiredSkills.length && !missing.length) skillNote = 'You have every required skill on this posting.';
    else if (requiredSkills.length) skillNote = `You have ${requiredSkills.length - missing.length} of ${requiredSkills.length} required skills. Missing: ${missing.join(', ')}.`;
    return {
      company: detail.companyName, title: detail.title,
      meta: metaLine(detail.location, detail.workMode, JOB_TYPE_LABEL[detail.jobType]),
      matchValue, matchSourceLabel,
      salaryShort: fmtSalaryShort(detail.salaryMin, detail.salaryMax, detail.jobType),
      salaryLabel: detail.jobType === 'INTERNSHIP' ? 'Stipend / month' : 'Salary band (LPA)',
      deadlineText: fmtDeadline(detail.applicationDeadline),
      skillRows, skillNote,
      description: detail.description || 'Full description arrives with the posting.',
    };
  }, [detail, skillSet]);

  const currentApp = applicationByJob[selectedId];
  const latestResume = resumes[0];
  const activeResumeName = customResumeName || (latestResume && !resumeRemoved ? latestResume.fileName : null);
  const jobTypeChips = Object.keys(JOB_TYPE_LABEL);
  const workModeChips = Object.keys(WORK_MODE_LABEL);
  const filtersActive = !!(query || jobTypeFilter || workModeFilter);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-op-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
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
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, overflow: 'hidden' }}>
              {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={15} />}
            </div>
            <div className="cb-op-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <Button variant="ghost" size="sm" onClick={() => { clearTokens(); navigate('/'); }}>Log out</Button>
        </div>
      </header>

      <div className="cb-op-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside style={{ borderRight: '1px solid var(--line-hairline)', position: 'sticky', top: 64, height: 'calc(100vh - 64px)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${navCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-op-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
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
                {!navCollapsed && <span className="cb-op-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          {!navCollapsed && (
            <div className="cb-op-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>See your PRS impact for every role you apply to.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers your top 3 matches. Upgrade for unlimited job matching and coach follow-ups.</p>
                <Link to="/plans" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        <main style={{ minWidth: 0, background: 'var(--surface-page)' }}>
          <div className="cb-op-main-pad" style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 64px', boxSizing: 'border-box' }}>

            {loading && <Skeleton height={520} />}

            {!loading && (
              <div className="cb-op-fade" style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap', ...revealStyle(contentIn, 0, { distance: 16 }) }}>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>
                    Opportunities · {jobRows.length} matched role{jobRows.length === 1 ? '' : 's'}
                  </span>
                  <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
                    <Badge tone="info">{applications.length} live application{applications.length === 1 ? '' : 's'}</Badge>
                    <Button
                      size="sm" variant="secondary" iconAfter="file-text" to="/resume"
                      state={detailView ? { jobDescription: detailView.description, jobTitle: detailView.title } : undefined}
                    >
                      Tailor my résumé
                    </Button>
                  </div>
                </div>

                <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', paddingBottom: 20, borderBottom: '1px solid var(--line-hairline)' }}>
                  <div style={{ minWidth: 280 }}>
                    <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search roles, companies or locations" />
                  </div>
                  {jobTypeChips.map((v) => (
                    <FilterChip key={v} label={JOB_TYPE_LABEL[v]} selected={jobTypeFilter === v} onClick={() => setJobTypeFilter((cur) => (cur === v ? null : v))} />
                  ))}
                  {workModeChips.map((v) => (
                    <FilterChip key={v} label={WORK_MODE_LABEL[v]} selected={workModeFilter === v} onClick={() => setWorkModeFilter((cur) => (cur === v ? null : v))} />
                  ))}
                  {filtersActive && (
                    <button type="button" onClick={clearFilters} style={{ display: 'flex', alignItems: 'center', gap: 5, border: 0, background: 'none', padding: '4px 6px', cursor: 'pointer', fontSize: 12, color: 'var(--ink-500)' }}>
                      <Icon name="x" size={14} />Clear filters
                    </button>
                  )}
                </div>

                <div className="cb-op-board" style={{ display: 'grid', gap: 24, alignItems: 'start' }}>

                  <div className="cb-op-job-list" style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', maxHeight: 'calc(100vh - 280px)', overflowY: 'auto', minWidth: 0 }}>
                    {jobRows.length === 0 && (
                      <div style={{ padding: '48px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                        <Icon name="briefcase" size={20} />
                        <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>No roles match</span>
                        <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Try clearing a filter or searching a broader term.</span>
                      </div>
                    )}
                    {jobRows.map((job, idx) => (
                      <JobRow key={job.id} job={job} isSelected={job.id === selectedId} onSelect={() => selectJob(job.id)} index={idx} revealed={contentIn} />
                    ))}
                  </div>

                  <div className="cb-op-detail" style={{ position: 'sticky', top: 84, border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', minWidth: 0 }}>
                    {jobRows.length === 0 && !detailLoading && (
                      <div style={{ padding: '48px 24px', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                        <Icon name="briefcase" size={20} />
                        <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>Nothing to show</span>
                        <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Adjust your filters to see roles.</span>
                      </div>
                    )}
                    {detailLoading && <div style={{ padding: 32 }}><Skeleton height={440} /></div>}

                    {!detailLoading && detailView && (
                      <div style={{ padding: 32, display: 'flex', flexDirection: 'column', gap: 24 }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'flex-start' }}>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>{detailView.company}</span>
                          <h1 className="cb-op-detail-title" style={{ fontFamily: 'var(--font-display)', fontSize: 34, lineHeight: 1.08, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{detailView.title}</h1>
                          <span style={{ fontSize: 14, color: 'var(--ink-600)' }}>{detailView.meta}</span>
                        </div>

                        <div className="cb-op-detail-stats" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                          <StatTile value={`${detailView.matchValue}%`} label={detailView.matchSourceLabel} />
                          <StatTile value={detailView.salaryShort} label={detailView.salaryLabel} />
                          <StatTile value={detailView.deadlineText} label="Closes" />
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Required skills</span>
                          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                            {detailView.skillRows.map((skill) => (
                              <Tag key={skill.name} selected={skill.have}>{skill.name}</Tag>
                            ))}
                          </div>
                          {detailView.skillNote && <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-500)', margin: '2px 0 0' }}>{detailView.skillNote}</p>}
                        </div>

                        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>About the role</span>
                          <p style={{ fontSize: 14, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>{detailView.description}</p>
                        </div>

                        {activeResumeName ? (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, padding: '14px 16px', border: '1px solid var(--line-hairline)', background: 'var(--bone-100)' }}>
                            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10, minWidth: 0 }}>
                              <Icon name="file-text" size={18} style={{ flexShrink: 0, marginTop: 2 }} />
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                                <span style={{ fontSize: 13, color: 'var(--ink-900)', wordBreak: 'break-word' }}>{activeResumeName}</span>
                                <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>
                                  {customResumeName ? 'Custom upload · stored on this device only' : (latestResume ? `v${latestResume.version} · ${Math.round(latestResume.atsScore || 0)}% ATS match` : '')}
                                </span>
                              </div>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12 }}>
                              <span style={{ fontSize: 12, color: 'var(--ink-400)', minWidth: 0 }}>Recruiters can view this on your application.</span>
                              <button type="button" onClick={() => { setCustomResumeName(null); setResumeRemoved(true); }} style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer', fontSize: 12, color: 'var(--status-danger)', flexShrink: 0, whiteSpace: 'nowrap' }}>Delete</button>
                            </div>
                          </div>
                        ) : (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                            <div
                              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
                              onDragLeave={() => setDragOver(false)}
                              onDrop={(e) => { e.preventDefault(); setCustomResume(e.dataTransfer.files && e.dataTransfer.files[0]); }}
                              style={{
                                display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 8, padding: '24px 16px',
                                border: `1px dashed ${dragOver ? 'var(--line-ink)' : 'var(--line-strong)'}`,
                                background: dragOver ? 'var(--bone-100)' : 'transparent', textAlign: 'center',
                              }}
                            >
                              <Icon name="file-text" size={20} />
                              <span style={{ fontSize: 13, color: 'var(--ink-700)' }}>Drag and drop your résumé, or</span>
                              <label style={{ fontSize: 13, color: 'var(--ink-900)', textDecoration: 'underline', cursor: 'pointer' }}>
                                Browse files
                                <input type="file" accept=".pdf,.doc,.docx" onChange={(e) => setCustomResume(e.target.files && e.target.files[0])} style={{ display: 'none' }} />
                              </label>
                              <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>PDF or Word · stored on this device only</span>
                            </div>
                            {latestResume && resumeRemoved && !customResumeName && (
                              <Button size="sm" variant="secondary" iconAfter="refresh-cw" fullWidth onClick={() => { setCustomResumeName(null); setResumeRemoved(false); }}>Use my CareerBridge résumé</Button>
                            )}
                            {!latestResume && !customResumeName && (
                              <Alert tone="danger" title="No résumé on file" message="Recruiters will see only your profile until you generate or upload one." />
                            )}
                          </div>
                        )}

                        <Field label="Notes before you apply" hint="Not saved — for your own reference only">
                          <Textarea rows={4} value={coverNote} onChange={(e) => setCoverNote(e.target.value)} placeholder="Why this role, what to raise in the interview…" />
                        </Field>

                        <div style={{ display: 'flex', gap: 12, alignItems: 'center', paddingTop: 8, borderTop: '1px solid var(--line-hairline)' }}>
                          {!currentApp ? (
                            <Button size="lg" iconAfter="arrow-right" onClick={handleApply} disabled={applying}>{applying ? 'Applying…' : 'Apply to this role'}</Button>
                          ) : (
                            <Badge tone={STATUS_TONE[currentApp.status]}>{STATUS_LABEL[currentApp.status]}</Badge>
                          )}
                          <IconButton
                            icon="bookmark"
                            label={bookmarked[selectedId] ? 'Remove saved role' : 'Save role'}
                            variant="secondary"
                            iconStyle={bookmarked[selectedId] ? { color: 'var(--ink-900)', fill: 'var(--ink-900)' } : undefined}
                            onClick={() => setBookmarked((b) => ({ ...b, [selectedId]: !b[selectedId] }))}
                          />
                        </div>
                      </div>
                    )}
                  </div>
                </div>

                <section style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Your applications</span>
                    <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                  </div>
                  {applications.length === 0 ? (
                    <div style={{ padding: '32px 20px', border: '1px solid var(--line-hairline)', textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
                      <Icon name="briefcase" size={20} />
                      <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>No applications yet</span>
                      <span style={{ fontSize: 12.5, color: 'var(--text-secondary)' }}>Apply to a role above — it will show up here.</span>
                    </div>
                  ) : (
                    <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-page)' }}>
                      {applications.map((a, idx) => (
                        <div
                          key={a.id}
                          style={{
                            padding: '14px 18px', borderBottom: '1px solid var(--line-hairline)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 14,
                            ...revealStyle(contentIn, idx, { step: 40 }),
                          }}
                        >
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
                            <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{a.jobTitle}</span>
                            <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtAppliedDate(a.appliedAt)}</span>
                          </div>
                          <Badge tone={STATUS_TONE[a.status]}>{STATUS_LABEL[a.status]}</Badge>
                        </div>
                      ))}
                    </div>
                  )}
                </section>

              </div>
            )}

          </div>
        </main>
      </div>

      {toast.visible && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 60, maxWidth: 360 }}>
          <div style={{ background: toast.tone === 'danger' ? 'var(--status-danger)' : 'var(--ink-900)', color: 'var(--bone-50)', padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, boxShadow: 'var(--shadow-menu)' }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</span>
            <span style={{ fontSize: 13, color: 'var(--bone-300)' }}>{toast.message}</span>
          </div>
        </div>
      )}

    </div>
  );
}
