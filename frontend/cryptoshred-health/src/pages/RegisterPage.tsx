import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { ShieldCheck, Mail, Lock, UserCircle, AlertCircle } from 'lucide-react';
import type { Role } from '../types';

const ROLES: { value: Role; label: string; description: string }[] = [
  { value: 'DOCTOR',  label: 'Doctor',  description: 'Create and manage patient records' },
  { value: 'PATIENT', label: 'Patient', description: 'View your own health records' },
  { value: 'AUDITOR', label: 'Auditor', description: 'Compliance and right-to-be-forgotten' },
];

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [email,     setEmail]     = useState('');
  const [password,  setPassword]  = useState('');
  const [role,      setRole]      = useState<Role>('DOCTOR');
  const [error,     setError]     = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);
    try {
      await register(email, password, role);
      navigate('/dashboard');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Registration failed. Please try again.';
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center px-4 py-12">
      <div className="relative w-full max-w-lg animate-slide-up">
        {/* Logo */}
        <div className="mb-8 flex flex-col items-center gap-3">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50 ring-1 ring-blue-200">
            <ShieldCheck className="h-8 w-8 text-blue-600" />
          </div>
          <div className="text-center">
            <h1 className="text-2xl font-bold text-slate-900">Create Account</h1>
            <p className="mt-1 text-sm text-slate-500">Join the secure health platform</p>
          </div>
        </div>

        {/* Card */}
        <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-8">
          {error && (
            <div className="mb-5 flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div>
              <label htmlFor="reg-email" className="label">Email address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  id="reg-email"
                  type="email"
                  required
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                  placeholder="you@hospital.com"
                  className="input-field pl-10"
                />
              </div>
            </div>

            <div>
              <label htmlFor="reg-password" className="label">Password <span className="text-slate-400 font-normal">(min 8 chars)</span></label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  id="reg-password"
                  type="password"
                  required
                  minLength={8}
                  value={password}
                  onChange={e => setPassword(e.target.value)}
                  placeholder="••••••••"
                  className="input-field pl-10"
                />
              </div>
            </div>

            {/* Role selector */}
            <div>
              <label className="label">
                <UserCircle className="inline h-4 w-4 mb-0.5 mr-1" />
                Select Role
              </label>
              <div className="grid grid-cols-1 gap-3">
                {ROLES.map(r => (
                  <label
                    key={r.value}
                    htmlFor={`role-${r.value}`}
                    className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition-all duration-200 ${
                      role === r.value
                        ? 'border-blue-500 bg-blue-50/60 ring-1 ring-blue-500'
                        : 'border-slate-200 hover:border-slate-300 bg-white'
                    }`}
                  >
                    <input
                      type="radio"
                      id={`role-${r.value}`}
                      name="role"
                      value={r.value}
                      checked={role === r.value}
                      onChange={() => setRole(r.value)}
                      className="mt-1 accent-blue-600"
                    />
                    <div>
                      <p className="font-semibold text-slate-900 text-sm">{r.label}</p>
                      <p className="text-xs text-slate-500 mt-0.5">{r.description}</p>
                    </div>
                  </label>
                ))}
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              id="register-submit"
              className="btn-primary w-full mt-2"
            >
              {isLoading ? (
                <><div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" /> Creating account...</>
              ) : 'Create Account'}
            </button>
          </form>

          <p className="mt-6 text-center text-sm text-slate-500">
            Already have an account?{' '}
            <Link to="/login" className="text-blue-600 hover:text-blue-500 font-medium transition-colors">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
}
