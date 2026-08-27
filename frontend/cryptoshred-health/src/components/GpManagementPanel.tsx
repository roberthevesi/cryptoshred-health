import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Search,
  Plus,
  Pencil,
  XCircle,
  CheckCircle2,
  Trash2,
  Stethoscope,
  Building2,
  AlertCircle,
  Hash,
  Mail,
  UserCheck,
  UserX,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import GpFormModal from './GpFormModal';
import ConfirmationModal from './ConfirmationModal';
import type { GP } from '../types';

export default function GpManagementPanel() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const queryClient = useQueryClient();

  const [searchTerm, setSearchTerm] = useState('');
  const [statusTab, setStatusTab] = useState<'active' | 'inactive'>('active');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGp, setEditingGp] = useState<GP | null>(null);
  const [deactivatingGp, setDeactivatingGp] = useState<GP | null>(null);
  const [reactivatingGp, setReactivatingGp] = useState<GP | null>(null);
  const [deletingGp, setDeletingGp] = useState<GP | null>(null);
  const [error, setError] = useState('');

  const { data: gps = [], isLoading } = useQuery<GP[]>({
    queryKey: ['gps-management'],
    queryFn: () => apiClient.get<GP[]>('/gps?includeInactive=true').then((r) => r.data),
  });

  const activeGps = gps.filter((gp) => gp.isActive);
  const inactiveGps = gps.filter((gp) => !gp.isActive);

  const currentList = statusTab === 'active' ? activeGps : inactiveGps;

  const filteredGps = searchTerm
    ? currentList.filter(
        (gp) =>
          `${gp.firstName} ${gp.lastName}`.toLowerCase().includes(searchTerm.toLowerCase()) ||
          gp.gmcNumber.toLowerCase().includes(searchTerm.toLowerCase()) ||
          gp.practiceName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
          gp.specialisation?.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : currentList;

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
    mutationFn: (id: string) => apiClient.patch(`/gps/${id}/deactivate`),
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

  const reactivateMutation = useMutation({
    mutationFn: (id: string) => apiClient.patch(`/gps/${id}/activate`),
    onSuccess: () => {
      setError('');
      setReactivatingGp(null);
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to reactivate GP.';
      setError(msg);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/gps/${id}`),
    onSuccess: () => {
      setError('');
      setDeletingGp(null);
      queryClient.invalidateQueries({ queryKey: ['gps-management'] });
      queryClient.invalidateQueries({ queryKey: ['gps'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to permanently delete GP.';
      setError(msg);
    },
  });

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <h2 className="text-lg font-bold text-slate-900">NHS General Practitioner Directory</h2>
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
              {activeGps.length} Active Practices
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            {isAdmin
              ? 'Institutional directory of registered NHS primary care GPs, GMC licences, and medical surgeries.'
              : 'Official NHS primary care provider index for clinical referrals and patient assignment.'}
          </p>
        </div>

        {isAdmin && (
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
        )}
      </div>

      {error && (
        <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-xs text-red-700">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* Tabs for Active vs Inactive GPs */}
      <div className="flex items-center justify-between border-b border-slate-200 gap-4">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setStatusTab('active')}
            className={`flex items-center gap-2 px-4 py-3 text-xs font-semibold border-b-2 transition-all ${
              statusTab === 'active'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
            }`}
          >
            <UserCheck className="h-4 w-4" />
            <span>Active Practitioners</span>
            <span
              className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                statusTab === 'active'
                  ? 'bg-emerald-100 text-emerald-800'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {activeGps.length}
            </span>
          </button>

          <button
            type="button"
            onClick={() => setStatusTab('inactive')}
            className={`flex items-center gap-2 px-4 py-3 text-xs font-semibold border-b-2 transition-all ${
              statusTab === 'inactive'
                ? 'border-amber-600 text-amber-700'
                : 'border-transparent text-slate-500 hover:text-slate-900 hover:border-slate-300'
            }`}
          >
            <UserX className="h-4 w-4" />
            <span>Inactive / Retired GPs</span>
            <span
              className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                statusTab === 'inactive'
                  ? 'bg-amber-100 text-amber-800'
                  : 'bg-slate-100 text-slate-600'
              }`}
            >
              {inactiveGps.length}
            </span>
          </button>
        </div>
      </div>

      {/* Search Bar */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder={`Search ${statusTab === 'active' ? 'active' : 'inactive'} GPs by name, GMC number, or surgery...`}
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
            <p className="text-sm font-medium text-slate-600">
              No {statusTab === 'active' ? 'active' : 'inactive'} General Practitioners found
            </p>
            <p className="text-xs text-slate-400">
              {searchTerm
                ? 'Try adjusting your search criteria.'
                : statusTab === 'active'
                ? isAdmin
                  ? 'Click "Add GP" to register your first General Practitioner.'
                  : 'No active GPs registered.'
                : 'No inactive or retired General Practitioners in the registry.'}
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
                {isAdmin && <th className="px-6 py-3.5 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredGps.map((gp) => (
                <tr key={gp.id} className="hover:bg-slate-50/70 transition-colors">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div
                        className={`flex h-9 w-9 items-center justify-center rounded-xl font-bold text-xs ${
                          gp.isActive
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : 'bg-slate-100 text-slate-500 border border-slate-200'
                        }`}
                      >
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
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                        Inactive / Retired
                      </span>
                    )}
                  </td>

                  {isAdmin && (
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

                        {gp.isActive ? (
                          <button
                            type="button"
                            onClick={() => setDeactivatingGp(gp)}
                            disabled={deactivateMutation.isPending}
                            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-amber-600 hover:bg-amber-50 hover:text-amber-700 border border-transparent hover:border-amber-200 transition text-xs font-medium"
                            title="Deactivate GP"
                          >
                            <XCircle className="h-3.5 w-3.5" />
                            <span>Deactivate</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setReactivatingGp(gp)}
                            disabled={reactivateMutation.isPending}
                            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-emerald-600 hover:bg-emerald-50 hover:text-emerald-700 border border-transparent hover:border-emerald-200 transition text-xs font-medium"
                            title="Reactivate GP"
                          >
                            <CheckCircle2 className="h-3.5 w-3.5" />
                            <span>Reactivate</span>
                          </button>
                        )}

                        <button
                          type="button"
                          onClick={() => setDeletingGp(gp)}
                          disabled={deleteMutation.isPending}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-rose-600 hover:bg-rose-50 hover:text-rose-700 border border-transparent hover:border-rose-200 transition text-xs font-medium"
                          title="Permanently Delete GP"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          <span>Delete</span>
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* GP Create / Edit Modal (Admin Only) */}
      {isAdmin && (
        <GpFormModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          initialData={editingGp}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['gps-management'] });
            queryClient.invalidateQueries({ queryKey: ['gps'] });
          }}
        />
      )}

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
            ? They will be moved to the Inactive tab and hidden from new patient intake assignments.
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

      {/* GP Reactivation Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!reactivatingGp}
        onClose={() => setReactivatingGp(null)}
        onConfirm={() => {
          if (reactivatingGp) {
            reactivateMutation.mutate(reactivatingGp.id);
          }
        }}
        title="Reactivate General Practitioner"
        message={
          <>
            Are you sure you want to reactivate{' '}
            <strong className="text-slate-900">
              Dr. {reactivatingGp?.firstName} {reactivatingGp?.lastName}
            </strong>
            ? They will return to Active status and be available for clinical assignments.
          </>
        }
        detail={
          reactivatingGp && (
            <span>
              GMC Licence: {reactivatingGp.gmcNumber} | Practice: {reactivatingGp.practiceName || 'N/A'}
            </span>
          )
        }
        confirmLabel="Reactivate Practitioner"
        cancelLabel="Cancel"
        variant="info"
        isLoading={reactivateMutation.isPending}
      />

      {/* GP Permanent Deletion Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!deletingGp}
        onClose={() => setDeletingGp(null)}
        onConfirm={() => {
          if (deletingGp) {
            deleteMutation.mutate(deletingGp.id);
          }
        }}
        title="Permanently Delete GP Record"
        message={
          <>
            Are you certain you want to permanently remove{' '}
            <strong className="text-slate-900">
              Dr. {deletingGp?.firstName} {deletingGp?.lastName}
            </strong>{' '}
            from the institutional directory?
          </>
        }
        detail={
          deletingGp && (
            <span>
              GMC Licence: {deletingGp.gmcNumber} | Practice: {deletingGp.practiceName || 'N/A'}
            </span>
          )
        }
        confirmLabel="Delete GP Record"
        cancelLabel="Cancel"
        variant="danger"
        isLoading={deleteMutation.isPending}
      />
    </div>
  );
}
