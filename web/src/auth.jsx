import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { api, tokens } from './api.js';

const AuthContext = createContext(null);

export const uiFlags = { welcome: false };

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [subscription, setSubscription] = useState(null);
  const [ready, setReady] = useState(false);

  const loadMe = useCallback(async () => {
    const data = await api('/api/website/me');
    setUser(data.user);
    setSubscription(data.subscription);
    return data;
  }, []);

  useEffect(() => {
    if (!tokens.access && !tokens.refresh) {
      setReady(true);
      return;
    }
    loadMe()
      .catch(() => tokens.clear())
      .finally(() => setReady(true));
  }, [loadMe]);

  const setSession = useCallback((data) => {
    tokens.set(data);
    if (data?.user) setUser(data.user);
    if (data?.subscription) setSubscription(data.subscription);
  }, []);

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', {
        method: 'POST',
        body: { refresh_token: tokens.refresh },
        auth: false,
      });
    } catch {
      // сессия могла уже истечь — выходим локально
    }
    tokens.clear();
    uiFlags.welcome = false;
    setUser(null);
    setSubscription(null);
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, subscription, ready, setSession, logout, loadMe, setSubscription }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
