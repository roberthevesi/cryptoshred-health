import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Users,
  Search,
  UserPlus,
  Trash2,
  AlertCircle,
  Stethoscope,
  FileCheck2,
  ShieldAlert,
  Calendar,
  CheckCircle2,
  Pencil,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import StaffProvisionModal from './StaffProvisionModal';
import StaffEditModal from './StaffEditModal';
import type { AdminUser, Role } from '../types';

export default function StaffManagementPanel() {
  const { user: currentUser } = useAuth();
  const queryClient = useQueryClient();

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedRoleFilter, setSelectedRoleFilter] = useState<'ALL' | Role>('ALL');
  const [isProvisionModalOpen, setIsProvisionModalOpen] = useState(false);
  const [editingStaffUser, setEditingStaffUser] = useState<AdminUser | null>(null);
  const [error, setError] = useState('');

  const { data: staffList = [], isLoading } = useQuery<AdminUser[]>({
    queryKey: ['admin-users'],
    queryFn: () => apiClient.get<AdminUser[]>('/admin/users').then((r) => r.data),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/admin/users/${id}`),
    onSuccess: () => {
      setError('');
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to delete staff user account.';
      setError(msg);
    },
  });

  const handleDeleteUser = (user: AdminUser) => {
    const isSelf = user.email.toLowerCase() === currentUser?.email?.toLowerCase();
    const promptMsg = isSelf
      ? `Warning: You are attempting to delete your own administrator account (${user.email}). Are you sure?`
      : `Are you sure you want to permanently revoke and delete the staff account for ${user.email} (${user.role})?`;

    if (window.confirm(promptMsg)) {
      deleteMutation.mutate(user.id);
    }
  };

  const filteredStaff = staffList.filter((u) => {
    const matchesSearch =
      u.email.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.role.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesRole = selectedRoleFilter === 'ALL' || u.role === selectedRoleFilter;
    return matchesSearch && matchesRole;
  });

  const doctorCount = staffList.filter((u) => u.role === 'DOCTOR').length;
  const auditorCount = staffList.filter((u) => u.role === 'AUDITOR').length;
  const adminCount = staffList.filter((u) => u.role === 'ADMIN').length;

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Top Banner & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg font-bold text-slate-900">Hospital Staff &amp; Access Directory</h2>
            <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700 border border-blue-200">
              {staffList.length} Active Staff
            </span>
          </div>
          <p className="text-xs text-slate-500 mt-1">
            Provision and manage clinician (Doctor / GP) and compliance auditor access credentials.
          </p>
        </div>

        <button
          type="button"
          onClick={() => setIsProvisionModalOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-xs font-semibold shadow-sm transition"
        >
          <UserPlus className="h-4 w-4" />
          Provision Staff Account
        </button>
      </div>

      {error && (
        <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-xs text-red-700">
          <AlertCircle className="h-4 w-4 shrink-0" />
          {error}
        </div>
      )}

      {/* Role Filter Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
        <button
          type="button"
          onClick={() => setSelectedRoleFilter(selectedRoleFilter === 'DOCTOR' ? 'ALL' : 'DOCTOR')}
          className={`flex items-center justify-between p-4 rounded-xl border transition text-left ${
            selectedRoleFilter === 'DOCTOR'
              ? 'border-blue-500 bg-blue-50/60 ring-2 ring-blue-500/20'
              : 'border-slate-200 bg-white hover:bg-slate-50'
          }`}
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
              <Stethoscope className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Doctors &amp; Clinicians</p>
              <p className="text-lg font-bold text-slate-900">{doctorCount}</p>
            </div>
          </div>
          {selectedRoleFilter === 'DOCTOR' && <CheckCircle2 className="h-4 w-4 text-blue-600" />}
        </button>

        <button
          type="button"
          onClick={() => setSelectedRoleFilter(selectedRoleFilter === 'AUDITOR' ? 'ALL' : 'AUDITOR')}
          className={`flex items-center justify-between p-4 rounded-xl border transition text-left ${
            selectedRoleFilter === 'AUDITOR'
              ? 'border-purple-500 bg-purple-50/60 ring-2 ring-purple-500/20'
              : 'border-slate-200 bg-white hover:bg-slate-50'
          }`}
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-purple-100 text-purple-700">
              <FileCheck2 className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">Compliance Auditors</p>
              <p className="text-lg font-bold text-slate-900">{auditorCount}</p>
            </div>
          </div>
          {selectedRoleFilter === 'AUDITOR' && <CheckCircle2 className="h-4 w-4 text-purple-600" />}
        </button>

        <button
          type="button"
          onClick={() => setSelectedRoleFilter(selectedRoleFilter === 'ADMIN' ? 'ALL' : 'ADMIN')}
          className={`flex items-center justify-between p-4 rounded-xl border transition text-left ${
            selectedRoleFilter === 'ADMIN'
              ? 'border-amber-500 bg-amber-50/60 ring-2 ring-amber-500/20'
              : 'border-slate-200 bg-white hover:bg-slate-50'
          }`}
        >
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-amber-100 text-amber-700">
              <ShieldAlert className="h-4 w-4" />
            </div>
            <div>
              <p className="text-xs text-slate-500 font-medium">System Administrators</p>
              <p className="text-lg font-bold text-slate-900">{adminCount}</p>
            </div>
          </div>
          {selectedRoleFilter === 'ADMIN' && <CheckCircle2 className="h-4 w-4 text-amber-600" />}
        </button>
      </div>

      {/* Search Bar */}
      <div className="flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search staff members by email or role..."
            className="w-full rounded-xl border border-slate-300 bg-white pl-10 pr-4 py-2.5 text-xs text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
          />
        </div>
      </div>

      {/* Staff Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-card">
        {isLoading ? (
          <div className="flex min-h-[250px] flex-col items-center justify-center space-y-3">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
            <p className="text-xs text-slate-500">Loading staff directory...</p>
          </div>
        ) : filteredStaff.length === 0 ? (
          <div className="p-12 text-center text-slate-400 space-y-2">
            <Users className="h-10 w-10 mx-auto opacity-40 text-slate-400" />
            <p className="text-sm font-medium text-slate-600">No staff members found</p>
            <p className="text-xs text-slate-400">
              {searchTerm || selectedRoleFilter !== 'ALL'
                ? 'Try adjusting your search criteria or role filters.'
                : 'Click "Provision Staff Account" to register your first clinician.'}
            </p>
          </div>
        ) : (
          <table className="w-full text-left text-xs text-slate-600">
            <thead className="bg-slate-50/80 border-b border-slate-200 text-[11px] uppercase tracking-wider font-semibold text-slate-500">
              <tr>
                <th className="px-6 py-3.5">Staff Email &amp; Identity</th>
                <th className="px-6 py-3.5">Assigned Role</th>
                <th className="px-6 py-3.5">Account ID</th>
                <th className="px-6 py-3.5">Provisioned Date</th>
                <th className="px-6 py-3.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {filteredStaff.map((staff) => {
                const isSelf = staff.email.toLowerCase() === currentUser?.email?.toLowerCase();
                return (
                  <tr key={staff.id} className="hover:bg-slate-50/70 transition-colors">
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div
                          className={`flex h-9 w-9 items-center justify-center rounded-xl font-bold text-xs ${
                            staff.role === 'DOCTOR'
                              ? 'bg-blue-50 text-blue-700 border border-blue-200'
                              : staff.role === 'AUDITOR'
                              ? 'bg-purple-50 text-purple-700 border border-purple-200'
                              : 'bg-amber-50 text-amber-700 border border-amber-200'
                          }`}
                        >
                          {staff.role === 'DOCTOR' ? (
                            <Stethoscope className="h-4 w-4" />
                          ) : staff.role === 'AUDITOR' ? (
                            <FileCheck2 className="h-4 w-4" />
                          ) : (
                            <ShieldAlert className="h-4 w-4" />
                          )}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-slate-900">{staff.email}</span>
                            {isSelf && (
                              <span className="text-[10px] bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded-md font-medium border border-slate-200">
                                You
                              </span>
                            )}
                          </div>
                          <span className="text-[11px] text-slate-400">Zero-Knowledge Auth</span>
                        </div>
                      </div>
                    </td>

                    <td className="px-6 py-4">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${
                          staff.role === 'DOCTOR'
                            ? 'bg-blue-50 text-blue-700 border border-blue-200'
                            : staff.role === 'AUDITOR'
                            ? 'bg-purple-50 text-purple-700 border border-purple-200'
                            : 'bg-amber-50 text-amber-700 border border-amber-200'
                        }`}
                      >
                        {staff.role}
                      </span>
                    </td>

                    <td className="px-6 py-4 font-mono text-slate-500 text-[11px]">
                      {staff.id.substring(0, 13)}...
                    </td>

                    <td className="px-6 py-4 text-slate-500">
                      <span className="flex items-center gap-1.5">
                        <Calendar className="h-3.5 w-3.5 text-slate-400" />
                        {staff.createdAt
                          ? new Date(staff.createdAt).toLocaleDateString('en-GB', {
                              day: 'numeric',
                              month: 'short',
                              year: 'numeric',
                            })
                          : 'Pre-seeded'}
                      </span>
                    </td>

                    <td className="px-6 py-4 text-right">
                      <div className="flex items-center justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => setEditingStaffUser(staff)}
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-slate-600 hover:bg-slate-100 hover:text-slate-900 border border-transparent hover:border-slate-200 transition text-xs font-medium"
                          title="Edit Staff Account (Opens Popup Modal)"
                        >
                          <Pencil className="h-3.5 w-3.5" />
                          <span>Edit</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => handleDeleteUser(staff)}
                          disabled={deleteMutation.isPending}
                          title="Revoke & Delete Staff Account"
                          className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-rose-600 hover:bg-rose-50 hover:text-rose-700 border border-transparent hover:border-rose-200 transition text-xs font-medium"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                          <span>Revoke</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      <StaffProvisionModal
        isOpen={isProvisionModalOpen}
        onClose={() => setIsProvisionModalOpen(false)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['admin-users'] });
        }}
      />

      <StaffEditModal
        isOpen={!!editingStaffUser}
        staffUser={editingStaffUser}
        onClose={() => setEditingStaffUser(null)}
        onSuccess={() => {
          queryClient.invalidateQueries({ queryKey: ['admin-users'] });
        }}
      />
    </div>
  );
}
