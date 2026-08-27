import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Search,
  Plus,
  Pencil,
  XCircle,
  Stethoscope,
  Building2,
  AlertCircle,
  Hash,
  Mail,
} from 'lucide-react';
import apiClient from '../lib/axios';
import GpFormModal from './GpFormModal';
import ConfirmationModal from './ConfirmationModal';
import type { GP } from '../types';

export default function GpManagementPanel() {
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = useState('');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGp, setEditingGp] = useState<GP | null>(null);
  const [deactivatingGp, setDeactivatingGp] = useState<GP | null>(null);
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

  const handleOpenAdd = () => {
    setEditingGp(null);
    setIsModalOpen(true);
    setError('');
  };

  const handleOpenEdit = (gp: GP) => {
    setEditingGp(gp);
    setIsModalOpen(true);
    setError('');
  };

  const deactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/gps/${id}`),
    onSuccess: () => {
      setError('');
      setDeactivatingGp(null);
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to deactivate GP.';
      setError(msg);
    },
  });

  return (
    <div className="space-y-5 animate-fade-in">
      {/* Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-slate-900">NHS General Practitioner Directory</h2>
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              {gps.length} Registered GPs
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Registered NHS primary care General Practitioners, practices, and GMC licence numbers.
          </p>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleOpenAdd}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-xs font-semibold shadow-sm transition"
          >
            <Plus className="h-4 w-4" />
            Add GP
          </button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-xs text-red-700">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* Search Bar */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search GPs by name, GMC number, or medical practice..."
            className="w-full rounded-xl border border-slate-300 bg-white pl-10 pr-4 py-2.5 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
          />
        </div>
      </div>

      {/* Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
        {isLoading ? (
          <div className="flex min-h-[250px] flex-col items-center justify-center space-y-3">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
            <p className="text-xs text-slate-500">Loading GP directory...</p>
          </div>
        ) : filteredGps.length === 0 ? (
          <div className="p-12 text-center text-slate-400 space-y-2">
            <Stethoscope className="h-10 w-10 mx-auto opacity-40 text-slate-400" />
            <p className="text-sm font-medium text-slate-600">No General Practitioners found</p>
            <p className="text-xs text-slate-400">
              {searchTerm
                ? 'Try adjusting your search criteria.'
                : 'Click "Add GP" to register your first General Practitioner.'}
            </p>
          </div>
        ) : (
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50/80 border-b border-slate-200 text-[11px] uppercase tracking-wider font-semibold text-slate-500">
              <tr>
                <th className="px-6 py-3.5">Practitioner Name</th>
                <th className="px-6 py-3.5">GMC Number</th>
                <th className="px-6 py-3.5">Clinical Specialisation</th>
                <th className="px-6 py-3.5">Medical Practice</th>
                <th className="px-6 py-3.5">Status</th>
                <th className="px-6 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredGps.map((gp) => (
                <tr key={gp.id} className="hover:bg-slate-50/70 transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700 border border-emerald-200 font-bold text-xs">
                        {gp.firstName.charAt(0)}
                        {gp.lastName.charAt(0)}
                      </div>
                      <div>
                        <p className="font-semibold text-slate-900">
                          Dr. {gp.firstName} {gp.lastName}
                        </p>
                        {gp.email && (
                          <p className="text-[11px] text-slate-400 flex items-center gap-1 mt-0.5">
                            <Mail className="h-3 w-3 text-slate-400" />
                            {gp.email}
                          </p>
                        )}
                      </div>
                    </div>
                  </td>

                  <td className="px-6 py-4 font-mono text-slate-700 text-[11px]">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-slate-100 border border-slate-200">
                      <Hash className="h-3 w-3 text-slate-400" />
                      {gp.gmcNumber}
                    </span>
                  </td>

                  <td className="px-6 py-4 text-slate-700 font-medium">
                    {gp.specialisation || 'General Practice'}
                  </td>

                  <td className="px-6 py-4 text-slate-600">
                    <span className="flex items-center gap-1.5">
                      <Building2 className="h-3.5 w-3.5 text-slate-400 shrink-0" />
                      <span>{gp.practiceName || '—'}</span>
                    </span>
                  </td>

                  <td className="px-6 py-4">
                    {gp.isActive ? (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                        Active
                      </span>
                    ) : (
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-slate-100 text-slate-500 border border-slate-200">
                        Inactive
                      </span>
                    )}
                  </td>

                  <td className="px-6 py-4 text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      <button
                        type="button"
                        onClick={() => handleOpenEdit(gp)}
                        className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 hover:text-slate-900 border border-transparent hover:border-slate-200 transition text-xs font-medium"
                        title="Edit GP Details (Opens Popup Modal)"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                        <span>Edit</span>
                      </button>

                      {gp.isActive && (
                        <button
                          type="button"
                          onClick={() => setDeactivatingGp(gp)}
                          disabled={deactivateMutation.isPending}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-rose-600 hover:bg-rose-50 hover:text-rose-700 border border-transparent hover:border-rose-200 transition text-xs font-medium"
                          title="Deactivate GP"
                        >
                          <XCircle className="h-3.5 w-3.5" />
                          <span>Deactivate</span>
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* GP Create / Edit Modal */}
      <GpFormModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        initialData={editingGp}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['gps-management'] });
          queryClient.invalidateQueries({ queryKey: ['gps'] });
        }}
      />

      {/* GP Deactivation Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deactivatingGp}
        onClose={() => setDeactivatingGp(null)}
        onConfirm={() => {
          if (deactivatingGp) {
            deactivateMutation.mutate(deactivatingGp.id);
          }
        }}
        title="Deactivate General Practitioner"
        message={
          <>
            Are you sure you want to deactivate{' '}
            <strong className="text-slate-900">
              Dr. {deactivatingGp?.firstName} {deactivatingGp?.lastName}
            </strong>
            ?
          </>
        }
        detail={
          deactivatingGp && (
            <span>
              GMC Licence: {deactivatingGp.gmcNumber} | Practice: {deactivatingGp.practiceName || 'N/A'}
            </span>
          )
        }
        confirmLabel="Deactivate Practitioner"
        cancelLabel="Cancel"
        variant="warning"
        isLoading={deactivateMutation.isPending}
      />
    </div>
  );
}
