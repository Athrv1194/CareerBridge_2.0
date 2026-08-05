import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  addEducation, addSkill, getMyProfileWithRetry, getSkillSuggestions, updateMyProfile,
} from '../../api/studentApi';
import { useAuthState } from '../../hooks/useAuth';
import {
  Alert, Button, Field, Icon, Input, Logo, ScoreRing, Select, StatTile, Switch, Tag, Textarea,
} from '../../components/ui';

const STEP_LABELS = ['Education', 'Skills', 'Basic info', 'Assessment'];
const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];
const MAX_EDUCATION = 3;
const MAX_SKILLS = 15;
const FALLBACK_SKILLS = [
  'Java', 'Python', 'JavaScript', 'TypeScript', 'C#', 'C++',
  'React', 'Angular', 'Vue', 'HTML/CSS', 'Tailwind CSS',
  'Node.js', '.NET', 'Spring Boot', 'Express.js', 'REST API',
  'SQL', 'MongoDB', 'AWS', 'Azure', 'Docker',
  'Git', 'GitHub', 'Postman', 'Figma',
  'Flutter', 'React Native', 'Kotlin',
];

const HEADINGS = [
  'Where are you studying?',
  'What can you already do?',
  'Tell us a little about yourself.',
  'One assessment unlocks everything.',
];
const SUBHEADINGS = [
  'Recruiters filter on graduation year and degree, so these two fields do real work.',
  'Unset skills are ignored in matching. Set proficiency honestly.',
  'This fills your public profile that recruiters browse.',
  'Your matches, roadmap, and readiness score are all generated from it. It takes about 18 minutes — you can leave and come back any time.',
];
const SIDE_COPY = [
  'Recruiters filter on this before reading a résumé.',
  'Every honest proficiency feeds the matching engine.',
  'What a recruiter sees when they search candidates.',
  'Every match and score traces back to this.',
];

function emptyEducation() {
  return {
    institution: '', degree: '', fieldOfStudy: '', graduationYear: '', grade: '',
  };
}

const BADGE_TONE_STYLE = {
  success: { background: 'rgba(143,179,147,.22)', color: '#8FB393' },
  accent: { background: 'rgba(227,214,196,.16)', color: '#E3D6C4' },
  neutral: { background: 'rgba(255,255,255,.06)', color: '#8A847C' },
};

