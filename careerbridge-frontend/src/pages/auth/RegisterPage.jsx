import { useState } from 'react';
import { register } from '../../api/authApi';
import { Link } from 'react-router-dom';
import {
  Alert, Button, Checkbox, Field, Icon, Input, Logo,
} from '../../components/ui';
import './register.css';

const ROLES = [
  { value: 'STUDENT', label: 'Student', hint: 'Assess, plan, apply' },
  { value: 'PLACEMENT_OFFICER', label: 'Placement officer', hint: 'Track a cohort' },
  { value: 'RECRUITER', label: 'Recruiter', hint: 'Post roles, hire' },
  { value: 'MENTOR', label: 'Mentor', hint: 'Guide students' },
];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validate({ firstName, lastName, email, password, agreed }) {
  const errors = {};
  if (!firstName.trim()) errors.firstName = 'First name is required';
  if (!lastName.trim()) errors.lastName = 'Last name is required';
  if (!email.trim()) errors.email = 'Email is required';
  else if (!EMAIL_RE.test(email)) errors.email = 'Enter a valid email';
  if (!password) errors.password = 'Password is required';
  else if (password.length < 8) errors.password = 'At least 8 characters';
  if (!agreed) errors.agreed = 'You must accept the terms';
  return errors;
}

export default function RegisterPage() {
  const [role, setRole] = useState('STUDENT');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState({});
  const [requestError, setRequestError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const onSubmit = async () => {
    const nextErrors = validate({
      firstName, lastName, email, password, agreed,
    });
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      const data = await register({
        firstName, lastName, email, password, role,
      });
      localStorage.setItem('cb_access_token', data.accessToken);
      localStorage.setItem('cb_refresh_token', data.refreshToken);
      window.location.href = '/dashboard';
    } catch (e) {
      setRequestError(e.message || 'Something went wrong. Please try again.');
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
        <img src="/images/hero-02.jpg" alt="" style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', objectPosition: '50% 25%', filter: 'saturate(.92)' }} />
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
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Create your account</span>
            <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
              Start with one assessment.
            </h1>
            <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-600)', margin: 0 }}>
              Everything downstream — matches, roadmap, readiness — is generated from it.
            </p>
          </div>

          {requestError && <Alert tone="danger" title={requestError} />}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
            <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>I am joining as</span>
            <div className="cb-reg-roles" style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 1, background: 'var(--line-hairline)', border: 'var(--border-hairline)' }}>
              {ROLES.map((r) => {
                const selected = r.value === role;
                return (
                  <button
                    key={r.value}
                    type="button"
                    onClick={() => setRole(r.value)}
                    style={{
                      display: 'flex', flexDirection: 'column', gap: 4, alignItems: 'flex-start',
                      padding: '15px 16px', border: 0, cursor: 'pointer', font: 'inherit', textAlign: 'left',
                      background: selected ? 'var(--ink-900)' : 'var(--bone-50)',
                      color: selected ? 'var(--bone-50)' : 'var(--ink-900)',
                    }}
                  >
                    <span style={{ fontSize: 14, fontWeight: 500 }}>{r.label}</span>
                    <span style={{ fontSize: 11, color: selected ? 'var(--taupe-500)' : 'var(--ink-400)' }}>{r.hint}</span>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="cb-reg-fields" style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 14 }}>
            <Field label="First name" error={errors.firstName}>
              <Input placeholder="Ishita" value={firstName} onChange={(e) => setFirstName(e.target.value)} error={errors.firstName} />
            </Field>
            <Field label="Last name" error={errors.lastName}>
              <Input placeholder="Rao" value={lastName} onChange={(e) => setLastName(e.target.value)} error={errors.lastName} />
            </Field>
          </div>

          <Field label="Email" error={errors.email}>
            <Input type="email" placeholder="you@college.edu" value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
          </Field>

          <Field label="Password" hint="At least 8 characters" error={errors.password}>
            <Input type="password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
          </Field>

          <Checkbox label="I agree to the terms of use and privacy policy" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
          {errors.agreed && <span style={{ fontSize: 12, color: 'var(--status-danger)', marginTop: -14 }}>{errors.agreed}</span>}

          <Button
            size="lg"
            iconAfter={isSubmitting ? undefined : 'arrow-right'}
            disabled={isSubmitting}
            onClick={onSubmit}
            style={{ width: '100%', justifyContent: 'center' }}
          >
            {isSubmitting ? 'CREATING…' : 'CREATE MY ACCOUNT'}
          </Button>

          <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
            <hr style={{ flex: 1, height: 1, background: 'var(--line-hairline)', border: 0 }} />
            <span style={{ fontSize: 11, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--ink-300)' }}>or</span>
            <hr style={{ flex: 1, height: 1, background: 'var(--line-hairline)', border: 0 }} />
          </div>
          <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
            Already have an account? <a href="/login">Log in</a>
          </p>
        </div>
      </div>
    </div>
  );
}
