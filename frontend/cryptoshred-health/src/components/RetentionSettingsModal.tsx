import React, { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  CalendarClock,
  Check,
  X,
  Shield,
  Clock,
  Sparkles,
  AlertCircle,
  Scale,
  RefreshCw,
} from 'lucide-react';
import type { RetentionPolicy } from '../types';

interface RetentionSettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  token?: string;
  onPolicyUpdated?: (policy: RetentionPolicy) => void;
}

interface PresetOption {
  years: number;
  label: string;
  badge: string;
  standardKey: string;
  description: string;
  recommended?: boolean;
}

const PRESETS: PresetOption[] = [
  {
    years: 8,
    label: 'NHS General Practice & Hospital',
    badge: '8 Years (NHS CoP 2021)',
    standardKey: 'UK_NHS_COP_2021',
    description: 'UK NHS Records Management Code of Practice 2021 statutory horizon for general adult medical records.',
    recommended: true,
  },
  {
    years: 6,
    label: 'US HIPAA Security Rule',
    badge: '6 Years (HIPAA § 164.316)',
    standardKey: 'US_HIPAA_SEC_164',
    description: 'US Health Insurance Portability and Accountability Act minimum 6-year retention mandate.',
  },
  {
    years: 25,
    label: 'Pediatric & Maternity Extended',
    badge: '25 Years (NHS Specialized)',
    standardKey: 'UK_NHS_PEDIATRIC_MATERNITY_25Y',
    description: 'Extended pediatric care horizon until patient reaches age 26, and maternity health records retention.',
  },
];

