import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { useQuery } from '@tanstack/react-query';
import {
  X,
  ShieldCheck,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  FileCheck2,
  RefreshCw,
} from 'lucide-react';
import apiClient from '../lib/axios';
import DeletionProofCard from './DeletionProofCard';
import type { DeletionProof, ProofVerificationResponse } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  proof?: DeletionProof | null;
  visitId?: string | null;
  patientId?: string | null;
}

export default function ProofViewerModal({
  isOpen,
  onClose,
  proof: initialProof,
  visitId,
  patientId,
}: Props) {
  const [verificationResult, setVerificationResult] = useState<ProofVerificationResponse | null>(null);
  const [isVerifying, setIsVerifying] = useState(false);
  const [verificationError, setVerificationError] = useState<string | null>(null);

  // 1. Fetch proof if not passed directly
  const shouldFetchVisit = isOpen && !initialProof && !!visitId;
  const shouldFetchPatient = isOpen && !initialProof && !visitId && !!patientId;

  const {
    data: fetchedVisitProof,
    isLoading: isVisitLoading,
    error: visitError,
  } = useQuery<DeletionProof>({
    queryKey: ['deletionProofVisit', visitId],
    queryFn: () => apiClient.get<DeletionProof>(`/erasure/visits/${visitId}/proof`).then((r) => r.data),
    enabled: shouldFetchVisit,
  });

  const {
    data: fetchedPatientProof,
    isLoading: isPatientLoading,
    error: patientError,
  } = useQuery<DeletionProof>({
    queryKey: ['deletionProofPatient', patientId],
    queryFn: () => apiClient.get<DeletionProof>(`/erasure/patients/${patientId}/proof`).then((r) => r.data),
    enabled: shouldFetchPatient,
  });

  const activeProof = initialProof || fetchedVisitProof || fetchedPatientProof || null;
  const isLoading = (shouldFetchVisit && isVisitLoading) || (shouldFetchPatient && isPatientLoading);
  const fetchError = (visitError || patientError) ? 'Failed to retrieve deletion proof certificate from server.' : null;

  // 2. Perform verification whenever activeProof changes
  const runVerification = async (proofToVerify: DeletionProof) => {
    setIsVerifying(true);
    setVerificationError(null);
    try {
      const response = await apiClient.post<ProofVerificationResponse>('/erasure/verify-proof', {
        proofArtifact: proofToVerify,
      });
      setVerificationResult(response.data);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (err instanceof Error ? err.message : 'Verification request failed');
      setVerificationError(msg);
      setVerificationResult(null);
    } finally {
      setIsVerifying(false);
    }
  };

  useEffect(() => {
    if (activeProof) {
      runVerification(activeProof);
    } else {
      setVerificationResult(null);
    }
  }, [activeProof?.auditTrailHash, activeProof?.digitalSignature]);

  if (!isOpen) return null;

  return createPortal(
    <div
      className="fixed inset-0 z-[100] !m-0 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-fade-in overflow-y-auto"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="relative w-full max-w-4xl rounded-2xl bg-white shadow-2xl my-auto max-h-[96vh] overflow-y-auto">
        {/* Loading State */}
        {isLoading && (
          <div className="py-16 text-center space-y-3 p-6">
            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-3 border-emerald-600 border-t-transparent" />
            <p className="text-xs font-medium text-slate-600">Retrieving signed deletion proof artifact...</p>
          </div>
        )}

        {/* Error State */}
        {fetchError && !isLoading && (
          <div className="p-6">
            <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center justify-between">
              <div className="flex items-center gap-2">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                <span>{fetchError}</span>
              </div>
              <button onClick={onClose} className="p-1 rounded text-red-500 hover:text-red-700 cursor-pointer">
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>
        )}

        {/* Verification Error Alert */}
        {verificationError && !isLoading && (
          <div className="px-5 pt-3">
            <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 shrink-0" />
              <span>{verificationError}</span>
            </div>
          </div>
        )}

        {/* Unified Proof Card Render */}
        {activeProof && !isLoading && (
          <DeletionProofCard
            proof={activeProof}
            onVerify={() => runVerification(activeProof)}
            isVerifying={isVerifying}
            verificationResult={verificationResult}
            onClose={onClose}
          />
        )}
      </div>
    </div>,
    document.body
  );
}
