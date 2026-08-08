import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert, Badge, Button, Icon, IconButton, Logo, MatchScore,
  ProgressMeter, ScoreRing, Skeleton, StatTile, Tag,
} from '../../components/ui';
import { getCareerCatalog, getMyRecommendation, getRecommendationHistory } from '../../api/recommendationApi';
import { getMyProfile } from '../../api/studentApi';
import { buildRoadmap } from '../../api/roadmapApi';
import './recommendation.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations', active: true },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

function formatDate(iso) {
  if (!iso) return '';
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

// Shared by both the top-3 cards and the "also ranked" grid -- one build-my-roadmap action per
// career, everywhere a career is shown, not just the #1 match. Idempotent on the backend, so a
// re-click after "Built" just re-confirms rather than erroring.
function RoadmapButton({ careerName, status, onBuild, size = 'sm' }) {
  if (status === 'built') {
    return (
      <Link to="/roadmap" style={{ border: 0 }}>
        <Badge tone="accent">Roadmap built</Badge>
      </Link>
    );
  }
  return (
    <Button
      size={size}
      variant={size === 'sm' ? 'primary' : 'secondary'}
      iconAfter={status === 'loading' ? undefined : 'arrow-right'}
      disabled={status === 'loading'}
      onClick={() => onBuild(careerName)}
    >
      {status === 'loading' ? 'Building…' : 'Build my roadmap'}
    </Button>
  );
}

export default function RecommendationPage() {
  const [navCollapsed, setNavCollapsed] = useState(false);
  const [recommendation, setRecommendation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [studentName, setStudentName] = useState('');
  const [skillsByCareer, setSkillsByCareer] = useState({});
  const [showHistory, setShowHistory] = useState(false);
  const [history, setHistory] = useState(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [typedTop, setTypedTop] = useState('');
  const [roadmapStatus, setRoadmapStatus] = useState({});

  const typeTimerRef = useRef(null);

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([getMyRecommendation(), getMyProfile(), getCareerCatalog()]).then(
      ([recRes, profileRes, catalogRes]) => {
        if (cancelled) return;
        if (recRes.status === 'fulfilled') {
          setRecommendation(recRes.value);
        } else {
          setError(recRes.reason.message);
        }
        if (profileRes.status === 'fulfilled' && profileRes.value) {
          setStudentName(`${profileRes.value.firstName || ''} ${profileRes.value.lastName || ''}`.trim());
        }
        if (catalogRes.status === 'fulfilled') {
          const map = {};
          catalogRes.value.forEach((c) => {
            map[c.name] = (c.requiredSkills || '').split(',').map((s) => s.trim()).filter(Boolean);
          });
          setSkillsByCareer(map);
        }
        setLoading(false);
      },
    );
    return () => { cancelled = true; };
  }, []);

  useEffect(() => {
    if (!recommendation?.topCareerName) return undefined;
    const target = recommendation.topCareerName;
    clearInterval(typeTimerRef.current);
    setTypedTop('');
    let i = 0;
    typeTimerRef.current = setInterval(() => {
      i += 1;
      setTypedTop(target.slice(0, i));
      if (i >= target.length) clearInterval(typeTimerRef.current);
    }, 45);
    return () => clearInterval(typeTimerRef.current);
  }, [recommendation?.topCareerName]);

  const toggleHistory = useCallback(async () => {
    if (!showHistory && history === null) {
      setHistoryLoading(true);
      try {
        setHistory(await getRecommendationHistory());
      } catch {
        setHistory([]);
      } finally {
        setHistoryLoading(false);
      }
    }
    setShowHistory((v) => !v);
  }, [showHistory, history]);

  const handleBuildRoadmap = useCallback(async (careerName) => {
    setRoadmapStatus((s) => ({ ...s, [careerName]: 'loading' }));
    try {
      await buildRoadmap(careerName);
      setRoadmapStatus((s) => ({ ...s, [careerName]: 'built' }));
    } catch {
      setRoadmapStatus((s) => ({ ...s, [careerName]: 'error' }));
    }
  }, []);

  const sidebarWidth = navCollapsed ? '60px' : '300px';

  return (
    <div className="cb-rec-shell" style={{ '--sidebar-w': sidebarWidth }}>

      <aside style={{ borderRight: '1px solid var(--line-hairline)', overflowX: 'hidden', overflowY: 'auto', display: 'flex', flexDirection: 'column', position: 'sticky', top: 0, height: '100vh' }}>
        <div style={{ height: 82, display: 'flex', alignItems: 'center', justifyContent: navCollapsed ? 'center' : 'flex-start', padding: navCollapsed ? '0 8px' : '0 12px', borderBottom: '1px solid var(--line-hairline)', boxSizing: 'border-box', overflow: 'hidden' }}>
          <Logo size={navCollapsed ? 32 : 44} showText={!navCollapsed} />
        </div>
        <div className="cb-sidebar-toggle-row" style={{ display: 'flex', justifyContent: navCollapsed ? 'center' : 'flex-end', padding: '10px 12px 0' }}>
          <IconButton
            icon="chevron-right"
            label={navCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            onClick={() => setNavCollapsed((v) => !v)}
            iconStyle={{ transform: navCollapsed ? 'none' : 'rotate(180deg)', transition: 'transform 200ms ease' }}
          />
        </div>
        <nav style={{ display: 'flex', flexDirection: 'column', padding: '8px 12px 18px', gap: 2 }}>
          {NAV_ITEMS.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              title={item.label}
              style={{
                display: 'flex', alignItems: 'center', gap: 11,
                justifyContent: navCollapsed ? 'center' : 'flex-start',
                padding: navCollapsed ? '10px 0' : '10px 12px', fontSize: 14,
                background: item.active ? 'var(--ink-900)' : 'transparent',
                color: item.active ? 'var(--bone-50)' : 'var(--ink-700)', border: 'none',
              }}
            >
              <Icon name={item.icon} size={16} style={{ color: item.active ? 'var(--bone-50)' : 'var(--ink-700)' }} />
              {!navCollapsed && <span className="cb-sidebar-label">{item.label}</span>}
            </Link>
          ))}
        </nav>
        {!navCollapsed && (
          <div className="cb-sidebar-footer" style={{ marginTop: 'auto' }}>
            <div style={{ padding: 18, borderTop: '1px solid var(--line-hairline)' }}>
              <div style={{ background: 'var(--bone-200)', padding: 16 }}>
                <Icon name="sparkles" size={16} />
                <div style={{ fontSize: 13, color: 'var(--ink-900)', marginTop: 8, lineHeight: 1.4 }}>
                  Roadmap pacing and coach follow-ups need Plus.
                </div>
                <div style={{ fontSize: 12, color: 'var(--ink-500)', marginTop: 4, lineHeight: 1.4 }}>
                  Free covers your top 3 matches. Upgrade for the full roadmap and unlimited coach sessions.
                </div>
                <Link to="/" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 12 }}>
                  <Button variant="primary" size="sm" fullWidth>See plans</Button>
                </Link>
              </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 11, padding: '16px 18px', background: 'var(--surface-card)' }}>
              <div style={{ width: 34, height: 34, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Icon name="user" size={16} />
              </div>
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
                <span style={{ fontSize: 11, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Student</span>
              </div>
            </div>
          </div>
        )}
      </aside>

      <main style={{ minWidth: 0 }}>
        <div className="cb-topbar" style={{ height: 64, borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 32px', position: 'sticky', top: 0, background: 'var(--surface-page)', zIndex: 10 }}>
          <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>
            Recommendations{recommendation ? ` · generated ${formatDate(recommendation.createdAt)}` : ''}
          </span>
          {recommendation && (
            <Button size="sm" variant="secondary" iconAfter="history" onClick={toggleHistory}>
              {showHistory ? 'Back to current' : 'See history'}
            </Button>
          )}
        </div>

        <div className="cb-main-pad" style={{ padding: '34px 32px 64px', maxWidth: 1100, display: 'flex', flexDirection: 'column', gap: 34 }}>
          {loading && <Skeleton height={240} />}

          {!loading && error && <Alert tone="danger" title="Couldn't load your recommendations" message={error} />}

          {!loading && !error && !recommendation && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16, alignItems: 'flex-start' }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                No recommendation yet.
              </h1>
              <p style={{ fontSize: 14, color: 'var(--text-secondary)', margin: 0, maxWidth: 480 }}>
                Recommendations are generated automatically once you finish the 20-question assessment.
              </p>
              <Button size="lg" iconAfter="arrow-right" to="/assessment">Take the assessment</Button>
            </div>
          )}

          {!loading && !error && recommendation && showHistory && (
            <section>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Past recommendations</span>
              <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
              {historyLoading && <Skeleton height={120} />}
              {!historyLoading && history && history.length === 0 && (
                <p style={{ fontSize: 14, color: 'var(--text-secondary)' }}>No earlier recommendations on file.</p>
              )}
              {!historyLoading && history && history.length > 0 && (
                <div style={{ display: 'flex', flexDirection: 'column', marginTop: 12 }}>
                  {history.map((r) => (
                    <div key={r.recommendationId} style={{ display: 'flex', alignItems: 'center', gap: 16, padding: '14px 4px', borderBottom: '1px solid var(--line-hairline)' }}>
                      <span style={{ flex: 1, fontSize: 14, color: 'var(--ink-900)' }}>{r.topCareerName}</span>
                      <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>{formatDate(r.createdAt)}</span>
                      <MatchScore value={Math.round(r.overallMatchPercentage)} size="sm" />
                      {!r.isActive && <Badge tone="default">Superseded</Badge>}
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}

          {!loading && !error && recommendation && !showHistory && (
            <>
              <div>
                <h1 className="cb-hero-title" style={{ fontFamily: 'var(--font-display)', fontSize: 44, lineHeight: 1.05, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                  Three careers fit you. <i>{typedTop}</i> fits best.
                </h1>
                <p style={{ fontSize: 15, lineHeight: 1.65, color: 'var(--text-secondary)', margin: '12px 0 0', maxWidth: 640 }}>
                  Ranked from your latest 20-question assessment. Every percentage is a match against the role's expected profile — not a probability of getting hired.
                </p>
              </div>

              <div className="cb-stat-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)' }}>
                <StatTile value={recommendation.topCareerName} label="Top match" />
                <StatTile value={`${Math.round(recommendation.overallMatchPercentage)}%`} label="Best score" />
                <StatTile value="3" label="Categories assessed" />
                <StatTile value={formatDate(recommendation.createdAt)} label="Generated" />
              </div>

              {recommendation.insight && (
                <div style={{ background: 'var(--bone-300)', border: '1px solid var(--taupe-300)', padding: 28, display: 'flex', flexDirection: 'column', gap: 14 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{ width: 30, height: 30, borderRadius: '50%', background: 'var(--ink-900)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <Icon name="sparkles" size={15} style={{ color: 'var(--bone-50)' }} />
                    </div>
                    <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Coach insight</span>
                  </div>
                  <div className="cb-insight-grid" style={{ display: 'grid', gap: 20 }}>
                    <div>
                      <span style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>Strength</span>
                      <p style={{ fontSize: 14, lineHeight: 1.5, color: 'var(--ink-900)', margin: '6px 0 0' }}>{recommendation.insight.strengthArea}</p>
                    </div>
                    <div>
                      <span style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>To improve</span>
                      <p style={{ fontSize: 14, lineHeight: 1.5, color: 'var(--ink-900)', margin: '6px 0 0' }}>{recommendation.insight.improvementArea}</p>
                    </div>
                    <div>
                      <span style={{ fontSize: 11, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>Next step</span>
                      <p style={{ fontSize: 14, lineHeight: 1.5, color: 'var(--ink-900)', margin: '6px 0 0' }}>{recommendation.insight.actionableAdvice}</p>
                    </div>
                  </div>
                  <span style={{ fontSize: 12, color: 'var(--text-muted)' }}>Generated from your assessment result · not a human review</span>
                </div>
              )}

              <section>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Top recommendations</span>
                <hr style={{ height: 1, background: 'var(--line-ink)', border: 0, margin: '8px 0 0' }} />
                <div style={{ border: '1px solid var(--line-hairline)', borderTop: 0, background: 'var(--surface-card)', marginTop: 22 }}>
                  {recommendation.topRecommendations.map((career, i) => (
                    <article
                      key={career.careerName}
                      className="cb-rec-row"
                      style={{
                        padding: '28px 26px', display: 'grid',
                        gap: 26, alignItems: 'start',
                        borderBottom: i < recommendation.topRecommendations.length - 1 ? '1px solid var(--line-hairline)' : 0,
                      }}
                    >
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                        <span style={{ fontSize: 10, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>Rank</span>
                        <span className="cb-num" style={{ fontFamily: 'var(--font-display)', fontSize: 40, lineHeight: 1, color: 'var(--ink-900)' }}>
                          {String(career.rank).padStart(2, '0')}
                        </span>
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                        <h3 style={{ fontFamily: 'var(--font-display)', fontSize: 28, lineHeight: 1.1, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>
                          {career.careerName}
                        </h3>
                        <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--text-secondary)', margin: 0, maxWidth: 520 }}>
                          {career.reasonText}
                        </p>
                        {(skillsByCareer[career.careerName] || []).length > 0 && (
                          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 4 }}>
                            {skillsByCareer[career.careerName].map((skill) => (
                              <Tag key={skill}>{skill}</Tag>
                            ))}
                          </div>
                        )}
                        <div style={{ marginTop: 6, maxWidth: 320 }}>
                          <ProgressMeter value={career.matchPercentage} max={100} tone="accent" />
                        </div>
                      </div>
                      <div className="cb-rec-side" style={{ display: 'flex', flexDirection: 'column', gap: 14, alignItems: 'flex-end' }}>
                        <MatchScore value={Math.round(career.matchPercentage)} size="lg" />
                        <RoadmapButton
                          careerName={career.careerName}
                          status={roadmapStatus[career.careerName]}
                          onBuild={handleBuildRoadmap}
                        />
                      </div>
                    </article>
                  ))}
                </div>
              </section>

              {recommendation.otherCareers && recommendation.otherCareers.length > 0 && (
                <section>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>
                    Also ranked — worth knowing, not worth leading with
                  </span>
                  <hr style={{ height: 1, background: 'var(--line-hairline)', border: 0, margin: '8px 0 0' }} />
                  <div className="cb-also-grid" style={{ display: 'grid', gap: 1, background: 'var(--line-hairline)', border: '1px solid var(--line-hairline)', borderTop: 0, marginTop: 20 }}>
                    {recommendation.otherCareers.map((career) => (
                      <div key={career.careerName} style={{ background: 'var(--surface-card)', padding: '20px 22px', display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 12 }}>
                        <ScoreRing value={Math.round(career.matchPercentage)} size="sm" />
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
                          <span className="cb-num" style={{ fontSize: 10, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--text-muted)' }}>
                            Rank {String(career.rank).padStart(2, '0')}
                          </span>
                          <span style={{ fontSize: 15, color: 'var(--ink-900)' }}>{career.careerName}</span>
                        </div>
                        <RoadmapButton
                          careerName={career.careerName}
                          status={roadmapStatus[career.careerName]}
                          onBuild={handleBuildRoadmap}
                        />
                      </div>
                    ))}
                  </div>
                </section>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
}