export default function RetentionSettingsModal({
  isOpen,
  onClose,
  token,
  onPolicyUpdated,
}: RetentionSettingsModalProps) {
  const [currentPolicy, setCurrentPolicy] = useState<RetentionPolicy | null>(null);
  const [selectedYears, setSelectedYears] = useState<number>(8);
  const [isCustom, setIsCustom] = useState<boolean>(false);
  const [customYearsInput, setCustomYearsInput] = useState<number>(10);
  const [loading, setLoading] = useState<boolean>(false);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const effectiveYears = isCustom ? customYearsInput : selectedYears;

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen && !saving) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, saving, onClose]);

  useEffect(() => {
    if (isOpen) {
      fetchCurrentPolicy();
    } else {
      setError(null);
      setSuccessMsg(null);
    }
  }, [isOpen]);

  const fetchCurrentPolicy = async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetch('/api/admin/retention-policy', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      if (!res.ok) {
        throw new Error(`Failed to load retention policy: HTTP ${res.status}`);
      }
      const data: RetentionPolicy = await res.json();
      setCurrentPolicy(data);

      const isPreset = PRESETS.some((p) => p.years === data.retentionPeriodYears);
      if (isPreset) {
        setSelectedYears(data.retentionPeriodYears);
        setIsCustom(false);
      } else {
        setIsCustom(true);
        setCustomYearsInput(data.retentionPeriodYears);
      }
    } catch (err: any) {
      console.error('Error fetching retention policy:', err);
      setError(err.message || 'Could not fetch current retention policy');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    if (effectiveYears < 1 || effectiveYears > 100) {
      setError('Retention period must be between 1 and 100 years.');
      return;
    }

    setSaving(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const res = await fetch('/api/admin/retention-policy', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          retentionPeriodYears: effectiveYears,
        }),
      });

      if (!res.ok) {
        const errorData = await res.json().catch(() => ({}));
        throw new Error(errorData.message || `Failed to update retention policy: HTTP ${res.status}`);
      }

      const updatedPolicy: RetentionPolicy = await res.json();
      setCurrentPolicy(updatedPolicy);
      setSuccessMsg(`Retention policy updated to ${updatedPolicy.retentionPeriodYears} years (${updatedPolicy.regulatoryStandard}).`);

      if (onPolicyUpdated) {
        onPolicyUpdated(updatedPolicy);
      }

      setTimeout(() => {
        onClose();
      }, 1200);
    } catch (err: any) {
      console.error('Failed to update policy:', err);
      setError(err.message || 'Failed to save retention policy.');
    } finally {
      setSaving(false);
    }
  };

  if (!isOpen) return null;

  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-2xl max-w-2xl w-full overflow-hidden animate-fade-in flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50/80">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-teal-50 border border-teal-200 text-teal-700 shadow-sm">
              <CalendarClock className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-bold text-slate-900">Statutory Retention Governance</h2>
                <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-teal-100 text-teal-800 border border-teal-200">
                  GDPR Art. 17(3)(b)
                </span>
              </div>
              <p className="text-xs text-slate-500">Configure system-wide legal erasure horizon and clinical retention presets</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="p-2 rounded-xl hover:bg-slate-200/60 text-slate-400 hover:text-slate-700 transition-colors disabled:opacity-50"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6 space-y-5 overflow-y-auto flex-1">
          {error && (
            <div className="flex items-start gap-2.5 rounded-xl bg-rose-50 border border-rose-200 p-3 text-xs text-rose-800 animate-shake">
              <AlertCircle className="h-4 w-4 text-rose-600 shrink-0 mt-0.5" />
              <span>{error}</span>
            </div>
          )}

          {successMsg && (
            <div className="flex items-center gap-2.5 rounded-xl bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800 animate-fade-in">
              <Check className="h-4 w-4 text-emerald-600 shrink-0" />
              <span>{successMsg}</span>
            </div>
          )}

          {/* Current Policy Overview */}
          <div className="rounded-xl bg-slate-50 border border-slate-200 p-4">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-500 flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-slate-400" />
                Active Production Policy
              </span>
              {loading ? (
                <div className="flex items-center gap-1 text-xs text-slate-400">
                  <RefreshCw className="h-3 w-3 animate-spin" /> Loading...
                </div>
              ) : currentPolicy ? (
                <span className="text-xs font-mono font-bold text-teal-700 bg-teal-50 px-2.5 py-0.5 rounded-md border border-teal-200">
                  {currentPolicy.retentionPeriodYears} Years ({currentPolicy.regulatoryStandard})
                </span>
              ) : null}
            </div>

            {currentPolicy && (
              <div className="text-xs text-slate-600 space-y-1">
                <p className="font-medium text-slate-800">{currentPolicy.description}</p>
                <div className="flex items-center gap-4 text-[11px] text-slate-400 font-mono pt-1 border-t border-slate-200/60">
                  <span>Last modified: {new Date(currentPolicy.lastUpdated).toLocaleString()}</span>
                  <span>By: {currentPolicy.updatedBy}</span>
                </div>
              </div>
            )}
          </div>

          {/* Policy Preset Cards */}
          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-2.5">
              Select Regulatory Preset Horizon
            </label>
            <div className="grid grid-cols-1 gap-2.5">
              {PRESETS.map((preset) => {
                const isSelected = !isCustom && selectedYears === preset.years;
                return (
                  <button
                    key={preset.years}
                    type="button"
                    onClick={() => {
                      setIsCustom(false);
                      setSelectedYears(preset.years);
                    }}
                    className={`text-left p-3.5 rounded-xl border transition-all relative ${
                      isSelected
                        ? 'border-teal-600 bg-teal-50/50 shadow-sm ring-1 ring-teal-600'
                        : 'border-slate-200 hover:border-slate-300 bg-white hover:bg-slate-50/50'
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold text-slate-900">{preset.label}</span>
                        {preset.recommended && (
                          <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-emerald-100 text-emerald-800 border border-emerald-200">
                            Recommended
                          </span>
                        )}
                      </div>
                      <span className={`text-xs font-mono font-bold px-2 py-0.5 rounded ${
                        isSelected ? 'bg-teal-600 text-white' : 'bg-slate-100 text-slate-600'
                      }`}>
                        {preset.badge}
                      </span>
                    </div>
                    <p className="text-xs text-slate-500 pr-4">{preset.description}</p>
                  </button>
                );
              })}

              {/* Custom Horizon Option */}
              <button
                type="button"
                onClick={() => setIsCustom(true)}
                className={`text-left p-3.5 rounded-xl border transition-all ${
                  isCustom
                    ? 'border-teal-600 bg-teal-50/50 shadow-sm ring-1 ring-teal-600'
                    : 'border-slate-200 hover:border-slate-300 bg-white hover:bg-slate-50/50'
                }`}
              >
                <div className="flex items-center justify-between mb-1">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-bold text-slate-900">Custom Statutory Schedule</span>
                    <span className="px-2 py-0.5 text-[10px] font-bold rounded-full bg-indigo-100 text-indigo-800 border border-indigo-200">
                      Administrative Override
                    </span>
                  </div>
                  {isCustom && (
                    <span className="text-xs font-mono font-bold px-2 py-0.5 rounded bg-teal-600 text-white">
                      {customYearsInput} Years
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-500">Specify an arbitrary statutory retention horizon between 1 and 100 years.</p>
              </button>
            </div>
          </div>

          {/* Custom Horizon Slider & Input (Conditional) */}
          {isCustom && (
            <div className="rounded-xl border border-indigo-200 bg-indigo-50/40 p-4 space-y-3 animate-fade-in">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-indigo-900">Retention Horizon (Years)</span>
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    min="1"
                    max="100"
                    value={customYearsInput}
                    onChange={(e) => setCustomYearsInput(Math.max(1, Math.min(100, parseInt(e.target.value) || 1)))}
                    className="w-20 px-2.5 py-1 text-sm font-mono font-bold text-slate-900 bg-white border border-indigo-300 rounded-lg text-center focus:ring-2 focus:ring-teal-500 focus:outline-none"
                  />
                  <span className="text-xs font-semibold text-slate-600">Years</span>
                </div>
              </div>
              <input
                type="range"
                min="1"
                max="100"
                value={customYearsInput}
                onChange={(e) => setCustomYearsInput(parseInt(e.target.value))}
                className="w-full accent-teal-600 h-2 bg-slate-200 rounded-lg cursor-pointer"
              />
              <div className="flex justify-between text-[10px] font-mono text-slate-400">
                <span>1 Year</span>
                <span>25 Years</span>
                <span>50 Years</span>
                <span>75 Years</span>
                <span>100 Years</span>
              </div>
            </div>
          )}

          {/* Real-Time Impact Guarantee Notice */}
          <div className="rounded-xl bg-slate-100/70 border border-slate-200 p-3.5 flex items-start gap-3">
            <Scale className="h-5 w-5 text-slate-600 shrink-0 mt-0.5" />
            <div className="text-xs text-slate-600 leading-relaxed">
              <strong className="text-slate-800">Dynamic Horizon Impact:</strong> Saving this setting immediately recalibrates the rolling legal erasure threshold for all active records:
              <span className="block mt-1 font-mono text-[11px] text-slate-700 bg-white p-1.5 rounded border border-slate-200">
                legalErasureEligibleDate = latestActivityDate + {effectiveYears} years
              </span>
              Patients with last clinical encounters older than {effectiveYears} years will immediately become <strong>🟢 Erasure Eligible</strong>.
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
          <div className="flex items-center gap-2 text-xs text-slate-500 font-medium">
            <Shield className="h-3.5 w-3.5 text-teal-600" />
            <span>Target: <strong>{effectiveYears} Years</strong></span>
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-200/60 transition-colors disabled:opacity-50"
            >
              Cancel
            </button>

            <button
              type="button"
              onClick={handleSave}
              disabled={saving || loading}
              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold bg-teal-600 hover:bg-teal-500 active:bg-teal-700 text-white shadow-sm transition-colors disabled:opacity-50"
            >
              {saving ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  Applying Policy...
                </>
              ) : (
                <>
                  <Check className="h-3.5 w-3.5" />
                  Save Retention Policy
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>,
    document.body
  );
}
