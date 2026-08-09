import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Checkbox, Field, Icon, IconButton, Input, Logo,
  Skeleton, Switch, Tag, Textarea,
} from '../../components/ui';
import {
  getMyProfile, updateMyProfile,
  addEducation, updateEducation as apiUpdateEducation, deleteEducation as apiDeleteEducation,
  addSkill, deleteSkill as apiDeleteSkill,
  addProject, updateProject as apiUpdateProject, deleteProject as apiDeleteProject,
  addCertificate, updateCertificate as apiUpdateCertificate, deleteCertificate as apiDeleteCertificate,
  getSkillSuggestions, uploadAvatar, getAvatarBlobUrl, uploadProjectCover, getProjectCoverBlobUrl,
  deleteProjectCover,
} from '../../api/studentApi';
import { getMyResumes, generateResume, deleteResume, downloadResume } from '../../api/resumeApi';
import './profile.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile', active: true },
];

const PROFICIENCY_OPTIONS = [
  { value: '', label: 'Not set' },
  { value: 'BEGINNER', label: 'Beginner' },
  { value: 'INTERMEDIATE', label: 'Intermediate' },
  { value: 'ADVANCED', label: 'Advanced' },
  { value: 'EXPERT', label: 'Expert' },
];

const LEVEL_TONE = { EXPERT: 'success', ADVANCED: 'success', INTERMEDIATE: 'info', BEGINNER: 'default' };

function filled(v) {
  return typeof v === 'string' && v.trim().length > 0;
}

function joinList(items) {
  if (items.length <= 1) return items[0] || '';
  return `${items.slice(0, -1).join(', ')} and ${items[items.length - 1]}`;
}

// Mirrors student-service's ProfileCompletionCalculator weights (20/15/15/20/15/10/5 -- see
// CLAUDE.md). Used only to explain gaps; the ring itself always shows the server's own
// profileCompletionPercentage, the authoritative number.
function computeGaps(profile, skills, educations, projects) {
  const gaps = [];
  const basicFields = [['firstName', 'first name'], ['lastName', 'last name'], ['phone', 'phone'], ['bio', 'bio'], ['city', 'city']];
  const missingBasic = basicFields.filter(([k]) => !filled(profile[k])).map(([, label]) => label);
  if (missingBasic.length) {
    gaps.push({ weight: 20, title: 'Complete your basic info', detail: `Missing ${joinList(missingBasic)}`, cta: 'Edit', action: 'editProfile' });
  }
  if ((educations || []).length < 1) {
    gaps.push({ weight: 15, title: 'Add an education entry', detail: 'Recruiters filter on institution and graduation year', cta: 'Add', action: 'addEducation' });
  }
  const skillGap = 2 - (skills || []).length;
  if (skillGap > 0) {
    gaps.push({ weight: 15, title: `Add ${skillGap} more skill${skillGap === 1 ? '' : 's'}`, detail: 'Only the count matters for this weight', cta: 'Add', action: 'addSkill' });
  }
  if ((projects || []).length < 1) {
    gaps.push({ weight: 20, title: 'Add a project', detail: 'The single biggest lever on this page', cta: 'Add', action: 'addProject' });
  }
  if (!filled(profile.resumeUrl)) {
    gaps.push({ weight: 15, title: 'Attach a résumé', detail: 'Generate one from the résumé workspace', cta: 'Attach', action: 'generateResume' });
  }
  if (!filled(profile.linkedinUrl) && !filled(profile.githubUrl)) {
    gaps.push({ weight: 10, title: 'Add a LinkedIn or GitHub link', detail: 'Either one satisfies this', cta: 'Edit', action: 'editProfile' });
  }
  if (!filled(profile.portfolioUrl)) {
    gaps.push({ weight: 5, title: 'Add a portfolio link', detail: 'The last 5%', cta: 'Edit', action: 'editProfile' });
  }
  return gaps.sort((a, z) => z.weight - a.weight);
}

function urlHref(u) {
  return u ? (u.startsWith('http') ? u : `https://${u}`) : '';
}

function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function toneForScore(v) {
  if (v >= 80) return 'success';
  if (v >= 60) return 'accent';
  return 'warning';
}

function SectionHeader({ label, actionLabel, onAction }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 14 }}>
      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-600)' }}>{label}</span>
      {actionLabel && (
        <button type="button" onClick={onAction} style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', fontSize: 12, color: 'var(--taupe-700)' }}>
          {actionLabel}
        </button>
      )}
      <hr style={{ display: actionLabel ? 'none' : 'block', flex: 1, height: 1, background: 'var(--line-ink)', border: 0, marginLeft: 12 }} />
    </div>
  );
}

function EmptyRow({ icon, title, message, actionLabel, onAction }) {
  return (
    <div style={{ marginTop: 20, border: '1px dashed var(--line-hairline)', padding: '28px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, textAlign: 'center' }}>
      <Icon name={icon} size={20} />
      <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink-900)' }}>{title}</span>
      <span style={{ fontSize: 13, color: 'var(--ink-500)', maxWidth: 420 }}>{message}</span>
      {actionLabel && <Button size="sm" variant="secondary" onClick={onAction} style={{ marginTop: 4 }}>{actionLabel}</Button>}
    </div>
  );
}

function ScoreRingSmall({ value }) {
  const px = 150;
  const stroke = 10;
  const r = (px - stroke) / 2;
  const c = 2 * Math.PI * r;
  const offset = c - (Math.max(0, Math.min(100, value)) / 100) * c;
  return (
    <div style={{ position: 'relative', width: px, height: px }}>
      <svg width={px} height={px} viewBox={`0 0 ${px} ${px}`}>
        <circle cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--bone-300)" strokeWidth={stroke} />
        <circle
          cx={px / 2} cy={px / 2} r={r} fill="none" stroke="var(--ink-900)" strokeWidth={stroke}
          strokeDasharray={c} strokeDashoffset={offset} strokeLinecap="round"
          transform={`rotate(-90 ${px / 2} ${px / 2})`}
          style={{ transition: 'stroke-dashoffset 600ms cubic-bezier(.2,0,.2,1)' }}
        />
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '0 20px', boxSizing: 'border-box' }}>
        <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 40, color: 'var(--ink-900)' }}>{value}<span style={{ fontSize: 18, verticalAlign: 'super' }}>%</span></span>
        <span style={{ fontSize: 9, letterSpacing: '.06em', color: 'var(--ink-500)', textAlign: 'center' }}>PROFILE COMPLETE</span>
      </div>
    </div>
  );
}

