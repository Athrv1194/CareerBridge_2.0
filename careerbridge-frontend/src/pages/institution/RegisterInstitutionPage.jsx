import { useState } from 'react';
import { Link } from 'react-router-dom';
import { submitInstitutionRequest } from '../../api/organizationApi';
import {
  Alert, Button, Checkbox, Field, Icon, Input, Logo, Select,
} from '../../components/ui';
import '../auth/register.css';

const INSTITUTION_TYPES = ['ENGINEERING_COLLEGE', 'POLYTECHNIC', 'UNIVERSITY'];

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^[+]?[0-9 ()-]{7,20}$/;

function validate({
  institutionName, institutionCode, contactPersonName, contactEmail, contactPhone, agreed,
}) {
  const errors = {};
  if (!institutionName.trim()) errors.institutionName = 'Institution name is required';
  if (!institutionCode.trim()) errors.institutionCode = 'Institution code is required';
  if (!contactPersonName.trim()) errors.contactPersonName = 'Contact person name is required';
  if (!contactEmail.trim()) errors.contactEmail = 'Contact email is required';
  else if (!EMAIL_RE.test(contactEmail)) errors.contactEmail = 'Enter a valid email';
  if (!contactPhone.trim()) errors.contactPhone = 'Contact phone is required';
  else if (!PHONE_RE.test(contactPhone)) errors.contactPhone = 'Enter a valid phone number';
  if (!agreed) errors.agreed = 'You must accept the terms';
  return errors;
}

export default function RegisterInstitutionPage() {
  const [institutionName, setInstitutionName] = useState('');
  const [institutionCode, setInstitutionCode] = useState('');
  const [organizationType, setOrganizationType] = useState(INSTITUTION_TYPES[0]);
  const [contactPersonName, setContactPersonName] = useState('');
  const [contactEmail, setContactEmail] = useState('');
  const [contactPhone, setContactPhone] = useState('');
  const [websiteDomain, setWebsiteDomain] = useState('');
  const [address, setAddress] = useState('');
  const [city, setCity] = useState('');
  const [state, setState] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState({});
  const [requestError, setRequestError] = useState(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const onSubmit = async () => {
    const nextErrors = validate({
      institutionName, institutionCode, contactPersonName, contactEmail, contactPhone, agreed,
    });
    setErrors(nextErrors);
    setRequestError(null);
    if (Object.keys(nextErrors).length > 0) return;

    setIsSubmitting(true);
    try {
      await submitInstitutionRequest({
        institutionName,
        institutionCode,
        organizationType,
        contactPersonName,
        contactEmail,
        contactPhone,
        websiteDomain: websiteDomain || undefined,
        address: address || undefined,
        city: city || undefined,
        state: state || undefined,
      });
      setSubmitted(true);
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
            Bring CareerBridge to your <i>campus</i>.
          </span>
          <p style={{ fontSize: 15, lineHeight: 1.6, color: 'var(--bone-300)', margin: 0 }}>
            A tenant-scoped dashboard, PRS leaderboards and placement stats for your institution &mdash; reviewed and provisioned by our team.
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

        {submitted ? (
          <div style={{ width: '100%', maxWidth: 440, display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Application received</span>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Thanks &mdash; we&apos;re reviewing it.
              </h1>
            </div>
            <Alert
              tone="info"
              title="Application under review by the CareerBridge team."
              message="You'll get an email with a link to set your password once your institution is approved. This usually takes 1&ndash;2 business days."
            />
            <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
              <Link to="/">Return home</Link>
            </p>
          </div>
        ) : (
          <div style={{ width: '100%', maxWidth: 440, display: 'flex', flexDirection: 'column', gap: 20 }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
              <span style={{ fontSize: 11, fontWeight: 500, letterSpacing: '.14em', textTransform: 'uppercase', color: 'var(--taupe-700)' }}>Register your institution</span>
              <h1 style={{ fontFamily: 'var(--font-display)', fontSize: 32, lineHeight: 1.1, letterSpacing: '-.015em', color: 'var(--ink-900)', margin: 0, fontWeight: 400 }}>
                Apply to join CareerBridge.
              </h1>
              <p style={{ fontSize: 14, lineHeight: 1.6, color: 'var(--ink-600)', margin: 0 }}>
                Our team reviews every application before your admin account is provisioned.
              </p>
            </div>

            {requestError && <Alert tone="danger" title={requestError} />}

            <div className="cb-reg-fields" style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 14 }}>
              <Field label="Institution name" error={errors.institutionName}>
                <Input placeholder="COEP Technological University" value={institutionName} onChange={(e) => setInstitutionName(e.target.value)} error={errors.institutionName} />
              </Field>
              <Field label="Institution code" error={errors.institutionCode}>
                <Input placeholder="COEP" value={institutionCode} onChange={(e) => setInstitutionCode(e.target.value)} error={errors.institutionCode} />
              </Field>
            </div>

            <Field label="Institution type">
              <Select options={INSTITUTION_TYPES} value={organizationType} onChange={(e) => setOrganizationType(e.target.value)} style={{ width: '100%' }} />
            </Field>

            <div className="cb-reg-fields" style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 14 }}>
              <Field label="Contact person (TPO / Dean)" error={errors.contactPersonName}>
                <Input placeholder="Prof. S. K. Sharma" value={contactPersonName} onChange={(e) => setContactPersonName(e.target.value)} error={errors.contactPersonName} />
              </Field>
              <Field label="Contact phone" error={errors.contactPhone}>
                <Input placeholder="+91 98765 43210" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} error={errors.contactPhone} />
              </Field>
            </div>

            <Field label="Contact email" error={errors.contactEmail}>
              <Input type="email" placeholder="tpo@college.edu" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} error={errors.contactEmail} />
            </Field>

            <Field label="Website" hint="Optional">
              <Input placeholder="https://college.edu" value={websiteDomain} onChange={(e) => setWebsiteDomain(e.target.value)} />
            </Field>

            <Field label="Address" hint="Optional">
              <Input placeholder="Street address" value={address} onChange={(e) => setAddress(e.target.value)} />
            </Field>

            <div className="cb-reg-fields" style={{ display: 'grid', gridTemplateColumns: 'repeat(2,minmax(0,1fr))', gap: 14 }}>
              <Field label="City" hint="Optional">
                <Input placeholder="Pune" value={city} onChange={(e) => setCity(e.target.value)} />
              </Field>
              <Field label="State" hint="Optional">
                <Input placeholder="Maharashtra" value={state} onChange={(e) => setState(e.target.value)} />
              </Field>
            </div>

            <Checkbox label="I confirm I am authorized to apply on behalf of this institution" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />
            {errors.agreed && <span style={{ fontSize: 12, color: 'var(--status-danger)', marginTop: -14 }}>{errors.agreed}</span>}

            <Button
              size="lg"
              iconAfter={isSubmitting ? undefined : 'arrow-right'}
              disabled={isSubmitting}
              onClick={onSubmit}
              style={{ width: '100%', justifyContent: 'center' }}
            >
              {isSubmitting ? 'SUBMITTING…' : 'SUBMIT APPLICATION'}
            </Button>

            <p style={{ textAlign: 'center', fontSize: 14, color: 'var(--ink-600)', margin: 0 }}>
              Already onboarded? <a href="/login">Log in</a>
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
