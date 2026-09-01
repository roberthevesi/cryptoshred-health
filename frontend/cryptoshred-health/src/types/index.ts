export type Role = 'PATIENT' | 'DOCTOR' | 'AUDITOR' | 'ADMIN';

export interface AuthUser {
  email: string;
  role: Role;
  token: string;
}

export interface AuthResponse {
  token: string;
  email: string;
  role: Role;
}

export interface PatientAttachment {
  id: string;
  fileName: string;
  contentType: string;
  fileSize: number;
  shredded: boolean;
  createdAt: string;
}

export interface PatientVisit {
  id: string;
  patientId?: string;
  patientName: string;
  mrn?: string;
  bloodPressure?: string;
  heartRate?: number;
  oxygenSaturation?: string;
  temperature?: string;
  respiratoryRate?: string;
  heightCm?: string;
  weightKg?: string;
  bmi?: string;
  painScore?: number;
  allergies?: string | null;
  prescriptions?: string | null;
  chiefComplaint?: string;
  chronicConditions?: string;
  immunizationStatus?: string;
  lifestyleFactors?: string;
  followUpDate?: string;
  diagnosis: string | null;
  medicalNotes: string | null;
  soapSubjective?: string;
  soapObjective?: string;
  soapAssessment?: string;
  soapPlan?: string;
  encryptedDataBlob: string | null;
  attendingDoctor?: string;
  department?: string;
  shredded: boolean;
  ownerEmail: string;
  attachments?: PatientAttachment[];
  createdAt: string;
  updatedAt: string;
}

export type PatientRecord = PatientVisit;

export interface PatientVisitRequest {
  patientId?: string;
  patientName?: string;
  mrn?: string;
  attendingDoctor?: string;
  department?: string;
  bloodPressure?: string;
  heartRate?: number;
  respiratoryRate?: string;
  temperature?: string;
  oxygenSaturation?: string;
  heightCm?: string;
  weightKg?: string;
  bmi?: string;
  painScore?: number;
  allergies?: string;
  prescriptions?: string;
  chiefComplaint?: string;
  chronicConditions?: string;
  immunizationStatus?: string;
  lifestyleFactors?: string;
  followUpDate?: string;
  diagnosis: string;
  medicalNotes: string;
  soapSubjective?: string;
  soapObjective?: string;
  soapAssessment?: string;
  soapPlan?: string;
  encryptedDataBlob?: string;
  createdAt?: string;
  visitDate?: string;
}

export type PatientRecordRequest = PatientVisitRequest;

export interface DeletionProof {
  proofVersion?: string;
  scope?: 'PATIENT_PROFILE' | 'CLINICAL_VISIT';
  entityDescription?: string;
  timestamp: string;
  visitId?: string;
  patientRecordId?: string;
  patientId?: string;
  vaultKeyName?: string;
  requestedBy: string;
  sha256Hash?: string;
  auditTrailHash?: string;
  status: string;
  auditTrail: string;
  coveredStorageLayers?: string[];
  layerStatus?: Record<string, string>;
  merkleRoot?: string;
  merklePath?: string[];
  signatureAlgorithm?: string;
  digitalSignature?: string;
  overrideReason?: string;
  retentionStatus?: string;
  // Dual Hybrid Cryptography Fields
  classicalSignatureAlgorithm?: string;
  classicalDigitalSignature?: string;
  pqcSignatureAlgorithm?: string;
  pqcDigitalSignature?: string;
  pqcSecurityGuarantee?: string;
  pqcLatticeSecurityLevel?: string;
  pqcAlgorithm?: string;
  pqcSignatureSnippet?: string;
}

export interface ErasureProofBundle {
  patientId: string;
  masterPatientProof?: DeletionProof | null;
  visitProofs: DeletionProof[];
  totalShreddedVisits: number;
}

export interface ProofVerificationResponse {
  valid: boolean;
  signatureValid: boolean;
  payloadIntegrityValid: boolean;
  merkleInclusionValid: boolean;
  verificationMessage: string;
  verifiedAt: string;
  verifiedByAlgorithm: string;
  // Dual Cryptographic Verification Status
  classicalSignatureValid?: boolean;
  classicalAlgorithm?: string;
  classicalSignatureSnippet?: string;
  pqcSignatureValid?: boolean;
  pqcAlgorithm?: string;
  pqcSignatureSnippet?: string;
  pqcSecurityGuarantee?: string;
  pqcLatticeSecurityLevel?: string;
}

export interface ApiError {
  message: string;
  status: number;
}

export interface GP {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  gmcNumber: string;
  specialisation: string;
  practiceName: string;
  isActive?: boolean;
  active?: boolean;
  createdAt: string;
}

export interface Patient {
  id: string;
  patientId: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  gender: string;
  email: string;
  phoneNumber: string;
  address: string;
  nhsNumber?: string;
  bloodType?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContactRelationship?: string;
  insuranceProvider?: string;
  insurancePolicyNumber?: string;
  insuranceGroupNumber?: string;
  gp?: GP;
  isActive?: boolean;
  active?: boolean;
  shredded?: boolean;
  temporaryPassword?: string;
  // Retention Horizon & Legal Erasure
  latestActivityDate?: string;
  retentionPeriodYears?: number;
  legalErasureEligibleDate?: string;
  retentionStatus?: 'ELIGIBLE' | 'PROTECTED' | 'SHREDDED';
  retentionDaysRemaining?: number;
  createdAt: string;
  updatedAt: string;
}

export interface RetentionPolicy {
  retentionPeriodYears: number;
  regulatoryStandard: string;
  description: string;
  lastUpdated: string;
  updatedBy: string;
}

export interface PatientRequest {
  patientId: string;
  firstName: string;
  lastName: string;
  dateOfBirth?: string;
  gender?: string;
  email?: string;
  phoneNumber?: string;
  address?: string;
  nhsNumber?: string;
  bloodType?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emergencyContactRelationship?: string;
  insuranceProvider?: string;
  insurancePolicyNumber?: string;
  insuranceGroupNumber?: string;
  gpId?: string;
}

export interface GpRequest {
  firstName: string;
  lastName: string;
  email?: string;
  phoneNumber?: string;
  gmcNumber: string;
  specialisation?: string;
  practiceName?: string;
}

export interface AdminUser {
  id: string;
  email: string;
  role: Role;
  temporaryPassword?: string;
  createdAt?: string;
}

export interface AdminUserRequest {
  email: string;
  role: Role;
  password?: string;
}

