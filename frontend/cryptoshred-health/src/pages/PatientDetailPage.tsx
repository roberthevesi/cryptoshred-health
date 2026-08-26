import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  User,
  Calendar,
  Phone,
  Mail,
  MapPin,
  Stethoscope,
  Building2,
  Plus,
  Pencil,
  Eye,
  Trash2,
  ShieldCheck,
  ShieldAlert,
  AlertTriangle,
  FileText,
  Activity,
  Pill,
  Clock,
  Building,
  ShieldOff,
  UserCog,
  FileCheck2,
  LogOut,
  Lock,
  Download,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import RecordVisitModal from '../components/RecordVisitModal';
import ViewVisitModal from '../components/ViewVisitModal';
import PatientFormModal from '../components/PatientFormModal';
import VitalsCard from '../components/VitalsCard';
import DeletionProofCard from '../components/DeletionProofCard';
import VerifyProofModal from '../components/VerifyProofModal';
import type { Patient, PatientVisit, DeletionProof } from '../types';

type DetailTab = 'visits' | 'clinical' | 'demographics' | 'compliance';

const ROLE_BADGE: Record<string, string> = {
  DOCTOR: 'badge-role-doctor',
  PATIENT: 'badge-role-patient',
  AUDITOR: 'badge-role-auditor',
};

