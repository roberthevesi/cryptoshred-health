import { Activity, Heart, Wind, Thermometer, Scale, ShieldOff } from 'lucide-react';
import type { PatientRecord } from '../types';

interface Props {
  record: PatientRecord;
}

export default function VitalsCard({ record }: Props) {
  if (record.shredded) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-6 text-center">
        <ShieldOff className="mx-auto h-8 w-8 text-slate-400 mb-2" />
        <p className="text-sm text-slate-500 font-mono">Biometric telemetry & vital signs shredded under GDPR Art. 17</p>
      </div>
    );
  }

  // Parse Blood Pressure
  const getBpStatus = (bp?: string) => {
    if (!bp) return { label: 'Not Recorded', color: 'text-slate-500', bg: 'bg-slate-100 border-slate-200' };
    const parts = bp.split('/');
    if (parts.length === 2) {
      const sys = parseInt(parts[0]);
      const dia = parseInt(parts[1]);
      if (!isNaN(sys) && !isNaN(dia)) {
        if (sys >= 140 || dia >= 90) {
          return { label: 'Stage 2 HTN', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' };
        }
        if (sys >= 130 || dia >= 80) {
          return { label: 'Stage 1 HTN', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' };
        }
        if (sys >= 120 && dia < 80) {
          return { label: 'Elevated', color: 'text-yellow-700', bg: 'bg-yellow-50 border-yellow-200' };
        }
        return { label: 'Optimal', color: 'text-emerald-700', bg: 'bg-emerald-50 border-emerald-200' };
      }
    }
    return { label: 'Recorded', color: 'text-slate-600', bg: 'bg-slate-100 border-slate-200' };
  };

  // Heart Rate Status
  const getHrStatus = (hr?: number) => {
    if (!hr) return { label: 'Not Recorded', color: 'text-slate-500', bg: 'bg-slate-100 border-slate-200' };
    if (hr < 60) return { label: 'Bradycardia', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' };
    if (hr > 100) return { label: 'Tachycardia', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' };
    return { label: 'Normal Sinus', color: 'text-emerald-700', bg: 'bg-emerald-50 border-emerald-200' };
  };

  // SpO2 Status
  const getSpo2Status = (spo2?: string) => {
    if (!spo2) return { label: 'Not Recorded', color: 'text-slate-500', bg: 'bg-slate-100 border-slate-200' };
    const num = parseInt(spo2.replace('%', ''));
    if (!isNaN(num)) {
      if (num < 90) return { label: 'Hypoxemia (Critical)', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' };
      if (num < 95) return { label: 'Borderline', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' };
      return { label: 'Optimal', color: 'text-blue-700', bg: 'bg-blue-50 border-blue-200' };
    }
    return { label: 'Recorded', color: 'text-slate-600', bg: 'bg-slate-100 border-slate-200' };
  };

  // BMI Status
  const getBmiStatus = (bmiStr?: string) => {
    if (!bmiStr) return { label: 'N/A', color: 'text-slate-500', bg: 'bg-slate-100 border-slate-200' };
    const bmi = parseFloat(bmiStr);
    if (isNaN(bmi)) return { label: 'N/A', color: 'text-slate-500', bg: 'bg-slate-100 border-slate-200' };
    if (bmi < 18.5) return { label: 'Underweight', color: 'text-amber-700', bg: 'bg-amber-50 border-amber-200' };
    if (bmi < 25.0) return { label: 'Normal Weight', color: 'text-emerald-700', bg: 'bg-emerald-50 border-emerald-200' };
    if (bmi < 30.0) return { label: 'Overweight', color: 'text-yellow-700', bg: 'bg-yellow-50 border-yellow-200' };
    return { label: 'Obese (Class I+)', color: 'text-rose-700', bg: 'bg-rose-50 border-rose-200' };
  };

  const bpStatus = getBpStatus(record.bloodPressure);
  const hrStatus = getHrStatus(record.heartRate);
  const spo2Status = getSpo2Status(record.oxygenSaturation);
  const bmiStatus = getBmiStatus(record.bmi);

  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
      {/* 1. Blood Pressure */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Activity className="h-3.5 w-3.5 text-blue-600" /> Blood Pressure
          </span>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${bpStatus.bg} ${bpStatus.color}`}>
            {bpStatus.label}
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-slate-900 tracking-tight">
            {record.bloodPressure || '—'}
          </span>
          <span className="text-[10px] text-slate-400 ml-1">mmHg</span>
        </div>
      </div>

      {/* 2. Heart Rate */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Heart className="h-3.5 w-3.5 text-rose-500" /> Pulse
          </span>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${hrStatus.bg} ${hrStatus.color}`}>
            {hrStatus.label}
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-rose-600 tracking-tight">
            {record.heartRate || '—'}
          </span>
          <span className="text-[10px] text-slate-400 ml-1">bpm</span>
        </div>
      </div>

      {/* 3. Oxygen Saturation */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Wind className="h-3.5 w-3.5 text-blue-500" /> SpO₂
          </span>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${spo2Status.bg} ${spo2Status.color}`}>
            {spo2Status.label}
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-blue-600 tracking-tight">
            {record.oxygenSaturation || '—'}
          </span>
        </div>
      </div>

      {/* 4. Temperature */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Thermometer className="h-3.5 w-3.5 text-amber-500" /> Temp
          </span>
          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded border bg-emerald-50 border-emerald-200 text-emerald-700">
            Afebrile
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-slate-900 tracking-tight">
            {record.temperature || '—'}
          </span>
        </div>
      </div>

      {/* 5. Respiratory Rate */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Activity className="h-3.5 w-3.5 text-indigo-500" /> Resp Rate
          </span>
          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded border bg-slate-100 border-slate-200 text-slate-600">
            Eupnea
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-slate-900 tracking-tight">
            {record.respiratoryRate || '—'}
          </span>
        </div>
      </div>

      {/* 6. BMI & Weight */}
      <div className="rounded-xl border border-slate-200 bg-white p-3 flex flex-col justify-between shadow-sm">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-slate-500 flex items-center gap-1.5">
            <Scale className="h-3.5 w-3.5 text-emerald-600" /> BMI / Weight
          </span>
          <span className={`text-[10px] font-semibold px-1.5 py-0.5 rounded border ${bmiStatus.bg} ${bmiStatus.color}`}>
            {bmiStatus.label}
          </span>
        </div>
        <div className="mt-2">
          <span className="font-mono text-lg font-bold text-emerald-700 tracking-tight">
            {record.bmi || '—'}
          </span>
          {record.weightKg && (
            <span className="text-[10px] text-slate-400 ml-1">({record.weightKg})</span>
          )}
        </div>
      </div>
    </div>
  );
}
