import { useEffect, useState } from 'react';
import { clearTokens, getAccessToken, isTokenValid } from '../utils/tokenUtils';

export function useAuthState() {
  const [loggedIn, setLoggedIn] = useState(false);

  useEffect(() => {
    setLoggedIn(isTokenValid(getAccessToken()));
  }, []);

  const logout = () => {
    clearTokens();
    window.location.href = '/';
  };

  return { loggedIn, logout };
}
