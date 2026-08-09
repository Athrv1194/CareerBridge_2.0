import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Checkbox, Field, Icon, IconButton, Input, Logo,
  Skeleton, Switch, Tag, Textarea,
} from '../../components/ui';
import { getMyProfile, addExperience, updateExperience, deleteExperience, addCertificate, deleteCertificate, uploadCertificateFile } from '../../api/studentApi';
import { getMyResumes, generateResume, deleteResume, downloadResume, setDefaultResume } from '../../api/resumeApi';
import { getUnreadCount } from '../../api/notificationApi';
import './resume.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume', active: true },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function fmtMonthYear(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { month: 'short', year: 'numeric' });
}

function toneForScore(v) {
  if (v >= 80) return 'success';
  if (v >= 60) return 'accent';
  return 'warning';
}

function scoreDotColor(v) {
  if (v >= 70) return 'var(--status-success)';
  if (v >= 40) return 'var(--status-warning)';
  return 'var(--status-danger)';
}

function gradeLabel(v) {
  if (v >= 80) return 'Strong match';
  if (v >= 60) return 'Good match';
  if (v >= 40) return 'Needs work';
  return 'Weak match';
}

function gradeCopy(v) {
  if (v >= 80) return 'Your skills line up closely with this career’s expected keyword set.';
  if (v >= 60) return 'A solid overlap, with a handful of gaps worth closing.';
  if (v >= 40) return 'There is real overlap, but several expected keywords are missing.';
  return 'Add more of the listed skills to move this score up.';
}

const EMPTY_EXP = { title: '', company: '', startDate: '', endDate: '', isCurrent: false, description: '' };
const EMPTY_CERT = { name: '', issuingOrganization: '', issueDate: '', expiryDate: '', credentialUrl: '' };

