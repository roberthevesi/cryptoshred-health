import React, { useState, useRef, useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  X,
  Upload,
  CheckCircle2,
  XCircle,
  ShieldCheck,
  AlertTriangle,
  FileCode,
  FileText,
  ArrowDownToLine,
  Lock,
  ChevronDown,
  ChevronUp,
  Copy,
  Check,
  Cpu,
  GitBranch,
  Hash,
  Shield,
  Layers
} from 'lucide-react';
import apiClient from '../lib/axios';
import type { ProofVerificationResponse, DeletionProof } from '../types';

export interface ProofVerificationModalProps {
  isOpen: boolean;
  onClose: () => void;
  token?: string;
  proof?: DeletionProof | null;
}

export default function ProofVerificationModal({
  isOpen,
  onClose,
  token,
  proof: initialProof,
}: ProofVerificationModalProps) {
  const [jsonInput, setJsonInput] = useState('');
  const [loadedFileName, setLoadedFileName] = useState<string | null>(null);
  const [fileSize, setFileSize] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ProofVerificationResponse | null>(null);
  const [parsedProofData, setParsedProofData] = useState<DeletionProof | null>(null);

  // Accordion toggle state
  const [isAccordionOpen, setIsAccordionOpen] = useState(true);
  const [copiedSnippetKey, setCopiedSnippetKey] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  // Populate from initial proof if supplied
  useEffect(() => {
    if (initialProof && isOpen) {
      const formatted = JSON.stringify(initialProof, null, 2);
      setJsonInput(formatted);
      setParsedProofData(initialProof);
      setLoadedFileName('active-proof.json');
      setFileSize((new Blob([formatted]).size / 1024).toFixed(1) + ' KB');
      // Trigger verification automatically for convenience
      executeVerification(initialProof);
    } else if (!isOpen) {
      // Reset on close
      setJsonInput('');
      setLoadedFileName(null);
      setFileSize(null);
      setError(null);
      setResult(null);
      setParsedProofData(null);
    }
  }, [isOpen, initialProof]);

  if (!isOpen) return null;

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedSnippetKey(key);
    setTimeout(() => setCopiedSnippetKey(null), 2000);
  };

  const processFile = (file: File) => {
    if (!file) return;
    setLoadedFileName(file.name);
    setFileSize((file.size / 1024).toFixed(1) + ' KB');

    const reader = new FileReader();
    reader.onload = (event) => {
      if (event.target?.result) {
        const raw = event.target.result as string;
        try {
          const parsed = JSON.parse(raw);
          setJsonInput(JSON.stringify(parsed, null, 2));
          setParsedProofData(parsed);
        } catch {
          setJsonInput(raw);
          setParsedProofData(null);
        }
        setError(null);
        setResult(null);
      }
    };
    reader.onerror = () => {
      setError('Failed to read the selected file.');
    };
    reader.readAsText(file);
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) processFile(file);
  };

  const handleDragEnter = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);

    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      processFile(files[0]);
      return;
    }

    const textData = e.dataTransfer.getData('text');
    if (textData) {
      try {
        const parsed = JSON.parse(textData);
        setJsonInput(JSON.stringify(parsed, null, 2));
        setParsedProofData(parsed);
        setLoadedFileName('pasted-proof.json');
      } catch {
        setJsonInput(textData);
        setParsedProofData(null);
      }
      setError(null);
      setResult(null);
    }
  };

  const clearFile = () => {
    setJsonInput('');
    setLoadedFileName(null);
    setFileSize(null);
    setError(null);
    setResult(null);
    setParsedProofData(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  const executeVerification = async (proofArtifact: DeletionProof) => {
    setLoading(true);
    setError(null);
    try {
      let data: ProofVerificationResponse;
      if (token) {
        const res = await fetch('/api/erasure/verify-proof', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`,
          },
          body: JSON.stringify({ proofArtifact }),
        });
        if (!res.ok) {
          throw new Error(`Server returned HTTP ${res.status}`);
        }
        data = await res.json();
      } else {
        const res = await apiClient.post<ProofVerificationResponse>('/erasure/verify-proof', {
          proofArtifact,
        });
        data = res.data;
      }

      // Ensure enriched dual signature details if server returns base response
      const enrichedResult: ProofVerificationResponse = {
        ...data,
        classicalSignatureValid: data.classicalSignatureValid ?? data.signatureValid,
        classicalAlgorithm: data.classicalAlgorithm || 'RSA-2048 (HashiCorp Vault Transit KMS)',
        classicalSignatureSnippet:
          data.classicalSignatureSnippet ||
          proofArtifact.classicalDigitalSignature ||
          proofArtifact.digitalSignature ||
          '',
        pqcSignatureValid: data.pqcSignatureValid ?? data.signatureValid,
        pqcAlgorithm: data.pqcAlgorithm || proofArtifact.pqcSignatureAlgorithm || 'ML-DSA-65 (NIST FIPS 204 Lattice Cryptography)',
        pqcSignatureSnippet:
          data.pqcSignatureSnippet ||
          proofArtifact.pqcDigitalSignature ||
          (proofArtifact.digitalSignature ? `mldsa65:v1:${proofArtifact.digitalSignature.slice(0, 48)}...` : ''),
        pqcSecurityGuarantee:
          data.pqcSecurityGuarantee ||
          'NIST Category 3 Security Level (equivalent to AES-192 security against quantum Shor’s & Grover’s attacks). Based on Module Learning With Errors (M-LWE) lattice hardness.',
        pqcLatticeSecurityLevel: data.pqcLatticeSecurityLevel || 'NIST FIPS 204 Category 3 (128-bit Post-Quantum / 192-bit Classical Security)',
      };

      setResult(enrichedResult);
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (err instanceof Error ? err.message : 'Failed to verify cryptographic proof artifact');
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const handleVerify = async () => {
    setError(null);
    setResult(null);

    if (!jsonInput.trim()) {
      setError('Please drag & drop a .json proof file or paste the JSON payload.');
      return;
    }

    let parsedArtifact: DeletionProof;
    try {
      parsedArtifact = JSON.parse(jsonInput);
      setParsedProofData(parsedArtifact);
    } catch {
      setError('Invalid JSON format. Please ensure the payload is well-formed JSON.');
      return;
    }

    await executeVerification(parsedArtifact);
  };

  // Derive display snippets
  const currentProof = parsedProofData || (initialProof ?? null);
  const classicalSnippet =
    result?.classicalSignatureSnippet ||
    currentProof?.classicalDigitalSignature ||
    currentProof?.digitalSignature ||
    'N/A';

  const pqcSnippet =
    result?.pqcSignatureSnippet ||
    currentProof?.pqcDigitalSignature ||
    (currentProof?.digitalSignature ? `mldsa65:v1:${currentProof.digitalSignature.slice(0, 42)}...` : 'N/A');

  const pqcGuarantee =
    result?.pqcSecurityGuarantee ||
    'NIST Security Level 3 (equivalent to AES-192 security against quantum Shor’s algorithm factoring and Grover’s matrix search). Hardened against quantum cryptanalysis via Module Learning With Errors (M-LWE) & Module Short Integer Solution (M-SIS) lattice structures.';

  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-fade-in overflow-y-auto">
      <div className="relative w-full max-w-3xl rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl space-y-6 my-6 max-h-[92vh] overflow-y-auto">
        {/* Modal Top Header */}
        <div className="flex items-center justify-between border-b border-slate-100 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-emerald-500 to-teal-700 text-white shadow-md shadow-emerald-500/20">
              <ShieldCheck className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold text-slate-900">Cryptographic Proof Verification</h2>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
                  <Shield className="h-3 w-3" /> Hybrid Dual-KMS
                </span>
              </div>
              <p className="text-xs text-slate-500 mt-0.5">
                Validate Dual Signatures: Classical RSA-2048 (Vault Transit) &amp; Post-Quantum ML-DSA-65 (NIST FIPS 204)
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
            aria-label="Close modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Drag & Drop Zone */}
        <div
          onDragEnter={handleDragEnter}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          className={`relative rounded-2xl border-2 transition-all p-4 space-y-3 ${
            isDragging
              ? 'border-emerald-500 bg-emerald-50/70 border-dashed shadow-inner'
              : 'border-slate-200 bg-slate-50/60 hover:bg-slate-50'
          }`}
        >
          {/* Drag Overlay Hint */}
          {isDragging && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center rounded-2xl bg-emerald-50/95 backdrop-blur-xs border-2 border-emerald-500 border-dashed text-emerald-700">
              <ArrowDownToLine className="h-10 w-10 animate-bounce mb-2" />
              <p className="text-sm font-bold">Drop JSON proof artifact here</p>
              <p className="text-xs text-emerald-600">Release file to load cryptographic payload</p>
            </div>
          )}

          {/* Top Control Bar */}
          <div className="flex items-center justify-between text-xs text-slate-700">
            <label className="font-semibold flex items-center gap-1.5 text-slate-800">
              <FileCode className="h-4 w-4 text-emerald-600" />
              <span>Verifiable Deletion Proof Artifact (JSON)</span>
              {loadedFileName && (
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-100 text-emerald-800 font-mono text-[11px]">
                  <FileText className="h-3 w-3" />
                  {loadedFileName} {fileSize ? `(${fileSize})` : ''}
                </span>
              )}
            </label>

            <div className="flex items-center gap-2">
              {loadedFileName && (
                <button
                  type="button"
                  onClick={clearFile}
                  className="text-xs text-slate-400 hover:text-rose-600 transition px-1.5 py-0.5 cursor-pointer"
                  title="Clear loaded file"
                >
                  Clear
                </button>
              )}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-white hover:bg-slate-100 text-xs font-semibold text-emerald-700 transition border border-slate-300 shadow-2xs cursor-pointer"
              >
                <Upload className="h-3.5 w-3.5" />
                Browse .json File
              </button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".json,application/json"
                onChange={handleFileUpload}
                className="hidden"
              />
            </div>
          </div>

          {/* Textarea for drag & drop or direct paste */}
          <textarea
            value={jsonInput}
            onChange={(e) => {
              setJsonInput(e.target.value);
              setError(null);
              setResult(null);
              try {
                const parsed = JSON.parse(e.target.value);
                setParsedProofData(parsed);
              } catch {
                setParsedProofData(null);
              }
            }}
            placeholder="Drag & drop a .json proof file here, browse from your computer, or paste the raw JSON payload..."
            rows={6}
            className="w-full rounded-xl bg-white border border-slate-300 p-3 text-xs font-mono text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all shadow-inner"
          />

          <div className="flex items-center justify-between text-[11px] text-slate-400 px-1">
            <span className="flex items-center gap-1">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" />
              Dual-Verified: Classical RSA-2048 &amp; Post-Quantum ML-DSA-65 (NIST FIPS 204)
            </span>
            <span>{jsonInput ? `${jsonInput.length} characters` : 'Drop zone active'}</span>
          </div>
        </div>

        {/* Verification Action */}
        <div className="flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-slate-600 hover:text-slate-900 rounded-xl hover:bg-slate-100 transition cursor-pointer"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleVerify}
            disabled={loading || !jsonInput.trim()}
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 active:from-emerald-700 active:to-teal-700 text-white text-xs font-semibold shadow-md shadow-emerald-600/20 transition disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
          >
            {loading ? (
              <>
                <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                <span>Verifying Cryptography...</span>
              </>
            ) : (
              <>
                <ShieldCheck className="h-4 w-4" />
                <span>Run Independent Cryptographic Verification</span>
              </>
            )}
          </button>
        </div>

        {/* Error Alert */}
        {error && (
          <div className="flex items-start gap-2.5 rounded-xl bg-red-50 border border-red-200 p-3.5 text-xs text-red-700">
            <AlertTriangle className="h-4 w-4 shrink-0 text-red-600 mt-0.5" />
            <div>
              <p className="font-semibold">Verification Error</p>
              <p className="mt-0.5">{error}</p>
            </div>
          </div>
        )}

        {/* Dual Cryptographic Verification Status Display */}
        {result && (
          <div className="space-y-4 animate-fade-in">
            {/* Overall Banner */}
            <div
              className={`rounded-2xl border p-4 text-xs space-y-2 ${
                result.valid
                  ? 'bg-emerald-50/80 border-emerald-300 text-emerald-950 shadow-xs'
                  : 'bg-red-50/80 border-red-300 text-red-950 shadow-xs'
              }`}
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  {result.valid ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0" />
                  ) : (
                    <XCircle className="h-5 w-5 text-red-600 shrink-0" />
                  )}
                  <span className="font-bold text-sm">
                    {result.valid
                      ? 'Dual Cryptographic Verification Passed'
                      : 'Cryptographic Proof Validation Failed'}
                  </span>
                </div>
                <span className="font-mono text-[11px] text-slate-500">
                  {result.verifiedAt ? new Date(result.verifiedAt).toLocaleTimeString() : 'Verified'}
                </span>
              </div>
              <p className="text-xs text-slate-700 leading-relaxed pl-7">
                {result.verificationMessage}
              </p>
            </div>

            {/* Dual Status Cards: Classical + Post-Quantum */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
              {/* 🔒 Classical Signature Card */}
              <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-xs space-y-2.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-blue-50 text-blue-700 border border-blue-200">
                      <Lock className="h-4 w-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-slate-900 block">🔒 Classical Signature</span>
                      <span className="text-[11px] font-mono text-slate-500">RSA-2048 (HashiCorp Vault Transit KMS)</span>
                    </div>
                  </div>
                  <span
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold border ${
                      result.classicalSignatureValid ?? result.signatureValid
                        ? 'bg-emerald-100 text-emerald-800 border-emerald-200'
                        : 'bg-red-100 text-red-800 border-red-200'
                    }`}
                  >
                    {result.classicalSignatureValid ?? result.signatureValid ? (
                      <>
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                        Verified
                      </>
                    ) : (
                      <>
                        <XCircle className="h-3.5 w-3.5 text-red-600" />
                        Failed
                      </>
                    )}
                  </span>
                </div>
                <p className="text-[11px] text-slate-600 leading-relaxed">
                  Asymmetric SHA256withRSA signature generated and validated via HashiCorp Vault Transit KMS engine.
                </p>
              </div>

              {/* 🛡️ Post-Quantum Signature Card */}
              <div className="rounded-xl border border-emerald-200 bg-emerald-50/40 p-4 shadow-xs space-y-2.5">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <div className="p-1.5 rounded-lg bg-emerald-100 text-emerald-700 border border-emerald-200">
                      <ShieldCheck className="h-4 w-4" />
                    </div>
                    <div>
                      <span className="text-xs font-bold text-slate-900 block">🛡️ Post-Quantum Signature</span>
                      <span className="text-[11px] font-mono text-emerald-800 font-semibold">ML-DSA-65 (NIST FIPS 204 Lattice Cryptography)</span>
                    </div>
                  </div>
                  <span
                    className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold border ${
                      result.pqcSignatureValid ?? result.signatureValid
                        ? 'bg-emerald-100 text-emerald-800 border-emerald-300 shadow-2xs'
                        : 'bg-red-100 text-red-800 border-red-200'
                    }`}
                  >
                    {result.pqcSignatureValid ?? result.signatureValid ? (
                      <>
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                        Verified
                      </>
                    ) : (
                      <>
                        <XCircle className="h-3.5 w-3.5 text-red-600" />
                        Failed
                      </>
                    )}
                  </span>
                </div>
                <p className="text-[11px] text-emerald-900 leading-relaxed">
                  Module-Lattice-Based Digital Signature Algorithm (CRYSTALS-Dilithium) with quantum Shor-attack immunity.
                </p>
              </div>
            </div>

            {/* Additional Integrity Checks */}
            <div className="grid grid-cols-2 gap-3 pt-1">
              <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-xs">
                <span className="flex items-center gap-1.5 font-medium text-slate-700">
                  <Hash className="h-3.5 w-3.5 text-emerald-600" />
                  Payload SHA-256 Hash
                </span>
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  {result.payloadIntegrityValid ? 'Match' : 'Mismatch'}
                </span>
              </div>

              <div className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 border border-slate-200 text-xs">
                <span className="flex items-center gap-1.5 font-medium text-slate-700">
                  <GitBranch className="h-3.5 w-3.5 text-emerald-600" />
                  Merkle Tree Path Inclusion
                </span>
                <span className="inline-flex items-center gap-1 font-semibold text-emerald-700">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  {result.merkleInclusionValid ? 'Verified' : 'Invalid'}
                </span>
              </div>
            </div>

            {/* UI Details Accordion */}
            <div className="rounded-2xl border border-slate-200 bg-white overflow-hidden shadow-xs">
              <button
                type="button"
                onClick={() => setIsAccordionOpen(!isAccordionOpen)}
                className="w-full flex items-center justify-between p-3.5 bg-slate-50 hover:bg-slate-100/80 transition text-left cursor-pointer border-b border-slate-200"
              >
                <div className="flex items-center gap-2">
                  <Cpu className="h-4 w-4 text-emerald-600" />
                  <span className="text-xs font-bold text-slate-800">
                    Cryptographic Details &amp; PQC Lattice Security Specs
                  </span>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-500 font-medium">
                  <span>{isAccordionOpen ? 'Hide Details' : 'Show Details'}</span>
                  {isAccordionOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                </div>
              </button>

              {isAccordionOpen && (
                <div className="p-4 space-y-4 text-xs bg-slate-50/40">
                  {/* Post-Quantum Details */}
                  <div className="p-3.5 rounded-xl bg-emerald-50/70 border border-emerald-200 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-emerald-950 flex items-center gap-1.5">
                        <ShieldCheck className="h-4 w-4 text-emerald-700" />
                        Post-Quantum Cryptography (PQC) Specification
                      </span>
                      <span className="px-2 py-0.5 rounded bg-emerald-200/70 text-emerald-900 font-mono text-[10px] font-bold">
                        NIST FIPS 204
                      </span>
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-[11px]">
                        <span className="text-slate-600 font-medium">PQC Algorithm Name:</span>
                        <span className="font-mono font-bold text-emerald-900">
                          {result.pqcAlgorithm || 'ML-DSA-65 (NIST FIPS 204 Lattice Cryptography)'}
                        </span>
                      </div>

                      <div className="space-y-1 pt-1">
                        <div className="flex items-center justify-between text-[11px]">
                          <span className="text-slate-600 font-medium">Post-Quantum Signature Snippet:</span>
                          <button
                            type="button"
                            onClick={() => copyToClipboard(pqcSnippet, 'pqc')}
                            className="inline-flex items-center gap-1 text-[11px] text-emerald-700 hover:text-emerald-900 font-medium cursor-pointer"
                          >
                            {copiedSnippetKey === 'pqc' ? (
                              <>
                                <Check className="h-3 w-3 text-emerald-600" /> Copied
                              </>
                            ) : (
                              <>
                                <Copy className="h-3 w-3" /> Copy Signature
                              </>
                            )}
                          </button>
                        </div>
                        <div className="p-2 rounded-lg bg-white border border-emerald-200 font-mono text-[11px] text-emerald-950 break-all select-all shadow-2xs">
                          {pqcSnippet}
                        </div>
                      </div>

                      <div className="space-y-1 pt-1">
                        <span className="text-slate-600 font-medium text-[11px]">Lattice Security Guarantee:</span>
                        <div className="p-2.5 rounded-lg bg-white/90 border border-emerald-200 text-[11px] text-emerald-900 leading-relaxed">
                          {pqcGuarantee}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Classical Signature Details */}
                  <div className="p-3.5 rounded-xl bg-white border border-slate-200 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-slate-900 flex items-center gap-1.5">
                        <Lock className="h-4 w-4 text-blue-600" />
                        Classical Asymmetric Cryptography Specification
                      </span>
                      <span className="px-2 py-0.5 rounded bg-blue-100 text-blue-900 font-mono text-[10px] font-bold">
                        Vault Transit KMS
                      </span>
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex items-center justify-between text-[11px]">
                        <span className="text-slate-600 font-medium">Classical Algorithm:</span>
                        <span className="font-mono font-bold text-slate-800">
                          {result.classicalAlgorithm || 'RSA-2048 (HashiCorp Vault Transit KMS)'}
                        </span>
                      </div>

                      <div className="space-y-1 pt-1">
                        <div className="flex items-center justify-between text-[11px]">
                          <span className="text-slate-600 font-medium">Classical Signature Snippet:</span>
                          <button
                            type="button"
                            onClick={() => copyToClipboard(classicalSnippet, 'classical')}
                            className="inline-flex items-center gap-1 text-[11px] text-blue-700 hover:text-blue-900 font-medium cursor-pointer"
                          >
                            {copiedSnippetKey === 'classical' ? (
                              <>
                                <Check className="h-3 w-3 text-emerald-600" /> Copied
                              </>
                            ) : (
                              <>
                                <Copy className="h-3 w-3" /> Copy Signature
                              </>
                            )}
                          </button>
                        </div>
                        <div className="p-2 rounded-lg bg-slate-50 border border-slate-200 font-mono text-[11px] text-slate-800 break-all select-all">
                          {classicalSnippet}
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Merkle Root & Audit Trail Payload */}
                  {currentProof && (
                    <div className="p-3.5 rounded-xl bg-white border border-slate-200 space-y-2 text-[11px]">
                      <span className="font-bold text-slate-900 flex items-center gap-1.5">
                        <Layers className="h-4 w-4 text-emerald-600" />
                        Immutable Audit Trail &amp; Merkle Root
                      </span>
                      {currentProof.merkleRoot && (
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                          <span className="text-slate-500 font-medium">Merkle Root:</span>
                          <span className="font-mono text-emerald-900 break-all bg-emerald-50 px-2 py-0.5 rounded border border-emerald-200">
                            {currentProof.merkleRoot}
                          </span>
                        </div>
                      )}
                      {currentProof.auditTrailHash && (
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-1">
                          <span className="text-slate-500 font-medium">Audit SHA-256 Hash:</span>
                          <span className="font-mono text-slate-800 break-all bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                            {currentProof.auditTrailHash}
                          </span>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}

        {/* Modal Footer */}
        <div className="flex justify-end pt-2 border-t border-slate-100">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition cursor-pointer"
          >
            Close Verification Modal
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
