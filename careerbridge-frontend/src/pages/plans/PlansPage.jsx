import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Badge, Button, Icon, IconButton, Logo, Skeleton } from '../../components/ui';
import { getPlans, createOrder, verifyPayment, getMySubscription } from '../../api/paymentApi';
import { getUnreadCount } from '../../api/notificationApi';

const NAV_ITEMS = [
  { icon: 'sun', label: 'Dashboard', to: '/dashboard' },
  { icon: 'file-text', label: 'Assessment', to: '/assessment' },
  { icon: 'sparkles', label: 'Recommendations', to: '/recommendations' },
  { icon: 'route', label: 'Roadmap', to: '/roadmap' },
  { icon: 'briefcase', label: 'Opportunities', to: '/opportunities' },
  { icon: 'download', label: 'Résumé', to: '/resume' },
  { icon: 'sparkles', label: 'Coach', to: '/coach' },
  { icon: 'user', label: 'Profile', to: '/profile' },
];

// Loads Razorpay's Checkout.js once and reuses it on repeat visits.
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(true);
  return new Promise((resolve) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

export default function PlansPage() {
  const navigate = useNavigate();
  const [unreadCount, setUnreadCount] = useState(0);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [subscription, setSubscription] = useState(null);
  const [payingPlanId, setPayingPlanId] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getUnreadCount().then((r) => setUnreadCount(r?.unreadCount || 0)).catch(() => {});
    Promise.all([getPlans(), getMySubscription().catch(() => null)])
      .then(([p, sub]) => { setPlans(p); setSubscription(sub); })
      .finally(() => setLoading(false));
  }, []);

  async function handleUpgrade(plan) {
    setError('');
    if (plan.price === 0) return;
    setPayingPlanId(plan.id);
    try {
      const [order, ready] = await Promise.all([createOrder(plan.id), loadRazorpay()]);
      if (!ready) throw new Error('Unable to load the payment window. Check your connection and try again.');

      const rzp = new window.Razorpay({
        key: order.keyId,
        amount: order.amountPaise,
        currency: order.currency,
        name: 'CareerBridge',
        description: order.planName,
        order_id: order.razorpayOrderId,
        handler: async (response) => {
          try {
            await verifyPayment({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });
            const sub = await getMySubscription().catch(() => null);
            setSubscription(sub);
          } catch (e) {
            setError(e.message || 'Payment succeeded but verification failed. Contact support.');
          } finally {
            setPayingPlanId(null);
          }
        },
        modal: { ondismiss: () => setPayingPlanId(null) },
        theme: { color: '#1a1a1a' },
      });
      rzp.on('payment.failed', (resp) => {
        setError(resp?.error?.description || 'Payment failed. Please try again.');
        setPayingPlanId(null);
      });
      rzp.open();
    } catch (e) {
      setError(e.message || 'Unable to start checkout. Please try again.');
      setPayingPlanId(null);
    }
  }

  return (
    <div style={{ minHeight: '100vh', background: 'var(--surface-page)', color: 'var(--ink-800)', fontFamily: 'var(--font-sans)' }}>
      <header style={{ position: 'sticky', top: 0, zIndex: 40, height: 64, background: 'var(--surface-page)', borderBottom: '1px solid var(--line-hairline)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 20, padding: '0 28px', boxSizing: 'border-box' }}>
        <Logo size={32} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 18 }}>
          <div style={{ position: 'relative', display: 'flex' }}>
            <IconButton icon="bell" label="Notifications" onClick={() => navigate('/notifications')} />
            {unreadCount > 0 && (
              <span style={{ position: 'absolute', top: 1, right: 1, minWidth: 15, height: 15, padding: '0 3px', borderRadius: '50%', background: 'var(--status-danger)', color: '#FCFBF9', fontSize: 9, fontWeight: 600, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                {unreadCount > 9 ? '9+' : unreadCount}
              </span>
            )}
          </div>
        </div>
      </header>

      <div style={{ minHeight: 'calc(100vh - 64px)', display: 'block' }}>

        <main style={{ padding: '40px 32px', maxWidth: 1040, margin: '0 auto', width: '100%', boxSizing: 'border-box' }}>
          <h1 style={{ fontSize: 28, fontWeight: 600, color: 'var(--ink-900)', margin: '0 0 6px' }}>Plans</h1>
          <p style={{ fontSize: 14, color: 'var(--ink-600)', margin: '0 0 28px' }}>
            {subscription?.active ? `You're on ${subscription.planName}.` : 'Pick a plan to unlock more of CareerBridge.'}
          </p>

          {error && <div style={{ marginBottom: 20 }}><Alert tone="danger" title="Payment error" message={error} /></div>}

          {loading ? (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20 }}>
              {[1, 2, 3, 4].map((i) => <Skeleton key={i} height={280} />)}
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 20 }}>
              {plans.map((plan) => {
                const isCurrent = subscription?.active && subscription.planName === plan.planName;
                const isFree = plan.price === 0;
                return (
                  <div key={plan.id} style={{ border: '1px solid var(--line-hairline)', borderRadius: 'var(--radius-md)', padding: 24, display: 'flex', flexDirection: 'column', gap: 14, background: 'var(--surface-card, #fff)' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: 15, fontWeight: 600, color: 'var(--ink-900)' }}>{plan.planName.replace(/_/g, ' ')}</span>
                      {isCurrent && <Badge tone="success">Current</Badge>}
                    </div>
                    <div>
                      <span style={{ fontSize: 26, fontWeight: 600, color: 'var(--ink-900)' }}>₹{plan.price}</span>
                      {!isFree && <span style={{ fontSize: 12, color: 'var(--ink-400)' }}> /{plan.billingCycle?.toLowerCase()}</span>}
                    </div>
                    <p style={{ fontSize: 13, color: 'var(--ink-600)', margin: 0, minHeight: 36 }}>{plan.description}</p>
                    <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8, flex: 1 }}>
                      {(plan.features || []).map((f) => (
                        <li key={f} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-700)' }}>
                          <Icon name="check" size={14} style={{ color: 'var(--status-success)', flexShrink: 0 }} />
                          {f}
                        </li>
                      ))}
                    </ul>
                    <Button
                      variant={isCurrent || isFree ? 'quiet' : 'primary'}
                      fullWidth
                      disabled={isCurrent || isFree || payingPlanId === plan.id}
                      onClick={() => handleUpgrade(plan)}
                    >
                      {isCurrent ? 'Current plan' : isFree ? 'Included' : payingPlanId === plan.id ? 'Opening…' : `Upgrade to ${plan.planName.replace(/_/g, ' ')}`}
                    </Button>
                  </div>
                );
              })}
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
