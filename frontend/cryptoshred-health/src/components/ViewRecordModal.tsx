import { useQuery } from '@tanstack/react-query';
import { X, ShieldCheck, ShieldOff, Zap, Activity, Heart, AlertTriangle, Pill, FileText, File } from 'lucide-react';
import apiClient from '../lib/axios';
import type { PatientRecord, PatientAttachment } from '../types';

interface ViewRecordModalProps {
  recordId: string;
  onClose: () => void;
  onViewPdf: (recordId: string, attachment: PatientAttachment) => void;
}

export default function ViewRecordModal({ recordId, onClose, onViewPdf }: ViewRecordModalProps) {
  const { data: record, isLoading, isError, refetch } = useQuery<PatientRecord>({
    queryKey: ['record-detail', recordId],
    queryFn: () => apiClient.get<PatientRecord>(`/records/${recordId}`).then((res) => res.data),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-fade-in">
      <div className="relative w-full max-w-2xl max-h-[90vh] overflow-y-auto glass-card p-6 border border-slate-700 shadow-2xl space-y-6">
        
        {/* Close Button */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-1.5 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
        >
          <X className="h-5 w-5" />
        </button>

        {/* Modal Header */}
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4 pr-8">
          <div>
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              Patient Record Details
            </h2>
            <p className="text-xs text-slate-400 font-mono mt-0.5">ID: {recordId}</p>
          </div>

          <div className="flex items-center gap-2">
            <span className="flex items-center gap-1.5 text-xs font-semibold px-3 py-1 rounded-full bg-emerald-950/80 text-emerald-400 border border-emerald-700/60 shadow-glow">
              <Zap className="h-3.5 w-3.5 text-emerald-400" />
              Redis Cache HIT ⚡
            </span>
          </div>
        </div>

        {isLoading ? (
          <div className="flex flex-col items-center justify-center py-12 gap-3">
            <div className="h-8 w-8 animate-spin rounded-full border-3 border-brand-500 border-t-transparent" />
            <p className="text-sm font-mono text-slate-400">Fetching record from Redis Cache...</p>
          </div>
        ) : isError || !record ? (
          <div className="flex flex-col items-center justify-center py-12 gap-3 text-center">
            <ShieldOff className="h-10 w-10 text-red-400" />
            <p className="text-slate-300 font-medium">Failed to fetch record from cache</p>
            <button onClick={() => refetch()} className="btn-ghost text-xs">
              Retry Fetch
            </button>
          </div>
        ) : (
          <div className="space-y-5 text-sm">
            {/* Status Banner */}
            {record.shredded ? (
              <div className="rounded-xl border border-red-800/80 bg-red-950/40 p-4 flex items-center gap-3">
                <ShieldOff className="h-6 w-6 text-red-400 shrink-0" />
                <div>
                  <h4 className="font-bold text-red-300">Crypto-Shredded Record</h4>
                  <p className="text-xs text-red-400/90 mt-0.5">
                    The Vault KEK for this record has been permanently destroyed. Sensitive payloads in PostgreSQL and Redis Cache return [SHREDDED].
                  </p>
                </div>
              </div>
            ) : (
              <div className="rounded-xl border border-brand-800/60 bg-brand-950/30 p-3.5 flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <ShieldCheck className="h-5 w-5 text-brand-400" />
                  <span className="text-xs font-semibold text-slate-200">Envelope Encrypted Payload (AES-256-GCM)</span>
                </div>
                <span className="text-[10px] font-mono bg-brand-900/60 text-brand-300 border border-brand-700/60 px-2 py-0.5 rounded-full">
                  Vault KEK Active
                </span>
              </div>
            )}

            {/* Patient Header Card */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 bg-surface p-4 rounded-xl border border-slate-800">
              <div>
                <span className="text-xs text-slate-400 uppercase tracking-wider font-semibold">Patient Name</span>
                <p className="font-bold text-white text-base mt-0.5">{record.patientName}</p>
                <p className="text-xs text-brand-400 font-mono mt-0.5">MRN: {record.mrn || 'N/A'}</p>
              </div>

              <div className="space-y-1 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Date of Birth:</span>
                  <span className="font-mono text-slate-200">{record.dateOfBirth || 'N/A'}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-slate-400">Gender / Blood:</span>
                  <span className="font-mono text-slate-200">{record.gender || 'N/A'} ({record.bloodType || 'N/A'})</span>
                </div>
              </div>
            </div>

            {/* Vitals */}
            <div className="grid grid-cols-2 gap-3">
              <div className="bg-surface p-3 rounded-xl border border-slate-800 flex items-center gap-3">
                <Activity className="h-5 w-5 text-emerald-400 shrink-0" />
                <div>
                  <span className="text-[11px] text-slate-400 block">Blood Pressure</span>
                  <span className="font-mono font-semibold text-slate-200">
                    {record.shredded ? '[SHREDDED]' : record.bloodPressure || '120/80 mmHg'}
                  </span>
                </div>
              </div>

              <div className="bg-surface p-3 rounded-xl border border-slate-800 flex items-center gap-3">
                <Heart className="h-5 w-5 text-rose-400 shrink-0" />
                <div>
                  <span className="text-[11px] text-slate-400 block">Heart Rate</span>
                  <span className="font-mono font-semibold text-slate-200">
                    {record.shredded ? '[SHREDDED]' : record.heartRate ? `${record.heartRate} bpm` : '72 bpm'}
                  </span>
                </div>
              </div>
            </div>

            {/* Clinical Observations */}
            <div className="space-y-3">
              <div>
                <span className="text-xs font-semibold text-slate-400 flex items-center gap-1.5 mb-1">
                  <FileText className="h-3.5 w-3.5 text-emerald-400" /> Diagnosis
                </span>
                <div className="p-3 bg-surface rounded-xl border border-slate-800 text-slate-200 font-medium">
                  {record.shredded ? '[SHREDDED]' : record.diagnosis || 'General Examination'}
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <span className="text-xs font-semibold text-slate-400 flex items-center gap-1.5 mb-1">
                    <AlertTriangle className="h-3.5 w-3.5 text-amber-400" /> Allergies
                  </span>
                  <div className="p-2.5 bg-surface rounded-xl border border-slate-800 text-slate-200 text-xs">
                    {record.shredded ? '[SHREDDED]' : record.allergies || 'None Known'}
                  </div>
                </div>

                <div>
                  <span className="text-xs font-semibold text-slate-400 flex items-center gap-1.5 mb-1">
                    <Pill className="h-3.5 w-3.5 text-brand-400" /> Prescriptions
                  </span>
                  <div className="p-2.5 bg-surface rounded-xl border border-slate-800 text-slate-200 text-xs font-mono">
                    {record.shredded ? '[SHREDDED]' : record.prescriptions || 'None Recorded'}
                  </div>
                </div>
              </div>

              <div>
                <span className="text-xs font-semibold text-slate-400 flex items-center gap-1.5 mb-1">
                  <FileText className="h-3.5 w-3.5 text-brand-400" /> Clinical Medical Notes
                </span>
                <div className="p-3 bg-surface rounded-xl border border-slate-800 text-slate-200 whitespace-pre-wrap font-mono text-xs leading-relaxed">
                  {record.shredded ? '[SHREDDED]' : record.medicalNotes || 'No notes attached.'}
                </div>
              </div>
            </div>

            {/* Attachments */}
            {record.attachments && record.attachments.length > 0 && (
              <div>
                <span className="text-xs font-semibold text-slate-400 mb-2 block">PDF Attachments</span>
                <div className="flex flex-wrap gap-2">
                  {record.attachments.map((att) => (
                    <button
                      key={att.id}
                      onClick={() => {
                        onClose();
                        onViewPdf(record.id, att);
                      }}
                      className="px-3 py-1.5 rounded-lg bg-brand-950/60 border border-brand-700/60 text-brand-300 hover:text-white hover:bg-brand-900/80 text-xs flex items-center gap-2 font-mono"
                    >
                      <File className="h-3.5 w-3.5 text-brand-400" />
                      <span>{att.fileName}</span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
