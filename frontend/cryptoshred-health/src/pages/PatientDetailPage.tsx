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
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import CreateRecordModal from '../components/CreateRecordModal';
import ViewRecordModal from '../components/ViewRecordModal';
import PatientFormModal from '../components/PatientFormModal';
import VitalsCard from '../components/VitalsCard';
import DeletionProofCard from '../components/DeletionProofCard';
import VerifyProofModal from '../components/VerifyProofModal';
import type { Patient, PatientRecord, DeletionProof } from '../types';

type DetailTab = 'encounters' | 'clinical' | 'demographics' | 'compliance';

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

  const [activeTab, setActiveTab] = useState<DetailTab>('encounters');
  const [showEncounterModal, setShowEncounterModal] = useState(false);
  const [showEditPatientModal, setShowEditPatientModal] = useState(false);
  const [selectedRecordForEdit, setSelectedRecordForEdit] = useState<PatientRecord | null>(null);
  const [selectedRecordForView, setSelectedRecordForView] = useState<string | null>(null);
  const [deletionProof, setDeletionProof] = useState<DeletionProof | null>(null);
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);

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

  // 2. Fetch All Encounters / Patient Records
  const { data: allRecords = [], isLoading: isRecordsLoading } = useQuery<PatientRecord[]>({
    queryKey: ['records'],
    queryFn: () => apiClient.get<PatientRecord[]>('/records').then((r) => r.data),
  });

  // Filter encounters specific to this patient
  const patientFullName = patient ? `${patient.firstName} ${patient.lastName}`.trim().toLowerCase() : '';
  const patientEncounters = allRecords.filter((r) => {
    if (!patient) return false;
    if (r.mrn && (r.mrn === patient.patientId || r.mrn === patient.nhsNumber)) return true;
    if (r.patientName && r.patientName.toLowerCase() === patientFullName) return true;
    return false;
  });

  // Delete an encounter mutation
  const deleteRecordMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/records/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['records'] }),
  });

  // Crypto-shred mutation
  const erasureMutation = useMutation({
    mutationFn: (recordId: string) =>
      apiClient.delete<DeletionProof>(`/erasure/${recordId}/forget`).then((r) => r.data),
    onSuccess: (proof) => {
      setDeletionProof(proof);
      queryClient.invalidateQueries({ queryKey: ['records'] });
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] });
    },
  });

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
  const latestEncounter = patientEncounters.find((e) => !e.shredded);

  if (isPatientLoading || isRecordsLoading) {
    return (
      <div className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-sm font-medium text-slate-600">Loading patient chart &amp; encounters...</p>
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
      {/* 1. Global App Header (Title, User, Role, Sign Out) */}
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
            {isDoctor && (
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
              <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-blue-600 text-white font-bold text-2xl shadow-sm border border-blue-500">
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
                  {patient.isActive !== false && patient.active !== false ? (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700">
                      <ShieldCheck className="h-3.5 w-3.5" /> Active Patient
                    </span>
                  ) : (
                    <span className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-0.5 rounded-md bg-red-50 border border-red-200 text-red-700">
                      <ShieldOff className="h-3.5 w-3.5" /> Deactivated / Shredded
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
                  {isDoctor && (
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
            onClick={() => setActiveTab('encounters')}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
              activeTab === 'encounters'
                ? 'bg-white text-slate-900 font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <Clock className="h-3.5 w-3.5 text-blue-600" />
            Visits &amp; Encounters ({patientEncounters.length})
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

        {/* TAB 1: Encounters & Visits */}
        {activeTab === 'encounters' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-base font-bold text-slate-900">Encounter History &amp; Consultations</h2>
                <p className="text-xs text-slate-500">
                  Chronological record of clinic visits, diagnoses, and encrypted attachments
                </p>
              </div>
              {isDoctor && (
                <button
                  id="record-new-visit-btn"
                  onClick={() => {
                    setSelectedRecordForEdit(null);
                    setShowEncounterModal(true);
                  }}
                  className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition"
                >
                  <Plus className="h-4 w-4" /> Record New Visit
                </button>
              )}
            </div>

            {patientEncounters.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-300 p-12 text-center bg-slate-50">
                <FileText className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                <h3 className="text-sm font-semibold text-slate-800">No clinical visits recorded yet</h3>
                <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                  Click "Record New Visit" to document today's medical consultation, vitals, and SOAP notes.
                </p>
                {isDoctor && (
                  <button
                    onClick={() => {
                      setSelectedRecordForEdit(null);
                      setShowEncounterModal(true);
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
                      <th className="py-3.5 px-3">Security &amp; Files</th>
                      <th className="py-3.5 pl-3 pr-4 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {patientEncounters.map((record) => (
                      <tr
                        key={record.id}
                        className={`hover:bg-slate-50 transition-colors ${
                          record.shredded ? 'opacity-60 bg-red-50/30' : ''
                        }`}
                      >
                        {/* Visit Date */}
                        <td className="py-3.5 pl-4 pr-2">
                          <span className="font-semibold text-slate-900 block">
                            {new Date(record.createdAt).toLocaleDateString(undefined, {
                              year: 'numeric',
                              month: 'short',
                              day: 'numeric',
                            })}
                          </span>
                          <span className="text-[11px] text-slate-400">
                            {new Date(record.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                          </span>
                        </td>

                        {/* Attending Clinician */}
                        <td className="py-3.5 px-3">
                          <span className="font-medium text-slate-900 block">
                            {record.attendingDoctor || (patient.gp ? `Dr. ${patient.gp.firstName} ${patient.gp.lastName}` : 'Dr. Alistair Finch, MD')}
                          </span>
                          <span className="text-[11px] text-slate-500 flex items-center gap-1 mt-0.5">
                            <Building className="h-3 w-3 text-slate-400" />
                            {record.department || patient.gp?.practiceName || 'General Practice'}
                          </span>
                        </td>

                        {/* Diagnosis */}
                        <td className="py-3.5 px-3 max-w-[240px]">
                          <p className="font-medium text-slate-900 truncate">
                            {record.diagnosis || 'General Clinical Review'}
                          </p>
                          {record.chiefComplaint && (
                            <p className="text-[11px] text-slate-500 truncate mt-0.5">
                              {record.chiefComplaint}
                            </p>
                          )}
                        </td>

                        {/* Vitals */}
                        <td className="py-3.5 px-3">
                          {record.shredded ? (
                            <span className="text-slate-400 font-mono text-[11px]">[SHREDDED]</span>
                          ) : (
                            <div className="space-y-0.5">
                              {record.bloodPressure && (
                                <div className="text-[11px]">
                                  <span className="text-slate-400">BP:</span>{' '}
                                  <span className="font-mono font-semibold text-slate-900">{record.bloodPressure}</span>
                                </div>
                              )}
                              {record.heartRate && (
                                <div className="text-[11px]">
                                  <span className="text-slate-400">HR:</span>{' '}
                                  <span className="font-mono text-rose-600 font-semibold">{record.heartRate} bpm</span>
                                </div>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Security */}
                        <td className="py-3.5 px-3">
                          {record.shredded ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-red-50 border border-red-200 text-red-700 text-[10px] font-semibold">
                              <ShieldOff className="h-3 w-3" /> Shredded
                            </span>
                          ) : (
                            <div className="space-y-1">
                              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700 text-[10px] font-semibold">
                                <ShieldCheck className="h-3 w-3" /> AES-256
                              </span>
                              {record.attachments && record.attachments.length > 0 && (
                                <span className="text-[10px] text-slate-500 block">
                                  {record.attachments.length} PDF(s)
                                </span>
                              )}
                            </div>
                          )}
                        </td>

                        {/* Actions */}
                        <td className="py-3.5 pl-3 pr-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => setSelectedRecordForView(record.id)}
                              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition border border-slate-200"
                              title="Open encounter chart"
                            >
                              <Eye className="h-3.5 w-3.5" /> Chart
                            </button>
                            {isDoctor && !record.shredded && (
                              <button
                                onClick={() => {
                                  setSelectedRecordForEdit(record);
                                  setShowEncounterModal(true);
                                }}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
                                title="Edit visit notes"
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </button>
                            )}
                            {isDoctor && (
                              <button
                                onClick={() => {
                                  if (confirm('Delete this encounter record?')) {
                                    deleteRecordMutation.mutate(record.id);
                                  }
                                }}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-slate-100 transition"
                                title="Delete encounter"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
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
            {latestEncounter ? (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
                <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
                  <Activity className="h-3.5 w-3.5 text-blue-600" /> Most Recent Biometrics (Recorded on {new Date(latestEncounter.createdAt).toLocaleDateString()})
                </h3>
                <VitalsCard record={latestEncounter} />
              </div>
            ) : (
              <div className="bg-white border border-slate-200 rounded-2xl p-6 text-center text-slate-500 text-sm">
                No biometric vitals recorded yet.
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {/* Allergies Card */}
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-3">
                <h4 className="text-xs font-semibold text-rose-600 uppercase tracking-wider flex items-center gap-1.5">
                  <AlertTriangle className="h-3.5 w-3.5" /> Known Allergies &amp; Adverse Reactions
                </h4>
                <p className="text-sm font-medium text-slate-900">
                  {latestEncounter?.allergies || 'No Known Drug Allergies (NKDA) recorded on chart.'}
                </p>
              </div>

              {/* Prescriptions Card */}
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-3">
                <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Pill className="h-3.5 w-3.5" /> Active Medications &amp; Prescriptions
                </h4>
                <p className="text-xs text-slate-700 font-mono whitespace-pre-wrap">
                  {latestEncounter?.prescriptions || 'No active outpatient prescriptions recorded.'}
                </p>
              </div>
            </div>
          </div>
        )}

        {/* TAB 3: Full Demographics & Admin */}
        {activeTab === 'demographics' && (
          <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="text-base font-bold text-slate-900">Master Patient Registry Details</h3>
              {isDoctor && (
                <button
                  onClick={() => setShowEditPatientModal(true)}
                  className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl border border-slate-300 hover:bg-slate-50 text-slate-700 text-xs font-medium transition shadow-sm"
                >
                  <Pencil className="h-3.5 w-3.5" /> Edit Information
                </button>
              )}
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-xs">
              <div className="space-y-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
                <h4 className="font-semibold text-blue-700 uppercase tracking-wider text-[11px]">
                  Demographics &amp; Identity
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
                  Contact &amp; Residence
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
                  Executing crypto-shredding on this patient permanently zeros-out the AES-256 decryption keys and redacts all PII in the database.
                  A mathematically signed cryptographic proof will be minted as a compliance certificate.
                </p>
              </div>
            </div>

            <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 space-y-4">
              <h3 className="text-base font-bold text-slate-900">Patient Data Destruction Controls</h3>
              <p className="text-xs text-slate-500">
                Select an encounter or trigger full patient anonymisation:
              </p>

              <div className="space-y-3">
                {patientEncounters.map((record) => (
                  <div
                    key={record.id}
                    className="flex items-center justify-between p-3.5 rounded-xl border border-slate-200 bg-slate-50"
                  >
                    <div>
                      <span className="text-xs font-semibold text-slate-900 block">
                        Encounter: {new Date(record.createdAt).toLocaleDateString()} — {record.diagnosis || 'Clinical Chart'}
                      </span>
                      <span className="text-[11px] text-slate-500 font-mono">
                        UUID: {record.id} • {record.shredded ? 'Already Shredded' : 'Active Encrypted Data'}
                      </span>
                    </div>

                    {!record.shredded && (
                      <button
                        onClick={() => {
                          if (confirm(`Permanently crypto-shred encounter record ${record.id}? This is irreversible.`)) {
                            erasureMutation.mutate(record.id);
                          }
                        }}
                        disabled={erasureMutation.isPending}
                        className="btn-danger text-xs py-1.5 px-3"
                      >
                        <ShieldAlert className="h-3.5 w-3.5" /> Crypto-Shred Encounter
                      </button>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {deletionProof && <DeletionProofCard proof={deletionProof} />}
          </div>
        )}
      </main>

      {/* Modals */}
      {showEncounterModal && (
        <CreateRecordModal
          defaultPatient={patient}
          editRecord={selectedRecordForEdit}
          onClose={() => {
            setShowEncounterModal(false);
            setSelectedRecordForEdit(null);
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

      {selectedRecordForView && (
        <ViewRecordModal
          recordId={selectedRecordForView}
          onClose={() => setSelectedRecordForView(null)}
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
