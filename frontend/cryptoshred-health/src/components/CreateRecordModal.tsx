import { useState, type FormEvent, type ChangeEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { X, UserPlus, Upload, FileText, Activity } from 'lucide-react';
import apiClient from '../lib/axios';
import type { PatientRecord, PatientRecordRequest } from '../types';

interface Props {
  onClose: () => void;
  editRecord?: PatientRecord | null;
}

export default function CreateRecordModal({ onClose, editRecord }: Props) {
  const queryClient = useQueryClient();
  const isEditing = !!editRecord;

  const [form, setForm] = useState<PatientRecordRequest>({
    patientName: editRecord?.patientName ?? '',
    mrn: editRecord?.mrn ?? '',
    dateOfBirth: editRecord?.dateOfBirth ?? '',
    gender: editRecord?.gender ?? 'Female',
    bloodType: editRecord?.bloodType ?? 'O+',
    bloodPressure: editRecord?.bloodPressure ?? '',
    heartRate: editRecord?.heartRate ?? 72,
    allergies: editRecord?.allergies ?? '',
    prescriptions: editRecord?.prescriptions ?? '',
    diagnosis: editRecord?.diagnosis ?? '',
    medicalNotes: editRecord?.medicalNotes ?? '',
  });

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: async (data: PatientRecordRequest) => {
      const res = isEditing
        ? await apiClient.put<PatientRecord>(`/records/${editRecord!.id}`, data)
        : await apiClient.post<PatientRecord>('/records', data);

      const savedRecord = res.data;

      // Upload file if attached
      if (selectedFile) {
        const formData = new FormData();
        formData.append('file', selectedFile);
        await apiClient.post(`/records/${savedRecord.id}/attachments`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }

      return savedRecord;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['records'] });
      onClose();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to save record and attachment.';
      setError(msg);
    },
  });

  const handleChange =
    (field: keyof PatientRecordRequest) =>
    (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
      const val = field === 'heartRate' ? parseInt(e.target.value) || 0 : e.target.value;
      setForm((prev) => ({ ...prev, [field]: val }));
    };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate(form);
  };

  return createPortal(
    <div
      id="create-record-modal-overlay"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/80 backdrop-blur-md p-4 overflow-y-auto"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="glass-card w-full max-w-2xl p-6 animate-slide-up my-auto max-h-[90vh] overflow-y-auto border border-slate-700 shadow-2xl">
        {/* Header */}
        <div className="flex items-center justify-between mb-5 pb-4 border-b border-slate-700">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand-600/20 ring-1 ring-brand-500/30">
              <UserPlus className="h-5 w-5 text-brand-400" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">
                {isEditing ? 'Edit Clinical Record' : 'New Patient Record & Attachment'}
              </h2>
              <p className="text-xs text-slate-400">Encrypted Electronic Health Record entry</p>
            </div>
          </div>
          <button onClick={onClose} id="modal-close" className="btn-ghost p-2 text-slate-400 hover:text-white">
            <X className="h-5 w-5" />
          </button>
        </div>

        {error && (
          <div className="mb-5 rounded-xl bg-red-900/30 border border-red-700/50 px-4 py-3 text-sm text-red-400">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {/* Section 1: Demographics */}
          <div className="bg-surface/50 p-4 rounded-xl border border-slate-700/60 space-y-3">
            <h3 className="text-xs font-semibold text-brand-400 uppercase tracking-wider flex items-center gap-1.5">
              <UserPlus className="h-3.5 w-3.5" /> Patient Demographics
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label htmlFor="patient-name" className="label">Full Name *</label>
                <input
                  id="patient-name"
                  required
                  value={form.patientName}
                  onChange={handleChange('patientName')}
                  placeholder="Eleanor Vance"
                  className="input-field py-2 text-sm"
                />
              </div>

              <div>
                <label htmlFor="mrn" className="label">MRN (Medical Record No.)</label>
                <input
                  id="mrn"
                  value={form.mrn}
                  onChange={handleChange('mrn')}
                  placeholder="e.g. MRN-90482 (Auto-generated if empty)"
                  className="input-field py-2 text-sm font-mono"
                />
              </div>

              <div>
                <label htmlFor="dob" className="label">Date of Birth</label>
                <input
                  id="dob"
                  type="date"
                  value={form.dateOfBirth}
                  onChange={handleChange('dateOfBirth')}
                  className="input-field py-2 text-sm"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label htmlFor="gender" className="label">Gender</label>
                  <select
                    id="gender"
                    value={form.gender}
                    onChange={handleChange('gender')}
                    className="input-field py-2 text-sm bg-surface"
                  >
                    <option value="Female">Female</option>
                    <option value="Male">Male</option>
                    <option value="Other">Other</option>
                  </select>
                </div>
                <div>
                  <label htmlFor="bloodType" className="label">Blood Type</label>
                  <select
                    id="bloodType"
                    value={form.bloodType}
                    onChange={handleChange('bloodType')}
                    className="input-field py-2 text-sm bg-surface font-mono"
                  >
                    {['A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'].map((bt) => (
                      <option key={bt} value={bt}>{bt}</option>
                    ))}
                  </select>
                </div>
              </div>
            </div>
          </div>

          {/* Section 2: Vitals */}
          <div className="bg-surface/50 p-4 rounded-xl border border-slate-700/60 space-y-3">
            <h3 className="text-xs font-semibold text-emerald-400 uppercase tracking-wider flex items-center gap-1.5">
              <Activity className="h-3.5 w-3.5" /> Clinical Vitals
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <label htmlFor="bp" className="label">Blood Pressure</label>
                <input
                  id="bp"
                  value={form.bloodPressure}
                  onChange={handleChange('bloodPressure')}
                  placeholder="120/80 mmHg"
                  className="input-field py-2 text-sm font-mono"
                />
              </div>

              <div>
                <label htmlFor="hr" className="label">Heart Rate (bpm)</label>
                <input
                  id="hr"
                  type="number"
                  value={form.heartRate}
                  onChange={handleChange('heartRate')}
                  placeholder="72"
                  className="input-field py-2 text-sm font-mono"
                />
              </div>
            </div>
          </div>

          {/* Section 3: Diagnosis & Prescriptions */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label htmlFor="allergies" className="label">Allergies & Sensitivities</label>
              <input
                id="allergies"
                value={form.allergies || ''}
                onChange={handleChange('allergies')}
                placeholder="Penicillin, Latex, etc."
                className="input-field py-2 text-sm"
              />
            </div>
            <div>
              <label htmlFor="diagnosis" className="label">Primary Diagnosis</label>
              <input
                id="diagnosis"
                value={form.diagnosis}
                onChange={handleChange('diagnosis')}
                placeholder="e.g. Type 2 Diabetes Mellitus"
                className="input-field py-2 text-sm"
              />
            </div>
          </div>

          <div>
            <label htmlFor="prescriptions" className="label">Active Prescriptions & Dosage</label>
            <input
              id="prescriptions"
              value={form.prescriptions || ''}
              onChange={handleChange('prescriptions')}
              placeholder="e.g. Metformin 500mg BD, Lisinopril 10mg OD"
              className="input-field py-2 text-sm"
            />
          </div>

          <div>
            <label htmlFor="medical-notes" className="label">Clinical Observations & Notes</label>
            <textarea
              id="medical-notes"
              value={form.medicalNotes}
              onChange={handleChange('medicalNotes')}
              placeholder="Detailed progress notes, assessment..."
              rows={3}
              className="input-field py-2 text-sm resize-none"
            />
          </div>

          {/* Section 4: File Upload */}
          <div className="bg-surface/60 p-4 rounded-xl border border-dashed border-slate-600 hover:border-brand-500 transition-colors">
            <label className="label mb-2 flex items-center justify-between">
              <span className="flex items-center gap-1.5 text-slate-200">
                <FileText className="h-4 w-4 text-brand-400" /> Upload Medical PDF / Attachment
              </span>
              <span className="text-xs text-slate-400">Encrypted on upload</span>
            </label>
            <div className="flex items-center gap-3">
              <label className="btn-ghost bg-surface hover:bg-slate-700 text-xs px-3 py-2 border border-slate-600 rounded-xl cursor-pointer flex items-center gap-2">
                <Upload className="h-4 w-4 text-brand-400" />
                <span>{selectedFile ? 'Change File' : 'Choose File (PDF/Image)'}</span>
                <input
                  type="file"
                  accept="application/pdf,image/*"
                  onChange={handleFileChange}
                  className="hidden"
                />
              </label>
              {selectedFile && (
                <span className="text-xs text-emerald-400 font-mono flex items-center gap-1 truncate max-w-xs">
                  <FileText className="h-3.5 w-3.5" /> {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
                </span>
              )}
            </div>
          </div>

          <div className="flex gap-3 pt-3 border-t border-slate-700">
            <button type="button" onClick={onClose} className="btn-ghost flex-1">
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              id="save-record-btn"
              className="btn-primary flex-1"
            >
              {mutation.isPending ? (
                <div className="flex items-center justify-center gap-2">
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Encrypting & Saving...</span>
                </div>
              ) : isEditing ? (
                'Update Record'
              ) : (
                'Create Encrypted Record'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
