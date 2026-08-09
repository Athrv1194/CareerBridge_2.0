import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Alert, Badge, Button, Icon, Logo, MatchScore, ProgressMeter, Skeleton, StatTile,
} from '../../components/ui';
import { startAttempt, submitAttempt } from '../../api/assessmentApi';
import './assessment.css';

const SECTIONS = ['APTITUDE', 'DOMAIN_KNOWLEDGE', 'SOFT_SKILLS'];
const ANALYZING_DELAY_MS = 1400;

const ORBIT_ICONS = [
  { name: 'chart-no-axes-column', left: 164, top: 90 },
  { name: 'briefcase', left: 127, top: 154 },
  { name: 'route', left: 53, top: 154 },
  { name: 'graduation-cap', left: 16, top: 90 },
  { name: 'sliders-horizontal', left: 53, top: 26 },
  { name: 'award', left: 127, top: 26 },
];

function OrbitGraphic() {
  return (
    <div style={{ width: 220, height: 220, position: 'relative', margin: '0 auto' }}>
      <div style={{ position: 'absolute', inset: -10, borderRadius: '50%', border: '1px dashed var(--taupe-700)', opacity: 0.5 }} />
      <div style={{ position: 'absolute', inset: -30, borderRadius: '50%', background: 'radial-gradient(circle, rgba(199,192,182,.28), transparent 68%)', filter: 'blur(2px)' }} />
      <div className="cb-orbit-spin" style={{ position: 'absolute', inset: 0 }}>
        {ORBIT_ICONS.map((o) => (
          <div key={o.name} style={{ position: 'absolute', left: o.left, top: o.top, width: 40, height: 40 }}>
            <div
              className="cb-orbit-counter"
              style={{
                width: '100%', height: '100%', borderRadius: '50%',
                background: 'linear-gradient(135deg, var(--taupe-600), var(--ink-800))',
                border: '1px solid var(--taupe-500)', boxShadow: '0 0 14px rgba(199,192,182,.3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              <Icon name={o.name} size={17} style={{ color: 'var(--bone-50)' }} />
            </div>
          </div>
        ))}
      </div>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <div
          className="cb-orbit-pulse"
          style={{
            width: 52, height: 52, borderRadius: '50%',
            background: 'radial-gradient(circle at 35% 30%, var(--taupe-500), var(--taupe-700))',
            boxShadow: '0 0 24px rgba(199,192,182,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <Icon name="sparkles" size={18} style={{ color: 'var(--bone-50)' }} />
        </div>
      </div>
    </div>
  );
}

const OPTION_TILE_BASE = {
  display: 'flex', alignItems: 'center', gap: 14, padding: '17px 20px', width: '100%',
  textAlign: 'left', border: 0, cursor: 'pointer', font: 'inherit',
};

export default function AssessmentPage() {
  const navigate = useNavigate();

  const [stage, setStage] = useState('intro');
  const [isBeginning, setIsBeginning] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [beginError, setBeginError] = useState(null);
  const [submitError, setSubmitError] = useState(null);
  const [attemptId, setAttemptId] = useState(null);
  const [categoryName, setCategoryName] = useState('');
  const [sectionIndex, setSectionIndex] = useState(0);
  const [questions, setQuestions] = useState([]);
  const [index, setIndex] = useState(0);
  const [answers, setAnswers] = useState({});
  const [result, setResult] = useState(null);
  const [animatedPct, setAnimatedPct] = useState(null);
  const [typedPath, setTypedPath] = useState('');
  const [isMobile, setIsMobile] = useState(false);

  const pctRafRef = useRef(null);
  const typeTimerRef = useRef(null);
  const analyzeTimerRef = useRef(null);

  useEffect(() => () => {
    cancelAnimationFrame(pctRafRef.current);
    clearInterval(typeTimerRef.current);
    clearTimeout(analyzeTimerRef.current);
  }, []);

  // Same breakpoint and matchMedia pattern as HomePage -- below this width the fixed 320px sidebar
  // column stops fitting alongside the question/intro content, so it needs to stack instead.
  useEffect(() => {
    const mq = window.matchMedia('(max-width: 860px)');
    const update = () => setIsMobile(mq.matches);
    update();
    mq.addEventListener('change', update);
    return () => mq.removeEventListener('change', update);
  }, []);

  const startResultAnimations = useCallback((finalResult) => {
    const targetPct = Math.round(finalResult.categoryScorePercentage || 0);
    const path = finalResult.topCareerPath || '';
    setAnimatedPct(0);
    setTypedPath('');
    cancelAnimationFrame(pctRafRef.current);
    const start = performance.now();
    const dur = 1100;
    const ease = (t) => 1 - (1 - t) ** 3;
    const stepPct = () => {
      const t = Math.min(1, (performance.now() - start) / dur);
      setAnimatedPct(Math.round(ease(t) * targetPct));
      if (t < 1) pctRafRef.current = requestAnimationFrame(stepPct);
    };
    pctRafRef.current = requestAnimationFrame(stepPct);

    clearInterval(typeTimerRef.current);
    let i = 0;
    typeTimerRef.current = setInterval(() => {
      i += 1;
      setTypedPath(path.slice(0, i));
      if (i >= path.length) clearInterval(typeTimerRef.current);
    }, 45);
  }, []);

  const startSection = useCallback(async (nextSectionIndex) => {
    try {
      const data = await startAttempt(SECTIONS[nextSectionIndex]);
      setIsBeginning(false);
      setStage('question');
      setSectionIndex(nextSectionIndex);
      setAttemptId(data.attemptId);
      setCategoryName(data.categoryName);
      setQuestions(data.questions || []);
      setIndex(0);
      setAnswers({});
    } catch (e) {
      setIsBeginning(false);
      setBeginError(e.message);
    }
  }, []);

  const begin = useCallback(() => {
    setIsBeginning(true);
    setBeginError(null);
    startSection(0);
  }, [startSection]);

  const selectOption = useCallback((questionId, optionId) => {
    setAnswers((prev) => ({ ...prev, [questionId]: optionId }));
  }, []);

  const back = useCallback(() => {
    setIndex((i) => Math.max(0, i - 1));
  }, []);

  // The Soft Skills (final section) response IS already the true 3-section blend -- assessment-service
  // computes it once and uses it for both the recommendation email and this response, so there is
  // nothing left to re-derive here. Re-averaging on the client used to produce a different number
  // than the career list underneath it (the client only ever averaged the headline percentage, never
  // the per-career scores), which is the bug this replaced.
  const finishSubmit = useCallback((sectionResult) => {
    const isLast = sectionIndex >= SECTIONS.length - 1;
    setIsSubmitting(false);
    setStage('analyzing');
    setResult(sectionResult);
    clearTimeout(analyzeTimerRef.current);
    analyzeTimerRef.current = setTimeout(() => {
      if (isLast) {
        setStage('result');
        startResultAnimations(sectionResult);
      } else {
        startSection(sectionIndex + 1);
      }
    }, ANALYZING_DELAY_MS);
  }, [sectionIndex, startSection, startResultAnimations]);

  const submit = useCallback(async () => {
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      const answerList = questions
        .map((q) => ({ questionId: q.questionId, selectedOptionId: answers[q.questionId] }))
        .filter((a) => a.selectedOptionId != null);
      const res = await submitAttempt(attemptId, answerList);
      finishSubmit(res);
    } catch {
      setIsSubmitting(false);
      setSubmitError("We couldn't reach the assessment service. Your answers are saved. Try submitting again.");
    }
  }, [questions, answers, attemptId, finishSubmit]);

  const next = useCallback(() => {
    if (index < questions.length - 1) {
      setIndex((i) => i + 1);
    } else {
      submit();
    }
  }, [index, questions.length, submit]);

  const retake = useCallback(() => {
    cancelAnimationFrame(pctRafRef.current);
    clearInterval(typeTimerRef.current);
    clearTimeout(analyzeTimerRef.current);
    setStage('intro');
    setIsBeginning(false);
    setIsSubmitting(false);
    setBeginError(null);
    setSubmitError(null);
    setAttemptId(null);
    setCategoryName('');
    setSectionIndex(0);
    setQuestions([]);
    setIndex(0);
    setAnswers({});
    setResult(null);
    setAnimatedPct(null);
    setTypedPath('');
  }, []);

  const currentQuestion = questions[index];
  const picked = currentQuestion ? answers[currentQuestion.questionId] : undefined;
  const answeredCount = questions.filter((q) => answers[q.questionId] !== undefined).length;
  const isLastQuestion = questions.length > 0 && index === questions.length - 1;

  const careerScores = result?.allCareerScores || {};
  const topCareers = Object.entries(careerScores)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([name, score], i) => ({
      name,
      rank: String(i + 1).padStart(2, '0'),
      score: Math.round(score),
      size: i === 0 ? 'lg' : 'sm',
      delayMs: 250 + i * 160,
    }));
  const pct = Math.round(result?.categoryScorePercentage || 0);
  const scoreColor = pct >= 85 ? 'var(--status-success)' : pct >= 65 ? 'var(--taupe-700)' : 'var(--text-muted)';
  const isTyping = stage === 'result' && typedPath.length < (result?.topCareerPath || '').length;
  const displayedPct = animatedPct != null ? animatedPct : pct;

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--text-body)', fontFamily: 'var(--font-sans)', display: 'flex', flexDirection: 'column' }}>

      <header style={{ borderBottom: '1px solid var(--line-hairline)', flex: '0 0 auto' }}>
        <div style={{ maxWidth: 1160, margin: '0 auto', padding: '0 32px', height: 64, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 24 }}>
          <Logo size={36} />
          {(stage === 'intro' || stage === 'question') && (
            <Link to="/" className="cb-ghost" style={{ color: 'var(--ink-600)', fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase' }}>
              Save &amp; exit
            </Link>
          )}
        </div>
      </header>

      {stage === 'question' && (
        <div style={{ flex: '0 0 auto', borderBottom: '1px solid var(--line-hairline)' }}>
          <div style={{ maxWidth: 1160, margin: '0 auto', padding: '16px 32px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
              <span className="cb-eyebrow cb-num" style={{ whiteSpace: 'nowrap' }}>
                {categoryName} &middot; Question {index + 1} of {questions.length || 1}
              </span>
              <div style={{ flex: 1 }}>
                <ProgressMeter value={index + 1} max={questions.length || 1} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {questions.map((q, i) => {
                const answered = answers[q.questionId] !== undefined;
                const current = i === index;
                return (
                  <span
                    key={q.questionId}
                    className="cb-num"
                    style={{
                      fontSize: 11, padding: '4px 8px',
                      border: `1px solid ${current ? 'var(--ink-900)' : answered ? 'var(--line-strong)' : 'var(--line-hairline)'}`,
                      color: current ? 'var(--ink-900)' : answered ? 'var(--text-secondary)' : 'var(--text-muted)',
                      background: current ? 'var(--bone-200)' : 'transparent',
                    }}
                  >
                    {String(i + 1).padStart(2, '0')}
                  </span>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {stage === 'intro' && (
        <main style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 48, padding: '56px 32px' }}>
          <div style={{ maxWidth: 1160, width: '100%', display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '320px minmax(0,1fr)', gap: isMobile ? 32 : 64, alignItems: isMobile ? 'stretch' : 'center' }}>

            <div style={{ background: 'var(--ink-900)', padding: '36px 28px', display: 'flex', flexDirection: 'column', gap: 24 }}>
              <OrbitGraphic />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <span className="cb-eyebrow" style={{ color: 'var(--taupe-300)' }}>AI insight</span>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--bone-400)', margin: 0 }}>
                  The engine compares your answers against real hiring signals across three categories to rank the careers that fit you best.
                </p>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {['Aptitude', 'Domain knowledge', 'Soft skills'].map((t) => (
                  <span key={t} style={{ display: 'inline-flex', alignItems: 'center', fontSize: 12, padding: '6px 11px', border: '1px solid var(--taupe-600)', color: 'var(--bone-100)' }}>
                    {t}
                  </span>
                ))}
              </div>
              <div style={{ height: 1, background: 'var(--ink-700)' }} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                <span className="cb-eyebrow" style={{ color: 'var(--taupe-300)' }}>What it reads</span>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {[
                    'Which option you pick under time pressure',
                    'Consistency across similar prompts',
                    'How answers cluster by career path',
                  ].map((line) => (
                    <span key={line} style={{ fontSize: 12, color: 'var(--bone-400)', display: 'flex', gap: 8 }}>
                      <span style={{ color: 'var(--taupe-500)' }}>&mdash;</span>{line}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 22, alignItems: 'flex-start' }}>
              <span className="cb-eyebrow">Before you start</span>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 'var(--text-display-lg)', lineHeight: 'var(--leading-display)', letterSpacing: 'var(--tracking-display)', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Twenty questions, and we can <i>rank</i> your careers.
              </h1>
              <p style={{ fontSize: 14, lineHeight: 'var(--leading-normal)', color: 'var(--text-secondary)', margin: 0, maxWidth: 480 }}>
                Three categories &mdash; aptitude, domain knowledge and soft skills. Answer honestly rather than optimally; the recommendation is only as good as the input.
              </p>
              <div style={{ display: 'grid', gridTemplateColumns: isMobile ? '1fr' : 'repeat(3,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', width: '100%', marginTop: 6 }}>
                <StatTile value="20" label="Questions" />
                <StatTile value="18 min" label="Time" />
                <StatTile value="Unlimited" label="Attempts" />
              </div>
              <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--text-muted)', fontStyle: 'italic', margin: '2px 0 0' }}>
                Progress is saved as you go. Leaving mid-way keeps the attempt open; it is only scored once you submit.
              </p>
              {beginError && (
                <Alert
                  tone="danger"
                  title="Couldn't start the assessment"
                  message={(
                    <>
                      <span>{beginError}</span>
                      <div style={{ marginTop: 8 }}>
                        <Button size="sm" variant="secondary" onClick={begin}>Try again</Button>
                      </div>
                    </>
                  )}
                />
              )}
              <Button size="lg" iconAfter="arrow-right" disabled={isBeginning} onClick={begin} style={{ marginTop: 8 }}>
                {isBeginning ? 'Starting…' : 'Begin assessment'}
              </Button>
            </div>
          </div>

          <div style={{ maxWidth: 1160, width: '100%', display: 'grid', gridTemplateColumns: isMobile ? '1fr' : 'repeat(3,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
            {[
              { icon: 'chart-no-axes-column', title: 'Aptitude', body: 'Logic, numbers and pattern recognition under time pressure.' },
              { icon: 'file-text', title: 'Domain knowledge', body: "What you actually know about the field you're pointed at." },
              { icon: 'users', title: 'Soft skills', body: 'How you handle ambiguity, disagreement and stakeholders.' },
            ].map((c) => (
              <div key={c.title} style={{ background: 'var(--surface-card)', padding: '24px 26px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <Icon name={c.icon} size={20} style={{ color: 'var(--taupe-700)' }} />
                <span style={{ fontSize: 15, color: 'var(--ink-900)' }}>{c.title}</span>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--text-secondary)', margin: 0 }}>{c.body}</p>
              </div>
            ))}
          </div>
        </main>
      )}

      {stage === 'question' && (
        <main style={{ flex: 1, padding: '52px 32px' }}>
          <div style={{ maxWidth: 1160, margin: '0 auto', display: 'grid', gridTemplateColumns: isMobile ? '1fr' : '320px minmax(0,1fr)', gap: isMobile ? 32 : 64, alignItems: 'start' }}>

            <div style={{ background: 'var(--ink-900)', padding: '36px 28px', display: 'flex', flexDirection: 'column', gap: 24 }}>
              <OrbitGraphic />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <span className="cb-eyebrow" style={{ color: 'var(--taupe-300)' }}>AI insight</span>
                <p style={{ fontSize: 13, lineHeight: 1.6, color: 'var(--bone-400)', margin: 0 }}>
                  Every answer sharpens the match. The engine weighs it against real hiring signals &mdash; it never shows you which option scores highest.
                </p>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
                  <span style={{ color: 'var(--taupe-300)' }}>Signals analyzed</span>
                  <span style={{ color: 'var(--bone-100)' }}>{answeredCount} of {questions.length || 1}</span>
                </div>
                <ProgressMeter value={answeredCount} max={questions.length || 1} tone="accent" />
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 26 }}>
              {isSubmitting ? (
                <Skeleton height={240} />
              ) : (
                <>
                  {submitError && (
                    <Alert
                      tone="danger"
                      title="Submission failed"
                      message={(
                        <>
                          <span>{submitError}</span>
                          <div style={{ marginTop: 8 }}>
                            <Button size="sm" variant="secondary" onClick={submit}>Try again</Button>
                          </div>
                        </>
                      )}
                    />
                  )}
                  <h2 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 'var(--leading-tight)', letterSpacing: 'var(--tracking-display)', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                    {currentQuestion?.questionText}
                  </h2>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                    {(currentQuestion?.options || []).map((opt) => {
                      const selected = picked === opt.optionId;
                      return (
                        <button
                          key={opt.optionId}
                          type="button"
                          onClick={() => selectOption(currentQuestion.questionId, opt.optionId)}
                          style={{
                            ...OPTION_TILE_BASE,
                            background: selected ? 'var(--ink-900)' : 'var(--surface-card)',
                            color: selected ? 'var(--text-inverse)' : 'var(--ink-900)',
                          }}
                        >
                          <span
                            style={{
                              width: 16, height: 16, flex: '0 0 auto', borderRadius: '50%',
                              border: `1px solid ${selected ? 'currentColor' : 'var(--line-strong)'}`,
                              display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                            }}
                          >
                            {selected && <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'currentColor' }} />}
                          </span>
                          <span style={{ fontSize: 14, lineHeight: 1.5 }}>{opt.optionText}</span>
                        </button>
                      );
                    })}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, marginTop: 4 }}>
                    <Button variant="secondary" icon="arrow-left" disabled={index === 0} onClick={back}>Back</Button>
                    <Button iconAfter="arrow-right" disabled={picked === undefined} onClick={next}>
                      {isLastQuestion ? 'Submit answers' : 'Next question'}
                    </Button>
                  </div>
                </>
              )}
            </div>
          </div>
        </main>
      )}

      {stage === 'analyzing' && (
        <main style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '56px 32px' }}>
          <div className="cb-fade" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20 }}>
            <div style={{ width: 110, height: 110, position: 'relative' }}>
              <div className="cb-orbit-spin" style={{ position: 'absolute', inset: 0, borderRadius: '50%', border: '2px dashed var(--taupe-500)', opacity: 0.6 }} />
              <div style={{ position: 'absolute', inset: 22, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <div
                  className="cb-orbit-pulse"
                  style={{
                    width: 66, height: 66, borderRadius: '50%',
                    background: 'radial-gradient(circle at 35% 30%, var(--taupe-500), var(--taupe-700))',
                    boxShadow: '0 0 26px rgba(199,192,182,.5)', display: 'flex', alignItems: 'center', justifyContent: 'center',
                  }}
                >
                  <Icon name="sparkles" size={22} style={{ color: 'var(--bone-50)' }} />
                </div>
              </div>
            </div>
            <span className="cb-eyebrow" style={{ color: 'var(--text-muted)' }}>Scoring your responses</span>
            <p style={{ fontSize: 13, color: 'var(--text-secondary)', margin: 0 }}>Matching your answers against real hiring signals&hellip;</p>
          </div>
        </main>
      )}

      {stage === 'result' && (
        <main style={{ flex: 1, padding: '52px 32px' }}>
          <div style={{ maxWidth: 960, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 36 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'flex-start' }}>
              <span className="cb-eyebrow">Assessment complete &middot; {result?.categoryName}</span>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 'var(--text-display-md)', lineHeight: 'var(--leading-display)', letterSpacing: 'var(--tracking-display)', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Your top match is <i>{typedPath}</i>
                {isTyping && (
                  <span className="cb-blink" style={{ display: 'inline-block', width: 2, height: '0.85em', background: 'var(--taupe-700)', marginLeft: 4, verticalAlign: '-0.1em' }} />
                )}.
              </h1>
            </div>

            <div style={{ display: 'flex', alignItems: 'baseline', gap: 14 }}>
              <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 64, lineHeight: 1, color: scoreColor }}>{displayedPct}%</span>
              <span className="cb-eyebrow" style={{ alignSelf: 'flex-end', marginBottom: 8 }}>Category score</span>
            </div>

            <section>
              <span className="cb-eyebrow">Top career matches</span>
              <hr className="cb-rule" style={{ background: 'var(--line-ink)', margin: '8px 0 4px' }} />
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {topCareers.map((career) => (
                  <div
                    key={career.name}
                    className="cb-rise"
                    style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '16px 4px', borderBottom: '1px solid var(--line-hairline)', animationDelay: `${career.delayMs}ms` }}
                  >
                    <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 20, color: 'var(--text-muted)', width: 30, flex: 'none' }}>{career.rank}</span>
                    <span style={{ flex: 1, fontSize: 15, color: 'var(--ink-900)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{career.name}</span>
                    <MatchScore value={career.score} size={career.size} />
                  </div>
                ))}
              </div>
            </section>

            <div style={{ background: 'var(--surface-sunken)', borderLeft: '2px solid var(--taupe-700)', padding: '20px 22px', display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 10 }}>
              <Badge tone="accent">AI generated</Badge>
              <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-800)', margin: 0 }}>
                Your result has been sent to your recommendations. Building your roadmap now takes one click.
              </p>
            </div>

            <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap' }}>
              <Button size="lg" iconAfter="arrow-right" onClick={() => navigate('/recommendations')}>See my recommendations</Button>
              <Button size="lg" variant="secondary" iconAfter="refresh-cw" onClick={retake}>Retake assessment</Button>
            </div>
          </div>
        </main>
      )}

    </div>
  );
}
