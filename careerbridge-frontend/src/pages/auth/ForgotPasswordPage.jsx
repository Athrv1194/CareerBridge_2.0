import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { forgotPassword, resetPassword, verifyOtp } from '../../api/authApi';
import {
  Alert, Button, Field, Icon, Input, Logo, OtpInput,
} from '../../components/ui';

const RESEND_COOLDOWN_SECONDS = 30;
const REDIRECT_DELAY_MS = 2500;

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function useCooldown() {
  const [secondsLeft, setSecondsLeft] = useState(0);

  useEffect(() => {
    if (secondsLeft <= 0) return undefined;
    const id = setInterval(() => setSecondsLeft((s) => Math.max(0, s - 1)), 1000);
    return () => clearInterval(id);
  }, [secondsLeft]);

  return [secondsLeft, () => setSecondsLeft(RESEND_COOLDOWN_SECONDS)];
}

export default function ForgotPasswordPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState('email'); // email -> otp -> reset -> done
  const [email, setEmail] = useState('');
  const [otp, setOtp] = useState('');
  const [resetToken, setResetToken] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [cooldown, startCooldown] = useCooldown();

  useEffect(() => {
    if (step !== 'done') return undefined;
    const id = setTimeout(() => navigate('/login'), REDIRECT_DELAY_MS);
    return () => clearTimeout(id);
  }, [step, navigate]);

  const submitEmail = async () => {
    setError(null);
    if (!email.trim() || !EMAIL_RE.test(email)) {
      setError('Enter a valid email address.');
      return;
    }
    setIsSubmitting(true);
    try {
      await forgotPassword(email);
      startCooldown();
      setStep('otp');
    } catch (e) {
      setError(e.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const resendCode = async () => {
    if (cooldown > 0) return;
    setError(null);
    try {
      await forgotPassword(email);
      startCooldown();
    } catch (e) {
      setError(e.message);
    }
  };

  const submitOtp = async () => {
    setError(null);
    if (otp.length !== 4) {
      setError('Enter the 4-digit code.');
      return;
    }
    setIsSubmitting(true);
    try {
      const data = await verifyOtp({ email, otp });
      setResetToken(data.resetToken);
      setStep('reset');
    } catch (e) {
      setError(e.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const submitNewPassword = async () => {
    setError(null);
    if (newPassword.length < 8) {
      setError('Password must be at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setIsSubmitting(true);
    try {
      await resetPassword({
        email, resetToken, newPassword, confirmPassword,
      });
      setStep('done');
    } catch (e) {
      setError(e.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: 'relative', minHeight: '100vh', display: 'flex', flexDirection: 'column',
        alignItems: 'center', justifyContent: 'center', background: 'var(--bone-100)',
        fontFamily: 'var(--font-sans)', padding: 24,
      }}
    >
      <div style={{ position: 'absolute', top: 24, left: 32 }}>
        <Logo size={32} />
      </div>
      <Link
        to="/login"
        style={{
          position: 'absolute', top: 28, right: 32, display: 'inline-flex', alignItems: 'center',
          gap: 6, fontSize: 13, fontWeight: 500, color: 'var(--ink-600)', border: 'none',
        }}
      >
        <Icon name="arrow-left" size={15} style={{ color: 'var(--ink-600)' }} />
        Back to login
      </Link>

      <div style={{ width: '100%', maxWidth: 380, display: 'flex', flexDirection: 'column', gap: 22, alignItems: 'center', textAlign: 'center' }}>
        {step === 'email' && (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Reset your password
              </h1>
              <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
                Enter your email and we&apos;ll send you a code.
              </p>
            </div>
            {error && <Alert tone="danger" title={error} />}
            <div style={{ width: '100%', textAlign: 'left' }}>
              <Field label="Email">
                <Input type="email" placeholder="you@college.edu" value={email} onChange={(e) => setEmail(e.target.value)} />
              </Field>
            </div>
            <Button size="lg" disabled={isSubmitting} onClick={submitEmail} style={{ width: '100%', justifyContent: 'center' }}>
              {isSubmitting ? 'SENDING…' : 'SEND CODE'}
            </Button>
          </>
        )}

        {step === 'otp' && (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Enter the code
              </h1>
              <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
                We sent a 4-digit code to <strong>{email}</strong>
              </p>
            </div>
            {error && <Alert tone="danger" title={error} />}
            <OtpInput length={4} value={otp} onChange={setOtp} error={!!error} />
            <Button size="lg" disabled={isSubmitting} onClick={submitOtp} style={{ width: '100%', justifyContent: 'center' }}>
              {isSubmitting ? 'VERIFYING…' : 'SUBMIT CODE'}
            </Button>
            <button
              type="button"
              onClick={resendCode}
              disabled={cooldown > 0}
              style={{
                background: 'none', border: 'none', padding: 0, fontSize: 13,
                color: cooldown > 0 ? 'var(--ink-300)' : 'var(--taupe-700)',
                cursor: cooldown > 0 ? 'default' : 'pointer',
              }}
            >
              {cooldown > 0 ? `Resend code in ${cooldown}s` : 'Resend code'}
            </button>
          </>
        )}

        {step === 'reset' && (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Choose a new password
              </h1>
            </div>
            {error && <Alert tone="danger" title={error} />}
            <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 14, textAlign: 'left' }}>
              <Field label="New password" hint="At least 8 characters">
                <Input type="password" placeholder="••••••••" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} />
              </Field>
              <Field label="Confirm password">
                <Input type="password" placeholder="••••••••" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} />
              </Field>
            </div>
            <Button size="lg" disabled={isSubmitting} onClick={submitNewPassword} style={{ width: '100%', justifyContent: 'center' }}>
              {isSubmitting ? 'CHANGING…' : 'CHANGE PASSWORD'}
            </Button>
          </>
        )}

        {step === 'done' && (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Password changed
              </h1>
              <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
                Your password has been changed. We&apos;ve also emailed you a confirmation.
              </p>
              <p style={{ fontSize: 13, color: 'var(--ink-400)', margin: 0 }}>
                Redirecting to log in…
              </p>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
