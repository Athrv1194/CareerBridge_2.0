import { useEffect, useState } from 'react';

// The same breakpoint spine the stylesheets use (src/styles/responsive.css).
// Anything expressible in CSS should be a media query in the page's own stylesheet --
// this hook is only for layout that genuinely needs JS: drawer open/close state, and
// swapping which elements render at all rather than just how they're styled.
export const BREAKPOINTS = {
  micro: 380,   // Galaxy Z Fold 5 folded (344), Galaxy S8+ (360), iPhone SE (375)
  phone: 560,   // iPhone 12-16 (390-440), Pixel 7-10 (411-412), Surface Duo (540)
  tablet: 900,  // iPad Mini (768), iPad Air (820), Asus Zenbook Fold (853)
  compact: 1180, // Z Fold 5 open (904), Surface Pro 7 (912), iPad Pro / Nest Hub (1024)
};

function match(maxWidth) {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia(`(max-width: ${maxWidth}px)`).matches;
}

/**
 * Subscribes to one max-width media query.
 * Pass a key from BREAKPOINTS, or a raw pixel number for a one-off.
 */
export function useMaxWidth(breakpoint) {
  const px = typeof breakpoint === 'number' ? breakpoint : BREAKPOINTS[breakpoint];
  const [matches, setMatches] = useState(() => match(px));

  useEffect(() => {
    const mq = window.matchMedia(`(max-width: ${px}px)`);
    const update = (e) => setMatches(e.matches);
    // Read once on mount too: the initial useState ran before any resize that may have
    // happened during hydration, and a rotated phone would otherwise render the wrong
    // layout until the next resize event.
    setMatches(mq.matches);
    mq.addEventListener('change', update);
    return () => mq.removeEventListener('change', update);
  }, [px]);

  return matches;
}

/**
 * All four bands at once, for components that branch on more than one.
 * Each flag is cumulative: a 344px screen is isMicro AND isPhone AND isTablet.
 */
export function useBreakpoint() {
  const isMicro = useMaxWidth('micro');
  const isPhone = useMaxWidth('phone');
  const isTablet = useMaxWidth('tablet');
  const isCompact = useMaxWidth('compact');
  return { isMicro, isPhone, isTablet, isCompact };
}
