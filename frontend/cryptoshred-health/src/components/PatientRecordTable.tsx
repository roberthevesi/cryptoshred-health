import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Plus,
  Pencil,
  Trash2,
  ShieldOff,
  RefreshCw,
  Eye,
  Search,
  ShieldCheck,
  AlertTriangle,
  Building,
  UserPlus,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import CreateRecordModal from './CreateRecordModal';
import ViewRecordModal from './ViewRecordModal';
import PatientFormModal from './PatientFormModal';
import type { PatientRecord } from '../types';

type FilterTab = 'all' | 'active' | 'shredded' | 'vitals_warning';

export default function PatientRecordTable() {
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const [showModal, setShowModal] = useState(false);
  const [showPatientModal, setShowPatientModal] = useState(false);
  const [editRecord, setEditRecord] = useState<PatientRecord | null>(null);
  const [viewRecordId, setViewRecordId] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState('');
  const [filterTab, setFilterTab] = useState<FilterTab>('all');
  const [selectedDepartment, setSelectedDepartment] = useState('ALL');

  const { data: records = [], isLoading, isError, refetch } = useQuery<PatientRecord[]>({
    queryKey: ['records'],
    queryFn: () => apiClient.get<PatientRecord[]>('/records').then((r) => r.data),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/records/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['records'] }),
  });

  const handleEdit = (record: PatientRecord) => {
    setEditRecord(record);
    setShowModal(true);
  };

  const handleDelete = (id: string) => {
    if (confirm('Permanently delete this clinical record from database?')) {
      deleteMutation.mutate(id);
    }
  };

  const isDoctor = user?.role === 'DOCTOR';

  // Extract unique departments for filter dropdown
  const departments = Array.from(
    new Set(records.map((r) => r.department).filter(Boolean))
  ) as string[];

  // Filtered records logic
  const filteredRecords = records.filter((record) => {
    // 1. Search Query Match
    const q = searchQuery.toLowerCase();
    const matchesSearch =
      !searchQuery ||
      record.patientName.toLowerCase().includes(q) ||
      (record.mrn && record.mrn.toLowerCase().includes(q)) ||
      (record.diagnosis && record.diagnosis.toLowerCase().includes(q)) ||
      (record.attendingDoctor && record.attendingDoctor.toLowerCase().includes(q));

    if (!matchesSearch) return false;

    // 2. Department Filter
    if (selectedDepartment !== 'ALL' && record.department !== selectedDepartment) {
      return false;
    }

    // 3. Tab Filter
    if (filterTab === 'active') return !record.shredded;
    if (filterTab === 'shredded') return record.shredded;
    if (filterTab === 'vitals_warning') {
      if (record.shredded) return false;
      const bpWarning = record.bloodPressure && (
        record.bloodPressure.startsWith('14') ||
        record.bloodPressure.startsWith('15') ||
        record.bloodPressure.startsWith('16')
      );
      const hrWarning = record.heartRate && (record.heartRate > 100 || record.heartRate < 60);
      return bpWarning || hrWarning;
    }

    return true;
  });

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-slate-500 text-sm font-mono">Loading EHR records &amp; Vault KMS status...</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3 text-center">
        <ShieldOff className="h-10 w-10 text-red-500" />
        <p className="text-slate-700 font-medium">Failed to load health records</p>
        <button onClick={() => refetch()} className="btn-ghost">
          <RefreshCw className="h-4 w-4" /> Retry
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="space-y-4">
        {/* Table Top Controls & Search Bar */}
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div>
              <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                Hospital Clinical Census
                <span className="text-xs font-mono font-normal bg-blue-50 text-blue-700 border border-blue-200 px-2 py-0.5 rounded-full">
                  AES-256-GCM
                </span>
              </h2>
              <p className="text-xs text-slate-500">
                {filteredRecords.length} of {records.length} patient record(s) visible
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {/* Search Input */}
            <div className="relative min-w-[240px]">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search patient, MRN, doctor..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 rounded-xl bg-white border border-slate-300 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>

            {/* Department Filter */}
            {departments.length > 0 && (
              <select
                value={selectedDepartment}
                onChange={(e) => setSelectedDepartment(e.target.value)}
                className="px-3 py-1.5 rounded-xl bg-white border border-slate-300 text-xs text-slate-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
              >
                <option value="ALL">All Departments</option>
                {departments.map((dept) => (
                  <option key={dept} value={dept}>
                    {dept}
                  </option>
                ))}
              </select>
            )}

            {/* Patient Intake & Registration (Doctors Only) */}
            {isDoctor && (
              <div className="flex items-center gap-2">
                <button
                  id="register-patient-btn"
                  onClick={() => setShowPatientModal(true)}
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold border border-slate-200 transition"
                  title="Register new patient profile with assigned GP"
                >
                  <UserPlus className="h-3.5 w-3.5 text-blue-600" /> Register Patient (GP)
                </button>
                <button
                  id="new-record-btn"
                  onClick={() => {
                    setEditRecord(null);
                    setShowModal(true);
                  }}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition"
                >
                  <Plus className="h-4 w-4" /> New Patient Intake
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Filter Pills */}
        <div className="flex flex-wrap gap-2 text-xs font-medium">
          <button
            onClick={() => setFilterTab('all')}
            className={`px-3 py-1.5 rounded-lg transition ${
              filterTab === 'all'
                ? 'bg-white text-slate-900 font-semibold shadow-sm border border-slate-200'
                : 'bg-slate-100 text-slate-600 hover:text-slate-900 border border-slate-200'
            }`}
          >
            All Charts ({records.length})
          </button>
          <button
            onClick={() => setFilterTab('active')}
            className={`px-3 py-1.5 rounded-lg transition ${
              filterTab === 'active'
                ? 'bg-emerald-50 border border-emerald-200 text-emerald-700 font-semibold'
                : 'bg-slate-100 text-slate-600 hover:text-slate-900 border border-slate-200'
            }`}
          >
            Active ({records.filter((r) => !r.shredded).length})
          </button>
          <button
            onClick={() => setFilterTab('vitals_warning')}
            className={`px-3 py-1.5 rounded-lg transition ${
              filterTab === 'vitals_warning'
                ? 'bg-amber-50 border border-amber-200 text-amber-700 font-semibold'
                : 'bg-slate-100 text-slate-600 hover:text-slate-900 border border-slate-200'
            }`}
          >
            Telemetry Alerts
          </button>
          <button
            onClick={() => setFilterTab('shredded')}
            className={`px-3 py-1.5 rounded-lg transition ${
              filterTab === 'shredded'
                ? 'bg-red-50 border border-red-200 text-red-700 font-semibold'
                : 'bg-slate-100 text-slate-600 hover:text-slate-900 border border-slate-200'
            }`}
          >
            Crypto-Shredded ({records.filter((r) => r.shredded).length})
          </button>
        </div>

        {/* Hospital Census Table */}
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
                <tr>
                  <th className="py-3.5 pl-4 pr-2">Patient / Demographics</th>
                  <th className="py-3.5 px-3">Vital Signs &amp; Biometrics</th>
                  <th className="py-3.5 px-3">Care Team &amp; Dept</th>
                  <th className="py-3.5 px-3">Diagnosis &amp; Allergies</th>
                  <th className="py-3.5 px-3">KMS Security</th>
                  <th className="py-3.5 pl-3 pr-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredRecords.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-slate-500">
                      No matching patient records found in current census filter.
                    </td>
                  </tr>
                ) : (
                  filteredRecords.map((record) => {
                    const hasAllergy = record.allergies && !record.shredded;

                    return (
                      <tr
                        key={record.id}
                        className={`hover:bg-slate-50 transition-colors ${
                          record.shredded ? 'opacity-60 bg-red-50/30' : ''
                        }`}
                      >
                        {/* 1. Patient & Demographics */}
                        <td className="py-3.5 pl-4 pr-2">
                          <div className="flex items-center gap-3">
                            <div
                              className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl font-bold text-sm ${
                                record.shredded
                                  ? 'bg-slate-100 text-slate-400'
                                  : 'bg-blue-50 border border-blue-200 text-blue-700'
                              }`}
                            >
                              {record.patientName.charAt(0).toUpperCase()}
                            </div>
                            <div>
                              <div className="flex items-center gap-2">
                                <span className="font-bold text-slate-900 text-sm">
                                  {record.patientName}
                                </span>
                                {record.mrn && (
                                  <span className="font-mono text-[10px] px-1.5 py-0.2 rounded bg-slate-100 text-slate-600 border border-slate-200">
                                    {record.mrn}
                                  </span>
                                )}
                              </div>
                              <div className="flex items-center gap-2 text-[11px] text-slate-500 mt-0.5">
                                <span>{record.gender || 'Unknown'}</span>
                                {record.dateOfBirth && <span>• DOB: {record.dateOfBirth}</span>}
                                {record.bloodType && (
                                  <span className="font-semibold text-rose-600 font-mono">
                                    {record.bloodType}
                                  </span>
                                )}
                              </div>
                            </div>
                          </div>
                        </td>

                        {/* 2. Vital Signs & Biometrics */}
                        <td className="py-3.5 px-3">
                          {record.shredded ? (
                            <span className="text-slate-400 font-mono text-[11px]">[SHREDDED]</span>
                          ) : (
                            <div className="space-y-1">
                              <div className="flex items-center gap-2">
                                <span className="text-slate-500">BP:</span>
                                <span className="font-mono font-semibold text-slate-900">
                                  {record.bloodPressure || '—'}
                                </span>
                              </div>
                              <div className="flex items-center gap-2 text-[11px]">
                                <span className="text-slate-500">HR:</span>
                                <span className="font-mono text-rose-600 font-semibold">
                                  {record.heartRate ? `${record.heartRate} bpm` : '—'}
                                </span>
                                {record.oxygenSaturation && (
                                  <>
                                    <span className="text-slate-300">•</span>
                                    <span className="text-slate-500">SpO₂:</span>
                                    <span className="font-mono text-blue-600 font-semibold">
                                      {record.oxygenSaturation}
                                    </span>
                                  </>
                                )}
                              </div>
                            </div>
                          )}
                        </td>

                        {/* 3. Care Team & Department */}
                        <td className="py-3.5 px-3">
                          <div>
                            <span className="font-medium text-slate-900 block">
                              {record.attendingDoctor || 'Dr. Alistair Finch, MD'}
                            </span>
                            <span className="text-[11px] text-slate-500 flex items-center gap-1 mt-0.5">
                              <Building className="h-3 w-3 text-slate-400" />
                              {record.department || 'General Practice'}
                            </span>
                          </div>
                        </td>

                        {/* 4. Diagnosis & Allergies */}
                        <td className="py-3.5 px-3">
                          <div className="max-w-[220px]">
                            <p className="font-medium text-slate-900 truncate">
                              {record.diagnosis || 'No primary diagnosis recorded'}
                            </p>
                            {hasAllergy && (
                              <div className="flex items-center gap-1 text-[11px] text-amber-700 mt-1 truncate">
                                <AlertTriangle className="h-3 w-3 shrink-0" />
                                <span className="truncate">{record.allergies}</span>
                              </div>
                            )}
                          </div>
                        </td>

                        {/* 5. KMS Security */}
                        <td className="py-3.5 px-3">
                          {record.shredded ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-red-50 border border-red-200 text-red-700 text-[10px] font-semibold">
                              <ShieldOff className="h-3 w-3" /> Shredded
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700 text-[10px] font-semibold">
                              <ShieldCheck className="h-3 w-3" /> Vault KMS Active
                            </span>
                          )}
                        </td>

                        {/* 6. Actions */}
                        <td className="py-3.5 pl-3 pr-4 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <button
                              onClick={() => setViewRecordId(record.id)}
                              className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium text-xs transition border border-slate-200"
                              title="Open full clinical chart"
                            >
                              <Eye className="h-3.5 w-3.5" /> Chart
                            </button>

                            {isDoctor && !record.shredded && (
                              <button
                                onClick={() => handleEdit(record)}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
                                title="Edit clinical record"
                              >
                                <Pencil className="h-3.5 w-3.5" />
                              </button>
                            )}

                            {isDoctor && (
                              <button
                                onClick={() => handleDelete(record.id)}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-red-600 hover:bg-slate-100 transition"
                                title="Delete record"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Modals */}
      {showModal && (
        <CreateRecordModal
          editRecord={editRecord}
          onClose={() => {
            setShowModal(false);
            setEditRecord(null);
          }}
        />
      )}

      {showPatientModal && (
        <PatientFormModal
          isOpen={showPatientModal}
          onClose={() => setShowPatientModal(false)}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['records'] });
            queryClient.invalidateQueries({ queryKey: ['patients'] });
          }}
        />
      )}

      {viewRecordId && (
        <ViewRecordModal
          recordId={viewRecordId}
          onClose={() => setViewRecordId(null)}
        />
      )}
    </>
  );
}
