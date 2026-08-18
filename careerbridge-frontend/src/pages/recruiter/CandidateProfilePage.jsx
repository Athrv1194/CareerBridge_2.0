import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Alert, Badge, Button, Icon, ScoreRing, Skeleton, Tag,
} from '../../components/ui';
import { getCandidateProfile, getCandidateAvatarBlobUrl } from '../../api/studentApi';
import { getStudentResumes, downloadResume } from '../../api/resumeApi';

function fmtDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}
function urlHref(u) {
  return u ? (u.startsWith('http') ? u : `https://${u}`) : '';
}

function SectionHeader({ label }) {
  return (
    <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
      <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-500)' }}>{label}</span>
      <div style={{ flex: 1, height: 1, background: 'var(--line-hairline)' }} />
    </div>
  );
}

export default function CandidateProfilePage() {
  const { studentId } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const prsFromState = location.state?.prsScore;

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [avatarSrc, setAvatarSrc] = useState('');
  const [resumes, setResumes] = useState([]);
  const [downloadingId, setDownloadingId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    getCandidateProfile(studentId)
      .then((p) => { if (!cancelled) setProfile(p); })
      .catch((e) => { if (!cancelled) setError(e.message || 'Could not load this candidate.'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    getStudentResumes(studentId).then((list) => { if (!cancelled) setResumes(list); }).catch(() => {});
    return () => { cancelled = true; };
  }, [studentId]);

  useEffect(() => {
    if (!profile?.hasAvatar) return undefined;
    let cancelled = false;
    getCandidateAvatarBlobUrl(studentId).then((url) => { if (!cancelled && url) setAvatarSrc(url); }).catch(() => {});
    return () => { cancelled = true; };
  }, [studentId, profile?.hasAvatar]);

  const handleDownload = async (r) => {
    setDownloadingId(r.id);
    try {
      await downloadResume(r.id, r.fileName);
    } catch {
      // downloadResume already surfaces nothing on failure here -- a silent no-op is acceptable,
      // the button simply stops spinning and the recruiter can try again.
    } finally {
      setDownloadingId(null);
    }
  };

  const p = profile || {};
  const contactLine = [p.city, p.state, p.country].filter(Boolean).join(', ');
  const hasPrs = prsFromState != null && prsFromState !== -1;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <header style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', gap: 16, padding: '0 28px', boxSizing: 'border-box' }}>
        <Button variant="ghost" size="sm" icon="arrow-left" onClick={() => navigate(-1)}>Back to candidates</Button>
      </header>

      <main style={{ maxWidth: 760, margin: '0 auto', padding: '40px 32px 80px' }}>
        {loading && <Skeleton height={420} />}
        {!loading && error && <Alert tone="danger" title="Could not load this candidate" message={error} />}

        {!loading && !error && profile && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 34 }}>
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: 20, flexWrap: 'wrap' }}>
              <div style={{ width: 84, height: 84, background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 28, fontWeight: 600, flexShrink: 0, overflow: 'hidden' }}>
                {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : `${(p.firstName?.[0] || '')}${(p.lastName?.[0] || '')}`.toUpperCase()}
              </div>
              <div style={{ flex: 1, minWidth: 240 }}>
                <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.15, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{p.firstName} {p.lastName}</h1>
                {p.bio && <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-600)', margin: '8px 0 0', maxWidth: 480 }}>{p.bio}</p>}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 14, marginTop: 12, fontSize: 13, color: 'var(--ink-500)' }}>
                  {p.email && <span>{p.email}</span>}
                  {p.phone && <span>{p.phone}</span>}
                  {contactLine && <span>{contactLine}</span>}
                </div>
                {(p.linkedinUrl || p.githubUrl || p.portfolioUrl) && (
                  <div style={{ display: 'flex', gap: 16, marginTop: 10, flexWrap: 'wrap' }}>
                    {p.linkedinUrl && <a href={urlHref(p.linkedinUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 13, color: 'var(--taupe-700)', display: 'inline-flex', alignItems: 'center', gap: 5, border: 0 }}>LinkedIn <Icon name="external-link" size={12} /></a>}
                    {p.githubUrl && <a href={urlHref(p.githubUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 13, color: 'var(--taupe-700)', display: 'inline-flex', alignItems: 'center', gap: 5, border: 0 }}>GitHub <Icon name="external-link" size={12} /></a>}
                    {p.portfolioUrl && <a href={urlHref(p.portfolioUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 13, color: 'var(--taupe-700)', display: 'inline-flex', alignItems: 'center', gap: 5, border: 0 }}>Portfolio <Icon name="external-link" size={12} /></a>}
                  </div>
                )}
              </div>
            </div>

            <div style={{ display: 'flex', gap: 32, flexWrap: 'wrap' }}>
              {hasPrs && <ScoreRing value={Math.round(prsFromState)} size="sm" label="Placement readiness" />}
              <ScoreRing value={p.profileCompletionPercentage ?? 0} size="sm" label="Profile complete" />
            </div>

            <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SectionHeader label="Skills" />
              {(p.skills || []).length === 0 ? (
                <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No skills listed.</span>
              ) : (
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                  {p.skills.map((s) => <Tag key={s.id || s.skillName}>{s.skillName}</Tag>)}
                </div>
              )}
            </section>

            <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SectionHeader label="Projects" />
              {(p.projects || []).length === 0 ? (
                <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No projects added.</span>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                  {p.projects.map((pj) => (
                    <div key={pj.id} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
                        <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--ink-900)' }}>{pj.title}</span>
                        {pj.projectUrl && <a href={urlHref(pj.projectUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 12, color: 'var(--taupe-700)', border: 0 }}>Live →</a>}
                        {pj.githubUrl && <a href={urlHref(pj.githubUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 12, color: 'var(--taupe-700)', border: 0 }}>Code →</a>}
                      </div>
                      {pj.description && <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>{pj.description}</p>}
                      {pj.techStack && <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>{pj.techStack}</span>}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SectionHeader label="Certificates" />
              {(p.certificates || []).length === 0 ? (
                <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No certificates added.</span>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {p.certificates.map((c) => (
                    <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 13, color: 'var(--ink-800)' }}>
                        {c.name}
                        {c.issuingOrganization && <span style={{ color: 'var(--ink-400)' }}> — {c.issuingOrganization}</span>}
                      </span>
                      {c.credentialUrl && <a href={urlHref(c.credentialUrl)} target="_blank" rel="noreferrer" style={{ fontSize: 12, color: 'var(--taupe-700)', border: 0 }}>View →</a>}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SectionHeader label="Education" />
              {(p.educations || []).length === 0 ? (
                <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No education added.</span>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {p.educations.map((ed) => (
                    <div key={ed.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
                      <span style={{ fontSize: 13, color: 'var(--ink-800)' }}>{[ed.degree, ed.fieldOfStudy].filter(Boolean).join(', ') || ed.institution}</span>
                      <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{ed.startYear} – {ed.endYear || 'Present'}</span>
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <SectionHeader label="Résumé" />
              {resumes.length === 0 ? (
                <span style={{ fontSize: 13, color: 'var(--ink-400)' }}>No résumé generated yet.</span>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                  {resumes.map((r) => (
                    <div key={r.id} style={{ background: 'var(--bone-50)', padding: '12px 14px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                      <div style={{ minWidth: 0, display: 'flex', alignItems: 'center', gap: 10 }}>
                        <Icon name="file-text" size={16} />
                        <div style={{ minWidth: 0 }}>
                          <div style={{ fontSize: 13, color: 'var(--ink-900)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                            v{r.version} · {Math.round(r.atsScore || 0)}% ATS {r.isDefault && <Badge tone="accent">Default</Badge>}
                          </div>
                          <div style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtDate(r.generatedAt)}</div>
                        </div>
                      </div>
                      <Button size="sm" variant="secondary" iconAfter="download" disabled={downloadingId === r.id} onClick={() => handleDownload(r)}>
                        {downloadingId === r.id ? 'Downloading…' : 'Download'}
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}
