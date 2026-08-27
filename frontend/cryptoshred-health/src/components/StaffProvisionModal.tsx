import { useState, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation } from '@tanstack/react-query';
import {
  X,
  UserPlus,
  AlertCircle,
  KeyRound,
  Copy,
  Check,
  ShieldCheck,
  Stethoscope,
  FileCheck2,
  ShieldAlert,
  Mail,
  Lock,
} from 'lucide-react';
import apiClient from '../lib/axios';
import type { AdminUser, AdminUserRequest, Role } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function StaffProvisionModal({ isOpen, onClose, onSuccess }: Props) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Role>('DOCTOR');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [provisionedStaff, setProvisionedStaff] = useState<AdminUser | null>(null);

  const resetForm = () => {
    setEmail('');
    setRole('DOCTOR');
    setPassword('');
    setError('');
    setCopied(false);
    setProvisionedStaff(null);
  };

  const handleClose = () => {
    resetForm();
    onClose();
  };

  const handleFinish = () => {
    resetForm();
    onSuccess();
    onClose();
  };

  const mutation = useMutation({
    mutationFn: (data: AdminUserRequest) =>
      apiClient.post<AdminUser>('/admin/users', data).then((r) => r.data),
    onSuccess: (data) => {
      setError('');
      setProvisionedStaff(data);
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to provision staff account. Please try again.';
      setError(msg);
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate({
      email: email.trim(),
      role,
      password: password ? password : undefined,
    });
  };

  const handleCopyCredentials = async () => {
    if (!provisionedStaff) return;
    const textToCopy = provisionedStaff.temporaryPassword
      ? `CryptoShred Health Staff Credentials\nRole: ${provisionedStaff.role}\nEmail: ${provisionedStaff.email}\nTemporary Password: ${provisionedStaff.temporaryPassword}`
      : `CryptoShred Health Staff Credentials\nRole: ${provisionedStaff.role}\nEmail: ${provisionedStaff.email}`;
    try {
      await navigator.clipboard.writeText(textToCopy);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback
    }
  };

  if (!isOpen) return null;

  // Handover card view
  if (provisionedStaff) {
    return createPortal(
      <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
        <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-lg w-full overflow-hidden animate-fade-in">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200 bg-emerald-50/50">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
                <KeyRound className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-semibold text-slate-900">Staff Account Provisioned Successfully</h2>
                <p className="text-xs text-slate-500">Enterprise Access &amp; Role Provisioning</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleClose}
              className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Body */}
          <div className="p-6 space-y-5">
            <div className="rounded-xl bg-emerald-50 border border-emerald-200/80 p-4 text-xs text-emerald-800 flex items-start gap-2.5">
              <ShieldCheck className="h-4 w-4 shrink-0 mt-0.5 text-emerald-600" />
              <span>
                A new staff account has been provisioned. Provide these initial credentials to the clinician or staff member.
              </span>
            </div>

            <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/50 p-4">
              <div className="flex items-center justify-between text-xs">
                <div>
                  <span className="text-slate-400 font-medium">Assigned Role</span>
                  <div className="mt-1">
                    <span
                      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                        provisionedStaff.role === 'DOCTOR'
                          ? 'bg-blue-100 text-blue-800 border border-blue-200'
                          : provisionedStaff.role === 'AUDITOR'
                          ? 'bg-purple-100 text-purple-800 border border-purple-200'
                          : 'bg-amber-100 text-amber-800 border border-amber-200'
                      }`}
                    >
                      {provisionedStaff.role}
                    </span>
                  </div>
                </div>
                <div>
                  <span className="text-slate-400 font-medium">Account ID</span>
                  <p className="text-slate-700 font-mono text-xs mt-1">
                    {provisionedStaff.id.substring(0, 8)}...
                  </p>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-200/60">
                <span className="text-xs text-slate-400 font-medium">Staff Email</span>
                <p className="text-sm text-slate-900 font-medium mt-0.5">{provisionedStaff.email}</p>
              </div>

              {provisionedStaff.temporaryPassword ? (
                <div className="pt-2 border-t border-slate-200/60">
                  <div className="flex items-center justify-between mb-1.5">
                    <span className="text-xs font-semibold text-slate-700">Temporary Password</span>
                    <span className="text-[10px] text-amber-600 font-medium bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                      One-Time Handover
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      readOnly
                      value={provisionedStaff.temporaryPassword}
                      className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm font-mono font-bold text-slate-900 focus:outline-none select-all"
                    />
                    <button
                      type="button"
                      onClick={handleCopyCredentials}
                      className={`inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-semibold transition-all shrink-0 ${
                        copied
                          ? 'bg-emerald-600 text-white'
                          : 'bg-slate-900 hover:bg-slate-800 text-white shadow-sm'
                      }`}
                    >
                      {copied ? (
                        <>
                          <Check className="h-3.5 w-3.5" />
                          Copied
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" />
                          Copy Credentials
                        </>
                      )}
                    </button>
                  </div>
                </div>
              ) : (
                <div className="pt-2 border-t border-slate-200/60 flex items-center justify-between">
                  <span className="text-xs text-slate-500">Password: Configured manually during provisioning</span>
                  <button
                    type="button"
                    onClick={handleCopyCredentials}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium bg-slate-100 hover:bg-slate-200 text-slate-700 transition-colors"
                  >
                    {copied ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
                    {copied ? 'Copied' : 'Copy Email'}
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end">
            <button
              type="button"
              onClick={handleFinish}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-sm font-semibold transition-colors"
            >
              Done &amp; Close
            </button>
          </div>
        </div>
      </div>,
      document.body
    );
  }

  // Provisioning form view
  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-lg w-full overflow-hidden animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600 ring-1 ring-blue-200">
              <UserPlus className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Provision Staff Account</h2>
              <p className="text-xs text-slate-500">Create clinician or compliance staff credentials</p>
            </div>
          </div>
          <button
            type="button"
            onClick={handleClose}
            className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="p-6 space-y-5">
            {error && (
              <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* Role Selection */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-2">
                Staff Role <span className="text-red-500">*</span>
              </label>
              <div className="grid grid-cols-3 gap-2.5">
                <button
                  type="button"
                  onClick={() => setRole('DOCTOR')}
                  className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border text-center transition-all ${
                    role === 'DOCTOR'
                      ? 'border-blue-600 bg-blue-50/70 text-blue-950 font-semibold ring-2 ring-blue-500/20'
                      : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100/70 text-slate-700'
                  }`}
                >
                  <Stethoscope className={`h-5 w-5 ${role === 'DOCTOR' ? 'text-blue-600' : 'text-slate-400'}`} />
                  <span className="text-xs">Doctor</span>
                  <span className="text-[10px] text-slate-400 leading-tight">Clinical EHR</span>
                </button>

                <button
                  type="button"
                  onClick={() => setRole('AUDITOR')}
                  className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border text-center transition-all ${
                    role === 'AUDITOR'
                      ? 'border-purple-600 bg-purple-50/70 text-purple-950 font-semibold ring-2 ring-purple-500/20'
                      : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100/70 text-slate-700'
                  }`}
                >
                  <FileCheck2 className={`h-5 w-5 ${role === 'AUDITOR' ? 'text-purple-600' : 'text-slate-400'}`} />
                  <span className="text-xs">Auditor</span>
                  <span className="text-[10px] text-slate-400 leading-tight">Audit &amp; Merkle</span>
                </button>

                <button
                  type="button"
                  onClick={() => setRole('ADMIN')}
                  className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border text-center transition-all ${
                    role === 'ADMIN'
                      ? 'border-amber-600 bg-amber-50/70 text-amber-950 font-semibold ring-2 ring-amber-500/20'
                      : 'border-slate-200 bg-slate-50/50 hover:bg-slate-100/70 text-slate-700'
                  }`}
                >
                  <ShieldAlert className={`h-5 w-5 ${role === 'ADMIN' ? 'text-amber-600' : 'text-slate-400'}`} />
                  <span className="text-xs">Admin</span>
                  <span className="text-[10px] text-slate-400 leading-tight">Governance</span>
                </button>
              </div>
            </div>

            {/* Email */}
            <div>
              <label htmlFor="staff-email" className="block text-xs font-semibold text-slate-700 mb-1.5">
                Staff Email Address <span className="text-red-500">*</span>
              </label>
              <div className="relative">
                <Mail className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <input
                  id="staff-email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="doctor.smith@hospital.com"
                  className="w-full rounded-xl border border-slate-300 bg-white pl-10 pr-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="staff-password" className="block text-xs font-semibold text-slate-700">
                  Password
                </label>
                <span className="text-[11px] text-slate-400">Optional (auto-generated if left blank)</span>
              </div>
              <div className="relative">
                <Lock className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
                <input
                  id="staff-password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full rounded-xl border border-slate-300 bg-white pl-10 pr-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <p className="mt-1.5 text-[11px] text-slate-500">
                Leave empty to generate a high-entropy temporary password (e.g. <code>Care-8429!Blue</code>).
              </p>
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-3">
            <button
              type="button"
              onClick={handleClose}
              className="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending || !email.trim()}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              {mutation.isPending ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Provisioning...
                </>
              ) : (
                <>
                  <UserPlus className="h-4 w-4" />
                  Provision Staff Account
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
