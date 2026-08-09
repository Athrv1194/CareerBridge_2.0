import { Component } from 'react';

export default class ErrorBoundary extends Component {
  state = { error: null };

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('Unhandled render error:', error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return (
      <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#F5F3EF', color: '#2A2927', fontFamily: 'Inter, Helvetica Neue, Arial, sans-serif', padding: 24, boxSizing: 'border-box' }}>
        <div style={{ maxWidth: 420, textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 14, alignItems: 'center' }}>
          <span style={{ fontSize: 20, fontWeight: 600 }}>Something went wrong</span>
          <p style={{ fontSize: 14, lineHeight: 1.6, color: '#6B6863', margin: 0 }}>
            This page hit an unexpected error. Reloading usually fixes it — if it keeps happening, let us know what you were doing.
          </p>
          <button
            type="button"
            onClick={() => { this.setState({ error: null }); window.location.reload(); }}
            style={{ padding: '10px 22px', background: '#2A2927', color: '#FCFBF9', border: 0, borderRadius: 4, fontSize: 14, cursor: 'pointer' }}
          >
            Reload page
          </button>
        </div>
      </div>
    );
  }
}
