import { useState, type FormEvent, type ChangeEvent } from 'react';
import { createPortal } from 'react-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  X,
  UserPlus,
  Upload,
  Activity,
  User,
  Heart,
  Pill,
  FileText,
  ShieldCheck,
  Building,
  CheckCircle2,
  Stethoscope,
} from 'lucide-react';
import apiClient from '../lib/axios';
import GpSelector from './GpSelector';
import type { PatientRecord, PatientRecordRequest, Patient } from '../types';

interface Props {
  onClose: () => void;
  editRecord?: PatientRecord | null;
  defaultPatient?: Patient | null;
}

type ModalTab = 'demographics' | 'vitals' | 'clinical' | 'soap' | 'attachments';

export default function CreateRecordModal({ onClose, editRecord, defaultPatient }: Props) {
  const queryClient = useQueryClient();
  const isEditing = !!editRecord && !!editRecord.id && editRecord.id.trim() !== '';

  // Fetch registered patients for quick intake auto-fill
  const { data: registeredPatients = [] } = useQuery<Patient[]>({
    queryKey: ['patients'],
    queryFn: () => apiClient.get<Patient[]>('/patients').then((r) => r.data),
    enabled: !isEditing && !defaultPatient,
  });

  const [activeTab, setActiveTab] = useState<ModalTab>('demographics');

  const [form, setForm] = useState<PatientRecordRequest>({
    patientName: editRecord?.patientName ?? (defaultPatient ? `${defaultPatient.firstName} ${defaultPatient.lastName}` : ''),
    mrn: editRecord?.mrn ?? defaultPatient?.patientId ?? '',
    dateOfBirth: editRecord?.dateOfBirth ?? defaultPatient?.dateOfBirth ?? '',
    gender: editRecord?.gender ?? defaultPatient?.gender ?? 'Female',
    bloodType: editRecord?.bloodType ?? 'O+',

    // Contact & Admin
    phone: editRecord?.phone ?? defaultPatient?.phoneNumber ?? '',
    email: editRecord?.email ?? defaultPatient?.email ?? '',
    address: editRecord?.address ?? defaultPatient?.address ?? '',
    emergencyContactName: editRecord?.emergencyContactName ?? '',
    emergencyContactPhone: editRecord?.emergencyContactPhone ?? '',
    emergencyContactRelationship: editRecord?.emergencyContactRelationship ?? '',

    // Provider & Insurance
    attendingDoctor:
      editRecord?.attendingDoctor ??
      (defaultPatient?.gp ? `Dr. ${defaultPatient.gp.firstName} ${defaultPatient.gp.lastName}` : 'Dr. Alistair Finch, MD'),
    department:
      editRecord?.department ??
      (defaultPatient?.gp?.practiceName || 'General Practice'),
    insuranceProvider: editRecord?.insuranceProvider ?? '',
    insurancePolicyNumber: editRecord?.insurancePolicyNumber ?? '',
    insuranceGroupNumber: editRecord?.insuranceGroupNumber ?? '',

    // Biometrics & Vitals
    bloodPressure: editRecord?.bloodPressure ?? '120/80 mmHg',
    heartRate: editRecord?.heartRate ?? 72,
    respiratoryRate: editRecord?.respiratoryRate ?? '16 breaths/min',
    temperature: editRecord?.temperature ?? '36.8 °C',
    oxygenSaturation: editRecord?.oxygenSaturation ?? '98%',
    heightCm: editRecord?.heightCm ?? '170 cm',
    weightKg: editRecord?.weightKg ?? '70.0 kg',
    bmi: editRecord?.bmi ?? '24.2',
    painScore: editRecord?.painScore ?? 0,

    // Clinical Profile
    allergies: editRecord?.allergies ?? '',
    prescriptions: editRecord?.prescriptions ?? '',
    chiefComplaint: editRecord?.chiefComplaint ?? '',
    chronicConditions: editRecord?.chronicConditions ?? '',
    immunizationStatus: editRecord?.immunizationStatus ?? '',
    lifestyleFactors: editRecord?.lifestyleFactors ?? '',
    followUpDate: editRecord?.followUpDate ?? '',

    // Encounter & SOAP Notes
    diagnosis: editRecord?.diagnosis ?? '',
    medicalNotes: editRecord?.medicalNotes ?? '',
    soapSubjective: editRecord?.soapSubjective ?? '',
    soapObjective: editRecord?.soapObjective ?? '',
    soapAssessment: editRecord?.soapAssessment ?? '',
    soapPlan: editRecord?.soapPlan ?? '',
  });

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState('');

  // Auto-calculate BMI when height or weight change
  const autoCalculateBmi = (heightStr: string, weightStr: string) => {
    const h = parseFloat(heightStr.replace(/[^0-9.]/g, ''));
    const w = parseFloat(weightStr.replace(/[^0-9.]/g, ''));
    if (h > 0 && w > 0) {
      const heightM = h / 100;
      const bmiVal = (w / (heightM * heightM)).toFixed(1);
      return bmiVal;
    }
    return form.bmi;
  };

  const mutation = useMutation({
    mutationFn: async (data: PatientRecordRequest) => {
      const res = isEditing
        ? await apiClient.put<PatientRecord>(`/records/${editRecord!.id}`, data)
        : await apiClient.post<PatientRecord>('/records', data);

      const savedRecord = res.data;

      // Upload file if attached
      if (selectedFile) {
        const formData = new FormData();
        formData.append('file', selectedFile);
        await apiClient.post(`/records/${savedRecord.id}/attachments`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        });
      }

      return savedRecord;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['records'] });
      onClose();
    },
    onError: (err: unknown) => {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Failed to save clinical record.';
      setError(msg);
    },
  });

  const handleChange =
    (field: keyof PatientRecordRequest) =>
    (e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
      const val =
        field === 'heartRate' || field === 'painScore'
          ? parseInt(e.target.value) || 0
          : e.target.value;

      setForm((prev) => {
        const updated = { ...prev, [field]: val };
        if (field === 'heightCm' || field === 'weightKg') {
          const newBmi = autoCalculateBmi(
            field === 'heightCm' ? e.target.value : prev.heightCm ?? '',
            field === 'weightKg' ? e.target.value : prev.weightKg ?? ''
          );
          updated.bmi = newBmi;
        }
        return updated;
      });
    };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setSelectedFile(e.target.files[0]);
    }
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    setError('');
    mutation.mutate(form);
  };

  return createPortal(
    <div
      id="create-record-modal-overlay"
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4 overflow-y-auto"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="w-full max-w-4xl rounded-2xl border border-slate-200 bg-white shadow-2xl my-auto max-h-[92vh] flex flex-col overflow-hidden">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50/80">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 ring-1 ring-blue-200">
              <UserPlus className="h-5 w-5 text-blue-600" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {isEditing ? `Edit Clinical Chart: ${editRecord?.patientName}` : 'New Clinical EHR Encounter Chart'}
              </h2>
              <p className="text-xs text-slate-500">AES-256-GCM field-level envelope encryption with Vault KMS</p>
            </div>
          </div>
          <button onClick={onClose} id="modal-close" className="p-2 text-slate-400 hover:text-slate-700 hover:bg-slate-100 rounded-xl transition">
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex border-b border-slate-200 bg-slate-50 px-6 gap-2 overflow-x-auto text-xs font-medium py-2">
          <button
            type="button"
            onClick={() => setActiveTab('demographics')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
              activeTab === 'demographics'
                ? 'bg-blue-600 text-white font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            <User className="h-3.5 w-3.5" /> 1. Demographics &amp; Admin
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('vitals')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
              activeTab === 'vitals'
                ? 'bg-blue-600 text-white font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            <Activity className="h-3.5 w-3.5" /> 2. Vitals &amp; Biometrics
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('clinical')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
              activeTab === 'clinical'
                ? 'bg-blue-600 text-white font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            <Pill className="h-3.5 w-3.5" /> 3. Meds &amp; Allergies
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('soap')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
              activeTab === 'soap'
                ? 'bg-blue-600 text-white font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            <FileText className="h-3.5 w-3.5" /> 4. SOAP Encounter
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('attachments')}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-xl transition ${
              activeTab === 'attachments'
                ? 'bg-blue-600 text-white font-semibold shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
            }`}
          >
            <Upload className="h-3.5 w-3.5" /> 5. Documents
          </button>
        </div>

        {error && (
          <div className="mx-6 mt-4 rounded-xl bg-red-50 border border-red-200 px-4 py-3 text-sm text-red-700">
            {error}
          </div>
        )}

        {/* Modal Form Body */}
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-6 bg-white">
          {/* TAB 1: Demographics & Admin */}
          {activeTab === 'demographics' && (
            <div className="space-y-4 animate-fade-in">
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-4">
                <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <User className="h-3.5 w-3.5" /> Patient Identification &amp; Contact
                </h3>

                {!isEditing && registeredPatients.length > 0 && (
                  <div className="bg-white border border-blue-200 rounded-xl p-3 shadow-sm">
                    <label className="text-xs font-semibold text-blue-900 block mb-1">
                      Choose Registered Patient (Auto-fills Demographics &amp; Assigned GP)
                    </label>
                    <select
                      className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs text-slate-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                      onChange={(e) => {
                        const sel = registeredPatients.find((p) => p.patientId === e.target.value);
                        if (sel) {
                          setForm((prev) => ({
                            ...prev,
                            patientName: `${sel.firstName} ${sel.lastName}`,
                            mrn: sel.patientId,
                            dateOfBirth: sel.dateOfBirth ?? prev.dateOfBirth,
                            gender: sel.gender ?? prev.gender,
                            phone: sel.phoneNumber ?? prev.phone,
                            email: sel.email ?? prev.email,
                            address: sel.address ?? prev.address,
                            attendingDoctor: sel.gp ? `Dr. ${sel.gp.firstName} ${sel.gp.lastName}` : prev.attendingDoctor,
                            department: sel.gp?.practiceName || prev.department || 'General Practice',
                          }));
                        }
                      }}
                      defaultValue=""
                    >
                      <option value="">— Select an existing patient from registry —</option>
                      {registeredPatients.map((p) => (
                        <option key={p.id} value={p.patientId}>
                          {p.firstName} {p.lastName} (ID: {p.patientId}{p.nhsNumber ? ` • NHS: ${p.nhsNumber}` : ''}{p.gp ? ` • GP: Dr. ${p.gp.lastName}` : ''})
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div className="sm:col-span-2">
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      Full Legal Name <span className="text-rose-500">*</span>
                    </label>
                    <input
                      id="input-patient-name"
                      type="text"
                      required
                      value={form.patientName}
                      onChange={handleChange('patientName')}
                      placeholder="e.g. Eleanor Vance"
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">MRN (Medical Record #)</label>
                    <input
                      type="text"
                      value={form.mrn ?? ''}
                      onChange={handleChange('mrn')}
                      placeholder="Auto-generated if blank"
                      className="input-field font-mono"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Date of Birth</label>
                    <input
                      type="date"
                      value={form.dateOfBirth ?? ''}
                      onChange={handleChange('dateOfBirth')}
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Biological Sex / Gender</label>
                    <select value={form.gender ?? 'Female'} onChange={handleChange('gender')} className="input-field">
                      <option value="Female">Female</option>
                      <option value="Male">Male</option>
                      <option value="Non-Binary">Non-Binary</option>
                      <option value="Other">Other / Undisclosed</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Blood Type</label>
                    <select value={form.bloodType ?? 'O+'} onChange={handleChange('bloodType')} className="input-field">
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

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Phone Number</label>
                    <input
                      type="tel"
                      value={form.phone ?? ''}
                      onChange={handleChange('phone')}
                      placeholder="+1 (555) 000-0000"
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Email Address</label>
                    <input
                      type="email"
                      value={form.email ?? ''}
                      onChange={handleChange('email')}
                      placeholder="patient@example.com"
                      className="input-field"
                    />
                  </div>
                </div>

                <div>
                  <label className="text-xs font-medium text-slate-700 block mb-1">Residential Address</label>
                  <input
                    type="text"
                    value={form.address ?? ''}
                    onChange={handleChange('address')}
                    placeholder="Street Address, City, State, ZIP"
                    className="input-field"
                  />
                </div>
              </div>

              {/* Emergency Contact & Insurance */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-3">
                  <h3 className="text-xs font-semibold text-rose-600 uppercase tracking-wider flex items-center gap-1.5">
                    <Heart className="h-3.5 w-3.5" /> Emergency Contact
                  </h3>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Contact Name</label>
                    <input
                      type="text"
                      value={form.emergencyContactName ?? ''}
                      onChange={handleChange('emergencyContactName')}
                      placeholder="Next of Kin Name"
                      className="input-field"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Relationship</label>
                      <input
                        type="text"
                        value={form.emergencyContactRelationship ?? ''}
                        onChange={handleChange('emergencyContactRelationship')}
                        placeholder="Spouse / Parent"
                        className="input-field"
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Emergency Phone</label>
                      <input
                        type="tel"
                        value={form.emergencyContactPhone ?? ''}
                        onChange={handleChange('emergencyContactPhone')}
                        placeholder="+1 (555) 000-0000"
                        className="input-field"
                      />
                    </div>
                  </div>
                </div>

                <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-3">
                  <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                    <Building className="h-3.5 w-3.5" /> Insurance &amp; Attending Care
                  </h3>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      Attending GP / Physician (Search Directory)
                    </label>
                    <GpSelector
                      placeholder="Search GP by name, GMC number, practice..."
                      compact
                      onChange={(_gpId, gp) => {
                        if (gp) {
                          setForm((prev) => ({
                            ...prev,
                            attendingDoctor: `Dr. ${gp.firstName} ${gp.lastName}${gp.specialisation ? `, ${gp.specialisation}` : ''}`,
                            department: gp.practiceName || prev.department || 'General Practice',
                          }));
                        }
                      }}
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Physician Name / Title</label>
                      <input
                        type="text"
                        value={form.attendingDoctor ?? ''}
                        onChange={handleChange('attendingDoctor')}
                        placeholder="Dr. Alistair Finch, MD"
                        className="input-field"
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Department / Clinic</label>
                      <input
                        type="text"
                        value={form.department ?? ''}
                        onChange={handleChange('department')}
                        placeholder="General Practice"
                        className="input-field"
                      />
                    </div>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Insurance Provider</label>
                    <input
                      type="text"
                      value={form.insuranceProvider ?? ''}
                      onChange={handleChange('insuranceProvider')}
                      placeholder="e.g. Blue Cross Blue Shield"
                      className="input-field"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-2">
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Policy / Member ID</label>
                      <input
                        type="text"
                        value={form.insurancePolicyNumber ?? ''}
                        onChange={handleChange('insurancePolicyNumber')}
                        placeholder="BCBS-123456"
                        className="input-field font-mono"
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-slate-700 block mb-1">Group Number</label>
                      <input
                        type="text"
                        value={form.insuranceGroupNumber ?? ''}
                        onChange={handleChange('insuranceGroupNumber')}
                        placeholder="GRP-9901"
                        className="input-field font-mono"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: Vitals & Biometrics */}
          {activeTab === 'vitals' && (
            <div className="space-y-4 animate-fade-in">
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-4">
                <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Activity className="h-3.5 w-3.5" /> Clinical Biometrics &amp; Vital Signs
                </h3>

                <div className="grid grid-cols-2 sm:grid-cols-3 gap-4">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Blood Pressure (mmHg)</label>
                    <input
                      type="text"
                      value={form.bloodPressure ?? ''}
                      onChange={handleChange('bloodPressure')}
                      placeholder="120/80 mmHg"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Pulse / Heart Rate (bpm)</label>
                    <input
                      type="number"
                      min={30}
                      max={240}
                      value={form.heartRate ?? 72}
                      onChange={handleChange('heartRate')}
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Oxygen Saturation (SpO₂)</label>
                    <input
                      type="text"
                      value={form.oxygenSaturation ?? ''}
                      onChange={handleChange('oxygenSaturation')}
                      placeholder="98%"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Body Temperature</label>
                    <input
                      type="text"
                      value={form.temperature ?? ''}
                      onChange={handleChange('temperature')}
                      placeholder="36.8 °C"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Respiratory Rate</label>
                    <input
                      type="text"
                      value={form.respiratoryRate ?? ''}
                      onChange={handleChange('respiratoryRate')}
                      placeholder="16 breaths/min"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Pain Score (0 - 10)</label>
                    <input
                      type="number"
                      min={0}
                      max={10}
                      value={form.painScore ?? 0}
                      onChange={handleChange('painScore')}
                      className="input-field font-mono"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 border-t border-slate-200 pt-4">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Height (cm)</label>
                    <input
                      type="text"
                      value={form.heightCm ?? ''}
                      onChange={handleChange('heightCm')}
                      placeholder="175 cm"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Weight (kg)</label>
                    <input
                      type="text"
                      value={form.weightKg ?? ''}
                      onChange={handleChange('weightKg')}
                      placeholder="72.5 kg"
                      className="input-field font-mono"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Body Mass Index (BMI)</label>
                    <input
                      type="text"
                      value={form.bmi ?? ''}
                      onChange={handleChange('bmi')}
                      placeholder="Auto-calculated"
                      className="input-field font-mono text-emerald-700 font-bold"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: Meds & Allergies */}
          {activeTab === 'clinical' && (
            <div className="space-y-4 animate-fade-in">
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-4">
                <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Pill className="h-3.5 w-3.5" /> Allergies, Medications &amp; Clinical History
                </h3>

                <div>
                  <label className="text-xs font-medium text-slate-700 block mb-1">
                    Known Allergies &amp; Reactions (Enter severity, e.g. Penicillin - Severe Anaphylaxis)
                  </label>
                  <input
                    type="text"
                    value={form.allergies ?? ''}
                    onChange={handleChange('allergies')}
                    placeholder="e.g. Penicillin (Severe), Sulfa Drugs (Rash), Latex"
                    className="input-field"
                  />
                </div>

                <div>
                  <label className="text-xs font-medium text-slate-700 block mb-1">
                    Active Prescriptions &amp; Dosages
                  </label>
                  <textarea
                    rows={3}
                    value={form.prescriptions ?? ''}
                    onChange={handleChange('prescriptions')}
                    placeholder="e.g. Metformin 500mg PO BID with meals; Lisinopril 10mg PO Daily"
                    className="input-field"
                  />
                </div>

                <div>
                  <label className="text-xs font-medium text-slate-700 block mb-1">
                    Chronic Medical Conditions
                  </label>
                  <input
                    type="text"
                    value={form.chronicConditions ?? ''}
                    onChange={handleChange('chronicConditions')}
                    placeholder="e.g. Type 2 Diabetes Mellitus, Essential Hypertension, Asthma"
                    className="input-field"
                  />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Immunization Status</label>
                    <input
                      type="text"
                      value={form.immunizationStatus ?? ''}
                      onChange={handleChange('immunizationStatus')}
                      placeholder="COVID-19, Influenza 2025/2026, Tdap"
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Lifestyle &amp; Social History</label>
                    <input
                      type="text"
                      value={form.lifestyleFactors ?? ''}
                      onChange={handleChange('lifestyleFactors')}
                      placeholder="Non-smoker, Social alcohol, Active"
                      className="input-field"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: SOAP Encounter */}
          {activeTab === 'soap' && (
            <div className="space-y-4 animate-fade-in">
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-4 space-y-4">
                <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <FileText className="h-3.5 w-3.5" /> Structured SOAP Encounter Note &amp; Diagnosis
                </h3>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Chief Complaint / Reason for Visit</label>
                    <input
                      type="text"
                      value={form.chiefComplaint ?? ''}
                      onChange={handleChange('chiefComplaint')}
                      placeholder="e.g. Routine 6-month diabetic checkup and BP review"
                      className="input-field"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Primary Diagnosis / ICD-10</label>
                    <input
                      type="text"
                      value={form.diagnosis ?? ''}
                      onChange={handleChange('diagnosis')}
                      placeholder="e.g. E11.9 - Type 2 Diabetes Mellitus"
                      className="input-field"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      <span className="font-bold text-blue-600">S</span> - Subjective History
                    </label>
                    <textarea
                      rows={3}
                      value={form.soapSubjective ?? ''}
                      onChange={handleChange('soapSubjective')}
                      placeholder="Patient's symptoms, history of present illness, adherence..."
                      className="input-field text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      <span className="font-bold text-blue-600">O</span> - Objective Examination
                    </label>
                    <textarea
                      rows={3}
                      value={form.soapObjective ?? ''}
                      onChange={handleChange('soapObjective')}
                      placeholder="Physical exam findings, lab results, telemetry..."
                      className="input-field text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      <span className="font-bold text-blue-600">A</span> - Clinical Assessment
                    </label>
                    <textarea
                      rows={3}
                      value={form.soapAssessment ?? ''}
                      onChange={handleChange('soapAssessment')}
                      placeholder="Clinical judgment, differential diagnoses, progression..."
                      className="input-field text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">
                      <span className="font-bold text-blue-600">P</span> - Treatment &amp; Management Plan
                    </label>
                    <textarea
                      rows={3}
                      value={form.soapPlan ?? ''}
                      onChange={handleChange('soapPlan')}
                      placeholder="Prescriptions, diagnostics ordered, consultations, follow-up..."
                      className="input-field text-xs"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 border-t border-slate-200 pt-4">
                  <div className="sm:col-span-2">
                    <label className="text-xs font-medium text-slate-700 block mb-1">General Medical Notes (AES-256 Encrypted)</label>
                    <textarea
                      id="input-medical-notes"
                      rows={2}
                      value={form.medicalNotes ?? ''}
                      onChange={handleChange('medicalNotes')}
                      placeholder="Confidential clinical annotations..."
                      className="input-field text-xs"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-slate-700 block mb-1">Scheduled Follow-up Date</label>
                    <input
                      type="date"
                      value={form.followUpDate ?? ''}
                      onChange={handleChange('followUpDate')}
                      className="input-field"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 5: Attachments */}
          {activeTab === 'attachments' && (
            <div className="space-y-4 animate-fade-in">
              <div className="rounded-xl bg-slate-50 border border-slate-200 p-5 space-y-4">
                <h3 className="text-xs font-semibold text-blue-700 uppercase tracking-wider flex items-center gap-1.5">
                  <Upload className="h-3.5 w-3.5" /> Diagnostic Document Upload (Encrypted PDF / DICOM)
                </h3>

                <div className="border-2 border-dashed border-slate-300 hover:border-blue-500 rounded-2xl p-6 text-center transition bg-slate-50">
                  <Upload className="mx-auto h-8 w-8 text-slate-400 mb-2" />
                  <p className="text-sm font-medium text-slate-700">
                    {selectedFile ? selectedFile.name : 'Select or drop diagnostic medical report (PDF)'}
                  </p>
                  <p className="text-xs text-slate-500 mt-1">
                    {selectedFile
                      ? `${(selectedFile.size / 1024).toFixed(1)} KB — Will be encrypted with AES-256-GCM via Vault Transit KEK`
                      : 'File will be encrypted in-memory before storage'}
                  </p>
                  <label className="mt-4 inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white hover:bg-slate-100 text-slate-700 text-xs font-medium cursor-pointer transition border border-slate-300 shadow-sm">
                    Browse File
                    <input
                      type="file"
                      accept=".pdf,application/pdf"
                      onChange={handleFileChange}
                      className="hidden"
                    />
                  </label>
                </div>
              </div>
            </div>
          )}

          {/* Footer Action Bar */}
          <div className="flex items-center justify-between pt-4 border-t border-slate-200">
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <ShieldCheck className="h-4 w-4 text-emerald-600" />
              <span>Protected by Vault KMS Zero-Purge Architecture</span>
            </div>

            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-xl border border-slate-300 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
              >
                Cancel
              </button>
              <button
                type="submit"
                id="btn-save-record"
                disabled={mutation.isPending}
                className="flex items-center gap-2 px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold shadow-sm transition disabled:opacity-50"
              >
                {mutation.isPending ? (
                  <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <CheckCircle2 className="h-4 w-4" />
                )}
                {isEditing ? 'Update Clinical Record' : 'Save & Encrypt Record'}
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>,
    document.body
  );
}