function EditProfileDialog({ open, draft, onChange, error, saving, onClose, onSave }) {
  if (!open) return null;
  const set = (field) => (e) => onChange({ ...draft, [field]: e.target.value });
  return (
    <div style={{ position: 'fixed', inset: 0, background: 'var(--surface-overlay)', zIndex: 70, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 20 }} onClick={onClose}>
      <div
        style={{ width: 600, maxWidth: '100%', maxHeight: '90vh', overflowY: 'auto', background: 'var(--surface-card)', border: '1px solid var(--line-hairline)', padding: 28, display: 'flex', flexDirection: 'column', gap: 18 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div>
          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Profile</span>
          <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 24, fontWeight: 400, color: 'var(--ink-900)', margin: '6px 0 0' }}>Edit your details</h2>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 16 }}>
          <Field label="First name"><Input value={draft.firstName} onChange={set('firstName')} /></Field>
          <Field label="Last name"><Input value={draft.lastName} onChange={set('lastName')} /></Field>
          <Field label="Phone"><Input value={draft.phone} onChange={set('phone')} /></Field>
          <Field label="City"><Input value={draft.city} onChange={set('city')} /></Field>
          <Field label="State"><Input value={draft.state} onChange={set('state')} /></Field>
          <Field label="Country"><Input value={draft.country} onChange={set('country')} /></Field>
        </div>
        <Field label="Bio"><Textarea rows={3} value={draft.bio} onChange={set('bio')} /></Field>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 16 }}>
          <Field label="LinkedIn URL"><Input value={draft.linkedinUrl} onChange={set('linkedinUrl')} placeholder="https://linkedin.com/in/…" /></Field>
          <Field label="GitHub URL"><Input value={draft.githubUrl} onChange={set('githubUrl')} placeholder="https://github.com/…" /></Field>
        </div>
        <Field label="Portfolio URL"><Input value={draft.portfolioUrl} onChange={set('portfolioUrl')} placeholder="https://" /></Field>
        {error && <Alert tone="danger" message={error} />}
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
          <Button variant="ghost" size="md" onClick={onClose}>Cancel</Button>
          <Button variant="primary" size="md" onClick={onSave} disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</Button>
        </div>
      </div>
    </div>
  );
}

const EMPTY_EDU = { institution: '', degree: '', fieldOfStudy: '', startYear: '', endYear: '', grade: '', description: '' };
const EMPTY_PROJECT = { title: '', description: '', techStack: '', projectUrl: '', githubUrl: '', startDate: '', endDate: '', isOngoing: false, coverImageUrl: '' };
const EMPTY_CERT = { name: '', issuingOrganization: '', issueDate: '', expiryDate: '', credentialUrl: '' };

