import { useState, useEffect, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation } from '@tanstack/react-query';
import {
  X,
  Stethoscope,
  Building2,
  AlertCircle,
  Check,
  Mail,
  Phone,
  Hash,
  GraduationCap,
} from 'lucide-react';
import apiClient from '../lib/axios';
import type { GP, GpRequest } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  initialData?: GP | null;
}

export default function GpFormModal({ isOpen, onClose, onSuccess, initialData }: Props) {
  const isEditing = !!initialData;

  const [form, setForm] = useState<GpRequest>({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    gmcNumber: '',
    specialisation: '',
    practiceName: '',
  });

  const [error, setError] = useState('');

  useEffect(() => {
    if (initialData) {
      setForm({
        firstName: initialData.firstName || '',
        lastName: initialData.lastName || '',
        email: initialData.email || '',
        phoneNumber: initialData.phoneNumber || '',
        gmcNumber: initialData.gmcNumber || '',
        specialisation: initialData.specialisation || '',
        practiceName: initialData.practiceName || '',
      });
    } else {
      setForm({
        firstName: '',
        lastName: '',
        email: '',
        phoneNumber: '',
        gmcNumber: '',
        specialisation: '',
        practiceName: '',
      });
    }
    setError('');
  }, [initialData, isOpen]);

  const mutation = useMutation({
    mutationFn: (data: GpRequest) => {
      if (isEditing && initialData) {
        return apiClient.put(`/gps/${initialData.id}`, data);
      }
      return apiClient.post('/gps', data);
    },
    onSuccess: () => {
      setError('');
      onSuccess();
      onClose();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        `Failed to ${isEditing ? 'update' : 'create'} GP record. Please check the fields and try again.`;
      setError(msg);
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate(form);
  };

  if (!isOpen) return null;

  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-lg w-full overflow-hidden animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200">
              <Stethoscope className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                {isEditing ? 'Edit General Practitioner' : 'Register General Practitioner'}
              </h2>
              <p className="text-xs text-slate-500">
                {isEditing
                  ? `Update NHS directory details for Dr. ${initialData?.firstName} ${initialData?.lastName}`
                  : 'Add a new GP or primary care physician to the NHS practice directory'}
              </p>
            </div>
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
          <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
            {error && (
              <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-xs text-red-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* Practitioner Name */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  First Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  placeholder="Arthur"
                  className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Last Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  placeholder="Conan"
                  className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* GMC Number & Specialisation */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  GMC Licence Number <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <Hash className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  <input
                    type="text"
                    required
                    value={form.gmcNumber}
                    onChange={(e) => setForm({ ...form, gmcNumber: e.target.value })}
                    placeholder="7481923"
                    className="w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Clinical Specialisation
                </label>
                <div className="relative">
                  <GraduationCap className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  <input
                    type="text"
                    value={form.specialisation}
                    onChange={(e) => setForm({ ...form, specialisation: e.target.value })}
                    placeholder="General Practice / Family Medicine"
                    className="w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>
            </div>

            {/* Contact Details */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Email Address
                </label>
                <div className="relative">
                  <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  <input
                    type="email"
                    value={form.email}
                    onChange={(e) => setForm({ ...form, email: e.target.value })}
                    placeholder="dr.conan@practice.nhs.uk"
                    className="w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Phone Number
                </label>
                <div className="relative">
                  <Phone className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                  <input
                    type="tel"
                    value={form.phoneNumber}
                    onChange={(e) => setForm({ ...form, phoneNumber: e.target.value })}
                    placeholder="+44 20 7946 0912"
                    className="w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>
            </div>

            {/* Medical Practice */}
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">
                NHS Medical Practice / Surgery Name
              </label>
              <div className="relative">
                <Building2 className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-slate-400" />
                <input
                  type="text"
                  value={form.practiceName}
                  onChange={(e) => setForm({ ...form, practiceName: e.target.value })}
                  placeholder="St. Jude Medical Practice & Health Centre"
                  className="w-full rounded-xl border border-slate-300 bg-white pl-9 pr-3.5 py-2 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending || !form.firstName || !form.lastName || !form.gmcNumber}
              className="inline-flex items-center gap-2 px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-xs font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed shadow-sm"
            >
              {mutation.isPending ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Saving...
                </>
              ) : (
                <>
                  <Check className="h-3.5 w-3.5" />
                  {isEditing ? 'Update GP' : 'Register GP'}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
