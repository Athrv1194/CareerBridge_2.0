import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Field, Icon, IconButton, Input, Logo, revealStyle, Textarea, useRevealOnMount,
} from '../../components/ui';
import { getTokenPayload } from '../../utils/tokenUtils';
import {
  getPlatformStats, listUsers, getUserById, deactivateUser, activateUser, linkUserOrganization,
  listOrganizations, createOrganization, updateOrganization, deactivateOrganization,
  listOrgRequests, approveOrgRequest, rejectOrgRequest,
  listCategories, listAdminQuestions, addQuestion, editQuestion, activateQuestion, deactivateQuestion,
  getLeaderboard, listSubscriptions, getPlacementStats, refreshAiCoachResources,
} from '../../api/adminApi';
import './super-admin.css';

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'users', label: 'Users' },
  { key: 'organisations', label: 'Organisations' },
  { key: 'requests', label: 'Institution join requests' },
  { key: 'assessment', label: 'Assessment' },
  { key: 'subscriptions', label: 'Subscriptions' },
  { key: 'placement', label: 'Placement' },
  { key: 'aicoach', label: 'AI coach' },
];

const ROLES = ['STUDENT', 'ORG_ADMIN', 'PLACEMENT_OFFICER', 'RECRUITER', 'MENTOR', 'SUPER_ADMIN'];
const ROLE_TONE = {
  STUDENT: 'default', ORG_ADMIN: 'accent', PLACEMENT_OFFICER: 'info',
  RECRUITER: 'warning', MENTOR: 'success', SUPER_ADMIN: 'danger',
};
const GRADE_TONE = { A: 'success', B: 'info', C: 'warning', D: 'danger', F: 'danger' };

function fmtDate(s) {
  if (!s) return '—';
  return new Date(s).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

function SectionHeader({ label }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--ink-900)' }}>{label}</span>
      <div style={{ height: 1, background: 'var(--ink-900)' }} />
    </div>
  );
}

function EmptyState({ icon = 'help-circle', title, message }) {
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      gap: 8, padding: '40px 20px', border: '1px dashed var(--line-hairline)', textAlign: 'center',
    }}
    >
      <Icon name={icon} size={26} />
      <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{title}</span>
      {message && <span style={{ fontSize: 13, color: 'var(--ink-400)', maxWidth: 320 }}>{message}</span>}
    </div>
  );
}

function Th({ children, align = 'left' }) {
  return (
    <th style={{
      padding: '9px 14px', textAlign: align, fontSize: 10, fontWeight: 500, letterSpacing: '.14em',
      textTransform: 'uppercase', color: 'var(--ink-500)', borderBottom: '1px solid var(--ink-900)', whiteSpace: 'nowrap',
    }}
    >
      {children}
    </th>
  );
}

function Td({ children, align = 'left', style }) {
  return (
    <td style={{
      padding: '9px 14px', textAlign: align, borderBottom: '1px solid var(--line-hairline)', verticalAlign: 'middle', ...style,
    }}
    >
      {children}
    </td>
  );
}

function Table({ children }) {
  return (
    <div className="cb-scroll-x" style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 13 }}>{children}</table>
    </div>
  );
}

// ---------------- Overview ----------------
function OverviewTab() {
  const [stats, setStats] = useState(null);
  const [leaderboard, setLeaderboard] = useState(null);
  const [orgCount, setOrgCount] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    Promise.all([getPlatformStats(), getLeaderboard().catch(() => []), listOrganizations().catch(() => [])])
      .then(([s, lb, orgs]) => { setStats(s); setLeaderboard(lb); setOrgCount(orgs.length); })
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <Alert tone="danger" title="Could not load overview" message={error} />;
  if (!stats) return <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span>;

  const roleRows = [
    ['STUDENT', 'Students', stats.totalStudents],
    ['ORG_ADMIN', 'Org admins', stats.totalOrgAdmins],
    ['RECRUITER', 'Recruiters', stats.totalRecruiters],
    ['PLACEMENT_OFFICER', 'Placement officers', stats.totalPlacementOfficers],
    ['MENTOR', 'Mentors', stats.totalMentors],
    ['SUPER_ADMIN', 'Super admins', stats.totalSuperAdmins],
  ];
  const total = Math.max(1, stats.totalUsers || 0);
  const swatch = { STUDENT: 'var(--ink-900)', ORG_ADMIN: '#7FA8C9', RECRUITER: '#E3C77A', PLACEMENT_OFFICER: '#8FB393', MENTOR: '#C9A6D9', SUPER_ADMIN: '#D97A66' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
      <div className="cb-sa-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
        {[
          [stats.totalUsers, 'Total users'], [stats.activeUsers, 'Active users'],
          [stats.totalStudents, 'Total students'], [orgCount ?? '—', 'Organisations'],
          [stats.totalOrgAdmins, 'Org admins'], [stats.totalRecruiters, 'Recruiters'],
          [stats.totalPlacementOfficers, 'Placement officers'], [stats.totalMentors, 'Mentors'],
        ].map(([v, l]) => (
          <div key={l} style={{ background: 'var(--bone-50)', padding: '32px 24px', display: 'flex', flexDirection: 'column', gap: 6, justifyContent: 'center', minHeight: 110 }}>
            <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 36, color: 'var(--ink-900)' }}>{v}</span>
            <span style={{ fontSize: 13, color: 'var(--ink-500)' }}>{l}</span>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <SectionHeader label="Role distribution" />
        <div style={{ height: 24, width: '100%', background: 'var(--bone-200)', display: 'flex', overflow: 'hidden', marginTop: 8 }}>
          {roleRows.map(([role, , count]) => (count > 0 ? (
            <div key={role} style={{ width: `${(count / total) * 100}%`, background: swatch[role] }} />
          ) : null))}
        </div>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, marginTop: 16 }}>
          {roleRows.map(([role, label, count]) => (
            <div key={role} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ width: 10, height: 10, borderRadius: 2, background: swatch[role], display: 'inline-block' }} />
              <span style={{ fontSize: 12, color: 'var(--ink-600)' }}>{label}</span>
              <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{count}</span>
            </div>
          ))}
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <SectionHeader label="Global PRS leaderboard" />
        <span style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: -10 }}>Top students across all institutions.</span>
        {!leaderboard || leaderboard.length === 0 ? (
          <EmptyState icon="chart-no-axes-column" title="No scores yet" message="No placement scores recorded on the platform yet." />
        ) : (
          <Table>
            <thead><tr><Th>Rank</Th><Th>Student ID</Th><Th align="right">Score</Th><Th align="center">Grade</Th></tr></thead>
            <tbody>
              {leaderboard.slice(0, 10).map((row) => (
                <tr key={row.studentId}>
                  <Td><span className="cb-num" style={{ fontWeight: 600, color: 'var(--ink-900)' }}>#{row.rank}</span></Td>
                  <Td><span className="cb-num">{row.studentId}</span></Td>
                  <Td align="right"><span className="cb-num">{row.totalScore?.toFixed?.(1) ?? row.totalScore}</span></Td>
                  <Td align="center"><Badge tone={GRADE_TONE[row.grade] || 'default'}>{row.grade}</Badge></Td>
                </tr>
              ))}
            </tbody>
          </Table>
        )}
      </div>
    </div>
  );
}