export default function PatientDetailPage() {
  const { patientId } = useParams<{ patientId: string }>();
  const navigate = useNavigate();
  const { user, logout } = useAuth();
  const queryClient = useQueryClient();

  const [activeTab, setActiveTab] = useState<DetailTab>('visits');
  const [showVisitModal, setShowVisitModal] = useState(false);
  const [showEditPatientModal, setShowEditPatientModal] = useState(false);
  const [selectedVisitForEdit, setSelectedVisitForEdit] = useState<PatientVisit | null>(null);
  const [selectedVisitForView, setSelectedVisitForView] = useState<string | null>(null);
  const [deletionProof, setDeletionProof] = useState<DeletionProof | null>(null);
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);
  const [erasureError, setErasureError] = useState('');
  const [isExportingFhir, setIsExportingFhir] = useState(false);
  const [visitsSubTab, setVisitsSubTab] = useState<'active' | 'shredded'>('active');

  // 1. Fetch Patient Master Profile
  const {
    data: patient,
    isLoading: isPatientLoading,
    isError: isPatientError,
  } = useQuery<Patient>({
    queryKey: ['patient', patientId],
    queryFn: () => apiClient.get<Patient>(`/patients/${patientId}`).then((r) => r.data),
    enabled: !!patientId,
  });

  // Persistent Deletion Proof Query for crypto-shredded patients
  const { data: persistentProof } = useQuery<DeletionProof>({
    queryKey: ['deletionProof', patientId],
    queryFn: () => apiClient.get<DeletionProof>(`/erasure/patients/${patientId}/proof`).then((r) => r.data),
    enabled: !!patientId && (!!patient?.shredded || patient?.isActive === false || patient?.active === false),
  });

  const effectiveProof = deletionProof || persistentProof;

  // 2. Fetch Clinical Visits for this Patient
  const { data: patientVisits = [], isLoading: isVisitsLoading } = useQuery<PatientVisit[]>({
    queryKey: ['visits', patientId],
    queryFn: () => apiClient.get<PatientVisit[]>(`/visits?patientId=${patientId}`).then((r) => r.data),
    enabled: !!patientId,
  });

  // Delete a visit mutation
  const deleteVisitMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/visits/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['visits'] });
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] });
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });

  // Full Patient Crypto-Shred Mutation (Auditor Only)
  const patientErasureMutation = useMutation({
    mutationFn: (pid: string) =>
      apiClient.delete<DeletionProof>(`/erasure/patients/${pid}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      setErasureError('');
      queryClient.invalidateQueries({ queryKey: ['visits'] });
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] });
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Patient crypto-shredding failed. Auditor authorization required.';
      setErasureError(msg);
    },
  });

  // Single Visit Crypto-shred mutation (Auditor Only)
  const visitErasureMutation = useMutation({
    mutationFn: (visitId: string) =>
      apiClient.delete<DeletionProof>(`/erasure/visits/${visitId}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      setErasureError('');
      queryClient.invalidateQueries({ queryKey: ['visits'] });
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Visit crypto-shredding failed. Auditor authorization required.';
      setErasureError(msg);
    },
  });

  const handleExportFhir = async () => {
    if (!patient?.patientId) return;
    try {
      setIsExportingFhir(true);
      const response = await apiClient.get(`/patients/${patient.patientId}/fhir`, {
        responseType: 'blob',
      });
      const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/fhir+json' }));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${patient.patientId}-fhir-r4.json`);
      document.body.appendChild(link);
      link.click();
      link.parentNode?.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export FHIR bundle:', err);
      alert('Failed to export FHIR R4 bundle. Please try again.');
    } finally {
      setIsExportingFhir(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const getAge = (dobString?: string) => {
    if (!dobString) return null;
    try {
      const dob = new Date(dobString);
      const diffMs = Date.now() - dob.getTime();
      const ageDate = new Date(diffMs);
      return Math.abs(ageDate.getUTCFullYear() - 1970);
    } catch {
      return null;
    }
  };

  const age = getAge(patient?.dateOfBirth);
  const isDoctor = user?.role === 'DOCTOR';
  const isAuditor = user?.role === 'AUDITOR';
  const isShredded = patient?.shredded || patient?.isActive === false || patient?.active === false;
  const activeVisits = patientVisits.filter((v) => !v.shredded && !isShredded);
  const shreddedVisits = patientVisits.filter((v) => v.shredded || isShredded);
  const displayedVisits = visitsSubTab === 'active' ? activeVisits : shreddedVisits;
  const latestVisit = activeVisits[0] || patientVisits.find((v) => !v.shredded);

  if (isPatientLoading || isVisitsLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-sm font-medium text-slate-600">Loading patient clinical chart &amp; visits...</p>
      </div>
    );
  }

  if (isPatientError || !patient) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center p-6 text-center">
        <div className="max-w-md w-full bg-white border border-slate-200 rounded-2xl p-8 shadow-card space-y-4">
          <ShieldAlert className="mx-auto h-12 w-12 text-red-500" />
          <h2 className="text-xl font-bold text-slate-900">Patient Record Not Found</h2>
          <p className="text-sm text-slate-500">
            The patient profile `{patientId}` could not be retrieved from the clinical registry.
          </p>
          <button
            onClick={() => navigate('/dashboard')}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition"
          >
            <ArrowLeft className="h-4 w-4" /> Return to Census
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {/* 1. Global App Header */}
      <nav className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-3.5">
          <div className="flex items-center gap-3">
            <Link to="/dashboard" className="flex items-center gap-3 group">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 ring-1 ring-blue-200 group-hover:ring-blue-400 transition">
                <ShieldCheck className="h-5 w-5 text-blue-600" />
              </div>
              <div>
                <span className="font-bold text-slate-900">CryptoShred</span>
                <span className="text-blue-600 font-bold"> Health</span>
              </div>
            </Link>
          </div>

          <div className="hidden md:flex items-center gap-2 text-xs text-slate-500">
            <Activity className="h-3.5 w-3.5 text-emerald-500" />
            EHR Core &amp; Vault KMS Transit Engine Online
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

      {/* 2. Sub-header / Patient Context Action Bar */}
      <div className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-7xl px-6 py-2.5 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Link
              to="/dashboard"
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-slate-200 text-xs font-semibold text-slate-600 hover:bg-slate-50 hover:text-slate-900 transition"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to Patient Census
            </Link>
            <span className="text-slate-300">/</span>
            <span className="text-xs font-bold text-slate-900">
              {patient.firstName} {patient.lastName}
            </span>
            <span className="font-mono text-[10px] px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-slate-600">
              ID: {patient.patientId}
            </span>
          </div>

          <div className="flex items-center gap-2.5">
            <button
              onClick={handleExportFhir}
              disabled={isExportingFhir}
              className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-blue-200 bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-semibold transition shadow-sm"
              title="Export complete HL7 FHIR R4 Collection Bundle"
            >
              {isExportingFhir ? (
                <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
              ) : (
                <Download className="h-3.5 w-3.5 text-blue-600" />
              )}
              <span>Export FHIR R4</span>
            </button>

            {isDoctor && !isShredded && (
              <button
                onClick={() => setShowEditPatientModal(true)}
                className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-medium transition shadow-sm"
              >
                <UserCog className="h-3.5 w-3.5 text-slate-500" /> Edit Patient Demographics
              </button>
            )}
          </div>
        </div>
      </div>

      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        {/* NHS Clinical Patient Header Banner */}
        <div className="bg-white border border-slate-200 rounded-2xl p-6 shadow-card">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
            {/* Left: Avatar & Identity */}
            <div className="flex items-start gap-4">
              <div className={`flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl text-white font-bold text-2xl shadow-sm border ${
                isShredded ? 'bg-red-600 border-red-500' : 'bg-blue-600 border-blue-500'
              }`}>
                {patient.firstName.charAt(0).toUpperCase()}
              </div>

              <div>
                <div className="flex flex-wrap items-center gap-2.5">
                  <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                    {patient.firstName} {patient.lastName}
                  </h1>
                  {patient.nhsNumber && (
                    <span className="font-mono text-xs font-semibold px-2.5 py-0.5 rounded-md bg-blue-50 border border-blue-200 text-blue-700">
                      NHS: {patient.nhsNumber}
                    </span>
                  )}
                  {!isShredded ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700">
                      <ShieldCheck className="h-3.5 w-3.5" /> Active Protected Patient
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-md bg-red-50 border border-red-200 text-red-700">
                      <ShieldOff className="h-3.5 w-3.5" /> Crypto-Shredded (GDPR Art. 17)
                    </span>
                  )}
                </div>

                <div className="mt-2 flex flex-wrap items-center gap-x-5 gap-y-1.5 text-xs text-slate-600">
                  <span className="flex items-center gap-1.5">
                    <User className="h-3.5 w-3.5 text-slate-400" />
                    {patient.gender || 'Unknown'} {age ? `• ${age} yrs` : ''}
                  </span>
                  {patient.dateOfBirth && (
                    <span className="flex items-center gap-1.5">
                      <Calendar className="h-3.5 w-3.5 text-slate-400" />
                      DOB: {patient.dateOfBirth}
                    </span>
                  )}
                  {patient.phoneNumber && (
                    <span className="flex items-center gap-1.5">
                      <Phone className="h-3.5 w-3.5 text-slate-400" />
                      {patient.phoneNumber}
                    </span>
                  )}
                  {patient.email && (
                    <span className="flex items-center gap-1.5">
                      <Mail className="h-3.5 w-3.5 text-slate-400" />
                      {patient.email}
                    </span>
                  )}
                  {patient.address && (
                    <span className="flex items-center gap-1.5">
                      <MapPin className="h-3.5 w-3.5 text-slate-400" />
                      <span className="truncate max-w-[200px]">{patient.address}</span>
                    </span>
                  )}
                </div>
              </div>
            </div>

            {/* Right: Assigned GP Surgery Card */}
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5 min-w-[260px]">
              <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block mb-1">
                Assigned Primary Care GP
              </span>
              {patient.gp ? (
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <Stethoscope className="h-4 w-4 text-blue-600 shrink-0" />
                    <span className="text-sm font-semibold text-slate-900">
                      Dr. {patient.gp.firstName} {patient.gp.lastName}
                    </span>
                  </div>
                  <div className="text-[11px] text-slate-500 flex flex-wrap items-center gap-2 pl-6">
                    <span className="font-mono">GMC: {patient.gp.gmcNumber}</span>
                    {patient.gp.practiceName && (
                      <span className="flex items-center gap-1">
                        <Building2 className="h-3 w-3 text-slate-400" />
                        {patient.gp.practiceName}
                      </span>
                    )}
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs text-slate-500 italic">No GP assigned yet</span>
                  {isDoctor && !isShredded && (
                    <button
                      onClick={() => setShowEditPatientModal(true)}
                      className="text-xs text-blue-600 hover:text-blue-700 font-semibold"
                    >
                      Assign GP
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Tab Navigation */}
        <div className="flex gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
          <button
            onClick={() => setActiveTab('visits')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'visits'
                ? 'bg-white text-slate-900 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Clock className="h-3.5 w-3.5 text-blue-600" />
            Visits &amp; Encounters ({patientVisits.length})
          </button>
          <button
            onClick={() => setActiveTab('clinical')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'clinical'
                ? 'bg-white text-slate-900 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Activity className="h-3.5 w-3.5 text-emerald-600" />
            Biometric Telemetry &amp; Vitals
          </button>
          <button
            onClick={() => setActiveTab('demographics')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'demographics'
                ? 'bg-white text-slate-900 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <User className="h-3.5 w-3.5 text-indigo-600" />
            Full Demographics &amp; Admin
          </button>
          <button
            onClick={() => setActiveTab('compliance')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'compliance'
                ? 'bg-white text-red-700 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ShieldAlert className="h-3.5 w-3.5 text-red-600" />
            GDPR Right to be Forgotten
          </button>
        </div>

        {/* TAB 1: Visits & Encounters */}
        {activeTab === 'visits' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <h2 className="text-base font-bold text-slate-900">Clinical Visits &amp; Consultations</h2>
                <p className="text-xs text-slate-500">
                  Chronological record of patient visits, diagnoses, SOAP charts, biometrics, and encrypted attachments
                </p>
              </div>
              {isDoctor && !isShredded && (
                <button
                  id="record-new-visit-btn"
                  onClick={() => {
                    setSelectedVisitForEdit(null);
                    setShowVisitModal(true);
                  }}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition"
                >
                  <Plus className="h-4 w-4" /> Record New Visit
                </button>
              )}
            </div>

            {/* Segmented Filter: Active vs Crypto-Shredded Visits */}
            <div className="flex gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
              <button
                onClick={() => setVisitsSubTab('active')}
                className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                  visitsSubTab === 'active'
                    ? 'bg-white text-emerald-800 font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <ShieldCheck className={`h-3.5 w-3.5 ${visitsSubTab === 'active' ? 'text-emerald-600' : 'text-slate-400'}`} />
                <span>Active Clinical Visits</span>
                <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                  visitsSubTab === 'active' ? 'bg-emerald-100 text-emerald-800 font-bold' : 'bg-slate-200 text-slate-600'
                }`}>
                  {activeVisits.length}
                </span>
              </button>

              <button
                onClick={() => setVisitsSubTab('shredded')}
                className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                  visitsSubTab === 'shredded'
                    ? 'bg-white text-rose-800 font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <ShieldOff className={`h-3.5 w-3.5 ${visitsSubTab === 'shredded' ? 'text-rose-600' : 'text-slate-400'}`} />
                <span>Crypto-Shredded Visits</span>
                <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                  visitsSubTab === 'shredded' ? 'bg-rose-100 text-rose-800 font-bold' : 'bg-slate-200 text-slate-600'
                }`}>
                  {shreddedVisits.length}
                </span>
              </button>
            </div>

            {displayedVisits.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 p-12 text-center bg-slate-50">
                <FileText className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                <h3 className="text-sm font-semibold text-slate-800">
                  {visitsSubTab === 'active' ? 'No active clinical visits recorded' : 'No crypto-shredded visits for this patient'}
                </h3>
                <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                  {visitsSubTab === 'active'
                    ? 'Click "Record New Visit" to document today\'s medical consultation, vital signs, and SOAP notes.'
                    : 'All visits for this patient currently remain protected under active Vault KMS Transit encryption keys.'}
                </p>
                {visitsSubTab === 'active' && isDoctor && !isShredded && (
                  <button
                    onClick={() => {
                      setSelectedVisitForEdit(null);
                      setShowVisitModal(true);
                    }}
                    className="mt-4 inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition"
                  >
                    <Plus className="h-4 w-4" /> Record New Visit
                  </button>
                )}
              </div>
            ) : (
              <div className="overflow-hidden rounded-xl border border-slate-200">
                <table className="w-full text-left text-xs">
                  <thead className="border-b border-slate-200 bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
                    <tr>
                      <th className="py-3.5 pl-4 pr-2">Visit Date</th>
                      <th className="py-3.5 px-3">Attending Clinician</th>
                      <th className="py-3.5 px-3">Diagnosis &amp; Reason</th>
                      <th className="py-3.5 px-3">Vital Signs</th>
                      <th className="py-3.5 px-3">Security &amp; Encryption</th>
                      <th className="py-3.5 pl-3 pr-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {displayedVisits.map((visit) => (
                      <tr
                        key={visit.id}
                        className={`hover:bg-slate-50 transition-colors ${
                          visit.shredded || isShredded ? 'opacity-70 bg-rose-50/30' : ''
                        }`}
                      >
                        {/* Visit Date */}
                        <td className="py-3.5 pl-4 pr-2">
                          <span className="font-semibold text-slate-900 block">
                            {new Date(visit.createdAt).toLocaleDateString(undefined, {
                              year: 'numeric',
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                          <span className="text-[11px] text-slate-400">
                            {new Date(visit.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </td>

                        {/* Attending Clinician */}
                        <td className="py-3.5 px-3">
                          <span className="font-medium text-slate-900 block">
                            {visit.attendingDoctor || (patient.gp ? `Dr. ${patient.gp.firstName} ${patient.gp.lastName}` : 'Unassigned')}
                          </span>
                          <span className="text-[11px] text-slate-500 flex items-center gap-1 mt-0.5">
                            <Building className="h-3 w-3 text-slate-400" />
                            {visit.department || patient.gp?.practiceName || '—'}
                          </span>
                        </td>

                        {/* Diagnosis */}
                        <td className="py-3.5 px-3 max-w-[240px]">
                          <p className={`font-medium truncate ${visit.shredded || isShredded ? 'text-rose-900 font-mono' : 'text-slate-900'}`}>
                            {visit.shredded || isShredded ? '[SHREDDED]' : (visit.diagnosis || '—')}
                          </p>
                          {!visit.shredded && !isShredded && visit.chiefComplaint && (
                            <p className="text-[11px] text-slate-500 truncate mt-0.5">
                              {visit.chiefComplaint}
                            </p>
                          )}
                        </td>

                        {/* Vitals */}
                        <td className="py-3.5 px-3">
                          {visit.shredded || isShredded ? (
                            <span className="text-slate-400 font-mono text-[11px]">[SHREDDED]</span>
                          ) : (
                            <div className="space-y-0.5">
                              {visit.bloodPressure && (
                                <div className="text-[11px]">
                                  <span className="text-slate-400">BP:</span>{' '}
                                  <span className="font-mono font-semibold text-slate-900">{visit.bloodPressure}</span>
                                </div>
                              )}
                              {visit.heartRate && (
                                <div className="text-[11px]">
                                  <span className="text-slate-400">HR:</span>{' '}
                                  <span className="font-mono text-rose-600 font-semibold">{visit.heartRate} bpm</span>
                                </div>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Security */}
                        <td className="py-3.5 px-3">
                          {visit.shredded || isShredded ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-rose-100 border border-rose-200 text-rose-800 text-[10px] font-semibold font-mono">
                              <ShieldOff className="h-3 w-3" /> Key Shredded
                            </span>
                          ) : (
                            <div className="space-y-1">
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700 text-[10px] font-semibold">
                                <ShieldCheck className="h-3 w-3" /> AES-256 Envelope
                              </span>
                              {visit.attachments && visit.attachments.length > 0 && (
                                <span className="text-[10px] text-slate-500 block">
                                  {visit.attachments.length} Encrypted Doc(s)
                                </span>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 pl-3 pr-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => setSelectedVisitForView(visit.id)}
                              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition border border-slate-200"
                              title="Open visit chart"
                            >
                              <Eye className="h-3.5 w-3.5" /> Chart
                            </button>
                            {isDoctor && !visit.shredded && !isShredded && (
                              <button
                                onClick={() => {
                                  setSelectedVisitForEdit(visit);
                                  setShowVisitModal(true);
                                }}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
                                title="Edit visit notes"
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </button>
                            )}
                            {isDoctor && !isShredded && (
                              <button
                                onClick={() => {
                                  if (confirm('Delete this clinical visit?')) {
                                    deleteVisitMutation.mutate(visit.id);
                                  }
                                }}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-slate-100 transition"
                                title="Delete visit"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            )}
                            {isAuditor && !visit.shredded && !isShredded && (
                              <button
                                onClick={() => {
                                  if (confirm(`Permanently crypto-shred visit ${visit.id}? This destroys the Vault KMS key and is irreversible.`)) {
                                    visitErasureMutation.mutate(visit.id);
                                  }
                                }}
                                disabled={visitErasureMutation.isPending}
                                className="p-1.5 rounded-lg text-rose-500 hover:text-rose-700 hover:bg-rose-50 transition"
                                title="Crypto-Shred Visit (GDPR Art. 17)"
                              >
                                <ShieldAlert className="h-3.5 w-3.5" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}

        {/* TAB 2: Biometric Telemetry & Summary */}
        {activeTab === 'clinical' && (
          <div className="space-y-6 animate-fade-in">
            {latestVisit && !isShredded ? (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
                <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Activity className="h-3.5 w-3.5 text-blue-600" /> Most Recent Biometrics (Recorded on {new Date(latestVisit.createdAt).toLocaleDateString()})
                </h3>
                <VitalsCard record={latestVisit} />
              </div>
            ) : (
              <div className="bg-white border border-slate-200 rounded-2xl p-6 text-center text-slate-500 text-sm">
                {isShredded ? 'Telemetry records have been crypto-shredded.' : 'No biometric vitals recorded yet.'}
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Allergies Card */}
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-3">
                <h4 className="text-xs font-semibold text-rose-600 uppercase tracking-wider flex items-center gap-1.5">
                  <AlertTriangle className="h-3.5 w-3.5" /> Known Allergies &amp; Adverse Reactions
                </h4>
                <p className="text-sm font-medium text-slate-900">
                  {isShredded ? '[SHREDDED]' : (latestVisit?.allergies || 'No Known Drug Allergies (NKDA) recorded on chart.')}
                </p>
              </div>

              {/* Prescriptions Card */}
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-3">
                <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Pill className="h-3.5 w-3.5" /> Active Medications &amp; Prescriptions
                </h4>
                <p className="text-xs text-slate-700 font-mono whitespace-pre-wrap">
                  {isShredded ? '[SHREDDED]' : (latestVisit?.prescriptions || 'No active outpatient prescriptions recorded.')}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: Full Demographics & Admin */}
        {activeTab === 'demographics' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in space-y-6">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-base font-bold text-slate-900">Master Patient Registry Details</h3>
                <p className="text-xs text-slate-500">Envelope-encrypted master demographic profile &amp; FHIR interoperability</p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleExportFhir}
                  disabled={isExportingFhir}
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-blue-200 bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-semibold transition shadow-sm"
                >
                  {isExportingFhir ? (
                    <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent" />
                  ) : (
                    <Download className="h-3.5 w-3.5 text-blue-600" />
                  )}
                  Export FHIR R4
                </button>
                {isDoctor && !isShredded && (
                  <button
                    onClick={() => setShowEditPatientModal(true)}
                    className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-medium transition shadow-sm"
                  >
                    <Pencil className="h-3.5 w-3.5" /> Edit Information
                  </button>
                )}
              </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
              <div className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <h4 className="font-semibold text-blue-700 uppercase tracking-wider text-[11px]">
                  Demographics &amp; Identity (Envelope Encrypted)
                </h4>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <span className="text-slate-500 block">First Name:</span>
                    <span className="font-semibold text-slate-900 text-sm">{patient.firstName}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Last Name:</span>
                    <span className="font-semibold text-slate-900 text-sm">{patient.lastName}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Patient ID:</span>
                    <span className="font-mono text-slate-800">{patient.patientId}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">NHS Number:</span>
                    <span className="font-mono font-semibold text-blue-700">{patient.nhsNumber || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Date of Birth:</span>
                    <span className="text-slate-800">{patient.dateOfBirth || '—'} ({age ? `${age} years` : 'Age unknown'})</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Gender:</span>
                    <span className="text-slate-800">{patient.gender || '—'}</span>
                  </div>
                </div>
              </div>

              <div className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <h4 className="font-semibold text-blue-700 uppercase tracking-wider text-[11px]">
                  Contact &amp; Residence (Envelope Encrypted)
                </h4>
                <div className="space-y-3">
                  <div>
                    <span className="text-slate-500 block">Email Address:</span>
                    <span className="text-slate-900 font-medium">{patient.email || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Phone Number:</span>
                    <span className="text-slate-900 font-medium">{patient.phoneNumber || '—'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500 block">Residential Address:</span>
                    <span className="text-slate-900">{patient.address || '—'}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 4: GDPR Compliance & Erasure */}
        {activeTab === 'compliance' && (
          <div className="space-y-6 animate-fade-in">
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 flex items-start gap-4">
              <ShieldAlert className="h-6 w-6 text-amber-600 shrink-0 mt-0.5" />
              <div>
                <h3 className="font-semibold text-amber-900">GDPR Article 17 — Patient Right to be Forgotten</h3>
                <p className="mt-1 text-sm text-amber-700">
                  Executing crypto-shredding irreversibly destroys the HashiCorp Vault KMS Transit keys protecting this patient's demographic PII and clinical visit ciphertext.
                  A mathematically signed RSA deletion certificate with Merkle tree inclusion proof will be minted for compliance records.
                </p>
              </div>
            </div>

            {erasureError && (
              <div className="rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700 flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                {erasureError}
              </div>
            )}

            {/* GDPR Article 20 — Right to Data Portability (HL7 FHIR R4) */}
            <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <Download className="h-5 w-5 text-blue-600" />
                    GDPR Article 20 — Right to Data Portability (HL7 FHIR R4)
                  </h3>
                  <p className="text-xs text-slate-500 mt-1">
                    Export all patient demographics, visits, observations, conditions, and encrypted document references in international HL7 FHIR R4 Bundle format.
                  </p>
                </div>

                <button
                  onClick={handleExportFhir}
                  disabled={isExportingFhir}
                  className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition shrink-0"
                >
                  {isExportingFhir ? (
                    <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  ) : (
                    <Download className="h-4 w-4" />
                  )}
                  Export FHIR R4 Bundle
                </button>
              </div>
            </div>

            {/* Whole-Patient Destruction Card */}
            <div className="bg-white border border-red-200 rounded-2xl shadow-card p-6 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-base font-bold text-slate-900 flex items-center gap-2">
                    <ShieldAlert className="h-5 w-5 text-red-600" />
                    Full Patient Cryptographic Erasure
                  </h3>
                  <p className="text-xs text-slate-500 mt-1">
                    Permanently destroys the master patient demographic key and all {patientVisits.length} linked visit keys across all storage layers.
                  </p>
                </div>

                {isAuditor ? (
                  !isShredded ? (
                    <button
                      onClick={() => {
                        if (confirm(`Are you certain you want to crypto-shred patient ${patient.patientId} (${patient.firstName} ${patient.lastName})? This will permanently destroy all encryption keys across Postgres, Kafka, Redis, and WORM backups.`)) {
                          patientErasureMutation.mutate(patient.patientId);
                        }
                      }}
                      disabled={patientErasureMutation.isPending}
                      className="btn-danger shrink-0"
                    >
                      {patientErasureMutation.isPending ? (
                        <div className="flex items-center gap-2">
                          <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                          <span>Shredding All Patient Keys...</span>
                        </div>
                      ) : (
                        <>
                          <ShieldAlert className="h-4 w-4" /> Shred Entire Patient Profile
                        </>
                      )}
                    </button>
                  ) : (
                    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-red-100 text-red-800 text-xs font-bold border border-red-300">
                      <ShieldOff className="h-4 w-4" /> Patient Already Crypto-Shredded
                    </span>
                  )
                ) : (
                  <div className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-slate-100 border border-slate-200 text-slate-600 text-xs font-medium">
                    <Lock className="h-3.5 w-3.5 text-slate-400" />
                    <span>Auditor Role Required to Execute Erasure</span>
                  </div>
                )}
              </div>
            </div>

            {/* Visit Level Destruction Controls */}
            {patientVisits.length > 0 && (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
                <h3 className="text-sm font-bold text-slate-900">Individual Visit Key Destruction Controls</h3>
                <p className="text-xs text-slate-500">
                  Selectively shred specific clinical visits if required for targeted Right-to-be-Forgotten requests:
                </p>

                <div className="space-y-3">
                  {patientVisits.map((visit) => (
                    <div
                      key={visit.id}
                      className="flex flex-col sm:flex-row sm:items-center justify-between p-3.5 rounded-xl border border-slate-200 bg-slate-50 gap-3"
                    >
                      <div>
                        <span className="text-xs font-semibold text-slate-900 block">
                          Visit: {new Date(visit.createdAt).toLocaleDateString()} — {visit.diagnosis || 'Clinical Chart'}
                        </span>
                        <span className="text-[11px] text-slate-500 font-mono">
                          UUID: {visit.id} • {visit.shredded || isShredded ? 'Shredded' : 'Active Encrypted Data'}
                        </span>
                      </div>

                      {!visit.shredded && !isShredded && (
                        isAuditor ? (
                          <button
                            onClick={() => {
                              if (confirm(`Permanently crypto-shred visit ${visit.id}? This is irreversible.`)) {
                                visitErasureMutation.mutate(visit.id);
                              }
                            }}
                            disabled={visitErasureMutation.isPending}
                            className="btn-danger text-xs py-1.5 px-3 shrink-0"
                          >
                            <ShieldAlert className="h-3.5 w-3.5" /> Crypto-Shred Visit
                          </button>
                        ) : (
                          <span className="text-[11px] text-slate-400 italic">Auditor clearance required</span>
                        )
                      )}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {effectiveProof && <DeletionProofCard proof={effectiveProof} />}
          </div>
        )}
      </main>

      {/* Modals */}
      {showVisitModal && (
        <RecordVisitModal
          defaultPatient={patient}
          editVisit={selectedVisitForEdit}
          onClose={() => {
            setShowVisitModal(false);
            setSelectedVisitForEdit(null);
          }}
        />
      )}

      {showEditPatientModal && (
        <PatientFormModal
          isOpen={showEditPatientModal}
          patient={patient}
          onClose={() => setShowEditPatientModal(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['patient', patientId] });
            queryClient.invalidateQueries({ queryKey: ['patients'] });
          }}
        />
      )}

      {selectedVisitForView && (
        <ViewVisitModal
          visitId={selectedVisitForView}
          onClose={() => setSelectedVisitForView(null)}
        />
      )}

      <VerifyProofModal
        isOpen={isVerifyModalOpen}
        onClose={() => setIsVerifyModalOpen(false)}
        token={user?.token || ''}
      />
    </div>
  );
}
