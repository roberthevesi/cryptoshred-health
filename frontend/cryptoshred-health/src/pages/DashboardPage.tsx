import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ShieldCheck,
  LogOut,
  Activity,
  FileCheck2,
  Stethoscope,
  Users,
  ShieldAlert,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate } from 'react-router-dom';
import PatientCensusTable from '../components/PatientCensusTable';
import VerifyProofModal from '../components/VerifyProofModal';
import GpManagementPanel from '../components/GpManagementPanel';
import StaffManagementPanel from '../components/StaffManagementPanel';
import PatientPortalView from '../components/PatientPortalView';
import apiClient from '../lib/axios';
import type { Patient } from '../types';

type Tab = 'patients' | 'gp-directory' | 'staff-management';

const ROLE_BADGE: Record<string, string> = {
  DOCTOR: 'badge-role-doctor',
  PATIENT: 'badge-role-patient',
  AUDITOR: 'badge-role-auditor',
  ADMIN: 'badge-role-admin',
};

export default function DashboardPage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const isAdmin = user?.role === 'ADMIN';
  const isPatientUser = user?.role === 'PATIENT';
  const isClinicalStaff = user?.role === 'DOCTOR' || user?.role === 'AUDITOR';

  const [activeTab, setActiveTab] = useState<Tab>(isAdmin ? 'staff-management' : 'patients');
  const [isVerifyModalOpen, setIsVerifyModalOpen] = useState(false);

  // Fetch Patients (Only for Clinicians / Auditors - Admins are excluded from viewing PHI)
  const { data: patients = [] } = useQuery<Patient[]>({
    queryKey: ['patients'],
    queryFn: () => apiClient.get<Patient[]>('/patients?includeDeleted=true').then((r) => r.data),
    enabled: isClinicalStaff,
  });

  const activePatientsCount = patients.filter((p) => p.isActive !== false && !p.shredded).length;
  const shreddedPatientsCount = patients.filter((p) => p.shredded || p.isActive === false).length;

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Top Nav */}
      <nav className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-blue-50 ring-1 ring-blue-200">
              <ShieldCheck className="h-5 w-5 text-blue-600" />
            </div>
            <div>
              <span className="font-bold text-slate-900">CryptoShred</span>
              <span className="text-blue-600 font-bold"> Health</span>
            </div>
          </div>

          <div className="hidden md:flex items-center gap-2 text-xs text-slate-500">
            <Activity className="h-3.5 w-3.5 text-emerald-500" />
            EHR Core &amp; Vault KMS Key Store Online
          </div>

          <div className="flex items-center gap-3">
            <button
              onClick={() => setIsVerifyModalOpen(true)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-700 text-xs font-medium transition"
            >
              <FileCheck2 className="h-3.5 w-3.5 text-emerald-600" />
              Verify Proof Artifact
            </button>
            <div className="hidden sm:flex items-center gap-2 px-3 py-1.5 rounded-xl bg-slate-100/90 border border-slate-200 shadow-2xs">
              <span className="text-xs font-semibold text-slate-700">{user?.email}</span>
              <span className={`${ROLE_BADGE[user?.role ?? '']} text-[11px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-md`}>
                {user?.role}
              </span>
            </div>
            <button id="logout-btn" onClick={handleLogout} className="btn-ghost gap-1.5" title="Sign out" aria-label="Sign out">
              <LogOut className="h-4 w-4" />
              <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
      </nav>

      {isPatientUser ? (
        <main className="mx-auto max-w-7xl px-6 py-8">
          <PatientPortalView />
        </main>
      ) : (
        <>
          {/* Hero Banner */}
          <div className="border-b border-slate-200 bg-white py-6">
            <div className="mx-auto max-w-7xl px-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h1 className="text-2xl font-bold text-slate-900">
                    {isAdmin
                      ? 'Hospital Administration & Staff Access'
                      : 'Clinical Dashboard & Patient Census'}
                  </h1>
                  <p className="text-xs text-slate-500 mt-1">
                    {isAdmin
                      ? 'Role-Based Access Control, Clinician Provisioning & GP Practice Directory'
                      : 'Zero-Knowledge EHR with Cryptographic Right-to-be-Forgotten (GDPR Article 17 Compliance)'}
                  </p>
                </div>

                {isClinicalStaff && (
                  <div className="flex items-center gap-3">
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-center">
                      <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                        Active Patients
                      </span>
                      <span className="text-lg font-bold text-emerald-600">{activePatientsCount}</span>
                    </div>
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-2 text-center">
                      <span className="text-[10px] font-bold uppercase tracking-wider text-slate-400 block">
                        Crypto-Shredded
                      </span>
                      <span className="text-lg font-bold text-rose-600">{shreddedPatientsCount}</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>

          <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
            {/* Navigation Tabs */}
            <div className="flex gap-1 rounded-xl bg-slate-100 p-1 w-fit border border-slate-200 text-xs font-medium">
              {/* Admin Tabs */}
              {isAdmin && (
                <button
                  onClick={() => setActiveTab('staff-management')}
                  className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
                    activeTab === 'staff-management'
                      ? 'bg-white text-slate-900 font-semibold shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <ShieldAlert className="h-3.5 w-3.5 text-blue-600" />
                  Staff Directory &amp; Provisioning
                </button>
              )}

              {/* Clinician / Auditor Tabs */}
              {isClinicalStaff && (
                <button
                  onClick={() => setActiveTab('patients')}
                  className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
                    activeTab === 'patients'
                      ? 'bg-white text-slate-900 font-semibold shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  <Users className="h-3.5 w-3.5 text-blue-600" />
                  Patient Census Explorer ({patients.length})
                </button>
              )}

              {/* Shared Tab: GP Directory */}
              <button
                onClick={() => setActiveTab('gp-directory')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 transition-all ${
                  activeTab === 'gp-directory'
                    ? 'bg-white text-slate-900 font-semibold shadow-sm'
                    : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                <Stethoscope className="h-3.5 w-3.5 text-emerald-600" />
                General Practitioner Directory
              </button>
            </div>

            {/* Staff Management Tab (Admin only) */}
            {isAdmin && activeTab === 'staff-management' && (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
                <StaffManagementPanel />
              </div>
            )}

            {/* Patients Census Tab (Clinical Staff only) */}
            {isClinicalStaff && activeTab === 'patients' && (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
                <PatientCensusTable />
              </div>
            )}

            {/* GP Directory Tab (Admins, Doctors, Auditors) */}
            {activeTab === 'gp-directory' && (
              <div className="bg-white border border-slate-200 rounded-2xl shadow-card p-6 animate-fade-in">
                <GpManagementPanel />
              </div>
            )}
          </main>
        </>
      )}

      <VerifyProofModal
        isOpen={isVerifyModalOpen}
        onClose={() => setIsVerifyModalOpen(false)}
        token={user?.token || ''}
      />
    </div>
  );
}
