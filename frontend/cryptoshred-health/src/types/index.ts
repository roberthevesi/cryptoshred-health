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
  timestamp: string;
  patientRecordId: string;
  requestedBy: string;
  sha256Hash: string;
  status: string;
  auditTrail: string;
}

export interface ApiError {
  message: string;
  status: number;
}
