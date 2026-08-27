import { useState, type FormEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation } from '@tanstack/react-query';
import { X, UserPlus, UserCog, AlertCircle, KeyRound, Copy, Check, ShieldCheck, Heart, Shield, PhoneCall } from 'lucide-react';
import apiClient from '../lib/axios';
import GpSelector from './GpSelector';
import type { Patient, PatientRequest } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  patient?: Patient;
  onSuccess: () => void;
}

export default function PatientFormModal({ isOpen, onClose, patient, onSuccess }: Props) {
  const isEdit = !!patient;

  const [form, setForm] = useState<PatientRequest>({
    patientId: patient?.patientId ?? '',
    firstName: patient?.firstName ?? '',
    lastName: patient?.lastName ?? '',
    dateOfBirth: patient?.dateOfBirth ?? '',
    gender: patient?.gender ?? '',
    email: patient?.email ?? '',
    phoneNumber: patient?.phoneNumber ?? '',
    address: patient?.address ?? '',
    nhsNumber: patient?.nhsNumber ?? '',
    bloodType: patient?.bloodType ?? '',
    emergencyContactName: patient?.emergencyContactName ?? '',
    emergencyContactPhone: patient?.emergencyContactPhone ?? '',
    emergencyContactRelationship: patient?.emergencyContactRelationship ?? '',
    insuranceProvider: patient?.insuranceProvider ?? '',
    insurancePolicyNumber: patient?.insurancePolicyNumber ?? '',
    insuranceGroupNumber: patient?.insuranceGroupNumber ?? '',
    gpId: patient?.gp?.id ?? undefined,
  });

  const [error, setError] = useState('');
  const [provisionedCredentials, setProvisionedCredentials] = useState<{
    patientName: string;
    patientId: string;
    email: string;
    temporaryPassword: string;
  } | null>(null);
  const [copied, setCopied] = useState(false);

  const mutation = useMutation({
    mutationFn: (data: PatientRequest) => {
      if (isEdit) {
        return apiClient.put<Patient>(`/patients/${patient!.patientId}`, data);
      }
      return apiClient.post<Patient>('/patients', data);
    },
    onSuccess: (response) => {
      setError('');
      if (!isEdit && response?.data?.temporaryPassword) {
        setProvisionedCredentials({
          patientName: `${response.data.firstName} ${response.data.lastName}`,
          patientId: response.data.patientId,
          email: response.data.email,
          temporaryPassword: response.data.temporaryPassword,
        });
      } else {
        onSuccess();
        onClose();
      }
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        `Failed to ${isEdit ? 'update' : 'create'} patient. Please try again.`;
      setError(msg);
    },
  });

  const handleCopyPassword = async () => {
    if (!provisionedCredentials?.temporaryPassword) return;
    try {
      await navigator.clipboard.writeText(provisionedCredentials.temporaryPassword);
      setCopied(true);
      setTimeout(() => setCopied(false), 2500);
    } catch {
      // Fallback
    }
  };

  const handleFinishProvisioning = () => {
    setProvisionedCredentials(null);
    onSuccess();
    onClose();
  };

  const handleClose = () => {
    setProvisionedCredentials(null);
    onClose();
  };

  const handleChange = (field: keyof PatientRequest, value: string | undefined) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate(form);
  };

  if (!isOpen) return null;

  if (provisionedCredentials) {
    return createPortal(
      <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
        <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-lg w-full overflow-hidden animate-fade-in">
          {/* Header */}
          <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200 bg-emerald-50/50">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 text-emerald-700">
                <KeyRound className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-semibold text-slate-900">Account Provisioned Successfully</h2>
                <p className="text-xs text-slate-500">Zero-Knowledge Patient Portal Access</p>
              </div>
            </div>
            <button
              type="button"
              onClick={handleClose}
              className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Body */}
          <div className="p-6 space-y-5">
            <div className="rounded-xl bg-emerald-50 border border-emerald-200/80 p-4 text-xs text-emerald-800 flex items-start gap-2.5">
              <ShieldCheck className="h-4 w-4 shrink-0 mt-0.5 text-emerald-600" />
              <span>
                A secure patient user account has been provisioned. The patient can use these credentials to log in, view clinical visits, and export HL7 FHIR records.
              </span>
            </div>

            <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50/50 p-4">
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div>
                  <span className="text-slate-400 font-medium">Patient Name</span>
                  <p className="text-slate-900 font-semibold mt-0.5">{provisionedCredentials.patientName}</p>
                </div>
                <div>
                  <span className="text-slate-400 font-medium">Clinic ID</span>
                  <p className="text-slate-900 font-mono font-semibold mt-0.5">{provisionedCredentials.patientId}</p>
                </div>
              </div>

              <div className="pt-2 border-t border-slate-200/60">
                <span className="text-xs text-slate-400 font-medium">Login Email</span>
                <p className="text-sm text-slate-900 font-medium mt-0.5">{provisionedCredentials.email}</p>
              </div>

              <div className="pt-2 border-t border-slate-200/60">
                <div className="flex items-center justify-between mb-1.5">
                  <span className="text-xs font-semibold text-slate-700">Temporary Password</span>
                  <span className="text-[10px] text-amber-600 font-medium bg-amber-50 px-2 py-0.5 rounded-full border border-amber-200">
                    One-Time Handover
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    readOnly
                    value={provisionedCredentials.temporaryPassword}
                    className="w-full rounded-xl border border-slate-300 bg-white px-3.5 py-2.5 text-sm font-mono font-bold text-slate-900 focus:outline-none select-all"
                  />
                  <button
                    type="button"
                    onClick={handleCopyPassword}
                    className={`inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-xs font-semibold transition-all ${
                      copied
                        ? 'bg-emerald-600 text-white'
                        : 'bg-slate-900 hover:bg-slate-800 text-white shadow-sm'
                    }`}
                  >
                    {copied ? (
                      <>
                        <Check className="h-3.5 w-3.5" />
                        Copied
                      </>
                    ) : (
                      <>
                        <Copy className="h-3.5 w-3.5" />
                        Copy
                      </>
                    )}
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end">
            <button
              type="button"
              onClick={handleFinishProvisioning}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-sm font-semibold transition-colors"
            >
              Done &amp; Close
            </button>
          </div>
        </div>
      </div>,
      document.body
    );
  }

  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-5 border-b border-slate-200 sticky top-0 bg-white z-10">
          <div className="flex items-center gap-3">
            {isEdit ? (
              <UserCog className="h-5 w-5 text-blue-600" />
            ) : (
              <UserPlus className="h-5 w-5 text-blue-600" />
            )}
            <div>
              <h2 className="text-lg font-semibold text-slate-900">
                {isEdit ? 'Edit Patient Demographic Profile' : 'Register New Patient Profile'}
              </h2>
              <p className="text-xs text-slate-500">
                Envelope-encrypted master identity, contact, and insurance records
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="px-6 py-6 space-y-6">
            {error && (
              <div className="flex items-center gap-3 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
                <AlertCircle className="h-4 w-4 shrink-0" />
                {error}
              </div>
            )}

            {/* SECTION 1: Identity & Demographics */}
            <div className="space-y-4">
              <h3 className="text-xs font-bold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                <UserPlus className="h-3.5 w-3.5" /> 1. Master Identity &amp; Demographics
              </h3>

              {/* Patient ID */}
              <div>
                <div className="flex items-center justify-between mb-1.5">
                  <label htmlFor="patient-id" className="block text-sm font-medium text-slate-700">
                    Patient Clinic ID
                  </label>
                  <span className="text-xs text-slate-400">
                    {isEdit ? 'Immutable ID' : 'Optional (auto-generated if left blank)'}
                  </span>
                </div>
                <input
                  id="patient-id"
                  type="text"
                  disabled={isEdit}
                  value={form.patientId}
                  onChange={(e) => handleChange('patientId', e.target.value)}
                  placeholder="e.g. PAT-49201 (Leave blank to auto-generate)"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:bg-slate-50 disabled:text-slate-500 transition-all font-mono"
                />
              </div>

              {/* Name row */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="first-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    First Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="first-name"
                    type="text"
                    required
                    value={form.firstName}
                    onChange={(e) => handleChange('firstName', e.target.value)}
                    placeholder="First name"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="last-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Last Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    id="last-name"
                    type="text"
                    required
                    value={form.lastName}
                    onChange={(e) => handleChange('lastName', e.target.value)}
                    placeholder="Last name"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>

              {/* DOB, Gender & Blood Type row */}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="dob" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Date of Birth
                  </label>
                  <input
                    id="dob"
                    type="date"
                    value={form.dateOfBirth}
                    onChange={(e) => handleChange('dateOfBirth', e.target.value)}
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="gender" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Gender
                  </label>
                  <select
                    id="gender"
                    value={form.gender}
                    onChange={(e) => handleChange('gender', e.target.value)}
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent appearance-none transition-all"
                  >
                    <option value="">Select gender</option>
                    <option value="Male">Male</option>
                    <option value="Female">Female</option>
                    <option value="Non-Binary">Non-Binary</option>
                    <option value="Other">Other</option>
                    <option value="Prefer not to say">Prefer not to say</option>
                  </select>
                </div>
                <div>
                  <label htmlFor="blood-type" className="block text-sm font-medium text-slate-700 mb-1.5 flex items-center gap-1">
                    <Heart className="h-3.5 w-3.5 text-rose-500" />
                    Blood Type
                  </label>
                  <select
                    id="blood-type"
                    value={form.bloodType}
                    onChange={(e) => handleChange('bloodType', e.target.value)}
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent appearance-none transition-all"
                  >
                    <option value="">Select blood type</option>
                    <option value="A+">A+</option>
                    <option value="A-">A-</option>
                    <option value="B+">B+</option>
                    <option value="B-">B-</option>
                    <option value="AB+">AB+</option>
                    <option value="AB-">AB-</option>
                    <option value="O+">O+</option>
                    <option value="O-">O-</option>
                  </select>
                </div>
              </div>

              {/* NHS Number */}
              <div>
                <label htmlFor="nhs-number" className="block text-sm font-medium text-slate-700 mb-1.5">
                  NHS Number <span className="text-slate-400 text-xs">(optional)</span>
                </label>
                <input
                  id="nhs-number"
                  type="text"
                  value={form.nhsNumber}
                  onChange={(e) => handleChange('nhsNumber', e.target.value)}
                  placeholder="e.g. 943 476 5919"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                />
              </div>
            </div>

            {/* SECTION 2: Contact & Residence */}
            <div className="space-y-4 pt-4 border-t border-slate-200">
              <h3 className="text-xs font-bold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                <PhoneCall className="h-3.5 w-3.5" /> 2. Contact &amp; Residence
              </h3>

              {/* Contact row */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label htmlFor="patient-email" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Email <span className="text-red-500">*</span> <span className="text-xs text-slate-400 font-normal">(Portal login)</span>
                  </label>
                  <input
                    id="patient-email"
                    type="email"
                    required
                    value={form.email}
                    onChange={(e) => handleChange('email', e.target.value)}
                    placeholder="patient@example.com"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="patient-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Phone Number
                  </label>
                  <input
                    id="patient-phone"
                    type="tel"
                    value={form.phoneNumber}
                    onChange={(e) => handleChange('phoneNumber', e.target.value)}
                    placeholder="+44 7700 900000"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>

              {/* Address */}
              <div>
                <label htmlFor="patient-address" className="block text-sm font-medium text-slate-700 mb-1.5">
                  Residential Address
                </label>
                <textarea
                  id="patient-address"
                  rows={2}
                  value={form.address}
                  onChange={(e) => handleChange('address', e.target.value)}
                  placeholder="Full street address, city, postcode"
                  className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none transition-all"
                />
              </div>

              {/* Assigned GP */}
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1.5">
                  Assigned General Practitioner <span className="text-slate-400 text-xs">(optional)</span>
                </label>
                <GpSelector
                  value={form.gpId}
                  onChange={(gpId) => handleChange('gpId', gpId)}
                />
              </div>
            </div>

            {/* SECTION 3: Emergency Contact */}
            <div className="space-y-4 pt-4 border-t border-slate-200">
              <h3 className="text-xs font-bold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                <PhoneCall className="h-3.5 w-3.5" /> 3. Emergency Contact (Next of Kin)
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="emergency-contact-name" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Contact Name
                  </label>
                  <input
                    id="emergency-contact-name"
                    type="text"
                    value={form.emergencyContactName ?? ''}
                    onChange={(e) => handleChange('emergencyContactName', e.target.value)}
                    placeholder="e.g. Sarah Vance"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="emergency-contact-rel" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Relationship
                  </label>
                  <input
                    id="emergency-contact-rel"
                    type="text"
                    value={form.emergencyContactRelationship ?? ''}
                    onChange={(e) => handleChange('emergencyContactRelationship', e.target.value)}
                    placeholder="e.g. Spouse / Parent / Sibling"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="emergency-contact-phone" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Emergency Phone
                  </label>
                  <input
                    id="emergency-contact-phone"
                    type="tel"
                    value={form.emergencyContactPhone ?? ''}
                    onChange={(e) => handleChange('emergencyContactPhone', e.target.value)}
                    placeholder="+44 7700 900088"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
              </div>
            </div>

            {/* SECTION 4: Health Insurance Coverage */}
            <div className="space-y-4 pt-4 border-t border-slate-200">
              <h3 className="text-xs font-bold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                <Shield className="h-3.5 w-3.5" /> 4. Health Insurance &amp; Billing
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label htmlFor="insurance-provider" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Insurance Provider
                  </label>
                  <input
                    id="insurance-provider"
                    type="text"
                    value={form.insuranceProvider ?? ''}
                    onChange={(e) => handleChange('insuranceProvider', e.target.value)}
                    placeholder="e.g. NHS Standard Care / Bupa"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="insurance-policy" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Policy Number
                  </label>
                  <input
                    id="insurance-policy"
                    type="text"
                    value={form.insurancePolicyNumber ?? ''}
                    onChange={(e) => handleChange('insurancePolicyNumber', e.target.value)}
                    placeholder="e.g. NHS-POL-940010"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono transition-all"
                  />
                </div>
                <div>
                  <label htmlFor="insurance-group" className="block text-sm font-medium text-slate-700 mb-1.5">
                    Group Number
                  </label>
                  <input
                    id="insurance-group"
                    type="text"
                    value={form.insuranceGroupNumber ?? ''}
                    onChange={(e) => handleChange('insuranceGroupNumber', e.target.value)}
                    placeholder="e.g. GRP-UK-8012"
                    className="w-full rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent font-mono transition-all"
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex justify-end gap-3 sticky bottom-0">
            <button
              type="button"
              onClick={onClose}
              className="px-5 py-2.5 rounded-xl text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={mutation.isPending}
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white text-sm font-semibold transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {mutation.isPending ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  {isEdit ? 'Updating...' : 'Creating...'}
                </>
              ) : isEdit ? (
                'Update Patient'
              ) : (
                'Register Patient'
              )}
            </button>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
