import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Badge, Button, Icon, IconButton, Logo, revealStyle, Skeleton, Tag } from '../../components/ui';
import { getSessions, getSession, createSession, deleteSession, sendCoachMessage, getMyResources } from '../../api/aiCoachApi';
import { getUnreadCount } from '../../api/notificationApi';
import { getMyProfile, getAvatarBlobUrl } from '../../api/studentApi';
import { clearTokens } from '../../utils/tokenUtils';
import { getNavCollapsed, setNavCollapsed as persistNavCollapsed } from '../../utils/navPrefs';
import { useMaxWidth } from '../../hooks/useBreakpoint';
import './coach.css';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach', active: true },
  { icon: 'users', label: 'Mentors', to: '/mentors' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

const SUGGESTION_CHIPS = [
  'What should I focus on this week?',
  'Where is my score weakest?',
  'How do I explain this project in an interview?',
];

function pad2(n) { return n < 10 ? '0' + n : '' + n; }
function startOfDay(d) { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; }
function diffDays(iso, now) { return Math.round((startOfDay(now).getTime() - startOfDay(new Date(iso)).getTime()) / 86400000); }
function groupLabel(days) { if (days <= 0) return 'Today'; if (days < 7) return 'This week'; return 'Earlier'; }
function fmtSessionDate(iso, now) {
  const days = diffDays(iso, now);
  const d = new Date(iso);
  if (days <= 0) return 'Today';
  if (days < 7) return d.toLocaleDateString('en-US', { weekday: 'short' });
  return d.getDate() + ' ' + d.toLocaleDateString('en-US', { month: 'short' });
}
function fmtClock(iso) {
  const d = new Date(iso);
  let h = d.getHours(); const m = pad2(d.getMinutes()); const ap = h >= 12 ? 'PM' : 'AM';
  h = h % 12; if (h === 0) h = 12;
  return h + ':' + m + ' ' + ap;
}

// The coach's replies are LLM markdown (**bold**, "1. " / "* " lists) -- rendering it as plain
// text left the literal asterisks and list markers on screen. No markdown library for this: the
// coach's own output is the only source, so a tiny inline parser covering just what it actually
// produces is smaller and safer than pulling in a full markdown renderer for one chat bubble.
function renderInline(text, keyPrefix) {
  return text.split(/(\*\*[^*]+\*\*)/g).map((part, i) => (
    part.startsWith('**') && part.endsWith('**') && part.length > 4
      // eslint-disable-next-line react/no-array-index-key
      ? <strong key={`${keyPrefix}-${i}`}>{part.slice(2, -2)}</strong>
      // eslint-disable-next-line react/no-array-index-key
      : <span key={`${keyPrefix}-${i}`}>{part}</span>
  ));
}
function MarkdownLite({ text }) {
  const blocks = text.split(/\n{2,}/);
  return blocks.map((block, bi) => {
    const lines = block.split('\n').filter((l) => l.trim() !== '');
    const isOrdered = lines.length > 0 && lines.every((l) => /^\d+\.\s/.test(l.trim()));
    const isUnordered = lines.length > 0 && lines.every((l) => /^[*-]\s/.test(l.trim()));
    const marginBottom = bi === blocks.length - 1 ? 0 : 10;
    if (isOrdered || isUnordered) {
      const ListTag = isOrdered ? 'ol' : 'ul';
      return (
        // eslint-disable-next-line react/no-array-index-key
        <ListTag key={bi} style={{ margin: `0 0 ${marginBottom}px`, paddingLeft: 20 }}>
          {lines.map((line, li) => (
            // eslint-disable-next-line react/no-array-index-key
            <li key={li} style={{ marginBottom: 4 }}>{renderInline(line.trim().replace(/^(\d+\.|[*-])\s+/, ''), `${bi}-${li}`)}</li>
          ))}
        </ListTag>
      );
    }
    return (
      // eslint-disable-next-line react/no-array-index-key
      <p key={bi} style={{ margin: `0 0 ${marginBottom}px` }}>
        {block.split('\n').map((line, li, arr) => (
          // eslint-disable-next-line react/no-array-index-key
          <span key={li}>{renderInline(line, `${bi}-${li}`)}{li < arr.length - 1 && <br />}</span>
        ))}
      </p>
    );
  });
}

const GROUP_LABELS = ['Today', 'This week', 'Earlier'];

export default function CoachPage() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [studentName, setStudentName] = useState('');
  const [avatarSrc, setAvatarSrc] = useState('');
  const [navCollapsed, setNavCollapsed] = useState(getNavCollapsed);
  const [sessionsCollapsed, setSessionsCollapsed] = useState(false);
  // Below 560px the three rails stop being grid columns and become overlay drawers, so the
  // chat gets the whole viewport instead of a ~360px slice of a sideways-scrolling shell.
  const isPhone = useMaxWidth('phone');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [sessionsOpen, setSessionsOpen] = useState(false);
  const [resourcesOpen, setResourcesOpen] = useState(false);
  const closeAllDrawers = () => { setDrawerOpen(false); setSessionsOpen(false); setResourcesOpen(false); };
  const [resourcesCollapsed, setResourcesCollapsed] = useState(false);
  const railCollapsed = isPhone ? false : navCollapsed;
  const sessionsRailCollapsed = isPhone ? false : sessionsCollapsed;
  const resourcesRailCollapsed = isPhone ? false : resourcesCollapsed;

  const [sessionsLoading, setSessionsLoading] = useState(true);
  const [sessions, setSessions] = useState([]);
  const [sessionsEntered, setSessionsEntered] = useState(false);
  const [activeSessionId, setActiveSessionId] = useState(null);
  const [activeSession, setActiveSession] = useState(null);
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [removingId, setRemovingId] = useState(null);
  const [justCreatedId, setJustCreatedId] = useState(null);
  const [newSessionBusy, setNewSessionBusy] = useState(false);

  const [draftText, setDraftText] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  const [recording, setRecording] = useState(false);
  const [micError, setMicError] = useState('');
  const recognitionRef = useRef(null);
  const draftBeforeRecordingRef = useRef('');

  const [streamingIndex, setStreamingIndex] = useState(null);
  const [streamingWords, setStreamingWords] = useState(0);

  const [robotPhase, setRobotPhase] = useState('idle');
  const [robotExpression, setRobotExpression] = useState('neutral');
  const [armLift, setArmLift] = useState(false);
  const [blinking, setBlinking] = useState(false);
  const [dotActive, setDotActive] = useState(0);

  const [resourcesLoading, setResourcesLoading] = useState(true);
  const [resourceGroups, setResourceGroups] = useState([]);
  const [resourcesEntered, setResourcesEntered] = useState(false);
  const [welcomeIn, setWelcomeIn] = useState(false);

  const reducedMotion = useMemo(
    () => typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    []
  );

  const transcriptRef = useRef(null);
  const selToken = useRef(0);
  const peekedIds = useRef(new Set());
  const lastScrollCount = useRef(-1);
  const blinkTimer = useRef(null);
  const streamTimer = useRef(null);
  const dotsTimer = useRef(null);
  const robotTimers = useRef([]);

  const clearRobotTimers = useCallback(() => {
    robotTimers.current.forEach(clearTimeout);
    robotTimers.current = [];
  }, []);

  const playWelcome = useCallback(() => {
    clearRobotTimers();
    if (reducedMotion) { setRobotPhase('idle'); setRobotExpression('neutral'); return; }
    setRobotPhase('peek'); setRobotExpression('surprised');
    robotTimers.current.push(setTimeout(() => setRobotPhase('wave'), 420));
    robotTimers.current.push(setTimeout(() => setRobotExpression('happy'), 600));
    robotTimers.current.push(setTimeout(() => { setRobotPhase('idle'); setRobotExpression('neutral'); }, 1220));
  }, [clearRobotTimers, reducedMotion]);

  const robotThinking = useCallback(() => { clearRobotTimers(); setRobotExpression('thinking'); }, [clearRobotTimers]);

  const robotHappy = useCallback(() => {
    clearRobotTimers();
    setRobotExpression('happy'); setArmLift(true);
    robotTimers.current.push(setTimeout(() => setArmLift(false), 300));
    robotTimers.current.push(setTimeout(() => setRobotExpression('neutral'), 1000));
  }, [clearRobotTimers]);

  const robotSurprised = useCallback(() => {
    clearRobotTimers();
    setRobotExpression('surprised');
    robotTimers.current.push(setTimeout(() => setRobotExpression('neutral'), 600));
  }, [clearRobotTimers]);

  const onRobotClick = useCallback(() => {
    if (robotPhase === 'wave') return;
    clearRobotTimers();
    setRobotPhase('wave'); setRobotExpression('happy');
    robotTimers.current.push(setTimeout(() => { setRobotPhase('idle'); setRobotExpression('neutral'); }, 1150));
  }, [robotPhase, clearRobotTimers]);

  const startDots = useCallback(() => {
    clearInterval(dotsTimer.current);
    setDotActive(0);
    dotsTimer.current = setInterval(() => setDotActive((d) => (d + 1) % 3), 250);
  }, []);
  const stopDots = useCallback(() => clearInterval(dotsTimer.current), []);

  const startStreaming = useCallback((index, fullText) => {
    const words = (fullText || '').split(' ');
    clearInterval(streamTimer.current);
    if (reducedMotion) { setStreamingIndex(null); return; }
    setStreamingIndex(index); setStreamingWords(0);
    streamTimer.current = setInterval(() => {
      setStreamingWords((w) => {
        const next = w + 1;
        if (next >= words.length) { clearInterval(streamTimer.current); setStreamingIndex(null); return words.length; }
        return next;
      });
    }, 30);
  }, [reducedMotion]);

  const scheduleBlink = useCallback(() => {
    clearTimeout(blinkTimer.current);
    if (reducedMotion) return;
    blinkTimer.current = setTimeout(() => {
      setBlinking(true);
      setTimeout(() => { setBlinking(false); scheduleBlink(); }, 130);
    }, 2600 + Math.random() * 2200);
  }, [reducedMotion]);

  const onSessionReady = useCallback((session) => {
    setActiveSession(session);
    if (!session.messages || session.messages.length === 0) {
      if (!peekedIds.current.has(session.id)) {
        peekedIds.current.add(session.id);
        playWelcome();
      } else {
        setRobotPhase('idle'); setRobotExpression('neutral');
      }
    } else {
      setRobotPhase('idle'); setRobotExpression('neutral');
    }
  }, [playWelcome]);

  const selectSession = useCallback((id, summary) => {
    selToken.current += 1;
    const token = selToken.current;
    setActiveSessionId(id); setActiveSession(null); setDraftText(''); setError('');
    getSession(id).then((full) => {
      if (token !== selToken.current) return;
      onSessionReady(full);
    }).catch((e) => {
      if (token !== selToken.current) return;
      setError(e.message);
      onSessionReady({ id, careerPath: summary?.careerPath || '', messages: [] });
    });
  }, [onSessionReady]);

  const startSession = useCallback(() => {
    setNewSessionBusy(true);
    createSession().then((session) => {
      const summary = { id: session.id, careerPath: session.careerPath, createdAt: session.createdAt, updatedAt: session.createdAt };
      setSessions((list) => [summary, ...list]);
      setActiveSessionId(session.id); setActiveSession(session);
      setNewSessionBusy(false); setJustCreatedId(session.id); setError(''); setDraftText('');
      setTimeout(() => setJustCreatedId(null), 240);
      peekedIds.current.add(session.id);
      playWelcome();
    }).catch((e) => { setNewSessionBusy(false); setError(e.message); });
  }, [playWelcome]);

  const onSessionsLoaded = useCallback((list) => {
    const sorted = (list || []).slice().sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt));
    setSessions(sorted); setSessionsLoading(false);
    setTimeout(() => setSessionsEntered(true), 20);
    if (sorted.length) selectSession(sorted[0].id, sorted[0]);
    else startSession();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadResources = useCallback(() => {
    getMyResources().then((groups) => {
      setResourceGroups(groups || []); setResourcesLoading(false);
      setTimeout(() => setResourcesEntered(true), 20);
    });
  }, []);

  useEffect(() => {
    getSessions().then(onSessionsLoaded);
    loadResources();
    scheduleBlink();
    getUnreadCount().then((r) => setUnreadCount(r.unreadCount)).catch(() => {});
    getMyProfile().then((p) => { if (p) setStudentName(`${p.firstName || ''} ${p.lastName || ''}`.trim()); }).catch(() => {});
    getAvatarBlobUrl().then((url) => { if (url) setAvatarSrc(url); }).catch(() => {});
    return () => {
      clearRobotTimers();
      clearInterval(dotsTimer.current);
      clearInterval(streamTimer.current);
      clearTimeout(blinkTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Auto-collapse side rails on narrow viewports; a manual re-expand survives small resizes.
  useEffect(() => {
    const mqNav = window.matchMedia('(max-width: 1180px)');
    const mqSessions = window.matchMedia('(max-width: 720px)');
    const applyNav = (e) => setNavCollapsed(e.matches);
    const applyResources = (e) => setResourcesCollapsed(e.matches);
    const applySessions = (e) => setSessionsCollapsed(e.matches);
    applyNav(mqNav); applyResources(mqNav); applySessions(mqSessions);
    mqNav.addEventListener('change', applyNav);
    mqNav.addEventListener('change', applyResources);
    mqSessions.addEventListener('change', applySessions);
    return () => {
      mqNav.removeEventListener('change', applyNav);
      mqNav.removeEventListener('change', applyResources);
      mqSessions.removeEventListener('change', applySessions);
    };
  }, []);

  useEffect(() => {
    const el = transcriptRef.current;
    if (!el) return;
    const msgCount = activeSession ? activeSession.messages.length : 0;
    const count = msgCount + (sending ? 1 : 0);
    if (count === lastScrollCount.current) return;
    lastScrollCount.current = count;
    if (count === 0) { el.scrollTop = 0; return; }
    el.scrollTo({ top: el.scrollHeight, behavior: reducedMotion ? 'auto' : 'smooth' });
  }, [activeSession, sending, reducedMotion]);

  const touchSession = useCallback((id) => {
    const now = new Date().toISOString();
    setSessions((list) => list.map((x) => (x.id === id ? { ...x, updatedAt: now } : x)));
  }, []);

  const send = useCallback((overrideText) => {
    const text = (overrideText != null ? overrideText : draftText).trim();
    if (!text || sending || !activeSession) return;
    const session = activeSession;
    const optimistic = { role: 'user', content: text, timestamp: new Date().toISOString() };
    setActiveSession((s) => ({ ...s, messages: [...s.messages, optimistic] }));
    setDraftText(''); setSending(true); setError('');
    robotThinking(); startDots();
    sendCoachMessage(session.id, text).then((reply) => {
      stopDots();
      setActiveSession((s) => {
        const messages = [...s.messages, reply];
        setTimeout(() => startStreaming(messages.length - 1, reply.content), 0);
        return { ...s, messages };
      });
      setSending(false);
      robotHappy(); touchSession(session.id);
    }).catch((e) => {
      stopDots();
      setActiveSession((s) => ({ ...s, messages: s.messages.slice(0, -1) }));
      setSending(false); setDraftText(text); setError(e.message);
      robotSurprised();
    });
  }, [draftText, sending, activeSession, robotThinking, startDots, stopDots, startStreaming, robotHappy, touchSession, robotSurprised]);

  const draftInputRef = useRef(null);
  const onDraftChange = (e) => setDraftText(e.target.value.slice(0, 2000));
  const onDraftKeyDown = (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } };

  // Height is derived from draftText (not just the onChange handler) so it also resizes when
  // voice input or send() sets draftText directly, without going through a real input event.
  useEffect(() => {
    const el = draftInputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, [draftText]);

  // Web Speech API only -- it doesn't expose input-device selection (unlike getUserMedia), it just
  // uses whatever the OS/browser treats as the default mic. There is no way to replicate a
  // device picker like a native app's from a web page.
  const toggleRecording = useCallback(() => {
    if (recording) {
      recognitionRef.current?.stop();
      return;
    }
    const SpeechRecognitionImpl = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognitionImpl) {
      setMicError('Voice input is not supported in this browser -- try Chrome or Edge.');
      return;
    }
    setMicError('');
    const recognition = new SpeechRecognitionImpl();
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.lang = navigator.language || 'en-US';
    draftBeforeRecordingRef.current = draftText;
    let finalText = draftText;

    recognition.onresult = (event) => {
      let interim = '';
      for (let i = event.resultIndex; i < event.results.length; i += 1) {
        const transcript = event.results[i][0].transcript;
        if (event.results[i].isFinal) {
          finalText = `${finalText}${finalText && !finalText.endsWith(' ') ? ' ' : ''}${transcript.trim()}`;
        } else {
          interim += transcript;
        }
      }
      const combined = `${finalText}${interim ? (finalText ? ' ' : '') + interim : ''}`;
      setDraftText(combined.slice(0, 2000));
    };
    recognition.onerror = (event) => {
      setMicError(event.error === 'not-allowed' ? 'Microphone access was denied.' : 'Could not hear you -- try again.');
      setRecording(false);
    };
    recognition.onend = () => setRecording(false);

    recognitionRef.current = recognition;
    setRecording(true);
    recognition.start();
  }, [recording, draftText]);

  useEffect(() => () => recognitionRef.current?.stop(), []);

  const onDeleteClick = (id, e) => { e.stopPropagation(); setConfirmDeleteId(id); };
  const onCancelDelete = () => setConfirmDeleteId(null);
  const onConfirmDelete = useCallback((id) => {
    deleteSession(id).catch(() => {}).then(() => {
      setConfirmDeleteId(null); setRemovingId(id);
      setTimeout(() => {
        setSessions((list) => {
          const remaining = list.filter((x) => x.id !== id);
          if (activeSessionId === id) {
            if (remaining.length) selectSession(remaining[0].id, remaining[0]);
            else startSession();
          }
          return remaining;
        });
        setRemovingId(null);
      }, 140);
    });
  }, [activeSessionId, selectSession, startSession]);

  const now = new Date();
  const sessionRows = useMemo(() => {
    const rows = [];
    if (!sessions.length) return rows;
    const groups = { Today: [], 'This week': [], Earlier: [] };
    sessions.forEach((sess) => groups[groupLabel(diffDays(sess.updatedAt, now))].push(sess));
    GROUP_LABELS.forEach((label) => {
      if (!groups[label].length) return;
      rows.push({ key: 'h-' + label, isHeader: true, label });
      groups[label].forEach((sess) => rows.push({ key: sess.id, isHeader: false, sess }));
    });
    return rows;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessions]);

  const messagesRaw = activeSession ? activeSession.messages : [];
  const firstAssistantIdx = messagesRaw.findIndex((m) => m.role === 'assistant');
  const welcomeVisible = !!activeSession && messagesRaw.length === 0;
  const dockedRobotVisible = !!activeSession && messagesRaw.length > 0;

  useEffect(() => {
    if (!welcomeVisible) return;
    setWelcomeIn(false);
    const raf1 = requestAnimationFrame(() => requestAnimationFrame(() => setWelcomeIn(true)));
    return () => cancelAnimationFrame(raf1);
  }, [welcomeVisible, activeSessionId]);
  const eye = robotExpression;
  const outerAnim = reducedMotion ? 'none'
    : robotPhase === 'peek' ? 'cbPeek 420ms cubic-bezier(.2,0,.2,1) forwards'
    : eye === 'thinking' ? 'cbTilt 1200ms cubic-bezier(.2,0,.2,1) infinite'
    : 'cbFloat 2400ms ease-in-out infinite';
  const armAnim = reducedMotion ? 'none'
    : robotPhase === 'wave' ? 'cbWave 1100ms cubic-bezier(.2,0,.2,1)'
    : armLift ? 'cbArmLift 300ms cubic-bezier(.2,0,.2,1)'
    : 'none';
  const antennaAnim = (!reducedMotion && eye === 'thinking') ? 'cbAntennaPulse 800ms cubic-bezier(.2,0,.2,1) infinite' : 'none';
  const draftLen = draftText.length;

  return (
    <div className="cb-co-root" style={{ height: '100vh', overflow: 'hidden', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)', display: 'flex', flexDirection: 'column' }}>

      <header className="cb-co-header" style={{ flexShrink: 0, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
          <IconButton
            className="cb-app-drawer-toggle"
            icon={drawerOpen ? 'x' : 'sliders-horizontal'}
            label={drawerOpen ? 'Close menu' : 'Open menu'}
            onClick={() => { const next = !drawerOpen; closeAllDrawers(); setDrawerOpen(next); }}
          />
          <Logo size={32} />
        </div>
        <div className="cb-app-header-actions" style={{ display: 'flex', alignItems: 'center', gap: 18, flexShrink: 0 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--status-danger)', color: '#FCFBF9', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center', boxSizing: 'border-box' }}>
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
          <div className="cb-app-header-divider" style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ width: 32, height: 32, borderRadius: '50%', background: 'var(--bone-300)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, overflow: 'hidden' }}>
              {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={15} />}
            </div>
            <div className="cb-co-avatar-name" style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
              <span style={{ fontSize: 13, color: 'var(--ink-900)' }}>{studentName || 'Your account'}</span>
            </div>
          </div>
          <div className="cb-app-header-divider" style={{ width: 1, height: 26, background: 'var(--line-hairline)' }} />
          <span className="cb-app-header-logout">
            <Button variant="ghost" size="sm" onClick={() => { clearTokens(); navigate('/'); }}>Log out</Button>
          </span>
        </div>
      </header>

      <div
        className="cb-co-shell cb-app-shell"
        style={{ '--nav-w': navCollapsed ? '60px' : '248px', '--sessions-w': sessionsCollapsed ? '60px' : '280px', '--resources-w': resourcesCollapsed ? '60px' : '300px' }}
      >
        <aside className={`cb-app-rail${drawerOpen ? ' is-open' : ''}`} style={{ borderRight: '1px solid var(--line-hairline)', overflowY: 'auto', overflowX: 'hidden', padding: `14px ${railCollapsed ? '8px' : '14px'} 18px`, boxSizing: 'border-box', display: 'flex', flexDirection: 'column' }}>
          <div className="cb-co-toggle-row" style={{ display: 'flex', justifyContent: railCollapsed ? 'center' : 'flex-end', paddingBottom: 10 }}>
            <IconButton icon="chevron-right" label={navCollapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={() => setNavCollapsed((v) => { persistNavCollapsed(!v); return !v; })} iconStyle={{ transform: navCollapsed ? 'none' : 'rotate(180deg)', transition: 'transform 200ms ease' }} />
          </div>
          <nav style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {NAV_ITEMS.map((item) => (
              <Link key={item.to} to={item.to} title={item.label} onClick={() => setDrawerOpen(false)} style={{ display: 'flex', alignItems: 'center', gap: 11, justifyContent: railCollapsed ? 'center' : 'flex-start', padding: railCollapsed ? '10px 0' : '10px 14px', fontSize: 13, letterSpacing: '.06em', textTransform: 'uppercase', background: item.active ? 'var(--ink-900)' : 'transparent', color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)', border: 'none' }}>
                <Icon name={item.icon} size={16} style={{ color: item.active ? 'var(--text-inverse)' : 'var(--ink-700)' }} />
                {!railCollapsed && <span className="cb-co-sidebar-label">{item.label}</span>}
              </Link>
            ))}
          </nav>
          <div className="cb-app-drawer-logout" style={{ paddingTop: 12 }}>
            <Button variant="ghost" size="sm" fullWidth onClick={() => { clearTokens(); navigate('/'); }}>Log out</Button>
          </div>
          {!railCollapsed && (
            <div className="cb-co-sidebar-footer" style={{ marginTop: 'auto', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', paddingTop: 24 }}>
              <div style={{ background: 'var(--taupe-100)', padding: '28px 22px', display: 'flex', flexDirection: 'column', gap: 12 }}>
                <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>CareerBridge Plus</span>
                <p style={{ fontSize: 15, lineHeight: 1.5, color: 'var(--ink-900)', margin: 0, fontWeight: 500 }}>Unlimited coach sessions need Plus.</p>
                <p style={{ fontSize: 13, lineHeight: 1.55, color: 'var(--ink-600)', margin: 0 }}>Free covers 5 coach sessions a month. Upgrade for unlimited sessions and full roadmap pacing.</p>
                <Link to="/plans" style={{ display: 'block', textDecoration: 'none', border: 0, marginTop: 8 }}>
                  <Button variant="primary" size="sm" fullWidth iconAfter="arrow-right">See plans</Button>
                </Link>
              </div>
            </div>
          )}
        </aside>

        <aside className={`cb-co-sessions${sessionsOpen ? ' is-open' : ''}`} style={{ background: 'var(--bone-50)', borderRight: '1px solid var(--line-hairline)', overflowY: 'auto', overflowX: 'hidden', display: 'flex', flexDirection: 'column' }}>
          {sessionsRailCollapsed ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '16px 0' }}>
              <IconButton icon="chevron-right" label="Expand sessions" onClick={() => setSessionsCollapsed(false)} />
              <IconButton icon="plus" label="New session" variant="secondary" onClick={startSession} disabled={newSessionBusy} />
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)', writingMode: 'vertical-rl' }}>Sessions</span>
            </div>
          ) : (
            <>
              <div style={{ padding: '16px 16px 14px', borderBottom: '1px solid var(--line-hairline)', flexShrink: 0, display: 'flex', gap: 8, alignItems: 'center' }}>
                <Button variant="primary" size="sm" iconAfter="plus" fullWidth onClick={startSession} disabled={newSessionBusy}>{newSessionBusy ? 'Starting…' : 'New session'}</Button>
                <IconButton
                  icon="chevron-right"
                  label={isPhone ? 'Close sessions' : 'Collapse sessions'}
                  // On phone the rail is a fixed-width drawer, always expanded -- "collapse to
                  // an icon strip" has nothing to collapse into, so the button did nothing
                  // visible. Close the drawer instead, which is what a phone user expects
                  // this control to do.
                  onClick={() => (isPhone ? setSessionsOpen(false) : setSessionsCollapsed(true))}
                  iconStyle={{ transform: 'rotate(180deg)' }}
                />
              </div>

              {sessionsLoading && (
                <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <Skeleton height={46} /><Skeleton height={46} /><Skeleton height={46} />
                </div>
              )}

              {!sessionsLoading && sessions.length === 0 && (
                <div style={{ padding: '22px 16px', textAlign: 'center' }}>
                  <Icon name="sparkles" size={20} />
                  <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', margin: '10px 0 4px' }}>No sessions yet</p>
                  <p style={{ fontSize: 12, color: 'var(--ink-500)', margin: 0 }}>Start a conversation with your coach.</p>
                </div>
              )}

              {!sessionsLoading && sessions.length > 0 && (
                <div style={{ flex: 1 }}>
                  {sessionRows.map((row) => row.isHeader ? (
                    <div key={row.key} style={{ padding: '14px 16px 6px', fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>{row.label}</div>
                  ) : (
                    <SessionRow
                      key={row.key} sess={row.sess} isActive={row.sess.id === activeSessionId}
                      isConfirm={confirmDeleteId === row.sess.id}
                      opacity={removingId === row.sess.id ? 0 : (justCreatedId === row.sess.id ? 1 : (sessionsEntered ? 1 : 0))}
                      animate={justCreatedId === row.sess.id}
                      now={now}
                      onSelect={() => { if (row.sess.id !== activeSessionId) selectSession(row.sess.id, row.sess); }}
                      onDeleteClick={(e) => onDeleteClick(row.sess.id, e)}
                      onConfirmDelete={() => onConfirmDelete(row.sess.id)}
                      onCancelDelete={onCancelDelete}
                    />
                  ))}
                </div>
              )}
            </>
          )}
        </aside>

        <main className="cb-co-main" style={{ minWidth: 0, display: 'flex', flexDirection: 'column', background: 'var(--bone-100)' }}>
          {/* Phone-only. The sessions and resources rails are off-screen drawers at this width,
              so they need a way back in -- without this they'd be unreachable, not just hidden. */}
          <div className="cb-co-mobile-bar" style={{ flexShrink: 0, alignItems: 'center', gap: 8, padding: '10px 12px', borderBottom: '1px solid var(--line-hairline)', background: 'var(--surface-page)' }}>
            <Button variant="secondary" size="sm" onClick={() => { closeAllDrawers(); setSessionsOpen(true); }}>Sessions</Button>
            <Button variant="secondary" size="sm" onClick={() => { closeAllDrawers(); setResourcesOpen(true); }}>Resources</Button>
          </div>
          <div style={{ position: 'relative', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
            <div ref={transcriptRef} style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '28px 32px' }}>
              <div style={{ maxWidth: 760, margin: '0 auto', width: '100%', display: 'flex', flexDirection: 'column', gap: 18, minHeight: '100%' }}>

                {welcomeVisible && (
                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 22, textAlign: 'center', padding: '20px 0' }}>
                    <RobotSvg size={160} outerAnim={outerAnim} armAnim={armAnim} antennaAnim={antennaAnim} eye={eye} blinking={blinking} />
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center', ...revealStyle(welcomeIn, 0, { distance: 16 }) }}>
                      <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.15, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400, maxWidth: 480 }}>Ask me anything about your <i>{activeSession?.careerPath}</i> plan.</h1>
                      <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-500)', margin: 0, maxWidth: 400 }}>I have your assessment result, roadmap and readiness score. I know where you are. Ask me where to go next.</p>
                    </div>
                    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'center', maxWidth: 480 }}>
                      {SUGGESTION_CHIPS.map((chip, i) => (
                        <button key={chip} type="button" onClick={() => send(chip)} style={{ border: 0, background: 'none', padding: 0, cursor: 'pointer', ...revealStyle(welcomeIn, i + 1, { distance: 10, duration: 450 }) }}>
                          <Tag>{chip}</Tag>
                        </button>
                      ))}
                    </div>
                  </div>
                )}

                {!welcomeVisible && (
                  <>
                    {messagesRaw.map((msg, i) => {
                      const isAssistant = msg.role === 'assistant';
                      const isStreamingNow = isAssistant && i === streamingIndex;
                      const displayContent = isStreamingNow ? msg.content.split(' ').slice(0, streamingWords).join(' ') : msg.content;
                      const enterAnim = reducedMotion ? 'cbFadeIn 140ms cubic-bezier(.2,0,.2,1) both' : (isAssistant ? 'cbBubbleInL 220ms cubic-bezier(.2,0,.2,1) both' : 'cbBubbleInR 140ms cubic-bezier(.2,0,.2,1) both');
                      return (
                        <div key={i}>
                          {isAssistant && i === firstAssistantIdx && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
                              <Badge tone="accent">AI Coach</Badge>
                              <span style={{ fontSize: 12, color: 'var(--ink-400)' }}>Coaching session · context: your assessment, roadmap and readiness score</span>
                            </div>
                          )}
                          {!isAssistant ? (
                            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, alignItems: 'flex-start', animation: enterAnim }}>
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4 }}>
                                <div style={{ maxWidth: 480, background: 'var(--ink-900)', color: 'var(--bone-50)', padding: '12px 16px', fontSize: 14, lineHeight: 1.55, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>{msg.content}</div>
                                <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>{studentName ? `${studentName} · ` : ''}{fmtClock(msg.timestamp)}</span>
                              </div>
                              <div style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--bone-300)', overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 2 }}>
                                {avatarSrc ? <img src={avatarSrc} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} /> : <Icon name="user" size={13} />}
                              </div>
                            </div>
                          ) : (
                            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', animation: enterAnim }}>
                              <span style={{ width: 28, height: 28, borderRadius: '50%', background: 'var(--ink-900)', color: 'var(--bone-50)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0, marginTop: 2 }}>
                                <Icon name="sparkles" size={14} style={{ color: 'var(--bone-50)' }} />
                              </span>
                              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: 4, minWidth: 0 }}>
                                <div style={{ maxWidth: 560, background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', color: 'var(--ink-900)', padding: '14px 17px', fontSize: 14, lineHeight: 1.6, wordBreak: 'break-word' }}><MarkdownLite text={displayContent} /></div>
                                <span style={{ fontSize: 11, color: 'var(--ink-400)' }}>{fmtClock(msg.timestamp)}</span>
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })}

                    {sending && (
                      <div style={{ display: 'flex', alignItems: 'center' }}>
                        <div style={{ background: 'var(--bone-50)', border: '1px solid var(--line-hairline)', padding: '15px 18px', display: 'flex', gap: 6, alignItems: 'center' }}>
                          <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--ink-400)', display: 'inline-block', transform: `scale(${dotActive === 0 ? 1.4 : 1})` }} />
                          <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--ink-400)', display: 'inline-block', transform: `scale(${dotActive === 1 ? 1.4 : 1})` }} />
                          <span style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--ink-400)', display: 'inline-block', transform: `scale(${dotActive === 2 ? 1.4 : 1})` }} />
                        </div>
                      </div>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>

          <div style={{ flexShrink: 0, borderTop: '1px solid var(--line-hairline)', background: 'var(--bone-50)', padding: '16px 32px 18px', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {error && (
              <div style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
                <Alert tone="danger" title={error} />
              </div>
            )}
            {micError && (
              <div style={{ maxWidth: 760, margin: '0 auto', width: '100%' }}>
                <Alert tone="danger" title={micError} />
              </div>
            )}
            <div style={{ display: 'flex', alignItems: 'stretch', gap: 8, maxWidth: 760, margin: '0 auto', width: '100%' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <textarea
                  ref={draftInputRef}
                  rows={1}
                  placeholder={recording ? 'Listening…' : 'Ask your coach...'}
                  value={draftText}
                  onChange={onDraftChange}
                  onKeyDown={onDraftKeyDown}
                  style={{
                    width: '100%', boxSizing: 'border-box', padding: '9px 12px', fontSize: 14,
                    fontFamily: 'var(--font-sans)', color: 'var(--ink-900)', background: 'var(--bone-50)',
                    border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-sm)', outline: 'none',
                    resize: 'none', overflowY: 'auto', maxHeight: 160, lineHeight: 1.4,
                  }}
                />
              </div>
              <IconButton
                icon={recording ? 'mic-off' : 'mic'}
                label={recording ? 'Stop recording' : 'Speak your message'}
                variant={recording ? 'primary' : 'secondary'}
                onClick={toggleRecording}
                iconStyle={{ color: recording ? 'var(--bone-50)' : 'var(--status-danger)' }}
                disabled={sending}
              />
              <IconButton icon="arrow-right" label="Send message" variant="primary" onClick={() => send()} disabled={!draftText.trim() || sending} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', minHeight: 14, maxWidth: 760, margin: '0 auto', width: '100%' }}>
              {draftLen >= 1800 && <span className="cb-num" style={{ fontSize: 11, color: 'var(--ink-400)' }}>{2000 - draftLen} left</span>}
            </div>
            <span style={{ fontSize: 11, color: 'var(--ink-400)', fontStyle: 'italic', textAlign: 'center', maxWidth: 760, margin: '0 auto', width: '100%' }}>Generated from your assessment result · not a human review</span>
          </div>
        </main>

        {dockedRobotVisible && (
          <div className="cb-co-robot" onClick={onRobotClick} style={{ position: 'absolute', bottom: 130, right: (resourcesCollapsed ? 60 : 300) + 12, width: 70, height: 92, pointerEvents: 'auto', cursor: 'pointer', zIndex: 0, transition: 'right 220ms cubic-bezier(.2,0,.2,1)' }}>
            <RobotSvg size={90} outerAnim={outerAnim} armAnim={armAnim} antennaAnim={antennaAnim} eye={eye} blinking={blinking} />
          </div>
        )}

        <aside className={`cb-co-resources${resourcesOpen ? ' is-open' : ''}`} style={{ position: 'relative', zIndex: 2, background: 'var(--bone-50)', borderLeft: '1px solid var(--line-hairline)', overflowY: 'auto', overflowX: 'hidden' }}>
          {resourcesRailCollapsed ? (
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 14, padding: '16px 0' }}>
              <IconButton icon="chevron-right" label="Expand resources" onClick={() => setResourcesCollapsed(false)} iconStyle={{ transform: 'rotate(180deg)' }} />
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)', writingMode: 'vertical-rl' }}>Resources</span>
            </div>
          ) : (
            <>
              <div style={{ padding: '20px 22px 16px' }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10 }}>
                  <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-400)' }}>Milestone resources</span>
                  <IconButton
                    icon="chevron-right"
                    label={isPhone ? 'Close resources' : 'Collapse resources'}
                    onClick={() => (isPhone ? setResourcesOpen(false) : setResourcesCollapsed(true))}
                  />
                </div>
                <hr style={{ marginTop: 12, height: 1, background: 'var(--line-ink)', border: 0 }} />
                <p style={{ fontSize: 12, lineHeight: 1.55, color: 'var(--ink-400)', fontStyle: 'italic', margin: '10px 0 0' }}>They change as you move through the roadmap. Completing this step swaps the list.</p>
              </div>

              {resourcesLoading && (
                <div style={{ padding: '4px 22px', display: 'flex', flexDirection: 'column', gap: 10 }}>
                  <Skeleton height={72} /><Skeleton height={72} /><Skeleton height={72} />
                </div>
              )}

              {!resourcesLoading && resourceGroups.length === 0 && (
                <div style={{ padding: 22, textAlign: 'center' }}>
                  <Icon name="refresh-cw" size={20} />
                  <p style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-900)', margin: '10px 0 4px' }}>Resources not ready yet</p>
                  <p style={{ fontSize: 12, color: 'var(--ink-500)', margin: 0 }}>Your coach is building the catalog. Check back shortly.</p>
                </div>
              )}

              {!resourcesLoading && resourceGroups.length > 0 && (
                <div style={{ paddingBottom: 16 }}>
                  {resourceGroups.map((grp, gi) => (
                    <div key={grp.milestoneTitle} style={revealStyle(resourcesEntered, gi, { step: 70, distance: 14 })}>
                      <div style={{ padding: '16px 22px 8px' }}>
                        <span style={{ fontSize: 13, fontWeight: 600, textTransform: 'uppercase', letterSpacing: '.04em', color: 'var(--ink-900)' }}>{grp.milestoneTitle}</span>
                      </div>
                      {(!grp.resources || grp.resources.length === 0) ? (
                        <div style={{ padding: '4px 22px 8px', textAlign: 'center' }}>
                          <p style={{ fontSize: 12, color: 'var(--ink-500)', margin: 0 }}>Resources for this step are being prepared.</p>
                        </div>
                      ) : grp.resources.map((res) => {
                        const tone = res.type === 'VIDEO' ? 'danger' : res.type === 'COURSE' ? 'info' : 'default';
                        const label = res.type === 'VIDEO' ? `VIDEO · ${(res.platform || 'YouTube').toUpperCase()}` : (res.type || 'ARTICLE');
                        return (
                          <a key={res.url + res.title} href={res.url} target="_blank" rel="noopener noreferrer" className="cb-co-resource-row" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '13px 22px', borderBottom: '1px solid var(--line-hairline)' }}>
                            <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
                              <span style={{ fontSize: 13, fontWeight: 500, color: 'var(--ink-800)', lineHeight: 1.4 }}>{res.title}</span>
                              <Badge tone={tone}>{label}</Badge>
                            </span>
                            <Icon name="external-link" size={14} style={{ flexShrink: 0, color: 'var(--ink-400)' }} />
                          </a>
                        );
                      })}
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </aside>

        {isPhone && (drawerOpen || sessionsOpen || resourcesOpen) && (
          <button type="button" className="cb-app-scrim" aria-label="Close menu" onClick={closeAllDrawers} />
        )}
      </div>
    </div>
  );
}

function SessionRow({ sess, isActive, isConfirm, opacity, animate, now, onSelect, onDeleteClick, onConfirmDelete, onCancelDelete }) {
  if (isConfirm) {
    return (
      <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--line-hairline)', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <Alert tone="danger" title="Delete this session?" />
        <div style={{ display: 'flex', gap: 8 }}>
          <Button variant="secondary" size="sm" onClick={onConfirmDelete} style={{ color: 'var(--status-danger)', borderColor: 'var(--status-danger)' }}>Delete</Button>
          <Button variant="ghost" size="sm" onClick={onCancelDelete}>Cancel</Button>
        </div>
      </div>
    );
  }
  return (
    <div className="cb-co-row" style={{ position: 'relative', opacity, transition: 'opacity 140ms cubic-bezier(.2,0,.2,1)', animation: animate ? 'cbRowIn 220ms cubic-bezier(.2,0,.2,1) both' : 'none' }}>
      <button type="button" onClick={onSelect} style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'flex-start', width: '100%', padding: '12px 32px 12px 15px', border: 0, borderBottom: '1px solid var(--line-hairline)', borderLeft: `2px solid ${isActive ? 'var(--taupe-700)' : 'transparent'}`, background: isActive ? 'var(--bone-100)' : 'transparent', font: 'inherit', cursor: 'pointer', textAlign: 'left' }}>
        <span style={{ fontSize: 14, fontWeight: 500, color: 'var(--ink-900)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>{sess.careerPath}</span>
        <span className="cb-num" style={{ fontSize: 12, color: 'var(--ink-400)' }}>{fmtSessionDate(sess.updatedAt, now)}</span>
      </button>
      <button type="button" aria-label="Delete session" onClick={onDeleteClick} className="cb-co-row-delete" style={{ position: 'absolute', right: 6, top: '50%', transform: 'translateY(-50%)', width: 24, height: 24, display: 'flex', alignItems: 'center', justifyContent: 'center', border: 0, background: 'transparent', color: 'var(--ink-500)', cursor: 'pointer' }}>
        <Icon name="x" size={14} />
      </button>
    </div>
  );
}

function RobotSvg({ size, outerAnim, armAnim, antennaAnim, eye, blinking }) {
  const eyeNeutral = eye === 'neutral' && !blinking;
  const eyeHappy = eye === 'happy';
  const eyeThinking = eye === 'thinking';
  const eyeSurprised = eye === 'surprised';
  const eyeBlink = eye === 'neutral' && blinking;
  return (
    <svg viewBox="0 -10 100 140" aria-hidden="true" style={{ height: size, width: 'auto', display: 'block', overflow: 'visible' }}>
      <g style={{ transformOrigin: '50px 124px', animation: outerAnim }}>
        <rect x="38" y="108" width="9" height="16" rx="4" fill="var(--ink-900)" />
        <rect x="53" y="108" width="9" height="16" rx="4" fill="var(--ink-900)" />
        <line x1="26" y1="64" x2="10" y2="95" stroke="var(--ink-900)" strokeWidth="11" strokeLinecap="round" />
        <g style={{ transformOrigin: '74px 64px', animation: armAnim }}><line x1="74" y1="64" x2="90" y2="95" stroke="var(--ink-900)" strokeWidth="11" strokeLinecap="round" /></g>
        <rect x="24" y="62" width="52" height="46" rx="14" fill="var(--ink-900)" />
        <rect x="37" y="74" width="26" height="22" rx="5" fill="var(--bone-50)" />
        <svg x="43" y="79" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--ink-900)" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><path d="M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" /><rect width="20" height="14" x="2" y="6" rx="2" /></svg>
        <rect x="20" y="8" width="60" height="46" rx="16" fill="var(--ink-900)" />
        {eyeNeutral && (<><circle cx="38" cy="31" r="4" fill="var(--bone-50)" /><circle cx="62" cy="31" r="4" fill="var(--bone-50)" /></>)}
        {eyeHappy && (<><path d="M33,31 Q38,37 43,31" stroke="var(--status-success)" strokeWidth="3" fill="none" strokeLinecap="round" /><path d="M57,31 Q62,37 67,31" stroke="var(--status-success)" strokeWidth="3" fill="none" strokeLinecap="round" /></>)}
        {eyeThinking && (<><circle cx="38" cy="31" r="4" fill="var(--bone-50)" /><ellipse cx="62" cy="31" rx="4" ry="1.2" fill="var(--bone-50)" /></>)}
        {eyeSurprised && (<><circle cx="38" cy="31" r="6.5" fill="var(--bone-50)" /><circle cx="62" cy="31" r="6.5" fill="var(--bone-50)" /></>)}
        {eyeBlink && (<><rect x="34" y="30" width="8" height="2" rx="1" fill="var(--bone-50)" /><rect x="58" y="30" width="8" height="2" rx="1" fill="var(--bone-50)" /></>)}
        <line x1="50" y1="8" x2="50" y2="-2" stroke="var(--taupe-500)" strokeWidth="2" strokeLinecap="round" />
        <circle cx="50" cy="-4" r="3.5" fill="var(--taupe-500)" style={{ animation: antennaAnim }} />
      </g>
    </svg>
  );
}
