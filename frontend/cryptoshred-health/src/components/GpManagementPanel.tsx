import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Search,
  Plus,
  Pencil,
  XCircle,
  Stethoscope,
  Building2,
  AlertCircle,
  X,
  Check,
} from 'lucide-react';
import apiClient from '../lib/axios';
import type { GP, GpRequest } from '../types';

export default function GpManagementPanel() {
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = useState('');
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingGp, setEditingGp] = useState<GP | null>(null);
  const [error, setError] = useState('');

  const { data: gps = [], isLoading } = useQuery<GP[]>({
    queryKey: ['gps-management'],
    queryFn: () => apiClient.get<GP[]>('/gps').then((r) => r.data),
  });

  const filteredGps = searchTerm
    ? gps.filter(
        (gp) =>
          `${gp.firstName} ${gp.lastName}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
          gp.gmcNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
          gp.practiceName?.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : gps;

  // Form state
  const [form, setForm] = useState<GpRequest>({
    firstName: '',
    lastName: '',
    email: '',
    phoneNumber: '',
    gmcNumber: '',
    specialisation: '',
    practiceName: '',
  });

  const resetForm = () => {
    setForm({
      firstName: '',
      lastName: '',
      email: '',
      phoneNumber: '',
      gmcNumber: '',
      specialisation: '',
      practiceName: '',
    });
    setEditingGp(null);
    setIsFormOpen(false);
    setError('');
  };

  const openEdit = (gp: GP) => {
    setForm({
      firstName: gp.firstName,
      lastName: gp.lastName,
      email: gp.email,
      phoneNumber: gp.phoneNumber,
      gmcNumber: gp.gmcNumber,
      specialisation: gp.specialisation,
      practiceName: gp.practiceName,
    });
    setEditingGp(gp);
    setIsFormOpen(true);
    setError('');
  };

  const createMutation = useMutation({
    mutationFn: (data: GpRequest) => apiClient.post('/gps', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
      resetForm();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to create GP.';
      setError(msg);
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: string; data: GpRequest }) =>
      apiClient.put(`/gps/${id}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
      resetForm();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to update GP.';
      setError(msg);
    },
  });

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/gps/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
    },
  });

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (editingGp) {
      updateMutation.mutate({ id: editingGp.id, data: form });
    } else {
      createMutation.mutate(form);
    }
  };

  const isPending = createMutation.isPending || updateMutation.isPending;

  return (
    <div className="space-y-5">
      {/* Header & Search */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Stethoscope className="h-5 w-5 text-blue-600" />
          <h2 className="text-lg font-semibold text-slate-900">GP Directory</h2>
          <span className="text-xs font-medium text-slate-500 bg-slate-100 px-2 py-0.5 rounded-full">
            {gps.length}
          </span>
        </div>

        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search GPs..."
              className="rounded-xl border border-slate-300 bg-white pl-10 pr-4 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent w-56 transition-all"
            />
          </div>
          <button
            type="button"
            onClick={() => {
              resetForm();
              setIsFormOpen(true);
            }}
            className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors"
          >
            <Plus className="h-4 w-4" />
            Add GP
          </button>
        </div>
      </div>

      {/* Inline Form */}
      {isFormOpen && (
        <div className="bg-slate-50 border border-slate-200 rounded-xl p-5 animate-fade-in">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-slate-900">
              {editingGp ? 'Edit GP Details' : 'Add New GP'}
            </h3>
            <button
              type="button"
              onClick={resetForm}
              className="p-1 rounded-lg hover:bg-slate-200 text-slate-400 hover:text-slate-600 transition-colors"
            >
              <X className="h-4 w-4" />
            </button>
          </div>

          {error && (
            <div className="flex items-center gap-2 mb-4 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">
                  First Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.firstName}
                  onChange={(e) => setForm({ ...form, firstName: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">
                  Last Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.lastName}
                  onChange={(e) => setForm({ ...form, lastName: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">
                  GMC Number <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={form.gmcNumber}
                  onChange={(e) => setForm({ ...form, gmcNumber: e.target.value })}
                  placeholder="e.g. 7654321"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Email</label>
                <input
                  type="email"
                  value={form.email}
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Specialisation</label>
                <input
                  type="text"
                  value={form.specialisation}
                  onChange={(e) => setForm({ ...form, specialisation: e.target.value })}
                  placeholder="e.g. General Practice"
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-600 mb-1">Practice Name</label>
                <input
                  type="text"
                  value={form.practiceName}
                  onChange={(e) => setForm({ ...form, practiceName: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={resetForm}
                className="px-4 py-2 rounded-lg text-sm text-slate-600 hover:bg-slate-200 transition-colors"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isPending}
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors disabled:opacity-50"
              >
                {isPending ? (
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <Check className="h-4 w-4" />
                )}
                {editingGp ? 'Update' : 'Add GP'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Table */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12 text-sm text-slate-500">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-blue-600 border-t-transparent mr-2" />
          Loading GPs...
        </div>
      ) : filteredGps.length === 0 ? (
        <div className="text-center py-12 text-sm text-slate-500">
          {searchTerm ? 'No GPs match your search.' : 'No GPs registered yet.'}
        </div>
      ) : (
        <div className="border border-slate-200 rounded-xl overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-slate-50 border-b border-slate-200">
                <th className="text-left px-4 py-3 font-semibold text-slate-600">Name</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-600 hidden sm:table-cell">GMC Number</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-600 hidden md:table-cell">Specialisation</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-600 hidden lg:table-cell">Practice</th>
                <th className="text-left px-4 py-3 font-semibold text-slate-600">Status</th>
                <th className="text-right px-4 py-3 font-semibold text-slate-600">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredGps.map((gp) => (
                <tr
                  key={gp.id}
                  className="border-b border-slate-100 last:border-b-0 hover:bg-slate-50 transition-colors"
                >
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-2.5">
                      <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700 text-xs font-bold">
                        {gp.firstName.charAt(0)}
                        {gp.lastName.charAt(0)}
                      </div>
                      <div>
                        <p className="font-medium text-slate-900">
                          Dr. {gp.firstName} {gp.lastName}
                        </p>
                        {gp.email && (
                          <p className="text-xs text-slate-500">{gp.email}</p>
                        )}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 font-mono text-slate-700 hidden sm:table-cell">
                    {gp.gmcNumber}
                  </td>
                  <td className="px-4 py-3 text-slate-600 hidden md:table-cell">
                    {gp.specialisation || '—'}
                  </td>
                  <td className="px-4 py-3 hidden lg:table-cell">
                    <span className="flex items-center gap-1 text-slate-600">
                      <Building2 className="h-3.5 w-3.5 text-slate-400" />
                      {gp.practiceName || '—'}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {gp.isActive ? (
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                        Active
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-500 border border-slate-200">
                        Inactive
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button
                        type="button"
                        onClick={() => openEdit(gp)}
                        className="p-1.5 rounded-lg hover:bg-slate-100 text-slate-400 hover:text-blue-600 transition-colors"
                        title="Edit GP"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                      {gp.isActive && (
                        <button
                          type="button"
                          onClick={() => {
                            if (confirm(`Deactivate Dr. ${gp.firstName} ${gp.lastName}?`)) {
                              deactivateMutation.mutate(gp.id);
                            }
                          }}
                          className="p-1.5 rounded-lg hover:bg-red-50 text-slate-400 hover:text-red-600 transition-colors"
                          title="Deactivate GP"
                        >
                          <XCircle className="h-4 w-4" />
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
  );
}
