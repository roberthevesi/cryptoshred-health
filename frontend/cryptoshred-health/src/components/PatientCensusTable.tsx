import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Search,
  UserPlus,
  Stethoscope,
  Building2,
  ChevronLeft,
  ChevronRight,
  ChevronsLeft,
  ChevronsRight,
  ShieldCheck,
  ShieldOff,
  UserX,
  UserCog,
  RefreshCw,
  Phone,
  Mail,
  Users,
} from 'lucide-react';
import apiClient from '../lib/axios';
import { useAuth } from '../contexts/AuthContext';
import PatientFormModal from './PatientFormModal';
import type { Patient } from '../types';

export default function PatientCensusTable() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [searchQuery, setSearchQuery] = useState('');
  const [activeCensusTab, setActiveCensusTab] = useState<'active' | 'inactive' | 'shredded' | 'all'>('active');
  const [selectedPatientForEdit, setSelectedPatientForEdit] = useState<Patient | null>(null);
  const [showPatientModal, setShowPatientModal] = useState(false);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 1. Fetch Patients from /api/patients (includes shredded records)
  const {
    data: patients = [],
    isLoading: isPatientsLoading,
    isError: isPatientsError,
    refetch: refetchPatients,
  } = useQuery<Patient[]>({
    queryKey: ['patients'],
    queryFn: () => apiClient.get<Patient[]>('/patients?includeDeleted=true').then((r) => r.data),
  });

  const isDoctor = user?.role === 'DOCTOR';

  const activeCount = patients.filter((p) => !p.shredded && p.isActive !== false && p.active !== false).length;
  const inactiveCount = patients.filter((p) => !p.shredded && (p.isActive === false || p.active === false)).length;
  const shreddedCount = patients.filter((p) => !!p.shredded).length;
  const totalCount = patients.length;

  const getAge = (dobString?: string) => {
    if (!dobString) return null;
    try {
      const dob = new Date(dobString);
      const today = new Date();
      let age = today.getFullYear() - dob.getFullYear();
      const m = today.getMonth() - dob.getMonth();
      if (m < 0 || (m === 0 && today.getDate() < dob.getDate())) {
        age--;
      }
      return age >= 0 ? age : null;
    } catch {
      return null;
    }
  };

  // Filter patients by tab and search query
  const filteredPatients = patients.filter((p) => {
    const isShredded = !!p.shredded;
    const isInactive = !isShredded && (p.isActive === false || p.active === false);
    const isActive = !isShredded && !isInactive;

    if (activeCensusTab === 'active' && !isActive) return false;
    if (activeCensusTab === 'inactive' && !isInactive) return false;
    if (activeCensusTab === 'shredded' && !isShredded) return false;

    if (!searchQuery) return true;
    const q = searchQuery.toLowerCase();
    const fullName = `${p.firstName} ${p.lastName}`.toLowerCase();
    const gpName = p.gp ? `${p.gp.firstName} ${p.gp.lastName}`.toLowerCase() : '';
    const practice = p.gp?.practiceName?.toLowerCase() ?? '';
    const nhs = p.nhsNumber?.toLowerCase() ?? '';
    const id = p.patientId.toLowerCase();

    return (
      fullName.includes(q) ||
      id.includes(q) ||
      nhs.includes(q) ||
      gpName.includes(q) ||
      practice.includes(q)
    );
  });

  // Pagination calculation
  const totalPages = Math.max(1, Math.ceil(filteredPatients.length / pageSize));
  const validCurrentPage = Math.min(Math.max(1, currentPage), totalPages);
  const startIndex = (validCurrentPage - 1) * pageSize;
  const endIndex = Math.min(startIndex + pageSize, filteredPatients.length);
  const paginatedPatients = filteredPatients.slice(startIndex, endIndex);

  const getPageNumbers = (current: number, total: number): (number | string)[] => {
    if (total <= 7) {
      return Array.from({ length: total }, (_, i) => i + 1);
    }
    if (current <= 4) {
      return [1, 2, 3, 4, 5, '...', total];
    }
    if (current >= total - 3) {
      return [1, '...', total - 4, total - 3, total - 2, total - 1, total];
    }
    return [1, '...', current - 1, current, current + 1, '...', total];
  };

  if (isPatientsLoading) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-slate-500 text-sm font-mono">Loading patient directory...</p>
      </div>
    );
  }

  if (isPatientsError) {
    return (
      <div className="flex flex-col items-center justify-center py-20 gap-3 text-center">
        <ShieldOff className="h-10 w-10 text-red-500" />
        <p className="text-slate-700 font-medium">Failed to load patient directory</p>
        <button onClick={() => refetchPatients()} className="btn-ghost">
          <RefreshCw className="h-4 w-4" /> Retry
        </button>
      </div>
    );
  }

  return (
    <>
      <div className="space-y-4">
        {/* Table Top Controls & Search Bar */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
              Primary Care Patient Census
              <span className="text-xs font-mono font-normal bg-blue-50 text-blue-700 border border-blue-200 px-2.5 py-0.5 rounded-full">
                {totalCount} Registered Patients
              </span>
            </h2>
            <p className="text-xs text-slate-500">
              Click any patient to open their comprehensive clinical chart file and manage their medical visits.
            </p>
          </div>

          <div className="flex items-center gap-3">
            {/* Search Input */}
            <div className="relative min-w-[240px]">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input
                type="text"
                placeholder="Search by name, NHS #, GP..."
                value={searchQuery}
                onChange={(e) => {
                  setSearchQuery(e.target.value);
                  setCurrentPage(1);
                }}
                className="w-full pl-9 pr-3 py-1.5 rounded-xl bg-white border border-slate-300 text-xs text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
              />
            </div>

            {/* Register Patient Button (Doctors Only) */}
            {isDoctor && (
              <button
                id="register-patient-census-btn"
                onClick={() => {
                  setSelectedPatientForEdit(null);
                  setShowPatientModal(true);
                }}
                className="flex items-center gap-1.5 px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition shrink-0"
              >
                <UserPlus className="h-4 w-4" /> Register Patient
              </button>
            )}
          </div>
        </div>

        {/* Census Tab Navigation */}
        <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
          <div className="inline-flex rounded-xl bg-slate-100 p-1 border border-slate-200 text-xs font-medium">
            {/* 1. Active Patients */}
            <button
              onClick={() => {
                setActiveCensusTab('active');
                setCurrentPage(1);
              }}
              className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                activeCensusTab === 'active'
                  ? 'bg-white text-emerald-800 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldCheck className={`h-3.5 w-3.5 ${activeCensusTab === 'active' ? 'text-emerald-600' : 'text-slate-400'}`} />
              <span>Active Patients</span>
              <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                activeCensusTab === 'active' ? 'bg-emerald-100 text-emerald-800 font-bold' : 'bg-slate-200 text-slate-600'
              }`}>
                {activeCount}
              </span>
            </button>

            {/* 2. Inactive Patients */}
            <button
              onClick={() => {
                setActiveCensusTab('inactive');
                setCurrentPage(1);
              }}
              className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                activeCensusTab === 'inactive'
                  ? 'bg-white text-amber-800 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <UserX className={`h-3.5 w-3.5 ${activeCensusTab === 'inactive' ? 'text-amber-600' : 'text-slate-400'}`} />
              <span>Inactive Patients</span>
              <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                activeCensusTab === 'inactive' ? 'bg-amber-100 text-amber-800 font-bold' : 'bg-slate-200 text-slate-600'
              }`}>
                {inactiveCount}
              </span>
            </button>

            {/* 3. Crypto-Shredded Patients */}
            <button
              onClick={() => {
                setActiveCensusTab('shredded');
                setCurrentPage(1);
              }}
              className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                activeCensusTab === 'shredded'
                  ? 'bg-white text-rose-800 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <ShieldOff className={`h-3.5 w-3.5 ${activeCensusTab === 'shredded' ? 'text-rose-600' : 'text-slate-400'}`} />
              <span>Crypto-Shredded Patients</span>
              <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                activeCensusTab === 'shredded' ? 'bg-rose-100 text-rose-800 font-bold' : 'bg-slate-200 text-slate-600'
              }`}>
                {shreddedCount}
              </span>
            </button>

            {/* 4. All Patients */}
            <button
              onClick={() => {
                setActiveCensusTab('all');
                setCurrentPage(1);
              }}
              className={`inline-flex items-center gap-2 rounded-lg px-3.5 py-1.5 transition-all ${
                activeCensusTab === 'all'
                  ? 'bg-white text-slate-900 font-semibold shadow-sm'
                  : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              <Users className={`h-3.5 w-3.5 ${activeCensusTab === 'all' ? 'text-slate-700' : 'text-slate-400'}`} />
              <span>All Patients</span>
              <span className={`px-1.5 py-0.2 rounded-full text-[10px] font-mono ${
                activeCensusTab === 'all' ? 'bg-slate-200 text-slate-900 font-bold' : 'bg-slate-200 text-slate-600'
              }`}>
                {totalCount}
              </span>
            </button>
          </div>
        </div>

        {/* Patients Table */}
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-slate-200 bg-slate-50 text-slate-600 font-semibold uppercase tracking-wider text-[11px]">
                <tr>
                  <th className="py-3.5 pl-4 pr-2 whitespace-nowrap min-w-[210px]">Patient Name &amp; ID</th>
                  <th className="py-3.5 px-3 whitespace-nowrap min-w-[130px]">Demographics</th>
                  <th className="py-3.5 px-3 whitespace-nowrap min-w-[180px]">Assigned GP Surgery</th>
                  <th className="py-3.5 px-3 whitespace-nowrap min-w-[180px]">Contact</th>
                  <th className="py-3.5 px-3 whitespace-nowrap min-w-[110px]">Status</th>
                  <th className="py-3.5 pl-3 pr-4 text-right whitespace-nowrap min-w-[130px]">Patient File</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredPatients.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-12 text-center text-slate-500">
                      {patients.length === 0
                        ? 'No patients registered in the database yet. Click "Register Patient" above to add the first patient.'
                        : 'No matching patients found in registry.'}
                    </td>
                  </tr>
                ) : (
                  paginatedPatients.map((patient) => {
                    const isShredded = !!patient.shredded;
                    const isInactive = !isShredded && (patient.isActive === false || patient.active === false);
                    const age = !isShredded ? getAge(patient.dateOfBirth) : null;
                    const initials = isShredded
                      ? '✕'
                      : `${patient.firstName?.charAt(0) || ''}${patient.lastName?.charAt(0) || ''}`.toUpperCase() || 'PT';

                    return (
                      <tr
                        key={patient.id}
                        onClick={() => navigate(`/patients/${patient.patientId}`)}
                        className={`cursor-pointer transition-colors group ${
                          isShredded
                            ? 'bg-rose-50/40 hover:bg-rose-50/70 border-l-4 border-l-rose-500'
                            : isInactive
                            ? 'bg-amber-50/30 hover:bg-amber-50/60 border-l-4 border-l-amber-500'
                            : 'hover:bg-blue-50/40'
                        }`}
                      >
                        {/* 1. Patient Details */}
                        <td className="py-3.5 pl-4 pr-2">
                          <div className="flex items-center gap-3">
                            <div
                              className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl font-bold text-xs transition-colors ${
                                isShredded
                                  ? 'bg-rose-100 border border-rose-300 text-rose-700 group-hover:bg-rose-600 group-hover:text-white'
                                  : isInactive
                                  ? 'bg-amber-100 border border-amber-300 text-amber-800 group-hover:bg-amber-600 group-hover:text-white'
                                  : 'bg-blue-50 border border-blue-200 text-blue-700 group-hover:bg-blue-600 group-hover:text-white'
                              }`}
                            >
                              {initials}
                            </div>
                            <div className="min-w-0">
                              <div className="flex items-center gap-2 flex-wrap">
                                <span
                                  className={`font-bold text-sm transition-colors truncate ${
                                    isShredded
                                      ? 'text-rose-900 font-mono'
                                      : 'text-slate-900 group-hover:text-blue-600'
                                  }`}
                                >
                                  {isShredded ? '[SHREDDED]' : `${patient.firstName} ${patient.lastName}`}
                                </span>
                                {isShredded ? (
                                  <span className="font-mono text-[10px] px-1.5 py-0.2 rounded bg-rose-100 text-rose-800 border border-rose-200 font-semibold shrink-0">
                                    GDPR Art. 17
                                  </span>
                                ) : (
                                  patient.nhsNumber && (
                                    <span className="font-mono text-[10px] px-1.5 py-0.2 rounded bg-blue-50 text-blue-700 border border-blue-200 shrink-0">
                                      NHS {patient.nhsNumber}
                                    </span>
                                  )
                                )}
                              </div>
                              <span className="text-[11px] text-slate-400 font-mono block">
                                ID: {patient.patientId}
                              </span>
                            </div>
                          </div>
                        </td>

                        {/* 2. Demographics */}
                        <td className="py-3.5 px-3">
                          {isShredded ? (
                            <span className="text-slate-400 font-mono text-[11px] italic">[SHREDDED]</span>
                          ) : (
                            <div className="space-y-0.5">
                              <span className="text-slate-700 font-medium block whitespace-nowrap">
                                {patient.gender || 'Unknown'} {age ? `• ${age} yrs` : ''}
                              </span>
                              {patient.dateOfBirth && (
                                <span className="text-[11px] text-slate-400 block whitespace-nowrap">
                                  DOB: {patient.dateOfBirth}
                                </span>
                              )}
                            </div>
                          )}
                        </td>

                        {/* 3. Assigned GP */}
                        <td className="py-3.5 px-3">
                          {isShredded ? (
                            <span className="text-slate-400 font-mono text-[11px] italic">[SHREDDED]</span>
                          ) : patient.gp ? (
                            <div className="max-w-[170px]">
                              <span className="font-medium text-slate-900 flex items-center gap-1 truncate" title={`Dr. ${patient.gp.firstName} ${patient.gp.lastName}`}>
                                <Stethoscope className="h-3.5 w-3.5 text-blue-600 shrink-0" />
                                <span className="truncate">Dr. {patient.gp.firstName} {patient.gp.lastName}</span>
                              </span>
                              <span className="text-[11px] text-slate-500 flex items-center gap-1 mt-0.5 truncate" title={patient.gp.practiceName || `GMC: ${patient.gp.gmcNumber}`}>
                                <Building2 className="h-3 w-3 text-slate-400 shrink-0" />
                                <span className="truncate">{patient.gp.practiceName || `GMC: ${patient.gp.gmcNumber}`}</span>
                              </span>
                            </div>
                          ) : (
                            <span className="text-slate-400 italic text-xs">Unassigned</span>
                          )}
                        </td>

                        {/* 4. Contact */}
                        <td className="py-3.5 px-3">
                          {isShredded ? (
                            <span className="text-slate-400 font-mono text-[11px] italic">[SHREDDED]</span>
                          ) : (
                            <div className="space-y-0.5 text-[11px] max-w-[175px]">
                              {patient.phoneNumber && (
                                <div className="text-slate-700 flex items-center gap-1.5 truncate" title={patient.phoneNumber}>
                                  <Phone className="h-3 w-3 text-slate-400 shrink-0" />
                                  <span className="truncate">{patient.phoneNumber}</span>
                                </div>
                              )}
                              {patient.email && (
                                <div className="text-slate-500 flex items-center gap-1.5 truncate" title={patient.email}>
                                  <Mail className="h-3 w-3 text-slate-400 shrink-0" />
                                  <span className="truncate">{patient.email}</span>
                                </div>
                              )}
                            </div>
                          )}
                        </td>

                        {/* 5. Status */}
                        <td className="py-3.5 px-3 whitespace-nowrap">
                          {isShredded ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-rose-100 border border-rose-300 text-rose-800 text-[10px] font-bold">
                              <ShieldOff className="h-3 w-3" /> Crypto-Shredded
                            </span>
                          ) : isInactive ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-amber-50 border border-amber-200 text-amber-700 text-[10px] font-semibold">
                              <UserX className="h-3 w-3" /> Inactive
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700 text-[10px] font-semibold">
                              <ShieldCheck className="h-3 w-3" /> Active
                            </span>
                          )}
                        </td>

                        {/* 6. Action */}
                        <td className="py-3.5 pl-3 pr-4 text-right">
                          <div className="flex items-center justify-end gap-1.5" onClick={(e) => e.stopPropagation()}>
                            <button
                              onClick={() => navigate(`/patients/${patient.patientId}`)}
                              className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg font-semibold text-xs transition border shadow-sm ${
                                isShredded
                                  ? 'bg-rose-50 hover:bg-rose-100 text-rose-700 border-rose-200'
                                  : isInactive
                                  ? 'bg-amber-50 hover:bg-amber-100 text-amber-700 border-amber-200'
                                  : 'bg-blue-50 hover:bg-blue-100 text-blue-700 border-blue-200'
                              }`}
                            >
                              Open File <ChevronRight className="h-3.5 w-3.5" />
                            </button>

                            {isDoctor && !isShredded && (
                              <button
                                onClick={() => {
                                  setSelectedPatientForEdit(patient);
                                  setShowPatientModal(true);
                                }}
                                className="p-1.5 rounded-lg text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition"
                                title="Edit patient demographics"
                              >
                                <UserCog className="h-3.5 w-3.5" />
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

          {/* Pagination Control Bar */}
          {filteredPatients.length > 0 && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 px-6 py-3.5 border-t border-slate-200 bg-slate-50/70">
              {/* Left: Range and Page Size */}
              <div className="flex items-center gap-4 text-xs text-slate-600">
                <span>
                  Showing <strong className="font-semibold text-slate-900">{filteredPatients.length === 0 ? 0 : startIndex + 1}</strong>–<strong className="font-semibold text-slate-900">{endIndex}</strong> of <strong className="font-semibold text-slate-900">{filteredPatients.length}</strong> patients
                </span>
                <div className="flex items-center gap-1.5 border-l border-slate-200 pl-4">
                  <span className="text-slate-500">Per page:</span>
                  <select
                    value={pageSize}
                    onChange={(e) => {
                      setPageSize(Number(e.target.value));
                      setCurrentPage(1);
                    }}
                    className="rounded-lg border border-slate-300 bg-white px-2 py-1 text-xs text-slate-700 font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value={10}>10</option>
                    <option value={25}>25</option>
                    <option value={50}>50</option>
                    <option value={100}>100</option>
                  </select>
                </div>
              </div>

              {/* Right: Page Navigation Buttons */}
              {totalPages > 1 && (
                <div className="flex items-center gap-1.5">
                  <button
                    onClick={() => setCurrentPage(1)}
                    disabled={validCurrentPage === 1}
                    className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
                    title="First Page"
                  >
                    <ChevronsLeft className="h-4 w-4" />
                  </button>
                  <button
                    onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                    disabled={validCurrentPage === 1}
                    className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
                  >
                    <ChevronLeft className="h-3.5 w-3.5" /> Previous
                  </button>

                  {/* Page Number Pills */}
                  <div className="flex items-center gap-1">
                    {getPageNumbers(validCurrentPage, totalPages).map((page, idx) =>
                      page === '...' ? (
                        <span key={`dots-${idx}`} className="px-2 text-xs text-slate-400">...</span>
                      ) : (
                        <button
                          key={`page-${page}`}
                          onClick={() => setCurrentPage(Number(page))}
                          className={`min-w-[32px] h-8 px-2 rounded-lg text-xs font-bold transition ${
                            validCurrentPage === page
                              ? 'bg-blue-600 text-white shadow-sm'
                              : 'border border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
                          }`}
                        >
                          {page}
                        </button>
                      )
                    )}
                  </div>

                  <button
                    onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                    disabled={validCurrentPage === totalPages}
                    className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg border border-slate-200 bg-white text-xs font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
                  >
                    Next <ChevronRight className="h-3.5 w-3.5" />
                  </button>
                  <button
                    onClick={() => setCurrentPage(totalPages)}
                    disabled={validCurrentPage === totalPages}
                    className="p-1.5 rounded-lg border border-slate-200 bg-white text-slate-600 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition"
                    title="Last Page"
                  >
                    <ChevronsRight className="h-4 w-4" />
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Patient Form Modal */}
      {showPatientModal && (
        <PatientFormModal
          isOpen={showPatientModal}
          patient={selectedPatientForEdit ?? undefined}
          onClose={() => {
            setShowPatientModal(false);
            setSelectedPatientForEdit(null);
          }}
          onSuccess={() => {
            queryClient.invalidateQueries({ queryKey: ['patients'] });
            queryClient.invalidateQueries({ queryKey: ['visits'] });
          }}
        />
      )}
    </>
  );
}