export default function OnboardingPage() {
  const navigate = useNavigate();
  const { loggedIn } = useAuthState();

  const [step, setStep] = useState(0);
  const [education, setEducation] = useState([emptyEducation()]);
  const [educationErrors, setEducationErrors] = useState([{}]);
  const [skills, setSkills] = useState([]);
  const [skillInput, setSkillInput] = useState('');
  const [suggestions, setSuggestions] = useState(null);
  const [basic, setBasic] = useState({
    phone: '', city: '', state: '', country: '', bio: '',
    linkedinUrl: '', githubUrl: '', portfolioUrl: '', isPublic: true,
  });
  const [profile, setProfile] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (!loggedIn) return;
    getSkillSuggestions()
      .then((list) => setSuggestions(Array.isArray(list) && list.length ? list : FALLBACK_SKILLS))
      .catch(() => setSuggestions(FALLBACK_SKILLS));
    getMyProfileWithRetry().then(setProfile).catch(() => setProfile(null));
  }, [loggedIn]);

  const addEducationRow = () => {
    if (education.length >= MAX_EDUCATION) return;
    setEducation([...education, emptyEducation()]);
    setEducationErrors([...educationErrors, {}]);
  };

  const removeEducationRow = (i) => {
    setEducation(education.filter((_, idx) => idx !== i));
    setEducationErrors(educationErrors.filter((_, idx) => idx !== i));
  };

  const updateEducationField = (i, field, value) => {
    setEducation(education.map((e, idx) => (idx === i ? { ...e, [field]: value } : e)));
  };

  const addSkillEntry = (name, isCustom) => {
    const trimmed = (name || '').trim();
    if (!trimmed) return;
    if (skills.length >= MAX_SKILLS) return;
    if (skills.some((s) => s.name.toLowerCase() === trimmed.toLowerCase())) {
      setSkillInput('');
      return;
    }
    setSkills([...skills, { name: trimmed, level: 'INTERMEDIATE', isCustom: !!isCustom }]);
    setSkillInput('');
  };

  const removeSkillEntry = (name) => setSkills(skills.filter((s) => s.name !== name));
  const setSkillLevel = (name, level) => setSkills(skills.map((s) => (s.name === name ? { ...s, level } : s)));
  const updateBasicField = (field, value) => setBasic({ ...basic, [field]: value });

  const validateEducation = () => {
    const errors = education.map((e) => (e.institution.trim() ? {} : { institution: 'Required' }));
    setEducationErrors(errors);
    return errors.every((e) => Object.keys(e).length === 0);
  };

  const submitEducation = async () => {
    const toSend = education.filter((e) => e.institution.trim());
    for (const e of toSend) {
      // eslint-disable-next-line no-await-in-loop
      await addEducation({
        institution: e.institution,
        degree: e.degree || null,
        fieldOfStudy: e.fieldOfStudy || null,
        startYear: null,
        endYear: e.graduationYear ? Number(e.graduationYear) : null,
        grade: e.grade || null,
      });
    }
  };

  const submitSkills = async () => {
    for (const s of skills) {
      const isCustom = suggestions ? !suggestions.includes(s.name) : s.isCustom;
      // eslint-disable-next-line no-await-in-loop
      await addSkill({ skillName: s.name, proficiencyLevel: s.level, isCustom });
    }
  };

  const submitBasicInfo = async () => {
    await updateMyProfile({
      firstName: profile?.firstName || '',
      lastName: profile?.lastName || '',
      phone: basic.phone || null,
      bio: basic.bio || null,
      city: basic.city || null,
      state: basic.state || null,
      country: basic.country || null,
      linkedinUrl: basic.linkedinUrl || null,
      githubUrl: basic.githubUrl || null,
      portfolioUrl: basic.portfolioUrl || null,
      isPublic: basic.isPublic,
    });
  };

  const onContinue = async () => {
    setErrorMessage(null);
    if (step === 0 && !validateEducation()) return;

    setIsSubmitting(true);
    try {
      if (step === 0) await submitEducation();
      if (step === 1) await submitSkills();
      if (step === 2) await submitBasicInfo();
      setStep((s) => Math.min(3, s + 1));
    } catch (e) {
      setErrorMessage(e.message || 'Something went wrong. Try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const onSkip = () => {
    setErrorMessage(null);
    setStep((s) => Math.min(3, s + 1));
  };

  const filteredSuggestions = useMemo(() => {
    const list = suggestions || [];
    const query = skillInput.trim().toLowerCase();
    if (!query) return [];
    const already = new Set(skills.map((sk) => sk.name.toLowerCase()));
    return list.filter((sg) => sg.toLowerCase().includes(query) && !already.has(sg.toLowerCase())).slice(0, 6);
  }, [suggestions, skillInput, skills]);

  const alreadySelected = useMemo(() => new Set(skills.map((sk) => sk.name.toLowerCase())), [skills]);

  const firstEd = education[0] || emptyEducation();
  const educationDone = step > 0 && !!firstEd.institution.trim();
  const educationSummary = firstEd.institution.trim()
    ? [firstEd.degree, firstEd.institution].filter(Boolean).join(' · ')
    : 'Not added yet';
  const skillsDone = step > 1 && skills.length > 0;
  const skillsSummary = skills.length > 0 ? `${skills.length} skill${skills.length === 1 ? '' : 's'} selected` : 'Not added yet';
  const basicHasAny = Object.entries(basic).some(([k, v]) => k !== 'isPublic' && v);
  const basicSummary = basicHasAny ? ([basic.city, basic.country].filter(Boolean).join(', ') || 'In progress') : 'Not added yet';
  const doneCount = [educationDone, skillsDone, step > 2 && basicHasAny].filter(Boolean).length;
  const completionPct = Math.round((doneCount / 4) * 100);
  const completionGrade = completionPct >= 80 ? 'A' : completionPct >= 60 ? 'B' : completionPct >= 40 ? 'C' : completionPct >= 20 ? 'D' : 'F';
  const prsPreview = completionPct >= 60 ? `${completionPct} · Looking good` : `${completionPct} · Just getting started`;

  const checklist = [
    { key: 'education', icon: 'graduation-cap', label: 'Education', summary: educationSummary, done: educationDone },
    { key: 'skills', icon: 'sliders-horizontal', label: 'Skills', summary: skillsSummary, done: skillsDone },
    { key: 'basic', icon: 'user', label: 'Basic information', summary: basicSummary, done: step > 2 || (step === 2 && basicHasAny) },
    { key: 'assessment', icon: 'chart-no-axes-column', label: 'Assessment', summary: 'Not started', done: false },
  ].map((item, idx) => {
    const isCurrent = idx === step;
    const tone = item.done ? 'success' : isCurrent ? 'accent' : 'neutral';
    return {
      ...item,
      tone,
      status: item.done ? 'Done' : isCurrent ? 'Current' : 'Upcoming',
      iconBg: item.done ? '#4F6B52' : isCurrent ? '#6E6A64' : '#3A3835',
    };
  });

  if (!loggedIn) {
    navigate('/login');
    return null;
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0,0.86fr) minmax(0,1.14fr)', minHeight: '100vh', width: '100%', background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <div style={{ position: 'relative', overflowY: 'auto', background: '#1B1A18', display: 'flex', flexDirection: 'column', padding: '44px 48px 40px', gap: 36 }}>
        <div style={{ alignSelf: 'flex-start' }}>
          <Logo size={34} tone="inverse" />
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '8px 0 4px' }}>
          <span style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: '#FCFBF9' }}>Your profile summary</span>
          <div style={{ filter: 'brightness(0) invert(1)' }}>
            <ScoreRing value={completionPct} grade={completionGrade} size="lg" label="Complete" />
          </div>
          <span style={{ fontSize: 12, color: '#A69E94', textAlign: 'center' }}>{SIDE_COPY[step]}</span>
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: '#3A3835', borderTop: '1px solid #3A3835', borderBottom: '1px solid #3A3835' }}>
          {checklist.map((item) => (
            <div key={item.key} style={{ background: '#1B1A18', padding: '16px 4px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <span style={{ width: 36, height: 36, flex: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center', background: item.iconBg, borderRadius: '50%' }}>
                <Icon name={item.icon} size={16} style={{ color: '#FCFBF9' }} />
              </span>
              <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 2 }}>
                <span style={{ fontSize: 14, color: '#FCFBF9' }}>{item.label}</span>
                <span style={{ fontSize: 12, color: '#8A847C', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.summary}</span>
              </div>
              <span
                style={{
                  display: 'inline-flex', alignItems: 'center', gap: 4, padding: '4px 10px', fontSize: 11,
                  fontWeight: 500, letterSpacing: '.08em', textTransform: 'uppercase', borderRadius: 'var(--radius-pill)',
                  ...BADGE_TONE_STYLE[item.tone],
                }}
              >
                {item.done && <Icon name="check" size={11} style={{ color: BADGE_TONE_STYLE[item.tone].color }} />}
                {item.status}
              </span>
            </div>
          ))}
        </div>

        <div style={{ background: 'rgba(166,158,148,.14)', border: '1px solid rgba(166,158,148,.3)', padding: '18px 18px', display: 'flex', flexDirection: 'column', gap: 6 }}>
          <span style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: '#A69E94' }}>Placement readiness preview</span>
          <span style={{ fontFamily: 'var(--font-display)', fontSize: 26, color: '#FCFBF9' }}>{prsPreview}</span>
          <p style={{ fontSize: 12, lineHeight: 1.6, color: '#8A847C', margin: 0 }}>This improves as you complete your profile, roadmap and assessments.</p>
        </div>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', padding: '0 56px', overflowY: 'auto' }}>
        <header style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 16, padding: '36px 0 20px' }}>
          <span className="cb-num" style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-300)' }}>
            Step {step + 1} of 4
          </span>
        </header>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,minmax(0,1fr))', gap: 20 }}>
          {STEP_LABELS.map((label, i) => (
            <button
              key={label}
              type="button"
              onClick={i <= step ? () => { setStep(i); setErrorMessage(null); } : undefined}
              disabled={i > step}
              style={{
                display: 'flex', flexDirection: 'column', gap: 8, paddingBottom: 11,
                border: 0, borderBottom: `2px solid ${i === step ? 'var(--ink-900)' : 'transparent'}`,
                background: 'transparent', textAlign: 'left',
                cursor: i <= step && i !== step ? 'pointer' : i === step ? 'default' : 'not-allowed',
                font: 'inherit',
              }}
            >
              <span style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: i <= step ? 'var(--ink-900)' : 'var(--ink-300)' }}>{label}</span>
            </button>
          ))}
        </div>
        <div style={{ height: 1, background: 'var(--line-hairline)', marginBottom: 36 }} />

        <main style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 22, paddingBottom: 48, maxWidth: 620, width: '100%' }}>
          <div>
            <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.12, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>{HEADINGS[step]}</h1>
            <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-600)', margin: '12px 0 0', maxWidth: 460 }}>{SUBHEADINGS[step]}</p>
          </div>

          {errorMessage && <Alert tone="danger" title="Something went wrong" message={errorMessage} />}

          {step === 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {education.map((e, i) => (
                // eslint-disable-next-line react/no-array-index-key
                <section key={i} style={{ background: 'var(--bone-50)', border: 'var(--border-hairline)', padding: '26px 26px', display: 'flex', flexDirection: 'column', gap: 16 }}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>
                      Qualification {String(i + 1).padStart(2, '0')}
                    </span>
                    {education.length > 1 && (
                      <button type="button" onClick={() => removeEducationRow(i)} style={{ border: 0, background: 'transparent', fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--ink-400)', cursor: 'pointer' }}>
                        Remove
                      </button>
                    )}
                  </div>
                  <Field label="College or university" error={educationErrors[i]?.institution}>
                    <Input placeholder="RV College of Engineering" value={e.institution} onChange={(ev) => updateEducationField(i, 'institution', ev.target.value)} />
                  </Field>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 16 }}>
                    <Field label="Degree">
                      <Input placeholder="B.E. Computer Science" value={e.degree} onChange={(ev) => updateEducationField(i, 'degree', ev.target.value)} />
                    </Field>
                    <Field label="Field of study">
                      <Input placeholder="Computer Science" value={e.fieldOfStudy} onChange={(ev) => updateEducationField(i, 'fieldOfStudy', ev.target.value)} />
                    </Field>
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 16 }}>
                    <Field label="Graduation year">
                      <Input type="number" placeholder="2026" value={e.graduationYear} onChange={(ev) => updateEducationField(i, 'graduationYear', ev.target.value)} />
                    </Field>
                    <Field label="Grade / CGPA" hint="Optional">
                      <Input placeholder="8.4" value={e.grade} onChange={(ev) => updateEducationField(i, 'grade', ev.target.value)} />
                    </Field>
                  </div>
                </section>
              ))}
              {education.length < MAX_EDUCATION && (
                <Button size="sm" variant="secondary" iconAfter="plus" onClick={addEducationRow} style={{ width: '100%', justifyContent: 'center' }}>
                  Add another qualification
                </Button>
              )}
            </div>
          )}

          {step === 1 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              <div style={{ position: 'relative' }}>
                <Field label="Add a skill">
                  <Input placeholder="Start typing — Python, SQL, Figma…" value={skillInput} onChange={(ev) => setSkillInput(ev.target.value)} />
                </Field>
                {filteredSuggestions.length > 0 && (
                  <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: 'var(--bone-50)', border: 'var(--border-hairline)', borderTop: 0, zIndex: 5, maxHeight: 220, overflowY: 'auto' }}>
                    {filteredSuggestions.map((label) => (
                      <div
                        key={label}
                        onClick={() => addSkillEntry(label, false)}
                        style={{ padding: '9px 14px', fontSize: 13, color: 'var(--ink-800)', cursor: 'pointer' }}
                      >
                        {label}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {(suggestions || []).length > 0 && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                  {suggestions.map((name) => {
                    const selected = alreadySelected.has(name.toLowerCase());
                    return (
                      <button
                        key={name}
                        type="button"
                        onClick={selected ? () => removeSkillEntry(name) : () => addSkillEntry(name, false)}
                        style={{
                          padding: '8px 16px', border: `1px solid ${selected ? 'var(--ink-900)' : 'var(--bone-400)'}`,
                          background: selected ? 'var(--ink-900)' : 'transparent',
                          color: selected ? 'var(--bone-50)' : 'var(--ink-800)',
                          font: 'inherit', fontSize: 13, cursor: 'pointer', borderRadius: 'var(--radius-pill)',
                        }}
                      >
                        {name}
                      </button>
                    );
                  })}
                </div>
              )}

              {skills.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: 'var(--border-hairline)' }}>
                  {skills.map((skill) => (
                    <div key={skill.name} style={{ background: 'var(--bone-50)', padding: '14px 18px', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap' }}>
                      <Tag onRemove={() => removeSkillEntry(skill.name)}>{skill.name}</Tag>
                      <Select options={LEVELS} value={skill.level} onChange={(ev) => setSkillLevel(skill.name, ev.target.value)} style={{ width: 150 }} />
                    </div>
                  ))}
                </div>
              )}

              {skills.length < MAX_SKILLS && (
                <Button size="sm" variant="secondary" iconAfter="plus" disabled={!skillInput.trim()} onClick={() => addSkillEntry(skillInput, !(suggestions || []).some((x) => x.toLowerCase() === skillInput.trim().toLowerCase()))} style={{ alignSelf: 'flex-start' }}>
                  Add a skill
                </Button>
              )}
              <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-300)' }}>{skills.length} of {MAX_SKILLS} skills added</span>
            </div>
          )}

          {step === 2 && (
            <section style={{ background: 'var(--bone-50)', border: 'var(--border-hairline)', padding: '26px 26px', display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 16 }}>
                <Field label="Full name">
                  <Input value={[profile?.firstName, profile?.lastName].filter(Boolean).join(' ') || '—'} onChange={() => {}} />
                </Field>
                <Field label="Email" hint="From registration">
                  <Input value={profile?.email || '—'} onChange={() => {}} />
                </Field>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 16 }}>
                <Field label="Phone number">
                  <Input placeholder="+91 90000 00000" value={basic.phone} onChange={(ev) => updateBasicField('phone', ev.target.value)} />
                </Field>
                <Field label="City">
                  <Input placeholder="Bengaluru" value={basic.city} onChange={(ev) => updateBasicField('city', ev.target.value)} />
                </Field>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 16 }}>
                <Field label="State">
                  <Input placeholder="Karnataka" value={basic.state} onChange={(ev) => updateBasicField('state', ev.target.value)} />
                </Field>
                <Field label="Country">
                  <Input placeholder="India" value={basic.country} onChange={(ev) => updateBasicField('country', ev.target.value)} />
                </Field>
              </div>
              <Field label="Bio" hint={`${(basic.bio || '').length} / 2000 characters max`}>
                <Textarea rows={4} placeholder="What you're working toward, in your own words." value={basic.bio} onChange={(ev) => updateBasicField('bio', ev.target.value.slice(0, 2000))} />
              </Field>
              <Field label="LinkedIn URL">
                <Input placeholder="https://linkedin.com/in/you" value={basic.linkedinUrl} onChange={(ev) => updateBasicField('linkedinUrl', ev.target.value)} />
              </Field>
              <Field label="GitHub URL">
                <Input placeholder="https://github.com/you" value={basic.githubUrl} onChange={(ev) => updateBasicField('githubUrl', ev.target.value)} />
              </Field>
              <Field label="Portfolio URL">
                <Input placeholder="https://you.dev" value={basic.portfolioUrl} onChange={(ev) => updateBasicField('portfolioUrl', ev.target.value)} />
              </Field>
              <div style={{ height: 1, background: 'var(--line-hairline)', margin: '4px 0' }} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <Switch label="Visible to recruiters" checked={basic.isPublic} onChange={(val) => updateBasicField('isPublic', val)} />
                <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Recruiters can only see public profiles in candidate search.</span>
              </div>
            </section>
          )}

          {step === 3 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 22 }}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', border: 'var(--border-hairline)' }}>
                <StatTile value="20" label="Questions" />
                <StatTile value="18 min" label="Time" />
                <StatTile value="Unlimited" label="Attempts" />
              </div>
              <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--ink-400)', fontStyle: 'italic', margin: 0 }}>
                Progress is saved as you go. Leaving mid-way keeps the attempt open; it is only scored once you submit.
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center', paddingTop: 8 }}>
                <Button size="lg" iconAfter="arrow-right" to="/assessment" style={{ width: '100%', justifyContent: 'center' }}>Start my assessment</Button>
                <Button size="lg" variant="secondary" iconAfter="arrow-right" to="/dashboard" style={{ width: '100%', justifyContent: 'center' }}>Do this later</Button>
              </div>
            </div>
          )}
        </main>

        {step < 3 && (
          <footer style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, padding: '0 0 48px' }}>
            <button
              type="button"
              onClick={onSkip}
              style={{ fontFamily: 'var(--font-sans)', fontSize: 13, color: 'var(--ink-300)', background: 'transparent', border: 0, borderBottom: '1px solid transparent', cursor: 'pointer', padding: '8px 0' }}
            >
              Skip for now
            </button>
            <Button size="lg" iconAfter={isSubmitting ? undefined : 'arrow-right'} disabled={isSubmitting} onClick={onContinue}>
              {isSubmitting ? 'Saving…' : 'Continue'}
            </Button>
          </footer>
        )}
      </div>
    </div>
  );
}
