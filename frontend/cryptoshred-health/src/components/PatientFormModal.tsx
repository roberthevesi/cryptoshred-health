import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { X, UserPlus, UserCog, AlertCircle } from 'lucide-react';
import apiClient from '../lib/axios';
import GpSelector from './GpSelector';
import type { Patient, PatientRequest } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  patient?: Patient;
  onSuccess: () => void;
}

export default function PatientFormModal({ isOpen, onClose, patient, onSuccess }: Props) {
  const isEdit = !!patient;

  const [form, setForm] = useState<PatientRequest>({
    patientId: patient?.patientId ?? '',
    firstName: patient?.firstName ?? '',
    lastName: patient?.lastName ?? '',
    dateOfBirth: patient?.dateOfBirth ?? '',
    gender: patient?.gender ?? '',
    email: patient?.email ?? '',
    phoneNumber: patient?.phoneNumber ?? '',
    address: patient?.address ?? '',
    nhsNumber: patient?.nhsNumber ?? '',
    gpId: patient?.gp?.id ?? undefined,
  });

  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: (data: PatientRequest) => {
      if (isEdit) {
        return apiClient.put(`/patients/${patient!.patientId}`, data);
      }
      return apiClient.post('/patients', data);
    },
    onSuccess: () => {
      setError('');
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        `Failed to ${isEdit ? 'update' : 'create'} patient. Please try again.`;
      setError(msg);
    },
  });

  const handleChange = (field: keyof PatientRequest, value: string | undefined) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate(form);
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-black/30 z-50 flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200">
          <div className="flex items-center gap-3">
            {isEdit ? (
              <UserCog className="h-5 w-5 text-blue-600" />
            ) : (
              <UserPlus className="h-5 w-5 text-blue-600" />
            )}
            <h2 className="text-lg font-semibold text-slate-900">
              {isEdit ? 'Edit Patient' : 'Register New Patient'}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="px-6 py-6 space-y-5">
            {error && (
              <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* Patient ID */}
            <div>
              <div className="flex items-center justify-between mb-1.5">
                <label htmlFor="patient-id" className="block text-sm font-medium text-slate-700">
                  Patient Clinic ID
                </label>
                <span className="text-xs text-slate-400">
                  {isEdit ? 'Immutable ID' : 'Optional (auto-generated if left blank)'}
                </span>
              </div>
              <input
                id="patient-id"
                type="text"
                disabled={isEdit}
                value={form.patientId}
                onChange={(e) => handleChange('patientId', e.target.value)}
                placeholder="e.g. PAT-49201 (Leave blank to auto-generate)"
                className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-slate-50 disabled:text-slate-500 transition-all font-mono"
              />
            </div>

            {/* Name row */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="first-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                  First Name <span className="text-red-500">*</span>
                </label>
                <input
                  id="first-name"
                  type="text"
                  required
                  value={form.firstName}
                  onChange={(e) => handleChange('firstName', e.target.value)}
                  placeholder="First name"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label htmlFor="last-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Last Name <span className="text-red-500">*</span>
                </label>
                <input
                  id="last-name"
                  type="text"
                  required
                  value={form.lastName}
                  onChange={(e) => handleChange('lastName', e.target.value)}
                  placeholder="Last name"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* DOB & Gender row */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="dob" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Date of Birth
                </label>
                <input
                  id="dob"
                  type="date"
                  value={form.dateOfBirth}
                  onChange={(e) => handleChange('dateOfBirth', e.target.value)}
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label htmlFor="gender" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Gender
                </label>
                <select
                  id="gender"
                  value={form.gender}
                  onChange={(e) => handleChange('gender', e.target.value)}
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent appearance-none transition-all"
                >
                  <option value="">Select gender</option>
                  <option value="Male">Male</option>
                  <option value="Female">Female</option>
                  <option value="Other">Other</option>
                  <option value="Prefer not to say">Prefer not to say</option>
                </select>
              </div>
            </div>

            {/* Contact row */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label htmlFor="patient-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Email
                </label>
                <input
                  id="patient-email"
                  type="email"
                  value={form.email}
                  onChange={(e) => handleChange('email', e.target.value)}
                  placeholder="patient@example.com"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label htmlFor="patient-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Phone Number
                </label>
                <input
                  id="patient-phone"
                  type="tel"
                  value={form.phoneNumber}
                  onChange={(e) => handleChange('phoneNumber', e.target.value)}
                  placeholder="+44 7700 900000"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* NHS Number */}
            <div>
              <label htmlFor="nhs-number" className="block text-sm font-medium text-slate-700 mb-1.5">
                NHS Number <span className="text-slate-400 text-xs">(optional)</span>
              </label>
              <input
                id="nhs-number"
                type="text"
                value={form.nhsNumber}
                onChange={(e) => handleChange('nhsNumber', e.target.value)}
                placeholder="e.g. 943 476 5919"
                className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>

            {/* Address */}
            <div>
              <label htmlFor="patient-address" className="block text-sm font-medium text-slate-700 mb-1.5">
                Address
              </label>
              <textarea
                id="patient-address"
                rows={2}
                value={form.address}
                onChange={(e) => handleChange('address', e.target.value)}
                placeholder="Full address"
                className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none transition-all"
              />
            </div>

            {/* Assigned GP */}
            <div>
              <label className="block text-sm font-medium text-slate-700 mb-1.5">
                Assigned GP <span className="text-slate-400 text-xs">(optional)</span>
              </label>
              <GpSelector
                value={form.gpId}
                onChange={(gpId) => handleChange('gpId', gpId)}
              />
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 rounded-xl text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {mutation.isPending ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  {isEdit ? 'Updating...' : 'Creating...'}
                </>
              ) : isEdit ? (
                'Update Patient'
              ) : (
                'Register Patient'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
