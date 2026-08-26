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
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-xs p-4 animate-fade-in overflow-y-auto">
      <div className="relative w-full max-w-4xl rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl space-y-5 my-8 max-h-[90vh] overflow-y-auto">
        {/* Modal Top Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100 border border-emerald-200 text-emerald-700 shadow-2xs">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">Cryptographic Deletion Proof Inspector</h2>
              <p className="text-xs text-slate-500">
                Audited proof artifact with live RSA signature verification &amp; Merkle inclusion validation
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="py-16 text-center space-y-3">
            <div className="mx-auto h-8 w-8 animate-spin rounded-full border-3 border-emerald-600 border-t-transparent" />
            <p className="text-xs font-medium text-slate-600">Retrieving signed deletion proof artifact...</p>
          </div>
        )}

        {/* Error State */}
        {fetchError && !isLoading && (
          <div className="p-4 rounded-xl bg-red-50 border border-red-200 text-red-700 text-xs flex items-center gap-2">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>{fetchError}</span>
          </div>
        )}

        {/* Active Proof Card & Live Verification */}
        {activeProof && !isLoading && (
          <div className="space-y-5">
            {/* Live Verification Status Bar */}
            <div className="rounded-xl border p-4 bg-slate-50/80 border-slate-200 space-y-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <FileCheck2 className="h-4 w-4 text-emerald-600" />
                  <span className="text-xs font-bold text-slate-900 uppercase tracking-wider">
                    Live System Authority Cryptographic Verification
                  </span>
                </div>
                <button
                  onClick={() => runVerification(activeProof)}
                  disabled={isVerifying}
                  className="inline-flex items-center gap-1 text-xs text-emerald-700 hover:text-emerald-800 font-semibold cursor-pointer"
                >
                  <RefreshCw className={`h-3.5 w-3.5 ${isVerifying ? 'animate-spin' : ''}`} />
                  Re-verify Signature
                </button>
              </div>

              {isVerifying && (
                <div className="flex items-center gap-2 text-xs text-slate-500 py-1">
                  <div className="h-3 w-3 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" />
                  <span>Validating RSA-2048 signature against system public key...</span>
                </div>
              )}

              {verificationError && (
                <div className="p-3 rounded-lg bg-red-50 border border-red-200 text-xs text-red-700 flex items-center gap-2">
                  <AlertTriangle className="h-4 w-4 shrink-0" />
                  <span>{verificationError}</span>
                </div>
              )}

              {verificationResult && !isVerifying && (
                <div className={`p-3 rounded-xl border text-xs space-y-2 ${
                  verificationResult.valid
                    ? 'bg-emerald-50/80 border-emerald-200 text-emerald-950'
                    : 'bg-red-50 border-red-200 text-red-900'
                }`}>
                  <div className="flex items-center justify-between">
                    <span className="font-bold flex items-center gap-1.5">
                      {verificationResult.valid ? (
                        <>
                          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                          Cryptographically Valid &amp; Authentic
                        </>
                      ) : (
                        <>
                          <XCircle className="h-4 w-4 text-red-600" />
                          Verification Failed
                        </>
                      )}
                    </span>
                    <span className="font-mono text-[10px] text-slate-500">
                      Verified at {new Date(verificationResult.verifiedAt).toLocaleTimeString()}
                    </span>
                  </div>

                  <p className="text-[11px] leading-relaxed text-slate-700">
                    {verificationResult.verificationMessage}
                  </p>

                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 pt-2 border-t border-emerald-200/60 font-medium text-[11px]">
                    <div className="flex items-center gap-1.5">
                      {verificationResult.signatureValid ? (
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                      ) : (
                        <XCircle className="h-3.5 w-3.5 text-red-600" />
                      )}
                      <span>RSA Digital Signature Valid</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      {verificationResult.payloadIntegrityValid ? (
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                      ) : (
                        <XCircle className="h-3.5 w-3.5 text-red-600" />
                      )}
                      <span>SHA-256 Audit Integrity OK</span>
                    </div>

                    <div className="flex items-center gap-1.5">
                      {verificationResult.merkleInclusionValid ? (
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                      ) : (
                        <XCircle className="h-3.5 w-3.5 text-red-600" />
                      )}
                      <span>Merkle Inclusion Verified</span>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Proof Card Render */}
            <DeletionProofCard proof={activeProof} />
          </div>
        )}

        {/* Modal Footer */}
        <div className="flex justify-end pt-2 border-t border-slate-100">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition"
          >
            Close Inspector
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
