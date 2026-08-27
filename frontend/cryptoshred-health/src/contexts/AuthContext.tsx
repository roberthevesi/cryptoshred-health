import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { AuthUser, AuthResponse, Role } from '../types';
import apiClient from '../lib/axios';

interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Rehydrate from localStorage on mount
  useEffect(() => {
    const stored = localStorage.getItem('auth_user');
    const token  = localStorage.getItem('auth_token');
    if (stored && token) {
      try {
        const parsed = JSON.parse(stored) as AuthUser;
        setUser({ ...parsed, token });
      } catch {
        localStorage.removeItem('auth_user');
        localStorage.removeItem('auth_token');
      }
    }
    setIsLoading(false);
  }, []);

  // NOTE: Storing JWT tokens in localStorage is susceptible to XSS. In production, use httpOnly Secure SameSite cookies for session tokens.
  const persist = (data: AuthResponse) => {
    const authUser: AuthUser = { email: data.email, role: data.role, token: data.token };
    localStorage.setItem('auth_token', data.token);
    localStorage.setItem('auth_user', JSON.stringify(authUser));
    setUser(authUser);
  };

  const login = useCallback(async (email: string, password: string) => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', { email, password });
    persist(data);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_user');
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
