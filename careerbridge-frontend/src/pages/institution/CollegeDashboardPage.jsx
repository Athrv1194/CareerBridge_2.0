import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge, Button, Field, Icon, IconButton, Input, Logo, revealStyle, ScoreRing, Select, Skeleton,
  StatTile, Textarea, useRevealOnMount,
} from '../../components/ui';
import {
  getOrganization, listDepartments, createDepartment, updateOrganization,
  getPlatformStats, getLeaderboard, listUsers, deactivateUser, activateUser, getPlacementStats,
  assignUserDepartment,
} from '../../api/adminApi';
import { listOrgJoinRequests, approveOrgJoinRequest, rejectOrgJoinRequest } from '../../api/orgJoinApi';
import { getTokenPayload, getDisplayName } from '../../utils/tokenUtils';
import './college.css';

const ROLE_REDIRECT = { STUDENT: '/dashboard', RECRUITER: '/recruiter-console', PLACEMENT_OFFICER: '/placement-console', SUPER_ADMIN: '/super-admin', MENTOR: '/mentor-console' };
const PLAN_BADGE = { FREE: { tone: 'default', label: 'FREE' }, STUDENT_PREMIUM: { tone: 'accent', label: 'PLUS' } };
const GRADE_TONE = { A: 'success', B: 'info', C: 'warning', D: 'danger', F: 'danger' };
const TABS = [{ value: 'OVERVIEW', label: 'Overview' }, { value: 'STUDENTS', label: 'Students' }, { value: 'DEPARTMENTS', label: 'Departments' }, { value: 'PLACEMENT', label: 'Placement' }, { value: 'SETTINGS', label: 'Settings' }];

function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}
function fmtMemberSince(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
}
function fmtCtc(v) { return v == null ? '—' : `₹${Number(v).toFixed(1)}L`; }
function prsBarColor(score) {
  if (score >= 80) return 'var(--status-success)';
  if (score >= 60) return 'var(--ink-300)';
  if (score >= 40) return 'var(--status-warning)';
  return 'var(--status-danger)';
}

