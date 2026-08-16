import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShieldCheck,
  ClipboardList,
  ShieldAlert,
  LogOut,
  ChevronDown,
  Activity,
  FileCheck2,
  Stethoscope,
  Users,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import PatientCensusTable from '../components/PatientCensusTable';
import PatientRecordTable from '../components/PatientRecordTable';
import DeletionProofCard from '../components/DeletionProofCard';
import VerifyProofModal from '../components/VerifyProofModal';
import GpManagementPanel from '../components/GpManagementPanel';
import apiClient from '../lib/axios';
import type { DeletionProof, PatientRecord, Patient } from '../types';

type Tab = 'patients' | 'records' | 'gp-directory' | 'compliance';

const ROLE_BADGE: Record<string, string> = {
  DOCTOR: 'badge-role-doctor',
  PATIENT: 'badge-role-patient',
  AUDITOR: 'badge-role-auditor',
};

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<Tab>('patients');
  const [selectedRecordId, setSelectedRecordId] = useState('');
  const [deletionProof, setDeletionProof] = useState<DeletionProof | null>(null);
  const [eraseError, setEraseError] = useState('');
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);

  // Fetch Patients count
  const { data: patients = [] } = useQuery<Patient[]>({
    queryKey: ['patients'],
    queryFn: () => apiClient.get<Patient[]>('/patients').then((r) => r.data),
  });

  // Fetch Clinical Encounters
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
      queryClient.invalidateQueries({ queryKey: ['patients'] });
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
    <div className="min-h-screen bg-slate-50">
      {/* Top Nav */}
      <nav className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 ring-1 ring-blue-200">
              <ShieldCheck className="h-5 w-5 text-blue-600" />
            </div>
            <div>
              <span className="font-bold text-slate-900">CryptoShred</span>
              <span className="text-blue-600 font-bold"> Health</span>
            </div>
          </div>

          <div className="hidden md:flex items-center gap-2 text-xs text-slate-500">
            <Activity className="h-3.5 w-3.5 text-emerald-500" />
            EHR Core &amp; Vault KMS Key Store Online
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsVerifyModalOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 text-xs font-medium transition"
            >
              <FileCheck2 className="h-3.5 w-3.5 text-emerald-600" />
              Verify Proof Artifact
            </button>
            <div className="text-right hidden sm:block">
              <p className="text-sm font-medium text-slate-900">{user?.email}</p>
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
        <div className="mb-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl sm:text-3xl font-bold text-slate-900 tracking-tight">
              {user?.role === 'AUDITOR'
                ? 'Compliance & Audit Console'
                : user?.role === 'DOCTOR'
                ? 'Primary Care Clinical Census'
                : 'My Health Records'}
            </h1>
            <p className="mt-1 text-slate-500 text-xs sm:text-sm">
              Enterprise Electronic Health Record System with Vault KMS Crypto-Shredding
            </p>
          </div>

          {/* Quick System Metric Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-xs">
            <div className="rounded-xl bg-white border border-slate-200 px-3.5 py-2">
              <span className="text-[10px] uppercase font-semibold text-slate-500 block">Registered Patients</span>
              <span className="font-mono text-base font-bold text-slate-900">{patients.length}</span>
            </div>
            <div className="rounded-xl bg-white border-l-4 border-l-emerald-500 border border-slate-200 px-3.5 py-2">
              <span className="text-[10px] uppercase font-semibold text-emerald-600 block">Active Encounters</span>
              <span className="font-mono text-base font-bold text-emerald-700">{activeRecords.length}</span>
            </div>
            <div className="rounded-xl bg-white border-l-4 border-l-blue-500 border border-slate-200 px-3.5 py-2">
              <span className="text-[10px] uppercase font-semibold text-blue-600 block">Encrypted PDFs</span>
              <span className="font-mono text-base font-bold text-blue-700">
                {allRecords.reduce((acc, r) => acc + (r.attachments?.length || 0), 0)}
              </span>
            </div>
            <div className="rounded-xl bg-white border-l-4 border-l-red-500 border border-slate-200 px-3.5 py-2">
              <span className="text-[10px] uppercase font-semibold text-red-600 block">Crypto-Shredded</span>
              <span className="font-mono text-base font-bold text-red-700">
                {allRecords.filter((r) => r.shredded).length}
              </span>
            </div>
          </div>
        </div>

        {/* Tab bar */}
        <div className="mb-6 flex flex-wrap gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
          <button
            id="tab-patients"
            onClick={() => setActiveTab('patients')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2.5 transition-all duration-200 ${
              activeTab === 'patients' ? 'bg-white text-slate-900 font-semibold shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Users className="h-4 w-4 text-blue-600" />
            Patient Census &amp; Directory
          </button>
          <button
            id="tab-records"
            onClick={() => setActiveTab('records')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2.5 transition-all duration-200 ${
              activeTab === 'records' ? 'bg-white text-slate-900 font-semibold shadow-sm' : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ClipboardList className="h-4 w-4 text-indigo-600" />
            All Clinical Encounters
          </button>
          {(user?.role === 'DOCTOR' || user?.role === 'AUDITOR') && (
            <button
              id="tab-gp-directory"
              onClick={() => setActiveTab('gp-directory')}
              className={`flex items-center gap-2 rounded-lg px-4 py-2.5 transition-all duration-200 ${
                activeTab === 'gp-directory' ? 'bg-white text-slate-900 font-semibold shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Stethoscope className="h-4 w-4 text-blue-600" />
              GP Directory
            </button>
          )}
          {user?.role === 'AUDITOR' && (
            <button
              id="tab-compliance"
              onClick={() => setActiveTab('compliance')}
              className={`flex items-center gap-2 rounded-lg px-4 py-2.5 transition-all duration-200 ${
                activeTab === 'compliance' ? 'bg-white text-red-700 font-semibold shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldAlert className="h-4 w-4 text-red-600" />
              Privacy &amp; Compliance
            </button>
          )}
        </div>

        {/* Patients Tab (Primary Patient-First view) */}
        {activeTab === 'patients' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
            <PatientCensusTable />
          </div>
        )}

        {/* Records tab */}
        {activeTab === 'records' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
            <PatientRecordTable />
          </div>
        )}

        {/* GP Directory tab */}
        {activeTab === 'gp-directory' && (user?.role === 'DOCTOR' || user?.role === 'AUDITOR') && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
            <GpManagementPanel />
          </div>
        )}

        {/* Compliance tab (Auditor only) */}
        {activeTab === 'compliance' && user?.role === 'AUDITOR' && (
          <div className="space-y-6 animate-fade-in">
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 flex items-start gap-4">
              <ShieldAlert className="h-6 w-6 text-amber-600 shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-amber-900">Right to be Forgotten — Crypto-Shredding Engine</h3>
                <p className="mt-1 text-sm text-amber-700">
                  Executing data erasure will permanently zero-out the AES encryption key and nullify all stored PDF attachment payloads for the selected patient record.
                  The result is mathematically irrecoverable ciphertext, fulfilling GDPR Article 17 obligations.
                </p>
              </div>
            </div>

            <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6">
              <h2 className="text-lg font-semibold text-slate-900 mb-4 flex items-center gap-2">
                <ShieldAlert className="h-5 w-5 text-red-600" />
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
                      className="input-field appearance-none pr-10 text-sm"
                    >
                      <option value="">— Choose an active record —</option>
                      {activeRecords.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.patientName} ({r.mrn || 'NO-MRN'}) — {r.diagnosis || 'General Examination'} [
                          {r.attachments?.length || 0} PDF(s)]
                        </option>
                      ))}
                    </select>
                    <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  </div>
                </div>

                {eraseError && (
                  <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
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

      <VerifyProofModal
        isOpen={isVerifyModalOpen}
        onClose={() => setIsVerifyModalOpen(false)}
        token={user?.token || ''}
      />
    </div>
  );
}
