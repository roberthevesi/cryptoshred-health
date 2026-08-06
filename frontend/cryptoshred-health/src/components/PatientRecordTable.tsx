import { useState, Fragment } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus,
  Pencil,
  Trash2,
  ShieldOff,
  FileText,
  RefreshCw,
  Eye,
  Activity,
  Heart,
  ChevronDown,
  ChevronUp,
  File,
  AlertTriangle,
  Pill,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import CreateRecordModal from './CreateRecordModal';
import PdfViewerModal from './PdfViewerModal';
import ViewRecordModal from './ViewRecordModal';
import type { PatientRecord, PatientAttachment } from '../types';

export default function PatientRecordTable() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [showModal, setShowModal] = useState(false);
  const [editRecord, setEditRecord] = useState<PatientRecord | null>(null);
  const [viewRecordId, setViewRecordId] = useState<string | null>(null);

  const [viewPdfState, setViewPdfState] = useState<{ recordId: string; attachment: PatientAttachment } | null>(null);
  const [expandedRows, setExpandedRows] = useState<Record<string, boolean>>({});


  const { data: records = [], isLoading, isError, refetch } = useQuery<PatientRecord[]>({
    queryKey: ['records'],
    queryFn: () => apiClient.get<PatientRecord[]>('/records').then((r) => r.data),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/records/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['records'] }),
  });

  const toggleRow = (id: string) => {
    setExpandedRows((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const handleEdit = (record: PatientRecord) => {
    setEditRecord(record);
    setShowModal(true);
  };

  const handleDelete = (id: string) => {
    if (confirm('Permanently delete this record?')) {
      deleteMutation.mutate(id);
    }
  };

  const isDoctor = user?.role === 'DOCTOR';

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-brand-500 border-t-transparent" />
        <p className="text-slate-400 text-sm font-mono">Loading EHR records & key status...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3 text-center">
        <ShieldOff className="h-10 w-10 text-red-400" />
        <p className="text-slate-300 font-medium">Failed to load health records</p>
        <button onClick={() => refetch()} className="btn-ghost">
          <RefreshCw className="h-4 w-4" /> Retry
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="flex items-center justify-between mb-5">
        <div>
          <h2 className="text-lg font-bold text-white flex items-center gap-2">
            Electronic Health Records
            <span className="text-xs font-mono font-normal bg-brand-950/60 text-brand-400 border border-brand-800/60 px-2 py-0.5 rounded-full">
              AES-256
            </span>
          </h2>
          <p className="text-sm text-slate-400">{records.length} patient record(s) indexed</p>
        </div>
        {isDoctor && (
          <button
            id="new-record-btn"
            onClick={() => {
              setEditRecord(null);
              setShowModal(true);
            }}
            className="btn-primary"
          >
            <Plus className="h-4 w-4" /> Add Record & Attachment
          </button>
        )}
      </div>

      {records.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3 text-center">
          <FileText className="h-12 w-12 text-slate-600" />
          <p className="text-slate-400">No patient records found in database.</p>
          {isDoctor && (
            <button
              onClick={() => {
                setEditRecord(null);
                setShowModal(true);
              }}
              className="btn-primary mt-2"
            >
              <Plus className="h-4 w-4" /> Create First Record
            </button>
          )}
        </div>
      ) : (
        <div className="overflow-x-auto rounded-xl border border-slate-700 bg-surface/50">
          <table className="w-full text-sm text-left border-collapse">
            <thead>
              <tr className="border-b border-slate-700 bg-surface text-slate-400 text-xs font-semibold uppercase tracking-wider">
                <th className="w-10 px-3 py-3 text-center"></th>
                <th className="px-4 py-3">Patient / MRN</th>
                <th className="px-4 py-3">Vitals &amp; Metrics</th>
                <th className="px-4 py-3">Diagnosis</th>
                <th className="px-4 py-3">Documents / PDFs</th>
                <th className="px-4 py-3">Crypto Status</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {records.map((record) => {
                const isExpanded = !!expandedRows[record.id];
                const hasAttachments = record.attachments && record.attachments.length > 0;

                return (
                  <Fragment key={record.id}>
                    <tr className={`border-b border-slate-800/80 transition-colors hover:bg-slate-800/40 ${record.shredded ? 'bg-red-950/10' : ''}`}>
                      {/* 1. Expand Toggle */}
                      <td className="px-3 py-4 text-center">
                        <button
                          onClick={() => toggleRow(record.id)}
                          className="p-1 rounded hover:bg-slate-700 text-slate-400 hover:text-white transition-colors"
                          title="Toggle details"
                        >
                          {isExpanded ? <ChevronUp className="h-4 w-4 text-brand-400" /> : <ChevronDown className="h-4 w-4" />}
                        </button>
                      </td>

                      {/* 2. Patient / MRN */}
                      <td className="px-4 py-4">
                        <p className="font-semibold text-white text-sm flex items-center gap-2">
                          {record.patientName}
                          {record.gender && (
                            <span className="text-[10px] text-slate-400 bg-slate-800 px-1.5 py-0.5 rounded font-normal">
                              {record.gender}
                            </span>
                          )}
                        </p>
                        <p className="text-xs text-brand-400 font-mono mt-0.5">
                          {record.mrn || 'NO-MRN'} • DOB: {record.dateOfBirth || 'N/A'}
                        </p>
                      </td>

                      {/* 3. Vitals & Metrics */}
                      <td className="px-4 py-4 text-xs">
                        {record.shredded ? (
                          <span className="text-red-400/80 italic font-mono">[KEY SHREDDED]</span>
                        ) : (
                          <div className="space-y-1">
                            <div className="flex items-center gap-2 text-slate-300">
                              <Activity className="h-3.5 w-3.5 text-emerald-400 shrink-0" />
                              <span className="font-mono">{record.bloodPressure || '120/80 mmHg'}</span>
                            </div>
                            <div className="flex items-center gap-2 text-slate-400">
                              <Heart className="h-3.5 w-3.5 text-rose-400 shrink-0" />
                              <span className="font-mono">{record.heartRate ? `${record.heartRate} bpm` : '72 bpm'}</span>
                              {record.bloodType && (
                                <span className="font-mono text-[10px] bg-red-950 text-red-300 border border-red-800/80 px-1.5 py-0.5 rounded">
                                  {record.bloodType}
                                </span>
                              )}
                            </div>
                          </div>
                        )}
                      </td>

                      {/* 4. Diagnosis */}
                      <td className="px-4 py-4 text-xs text-slate-300">
                        {record.shredded ? (
                          <span className="text-red-400 italic">[SHREDDED]</span>
                        ) : (
                          <span className="font-medium text-slate-200 line-clamp-2">
                            {record.diagnosis || 'General Examination'}
                          </span>
                        )}
                      </td>

                      {/* 5. Documents / PDFs */}
                      <td className="px-4 py-4 text-xs">
                        {hasAttachments ? (
                          <div className="flex flex-col gap-1.5">
                            {record.attachments!.map((att) => (
                              <button
                                key={att.id}
                                onClick={() => setViewPdfState({ recordId: record.id, attachment: att })}
                                className={`text-xs px-2.5 py-1 rounded-lg border transition-all flex items-center gap-1.5 w-fit ${
                                  att.shredded || record.shredded
                                    ? 'bg-red-950/40 border-red-800/60 text-red-400 hover:bg-red-900/60'
                                    : 'bg-brand-950/60 border-brand-700/60 text-brand-300 hover:bg-brand-900/80 hover:text-white'
                                }`}
                              >
                                <File className="h-3.5 w-3.5 shrink-0 text-brand-400" />
                                <span className="truncate max-w-[130px] font-mono">{att.fileName}</span>
                                <Eye className="h-3 w-3 opacity-70" />
                              </button>
                            ))}
                          </div>
                        ) : (
                          <span className="text-xs text-slate-500 italic">No PDF attached</span>
                        )}
                      </td>

                      {/* 6. Crypto Status */}
                      <td className="px-4 py-4 text-xs">
                        {record.shredded ? (
                          <span className="badge-shredded">CRYPTO-SHREDDED</span>
                        ) : (
                          <span className="badge-active">AES-256 ACTIVE</span>
                        )}
                      </td>

                      {/* 7. Actions */}
                      <td className="px-4 py-4 text-right">
                        <div className="inline-flex items-center justify-end gap-1">
                          <button
                            onClick={() => setViewRecordId(record.id)}
                            className="btn-ghost px-2 py-1 text-emerald-400 hover:text-emerald-300 hover:bg-emerald-950/60 flex items-center gap-1 text-xs font-mono border border-emerald-800/40 rounded-lg"
                            title="Fetch & View Details from Redis Cache"
                          >
                            <Eye className="h-3.5 w-3.5" />
                            <span>View (Cache)</span>
                          </button>

                          {isDoctor && !record.shredded && (
                            <>
                              <button
                                onClick={() => handleEdit(record)}
                                className="btn-ghost p-1.5 text-slate-400 hover:text-white"
                                title="Edit record"
                              >
                                <Pencil className="h-4 w-4" />
                              </button>
                              <button
                                onClick={() => handleDelete(record.id)}
                                className="btn-ghost p-1.5 text-slate-400 hover:text-red-400"
                                title="Delete record"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </>
                          )}
                          {record.shredded && (
                            <ShieldOff className="h-4 w-4 text-red-400/80 inline ml-1" />
                          )}
                        </div>
                      </td>
                    </tr>

                    {/* Expanded Drawer Row */}
                    {isExpanded && (
                      <tr className="bg-slate-900/90 border-b border-slate-800">
                        <td colSpan={7} className="px-8 py-4 text-xs space-y-3">
                          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <div>
                              <p className="text-slate-400 font-semibold mb-1 flex items-center gap-1.5">
                                <AlertTriangle className="h-3.5 w-3.5 text-amber-400" /> Allergies & Contraindications
                              </p>
                              <p className="text-slate-200 bg-surface p-2.5 rounded-xl border border-slate-800">
                                {record.shredded ? '[SHREDDED]' : record.allergies || 'None Known'}
                              </p>
                            </div>

                            <div>
                              <p className="text-slate-400 font-semibold mb-1 flex items-center gap-1.5">
                                <Pill className="h-3.5 w-3.5 text-brand-400" /> Active Prescriptions & Dosage
                              </p>
                              <p className="text-slate-200 bg-surface p-2.5 rounded-xl border border-slate-800 font-mono">
                                {record.shredded ? '[SHREDDED]' : record.prescriptions || 'None Recorded'}
                              </p>
                            </div>
                          </div>

                          <div>
                            <p className="text-slate-400 font-semibold mb-1 flex items-center gap-1.5">
                              <FileText className="h-3.5 w-3.5 text-emerald-400" /> Clinical Observations & Medical Notes
                            </p>
                            <p className="text-slate-200 bg-surface p-3 rounded-xl border border-slate-800 whitespace-pre-wrap leading-relaxed">
                              {record.shredded ? '[SHREDDED]' : record.medicalNotes || 'No notes attached.'}
                            </p>
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {/* Edit / Create Modal */}
      {showModal && (
        <CreateRecordModal
          editRecord={editRecord}
          onClose={() => {
            setShowModal(false);
            setEditRecord(null);
          }}
        />
      )}

      {/* View Record (Redis Cache) Modal */}
      {viewRecordId && (
        <ViewRecordModal
          recordId={viewRecordId}
          onClose={() => setViewRecordId(null)}
          onViewPdf={(recId, att) => setViewPdfState({ recordId: recId, attachment: att })}
        />
      )}

      {/* PDF Viewer Modal */}
      {viewPdfState && (
        <PdfViewerModal
          recordId={viewPdfState.recordId}
          attachment={viewPdfState.attachment}
          onClose={() => setViewPdfState(null)}
        />
      )}
    </>
  );
}