function Tabs({ items, value, onChange }) {
  return (
    <div style={{ display: 'flex', gap: 4 }}>
      {items.map((it) => (
        <button
          key={it.value}
          type="button"
          onClick={() => onChange(it.value)}
          style={{ border: 0, borderBottom: `2px solid ${value === it.value ? 'var(--ink-900)' : 'transparent'}`, background: 'none', padding: '12px 16px', fontSize: 13.5, fontWeight: 500, cursor: 'pointer', color: value === it.value ? 'var(--ink-900)' : 'var(--ink-400)' }}
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

function SectionHeader({ label, action }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 12, borderBottom: '1px solid var(--ink-900)', paddingBottom: 10 }}>
      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>{label}</span>
      {action && <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{action}</span>}
    </div>
  );
}

export default function CollegeDashboardPage() {
  const navigate = useNavigate();
  const [adminName, setAdminName] = useState('');
  const [orgId, setOrgId] = useState(null);
  const [activeTab, setActiveTab] = useState('OVERVIEW');
  const [ready, setReady] = useState(false);
  const [tabIn, setTabIn] = useState(false);
  const topbarIn = useRevealOnMount();

  useEffect(() => {
    setTabIn(false);
    const t = setTimeout(() => setTabIn(true), 20);
    return () => clearTimeout(t);
  }, [activeTab]);

  const [org, setOrg] = useState(null);
  const [stats, setStats] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);
  const [overviewLoaded, setOverviewLoaded] = useState(false);

  const [joinRequests, setJoinRequests] = useState([]);
  const [joinRequestsBusyId, setJoinRequestsBusyId] = useState(null);

  const [students, setStudents] = useState([]);
  const [studentsLoaded, setStudentsLoaded] = useState(false);
  const [studentQuery, setStudentQuery] = useState('');
  const [studentStatus, setStudentStatus] = useState('ALL');
  const [studentDept, setStudentDept] = useState('ALL');
  const [confirmDeactivateId, setConfirmDeactivateId] = useState(null);
  const [busyStudentId, setBusyStudentId] = useState(null);
  const [deptBusyId, setDeptBusyId] = useState(null);

  const [departments, setDepartments] = useState([]);
  const [departmentsLoaded, setDepartmentsLoaded] = useState(false);
  const [deptFormOpen, setDeptFormOpen] = useState(false);
  const [deptName, setDeptName] = useState('');
  const [deptDescription, setDeptDescription] = useState('');
  const [deptSaving, setDeptSaving] = useState(false);
  const [deptError, setDeptError] = useState('');

  const [placement, setPlacement] = useState(null);
  const [placementLoaded, setPlacementLoaded] = useState(false);

  const [settingsDraft, setSettingsDraft] = useState(null);
  const [settingsSaving, setSettingsSaving] = useState(false);
  const [settingsError, setSettingsError] = useState('');

  const [toast, setToast] = useState(null);
  const toastTimer = useRef(null);

  const showToast = useCallback((title, message, tone = 'success') => {
    clearTimeout(toastTimer.current);
    setToast({ title, message, tone });
    toastTimer.current = setTimeout(() => setToast(null), 4200);
  }, []);
  useEffect(() => () => clearTimeout(toastTimer.current), []);

  useEffect(() => {
    const payload = getTokenPayload();
    if (!payload) {
      navigate('/login', { replace: true });
      return;
    }
    if (payload.role !== 'ORG_ADMIN') {
      navigate(ROLE_REDIRECT[payload.role] || '/dashboard', { replace: true });
      return;
    }
    const id = payload?.organizationId ?? payload?.orgId ?? null;
    setOrgId(id);
    setAdminName(getDisplayName('Org admin'));
    setReady(true);
  }, [navigate]);

  useEffect(() => {
    if (!ready || !orgId) return;
    getOrganization(orgId).then((o) => {
      setOrg(o);
      setSettingsDraft({ name: o.name || '', type: o.type || '', contactEmail: o.contactEmail || '', contactPhone: o.contactPhone || '', address: o.address || '', city: o.city || '', state: o.state || '' });
    }).catch(() => {});
    Promise.all([getPlatformStats().catch(() => null), getLeaderboard().catch(() => [])]).then(([st, lb]) => {
      setStats(st);
      setLeaderboard(lb || []);
      setOverviewLoaded(true);
    });
  }, [ready, orgId]);

  const loadJoinRequests = useCallback(() => {
    if (!orgId) return;
    listOrgJoinRequests('PENDING').then(setJoinRequests).catch(() => {});
  }, [orgId]);
  useEffect(() => { loadJoinRequests(); }, [loadJoinRequests]);

  const reviewJoinRequest = (id, approve) => {
    setJoinRequestsBusyId(id);
    const action = approve ? approveOrgJoinRequest : rejectOrgJoinRequest;
    action(id)
      .then(() => {
        setJoinRequests((prev) => prev.filter((r) => r.id !== id));
        showToast(approve ? 'Request approved' : 'Request rejected', approve ? 'The student is now linked to your institution.' : undefined);
        if (approve) loadStudents();
      })
      .catch((e) => showToast('Could not update request', e.message, 'danger'))
      .finally(() => setJoinRequestsBusyId(null));
  };

  const loadStudents = useCallback(() => {
    listUsers('STUDENT').then((data) => { setStudents(data); setStudentsLoaded(true); }).catch(() => setStudentsLoaded(true));
  }, []);
  const loadDepartments = useCallback(() => {
    if (!orgId) return;
    listDepartments(orgId).then((data) => { setDepartments(data); setDepartmentsLoaded(true); }).catch(() => setDepartmentsLoaded(true));
  }, [orgId]);
  const loadPlacement = useCallback(() => {
    getPlacementStats().then((data) => { setPlacement(data); setPlacementLoaded(true); }).catch(() => setPlacementLoaded(true));
  }, []);

  const goToTab = (tab) => {
    setActiveTab(tab);
    // Students needs the department list too -- it drives the per-row assignment dropdown, so
    // without it a roster opened directly would offer no departments to assign.
    if (tab === 'STUDENTS' && !studentsLoaded) loadStudents();
    if ((tab === 'STUDENTS' || tab === 'DEPARTMENTS') && !departmentsLoaded) loadDepartments();
    // Departments shows a per-department headcount computed from the roster, so it needs students.
    if (tab === 'DEPARTMENTS' && !studentsLoaded) loadStudents();
    if (tab === 'PLACEMENT' && !placementLoaded) loadPlacement();
  };

  const changeDepartment = (u, department) => {
    setDeptBusyId(u.id);
    const next = department || null;
    assignUserDepartment(u.id, next)
      .then(() => {
        setStudents((prev) => prev.map((x) => (x.id === u.id ? { ...x, department: next } : x)));
        showToast(next ? 'Department assigned' : 'Department cleared',
          `${u.firstName} ${u.lastName}${next ? ` → ${next}` : ''}`, 'success');
      })
      .catch((e) => showToast('Could not update department', e.message, 'danger'))
      .finally(() => setDeptBusyId(null));
  };

  const askDeactivate = (u) => setConfirmDeactivateId(u.id);
  const cancelDeactivate = () => setConfirmDeactivateId(null);
  const confirmDeactivate = (u) => {
    setBusyStudentId(u.id);
    deactivateUser(u.id).catch(() => {}).finally(() => {
      setStudents((prev) => prev.map((x) => (x.id === u.id ? { ...x, isDeleted: true } : x)));
      setConfirmDeactivateId(null);
      setBusyStudentId(null);
      showToast('Student deactivated', `${u.firstName} ${u.lastName}`, 'success');
    });
  };
  const activate = (u) => {
    setBusyStudentId(u.id);
    activateUser(u.id).catch(() => {}).finally(() => {
      setStudents((prev) => prev.map((x) => (x.id === u.id ? { ...x, isDeleted: false } : x)));
      setBusyStudentId(null);
      showToast('Student activated', `${u.firstName} ${u.lastName}`, 'success');
    });
  };

  const submitDept = () => {
    if (!deptName.trim()) { setDeptError('Department name is required.'); return; }
    setDeptSaving(true);
    setDeptError('');
    createDepartment(orgId, { name: deptName.trim(), description: deptDescription.trim() || null })
      .then((saved) => {
        setDepartments((prev) => [saved, ...prev]);
        setDeptFormOpen(false);
        setDeptName('');
        setDeptDescription('');
        showToast('Department created', saved.name, 'success');
      })
      .catch((e) => setDeptError(e.message))
      .finally(() => setDeptSaving(false));
  };
  const closeDeptForm = () => { setDeptFormOpen(false); setDeptError(''); setDeptName(''); setDeptDescription(''); };

  const submitSettings = () => {
    if (!settingsDraft.name.trim()) { setSettingsError('Organisation name is required.'); return; }
    setSettingsSaving(true);
    setSettingsError('');
    const body = {};
    Object.entries(settingsDraft).forEach(([k, v]) => { body[k] = v === '' ? null : v; });
    updateOrganization(orgId, body)
      .then((updated) => {
        setOrg(updated);
        showToast('Organisation profile updated.', '', 'success');
      })
      .catch((e) => setSettingsError(e.message))
      .finally(() => setSettingsSaving(false));
  };

  const isActive = org ? org.isActive !== false : true;
  const deptCount = departments.length || (org?.departments?.length ?? 0);
  const leaderboardTop = useMemo(() => leaderboard.slice(0, 10), [leaderboard]);

  const filteredStudents = useMemo(() => {
    const q = studentQuery.trim().toLowerCase();
    return students.filter((u) => {
      if (q && !`${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(q)) return false;
      if (studentStatus === 'ACTIVE' && u.isDeleted) return false;
      if (studentStatus === 'DEACTIVATED' && !u.isDeleted) return false;
      if (studentDept === 'UNASSIGNED' && u.department) return false;
      if (studentDept !== 'ALL' && studentDept !== 'UNASSIGNED' && u.department !== studentDept) return false;
      return true;
    });
  }, [students, studentQuery, studentStatus, studentDept]);

  // Headcount per department name, plus the unassigned tail. Computed from the roster already in
  // memory rather than a new endpoint -- both lists are loaded for these two tabs anyway.
  const deptCounts = useMemo(() => {
    const counts = {};
    let unassigned = 0;
    students.forEach((u) => {
      if (u.department) counts[u.department] = (counts[u.department] || 0) + 1;
      else unassigned += 1;
    });
    return { counts, unassigned };
  }, [students]);
  const studentsEmptyAll = studentsLoaded && students.length === 0;
  const studentsNoMatch = studentsLoaded && students.length > 0 && filteredStudents.length === 0;

  if (!ready) return null;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <header style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 24, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)', paddingLeft: 24, borderLeft: '1px solid var(--line-hairline)' }}>College</span>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Icon name="user" size={15} />
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{adminName}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Org admin</span>
            </div>
          </div>
        </div>
      </header>

      <div style={{ background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div style={{ maxWidth: 1320, margin: '0 auto', padding: '26px 32px', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap', boxSizing: 'border-box', ...revealStyle(topbarIn, 0, { distance: 16 }) }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>College dashboard</span>
            <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{org?.name || 'Loading…'}</h1>
            <span style={{ fontSize: 14, color: 'var(--ink-400)' }}>{[org?.city, org?.state].filter(Boolean).join(', ')}</span>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexShrink: 0 }}>
            {orgId != null && (
              <span
                title="Give this to students, placement officers and mentors -- they enter it as Organization ID when they register."
                style={{ fontSize: 12, color: 'var(--ink-600)', border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-pill)', padding: '6px 14px', display: 'flex', alignItems: 'center', gap: 6 }}
              >
                Organization ID <span className="cb-num" style={{ fontWeight: 600, color: 'var(--ink-900)' }}>{orgId}</span>
              </span>
            )}
            <Badge tone={isActive ? 'success' : 'danger'}>{isActive ? 'ACTIVE' : 'INACTIVE'}</Badge>
          </div>
        </div>
      </div>

      <div style={{ position: 'sticky', top: 64, zIndex: 30, background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div style={{ display: 'flex', justifyContent: 'center', padding: '0 32px', boxSizing: 'border-box' }}>
          <Tabs items={TABS} value={activeTab} onChange={goToTab} />
        </div>
      </div>

      <main style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 90px', boxSizing: 'border-box' }}>
        <div style={revealStyle(tabIn, 0, { distance: 16, duration: 380 })}>

        {activeTab === 'OVERVIEW' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
            {joinRequests.length > 0 && (
              <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)' }}>
                <div style={{ padding: '14px 20px', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 10 }}>
                  <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>Pending join requests</span>
                  <Badge tone="warning">{joinRequests.length}</Badge>
                </div>
                {joinRequests.map((r) => (
                  <div key={r.id} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14, padding: '14px 20px', borderBottom: '1px solid var(--line-hairline)' }}>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0 }}>
                      <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{r.firstName} {r.lastName} <span style={{ color: 'var(--ink-400)' }}>({r.email})</span></span>
                      <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{r.role?.replace('_', ' ')} · requested {fmtDate(r.requestedAt)}</span>
                    </div>
                    <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                      <Button variant="ghost" size="sm" disabled={joinRequestsBusyId === r.id} onClick={() => reviewJoinRequest(r.id, false)} style={{ color: 'var(--status-danger)' }}>Reject</Button>
                      <Button variant="primary" size="sm" disabled={joinRequestsBusyId === r.id} onClick={() => reviewJoinRequest(r.id, true)}>Approve</Button>
                    </div>
                  </div>
                ))}
              </div>
            )}

            {!overviewLoaded ? <Skeleton height={110} /> : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }} className="cb-college-stat4-grid">
                <StatTile value={stats?.totalStudents ?? 0} label="Total students" style={revealStyle(overviewLoaded, 0)} />
                <StatTile value={stats?.activeUsers ?? 0} label="Active members" style={revealStyle(overviewLoaded, 1)} />
                <StatTile value={stats?.totalPlacementOfficers ?? 0} label="Placement officers" style={revealStyle(overviewLoaded, 2)} />
                <StatTile value={stats?.totalMentors ?? 0} label="Mentors" style={revealStyle(overviewLoaded, 3)} />
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '3fr 2fr', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', alignItems: 'stretch' }} className="cb-college-overview-grid">
              <div style={{ background: 'var(--bone-50)', padding: '30px 32px', display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0, boxSizing: 'border-box' }}>
                <SectionHeader label="Top students" />
                <span style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: -8 }}>Placement Readiness Score — your college ranking.</span>

                {overviewLoaded && leaderboardTop.length === 0 && (
                  <EmptyState icon="chart-no-axes-column" title="No scores yet" message="No placement scores yet. Students need to complete their assessment first." />
                )}
                {leaderboardTop.length > 0 && (
                  <>
                    <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', overflowX: 'auto' }}>
                      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                        <thead><tr>
                          <th style={thStyle('left')}>Rank</th>
                          <th style={thStyle('left')}>Student ID</th>
                          <th style={thStyle('right')}>Score</th>
                          <th style={thStyle('center')}>Grade</th>
                        </tr></thead>
                        <tbody>
                          {leaderboardTop.map((row) => (
                            <tr key={row.studentId}>
                              <td style={tdStyle('left')} className="cb-num">#{row.rank}</td>
                              <td style={tdStyle('left')} className="cb-num">Student #{row.studentId}</td>
                              <td style={tdStyle('right')}>
                                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10 }}>
                                  <span className="cb-num">{Math.round(row.totalScore)}</span>
                                  <div style={{ width: 60, height: 4, background: 'var(--bone-300)', flexShrink: 0 }}><div style={{ width: `${Math.max(0, Math.min(100, row.totalScore))}%`, height: '100%', background: prsBarColor(row.totalScore) }} /></div>
                                </div>
                              </td>
                              <td style={tdStyle('center')}><Badge tone={GRADE_TONE[row.grade] || 'default'}>{row.grade}</Badge></td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <Button variant="ghost" size="sm" iconAfter="arrow-right" onClick={() => goToTab('STUDENTS')}>View all</Button>
                    </div>
                  </>
                )}
              </div>

              <div style={{ background: 'var(--bone-50)', padding: '30px 32px', display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, boxSizing: 'border-box' }}>
                <SectionHeader label="College at a glance" />
                <InfoRow label="Total departments" value={deptCount} />
                <InfoRow label="Total org admins" value={stats ? stats.totalOrgAdmins : '—'} />
                <InfoRow label="Total recruiters" value={stats ? stats.totalRecruiters : '—'} />
                <InfoRow label="Joined" value={fmtMemberSince(org?.createdAt)} />
                <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '12px 0' }}>
                  <span style={{ fontSize: 13, color: 'var(--ink-600)' }}>Contact</span>
                  {org?.contactEmail ? <a href={`mailto:${org.contactEmail}`} style={{ fontSize: 13, color: 'var(--taupe-700)' }}>{org.contactEmail}</a> : <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>—</span>}
                </div>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-500)', marginTop: 16 }}>{[org?.address, org?.city, org?.state].filter(Boolean).join(', ') || '—'}</p>
              </div>
            </div>
          </div>
        )}

        {activeTab === 'STUDENTS' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
            <SectionHeader label="Student roster" action={`Total: ${students.length}`} />

            <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
              <div style={{ minWidth: 280, flex: 1 }}>
                <Input placeholder="Search by name or email…" value={studentQuery} onChange={(e) => setStudentQuery(e.target.value)} />
              </div>
              {departments.length > 0 && (
                <Select
                  value={studentDept}
                  onChange={(e) => setStudentDept(e.target.value)}
                  style={{ flexShrink: 0 }}
                  options={[
                    { value: 'ALL', label: 'All departments' },
                    ...departments.map((d) => ({ value: d.name, label: d.name })),
                    { value: 'UNASSIGNED', label: 'Unassigned' },
                  ]}
                />
              )}
              <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                <Button size="sm" variant={studentStatus === 'ALL' ? 'primary' : 'ghost'} onClick={() => setStudentStatus('ALL')}>All</Button>
                <Button size="sm" variant={studentStatus === 'ACTIVE' ? 'primary' : 'ghost'} onClick={() => setStudentStatus('ACTIVE')}>Active</Button>
                <Button size="sm" variant={studentStatus === 'DEACTIVATED' ? 'primary' : 'ghost'} onClick={() => setStudentStatus('DEACTIVATED')}>Deactivated</Button>
              </div>
            </div>

            {!studentsLoaded && <Skeleton height={320} />}
            {studentsEmptyAll && (
              <EmptyState
                icon="users"
                title="No students yet"
                message={orgId != null
                  ? `Share Organization ID ${orgId} — students enter it on the register page, under Organization ID.`
                  : 'Students join by registering with your organization ID.'}
              />
            )}
            {studentsNoMatch && <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No students match your search.</span>}

            {studentsLoaded && !studentsEmptyAll && !studentsNoMatch && (
              <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>
                  <thead><tr>
                    <th style={thStyle('left')}>Name</th>
                    <th style={thStyle('left')}>Email</th>
                    <th style={thStyle('left')}>Role</th>
                    <th style={thStyle('left')}>Department</th>
                    <th style={thStyle('left')}>Plan</th>
                    <th style={thStyle('right')}>Joined</th>
                    <th style={thStyle('left')}>Status</th>
                    <th style={thStyle('right')}>Actions</th>
                  </tr></thead>
                  <tbody>
                    {filteredStudents.map((u) => {
                      const plan = PLAN_BADGE[u.subscriptionPlan] || { tone: 'default', label: u.subscriptionPlan || '—' };
                      const confirming = confirmDeactivateId === u.id;
                      const busy = busyStudentId === u.id;
                      return (
                        <tr key={u.id}>
                          <td style={tdStyle('left')}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                              <div style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, fontSize: 11, fontWeight: 600, color: 'var(--ink-700)' }}>
                                {`${u.firstName?.[0] || ''}${u.lastName?.[0] || ''}`.toUpperCase()}
                              </div>
                              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', whiteSpace: 'nowrap' }}>{u.firstName} {u.lastName}</span>
                            </div>
                          </td>
                          <td style={{ ...tdStyle('left'), color: 'var(--ink-500)' }}>{u.email}</td>
                          <td style={tdStyle('left')}><Badge tone="default">{u.role}</Badge></td>
                          <td style={tdStyle('left')}>
                            {departments.length === 0 ? (
                              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Add a department first</span>
                            ) : (
                              <Select
                                value={u.department || ''}
                                onChange={(e) => changeDepartment(u, e.target.value)}
                                style={{ minWidth: 150, opacity: deptBusyId === u.id ? 0.5 : 1 }}
                                options={[
                                  { value: '', label: 'Unassigned' },
                                  ...departments.map((d) => ({ value: d.name, label: d.name })),
                                  // Department is free text, so a stored value can outlive the
                                  // department it named (renamed, or created before this list).
                                  // Without this the select falls back to blank and the row reads
                                  // as unassigned while the database says otherwise.
                                  ...(u.department && !departments.some((d) => d.name === u.department)
                                    ? [{ value: u.department, label: `${u.department} (not in list)` }]
                                    : []),
                                ]}
                              />
                            )}
                          </td>
                          <td style={tdStyle('left')}><Badge tone={plan.tone}>{plan.label}</Badge></td>
                          <td style={{ ...tdStyle('right'), color: 'var(--ink-500)' }} className="cb-num">{fmtDate(u.createdAt)}</td>
                          <td style={tdStyle('left')}><Badge tone={u.isDeleted ? 'danger' : 'success'}>{u.isDeleted ? 'DEACTIVATED' : 'ACTIVE'}</Badge></td>
                          <td style={{ ...tdStyle('right') }}>
                            {!u.isDeleted && !confirming && (
                              <Button variant="ghost" size="sm" onClick={() => askDeactivate(u)} style={{ color: 'var(--status-danger)' }}>Deactivate</Button>
                            )}
                            {confirming && (
                              <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                                <span style={{ fontSize: 12, color: 'var(--ink-600)', whiteSpace: 'nowrap' }}>Deactivate {u.firstName}?</span>
                                <Button variant="primary" size="sm" disabled={busy} onClick={() => confirmDeactivate(u)} style={{ background: 'var(--status-danger)', border: '1px solid var(--status-danger)' }}>{busy ? '…' : 'Confirm'}</Button>
                                <Button variant="ghost" size="sm" onClick={cancelDeactivate}>Cancel</Button>
                              </div>
                            )}
                            {!!u.isDeleted && (
                              <Button variant="ghost" size="sm" disabled={busy} onClick={() => activate(u)}>{busy ? '…' : 'Activate'}</Button>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {activeTab === 'DEPARTMENTS' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="primary" size="md" iconAfter="plus" onClick={() => setDeptFormOpen(true)}>Add department</Button>
            </div>

            {deptFormOpen && (
              <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 16, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 14 }}>
                <Field label="Name"><Input value={deptName} onChange={(e) => setDeptName(e.target.value)} placeholder="Computer Science and Engineering" /></Field>
                <Field label="Description"><Textarea rows={2} value={deptDescription} onChange={(e) => setDeptDescription(e.target.value)} placeholder="What the department covers." /></Field>
                {deptError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{deptError}</span>}
                <div style={{ display: 'flex', gap: 10 }}>
                  <Button variant="primary" size="sm" disabled={deptSaving} onClick={submitDept}>{deptSaving ? 'Creating…' : 'Create'}</Button>
                  <Button variant="ghost" size="sm" onClick={closeDeptForm}>Cancel</Button>
                </div>
              </div>
            )}

            {!departmentsLoaded && <Skeleton height={220} />}
            {departmentsLoaded && departments.length === 0 && (
              <EmptyState icon="building-2" title="No departments yet" message="Add departments to organise your institution." action={<Button variant="primary" size="sm" onClick={() => setDeptFormOpen(true)}>Add first department</Button>} />
            )}
            {departments.length > 0 && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }} className="cb-college-dept-grid">
                {departments.map((dept) => {
                  const headcount = deptCounts.counts[dept.name] || 0;
                  return (
                    <div key={dept.id} style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 16, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 10 }}>
                        <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{dept.name}</span>
                        <Badge tone={headcount > 0 ? 'default' : 'warning'}>
                          {headcount} {headcount === 1 ? 'student' : 'students'}
                        </Badge>
                      </div>
                      <span style={{ fontSize: 13, color: 'var(--ink-500)', lineHeight: 1.5, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{dept.description || '—'}</span>
                      <button
                        type="button"
                        onClick={() => { setStudentDept(dept.name); goToTab('STUDENTS'); }}
                        style={{ alignSelf: 'flex-start', border: 'none', background: 'transparent', padding: 0, cursor: 'pointer', fontSize: 12, fontFamily: 'var(--font-sans)', color: 'var(--taupe-700)' }}
                      >
                        View students →
                      </button>
                    </div>
                  );
                })}
              </div>
            )}
            {departments.length > 0 && studentsLoaded && deptCounts.unassigned > 0 && (
              <span style={{ fontSize: 13, color: 'var(--ink-500)' }}>
                {deptCounts.unassigned} {deptCounts.unassigned === 1 ? 'student is' : 'students are'} not
                assigned to a department yet — assign them from the Students tab.
              </span>
            )}
          </div>
        )}

        {activeTab === 'PLACEMENT' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
            {!placementLoaded && <Skeleton height={110} />}
            {placementLoaded && (
              <>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }} className="cb-college-placement-grid">
                  <div style={{ background: 'var(--bone-50)', padding: '20px 22px', display: 'flex', flexDirection: 'column', gap: 6, boxSizing: 'border-box' }}>
                    <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 34, lineHeight: 1.05, letterSpacing: '-.01em', color: 'var(--ink-900)' }}>{placement?.totalStudentsInScope ?? 0}</span>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Students in scope</span>
                      <Icon name="triangle-alert" size={12} style={{ color: 'var(--ink-400)' }} title="This count comes from a live service — 0 may mean the service was temporarily unavailable." />
                    </div>
                  </div>
                  <StatTile value={placement?.totalApplications ?? 0} label="Total applications" />
                  <div style={{ background: 'var(--bone-50)', padding: 20, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, justifyContent: 'center', boxSizing: 'border-box' }}>
                    <ScoreRing value={Math.round(placement?.placementRate || 0)} size="sm" />
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Placement rate</span>
                  </div>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }} className="cb-college-placement-grid">
                  <StatTile value={placement?.offersExtended ?? 0} label="Offers extended" />
                  <StatTile value={placement?.offersAccepted ?? 0} label="Offers accepted" />
                  <StatTile value={placement?.offersDeclined ?? 0} label="Offers declined" />
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 1, background: 'var(--ink-900)', border: '1px solid var(--ink-900)' }} className="cb-college-ctc-grid">
                  <StatTile tone="inverse" value={fmtCtc(placement?.averageCtc)} label="Average CTC" />
                  <StatTile tone="inverse" value={fmtCtc(placement?.highestCtc)} label="Highest CTC" />
                </div>

                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <SectionHeader label="Top hiring companies" />
                  {(!placement?.topCompanies || placement.topCompanies.length === 0) && (
                    <EmptyState icon="award" title="No accepted offers recorded yet." />
                  )}
                  {(placement?.topCompanies || []).map((name, i) => (
                    <div key={name} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 4px', borderBottom: '1px solid var(--line-hairline)' }}>
                      <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-400)', width: 28 }}>#{i + 1}</span>
                      <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{name}</span>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>
        )}

        {activeTab === 'SETTINGS' && settingsDraft && (
          <div style={{ maxWidth: 640, display: 'flex', flexDirection: 'column', gap: 24 }}>
            <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 24, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 16 }}>
              <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>Organisation profile</h2>

              <Field label="Name"><Input value={settingsDraft.name} onChange={(e) => setSettingsDraft((d) => ({ ...d, name: e.target.value }))} /></Field>
              <Field label="Institution type"><Input value={settingsDraft.type} onChange={(e) => setSettingsDraft((d) => ({ ...d, type: e.target.value }))} placeholder="University, College…" /></Field>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }} className="cb-college-settings-grid">
                <Field label="Contact email"><Input type="email" value={settingsDraft.contactEmail} onChange={(e) => setSettingsDraft((d) => ({ ...d, contactEmail: e.target.value }))} /></Field>
                <Field label="Contact phone"><Input value={settingsDraft.contactPhone} onChange={(e) => setSettingsDraft((d) => ({ ...d, contactPhone: e.target.value }))} /></Field>
              </div>
              <Field label="Address"><Textarea rows={2} value={settingsDraft.address} onChange={(e) => setSettingsDraft((d) => ({ ...d, address: e.target.value }))} /></Field>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }} className="cb-college-settings-grid">
                <Field label="City"><Input value={settingsDraft.city} onChange={(e) => setSettingsDraft((d) => ({ ...d, city: e.target.value }))} /></Field>
                <Field label="State"><Input value={settingsDraft.state} onChange={(e) => setSettingsDraft((d) => ({ ...d, state: e.target.value }))} /></Field>
              </div>

              {settingsError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{settingsError}</span>}

              <div>
                <Button variant="primary" size="md" disabled={settingsSaving} onClick={submitSettings}>{settingsSaving ? 'Saving…' : 'Save changes'}</Button>
              </div>

              <div style={{ borderTop: '1px solid var(--line-hairline)', marginTop: 8, paddingTop: 20, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--status-danger)' }}>Danger zone</span>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-500)', margin: 0 }}>Deactivating removes this organisation from the platform and cannot be reversed here. This action is available to platform administrators only.</p>
              </div>
            </div>
          </div>
        )}

        </div>
      </main>

      {toast && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 200, maxWidth: 360 }}>
          <div style={{ background: toast.tone === 'danger' ? 'var(--status-danger)' : 'var(--ink-900)', color: 'var(--bone-50)', padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, boxShadow: 'var(--shadow-menu)' }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</span>
            {toast.message && <span style={{ fontSize: 13, color: 'var(--bone-300)' }}>{toast.message}</span>}
          </div>
        </div>
      )}
    </div>
  );
}

function InfoRow({ label, value }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, padding: '12px 0', borderBottom: '1px solid var(--line-hairline)' }}>
      <span style={{ fontSize: 13, color: 'var(--ink-600)' }}>{label}</span>
      <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-900)' }}>{value}</span>
    </div>
  );
}

function thStyle(align) {
  return { padding: '9px 14px', textAlign: align, fontSize: 10, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)', borderBottom: '1px solid var(--ink-900)', whiteSpace: 'nowrap' };
}
function tdStyle(align) {
  return { padding: '9px 14px', textAlign: align, borderBottom: '1px solid var(--line-hairline)', verticalAlign: 'middle' };
}