// ---------------- Users ----------------
function UsersTab() {
  const [users, setUsers] = useState(null);
  const [error, setError] = useState(null);
  const [query, setQuery] = useState('');
  const [roleFilter, setRoleFilter] = useState('ALL');
  const [confirmingId, setConfirmingId] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [orgs, setOrgs] = useState(null);
  const [orgEditId, setOrgEditId] = useState(null);
  const [orgDraft, setOrgDraft] = useState('');

  const load = useCallback(() => {
    listUsers(roleFilter === 'ALL' ? undefined : roleFilter).then(setUsers).catch((e) => setError(e.message));
  }, [roleFilter]);

  useEffect(() => { load(); }, [load]);
  // Fetched once, independent of the role filter -- this is the only place a user's organizationId
  // can ever be set after registration (see auth-service AdminUserService.linkOrganization).
  useEffect(() => { listOrganizations().then(setOrgs).catch(() => setOrgs([])); }, []);

  function startEditOrg(u) {
    setOrgEditId(u.id);
    setOrgDraft(u.organizationId != null ? String(u.organizationId) : '');
  }

  async function saveOrg(u) {
    setBusyId(u.id);
    try {
      await linkUserOrganization(u.id, orgDraft === '' ? null : Number(orgDraft));
      setOrgEditId(null);
      load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  const rows = useMemo(() => {
    if (!users) return [];
    const q = query.trim().toLowerCase();
    if (!q) return users;
    return users.filter((u) => `${u.firstName} ${u.lastName} ${u.email}`.toLowerCase().includes(q));
  }, [users, query]);

  async function toggleActive(u) {
    setBusyId(u.id);
    try {
      if (u.isDeleted) await activateUser(u.id);
      else await deactivateUser(u.id);
      setConfirmingId(null);
      load();
    } catch (e) {
      setError(e.message);
    } finally {
      setBusyId(null);
    }
  }

  if (error) return <Alert tone="danger" title="Could not load users" message={error} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
        <SectionHeader label="User management" />
        <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-400)', flexShrink: 0 }}>{users ? `${rows.length} of ${users.length}` : '…'}</span>
      </div>

      <Input placeholder="Name, email…" value={query} onChange={(e) => setQuery(e.target.value)} />

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
        {['ALL', ...ROLES].map((r) => (
          <Button key={r} variant={roleFilter === r ? 'primary' : 'ghost'} size="sm" onClick={() => setRoleFilter(r)}>
            {r === 'ALL' ? 'All roles' : r.replace('_', ' ')}
          </Button>
        ))}
      </div>

      {!users ? <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span> : rows.length === 0 ? (
        <EmptyState icon="users" title="No users match" message="Try a different filter or search term." />
      ) : (
        <Table>
          <thead>
            <tr>
              <Th>Name</Th><Th>Email</Th><Th>Role</Th><Th>Org</Th><Th align="right">Joined</Th><Th>Status</Th><Th align="right">Actions</Th>
            </tr>
          </thead>
          <tbody>
            {rows.map((u) => (
              <tr key={u.id}>
                <Td><span style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>{u.firstName} {u.lastName}</span></Td>
                <Td style={{ color: 'var(--ink-500)' }}>{u.email}</Td>
                <Td><Badge tone={ROLE_TONE[u.role] || 'default'}>{u.role?.replace('_', ' ')}</Badge></Td>
                <Td style={{ color: 'var(--ink-500)' }}>
                  {orgEditId === u.id ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <select
                        value={orgDraft}
                        onChange={(e) => setOrgDraft(e.target.value)}
                        style={{
                          boxSizing: 'border-box', padding: '6px 8px', fontSize: 12.5, fontFamily: 'var(--font-sans)',
                          color: 'var(--ink-900)', background: 'var(--bone-50)', border: '1px solid var(--line-hairline)',
                          borderRadius: 'var(--radius-sm)', outline: 'none', cursor: 'pointer', maxWidth: 150,
                        }}
                      >
                        <option value="">Unlinked</option>
                        {(orgs || []).map((o) => <option key={o.id} value={o.id}>{o.name}</option>)}
                      </select>
                      <IconButton icon="check" label="Save" onClick={() => saveOrg(u)} disabled={busyId === u.id} />
                      <IconButton icon="x" label="Cancel" onClick={() => setOrgEditId(null)} disabled={busyId === u.id} />
                    </div>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span className="cb-num">{orgs?.find((o) => o.id === u.organizationId)?.name ?? u.organizationId ?? '—'}</span>
                      <IconButton icon="pencil" label="Link organization" onClick={() => startEditOrg(u)} />
                    </div>
                  )}
                </Td>
                <Td align="right" style={{ color: 'var(--ink-500)' }}><span className="cb-num">{fmtDate(u.createdAt)}</span></Td>
                <Td><Badge tone={u.isDeleted ? 'danger' : 'success'}>{u.isDeleted ? 'Inactive' : 'Active'}</Badge></Td>
                <Td align="right">
                  {confirmingId === u.id ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 12, color: 'var(--ink-600)' }}>Deactivate?</span>
                      <Button variant="primary" size="sm" disabled={busyId === u.id} onClick={() => toggleActive(u)} style={{ background: 'var(--status-danger)', borderColor: 'var(--status-danger)' }}>Confirm</Button>
                      <Button variant="ghost" size="sm" onClick={() => setConfirmingId(null)}>Cancel</Button>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', gap: 4, justifyContent: 'flex-end' }}>
                      {u.isDeleted ? (
                        <IconButton icon="check" label="Activate" onClick={() => toggleActive(u)} disabled={busyId === u.id} />
                      ) : (
                        <IconButton icon="x" label="Deactivate" onClick={() => setConfirmingId(u.id)} iconStyle={{ color: 'var(--status-danger)' }} />
                      )}
                    </div>
                  )}
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}

// ---------------- Organisations ----------------
const EMPTY_ORG_DRAFT = { name: '', type: '', contactEmail: '', contactPhone: '', address: '', city: '', state: '' };

function OrganisationsTab() {
  const [orgs, setOrgs] = useState(null);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [showCreate, setShowCreate] = useState(false);
  const [draft, setDraft] = useState(EMPTY_ORG_DRAFT);
  const [editDraft, setEditDraft] = useState(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);

  const load = useCallback(() => {
    listOrganizations().then((list) => { setOrgs(list); if (!selectedId && list.length) setSelectedId(list[0].id); }).catch((e) => setError(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => { load(); }, [load]);

  const selected = orgs?.find((o) => o.id === selectedId) || null;

  async function submitCreate() {
    setFormError(null);
    if (!draft.name.trim() || !draft.type.trim()) { setFormError('Name and type are required.'); return; }
    setSaving(true);
    try {
      const org = await createOrganization(draft);
      setShowCreate(false);
      setDraft(EMPTY_ORG_DRAFT);
      setOrgs((prev) => [...(prev || []), org]);
      setSelectedId(org.id);
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  }

  async function submitEdit() {
    setFormError(null);
    setSaving(true);
    try {
      const org = await updateOrganization(selected.id, editDraft);
      setOrgs((prev) => prev.map((o) => (o.id === org.id ? org : o)));
      setEditing(false);
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  }

  async function doDeactivate() {
    setSaving(true);
    try {
      await deactivateOrganization(selected.id);
      setConfirmDeactivate(false);
      load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  }

  if (error) return <Alert tone="danger" title="Could not load organisations" message={error} />;
  if (!orgs) return <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
        <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-400)' }}>{orgs.length} organisations</span>
        <Button variant="primary" iconAfter="plus" onClick={() => { setShowCreate(true); setSelectedId(null); }}>Create organisation</Button>
      </div>

      <div className="cb-sa-split" style={{ display: 'grid', gridTemplateColumns: '320px minmax(0,1fr)', gap: 28, alignItems: 'start' }}>
        <div className="cb-sa-split-list" style={{ display: 'flex', flexDirection: 'column', gap: 12, maxHeight: 'calc(100vh - 260px)', overflowY: 'auto' }}>
          {orgs.length === 0 && <EmptyState icon="building-2" title="No organisations yet" />}
          {orgs.map((org) => (
            <div
              key={org.id}
              onClick={() => { setSelectedId(org.id); setShowCreate(false); setEditing(false); }}
              style={{
                display: 'flex', flexDirection: 'column', gap: 4, padding: 14, cursor: 'pointer',
                background: selectedId === org.id ? 'var(--bone-100)' : 'var(--bone-50)',
                border: `1px solid ${selectedId === org.id ? 'var(--ink-900)' : 'var(--line-hairline)'}`,
              }}
            >
              <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{org.name}</span>
              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{org.type}</span>
              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{[org.city, org.state].filter(Boolean).join(', ') || '—'}</span>
              {!org.isActive && <span style={{ fontSize: 11, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--status-danger)' }}>Inactive</span>}
              <span style={{ fontSize: 11, color: 'var(--ink-300)' }}>{org.departments?.length ?? 0} departments</span>
            </div>
          ))}
        </div>

        <div style={{ background: 'var(--bone-100)', minHeight: 480, padding: 32, boxSizing: 'border-box' }}>
          {showCreate ? (
            <div style={{ maxWidth: 560, background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 32, display: 'flex', flexDirection: 'column', gap: 16 }}>
              <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 28, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>New organisation</h2>
              <Field label="Name"><Input value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} placeholder="Institution name" /></Field>
              <Field label="Type"><Input value={draft.type} onChange={(e) => setDraft({ ...draft, type: e.target.value })} placeholder="University / College / Institute" /></Field>
              <div className="cb-sa-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <Field label="Contact email"><Input type="email" value={draft.contactEmail} onChange={(e) => setDraft({ ...draft, contactEmail: e.target.value })} /></Field>
                <Field label="Contact phone"><Input value={draft.contactPhone} onChange={(e) => setDraft({ ...draft, contactPhone: e.target.value })} /></Field>
              </div>
              <Field label="Address"><Textarea rows={2} value={draft.address} onChange={(e) => setDraft({ ...draft, address: e.target.value })} /></Field>
              <div className="cb-sa-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <Field label="City"><Input value={draft.city} onChange={(e) => setDraft({ ...draft, city: e.target.value })} /></Field>
                <Field label="State"><Input value={draft.state} onChange={(e) => setDraft({ ...draft, state: e.target.value })} /></Field>
              </div>
              {formError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{formError}</span>}
              <div style={{ display: 'flex', gap: 10 }}>
                <Button variant="primary" disabled={saving} onClick={submitCreate}>Create</Button>
                <Button variant="ghost" onClick={() => setShowCreate(false)}>Cancel</Button>
              </div>
            </div>
          ) : selected ? (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 36, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{selected.name}</h1>
              <span style={{ fontSize: 14, color: 'var(--ink-400)', marginTop: 6 }}>{[selected.type, selected.city, selected.state].filter(Boolean).join(' · ')}</span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
                <Badge tone={selected.isActive ? 'success' : 'danger'}>{selected.isActive ? 'Active' : 'Inactive'}</Badge>
                <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtDate(selected.createdAt)}</span>
              </div>

              <div className="cb-sa-detail-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(3,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', marginTop: 28 }}>
                <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span className="cb-eyebrow">Departments</span><div className="cb-num" style={{ fontSize: 22, color: 'var(--ink-900)', marginTop: 6 }}>{selected.departments?.length ?? 0}</div></div>
                <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span className="cb-eyebrow">Contact</span><div style={{ marginTop: 6, fontSize: 13 }}>{selected.contactEmail || '—'}</div></div>
                <div style={{ background: 'var(--bone-50)', padding: '16px 18px' }}><span className="cb-eyebrow">ID</span><div className="cb-num" style={{ fontSize: 15, color: 'var(--ink-900)', marginTop: 6 }}>{selected.id}</div></div>
              </div>

              <div style={{ marginTop: 32, display: 'flex', flexDirection: 'column', gap: 12 }}>
                <SectionHeader label="Departments" />
                {(!selected.departments || selected.departments.length === 0) ? (
                  <span style={{ fontSize: 13, color: 'var(--ink-400)', fontStyle: 'italic' }}>No departments yet.</span>
                ) : (
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                    {selected.departments.map((d) => <Badge key={d.id}>{d.name}</Badge>)}
                  </div>
                )}
              </div>

              <div style={{ marginTop: 28 }}>
                <Button
                  variant="ghost" size="sm" iconAfter="chevron-down"
                  onClick={() => { setEditing((v) => !v); setEditDraft({ name: selected.name, type: selected.type, contactEmail: selected.contactEmail || '', contactPhone: selected.contactPhone || '', address: selected.address || '', city: selected.city || '', state: selected.state || '' }); }}
                >
                  Edit
                </Button>
                {editing && (
                  <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: 20, display: 'flex', flexDirection: 'column', gap: 14, marginTop: 14, maxWidth: 560 }}>
                    <Field label="Name"><Input value={editDraft.name} onChange={(e) => setEditDraft({ ...editDraft, name: e.target.value })} /></Field>
                    <Field label="Type"><Input value={editDraft.type} onChange={(e) => setEditDraft({ ...editDraft, type: e.target.value })} /></Field>
                    <div className="cb-sa-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                      <Field label="Contact email"><Input type="email" value={editDraft.contactEmail} onChange={(e) => setEditDraft({ ...editDraft, contactEmail: e.target.value })} /></Field>
                      <Field label="Contact phone"><Input value={editDraft.contactPhone} onChange={(e) => setEditDraft({ ...editDraft, contactPhone: e.target.value })} /></Field>
                    </div>
                    <Field label="Address"><Textarea rows={2} value={editDraft.address} onChange={(e) => setEditDraft({ ...editDraft, address: e.target.value })} /></Field>
                    <div className="cb-sa-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                      <Field label="City"><Input value={editDraft.city} onChange={(e) => setEditDraft({ ...editDraft, city: e.target.value })} /></Field>
                      <Field label="State"><Input value={editDraft.state} onChange={(e) => setEditDraft({ ...editDraft, state: e.target.value })} /></Field>
                    </div>
                    {formError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{formError}</span>}
                    <div style={{ display: 'flex', gap: 10 }}>
                      <Button variant="primary" size="sm" disabled={saving} onClick={submitEdit}>Save</Button>
                      <Button variant="ghost" size="sm" onClick={() => setEditing(false)}>Cancel</Button>
                    </div>
                  </div>
                )}
              </div>

              {selected.isActive && (
                <div style={{ borderTop: '1px solid var(--line-hairline)', marginTop: 28, paddingTop: 18, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--status-danger)' }}>Danger zone</span>
                  {confirmDeactivate ? (
                    <>
                      <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-500)', margin: 0 }}>This hides the org from the platform. It can be reactivated by an admin.</p>
                      <div style={{ display: 'flex', gap: 10 }}>
                        <Button variant="primary" size="sm" disabled={saving} onClick={doDeactivate} style={{ background: 'var(--status-danger)', borderColor: 'var(--status-danger)' }}>Confirm deactivate</Button>
                        <Button variant="ghost" size="sm" onClick={() => setConfirmDeactivate(false)}>Cancel</Button>
                      </div>
                    </>
                  ) : (
                    <div><Button variant="ghost" size="sm" onClick={() => setConfirmDeactivate(true)} style={{ color: 'var(--status-danger)' }}>Deactivate organisation</Button></div>
                  )}
                </div>
              )}
            </div>
          ) : (
            <EmptyState icon="building-2" title="Select an organisation" />
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------- Join requests ----------------
function RequestsTab() {
  const [requests, setRequests] = useState(null);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('PENDING');
  const [busyId, setBusyId] = useState(null);
  const [rejectingId, setRejectingId] = useState(null);
  const [reason, setReason] = useState('');

  const load = useCallback(() => {
    listOrgRequests(statusFilter === 'ALL' ? undefined : statusFilter).then(setRequests).catch((e) => setError(e.message));
  }, [statusFilter]);

  useEffect(() => { load(); }, [load]);

  async function approve(id) {
    setBusyId(id);
    try { await approveOrgRequest(id); load(); } catch (e) { setError(e.message); } finally { setBusyId(null); }
  }

  async function submitReject(id) {
    if (!reason.trim()) { setError('A rejection reason is required.'); return; }
    setBusyId(id);
    try { await rejectOrgRequest(id, reason); setRejectingId(null); setReason(''); load(); } catch (e) { setError(e.message); } finally { setBusyId(null); }
  }

  if (error) return <Alert tone="danger" title="Could not load requests" message={error} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <SectionHeader label="Institution join requests" />
      <div style={{ display: 'flex', gap: 8 }}>
        {['PENDING', 'APPROVED', 'REJECTED', 'ALL'].map((s) => (
          <Button key={s} variant={statusFilter === s ? 'primary' : 'ghost'} size="sm" onClick={() => setStatusFilter(s)}>{s.charAt(0) + s.slice(1).toLowerCase()}</Button>
        ))}
      </div>

      {!requests ? <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span> : requests.length === 0 ? (
        <EmptyState icon="building-2" title="No requests" message="No institution join requests in this status." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {requests.map((r) => (
            <div key={r.id} style={{ border: '1px solid var(--line-hairline)', background: 'var(--bone-50)', padding: 20, display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                  <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--ink-900)' }}>{r.institutionName}</span>
                  <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{r.organizationType} · {[r.city, r.state].filter(Boolean).join(', ')}</span>
                </div>
                <Badge tone={r.status === 'PENDING' ? 'warning' : r.status === 'APPROVED' ? 'success' : 'danger'}>{r.status}</Badge>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, fontSize: 12, color: 'var(--ink-500)' }}>
                <span>Contact: {r.contactPersonName}</span>
                <span>{r.contactEmail}</span>
                <span>{r.contactPhone}</span>
              </div>
              {r.status === 'REJECTED' && r.rejectionReason && (
                <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>Reason: {r.rejectionReason}</span>
              )}
              {r.status === 'PENDING' && (
                rejectingId === r.id ? (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 8 }}>
                    <Textarea rows={2} placeholder="Rejection reason…" value={reason} onChange={(e) => setReason(e.target.value)} />
                    <div style={{ display: 'flex', gap: 10 }}>
                      <Button variant="primary" size="sm" disabled={busyId === r.id} onClick={() => submitReject(r.id)} style={{ background: 'var(--status-danger)', borderColor: 'var(--status-danger)' }}>Confirm reject</Button>
                      <Button variant="ghost" size="sm" onClick={() => { setRejectingId(null); setReason(''); }}>Cancel</Button>
                    </div>
                  </div>
                ) : (
                  <div style={{ display: 'flex', gap: 10, marginTop: 8 }}>
                    <Button variant="primary" size="sm" disabled={busyId === r.id} onClick={() => approve(r.id)}>Approve</Button>
                    <Button variant="ghost" size="sm" onClick={() => setRejectingId(r.id)} style={{ color: 'var(--status-danger)' }}>Reject</Button>
                  </div>
                )
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ---------------- Assessment ----------------
const EMPTY_Q_DRAFT = { text: '', categoryId: '', orderIndex: 1, isActive: true, options: [{ text: '', weight: 0 }, { text: '', weight: 0 }] };

function AssessmentTab() {
  const [categories, setCategories] = useState(null);
  const [questions, setQuestions] = useState(null);
  const [error, setError] = useState(null);
  const [categoryFilter, setCategoryFilter] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [formMode, setFormMode] = useState('add');
  const [draft, setDraft] = useState(EMPTY_Q_DRAFT);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState(null);

  const load = useCallback(() => {
    listAdminQuestions(categoryFilter).then(setQuestions).catch((e) => setError(e.message));
  }, [categoryFilter]);

  useEffect(() => { listCategories().then(setCategories).catch((e) => setError(e.message)); }, []);
  useEffect(() => { load(); }, [load]);

  const selected = questions?.find((q) => q.id === selectedId) || null;

  function openAdd() {
    setDraft({ ...EMPTY_Q_DRAFT, categoryId: categories?.[0]?.id || '' });
    setFormMode('add');
    setShowForm(true);
    setSelectedId(null);
  }

  function openEdit(q) {
    setDraft({ text: q.text, categoryId: q.categoryId, orderIndex: q.orderIndex, isActive: q.isActive, options: q.options.map((o) => ({ text: o.text, weight: o.weight })) });
    setFormMode('edit');
    setShowForm(true);
  }

  function updateOption(i, field, value) {
    const options = draft.options.map((o, idx) => (idx === i ? { ...o, [field]: value } : o));
    setDraft({ ...draft, options });
  }

  async function submit() {
    setFormError(null);
    if (!draft.text.trim() || !draft.categoryId) { setFormError('Text and category are required.'); return; }
    const weights = draft.options.map((o) => Number(o.weight));
    if (weights.filter((w) => w === 3).length !== 1) { setFormError('Exactly one option must carry the maximum weight (3).'); return; }
    setSaving(true);
    try {
      const payload = { ...draft, categoryId: Number(draft.categoryId), orderIndex: Number(draft.orderIndex), options: draft.options.map((o) => ({ text: o.text, weight: Number(o.weight) })) };
      if (formMode === 'add') await addQuestion(payload);
      else await editQuestion(selectedId, payload);
      setShowForm(false);
      load();
    } catch (e) {
      setFormError(e.message);
    } finally {
      setSaving(false);
    }
  }

  async function toggleActive(q) {
    try {
      if (q.isActive) await deactivateQuestion(q.id); else await activateQuestion(q.id);
      load();
    } catch (e) {
      setError(e.message);
    }
  }

  if (error) return <Alert tone="danger" title="Could not load question bank" message={error} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16 }}>
        <SectionHeader label="Question bank" />
        <Button variant="primary" iconAfter="plus" onClick={openAdd}>Add question</Button>
      </div>

      {categories && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          <Button variant={categoryFilter == null ? 'primary' : 'ghost'} size="sm" onClick={() => setCategoryFilter(null)}>All</Button>
          {categories.map((c) => (
            <Button key={c.id} variant={categoryFilter === c.id ? 'primary' : 'ghost'} size="sm" onClick={() => setCategoryFilter(c.id)}>{c.name}</Button>
          ))}
        </div>
      )}

      <div className="cb-sa-split" style={{ display: 'grid', gridTemplateColumns: '340px minmax(0,1fr)', gap: 28, alignItems: 'start' }}>
        <div className="cb-sa-split-list" style={{ display: 'flex', flexDirection: 'column', gap: 12, maxHeight: 'calc(100vh - 320px)', overflowY: 'auto' }}>
          {!questions ? <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span> : questions.length === 0 ? (
            <EmptyState icon="help-circle" title="No questions yet." />
          ) : questions.map((q) => (
            <div
              key={q.id}
              onClick={() => { setSelectedId(q.id); setShowForm(false); }}
              style={{
                display: 'flex', flexDirection: 'column', gap: 6, padding: 14, cursor: 'pointer',
                background: selectedId === q.id ? 'var(--bone-100)' : 'var(--bone-50)',
                border: `1px solid ${selectedId === q.id ? 'var(--ink-900)' : 'var(--line-hairline)'}`,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                {!q.isActive && <span style={{ fontSize: 10, letterSpacing: '.08em', textTransform: 'uppercase', color: 'var(--status-danger)' }}>Inactive</span>}
                <Badge>{q.orderIndex}</Badge>
              </div>
              <span style={{
                fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', lineHeight: 1.4,
                display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
              }}
              >
                {q.text}
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Badge>{q.categoryName}</Badge>
                <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>{q.options.length} options</span>
              </div>
            </div>
          ))}
        </div>

        <div style={{ background: 'var(--bone-100)', minHeight: 480, padding: 32, boxSizing: 'border-box' }}>
          {showForm ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 620 }}>
              <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{formMode === 'add' ? 'New question' : 'Edit question'}</h2>
              <Field label="Question text"><Textarea rows={2} value={draft.text} onChange={(e) => setDraft({ ...draft, text: e.target.value })} /></Field>
              <div className="cb-sa-field-grid" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14 }}>
                <Field label="Category">
                  <select
                    value={draft.categoryId}
                    onChange={(e) => setDraft({ ...draft, categoryId: e.target.value })}
                    style={{
                      boxSizing: 'border-box', padding: '9px 10px', fontSize: 13, fontFamily: 'var(--font-sans)',
                      color: 'var(--ink-900)', background: 'var(--bone-50)', border: '1px solid var(--line-hairline)',
                      borderRadius: 'var(--radius-sm)', outline: 'none', cursor: 'pointer', width: '100%',
                    }}
                  >
                    {(categories || []).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                  </select>
                </Field>
                <Field label="Order index"><Input type="number" value={draft.orderIndex} onChange={(e) => setDraft({ ...draft, orderIndex: e.target.value })} /></Field>
              </div>
              <Field label="Options (weight 0-3, exactly one at 3)">
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {draft.options.map((o, i) => (
                    <div key={i} style={{ display: 'flex', gap: 8 }}>
                      <Input value={o.text} onChange={(e) => updateOption(i, 'text', e.target.value)} placeholder={`Option ${i + 1}`} />
                      <Input type="number" value={o.weight} onChange={(e) => updateOption(i, 'weight', e.target.value)} />
                      {draft.options.length > 2 && (
                        <IconButton icon="x" label="Remove option" onClick={() => setDraft({ ...draft, options: draft.options.filter((_, idx) => idx !== i) })} />
                      )}
                    </div>
                  ))}
                  {draft.options.length < 4 && (
                    <Button variant="ghost" size="sm" iconAfter="plus" onClick={() => setDraft({ ...draft, options: [...draft.options, { text: '', weight: 0 }] })}>Add option</Button>
                  )}
                </div>
              </Field>
              {formError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{formError}</span>}
              <div style={{ display: 'flex', gap: 10 }}>
                <Button variant="primary" disabled={saving} onClick={submit}>Save</Button>
                <Button variant="ghost" onClick={() => setShowForm(false)}>Cancel</Button>
              </div>
            </div>
          ) : selected ? (
            <div style={{ display: 'flex', flexDirection: 'column' }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: 'var(--ink-900)', margin: 0, fontWeight: 400, maxWidth: 560 }}>{selected.text}</h1>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
                <Badge>{selected.categoryName}</Badge>
                <Badge tone={selected.isActive ? 'success' : 'danger'}>{selected.isActive ? 'Active' : 'Inactive'}</Badge>
              </div>
              <div style={{ marginTop: 24, border: '1px solid var(--line-hairline)' }}>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 70px', gap: 0, borderBottom: '1px solid var(--line-hairline)', padding: '10px 14px' }}>
                  <span className="cb-eyebrow">Option</span><span className="cb-eyebrow">Weight</span>
                </div>
                {selected.options.map((o) => (
                  <div key={o.id} style={{ display: 'grid', gridTemplateColumns: '1fr 70px', gap: 0, alignItems: 'center', padding: '12px 14px', borderBottom: '1px solid var(--line-hairline)' }}>
                    <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{o.text}</span>
                    <span className="cb-num" style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)' }}>{o.weight}</span>
                  </div>
                ))}
              </div>
              <Alert tone="warning" title="Option weights are visible to admins only. Students never see these scores." />
              <div style={{ marginTop: 20, display: 'flex', gap: 10 }}>
                <Button variant="primary" size="sm" onClick={() => openEdit(selected)}>Edit</Button>
                <Button variant="ghost" size="sm" onClick={() => toggleActive(selected)} style={{ color: selected.isActive ? 'var(--status-danger)' : undefined }}>
                  {selected.isActive ? 'Deactivate' : 'Activate'}
                </Button>
              </div>
            </div>
          ) : (
            <EmptyState icon="help-circle" title="Select a question" />
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------- Subscriptions ----------------
function SubscriptionsTab() {
  const [subs, setSubs] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => { listSubscriptions().then(setSubs).catch((e) => setError(e.message)); }, []);

  if (error) return <Alert tone="danger" title="Could not load subscriptions" message={error} />;
  if (!subs) return <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <SectionHeader label="Subscriptions" />
      {subs.length === 0 ? <EmptyState title="No subscriptions yet" /> : (
        <Table>
          <thead><tr><Th>User</Th><Th>Plan</Th><Th>Cycle</Th><Th align="right">Start</Th><Th align="right">End</Th><Th>Status</Th></tr></thead>
          <tbody>
            {subs.map((s) => (
              <tr key={s.id ?? `${s.userId}-${s.startDate}`}>
                <Td><span className="cb-num">{s.userId}</span></Td>
                <Td>{s.planName}</Td>
                <Td>{s.billingCycle}</Td>
                <Td align="right"><span className="cb-num">{fmtDate(s.startDate)}</span></Td>
                <Td align="right"><span className="cb-num">{fmtDate(s.endDate)}</span></Td>
                <Td><Badge tone={s.active ? 'success' : 'default'}>{s.status}</Badge></Td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
}

// ---------------- Placement ----------------
function PlacementTab() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => { getPlacementStats().then(setStats).catch((e) => setError(e.message)); }, []);

  if (error) return <Alert tone="danger" title="Could not load placement stats" message={error} />;
  if (!stats) return <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>Loading…</span>;

  const tiles = [
    [stats.totalStudentsInScope, 'Students in scope'], [stats.totalApplications, 'Applications'],
    [stats.offersExtended, 'Offers extended'], [stats.offersAccepted, 'Offers accepted'],
    [stats.offersDeclined, 'Offers declined'], [`${(stats.placementRate ?? 0).toFixed(1)}%`, 'Placement rate'],
    [stats.averageCtc != null ? `₹${stats.averageCtc} LPA` : '—', 'Average CTC'],
    [stats.highestCtc != null ? `₹${stats.highestCtc} LPA` : '—', 'Highest CTC'],
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
      <SectionHeader label="Platform placement stats" />
      <div className="cb-sa-stat-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
        {tiles.map(([v, l]) => (
          <div key={l} style={{ background: 'var(--bone-50)', padding: '28px 20px', display: 'flex', flexDirection: 'column', gap: 6, minHeight: 100 }}>
            <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 28, color: 'var(--ink-900)' }}>{v}</span>
            <span style={{ fontSize: 12, color: 'var(--ink-500)' }}>{l}</span>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <SectionHeader label="Top hiring companies" />
        <span style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: -6 }}>Companies with at least one accepted offer — not every company on the platform.</span>
        {stats.topCompanies?.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {stats.topCompanies.map((c) => <Badge key={c}>{c}</Badge>)}
          </div>
        ) : (
          <EmptyState icon="award" title="No accepted offers recorded yet." />
        )}
      </div>
    </div>
  );
}

// ---------------- AI coach ----------------
function AiCoachTab() {
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  async function refresh() {
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      await refreshAiCoachResources();
      setResult('Catalog refresh started. It runs in the background and can take a few minutes for all 7 careers.');
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20, maxWidth: 560 }}>
      <SectionHeader label="AI coach resource catalog" />
      <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-500)', margin: 0 }}>
        Rebuilds the shared milestone resource catalog (Tavily + YouTube search) for every seeded career roadmap.
        This is shared across all students, not per-student — running it refreshes resources for everyone at once.
      </p>
      {error && <Alert tone="danger" title="Refresh failed" message={error} />}
      {result && <Alert tone="info" title="Refresh queued" message={result} />}
      <div><Button variant="primary" iconAfter="refresh-cw" disabled={busy} onClick={refresh}>{busy ? 'Starting…' : 'Refresh catalog'}</Button></div>
    </div>
  );
}

// ---------------- Shell ----------------
export default function SuperAdminPage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('overview');
  const [gateChecked, setGateChecked] = useState(false);
  const [adminName, setAdminName] = useState('Admin');
  const [tabIn, setTabIn] = useState(false);
  const topbarIn = useRevealOnMount();

  useEffect(() => {
    setTabIn(false);
    const t = setTimeout(() => setTabIn(true), 20);
    return () => clearTimeout(t);
  }, [activeTab]);

  useEffect(() => {
    const payload = getTokenPayload();
    if (!payload) {
      navigate('/login', { replace: true });
      return;
    }
    if (payload.role !== 'SUPER_ADMIN') {
      navigate('/dashboard', { replace: true });
      return;
    }
    setGateChecked(true);
    if (payload.userId) {
      getUserById(payload.userId).then((u) => setAdminName(`${u.firstName} ${u.lastName}`)).catch(() => {});
    }
  }, [navigate]);

  if (!gateChecked) return null;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--bone-100)', color: 'var(--ink-900)', fontFamily: 'var(--font-sans)' }}>
      <header className="cb-sa-header" style={{
        position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--bone-50)',
        borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 24, padding: '0 28px', boxSizing: 'border-box',
      }}
      >
        <Logo size={32} />
        <span style={{
          fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--status-danger)',
          paddingLeft: 24, borderLeft: '1px solid var(--line-hairline)',
        }}
        >
          Admin
        </span>
        <div style={{ flex: 1 }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{adminName}</span>
          <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Super admin</span>
        </div>
      </header>

      <div style={{ background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div className="cb-sa-topbar" style={{ maxWidth: 1320, margin: '0 auto', padding: '26px 32px', display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 24, flexWrap: 'wrap', boxSizing: 'border-box', ...revealStyle(topbarIn, 0, { distance: 16 }) }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--status-danger)' }}>Super admin</span>
            <h1 className="cb-sa-page-title" style={{ fontFamily: 'var(--font-display)', fontSize: 32, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>Platform control</h1>
            <span style={{ fontSize: 14, color: 'var(--ink-400)' }}>Global scope · all organisations · all users.</span>
          </div>
          <Badge tone="danger">Super admin</Badge>
        </div>
      </div>

      <div style={{ position: 'sticky', top: 64, zIndex: 30, background: 'var(--bone-50)', borderBottom: '1px solid var(--line-hairline)' }}>
        <div className="cb-sa-tabs-row cb-scroll-x" style={{ maxWidth: 1320, margin: '0 auto', padding: '0 32px', boxSizing: 'border-box', display: 'flex', gap: 4, overflowX: 'auto' }}>
          {TABS.map((t) => (
            <button
              key={t.key}
              type="button"
              onClick={() => setActiveTab(t.key)}
              style={{
                padding: '14px 16px', fontSize: 13, fontWeight: 500, whiteSpace: 'nowrap', background: 'none', cursor: 'pointer',
                color: activeTab === t.key ? 'var(--ink-900)' : 'var(--ink-400)',
                borderBottom: activeTab === t.key ? '2px solid var(--ink-900)' : '2px solid transparent',
              }}
            >
              {t.label}
            </button>
          ))}
        </div>
      </div>

      <main className="cb-sa-main" style={{ maxWidth: 1320, margin: '0 auto', padding: '32px 32px 120px', boxSizing: 'border-box' }}>
        <div style={revealStyle(tabIn, 0, { distance: 16, duration: 380 })}>
          {activeTab === 'overview' && <OverviewTab />}
          {activeTab === 'users' && <UsersTab />}
          {activeTab === 'organisations' && <OrganisationsTab />}
          {activeTab === 'requests' && <RequestsTab />}
          {activeTab === 'assessment' && <AssessmentTab />}
          {activeTab === 'subscriptions' && <SubscriptionsTab />}
          {activeTab === 'placement' && <PlacementTab />}
          {activeTab === 'aicoach' && <AiCoachTab />}
        </div>
      </main>
    </div>
  );
}
