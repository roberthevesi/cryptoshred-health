import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  User,
  Calendar,
  Phone,
  Mail,
  MapPin,
  Stethoscope,
  Building2,
  FileCheck2,
  Download,
  Activity,
  ShieldCheck,
  ShieldAlert,
  Clock,
  Pill,
  FileText,
  Eye,
  File,
  Sparkles,
  Lock,
  Layers,
  CheckCircle2,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import VitalsCard from './VitalsCard';
import ViewVisitModal from './ViewVisitModal';
import VerifyProofModal from './VerifyProofModal';
import ProofViewerModal from './ProofViewerModal';
import type { Patient, PatientVisit } from '../types';

export default function PatientPortalView() {
  const { user } = useAuth();

  const [selectedVisitId, setSelectedVisitId] = useState<string | null>(null);
  const [selectedProofVisitId, setSelectedProofVisitId] = useState<string | null>(null);
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);
  const [isExportingFhir, setIsExportingFhir] = useState(false);
  const [visitsTab, setVisitsTab] = useState<'active' | 'shredded'>('active');

  // 1. Fetch Patient Profile via /api/patients/me
  const {
    data: patient,
    isLoading: isPatientLoading,
    isError: isPatientError,
  } = useQuery<Patient>({
    queryKey: ['patient-me'],
    queryFn: () => apiClient.get<Patient>('/patients/me').then((r) => r.data),
  });

  // 2. Fetch Patient's Clinical Visits
  const {
    data: visits = [],
    isLoading: isVisitsLoading,
  } = useQuery<PatientVisit[]>({
    queryKey: ['patient-visits-me'],
    queryFn: () => apiClient.get<PatientVisit[]>('/visits').then((r) => r.data),
  });

  const activeVisits = visits.filter((v) => !v.shredded);
  const shreddedVisits = visits.filter((v) => v.shredded);

  // Latest clinical encounter for vitals summary
  const latestVisit = activeVisits.length > 0 ? activeVisits[0] : visits[0];

  const handleExportFhir = async () => {
    if (!patient?.patientId) return;
    setIsExportingFhir(true);
    try {
      const response = await apiClient.get(`/patients/${patient.patientId}/fhir`, {
        headers: { Accept: 'application/fhir+json, application/json' },
      });
      const blob = new Blob([JSON.stringify(response.data, null, 2)], {
        type: 'application/fhir+json',
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${patient.patientId}-fhir-r4-bundle.json`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('Failed to export FHIR bundle:', err);
    } finally {
      setIsExportingFhir(false);
    }
  };

  const calculateAge = (dobString?: string) => {
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

  if (isPatientLoading || isVisitsLoading) {
    return (
      <div className="flex min-h-[450px] flex-col items-center justify-center space-y-4">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-sm font-medium text-slate-600 animate-pulse">
          Decrypting your personal zero-knowledge EHR portal via Vault KMS...
        </p>
      </div>
    );
  }

  if (isPatientError || !patient) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 p-8 text-center max-w-xl mx-auto my-12">
        <ShieldAlert className="h-12 w-12 text-red-500 mx-auto mb-3" />
        <h3 className="text-lg font-bold text-slate-900 mb-1">Patient Profile Inaccessible</h3>
        <p className="text-sm text-slate-600 mb-4">
          Unable to retrieve your patient health records. Please confirm with your healthcare provider that your patient profile is linked to <span className="font-semibold text-slate-800">{user?.email}</span>.
        </p>
      </div>
    );
  }

  const patientAge = calculateAge(patient.dateOfBirth);

  return (
    <div className="space-y-6 animate-fade-in">
      {/* ── 1. Demographics & Quick Actions Header ───────────────────────── */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-card">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6">
          {/* Patient Bio & Avatar */}
          <div className="flex items-start sm:items-center gap-4">
            <div className="relative flex h-16 w-16 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-600 text-white font-bold text-2xl shadow-md ring-4 ring-blue-50">
              {patient.firstName ? patient.firstName.charAt(0) : 'P'}
              <span className="absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-emerald-500 border-2 border-white" />
            </div>

            <div>
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-2xl font-bold text-slate-900 tracking-tight">
                  {patient.firstName} {patient.lastName}
                </h1>
                <span className="font-mono text-xs font-semibold px-2.5 py-0.5 rounded-md bg-blue-50 border border-blue-200 text-blue-700">
                  {patient.patientId}
                </span>
                {patient.nhsNumber && (
                  <span className="font-mono text-xs font-semibold px-2.5 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-700">
                    NHS: {patient.nhsNumber}
                  </span>
                )}
                <span className="inline-flex items-center gap-1 text-[11px] font-medium bg-emerald-50 text-emerald-700 px-2 py-0.5 rounded-full border border-emerald-200">
                  <CheckCircle2 className="h-3 w-3 text-emerald-600" />
                  Verified Zero-Knowledge Profile
                </span>
              </div>

              <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-500">
                {patient.dateOfBirth && (
                  <span className="flex items-center gap-1">
                    <Calendar className="h-3.5 w-3.5 text-slate-400" />
                    {patient.dateOfBirth} {patientAge !== null && `(${patientAge} yrs)`}
                  </span>
                )}
                {patient.gender && (
                  <span className="flex items-center gap-1">
                    <User className="h-3.5 w-3.5 text-slate-400" />
                    {patient.gender}
                  </span>
                )}
                {patient.bloodType && (
                  <span className="flex items-center gap-1 font-semibold text-slate-700 bg-rose-50 border border-rose-200 px-1.5 py-0.5 rounded">
                    Blood Type: <span className="text-rose-600 font-bold">{patient.bloodType}</span>
                  </span>
                )}
                {patient.emergencyContactName && (
                  <span className="flex items-center gap-1 text-slate-600 bg-slate-100 border border-slate-200 px-1.5 py-0.5 rounded">
                    ICE: <span className="font-semibold text-slate-800">{patient.emergencyContactName}</span> {patient.emergencyContactRelationship ? `(${patient.emergencyContactRelationship})` : ''}
                  </span>
                )}
                {patient.email && (
                  <span className="flex items-center gap-1">
                    <Mail className="h-3.5 w-3.5 text-slate-400" />
                    {patient.email}
                  </span>
                )}
                {patient.phoneNumber && (
                  <span className="flex items-center gap-1">
                    <Phone className="h-3.5 w-3.5 text-slate-400" />
                    {patient.phoneNumber}
                  </span>
                )}
              </div>

              {patient.address && (
                <div className="mt-1.5 flex items-center gap-1 text-xs text-slate-500">
                  <MapPin className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                  <span className="truncate">{patient.address}</span>
                </div>
              )}
            </div>
          </div>

          {/* Action Buttons */}
          <div className="flex flex-wrap items-center gap-2.5 pt-4 lg:pt-0 border-t lg:border-t-0 border-slate-100">
            <button
              type="button"
              onClick={handleExportFhir}
              disabled={isExportingFhir}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-xs font-semibold shadow-sm transition disabled:opacity-50"
            >
              {isExportingFhir ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Generating FHIR R4...
                </>
              ) : (
                <>
                  <Download className="h-3.5 w-3.5" />
                  Export FHIR R4 Bundle
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => setIsVerifyModalOpen(true)}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 active:bg-emerald-200 border border-emerald-200 text-emerald-800 text-xs font-semibold transition shadow-sm"
            >
              <FileCheck2 className="h-3.5 w-3.5 text-emerald-600" />
              Verify Proof Artifact
            </button>
          </div>
        </div>

        {/* Assigned General Practice Surgery */}
        {patient.gp && (
          <div className="mt-5 pt-4 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-3 text-xs bg-slate-50/70 -mx-6 -mb-6 px-6 py-3 rounded-b-2xl">
            <div className="flex items-center gap-2 text-slate-700">
              <Stethoscope className="h-4 w-4 text-emerald-600 shrink-0" />
              <span className="font-semibold">Registered GP:</span>
              <span>Dr. {patient.gp.firstName} {patient.gp.lastName} ({patient.gp.specialisation})</span>
            </div>
            <div className="flex items-center gap-2 text-slate-500">
              <Building2 className="h-4 w-4 text-slate-400 shrink-0" />
              <span>{patient.gp.practiceName}</span>
            </div>
          </div>
        )}
      </div>

      {/* ── 2. Latest Vitals Telemetry Summary ───────────────────────────── */}
      {latestVisit && (
        <div className="space-y-2">
          <div className="flex items-center justify-between px-1">
            <h2 className="text-sm font-bold text-slate-900 flex items-center gap-2">
              <Activity className="h-4 w-4 text-blue-600" />
              Latest Physiological Telemetry &amp; Vitals
            </h2>
            <span className="text-xs text-slate-400">
              Recorded during visit on {new Date(latestVisit.createdAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
            </span>
          </div>
          <VitalsCard record={latestVisit} />
        </div>
      )}

      {/* ── 3. Chronological Clinical Visits Timeline ────────────────────── */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-card space-y-5">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-4">
          <div>
            <h2 className="text-lg font-bold text-slate-900">Clinical Encounters &amp; Records</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Complete history of consultations, SOAP clinical notes, prescriptions, and diagnostic PDF reports.
            </p>
          </div>

          <div className="flex gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
            <button
              onClick={() => setVisitsTab('active')}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 transition-all ${
                visitsTab === 'active'
                  ? 'bg-white text-slate-900 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <FileText className="h-3.5 w-3.5 text-blue-600" />
              Active Records ({activeVisits.length})
            </button>
            <button
              onClick={() => setVisitsTab('shredded')}
              className={`flex items-center gap-1.5 rounded-lg px-3 py-1.5 transition-all ${
                visitsTab === 'shredded'
                  ? 'bg-white text-slate-900 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldAlert className="h-3.5 w-3.5 text-rose-500" />
              Crypto-Shredded ({shreddedVisits.length})
            </button>
          </div>
        </div>

        {/* Active Encounters Tab */}
        {visitsTab === 'active' && (
          <div className="space-y-4">
            {activeVisits.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-slate-400">
                <FileText className="h-8 w-8 mx-auto mb-2 opacity-50" />
                <p className="text-sm font-medium text-slate-600">No active clinical encounters on file.</p>
              </div>
            ) : (
              <div className="space-y-3">
                {activeVisits.map((visit, idx) => (
                  <div
                    key={visit.id}
                    className="group rounded-xl border border-slate-200 bg-white p-5 hover:border-blue-300 hover:shadow-md transition-all space-y-3"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 text-blue-600 font-bold text-xs border border-blue-200">
                          #{activeVisits.length - idx}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-900 text-sm">
                              {visit.department || 'General Medicine'} Consultation
                            </span>
                            {visit.diagnosis && (
                              <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-100 text-slate-700 border border-slate-200">
                                {visit.diagnosis.split(' - ')[0]}
                              </span>
                            )}
                          </div>
                          <div className="flex items-center gap-3 text-xs text-slate-500 mt-0.5">
                            <span className="flex items-center gap-1">
                              <Clock className="h-3 w-3 text-slate-400" />
                              {new Date(visit.createdAt).toLocaleDateString('en-GB', {
                                day: 'numeric',
                                month: 'short',
                                year: 'numeric',
                                hour: '2-digit',
                                minute: '2-digit',
                              })}
                            </span>
                            {visit.attendingDoctor && (
                              <span className="flex items-center gap-1">
                                <Stethoscope className="h-3 w-3 text-slate-400" />
                                {visit.attendingDoctor}
                              </span>
                            )}
                          </div>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => setSelectedVisitId(visit.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-blue-50 hover:bg-blue-100 text-blue-700 text-xs font-semibold transition self-start sm:self-auto"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        View Full Chart &amp; SOAP Notes
                      </button>
                    </div>

                    {/* Chief complaint snippet */}
                    {visit.chiefComplaint && (
                      <p className="text-xs text-slate-600 bg-slate-50/70 p-2.5 rounded-lg border border-slate-100">
                        <span className="font-semibold text-slate-700">Chief Complaint:</span> {visit.chiefComplaint}
                      </p>
                    )}

                    {/* Prescriptions & Diagnostic PDF Badges */}
                    <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-100 text-xs">
                      <div className="flex flex-wrap items-center gap-1.5">
                        {visit.prescriptions && (
                          <div className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-50 border border-amber-200 text-amber-800 text-[11px]">
                            <Pill className="h-3 w-3 text-amber-600 shrink-0" />
                            <span className="truncate max-w-[280px]">{visit.prescriptions}</span>
                          </div>
                        )}
                        {visit.bloodPressure && (
                          <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 font-mono text-[11px]">
                            BP: {visit.bloodPressure}
                          </span>
                        )}
                        {visit.heartRate && (
                          <span className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-600 font-mono text-[11px]">
                            HR: {visit.heartRate} bpm
                          </span>
                        )}
                      </div>

                      {/* Attachments */}
                      {visit.attachments && visit.attachments.length > 0 && (
                        <div className="flex items-center gap-1.5">
                          {visit.attachments.map((att) => (
                            <button
                              key={att.id}
                              type="button"
                              onClick={() => setSelectedVisitId(visit.id)}
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 text-indigo-700 text-[11px] font-medium transition"
                            >
                              <File className="h-3 w-3 text-indigo-600" />
                              <span>{att.fileName}</span>
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Crypto-Shredded Encounters Tab */}
        {visitsTab === 'shredded' && (
          <div className="space-y-4">
            {shreddedVisits.length === 0 ? (
              <div className="rounded-xl border border-dashed border-slate-200 p-8 text-center text-slate-400">
                <ShieldCheck className="h-8 w-8 mx-auto mb-2 text-emerald-500" />
                <p className="text-sm font-medium text-slate-600">No crypto-shredded records for your profile.</p>
                <p className="text-xs text-slate-400 mt-1">
                  All active clinical records remain securely encrypted under your active Vault KMS keys.
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {shreddedVisits.map((visit) => (
                  <div
                    key={visit.id}
                    className="rounded-xl border border-rose-200 bg-rose-50/40 p-4 space-y-3"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                      <div className="flex items-center gap-3">
                        <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-rose-100 text-rose-700">
                          <Lock className="h-4 w-4" />
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-900 text-sm">
                              Clinical Encounter (Crypto-Shredded)
                            </span>
                            <span className="text-[10px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-rose-100 text-rose-700 border border-rose-300">
                              GDPR Art. 17 Forgotten
                            </span>
                          </div>
                          <span className="text-xs text-slate-500 font-mono">Visit ID: {visit.id}</span>
                        </div>
                      </div>

                      <button
                        type="button"
                        onClick={() => setSelectedProofVisitId(visit.id)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white hover:bg-slate-50 border border-slate-300 text-slate-700 text-xs font-semibold transition self-start sm:self-auto shadow-sm"
                      >
                        <FileCheck2 className="h-3.5 w-3.5 text-emerald-600" />
                        View Deletion Certificate &amp; Proof
                      </button>
                    </div>

                    <div className="p-3 rounded-lg bg-white/80 border border-rose-100 text-xs text-slate-600 font-mono space-y-1">
                      <p>Ciphertext Blob: <span className="text-rose-600 font-bold">[PURGED / UNRECOVERABLE]</span></p>
                      <p>Vault KMS Key: <span className="text-slate-500">patient_visit_{visit.id} (Destroyed)</span></p>
                      <p>All clinical notes, prescriptions, and attachments permanently erased.</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── 4. Modals ────────────────────────────────────────────────────── */}
      {selectedVisitId && (
        <ViewVisitModal
          visitId={selectedVisitId}
          onClose={() => setSelectedVisitId(null)}
        />
      )}

      {selectedProofVisitId && (
        <ProofViewerModal
          isOpen={!!selectedProofVisitId}
          onClose={() => setSelectedProofVisitId(null)}
          visitId={selectedProofVisitId}
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
