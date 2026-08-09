import { useState } from 'react';
import { Link } from 'react-router-dom';
import { login } from '../../api/authApi';
import { resolvePostLoginDestination } from '../../utils/postLoginRedirect';
import { setStoredUser } from '../../utils/tokenUtils';
import {
  Alert, Button, Field, Icon, Input, Logo,
} from '../../components/ui';
import './register.css';

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate({ email, password }) {
  const errors = {};
  if (!email.trim()) errors.email = 'Email is required';
  else if (!EMAIL_RE.test(email)) errors.email = 'Enter a valid email';
  if (!password) errors.password = 'Password is required';
  return errors;
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState({});
  const [requestError, setRequestError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const onSubmit = async () => {
    const nextErrors = validate({ email, password });
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const data = await login({ email, password });
      localStorage.setItem('cb_access_token', data.accessToken);
      localStorage.setItem('cb_refresh_token', data.refreshToken);
      // AuthResponse carries no name on login (only register does) -- email is the best we have here.
      setStoredUser({ email: data.email });
      window.location.href = await resolvePostLoginDestination(data.role);
    } catch (e) {
      setRequestError(e.message || 'Check your email and password and try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      className="cb-reg-grid"
      style={{ background: 'var(--bone-100)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}
    >
      <div className="cb-reg-hero" style={{ position: 'relative', overflow: 'hidden', background: 'var(--bone-300)', display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '44px 48px', minHeight: 280 }}>
        <img src="/images/hero-03.jpg" alt="" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: '50% 25%', filter: 'saturate(.92)' }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(180deg, rgba(27,26,24,.32) 0%, rgba(27,26,24,.10) 38%, rgba(27,26,24,.78) 100%)' }} />
        <div style={{ position: 'relative', alignSelf: 'flex-start' }}>
          <Logo size={46} tone="inverse" />
        </div>
        <div style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: 16, maxWidth: 440 }}>
          <span style={{ fontFamily: 'var(--font-display)', fontSize: 46, lineHeight: 1.04, letterSpacing: '-.015em', color: 'var(--bone-50)' }}>
            Bridge today. <i>Build</i> tomorrow.
          </span>
          <p style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--bone-300)', margin: 0 }}>
            One assessment gives you ranked career matches, a sequenced roadmap and a readiness score recruiters can filter on.
          </p>
        </div>
      </div>

      <div className="cb-reg-form-wrap" style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '56px 40px', overflowY: 'auto' }}>
        <Link
          to="/"
          style={{
            position: 'absolute', top: 24, right: 32, display: 'inline-flex', alignItems: 'center',
            gap: 6, fontSize: 13, fontWeight: 500, color: 'var(--ink-600)', border: 'none',
          }}
        >
          <Icon name="arrow-left" size={15} style={{ color: 'var(--ink-600)' }} />
          Back
        </Link>
        <div style={{ width: '100%', maxWidth: 440, display: 'flex', flexDirection: 'column', gap: 24 }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Welcome back</span>
            <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
              Pick up where you <i>left</i> off.
            </h1>
          </div>

          {requestError && <Alert tone="danger" title="Couldn't log in" message={requestError} />}

          <Field label="Email" error={errors.email}>
            <Input type="email" placeholder="you@college.edu" value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
          </Field>

          <Field label="Password" error={errors.password}>
            <Input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
          </Field>

          <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: -8 }}>
            <Link to="/forgot-password" style={{ fontSize: 13, color: 'var(--taupe-700)' }}>Forgot password?</Link>
          </div>

          <Button
            size="lg"
            iconAfter={isSubmitting ? undefined : 'arrow-right'}
            disabled={isSubmitting}
            onClick={onSubmit}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            {isSubmitting ? 'LOGGING IN…' : 'LOG IN'}
          </Button>

          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <hr style={{ flex: 1, height: 1, background: 'var(--line-hairline)', border: 0 }} />
            <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-300)' }}>or</span>
            <hr style={{ flex: 1, height: 1, background: 'var(--line-hairline)', border: 0 }} />
          </div>

          <Button
            variant="secondary"
            size="lg"
            iconAfter="arrow-right"
            to="/register"
            style={{ width: '100%', justifyContent: 'center' }}
          >
            CREATE AN ACCOUNT
          </Button>
        </div>
      </div>
    </div>
  );
}
