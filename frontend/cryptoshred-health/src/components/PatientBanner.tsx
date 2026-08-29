import { AlertTriangle, User, Calendar, Droplets, Stethoscope, ShieldCheck, ShieldAlert } from 'lucide-react';
import type { PatientRecord } from '../types';

interface Props {
  record: PatientRecord;
  showVitalsSummary?: boolean;
}

export default function PatientBanner({ record, showVitalsSummary = true }: Props) {
  const getAge = (dobString?: string) => {
    if (!dobString) return null;
    try {
      const dob = new Date(dobString);
      const diffMs = Date.now() - dob.getTime();
      const ageDate = new Date(diffMs);
      return Math.abs(ageDate.getUTCFullYear() - 1970);
    } catch {
      return null;
    }
  };

  const age = getAge(record.dateOfBirth);
  const hasSevereAllergy =
    record.allergies &&
    !record.shredded &&
    (record.allergies.toLowerCase().includes('severe') ||
      record.allergies.toLowerCase().includes('anaphylaxis') ||
      record.allergies.toLowerCase().includes('penicillin'));

  return (
    <div
      className={`rounded-2xl border p-4 sm:p-5 transition-all ${
        record.shredded
          ? 'bg-slate-50 border-slate-200'
          : 'bg-white border-slate-200 shadow-card'
      }`}
    >
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        {/* Left: Avatar & Demographics Anchor */}
        <div className="flex items-start sm:items-center gap-4">
          <div
            className={`relative flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl border font-bold text-xl ${
              record.shredded
                ? 'bg-slate-100 border-slate-200 text-slate-400'
                : 'bg-blue-600 border-blue-500 text-white shadow-sm'
            }`}
          >
            {(() => {
              if (record.shredded) return '✕';
              const parts = (record.patientName || '').trim().split(/\s+/);
              if (parts.length >= 2) return `${parts[0].charAt(0)}${parts[parts.length - 1].charAt(0)}`.toUpperCase();
              return (record.patientName || 'PT').slice(0, 2).toUpperCase();
            })()}
            <span
              className={`absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full border-2 border-white ${
                record.shredded ? 'bg-slate-400' : 'bg-emerald-500'
              }`}
            />
          </div>

          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-xl font-bold text-slate-900 tracking-tight">{record.patientName}</h2>
              {record.mrn && (
                <span className="font-mono text-xs font-semibold px-2.5 py-0.5 rounded-md bg-slate-100 border border-slate-200 text-slate-700">
                  {record.mrn}
                </span>
              )}
              {record.shredded ? (
                <span className="flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-md bg-red-50 border border-red-200 text-red-700">
                  <ShieldAlert className="h-3 w-3 text-red-600" /> Crypto-Shredded
                </span>
              ) : (
                <span className="flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-md bg-emerald-50 border border-emerald-200 text-emerald-700">
                  <ShieldCheck className="h-3 w-3 text-emerald-600" /> Vault KMS Protected
                </span>
              )}
            </div>

            <div className="mt-1.5 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-600">
              <span className="flex items-center gap-1">
                <User className="h-3.5 w-3.5 text-slate-400" />
                {record.gender || 'Unknown'} {age ? `• ${age} yrs` : ''}
              </span>

              {record.dateOfBirth && (
                <span className="flex items-center gap-1">
                  <Calendar className="h-3.5 w-3.5 text-slate-400" />
                  DOB: {record.dateOfBirth}
                </span>
              )}

              {record.bloodType && (
                <span className="flex items-center gap-1 font-semibold text-rose-700 bg-rose-50 border border-rose-200 px-1.5 py-0.2 rounded">
                  <Droplets className="h-3 w-3 text-rose-500" />
                  {record.bloodType}
                </span>
              )}

              {record.attendingDoctor && (
                <span className="flex items-center gap-1">
                  <Stethoscope className="h-3.5 w-3.5 text-blue-600" />
                  {record.attendingDoctor}
                  {record.department && <span className="text-slate-400">({record.department})</span>}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Right: Clinical Triaging & Allergy Alert Bar */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3 self-stretch lg:self-auto justify-end">
          {/* Severe Allergy Warning Banner */}
          {record.allergies && !record.shredded && (
            <div
              className={`flex items-center gap-2 rounded-xl px-3 py-2 text-xs font-medium border ${
                hasSevereAllergy
                  ? 'bg-red-50 border-red-200 text-red-800'
                  : 'bg-amber-50 border-amber-200 text-amber-800'
              }`}
            >
              <AlertTriangle
                className={`h-4 w-4 shrink-0 ${
                  hasSevereAllergy ? 'text-red-600 animate-pulse' : 'text-amber-600'
                }`}
              />
              <div className="truncate max-w-[220px]">
                <span className="font-bold uppercase tracking-wider text-[10px] block opacity-80">Allergies</span>
                <span className="truncate">{record.allergies}</span>
              </div>
            </div>
          )}

          {/* Quick Vitals Capsule */}
          {showVitalsSummary && !record.shredded && (record.bloodPressure || record.heartRate) && (
            <div className="flex items-center gap-3 rounded-xl bg-slate-50 border border-slate-200 px-3.5 py-2 text-xs">
              {record.bloodPressure && (
                <div>
                  <span className="text-[10px] font-semibold uppercase text-slate-500 block">BP</span>
                  <span className="font-mono font-bold text-slate-900">{record.bloodPressure}</span>
                </div>
              )}
              {record.heartRate && (
                <div className="border-l border-slate-200 pl-3">
                  <span className="text-[10px] font-semibold uppercase text-slate-500 block">Pulse</span>
                  <span className="font-mono font-bold text-rose-600">
                    {record.heartRate} <span className="text-[10px] font-normal text-slate-500">bpm</span>
                  </span>
                </div>
              )}
              {record.oxygenSaturation && (
                <div className="border-l border-slate-200 pl-3">
                  <span className="text-[10px] font-semibold uppercase text-slate-500 block">SpO₂</span>
                  <span className="font-mono font-bold text-blue-600">{record.oxygenSaturation}</span>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
