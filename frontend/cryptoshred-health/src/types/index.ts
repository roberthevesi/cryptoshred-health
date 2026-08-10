export type Role = 'PATIENT' | 'DOCTOR' | 'AUDITOR';

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

export interface PatientRecord {
  id: string;
  patientName: string;
  mrn?: string;
  dateOfBirth?: string;
  gender?: string;
  bloodType?: string;
  bloodPressure?: string;
  heartRate?: number;
  allergies?: string | null;
  prescriptions?: string | null;
  diagnosis: string | null;
  medicalNotes: string | null;
  encryptedDataBlob: string | null;
  shredded: boolean;
  ownerEmail: string;
  attachments?: PatientAttachment[];
  createdAt: string;
  updatedAt: string;
}

export interface PatientRecordRequest {
  patientName: string;
  mrn?: string;
  dateOfBirth?: string;
  gender?: string;
  bloodType?: string;
  bloodPressure?: string;
  heartRate?: number;
  allergies?: string;
  prescriptions?: string;
  diagnosis: string;
  medicalNotes: string;
  encryptedDataBlob?: string;
}

export interface DeletionProof {
  proofVersion?: string;
  timestamp: string;
  patientRecordId: string;
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
}

export interface ProofVerificationResponse {
  valid: boolean;
  signatureValid: boolean;
  payloadIntegrityValid: boolean;
  merkleInclusionValid: boolean;
  verificationMessage: string;
  verifiedAt: string;
  verifiedByAlgorithm: string;
}

export interface ApiError {
  message: string;
  status: number;
}
