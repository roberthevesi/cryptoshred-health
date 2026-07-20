import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShieldCheck,
  ClipboardList,
  ShieldAlert,
  LogOut,
  ChevronDown,
  Activity,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import PatientRecordTable from '../components/PatientRecordTable';
import DeletionProofCard from '../components/DeletionProofCard';
import apiClient from '../lib/axios';
import type { DeletionProof, PatientRecord } from '../types';

type Tab = 'records' | 'compliance';

const ROLE_BADGE: Record<string, string> = {
  DOCTOR: 'badge-role-doctor',
  PATIENT: 'badge-role-patient',
  AUDITOR: 'badge-role-auditor',
};

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<Tab>('records');
  const [selectedRecordId, setSelectedRecordId] = useState('');
  const [deletionProof, setDeletionProof] = useState<DeletionProof | null>(null);
  const [eraseError, setEraseError] = useState('');

  const { data: allRecords = [] } = useQuery<PatientRecord[]>({
    queryKey: ['records'],
    queryFn: () => apiClient.get<PatientRecord[]>('/records').then((r) => r.data),
  });

  const erasureMutation = useMutation({
    mutationFn: (recordId: string) =>
      apiClient.delete<DeletionProof>(`/erasure/${recordId}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      setEraseError('');
      queryClient.invalidateQueries({ queryKey: ['records'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Erasure failed. Please try again.';
      setEraseError(msg);
    },
  });

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleForgetMe = () => {
    if (!selectedRecordId) return;
    if (!confirm('This action is IRREVERSIBLE. The patient data and all attached PDFs will be permanently crypto-shredded. Proceed?')) return;
    setDeletionProof(null);
    erasureMutation.mutate(selectedRecordId);
  };

  const activeRecords = allRecords.filter((r) => !r.shredded);

  return (
    <div className="min-h-screen bg-surface">
      {/* Background grid */}
      <div
        className="pointer-events-none fixed inset-0"
        style={{
          backgroundImage: 'radial-gradient(circle at 1px 1px, rgba(148,163,184,0.06) 1px, transparent 0)',
          backgroundSize: '32px 32px',
        }}
      />

      {/* Top Nav */}
      <nav className="sticky top-0 z-40 border-b border-slate-800 bg-surface/90 backdrop-blur-md">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-600/20 ring-1 ring-brand-500/30">
              <ShieldCheck className="h-5 w-5 text-brand-400" />
            </div>
            <div>
              <span className="font-bold text-white">CryptoShred</span>
              <span className="text-brand-400 font-bold"> Health</span>
            </div>
          </div>

          <div className="hidden md:flex items-center gap-2 text-xs text-slate-400">
            <Activity className="h-3.5 w-3.5 text-success" />
            EHR Core &amp; Key Store Online
          </div>

          <div className="flex items-center gap-3">
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-white">{user?.email}</p>
              <span className={`${ROLE_BADGE[user?.role ?? '']} text-xs`}>{user?.role}</span>
            </div>
            <button id="logout-btn" onClick={handleLogout} className="btn-ghost gap-1.5" title="Sign out">
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </nav>

      {/* Main content */}
      <main className="mx-auto max-w-7xl px-6 py-8">
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-white">
            {user?.role === 'AUDITOR'
              ? 'Compliance & Audit Console'
              : user?.role === 'DOCTOR'
              ? 'Clinical EHR Dashboard'
              : 'My Health Records'}
          </h1>
          <p className="mt-1 text-slate-400 text-sm">
            Encrypted Health Records with Automated Crypto-Shredding Compliance
          </p>
        </div>

        {/* Tab bar */}
        <div className="mb-6 flex gap-1 rounded-xl bg-surface-card p-1 w-fit border border-slate-800">
          <button
            id="tab-records"
            onClick={() => setActiveTab('records')}
            className={`flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium transition-all duration-200 ${
              activeTab === 'records' ? 'bg-brand-600 text-white shadow-glow' : 'text-slate-400 hover:text-white'
            }`}
          >
            <ClipboardList className="h-4 w-4" />
            Records &amp; Attachments
          </button>
          {user?.role === 'AUDITOR' && (
            <button
              id="tab-compliance"
              onClick={() => setActiveTab('compliance')}
              className={`flex items-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium transition-all duration-200 ${
                activeTab === 'compliance' ? 'bg-danger text-white' : 'text-slate-400 hover:text-white'
              }`}
            >
              <ShieldAlert className="h-4 w-4" />
              Privacy &amp; Compliance (Right to be Forgotten)
            </button>
          )}
        </div>

        {/* Records tab */}
        {activeTab === 'records' && (
          <div className="glass-card p-6 animate-fade-in">
            <PatientRecordTable />
          </div>
        )}

        {/* Compliance tab (Auditor only) */}
        {activeTab === 'compliance' && user?.role === 'AUDITOR' && (
          <div className="space-y-6 animate-fade-in">
            <div className="rounded-2xl border border-amber-700/50 bg-amber-950/30 p-5 flex items-start gap-4">
              <ShieldAlert className="h-6 w-6 text-amber-400 shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-amber-300">Right to be Forgotten — Crypto-Shredding Engine</h3>
                <p className="mt-1 text-sm text-amber-400/80">
                  Executing data erasure will permanently zero-out the AES encryption key and nullify all stored PDF attachment payloads for the selected patient record.
                  The result is mathematically irrecoverable ciphertext, fulfilling GDPR Article 17 obligations.
                </p>
              </div>
            </div>

            <div className="glass-card p-6">
              <h2 className="text-lg font-semibold text-white mb-4 flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-danger" />
                Trigger Data &amp; PDF Erasure
              </h2>

              <div className="space-y-4">
                <div>
                  <label htmlFor="record-select" className="label">
                    Select Patient Record to Shred
                  </label>
                  <div className="relative">
                    <select
                      id="record-select"
                      value={selectedRecordId}
                      onChange={(e) => setSelectedRecordId(e.target.value)}
                      className="input-field appearance-none pr-10 bg-surface text-sm"
                    >
                      <option value="">— Choose an active record —</option>
                      {activeRecords.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.patientName} ({r.mrn || 'NO-MRN'}) — {r.diagnosis || 'General Examination'} [
                          {r.attachments?.length || 0} PDF(s)]
                        </option>
                      ))}
                    </select>
                    <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                  </div>
                </div>

                {eraseError && (
                  <div className="rounded-xl bg-red-900/30 border border-red-700/50 px-4 py-3 text-sm text-red-400">
                    {eraseError}
                  </div>
                )}

                <button
                  id="forget-me-btn"
                  onClick={handleForgetMe}
                  disabled={!selectedRecordId || erasureMutation.isPending}
                  className="btn-danger"
                >
                  {erasureMutation.isPending ? (
                    <div className="flex items-center gap-2">
                      <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                      <span>Shredding Key &amp; Files...</span>
                    </div>
                  ) : (
                    <>
                      <ShieldAlert className="h-4 w-4" /> Execute Right to be Forgotten
                    </>
                  )}
                </button>
              </div>
            </div>

            {deletionProof && <DeletionProofCard proof={deletionProof} />}
          </div>
        )}
      </main>
    </div>
  );
}
