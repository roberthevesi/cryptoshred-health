import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ShieldCheck,
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
import DeletionProofCard from '../components/DeletionProofCard';
import VerifyProofModal from '../components/VerifyProofModal';
import GpManagementPanel from '../components/GpManagementPanel';
import apiClient from '../lib/axios';
import type { DeletionProof, PatientVisit, Patient } from '../types';

type Tab = 'patients' | 'gp-directory' | 'compliance';

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
  const [selectedPatientId, setSelectedPatientId] = useState('');
  const [selectedVisitId, setSelectedVisitId] = useState('');
  const [deletionProof, setDeletionProof] = useState<DeletionProof | null>(null);
  const [eraseError, setEraseError] = useState('');
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);

  // Fetch Patients
  const { data: patients = [] } = useQuery<Patient[]>({
    queryKey: ['patients'],
    queryFn: () => apiClient.get<Patient[]>('/patients?includeDeleted=true').then((r) => r.data),
  });

  // Fetch Clinical Visits only when Auditor opens the Compliance Tab
  const { data: allVisits = [] } = useQuery<PatientVisit[]>({
    queryKey: ['visits'],
    queryFn: () => apiClient.get<PatientVisit[]>('/visits').then((r) => r.data),
    enabled: activeTab === 'compliance' && user?.role === 'AUDITOR',
  });

  // Patient-level erasure mutation
  const patientErasureMutation = useMutation({
    mutationFn: (patientId: string) =>
      apiClient.delete<DeletionProof>(`/erasure/patients/${patientId}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      setEraseError('');
      setSelectedPatientId('');
      queryClient.invalidateQueries({ queryKey: ['visits'] });
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Patient erasure failed. Please verify auditor permissions.';
      setEraseError(msg);
    },
  });

  // Visit-level erasure mutation
  const visitErasureMutation = useMutation({
    mutationFn: (visitId: string) =>
      apiClient.delete<DeletionProof>(`/erasure/visits/${visitId}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      setEraseError('');
      setSelectedVisitId('');
      queryClient.invalidateQueries({ queryKey: ['visits'] });
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Visit erasure failed. Please try again.';
      setEraseError(msg);
    },
  });

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const handleForgetPatient = () => {
    if (!selectedPatientId) return;
    if (!confirm(`Are you certain you want to crypto-shred Patient ${selectedPatientId}? All demographics and clinical visit keys will be permanently destroyed across all storage layers.`)) return;
    setDeletionProof(null);
    patientErasureMutation.mutate(selectedPatientId);
  };

  const handleForgetVisit = () => {
    if (!selectedVisitId) return;
    if (!confirm('This action is IRREVERSIBLE. The clinical visit and all attached files will be permanently crypto-shredded. Proceed?')) return;
    setDeletionProof(null);
    visitErasureMutation.mutate(selectedVisitId);
  };

  const activePatients = patients.filter((p) => p.isActive !== false && !p.shredded);
  const activeVisits = allVisits.filter((v) => !v.shredded);
  const totalVisitsCount = patients.reduce((sum, p) => sum + (p.visitCount || 0), 0);
  const isPending = patientErasureMutation.isPending || visitErasureMutation.isPending;

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
            <button id="logout-btn" onClick={handleLogout} className="btn-ghost gap-1.5" title="Sign out" aria-label="Sign out">
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </nav>

      {/* Hero Stats */}
      <div className="border-b border-slate-200 bg-white py-6">
        <div className="mx-auto max-w-7xl px-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-slate-900">Clinical Dashboard &amp; Patient Census</h1>
              <p className="text-xs text-slate-500 mt-1">
                Zero-Knowledge EHR with Cryptographic Right-to-be-Forgotten (GDPR Article 17 Compliance)
              </p>
            </div>

            <div className="flex items-center gap-3">
              <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-center">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Registered Patients
                </span>
                <span className="text-lg font-bold text-slate-900">{patients.length}</span>
              </div>
              <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-center">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                  Total Visits
                </span>
                <span className="text-lg font-bold text-blue-600">{totalVisitsCount}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        {/* Navigation Tabs */}
        <div className="flex gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
          <button
            onClick={() => setActiveTab('patients')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'patients'
                ? 'bg-white text-slate-900 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Users className="h-3.5 w-3.5 text-blue-600" />
            Patient Census Explorer ({patients.length})
          </button>
          {(user?.role === 'DOCTOR' || user?.role === 'AUDITOR') && (
            <button
              onClick={() => setActiveTab('gp-directory')}
              className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
                activeTab === 'gp-directory'
                  ? 'bg-white text-slate-900 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Stethoscope className="h-3.5 w-3.5 text-emerald-600" />
              General Practitioner Directory
            </button>
          )}
          {user?.role === 'AUDITOR' && (
            <button
              onClick={() => setActiveTab('compliance')}
              className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
                activeTab === 'compliance'
                  ? 'bg-white text-red-700 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldAlert className="h-3.5 w-3.5 text-red-600" />
              GDPR Crypto-Shredding Engine
            </button>
          )}
        </div>

        {/* Patients Census Tab */}
        {activeTab === 'patients' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
            <PatientCensusTable />
          </div>
        )}

        {/* GP Directory Tab */}
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
                <h3 className="font-semibold text-amber-900">Right to be Forgotten — Verifiable Crypto-Shredding Engine</h3>
                <p className="mt-1 text-sm text-amber-700">
                  Executing data erasure irreversibly destroys the HashiCorp Vault KMS Transit keys protecting patient demographics and clinical visit payloads.
                  Ciphertexts across Postgres, Kafka event log, Redis cache, and immutable WORM backup files become permanently un-decryptable.
                </p>
              </div>
            </div>

            {eraseError && (
              <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                {eraseError}
              </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* Option A: Whole-Patient Destruction */}
              <div className="bg-white border border-red-200 rounded-2xl shadow-card p-6 space-y-4">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <ShieldAlert className="h-5 w-5 text-red-600" />
                  Full Patient Cryptographic Erasure
                </h2>
                <p className="text-xs text-slate-500">
                  Select a patient to destroy their demographic Transit key and all associated clinical visits.
                </p>

                <div className="space-y-3">
                  <div>
                    <label htmlFor="patient-select" className="label text-xs">
                      Select Patient Profile
                    </label>
                    <div className="relative">
                      <select
                        id="patient-select"
                        value={selectedPatientId}
                        onChange={(e) => setSelectedPatientId(e.target.value)}
                        className="input-field appearance-none pr-10 text-xs"
                      >
                        <option value="">— Choose an active patient —</option>
                        {activePatients.map((p) => (
                          <option key={p.id} value={p.patientId}>
                            {p.firstName} {p.lastName} ({p.patientId}) — NHS: {p.nhsNumber || '—'}
                          </option>
                        ))}
                      </select>
                      <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    </div>
                  </div>

                  <button
                    id="forget-patient-btn"
                    onClick={handleForgetPatient}
                    disabled={!selectedPatientId || isPending}
                    className="btn-danger w-full text-xs py-2.5"
                  >
                    {patientErasureMutation.isPending ? (
                      <div className="flex items-center justify-center gap-2">
                        <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        <span>Shredding All Patient Keys...</span>
                      </div>
                    ) : (
                      <>
                        <ShieldAlert className="h-4 w-4" /> Shred Entire Patient Profile
                      </>
                    )}
                  </button>
                </div>
              </div>

              {/* Option B: Visit-Level Destruction */}
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
                <h2 className="text-base font-bold text-slate-900 flex items-center gap-2">
                  <ShieldAlert className="h-5 w-5 text-slate-600" />
                  Single Visit Key Destruction
                </h2>
                <p className="text-xs text-slate-500">
                  Select a specific clinical visit to shred its dedicated Transit key and file attachments.
                </p>

                <div className="space-y-3">
                  <div>
                    <label htmlFor="visit-select" className="label text-xs">
                      Select Clinical Visit Chart
                    </label>
                    <div className="relative">
                      <select
                        id="visit-select"
                        value={selectedVisitId}
                        onChange={(e) => setSelectedVisitId(e.target.value)}
                        className="input-field appearance-none pr-10 text-xs"
                      >
                        <option value="">— Choose an active visit —</option>
                        {activeVisits.map((v) => (
                          <option key={v.id} value={v.id}>
                            {v.patientName} ({v.mrn || 'NO-MRN'}) — {v.diagnosis || 'Clinical Visit'} [
                            {v.attachments?.length || 0} File(s)]
                          </option>
                        ))}
                      </select>
                      <ChevronDown className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                    </div>
                  </div>

                  <button
                    id="forget-visit-btn"
                    onClick={handleForgetVisit}
                    disabled={!selectedVisitId || isPending}
                    className="btn-danger w-full text-xs py-2.5"
                  >
                    {visitErasureMutation.isPending ? (
                      <div className="flex items-center justify-center gap-2">
                        <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        <span>Shredding Visit Key...</span>
                      </div>
                    ) : (
                      <>
                        <ShieldAlert className="h-4 w-4" /> Shred Selected Clinical Visit
                      </>
                    )}
                  </button>
                </div>
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
