import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../../api/authApi';
import {
  Alert, Button, Field, Icon, Input, Logo,
} from '../../components/ui';

const REDIRECT_DELAY_MS = 2500;

// Invite activation reuses the forgot-password reset endpoint, just pre-filled from the link.
export default function SetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const email = searchParams.get('email') || '';

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    if (!done) return undefined;
    const id = setTimeout(() => navigate('/login'), REDIRECT_DELAY_MS);
    return () => clearTimeout(id);
  }, [done, navigate]);

  const onSubmit = async () => {
    setError(null);
    if (!token || !email) {
      setError('This link is missing information. Please use the link from your email exactly as sent.');
      return;
    }
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
        email, resetToken: token, newPassword, confirmPassword,
      });
      setDone(true);
    } catch (e) {
      setError(e.message || 'That link is invalid or has expired.');
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
        {!done ? (
          <>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Set your password
              </h1>
              <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
                {email ? <>Finish setting up <strong>{email}</strong></> : 'Complete your account setup'}
              </p>
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
            <Button size="lg" disabled={isSubmitting} onClick={onSubmit} style={{ width: '100%', justifyContent: 'center' }}>
              {isSubmitting ? 'SAVING…' : 'SET PASSWORD'}
            </Button>
          </>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 28, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
              You&apos;re all set
            </h1>
            <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
              Your password has been set. Redirecting to log in…
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