export default function ProfilePage() {
  const navigate = useNavigate();
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [loading, setLoading] = useState(true);
  const [fadeIn, setFadeIn] = useState(false);
  const [avatarSrc, setAvatarSrc] = useState('');

  const [profile, setProfile] = useState(null);
  const [skills, setSkills] = useState([]);
  const [educations, setEducations] = useState([]);
  const [projects, setProjects] = useState([]);
  const [certificates, setCertificates] = useState([]);
  const [resumes, setResumes] = useState([]);
  const [skillCatalogue, setSkillCatalogue] = useState([]);

  const [skillQuery, setSkillQuery] = useState('');
  const [skillDropdownOpen, setSkillDropdownOpen] = useState(false);
  const [pendingProficiency, setPendingProficiency] = useState('');
  const [skillFormError, setSkillFormError] = useState('');

  const [showEduForm, setShowEduForm] = useState(false);
  const [editingEduId, setEditingEduId] = useState(null);
  const [eduDraft, setEduDraft] = useState(EMPTY_EDU);
  const [eduFormError, setEduFormError] = useState('');

  const [showProjectForm, setShowProjectForm] = useState(false);
  const [editingProjectId, setEditingProjectId] = useState(null);
  const [projectDraft, setProjectDraft] = useState(EMPTY_PROJECT);
  const [projectFormError, setProjectFormError] = useState('');

  const [showCertForm, setShowCertForm] = useState(false);
  const [editingCertId, setEditingCertId] = useState(null);
  const [certDraft, setCertDraft] = useState(EMPTY_CERT);
  const [certFormError, setCertFormError] = useState('');

  const [editOpen, setEditOpen] = useState(false);
  const [editDraft, setEditDraft] = useState({});
  const [editSaving, setEditSaving] = useState(false);
  const [editError, setEditError] = useState('');

  const [coverUrls, setCoverUrls] = useState({});
  const [generating, setGenerating] = useState(false);
  const [toast, setToast] = useState({ visible: false, title: '', message: '', tone: 'success' });

  const toastTimerRef = useRef(null);
  const avatarInputRef = useRef(null);
  const coverInputRef = useRef(null);
  const blurTimerRef = useRef(null);

  const showToast = useCallback((title, message, tone = 'success') => {
    clearTimeout(toastTimerRef.current);
    setToast({ visible: true, title, message, tone });
    toastTimerRef.current = setTimeout(() => setToast((t) => ({ ...t, visible: false })), 4500);
  }, []);

  const loadAll = useCallback(async (opts = {}) => {
    if (!opts.silent) setLoading(true);
    try {
      const data = await getMyProfile();
      setProfile(data);
      setSkills(data.skills || []);
      setEducations(data.educations || []);
      setProjects(data.projects || []);
      setCertificates(data.certificates || []);
    } catch (e) {
      if (!opts.silent) showToast('Could not load your profile', e.message, 'danger');
    }
    try {
      const suggestions = await getSkillSuggestions();
      if (suggestions.length) setSkillCatalogue(suggestions);
    } catch { /* keep previous catalogue */ }
    if (!opts.silent) {
      try { setResumes(await getMyResumes()); } catch { /* keep previous list */ }
    }
    if (!opts.silent) {
      setLoading(false);
      setTimeout(() => setFadeIn(true), 20);
    }
  }, [showToast]);

  useEffect(() => { loadAll(); }, [loadAll]);
  useEffect(() => () => { clearTimeout(toastTimerRef.current); clearTimeout(blurTimerRef.current); }, []);

  useEffect(() => {
    if (!profile?.hasAvatar) return;
    let cancelled = false;
    getAvatarBlobUrl().then((url) => { if (!cancelled && url) setAvatarSrc(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [profile?.hasAvatar]);

  useEffect(() => {
    const missing = projects.filter((p) => p.hasCoverImage && !coverUrls[p.id]);
    if (missing.length === 0) return;
    let cancelled = false;
    Promise.all(missing.map((p) => getProjectCoverBlobUrl(p.id).then((url) => [p.id, url]).catch(() => [p.id, null])))
      .then((pairs) => {
        if (cancelled) return;
        setCoverUrls((prev) => {
          const next = { ...prev };
          pairs.forEach(([id, url]) => { if (url) next[id] = url; });
          return next;
        });
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projects]);

  const scrollToSection = useCallback((id, openSetter) => () => {
    if (openSetter) openSetter(true);
    setTimeout(() => {
      const el = document.getElementById(id);
      if (!el) return;
      window.scrollTo({ top: el.getBoundingClientRect().top + window.pageYOffset - 84, behavior: 'smooth' });
    }, 0);
  }, []);

  const openEditDialog = useCallback(() => {
    const p = profile || {};
    setEditDraft({
      firstName: p.firstName || '', lastName: p.lastName || '', phone: p.phone || '', bio: p.bio || '',
      city: p.city || '', state: p.state || '', country: p.country || '',
      linkedinUrl: p.linkedinUrl || '', githubUrl: p.githubUrl || '', portfolioUrl: p.portfolioUrl || '',
    });
    setEditError('');
    setEditOpen(true);
  }, [profile]);

  const gapDispatch = {
    editProfile: openEditDialog,
    addEducation: scrollToSection('sec-education', setShowEduForm),
    addSkill: scrollToSection('sec-skills', null),
    addProject: scrollToSection('sec-projects', setShowProjectForm),
    generateResume: scrollToSection('sec-resume', null),
  };

  const togglePublic = useCallback(async () => {
    if (!profile) return;
    const nextPublic = !profile.isPublic;
    setProfile((p) => ({ ...p, isPublic: nextPublic }));
    try {
      const updated = await updateMyProfile({ ...profile, isPublic: nextPublic });
      setProfile(updated);
    } catch (e) {
      setProfile((p) => ({ ...p, isPublic: !nextPublic }));
      showToast('Could not update visibility', e.message, 'danger');
    }
  }, [profile, showToast]);

  const saveEditDialog = useCallback(async () => {
    if (!filled(editDraft.firstName) || !filled(editDraft.lastName)) {
      setEditError('First and last name are required');
      return;
    }
    setEditSaving(true);
    setEditError('');
    try {
      const updated = await updateMyProfile({ ...editDraft, isPublic: profile.isPublic });
      setProfile(updated);
      setEducations(updated.educations || educations);
      setSkills(updated.skills || skills);
      setProjects(updated.projects || projects);
      setCertificates(updated.certificates || certificates);
      setEditOpen(false);
      showToast('Profile updated', 'Your changes are saved.');
    } catch (e) {
      setEditError(e.message);
    } finally {
      setEditSaving(false);
    }
  }, [editDraft, profile, educations, skills, projects, certificates, showToast]);

  // Skills
  const existingSkillNames = new Set(skills.map((s) => s.skillName.toLowerCase()));
  const q = skillQuery.trim().toLowerCase();
  const filteredCatalogue = skillCatalogue.filter((n) => !existingSkillNames.has(n.toLowerCase()) && (!q || n.toLowerCase().includes(q))).slice(0, 8);
  const exactMatch = skillCatalogue.some((n) => n.toLowerCase() === q);
  const showAddCustom = q.length > 0 && !exactMatch && !existingSkillNames.has(q);

  const submitSkill = useCallback(async () => {
    const name = skillQuery.trim();
    if (!name) return;
    if (existingSkillNames.has(name.toLowerCase())) {
      setSkillFormError(`Skill '${name}' is already on this profile`);
      return;
    }
    const predefined = skillCatalogue.some((s) => s.toLowerCase() === name.toLowerCase());
    setSkillFormError('');
    try {
      const created = await addSkill({ skillName: name, proficiencyLevel: pendingProficiency || null, isCustom: !predefined });
      setSkills((s) => [...s, created]);
      setSkillQuery(''); setPendingProficiency(''); setSkillDropdownOpen(false);
      loadAll({ silent: true });
    } catch (e) {
      setSkillFormError(e.message);
    }
  }, [skillQuery, existingSkillNames, skillCatalogue, pendingProficiency, loadAll]);

  const deleteSkill = useCallback(async (id) => {
    try {
      await apiDeleteSkill(id);
      setSkills((s) => s.filter((sk) => sk.id !== id));
      loadAll({ silent: true });
      showToast('Skill removed', 'It no longer appears on your profile.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    }
  }, [loadAll, showToast]);

  // Education
  const resetEduForm = () => { setShowEduForm(false); setEditingEduId(null); setEduFormError(''); setEduDraft(EMPTY_EDU); };
  const editEducation = (e) => {
    setEditingEduId(e.id); setEduFormError('');
    setEduDraft({ institution: e.institution || '', degree: e.degree || '', fieldOfStudy: e.fieldOfStudy || '', startYear: e.startYear ? String(e.startYear) : '', endYear: e.endYear ? String(e.endYear) : '', grade: e.grade || '', description: e.description || '' });
    setShowEduForm(true);
  };
  const submitEducation = useCallback(async () => {
    if (!filled(eduDraft.institution)) { setEduFormError('Institution is required'); return; }
    const payload = {
      institution: eduDraft.institution.trim(), degree: eduDraft.degree || null, fieldOfStudy: eduDraft.fieldOfStudy || null,
      startYear: eduDraft.startYear ? parseInt(eduDraft.startYear, 10) : null, endYear: eduDraft.endYear ? parseInt(eduDraft.endYear, 10) : null,
      grade: eduDraft.grade || null, description: eduDraft.description || null,
    };
    try {
      if (editingEduId) {
        const updated = await apiUpdateEducation(editingEduId, payload);
        setEducations((list) => list.map((e) => (e.id === editingEduId ? updated : e)));
        showToast('Education updated', 'Your changes are saved.');
      } else {
        const created = await addEducation(payload);
        setEducations((list) => [...list, created]);
      }
      loadAll({ silent: true });
      resetEduForm();
    } catch (e) {
      setEduFormError(e.message);
    }
  }, [eduDraft, editingEduId, loadAll, showToast]);
  const deleteEducation = useCallback(async (id) => {
    try {
      await apiDeleteEducation(id);
      setEducations((list) => list.filter((e) => e.id !== id));
      loadAll({ silent: true });
      showToast('Education removed', 'The entry was deleted.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    }
  }, [loadAll, showToast]);

  // Projects
  const resetProjectForm = () => { setShowProjectForm(false); setEditingProjectId(null); setProjectFormError(''); setProjectDraft(EMPTY_PROJECT); };
  const editProjectRow = (p) => {
    setEditingProjectId(p.id); setProjectFormError('');
    setProjectDraft({
      title: p.title || '', description: p.description || '', techStack: p.techStack || '', projectUrl: p.projectUrl || '', githubUrl: p.githubUrl || '',
      startDate: p.startDate || '', endDate: p.endDate || '', isOngoing: !!p.isOngoing,
      coverFile: null, coverImageUrl: coverUrls[p.id] || '',
    });
    setShowProjectForm(true);
  };
  const readCoverFile = (file) => {
    if (!file) return;
    setProjectDraft((d) => ({ ...d, coverFile: file, coverImageUrl: URL.createObjectURL(file) }));
  };
  const removeCover = useCallback(async () => {
    setProjectDraft((d) => ({ ...d, coverFile: null, coverImageUrl: '' }));
    if (!editingProjectId) return;
    try {
      await deleteProjectCover(editingProjectId);
      setCoverUrls((prev) => {
        const next = { ...prev };
        delete next[editingProjectId];
        return next;
      });
      setProjects((list) => list.map((p) => (p.id === editingProjectId ? { ...p, hasCoverImage: false } : p)));
      showToast('Cover image removed', 'Upload a new one, or save without one.');
    } catch (e) {
      showToast('Could not remove cover image', e.message, 'danger');
    }
  }, [editingProjectId, showToast]);
  const submitProject = useCallback(async () => {
    if (!filled(projectDraft.title)) { setProjectFormError('Title is required'); return; }
    const payload = {
      title: projectDraft.title.trim(), description: projectDraft.description || null, techStack: projectDraft.techStack || null,
      projectUrl: projectDraft.projectUrl || null, githubUrl: projectDraft.githubUrl || null,
      startDate: projectDraft.startDate || null, endDate: projectDraft.isOngoing ? null : (projectDraft.endDate || null), isOngoing: !!projectDraft.isOngoing,
    };
    try {
      let projectId = editingProjectId;
      if (editingProjectId) {
        const updated = await apiUpdateProject(editingProjectId, payload);
        setProjects((list) => list.map((p) => (p.id === editingProjectId ? updated : p)));
        showToast('Project updated', 'Your changes are saved.');
      } else {
        const created = await addProject(payload);
        projectId = created.id;
        setProjects((list) => [...list, created]);
      }
      if (projectDraft.coverFile) {
        await uploadProjectCover(projectId, projectDraft.coverFile);
        setCoverUrls((prev) => ({ ...prev, [projectId]: projectDraft.coverImageUrl }));
      }
      loadAll({ silent: true });
      resetProjectForm();
    } catch (e) {
      setProjectFormError(e.message);
    }
  }, [projectDraft, editingProjectId, loadAll, showToast]);
  const deleteProject = useCallback(async (id) => {
    try {
      await apiDeleteProject(id);
      setProjects((list) => list.filter((p) => p.id !== id));
      loadAll({ silent: true });
      showToast('Project removed', 'The entry was deleted.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    }
  }, [loadAll, showToast]);

  // Certificates
  const resetCertForm = () => { setShowCertForm(false); setEditingCertId(null); setCertFormError(''); setCertDraft(EMPTY_CERT); };
  const editCertificate = (c) => {
    setEditingCertId(c.id); setCertFormError('');
    setCertDraft({ name: c.name || '', issuingOrganization: c.issuingOrganization || '', issueDate: c.issueDate || '', expiryDate: c.expiryDate || '', credentialUrl: c.credentialUrl || '' });
    setShowCertForm(true);
  };
  const submitCertificate = useCallback(async () => {
    if (!filled(certDraft.name)) { setCertFormError('Certificate name is required'); return; }
    const payload = { name: certDraft.name.trim(), issuingOrganization: certDraft.issuingOrganization || null, issueDate: certDraft.issueDate || null, expiryDate: certDraft.expiryDate || null, credentialUrl: certDraft.credentialUrl || null };
    try {
      if (editingCertId) {
        const updated = await apiUpdateCertificate(editingCertId, payload);
        setCertificates((list) => list.map((c) => (c.id === editingCertId ? updated : c)));
        showToast('Certificate updated', 'Your changes are saved.');
      } else {
        const created = await addCertificate(payload);
        setCertificates((list) => [...list, created]);
      }
      resetCertForm();
    } catch (e) {
      setCertFormError(e.message);
    }
  }, [certDraft, editingCertId, showToast]);
  const deleteCertificate = useCallback(async (id) => {
    try {
      await apiDeleteCertificate(id);
      setCertificates((list) => list.filter((c) => c.id !== id));
      showToast('Certificate removed', 'The entry was deleted.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    }
  }, [showToast]);

  // Résumé -- fully real
  const handleGenerateResume = useCallback(async () => {
    setGenerating(true);
    try {
      const resume = await generateResume();
      setResumes((list) => [resume, ...list.map((r) => ({ ...r, isDefault: false }))]);
      await loadAll({ silent: true });
      showToast('Résumé generated', `${Math.round(resume.atsScore)}% ATS match on this version.`);
    } catch (e) {
      showToast('Could not generate résumé', e.message, 'danger');
    } finally {
      setGenerating(false);
    }
  }, [loadAll, showToast]);

  const handleDeleteResume = useCallback(async (id) => {
    try {
      await deleteResume(id);
      setResumes((list) => list.filter((r) => r.id !== id));
      await loadAll({ silent: true });
      showToast('Résumé deleted', 'It no longer appears on your applications.');
    } catch (e) {
      showToast('Could not delete', e.message, 'danger');
    }
  }, [loadAll, showToast]);

  const handleDownloadResume = useCallback(async (r) => {
    try {
      await downloadResume(r.id, r.fileName);
    } catch (e) {
      showToast('Could not download', e.message, 'danger');
    }
  }, [showToast]);

  const onAvatarFileChange = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    setAvatarSrc(URL.createObjectURL(file));
    try {
      await uploadAvatar(file);
      showToast('Photo updated', 'Your new profile photo is saved.');
    } catch (err) {
      showToast('Could not upload photo', err.message, 'danger');
    }
  };

  const sidebarWidth = navCollapsed ? '60px' : '248px';
  const p = profile || {};
  const completion = profile ? (profile.profileCompletionPercentage ?? 0) : 0;
  const gapsRaw = profile ? computeGaps(p, skills, educations, projects) : [];
  const gaps = gapsRaw.map((g) => ({ ...g, weightLabel: `${g.weight}%`, onClick: gapDispatch[g.action] }));
  const locationLine = [p.city, p.state, p.country].filter(Boolean).join(', ');
  const defaultResume = resumes.find((r) => r.isDefault);
  const displayName = p.firstName ? `${p.firstName} ${p.lastName}` : '';

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>

      <header className="cb-pf-header" style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <div className="cb-pf-search" style={{ flex: 1, display: 'flex', justifyContent: 'center', padding: '0 20px', minWidth: 0 }}>
          <div style={{ width: '100%', maxWidth: 440 }}>
            <Input placeholder="Search careers, roadmap steps, opportunities" value="" onChange={() => {}} />
          </div>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
          <div style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', overflow: 'hidden', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={15} />}
            </div>
            <div className="cb-pf-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{displayName || 'Your account'}</span>
              <span style={{ fontSize: 10, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
            </div>
          </div>
        </div>
      </header>

      <div className="cb-pf-shell" style={{ '--sidebar-w': sidebarWidth }}>
        <aside style={{ borderRight: '1px solid var(--line-hairline)', position: 'sticky', top: 64, height: 'calc(100vh - 64px)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${navCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-pf-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
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
                {!navCollapsed && <span className="cb-pf-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          {!navCollapsed && (
            <div className="cb-pf-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
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
          <div className="cb-pf-main-pad" style={{ maxWidth: 1160, margin: '0 auto', padding: '32px 32px 64px', boxSizing: 'border-box' }}>

            {loading && <Skeleton height={520} />}

            {!loading && (
              <div className="cb-pf-fade" style={{ display: 'flex', flexDirection: 'column', gap: 36, opacity: fadeIn ? 1 : 0, transition: 'opacity 140ms cubic-bezier(.2,0,.2,1)' }}>

                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Profile · {completion}% complete</span>
                  <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
                    <Switch label="Visible to recruiters" checked={p.isPublic !== false} onChange={togglePublic} />
                    {defaultResume ? (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', border: '1px solid var(--line-hairline)', background: 'var(--bone-50)' }}>
                        <Badge tone="accent">Default</Badge>
                        <span title={defaultResume.fileName} style={{ fontSize: 12, color: 'var(--ink-700)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: 150 }}>{defaultResume.fileName}</span>
                        <IconButton icon="external-link" label="Open résumé workspace" onClick={() => scrollToSection('sec-resume', null)()} />
                      </div>
                    ) : (
                      <Button size="sm" variant="primary" iconAfter="download" disabled={generating} onClick={handleGenerateResume}>
                        {generating ? 'Generating…' : 'Attach résumé from workspace'}
                      </Button>
                    )}
                  </div>
                </div>

                <section style={{ display: 'grid', gridTemplateColumns: 'auto 1fr auto', gap: 26, alignItems: 'start' }} className="cb-pf-hero">
                  <div style={{ position: 'relative', width: 104, height: 104, flexShrink: 0 }}>
                    <div style={{ width: 104, height: 104, borderRadius: '50%', overflow: 'hidden', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={40} />}
                    </div>
                    <button
                      type="button"
                      onClick={() => avatarInputRef.current && avatarInputRef.current.click()}
                      title="Change photo"
                      style={{ position: 'absolute', bottom: 0, right: 0, width: 30, height: 30, borderRadius: '50%', background: 'var(--ink-900)', border: '2px solid var(--surface-page)', display: 'flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer', padding: 0 }}
                    >
                      <Icon name="camera" size={14} style={{ color: 'var(--text-inverse)' }} />
                    </button>
                    <input type="file" accept="image/*" ref={avatarInputRef} onChange={onAvatarFileChange} style={{ display: 'none' }} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0 }}>
                    <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 42, lineHeight: 1.05, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{displayName}</h1>
                    <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{p.email || ''} · Student</span>
                    <p style={{ fontSize: 15, lineHeight: 1.65, color: 'var(--ink-700)', margin: 0, maxWidth: 600 }}>{p.bio || ''}</p>
                    <div style={{ display: 'flex', gap: 18, flexWrap: 'wrap', fontSize: 13, color: 'var(--ink-600)', marginTop: 2, alignItems: 'center' }}>
                      {locationLine && <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}><Icon name="map-pin" size={14} />{locationLine}</span>}
                      {p.linkedinUrl && <a href={urlHref(p.linkedinUrl)} target="_blank" rel="noopener noreferrer">LinkedIn</a>}
                      {p.githubUrl && <a href={urlHref(p.githubUrl)} target="_blank" rel="noopener noreferrer">GitHub</a>}
                      {p.portfolioUrl && <a href={urlHref(p.portfolioUrl)} target="_blank" rel="noopener noreferrer">Portfolio</a>}
                    </div>
                  </div>
                  <Button variant="secondary" size="sm" onClick={openEditDialog}>Edit profile</Button>
                </section>

                <section style={{ display: 'grid', gridTemplateColumns: 'minmax(0,1fr) minmax(0,1.4fr)', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }} className="cb-pf-score-grid">
                  <div style={{ background: 'var(--surface-card)', padding: '28px 26px', display: 'flex', flexDirection: 'column', gap: 18, alignItems: 'center', justifyContent: 'center' }}>
                    <ScoreRingSmall value={completion} />
                    <span style={{ fontSize: 12, color: 'var(--ink-400)', textAlign: 'center' }}>Profile depth is 20% of your placement readiness</span>
                  </div>
                  <div style={{ background: 'var(--surface-card)', padding: '28px 26px', display: 'flex', flexDirection: 'column', gap: 14 }}>
                    <SectionHeader label={gaps.length ? `Finish these ${gaps.length} thing${gaps.length === 1 ? '' : 's'}` : 'Profile fully scored'} />
                    {gaps.length === 0 && (
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '6px 2px' }}>
                        <Icon name="check" size={16} style={{ color: 'var(--status-success)' }} />
                        <span style={{ fontSize: 14, color: 'var(--ink-700)' }}>All seven completion criteria are met.</span>
                      </div>
                    )}
                    {gaps.length > 0 && (
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                        {gaps.map((gap) => (
                          <div key={gap.title} style={{ background: 'var(--surface-page)', padding: '14px 16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 14 }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                              <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{gap.title}</span>
                              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{gap.detail} · {gap.weightLabel}</span>
                            </div>
                            <Button size="sm" variant="secondary" onClick={gap.onClick}>{gap.cta}</Button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </section>

                <section id="sec-skills">
                  <SectionHeader label="Skills" />
                  <div className="cb-pf-skill-form" style={{ display: 'flex', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap', justifyContent: 'space-between', marginTop: 20 }}>
                    <div style={{ position: 'relative', width: 280 }}>
                      <span style={{ display: 'block', fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)', marginBottom: 6 }}>Skill</span>
                      <Input
                        placeholder="Start typing — e.g. Python"
                        value={skillQuery}
                        onChange={(e) => { setSkillQuery(e.target.value); setSkillDropdownOpen(true); }}
                      />
                      {skillDropdownOpen && (
                        <div
                          style={{ position: 'absolute', top: 64, left: 0, right: 0, zIndex: 20, background: 'var(--surface-card)', border: '1px solid var(--line-strong)', maxHeight: 230, overflowY: 'auto', boxShadow: 'var(--shadow-menu)' }}
                          onMouseLeave={() => { blurTimerRef.current = setTimeout(() => setSkillDropdownOpen(false), 120); }}
                        >
                          {filteredCatalogue.map((name) => (
                            <div key={name} onMouseDown={() => { setSkillQuery(name); setSkillDropdownOpen(false); }} style={{ padding: '9px 12px', fontSize: 13, color: 'var(--ink-800)', cursor: 'pointer' }}>{name}</div>
                          ))}
                          {showAddCustom && (
                            <div onMouseDown={() => setSkillDropdownOpen(false)} style={{ padding: '9px 12px', fontSize: 13, color: 'var(--taupe-700)', borderTop: '1px solid var(--line-hairline)', cursor: 'pointer' }}>Add &quot;{skillQuery}&quot; as a custom skill</div>
                          )}
                          {filteredCatalogue.length === 0 && !showAddCustom && q.length > 0 && (
                            <div style={{ padding: '9px 12px', fontSize: 13, color: 'var(--ink-400)' }}>No catalogue match</div>
                          )}
                        </div>
                      )}
                    </div>
                    <div style={{ width: 180 }}>
                      <span style={{ display: 'block', fontSize: 11, fontWeight: 500, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-500)', marginBottom: 6 }}>Proficiency</span>
                      <select
                        value={pendingProficiency}
                        onChange={(e) => setPendingProficiency(e.target.value)}
                        style={{ width: '100%', boxSizing: 'border-box', padding: '9px 10px', fontSize: 13, fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-sm)', outline: 'none', cursor: 'pointer' }}
                      >
                        {PROFICIENCY_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                      </select>
                    </div>
                    <Button variant="secondary" size="md" onClick={submitSkill} style={{ marginTop: 20 }}>Add</Button>
                  </div>
                  {skillFormError && <span style={{ display: 'block', marginTop: 8, fontSize: 12, color: 'var(--status-danger)' }}>{skillFormError}</span>}

                  {skills.length === 0 ? (
                    <EmptyRow icon="sparkles" title="No skills yet" message="Add at least 2 — it's 15% of your readiness score." />
                  ) : (
                    <div className="cb-pf-skill-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', marginTop: 20 }}>
                      {skills.map((s) => {
                        const level = s.proficiencyLevel;
                        const isNotSet = !level;
                        return (
                          <div key={s.id} style={{ background: 'var(--surface-card)', padding: '16px 20px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16 }}>
                            <span style={{ fontSize: 14, color: 'var(--ink-900)' }}>{s.skillName}{s.isCustom ? '  ·  custom' : ''}</span>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                              <Badge tone={isNotSet ? 'default' : (LEVEL_TONE[level] || 'default')}>{isNotSet ? 'NOT SET' : level}</Badge>
                              <IconButton icon="trash-2" label="Delete skill" onClick={() => deleteSkill(s.id)} />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section id="sec-education">
                  <SectionHeader label="Education" actionLabel={showEduForm ? 'Cancel' : 'Add entry'} onAction={() => (showEduForm ? resetEduForm() : setShowEduForm(true))} />

                  {showEduForm && (
                    <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', padding: 24, marginTop: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 16 }} className="cb-pf-form-grid">
                        <Field label="Institution"><Input value={eduDraft.institution} onChange={(e) => setEduDraft((d) => ({ ...d, institution: e.target.value }))} placeholder="e.g. RV College of Engineering" /></Field>
                        <Field label="Degree"><Input value={eduDraft.degree} onChange={(e) => setEduDraft((d) => ({ ...d, degree: e.target.value }))} placeholder="e.g. B.E." /></Field>
                        <Field label="Field of study"><Input value={eduDraft.fieldOfStudy} onChange={(e) => setEduDraft((d) => ({ ...d, fieldOfStudy: e.target.value }))} placeholder="e.g. Information Science" /></Field>
                        <Field label="Grade"><Input value={eduDraft.grade} onChange={(e) => setEduDraft((d) => ({ ...d, grade: e.target.value }))} placeholder="e.g. CGPA 8.7" /></Field>
                        <Field label="Start year"><Input type="number" value={eduDraft.startYear} onChange={(e) => setEduDraft((d) => ({ ...d, startYear: e.target.value }))} placeholder="2022" /></Field>
                        <Field label="End year" hint="Leave blank if ongoing"><Input type="number" value={eduDraft.endYear} onChange={(e) => setEduDraft((d) => ({ ...d, endYear: e.target.value }))} placeholder="2026" /></Field>
                      </div>
                      <Field label="Description"><Textarea rows={2} value={eduDraft.description} onChange={(e) => setEduDraft((d) => ({ ...d, description: e.target.value }))} placeholder="Optional" /></Field>
                      {eduFormError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{eduFormError}</span>}
                      <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                        <Button variant="ghost" size="md" onClick={resetEduForm}>Cancel</Button>
                        <Button variant="primary" size="md" onClick={submitEducation}>{editingEduId ? 'Save changes' : 'Save'}</Button>
                      </div>
                    </div>
                  )}

                  {educations.length === 0 ? (
                    <EmptyRow icon="graduation-cap" title="No education yet" message="Add one entry — it's 15% of your readiness score." actionLabel="Add entry" onAction={() => setShowEduForm(true)} />
                  ) : (
                    <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, background: 'var(--surface-card)', marginTop: 20 }}>
                      {educations.map((e) => {
                        const datesMissing = !(e.startYear && e.endYear) && !(e.startYear && !e.endYear);
                        const dateText = e.startYear && e.endYear ? `${e.startYear} – ${e.endYear}` : (e.startYear ? `${e.startYear} – Present` : '');
                        return (
                          <div key={e.id} className="cb-pf-edu-row" style={{ padding: '20px 22px', borderTop: '1px solid var(--line-hairline)', display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 20, alignItems: 'center' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                              <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink-900)' }}>{[e.degree, e.fieldOfStudy].filter(Boolean).join(', ') || e.institution}</span>
                              <span style={{ fontSize: 13, color: 'var(--ink-600)' }}>{[e.institution, e.grade].filter(Boolean).join(' · ')}</span>
                              {e.description && <span style={{ fontSize: 13, color: 'var(--ink-600)', lineHeight: 1.5 }}>{e.description}</span>}
                            </div>
                            {datesMissing ? <Badge tone="warning">Dates missing</Badge> : <span className="cb-num" style={{ fontSize: 13, color: 'var(--ink-400)', whiteSpace: 'nowrap' }}>{dateText}</span>}
                            <div style={{ display: 'flex', gap: 6 }}>
                              <IconButton icon="pencil" label="Edit education" onClick={() => editEducation(e)} />
                              <IconButton icon="trash-2" label="Delete education" onClick={() => deleteEducation(e.id)} />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section id="sec-projects">
                  <SectionHeader label="Projects" actionLabel={showProjectForm ? 'Cancel' : 'Add project'} onAction={() => (showProjectForm ? resetProjectForm() : setShowProjectForm(true))} />

                  {showProjectForm && (
                    <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', padding: 24, marginTop: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 16 }} className="cb-pf-form-grid">
                        <Field label="Title"><Input value={projectDraft.title} onChange={(e) => setProjectDraft((d) => ({ ...d, title: e.target.value }))} placeholder="e.g. Retention teardown" /></Field>
                        <Field label="Tech stack" hint="Comma-separated"><Input value={projectDraft.techStack} onChange={(e) => setProjectDraft((d) => ({ ...d, techStack: e.target.value }))} placeholder="e.g. SQL, Python" /></Field>
                        <Field label="Project URL"><Input value={projectDraft.projectUrl} onChange={(e) => setProjectDraft((d) => ({ ...d, projectUrl: e.target.value }))} placeholder="https://" /></Field>
                        <Field label="GitHub URL"><Input value={projectDraft.githubUrl} onChange={(e) => setProjectDraft((d) => ({ ...d, githubUrl: e.target.value }))} placeholder="https://github.com/…" /></Field>
                        <Field label="Start date"><Input type="date" value={projectDraft.startDate} onChange={(e) => setProjectDraft((d) => ({ ...d, startDate: e.target.value }))} /></Field>
                        <Field label="End date"><Input type="date" value={projectDraft.endDate} onChange={(e) => setProjectDraft((d) => ({ ...d, endDate: e.target.value }))} /></Field>
                      </div>
                      <Field label="Description"><Textarea rows={2} value={projectDraft.description} onChange={(e) => setProjectDraft((d) => ({ ...d, description: e.target.value }))} placeholder="Optional" /></Field>
                      <Checkbox label="This is ongoing" checked={projectDraft.isOngoing} onChange={(e) => setProjectDraft((d) => ({ ...d, isOngoing: e.target.checked }))} />
                      <Field label="Cover image" hint="Drag and drop, or browse">
                        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12 }}>
                          <div
                            onClick={() => coverInputRef.current && coverInputRef.current.click()}
                            onDragOver={(e) => e.preventDefault()}
                            onDrop={(e) => { e.preventDefault(); readCoverFile(e.dataTransfer.files && e.dataTransfer.files[0]); }}
                            style={{ position: 'relative', width: 220, height: 130, border: '1px dashed var(--line-strong)', cursor: 'pointer', overflow: 'hidden', background: 'var(--bone-50)' }}
                          >
                            <img src={projectDraft.coverImageUrl || '/images/logo-monogram.png'} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block', opacity: projectDraft.coverImageUrl ? 1 : 0.25 }} />
                            <input type="file" accept="image/*" ref={coverInputRef} onChange={(e) => readCoverFile(e.target.files && e.target.files[0])} style={{ display: 'none' }} />
                          </div>
                          {projectDraft.coverImageUrl && (
                            <IconButton icon="trash-2" label="Remove cover image" onClick={removeCover} />
                          )}
                        </div>
                      </Field>
                      {projectFormError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{projectFormError}</span>}
                      <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                        <Button variant="ghost" size="md" onClick={resetProjectForm}>Cancel</Button>
                        <Button variant="primary" size="md" onClick={submitProject}>{editingProjectId ? 'Save changes' : 'Save'}</Button>
                      </div>
                    </div>
                  )}

                  {projects.length === 0 ? (
                    <EmptyRow icon="briefcase" title="No projects yet" message="Add one — it's 20% of your readiness score, the single biggest lever here." actionLabel="Add project" onAction={() => setShowProjectForm(true)} />
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', marginTop: 20 }}>
                      {projects.map((p2) => {
                        const yr = (d) => (d ? String(d).slice(0, 4) : '');
                        const tags = (p2.techStack || '').split(',').map((t) => t.trim()).filter(Boolean).slice(0, 6);
                        return (
                          <article key={p2.id} className="cb-pf-project-row" style={{ background: 'var(--surface-card)', display: 'grid', gridTemplateColumns: 'minmax(260px,1fr) 380px', alignItems: 'stretch' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 9, minWidth: 0, padding: 22 }}>
                              <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 20, lineHeight: 1.2, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>{p2.title}</h3>
                              {p2.startDate && <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{yr(p2.startDate)} – {p2.isOngoing ? 'Present' : (p2.endDate ? yr(p2.endDate) : '')}</span>}
                              <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-600)', margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{p2.description}</p>
                              <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap', marginTop: 4 }}>
                                {tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                              </div>
                            </div>
                            <div style={{ position: 'relative', minWidth: 0, minHeight: 220, overflow: 'hidden' }} className="cb-pf-project-cover">
                              {coverUrls[p2.id] && <img src={coverUrls[p2.id]} alt="" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', display: 'block' }} />}
                              <div style={{ position: 'absolute', top: 10, right: 10, display: 'flex', gap: 6 }}>
                                <IconButton icon="pencil" label="Edit project" onClick={() => editProjectRow(p2)} />
                                <IconButton icon="trash-2" label="Delete project" onClick={() => deleteProject(p2.id)} />
                              </div>
                            </div>
                          </article>
                        );
                      })}
                    </div>
                  )}
                </section>

                <section id="sec-certificates">
                  <SectionHeader label="Certificates" actionLabel={showCertForm ? 'Cancel' : 'Add certificate'} onAction={() => (showCertForm ? resetCertForm() : setShowCertForm(true))} />

                  {showCertForm && (
                    <div style={{ border: '1px solid var(--line-hairline)', background: 'var(--surface-card)', padding: 24, marginTop: 20, display: 'flex', flexDirection: 'column', gap: 16 }}>
                      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0,1fr))', gap: 16 }} className="cb-pf-form-grid">
                        <Field label="Name"><Input value={certDraft.name} onChange={(e) => setCertDraft((d) => ({ ...d, name: e.target.value }))} placeholder="e.g. AWS Certified Cloud Practitioner" /></Field>
                        <Field label="Issuing organization"><Input value={certDraft.issuingOrganization} onChange={(e) => setCertDraft((d) => ({ ...d, issuingOrganization: e.target.value }))} placeholder="e.g. Amazon Web Services" /></Field>
                        <Field label="Issue date"><Input type="date" value={certDraft.issueDate} onChange={(e) => setCertDraft((d) => ({ ...d, issueDate: e.target.value }))} /></Field>
                        <Field label="Expiry date" hint="Leave blank if it never expires"><Input type="date" value={certDraft.expiryDate} onChange={(e) => setCertDraft((d) => ({ ...d, expiryDate: e.target.value }))} /></Field>
                      </div>
                      <Field label="Credential URL"><Input value={certDraft.credentialUrl} onChange={(e) => setCertDraft((d) => ({ ...d, credentialUrl: e.target.value }))} placeholder="https://" /></Field>
                      {certFormError && <span style={{ fontSize: 12, color: 'var(--status-danger)' }}>{certFormError}</span>}
                      <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
                        <Button variant="ghost" size="md" onClick={resetCertForm}>Cancel</Button>
                        <Button variant="primary" size="md" onClick={submitCertificate}>{editingCertId ? 'Save changes' : 'Save'}</Button>
                      </div>
                    </div>
                  )}

                  {certificates.length === 0 ? (
                    <EmptyRow icon="award" title="No certificates yet" message="These don't affect your completion score, but they round out your profile for recruiters." actionLabel="Add certificate" onAction={() => setShowCertForm(true)} />
                  ) : (
                    <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, background: 'var(--surface-card)', marginTop: 20 }}>
                      {certificates.map((c) => (
                        <div key={c.id} style={{ padding: '18px 22px', borderTop: '1px solid var(--line-hairline)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 20 }}>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                            <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink-900)' }}>{c.name}</span>
                            <span style={{ fontSize: 13, color: 'var(--ink-600)' }}>{[c.issuingOrganization, c.issueDate ? `Issued ${fmtDate(c.issueDate)}` : null].filter(Boolean).join(' · ')}</span>
                          </div>
                          <div style={{ display: 'flex', gap: 6 }}>
                            <IconButton icon="pencil" label="Edit certificate" onClick={() => editCertificate(c)} />
                            <IconButton icon="trash-2" label="Delete certificate" onClick={() => deleteCertificate(c.id)} />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </section>

                <section id="sec-resume">
                  <SectionHeader label="Résumé" actionLabel="Open résumé workspace" onAction={() => { window.location.href = '/resume'; }} />
                  {resumes.length === 0 ? (
                    <EmptyRow icon="file-text" title="No résumé attached yet" message="Build and score your résumé, then set a version as default to attach it here." actionLabel={generating ? 'Generating…' : 'Attach résumé from workspace'} onAction={handleGenerateResume} />
                  ) : (
                    <>
                      <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, background: 'var(--surface-card)', marginTop: 20 }}>
                        {resumes.map((r) => (
                          <div key={r.id} style={{ padding: '18px 22px', borderTop: '1px solid var(--line-hairline)', display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 20, flexWrap: 'wrap' }}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                              <span style={{ fontSize: 15, fontWeight: 500, color: 'var(--ink-900)' }}>{r.fileName}</span>
                              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>v{r.version} · {fmtDate(r.generatedAt)}</span>
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                              <Badge tone={toneForScore(r.atsScore || 0)}>{Math.round(r.atsScore || 0)}% ATS match</Badge>
                              {r.isDefault && <Badge tone="inverse">Default</Badge>}
                              <IconButton icon="download" label="Download résumé" onClick={() => handleDownloadResume(r)} />
                              <IconButton icon="x" label="Delete résumé" onClick={() => handleDeleteResume(r.id)} />
                            </div>
                          </div>
                        ))}
                      </div>
                      <p style={{ fontSize: 12, color: 'var(--ink-400)', margin: '10px 2px 0' }}>
                        <Link to="/resume">Manage versions, ATS scores and downloads in the résumé workspace →</Link>
                      </p>
                    </>
                  )}
                </section>

              </div>
            )}

          </div>
        </main>
      </div>

      <EditProfileDialog
        open={editOpen}
        draft={editDraft}
        onChange={setEditDraft}
        error={editError}
        saving={editSaving}
        onClose={() => setEditOpen(false)}
        onSave={saveEditDialog}
      />

      {toast.visible && (
        <div style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 60, maxWidth: 360 }}>
          <div style={{ background: toast.tone === 'danger' ? 'var(--status-danger)' : toast.tone === 'warning' ? 'var(--ink-700)' : 'var(--ink-900)', color: 'var(--bone-50)', padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 4, boxShadow: 'var(--shadow-menu)' }}>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{toast.title}</span>
            <span style={{ fontSize: 13, color: 'var(--bone-300)' }}>{toast.message}</span>
          </div>
        </div>
      )}
    </div>
  );
}