export default function ResumePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [listCollapsed, setListCollapsed] = useState(false);

  const [loading, setLoading] = useState(true);
  const [resumes, setResumes] = useState([]);
  const [profile, setProfile] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [generating, setGenerating] = useState(false);
  const [genError, setGenError] = useState('');
  const [deletingId, setDeletingId] = useState(null);
  const [confirmingId, setConfirmingId] = useState(null);
  const [downloadingId, setDownloadingId] = useState(null);
  const [fullViewOpen, setFullViewOpen] = useState(false);

  const [toast, setToast] = useState({ visible: false, title: '', message: '', tone: 'success' });

  // Builder form -- section toggles/contact toggles/summary/job description.
  const [summary, setSummary] = useState('');
  const [includePhone, setIncludePhone] = useState(true);
  const [includeEmail, setIncludeEmail] = useState(true);
  const [includeLinks, setIncludeLinks] = useState(true);
  const [includeLocation, setIncludeLocation] = useState(true);
  const [includeExperience, setIncludeExperience] = useState(true);
  const [includeSkills, setIncludeSkills] = useState(true);
  const [includeProjects, setIncludeProjects] = useState(true);
  const [includeEducation, setIncludeEducation] = useState(true);
  const [includeCertificates, setIncludeCertificates] = useState(true);
  const [jobDescription, setJobDescription] = useState('');
  const [tailorBannerDismissed, setTailorBannerDismissed] = useState(false);

  const [showExpForm, setShowExpForm] = useState(false);
  const [editingExpId, setEditingExpId] = useState(null);
  const [expDraft, setExpDraft] = useState(EMPTY_EXP);
  const [expError, setExpError] = useState('');

  const [showCertForm, setShowCertForm] = useState(false);
  const [certDraft, setCertDraft] = useState(EMPTY_CERT);
  const [certFile, setCertFile] = useState(null);
  const [certError, setCertError] = useState('');

  const showToast = useCallback((title, message, tone = 'success') => {
    setToast({ visible: true, title, message, tone });
    setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      const [resumeList, profileData] = await Promise.all([getMyResumes(), getMyProfile().catch(() => null)]);
      setResumes(resumeList);
      setProfile(profileData);
      setSelectedId((prev) => (prev && resumeList.some((r) => r.id === prev)) ? prev : (resumeList[0]?.id ?? null));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);
  useEffect(() => { getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {}); }, []);

  const selected = useMemo(() => resumes.find((r) => r.id === selectedId) || null, [resumes, selectedId]);

  // Reseed the form from whichever resume is selected instead of always resetting to defaults.
  useEffect(() => {
    if (selected) {
      setSummary(selected.summary || '');
      setIncludePhone(selected.includePhone !== false);
      setIncludeEmail(selected.includeEmail !== false);
      setIncludeLinks(selected.includeLinks !== false);
      setIncludeLocation(selected.includeLocation !== false);
      setIncludeExperience(selected.includeExperience !== false);
      setIncludeSkills(selected.includeSkills !== false);
      setIncludeProjects(selected.includeProjects !== false);
      setIncludeEducation(selected.includeEducation !== false);
      setIncludeCertificates(selected.includeCertificates !== false);
    }
  }, [selected?.id]);

  // overrideJobDescription sidesteps a stale closure -- React hasn't re-rendered with the new state yet.
  const handleGenerate = useCallback(async (overrideJobDescription) => {
    setGenerating(true);
    setGenError('');
    try {
      const jd = typeof overrideJobDescription === 'string' ? overrideJobDescription : jobDescription;
      const options = {
        summary: summary || null,
        includePhone, includeEmail, includeLinks, includeLocation,
        includeExperience, includeSkills, includeProjects, includeEducation, includeCertificates,
        jobDescription: jd.trim() || null,
      };
      const created = await generateResume(options);
      setResumes((list) => [created, ...list.map((r) => ({ ...r, isDefault: false }))]);
      setSelectedId(created.id);
      showToast('Résumé generated', `${Math.round(created.atsScore)}% ATS match on this version.`);
      return created;
    } catch (e) {
      setGenError(e.message);
      showToast('Could not generate résumé', e.message, 'danger');
      return null;
    } finally {
      setGenerating(false);
    }
  }, [summary, includePhone, includeEmail, includeLinks, includeLocation, includeExperience, includeSkills, includeProjects, includeEducation, includeCertificates, jobDescription, showToast]);

  // Arrived via "Tailor my résumé" with a job description in router state -- generate right away.
  useEffect(() => {
    const incoming = location.state?.jobDescription;
    if (!incoming || loading) return;
    setJobDescription(incoming);
    setTailorBannerDismissed(true);
    navigate(location.pathname, { replace: true, state: null });
    handleGenerate(incoming).then((created) => {
      if (created) {
        showToast('Résumé tailored', location.state?.jobTitle ? `Scored against ${location.state.jobTitle}.` : 'Scored against that job description.');
      }
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loading]);

  const handleDelete = useCallback(async (id) => {
    setDeletingId(id);
    try {
      await deleteResume(id);
      setResumes((list) => list.filter((r) => r.id !== id));
      setConfirmingId(null);
      showToast('Résumé deleted', 'It no longer appears on your applications.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    } finally {
      setDeletingId(null);
    }
  }, [showToast]);

  const handleDownload = useCallback(async (resume) => {
    setDownloadingId(resume.id);
    try {
      await downloadResume(resume.id, resume.fileName);
    } catch (e) {
      showToast('Could not download', e.message, 'danger');
    } finally {
      setDownloadingId(null);
    }
  }, [showToast]);

  const handleSetDefault = useCallback(async (id) => {
    try {
      const updated = await setDefaultResume(id);
      setResumes((list) => list.map((r) => (r.id === id ? updated : { ...r, isDefault: false })));
      showToast('Default résumé updated', 'This version will be used across the platform.');
    } catch (e) {
      showToast('Could not set default', e.message, 'danger');
    }
  }, [showToast]);

  // Work experience -- edits the student's real profile, not just this resume's draft.
  const resetExpForm = () => { setShowExpForm(false); setEditingExpId(null); setExpError(''); setExpDraft(EMPTY_EXP); };
  const editExp = (exp) => {
    setEditingExpId(exp.id); setExpError('');
    setExpDraft({ title: exp.title || '', company: exp.company || '', startDate: exp.startDate || '', endDate: exp.endDate || '', isCurrent: !!exp.isCurrent, description: exp.description || '' });
    setShowExpForm(true);
  };
  const submitExp = useCallback(async () => {
    if (!expDraft.title.trim()) { setExpError('Title is required'); return; }
    const payload = {
      title: expDraft.title.trim(), company: expDraft.company || null,
      startDate: expDraft.startDate || null, endDate: expDraft.isCurrent ? null : (expDraft.endDate || null),
      isCurrent: expDraft.isCurrent, description: expDraft.description || null,
    };
    try {
      if (editingExpId) {
        const updated = await updateExperience(editingExpId, payload);
        setProfile((p) => ({ ...p, experiences: p.experiences.map((e) => (e.id === editingExpId ? updated : e)) }));
      } else {
        const created = await addExperience(payload);
        setProfile((p) => ({ ...p, experiences: [...(p.experiences || []), created] }));
      }
      resetExpForm();
    } catch (e) {
      setExpError(e.message);
    }
  }, [expDraft, editingExpId]);
  const removeExp = useCallback(async (id) => {
    try {
      await deleteExperience(id);
      setProfile((p) => ({ ...p, experiences: p.experiences.filter((e) => e.id !== id) }));
    } catch (e) {
      showToast('Could not delete role', e.message, 'danger');
    }
  }, [showToast]);

  // Certifications -- same story: edits the real profile.
  const resetCertForm = () => { setShowCertForm(false); setCertError(''); setCertDraft(EMPTY_CERT); setCertFile(null); };
  const submitCert = useCallback(async () => {
    if (!certDraft.name.trim()) { setCertError('Certificate name is required'); return; }
    const payload = { name: certDraft.name.trim(), issuingOrganization: certDraft.issuingOrganization || null, issueDate: certDraft.issueDate || null, credentialUrl: certDraft.credentialUrl || null };
    try {
      const created = await addCertificate(payload);
      if (certFile) {
        await uploadCertificateFile(created.id, certFile);
        created.hasCredentialFile = true;
        created.credentialFileName = certFile.name;
      }
      setProfile((p) => ({ ...p, certificates: [...(p.certificates || []), created] }));
      resetCertForm();
    } catch (e) {
      setCertError(e.message);
    }
  }, [certDraft, certFile]);
  const removeCert = useCallback(async (id) => {
    try {
      await deleteCertificate(id);
      setProfile((p) => ({ ...p, certificates: p.certificates.filter((c) => c.id !== id) }));
    } catch (e) {
      showToast('Could not delete certificate', e.message, 'danger');
    }
  }, [showToast]);

  const sidebarWidth = navCollapsed ? '60px' : '248px';
  const p = profile || {};
  const experiences = p.experiences || [];
  const certificates = p.certificates || [];
  const skills = p.skills || [];
  const projects = p.projects || [];
  const educations = p.educations || [];
  const displayName = p.firstName ? `${p.firstName} ${p.lastName}` : 'Your name';
  const contactLine = [p.email, p.phone, p.city].filter(Boolean).join(' | ');

  const isTailored = selected?.isTailored;
  const matchedKeywords = selected?.matchedKeywords || [];
  const missingKeywords = selected?.missingKeywords || [];
  const score = selected?.atsScore ?? 0;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-rs-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
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
            <div className="cb-rs-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{displayName}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
        </div>
      </header>

      <div className="cb-rs-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside style={{ borderRight: '1px solid var(--line-hairline)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${navCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-rs-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
            <IconButton icon="chevron-right" label={navCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={() => setNavCollapsed((v) => !v)} iconStyle={{ transform: navCollapsed ? 'none' : 'rotate(180deg)', transition: 'transform 200ms ease' }} />
          </div>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {NAV_ITEMS.map((item) => (
              <Link key={item.to} to={item.to} title={item.label} style={{ display: 'flex', alignItems: 'center', gap: 11, justifyContent: navCollapsed ? 'center' : 'flex-start', padding: navCollapsed ? '10px 0' : '10px 14px', fontSize: 13, letterSpacing: '.06em', textTransform: 'uppercase', background: item.active ? 'var(--ink-900)' : 'transparent', color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)', border: 'none' }}>
                <Icon name={item.icon} size={16} style={{ color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)' }} />
                {!navCollapsed && <span className="cb-rs-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          {!navCollapsed && (
            <div className="cb-rs-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>Résumé exports need Plus.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers your latest résumé. Upgrade for unlimited versions and priority ATS scoring.</p>
                <Link to="/plans" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        <main className="cb-rs-main" style={{ minWidth: 0, display: 'flex', flexDirection: 'column', background: 'var(--surface-page)' }}>

          <div className="cb-rs-topbar" style={{ flexShrink: 0, background: 'var(--surface-card)', borderBottom: '1px solid var(--line-hairline)', padding: '26px 32px', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 24, flexWrap: 'wrap', boxSizing: 'border-box' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Résumé</span>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>Your résumés</h1>
              <p style={{ fontSize: 14, color: 'var(--ink-500)', margin: 0, maxWidth: 440 }}>Generated from your profile. ATS-scored against your career path.</p>
            </div>
            <Button variant="primary" size="md" icon="refresh-cw" disabled={generating} onClick={() => handleGenerate()}>
              {generating ? 'Generating…' : (resumes.length ? 'Regenerate résumé' : 'Generate my résumé')}
            </Button>
          </div>

          <div className="cb-rs-body" style={{ flex: 1, minHeight: 0, display: 'flex', position: 'relative' }}>

            <div className={`cb-rs-list-panel ${listCollapsed ? 'collapsed' : ''}`} style={{ flexShrink: 0, background: 'var(--surface-card)', borderRight: '1px solid var(--line-hairline)', overflow: 'hidden', boxSizing: 'border-box' }}>
              {listCollapsed ? (
                <div style={{ width: 60, height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '16px 0', boxSizing: 'border-box' }}>
                  <IconButton icon="chevron-right" label="Expand résumé list" onClick={() => setListCollapsed(false)} />
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)', writingMode: 'vertical-rl' }}>Versions</span>
                </div>
              ) : (
                <div style={{ width: 400, height: '100%', overflowY: 'auto', padding: 20, boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'flex-end', margin: '-4px -4px 0 0' }}>
                    <IconButton icon="chevron-right" label="Collapse résumé list" onClick={() => setListCollapsed(true)} iconStyle={{ transform: 'rotate(180deg)' }} />
                  </div>

                  {loading && <Skeleton height={92} />}

                  {!loading && resumes.length === 0 && (
                    <div style={{ marginTop: 20 }}>
                      <Icon name="file-text" size={20} />
                      <p style={{ fontSize: 14, color: 'var(--ink-600)', marginTop: 10 }}>No résumés yet. Generate one from your profile.</p>
                    </div>
                  )}

                  {!loading && resumes.length > 0 && (
                    <div>
                      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Version history</span>
                      <hr style={{ marginTop: 8, marginBottom: 0, height: 1, background: 'var(--line-ink)', border: 0 }} />
                      <div className="cb-rs-list-row" style={{ display: 'grid', gridTemplateColumns: 'minmax(0,34px) minmax(0,52px) minmax(0,1fr) minmax(0,24px) minmax(0,52px)', gap: 6, padding: '10px 4px 8px', borderBottom: '1px solid var(--line-ink)' }}>
                        <span className="cb-rs-col-head">Version</span>
                        <span className="cb-rs-col-head">Generated</span>
                        <span className="cb-rs-col-head">ATS score</span>
                        <span className="cb-rs-col-head cb-rs-col-size">Size</span>
                        <span className="cb-rs-col-head" style={{ textAlign: 'right' }}>Actions</span>
                      </div>
                      {resumes.map((r) => (
                        <div key={r.id}>
                          <div
                            onClick={() => setSelectedId(r.id)}
                            className="cb-rs-list-row"
                            style={{
                              display: 'grid', gridTemplateColumns: 'minmax(0,34px) minmax(0,52px) minmax(0,1fr) minmax(0,24px) minmax(0,52px)', gap: 6, alignItems: 'center',
                              padding: '11px 4px', cursor: 'pointer', borderBottom: '1px solid var(--line-hairline)',
                              background: r.id === selectedId ? 'var(--bone-200)' : 'transparent',
                              borderLeft: `2px solid ${r.id === selectedId ? 'var(--taupe-600)' : 'transparent'}`,
                            }}
                          >
                            <span className="cb-num" style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }}>v{r.version}</span>
                            <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', minWidth: 0 }}>{fmtDate(r.generatedAt)}</span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0, flexWrap: 'wrap' }}>
                              <span style={{ width: 7, height: 7, flexShrink: 0, background: scoreDotColor(r.atsScore) }} />
                              <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-900)' }}>{Math.round(r.atsScore)}%</span>
                              {r.isDefault && <Badge tone="accent">Default</Badge>}
                              {r.isTailored && <Badge tone="info">AI</Badge>}
                            </div>
                            <span className="cb-num cb-rs-col-size" style={{ fontSize: 12, color: 'var(--ink-400)' }}>—</span>
                            <div style={{ display: 'flex', gap: 0, justifyContent: 'flex-end', minWidth: 0 }}>
                              <IconButton icon="download" label="Download résumé" onClick={(e) => { e.stopPropagation(); handleDownload(r); }} />
                              <IconButton icon="x" label="Delete résumé" onClick={(e) => { e.stopPropagation(); setConfirmingId(r.id); }} />
                            </div>
                          </div>
                          {confirmingId === r.id && (
                            <div style={{ padding: '10px 12px', background: 'var(--status-danger-soft)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                              <span style={{ fontSize: 12, color: 'var(--ink-900)', lineHeight: 1.4 }}>Delete v{r.version}? This can&apos;t be undone.</span>
                              <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                                <Button size="sm" variant="primary" disabled={deletingId === r.id} onClick={() => handleDelete(r.id)}>Delete</Button>
                                <Button size="sm" variant="ghost" onClick={() => setConfirmingId(null)}>Cancel</Button>
                              </div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>

            <div style={{ flex: 1, minWidth: 0, overflowY: 'auto', background: 'var(--bone-100)', padding: 32, boxSizing: 'border-box' }}>
              <div className="cb-rs-detail" style={{ maxWidth: 840, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 28 }}>

                {loading && <Skeleton height={480} />}

                {!loading && !selected && (
                  <div style={{ minHeight: '50vh', display: 'flex', alignItems: 'center', justifyContent: 'center', textAlign: 'center' }}>
                    <div style={{ maxWidth: 420 }}>
                      <Icon name="file-text" size={24} />
                      <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 24, fontWeight: 400, color: 'var(--ink-900)', margin: '12px 0 8px' }}>Nothing to show yet</h2>
                      <p style={{ fontSize: 14, color: 'var(--ink-500)', margin: '0 0 16px' }}>Generate your first résumé to see its ATS breakdown here.</p>
                      <Button iconAfter="arrow-right" disabled={generating} onClick={() => handleGenerate()}>Generate my résumé</Button>
                    </div>
                  </div>
                )}

                {!loading && selected && (
                  <>
                    {genError && <Alert tone="danger" title="Couldn't generate résumé" message={genError} />}

                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 20, flexWrap: 'wrap' }}>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
                        <h1 title={selected.fileName} style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.15, color: 'var(--ink-900)', margin: 0, fontWeight: 400, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 480 }}>{selected.fileName}</h1>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                          <span className="cb-num" style={{ fontSize: 14, color: 'var(--ink-400)' }}>v{selected.version} · {fmtDate(selected.generatedAt)}</span>
                          {selected.isDefault && <Badge tone="inverse">Default</Badge>}
                          {selected.isTailored && <Badge tone="accent">Tailored by AI</Badge>}
                        </div>
                      </div>
                      <div style={{ display: 'flex', gap: 10, flexShrink: 0, alignItems: 'center' }}>
                        <Button variant="primary" size="md" iconAfter="download" disabled={downloadingId === selected.id} onClick={() => handleDownload(selected)}>
                          {downloadingId === selected.id ? 'Downloading…' : 'Download PDF'}
                        </Button>
                        {!selected.isDefault && (
                          <Button variant="secondary" size="md" onClick={() => handleSetDefault(selected.id)}>Set as default</Button>
                        )}
                      </div>
                    </div>

                    <div className="cb-rs-card" style={{ background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 28 }}>
                      <div className="cb-rs-ats-grid" style={{ display: 'grid', gridTemplateColumns: 'auto minmax(0,1fr)', gap: 36, alignItems: 'center' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10 }}>
                          <AtsRing value={score} />
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>ATS score</span>
                        </div>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
                          <div><Badge tone={toneForScore(score)}>{gradeLabel(score)}</Badge></div>
                          <p style={{ fontSize: 14, color: 'var(--ink-700)', lineHeight: 1.6, margin: 0 }}>{gradeCopy(score)}</p>

                          <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 6, paddingTop: 14, borderTop: '1px solid var(--line-hairline)' }}>
                            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>
                              {isTailored ? 'What’s affecting this score' : `Closest match: ${selected.closestCareerName || 'best-match career'}`}
                            </span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                              <span style={{ fontSize: 12, color: 'var(--ink-600)', width: 112, flexShrink: 0 }}>Skill keywords</span>
                              <div style={{ flex: 1, height: 4, background: 'var(--bone-300)' }}>
                                <div style={{ height: '100%', width: `${(selected.totalKeywords ? (matchedKeywords.length / selected.totalKeywords) * 100 : 0)}%`, background: 'var(--taupe-500)' }} />
                              </div>
                              <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-900)', flexShrink: 0 }}>{matchedKeywords.length}/{selected.totalKeywords || 0}</span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <Icon name="check" size={14} style={{ color: selected.hasEducation ? 'var(--status-success)' : 'var(--ink-300)' }} />
                              <span style={{ fontSize: 12, color: 'var(--ink-600)' }}>{selected.hasEducation ? 'Education on file' : 'No education added'}</span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                              <Icon name="check" size={14} style={{ color: selected.hasProjects ? 'var(--status-success)' : 'var(--ink-300)' }} />
                              <span style={{ fontSize: 12, color: 'var(--ink-600)' }}>{selected.hasProjects ? 'Projects on file' : 'No projects added'}</span>
                            </div>
                            {missingKeywords.length > 0 && (
                              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 2 }}>
                                <span style={{ fontSize: 12, color: 'var(--ink-500)' }}>Add these to improve it</span>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                                  {missingKeywords.map((kw) => <Tag key={kw}>{kw}</Tag>)}
                                </div>
                              </div>
                            )}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div className="cb-rs-card" style={{ background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 28 }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
                        <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Résumé preview</span>
                        <Button variant="ghost" size="sm" iconAfter="external-link" onClick={() => setFullViewOpen(true)}>Full view</Button>
                      </div>
                      <div style={{ display: 'flex', justifyContent: 'center' }}>
                        <div className="cb-rs-preview-page" style={{ width: '100%', maxWidth: 420, aspectRatio: '1 / 1.414', background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: '26px 24px', boxSizing: 'border-box', display: 'flex', flexDirection: 'column', gap: 14, overflow: 'hidden' }}>
                          <div style={{ textAlign: 'center' }}>
                            <span style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--ink-900)' }}>{displayName}</span>
                            <div className="cb-num" style={{ fontSize: 9, color: 'var(--ink-400)', marginTop: 4 }}>{contactLine}</div>
                          </div>
                          <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0 }} />
                          {summary && <p style={{ fontSize: 9, lineHeight: 1.5, color: 'var(--ink-600)', margin: 0, fontStyle: 'italic' }}>{summary}</p>}
                          {includeExperience && experiences.length > 0 && (
                            <MiniSection label="Work experience">
                              {experiences.slice(0, 3).map((ex) => (
                                <div key={ex.id} style={{ fontSize: 9, color: 'var(--ink-700)' }}>{ex.title}{ex.company ? ` · ${ex.company}` : ''}</div>
                              ))}
                            </MiniSection>
                          )}
                          <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 16, flex: 1, minHeight: 0 }}>
                            {includeSkills && <MiniBars label="Skills" count={Math.min(skills.length, 5)} />}
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
                              {includeProjects && <MiniBars label="Projects" count={Math.min(projects.length, 3)} />}
                              {includeEducation && <MiniBars label="Education" count={Math.min(educations.length, 2)} />}
                              {includeCertificates && <MiniBars label="Certifications" count={Math.min(certificates.length, 2)} />}
                            </div>
                          </div>
                        </div>
                      </div>
                      <p style={{ textAlign: 'center', fontSize: 12, color: 'var(--ink-400)', fontStyle: 'italic', margin: '14px 0 0' }}>This is a layout preview. Open full view to read every section, or download the PDF.</p>
                    </div>

                    <div className="cb-rs-card" style={{ background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 28 }}>
                      <div style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>Build your résumé</div>
                      <hr style={{ marginTop: 8, marginBottom: 18, height: 1, background: 'var(--line-ink)', border: 0 }} />

                      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
                        {!tailorBannerDismissed && jobDescription.trim() === '' && (
                          <div style={{ position: 'relative' }}>
                            <Alert tone="info" title="Tailor mode" message="Paste a job description below to score this résumé against that specific role instead of a best-match career." />
                            <button type="button" onClick={() => setTailorBannerDismissed(true)} aria-label="Dismiss" style={{ position: 'absolute', top: 8, right: 8, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--status-info)' }}>
                              <Icon name="x" size={14} />
                            </button>
                          </div>
                        )}

                        <Field label="Professional summary"><Textarea rows={3} value={summary} onChange={(e) => setSummary(e.target.value)} placeholder="One or two lines on what you're looking for and what you bring." /></Field>

                        <div>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Contact info</span>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
                            <Switch label={p.phone ? `Include phone (${p.phone})` : 'Include phone'} checked={includePhone} onChange={setIncludePhone} />
                            <Switch label="Include registered email" checked={includeEmail} onChange={setIncludeEmail} />
                            <Switch label="LinkedIn / GitHub / Portfolio URL" checked={includeLinks} onChange={setIncludeLinks} />
                            <Switch label={p.city ? `Include location (${p.city})` : 'Include location'} checked={includeLocation} onChange={setIncludeLocation} />
                          </div>
                        </div>

                        <div>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Sections to include</span>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 12 }}>
                            <Switch label="Work experience" checked={includeExperience} onChange={setIncludeExperience} />
                            <Switch label="Skills" checked={includeSkills} onChange={setIncludeSkills} />
                            <Switch label="Projects" checked={includeProjects} onChange={setIncludeProjects} />
                            <Switch label="Education" checked={includeEducation} onChange={setIncludeEducation} />
                            <Switch label="Certifications" checked={includeCertificates} onChange={setIncludeCertificates} />
                          </div>
                        </div>

                        <div style={{ paddingTop: 6, borderTop: '1px solid var(--line-hairline)' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Work experience</span>
                            <Button variant="ghost" size="sm" icon="plus" onClick={() => (showExpForm ? resetExpForm() : setShowExpForm(true))}>{showExpForm ? 'Cancel' : 'Add role'}</Button>
                          </div>
                          {experiences.length > 0 && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                              {experiences.map((ex) => (
                                <div key={ex.id} style={{ background: 'var(--bone-50)', padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
                                  <div style={{ minWidth: 0 }}>
                                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{ex.title}{ex.company ? ` · ${ex.company}` : ''}</div>
                                    <div className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 2 }}>{fmtMonthYear(ex.startDate)} – {ex.isCurrent ? 'Present' : fmtMonthYear(ex.endDate)}</div>
                                  </div>
                                  <div style={{ display: 'flex', gap: 2, flexShrink: 0 }}>
                                    <IconButton icon="pencil" label="Edit role" onClick={() => editExp(ex)} />
                                    <IconButton icon="x" label="Delete role" onClick={() => removeExp(ex.id)} />
                                  </div>
                                </div>
                              ))}
                            </div>
                          )}
                          {showExpForm && (
                            <div style={{ marginTop: 12, padding: 16, background: 'var(--bone-100)', border: '1px solid var(--line-hairline)', display: 'flex', flexDirection: 'column', gap: 12 }}>
                              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                <Field label="Title"><Input value={expDraft.title} onChange={(e) => setExpDraft((d) => ({ ...d, title: e.target.value }))} placeholder="Software Engineer Intern" /></Field>
                                <Field label="Company"><Input value={expDraft.company} onChange={(e) => setExpDraft((d) => ({ ...d, company: e.target.value }))} placeholder="Finzo Technologies" /></Field>
                              </div>
                              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                <Field label="Start date"><Input type="date" value={expDraft.startDate} onChange={(e) => setExpDraft((d) => ({ ...d, startDate: e.target.value }))} /></Field>
                                <Field label="End date"><Input type="date" value={expDraft.endDate} onChange={(e) => setExpDraft((d) => ({ ...d, endDate: e.target.value }))} /></Field>
                              </div>
                              <Checkbox label="I currently work here" checked={expDraft.isCurrent} onChange={(e) => setExpDraft((d) => ({ ...d, isCurrent: e.target.checked }))} />
                              <Field label="Description"><Textarea rows={2} value={expDraft.description} onChange={(e) => setExpDraft((d) => ({ ...d, description: e.target.value }))} placeholder="What did you build or ship?" /></Field>
                              {expError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{expError}</span>}
                              <div style={{ display: 'flex', gap: 8 }}>
                                <Button variant="primary" size="sm" onClick={submitExp}>Save</Button>
                                <Button variant="ghost" size="sm" onClick={resetExpForm}>Cancel</Button>
                              </div>
                            </div>
                          )}
                        </div>

                        <div style={{ paddingTop: 6, borderTop: '1px solid var(--line-hairline)' }}>
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
                            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Certifications</span>
                            <Button variant="ghost" size="sm" icon="plus" onClick={() => (showCertForm ? resetCertForm() : setShowCertForm(true))}>{showCertForm ? 'Cancel' : 'Add cert'}</Button>
                          </div>
                          {certificates.length > 0 && (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                              {certificates.map((c) => (
                                <div key={c.id} style={{ background: 'var(--bone-50)', padding: '12px 14px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10 }}>
                                  <div style={{ minWidth: 0 }}>
                                    <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{c.name}</div>
                                    <div style={{ fontSize: 12, color: 'var(--ink-400)', marginTop: 2 }}>{c.issuingOrganization}{c.hasCredentialFile ? ' · file attached' : ''}</div>
                                  </div>
                                  <IconButton icon="x" label="Delete certificate" onClick={() => removeCert(c.id)} />
                                </div>
                              ))}
                            </div>
                          )}
                          {showCertForm && (
                            <div style={{ marginTop: 12, padding: 16, background: 'var(--bone-100)', border: '1px solid var(--line-hairline)', display: 'flex', flexDirection: 'column', gap: 12 }}>
                              <Field label="Certificate name"><Input value={certDraft.name} onChange={(e) => setCertDraft((d) => ({ ...d, name: e.target.value }))} placeholder="AWS Certified Cloud Practitioner" /></Field>
                              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                <Field label="Issuing organization"><Input value={certDraft.issuingOrganization} onChange={(e) => setCertDraft((d) => ({ ...d, issuingOrganization: e.target.value }))} placeholder="Amazon Web Services" /></Field>
                                <Field label="Issue date"><Input type="date" value={certDraft.issueDate} onChange={(e) => setCertDraft((d) => ({ ...d, issueDate: e.target.value }))} /></Field>
                              </div>
                              <Field label="Certificate or offer letter (PDF)">
                                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                  <label style={{ display: 'inline-flex', alignItems: 'center', gap: 8, padding: '9px 14px', background: 'var(--bone-50)', border: '1px solid var(--line-strong)', fontSize: 13, color: 'var(--ink-700)', cursor: 'pointer', flexShrink: 0 }}>
                                    <Icon name="file-text" size={14} />
                                    Choose file
                                    <input type="file" accept="application/pdf,image/*" onChange={(e) => setCertFile(e.target.files?.[0] || null)} style={{ display: 'none' }} />
                                  </label>
                                  <span style={{ fontSize: 12, color: 'var(--ink-400)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{certFile?.name || 'No file selected'}</span>
                                </div>
                              </Field>
                              {certError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{certError}</span>}
                              <div style={{ display: 'flex', gap: 8 }}>
                                <Button variant="primary" size="sm" onClick={submitCert}>Save</Button>
                                <Button variant="ghost" size="sm" onClick={resetCertForm}>Cancel</Button>
                              </div>
                            </div>
                          )}
                        </div>

                        <div style={{ paddingTop: 6, borderTop: '1px solid var(--line-hairline)', display: 'flex', flexDirection: 'column', gap: 10 }}>
                          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>Tailor to a job (optional)</span>
                          <Textarea rows={4} value={jobDescription} onChange={(e) => setJobDescription(e.target.value)} placeholder="Paste a job description here to score this résumé against that specific role instead of a best-match career." />
                        </div>

                        <div>
                          <Button variant="secondary" size="sm" iconAfter="refresh-cw" disabled={generating} onClick={() => handleGenerate()}>
                            {generating ? 'Regenerating…' : 'Regenerate with these changes'}
                          </Button>
                        </div>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </div>
          </div>
        </main>
      </div>

      {fullViewOpen && selected && (
        <FullViewDialog
          onClose={() => setFullViewOpen(false)}
          displayName={displayName}
          contactLine={contactLine}
          summary={summary}
          includeExperience={includeExperience}
          includeSkills={includeSkills}
          includeProjects={includeProjects}
          includeEducation={includeEducation}
          includeCertificates={includeCertificates}
          experiences={experiences}
          skills={skills}
          projects={projects}
          educations={educations}
          certificates={certificates}
          fmtMonthYear={fmtMonthYear}
        />
      )}

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

function AtsRing({ value }) {
  const px = 120;
  const stroke = 8;
  const r = (px - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (Math.max(0, Math.min(100, value)) / 100) * c;
  return (
    <div style={{ position: 'relative', width: px, height: px }}>
      <svg width={px} height={px} viewBox={`0 0 ${px} ${px}`} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--bone-300)" strokeWidth={stroke} />
        <circle cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--ink-900)" strokeWidth={stroke} strokeDasharray={c} strokeDashoffset={offset} style={{ transition: 'stroke-dashoffset 600ms cubic-bezier(.2,0,.2,1)' }} />
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 34, color: 'var(--ink-900)' }}>{Math.round(value)}<span style={{ fontSize: 16, verticalAlign: 'super' }}>%</span></span>
      </div>
    </div>
  );
}

function MiniSection({ label, children }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>{label}</span>
      <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0, margin: 0 }} />
      {children}
    </div>
  );
}

function MiniBars({ label, count }) {
  const widths = [85, 65, 75, 55, 70];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      <span style={{ fontSize: 10, fontWeight: 600, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>{label}</span>
      <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0, margin: 0 }} />
      {Array.from({ length: Math.max(count, 1) }).map((_, i) => (
        // eslint-disable-next-line react/no-array-index-key
        <div key={i} style={{ height: 6, width: `${widths[i % widths.length]}%`, background: 'var(--taupe-100)' }} />
      ))}
    </div>
  );
}

function FullViewDialog({ onClose, displayName, contactLine, summary, includeExperience, includeSkills, includeProjects, includeEducation, includeCertificates, experiences, skills, projects, educations, certificates, fmtMonthYear }) {
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--surface-overlay)', zIndex: 70, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={onClose}>
      <div style={{ width: 640, maxWidth: '100%', maxHeight: '90vh', overflowY: 'auto', background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 32 }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <IconButton icon="x" label="Close" onClick={onClose} />
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
          <div style={{ textAlign: 'center' }}>
            <span style={{ fontFamily: 'var(--font-display)', fontSize: 28, color: 'var(--ink-900)' }}>{displayName}</span>
            <div className="cb-num" style={{ fontSize: 12, color: 'var(--ink-500)', marginTop: 6 }}>{contactLine}</div>
          </div>
          <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0 }} />
          {summary && <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-700)', margin: 0, fontStyle: 'italic' }}>{summary}</p>}

          {includeExperience && (
            <FullSection label="Work experience">
              {experiences.length === 0 ? <Empty text="No work experience added yet." /> : experiences.map((ex) => (
                <div key={ex.id} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                    <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{ex.title}{ex.company ? ` · ${ex.company}` : ''}</span>
                    <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtMonthYear(ex.startDate)} – {ex.isCurrent ? 'Present' : fmtMonthYear(ex.endDate)}</span>
                  </div>
                  {ex.description && <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: '2px 0 0' }}>{ex.description}</p>}
                </div>
              ))}
            </FullSection>
          )}

          {includeSkills && (
            <FullSection label="Skills">
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {skills.map((sk) => <Tag key={sk.id || sk.skillName}>{sk.skillName}</Tag>)}
              </div>
            </FullSection>
          )}

          {includeProjects && (
            <FullSection label="Projects">
              {projects.map((pj) => (
                <div key={pj.id}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{pj.title}</span>
                  <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: '2px 0 0' }}>{pj.description}</p>
                </div>
              ))}
            </FullSection>
          )}

          {includeEducation && (
            <FullSection label="Education">
              {educations.map((ed) => (
                <div key={ed.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: 'var(--ink-900)' }}>{[ed.degree, ed.fieldOfStudy].filter(Boolean).join(', ') || ed.institution}</span>
                  <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{ed.startYear} – {ed.endYear || 'Present'}</span>
                </div>
              ))}
            </FullSection>
          )}

          {includeCertificates && (
            <FullSection label="Certifications">
              {certificates.length === 0 ? <Empty text="No certifications added yet." /> : certificates.map((c) => (
                <div key={c.id} style={{ fontSize: 13, color: 'var(--ink-700)' }}>{c.name} <span style={{ color: 'var(--ink-400)' }}>— {c.issuingOrganization}</span></div>
              ))}
            </FullSection>
          )}
        </div>
      </div>
    </div>
  );
}

function FullSection({ label, children }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>{label}</span>
      <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: 0 }} />
      {children}
    </div>
  );
}

function Empty({ text }) {
  return <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>{text}</span>;
}
