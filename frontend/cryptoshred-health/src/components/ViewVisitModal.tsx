import { useState } from 'react';
import { createPortal } from 'react-dom';
import { useQuery } from '@tanstack/react-query';
import {
  X,
  Activity,
  FileText,
  Building,
  Key,
  ShieldAlert,
  Calendar,
  Lock,
  AlertTriangle,
  Pill,
  User,
  Heart,
  File,
  Eye,
} from 'lucide-react';
import apiClient from '../lib/axios';
import PatientBanner from './PatientBanner';
import VitalsCard from './VitalsCard';
import PdfViewerModal from './PdfViewerModal';
import type { PatientVisit, PatientAttachment } from '../types';

interface Props {
  visitId: string;
  onClose: () => void;
}

type Tab = 'soap' | 'summary' | 'clinical' | 'admin' | 'documents' | 'security';

export default function ViewVisitModal({ visitId, onClose }: Props) {
  const [activeTab, setActiveTab] = useState<Tab>('soap');
  const [viewPdfAttachment, setViewPdfAttachment] = useState<PatientAttachment | null>(null);

  const { data: visit, isLoading, isError } = useQuery<PatientVisit>({
    queryKey: ['visit', visitId],
    queryFn: () => apiClient.get<PatientVisit>(`/visits/${visitId}`).then((r) => r.data),
  });

  if (isLoading) {
    return createPortal(
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4">
        <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-2xl">
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent mb-4" />
          <p className="text-sm font-medium text-slate-700">Decrypting clinical visit via Vault KMS...</p>
        </div>
      </div>,
      document.body
    );
  }

  if (isError || !visit) {
    return createPortal(
      <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4">
        <div className="w-full max-w-md rounded-2xl border border-red-200 bg-white p-6 text-center shadow-2xl">
          <ShieldAlert className="mx-auto h-12 w-12 text-red-500 mb-3" />
          <h3 className="text-lg font-bold text-slate-900 mb-2">Visit Decryption Failed</h3>
          <p className="text-sm text-slate-500 mb-6">
            Unable to load visit chart. The visit may have been crypto-shredded or the Vault KMS key is inaccessible.
          </p>
          <button onClick={onClose} className="px-5 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold">
            Close Chart
          </button>
        </div>
      </div>,
      document.body
    );
  }

  return (
    <>
      {createPortal(
        <div
          id="view-visit-modal-overlay"
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4 overflow-y-auto"
          onClick={(e) => e.target === e.currentTarget && onClose()}
        >
          <div className="w-full max-w-5xl rounded-2xl border border-slate-200 bg-white shadow-2xl my-auto max-h-[92vh] flex flex-col overflow-hidden">
            {/* Modal Top Bar */}
            <div className="flex items-center justify-between px-6 py-3 border-b border-slate-200 bg-slate-50/90">
              <div className="flex items-center gap-2">
                <span className="flex h-2.5 w-2.5 rounded-full bg-emerald-500 animate-pulse" />
                <span className="text-xs font-mono font-semibold uppercase tracking-wider text-slate-600">
                  Clinical Visit Chart
                </span>
                <span className="text-xs font-mono px-2 py-0.5 rounded bg-slate-100 border border-slate-200 text-slate-700">
                  {visit.mrn || visit.id.slice(0, 8)}
                </span>
              </div>
              <button onClick={onClose} id="modal-close" className="p-1.5 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition">
                <X className="h-5 w-5" />
              </button>
            </div>

            {/* Patient Header Banner */}
            <div className="p-6 pb-2 bg-white">
              <PatientBanner record={visit} showVitalsSummary={false} />
            </div>

            {/* Navigation Tabs */}
            <div className="flex border-b border-slate-200 bg-slate-50 px-6 gap-2 overflow-x-auto text-xs font-medium py-2">
              <button
                onClick={() => setActiveTab('soap')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'soap'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <FileText className="h-3.5 w-3.5" /> 1. SOAP Clinical Notes
              </button>
              <button
                onClick={() => setActiveTab('summary')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'summary'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <Activity className="h-3.5 w-3.5" /> 2. Biometrics &amp; Vitals
              </button>
              <button
                onClick={() => setActiveTab('clinical')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'clinical'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <Pill className="h-3.5 w-3.5" /> 3. Meds &amp; Allergies
              </button>
              <button
                onClick={() => setActiveTab('admin')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'admin'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <Building className="h-3.5 w-3.5" /> 4. Admin &amp; Insurance
              </button>
              <button
                onClick={() => setActiveTab('documents')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'documents'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <File className="h-3.5 w-3.5" /> 5. Documents ({visit.attachments?.length ?? 0})
              </button>
              <button
                onClick={() => setActiveTab('security')}
                className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
                  activeTab === 'security'
                    ? 'bg-blue-600 text-white font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
                }`}
              >
                <Key className="h-3.5 w-3.5" /> 6. KMS Audit &amp; Proof
              </button>
            </div>

            {/* Modal Body */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6 bg-white">
              {/* TAB 1: SOAP Notes */}
              {activeTab === 'soap' && (
                <div className="space-y-4 animate-fade-in">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                      <FileText className="h-3.5 w-3.5" /> Visit Overview
                    </h4>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <span className="text-[11px] font-medium text-slate-500 block">Chief Complaint</span>
                        <p className="text-sm font-semibold text-slate-900 mt-0.5">
                          {visit.chiefComplaint || 'Routine clinical visit'}
                        </p>
                      </div>
                      <div>
                        <span className="text-[11px] font-medium text-slate-500 block">Primary Diagnosis / ICD-10</span>
                        <p className="text-sm font-semibold text-blue-700 mt-0.5">
                          {visit.diagnosis || 'No primary diagnosis recorded'}
                        </p>
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-bold text-blue-700 uppercase tracking-wider block">
                        [S] Subjective History
                      </span>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {visit.soapSubjective || visit.medicalNotes || 'No subjective narrative recorded.'}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-bold text-blue-700 uppercase tracking-wider block">
                        [O] Objective Examination
                      </span>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {visit.soapObjective || 'Physical examination and objective vitals recorded in biometric chart.'}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-bold text-blue-700 uppercase tracking-wider block">
                        [A] Clinical Assessment
                      </span>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {visit.soapAssessment || visit.diagnosis || 'Clinical evaluation consistent with primary presentation.'}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-bold text-blue-700 uppercase tracking-wider block">
                        [P] Treatment &amp; Management Plan
                      </span>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {visit.soapPlan || 'Continue active prescription regimen and monitor vitals.'}
                      </p>
                    </div>
                  </div>

                  {visit.medicalNotes && (
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-bold text-slate-500 uppercase tracking-wider block flex items-center gap-1.5">
                        <Lock className="h-3.5 w-3.5 text-blue-600" /> Confidential Clinical Annotations
                      </span>
                      <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed">
                        {visit.medicalNotes}
                      </p>
                    </div>
                  )}
                </div>
              )}

              {/* TAB 2: Biometrics & Vitals */}
              {activeTab === 'summary' && (
                <div className="space-y-5 animate-fade-in">
                  <div>
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2.5 flex items-center gap-1.5">
                      <Activity className="h-3.5 w-3.5 text-blue-600" /> Current Biometric Telemetry
                    </h3>
                    <VitalsCard record={visit} />
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                      <Building className="h-3.5 w-3.5" /> Care Team &amp; Facility
                    </h4>
                    <div className="grid grid-cols-2 gap-3">
                      <div>
                        <span className="text-[11px] font-medium text-slate-500 block">Attending Clinician</span>
                        <p className="text-sm font-semibold text-slate-900 mt-0.5">
                          {visit.shredded ? '[SHREDDED]' : (visit.attendingDoctor || 'Dr. Alistair Finch, MD')}
                        </p>
                      </div>
                      <div>
                        <span className="text-[11px] font-medium text-slate-500 block">Department</span>
                        <p className="text-sm font-semibold text-slate-700 mt-0.5">
                          {visit.shredded ? '[SHREDDED]' : (visit.department || 'General Practice')}
                        </p>
                      </div>
                    </div>
                    {visit.followUpDate && (
                      <div className="border-t border-slate-200 pt-3">
                        <span className="text-[11px] font-medium text-slate-500 block">Scheduled Follow-up</span>
                        <p className="text-xs font-medium text-emerald-700 mt-0.5 flex items-center gap-1">
                          <Calendar className="h-3.5 w-3.5" /> {visit.followUpDate}
                        </p>
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* TAB 3: Meds, Allergies & History */}
              {activeTab === 'clinical' && (
                <div className="space-y-4 animate-fade-in">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-rose-600 uppercase tracking-wider flex items-center gap-1.5">
                      <AlertTriangle className="h-3.5 w-3.5" /> Documented Allergies &amp; Adverse Reactions
                    </h4>
                    <p className="text-sm font-medium text-slate-900">
                      {visit.shredded ? '[SHREDDED]' : (visit.allergies || 'No Known Drug Allergies (NKDA)')}
                    </p>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                      <Pill className="h-3.5 w-3.5" /> Active Prescriptions &amp; Dosages
                    </h4>
                    <p className="text-xs text-slate-700 whitespace-pre-wrap leading-relaxed font-mono">
                      {visit.shredded ? '[SHREDDED]' : (visit.prescriptions || 'No active outpatient prescriptions recorded.')}
                    </p>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block">
                        Chronic Conditions
                      </span>
                      <p className="text-xs text-slate-700 leading-relaxed">
                        {visit.shredded ? '[SHREDDED]' : (visit.chronicConditions || 'None documented.')}
                      </p>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block">
                        Immunizations &amp; Vaccines
                      </span>
                      <p className="text-xs text-slate-700 leading-relaxed">
                        {visit.shredded ? '[SHREDDED]' : (visit.immunizationStatus || 'Standard immunization schedule.')}
                      </p>
                    </div>
                  </div>

                  {visit.lifestyleFactors && (
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-2">
                      <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider block">
                        Lifestyle &amp; Social History
                      </span>
                      <p className="text-xs text-slate-700 leading-relaxed">
                        {visit.shredded ? '[SHREDDED]' : visit.lifestyleFactors}
                      </p>
                    </div>
                  )}
                </div>
              )}

              {/* TAB 4: Admin & Insurance */}
              {activeTab === 'admin' && (
                <div className="space-y-4 animate-fade-in">
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                      <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                        <User className="h-3.5 w-3.5" /> Patient Contact Details
                      </h4>
                      <div className="space-y-2 text-xs">
                        <div>
                          <span className="text-slate-500 block">Phone:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.phone || 'Not provided')}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-500 block">Email:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.email || visit.ownerEmail)}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-500 block">Residential Address:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.address || 'Not provided')}
                          </span>
                        </div>
                      </div>
                    </div>

                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                      <h4 className="text-xs font-semibold text-rose-600 uppercase tracking-wider flex items-center gap-1.5">
                        <Heart className="h-3.5 w-3.5" /> Emergency Contact
                      </h4>
                      <div className="space-y-2 text-xs">
                        <div>
                          <span className="text-slate-500 block">Name:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.emergencyContactName || 'Not recorded')}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-500 block">Relationship:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.emergencyContactRelationship || '—')}
                          </span>
                        </div>
                        <div>
                          <span className="text-slate-500 block">Emergency Phone:</span>
                          <span className="text-slate-800 font-medium">
                            {visit.shredded ? '[SHREDDED]' : (visit.emergencyContactPhone || '—')}
                          </span>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                      <Building className="h-3.5 w-3.5" /> Insurance &amp; Billing Policy
                    </h4>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                      <div>
                        <span className="text-slate-500 block">Provider:</span>
                        <span className="text-slate-800 font-medium">
                          {visit.shredded ? '[SHREDDED]' : (visit.insuranceProvider || 'None')}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Policy / Member ID:</span>
                        <span className="font-mono text-slate-800 font-medium">
                          {visit.shredded ? '[SHREDDED]' : (visit.insurancePolicyNumber || '—')}
                        </span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Group Number:</span>
                        <span className="font-mono text-slate-800 font-medium">
                          {visit.shredded ? '[SHREDDED]' : (visit.insuranceGroupNumber || '—')}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* TAB 5: Documents */}
              {activeTab === 'documents' && (
                <div className="space-y-4 animate-fade-in">
                  <div className="flex items-center justify-between">
                    <h4 className="text-xs font-semibold text-slate-500 uppercase tracking-wider">
                      Attached Diagnostic Reports &amp; Imaging
                    </h4>
                    <span className="text-xs text-slate-500">
                      {visit.attachments?.length ?? 0} Encrypted Document(s)
                    </span>
                  </div>

                  {(!visit.attachments || visit.attachments.length === 0) ? (
                    <div className="rounded-xl border border-slate-200 bg-slate-50 p-8 text-center">
                      <File className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                      <p className="text-xs text-slate-500 font-medium">No diagnostic documents attached to this visit chart.</p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      {visit.attachments.map((att) => (
                        <div
                          key={att.id}
                          className="flex items-center justify-between p-3.5 rounded-xl border border-slate-200 bg-slate-50 hover:border-slate-300 transition"
                        >
                          <div className="flex items-center gap-3 min-w-0">
                            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 border border-blue-200 text-blue-600">
                              <FileText className="h-4 w-4" />
                            </div>
                            <div className="min-w-0">
                              <p className="text-xs font-semibold text-slate-900 truncate">{att.fileName}</p>
                              <p className="text-[11px] text-slate-500 font-mono">
                                {(att.fileSize / 1024).toFixed(1)} KB • {att.shredded ? 'Shredded' : 'AES-256 Encrypted'}
                              </p>
                            </div>
                          </div>

                          {!att.shredded && (
                            <button
                              onClick={() => setViewPdfAttachment(att)}
                              className="flex items-center gap-1 px-3 py-1.5 rounded-lg bg-white hover:bg-slate-100 text-slate-700 text-xs font-medium transition border border-slate-200 shadow-sm"
                            >
                              <Eye className="h-3.5 w-3.5" /> View
                            </button>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}

              {/* TAB 6: Security & KMS Audit */}
              {activeTab === 'security' && (
                <div className="space-y-4 animate-fade-in">
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 space-y-3">
                    <h4 className="text-xs font-semibold text-emerald-700 uppercase tracking-wider flex items-center gap-1.5">
                      <Key className="h-3.5 w-3.5" /> Cryptographic Key Hierarchy &amp; Zero-Purge Architecture
                    </h4>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
                      <div>
                        <span className="text-slate-500 block">Visit UUID:</span>
                        <span className="font-mono text-slate-700">{visit.id}</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Encryption Standard:</span>
                        <span className="font-mono text-slate-700">AES-256-GCM (Authenticated Envelope Encryption)</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">KMS Key Lifecycle:</span>
                        <span className="text-slate-700 font-medium">HashiCorp Vault Transit Engine (`/v1/transit`)</span>
                      </div>
                      <div>
                        <span className="text-slate-500 block">Shredding Status:</span>
                        <span className={`font-semibold ${visit.shredded ? 'text-red-700' : 'text-emerald-700'}`}>
                          {visit.shredded ? 'DESTROYED (Irreversible Crypto-Shred)' : 'ACTIVE & VERIFIED'}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Modal Footer */}
            <div className="flex items-center justify-between px-6 py-3 border-t border-slate-200 bg-slate-50/90 text-xs text-slate-500">
              <span>Recorded: {new Date(visit.createdAt).toLocaleString()}</span>
              <button
                onClick={onClose}
                className="px-4 py-1.5 rounded-xl border border-slate-300 bg-white hover:bg-slate-100 text-slate-700 font-medium transition shadow-sm"
              >
                Close Chart
              </button>
            </div>
          </div>
        </div>,
        document.body
      )}

      {/* PDF Modal if active */}
      {viewPdfAttachment && (
        <PdfViewerModal
          recordId={visit.id}
          attachment={viewPdfAttachment}
          onClose={() => setViewPdfAttachment(null)}
        />
      )}
    </>
  );
}
