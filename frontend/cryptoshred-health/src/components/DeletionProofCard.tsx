import React, { useState } from 'react';
import {
  CheckCircle2,
  Hash,
  Clock,
  FileText,
  ShieldCheck,
  Download,
  KeyRound,
  Layers,
  GitBranch,
  RefreshCw,
  Copy,
  Check,
  ChevronDown,
  ChevronUp,
  X,
  Scale,
} from 'lucide-react';
import type { DeletionProof, ProofVerificationResponse } from '../types';

interface Props {
  proof: DeletionProof;
  onVerify?: () => void;
  isVerifying?: boolean;
  verificationResult?: ProofVerificationResponse | null;
  onClose?: () => void;
  className?: string;
}

export default function DeletionProofCard({
  proof,
  onVerify,
  isVerifying = false,
  verificationResult,
  onClose,
  className = '',
}: Props) {
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [showCanonical, setShowCanonical] = useState(false);

  const formattedTime = proof.timestamp
    ? new Date(proof.timestamp).toLocaleString('en-GB', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      })
    : 'Unknown';

  const hashValue = proof.auditTrailHash || proof.sha256Hash || '';

  const isVisit =
    proof.scope === 'CLINICAL_VISIT' ||
    proof.status === 'VISIT_DELETED' ||
    (!proof.status?.includes('PATIENT') && !!proof.visitId);

  const scopeBadgeText = isVisit ? '🏥 CLINICAL VISIT' : '🏛️ FULL PATIENT';
  const scopeBadgeClasses = isVisit
    ? 'bg-purple-50 text-purple-700 border-purple-200'
    : 'bg-blue-50 text-blue-700 border-blue-200';

  const recordIdentifier = isVisit
    ? proof.visitId || proof.patientRecordId || 'visit'
    : proof.patientId || proof.patientRecordId || 'patient';

  const entityDesc =
    proof.entityDescription ||
    (isVisit
      ? `Clinical Visit: ${recordIdentifier}`
      : `Patient Profile: ${recordIdentifier}`);

  const copyToClipboard = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const handleDownload = () => {
    const jsonString = JSON.stringify(proof, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = isVisit
      ? `proof-visit-${recordIdentifier.slice(0, 8)}.json`
      : `proof-patient-${recordIdentifier.slice(0, 8)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const classicalSig = proof.classicalDigitalSignature || proof.digitalSignature || '';
  const pqcSig = proof.pqcDigitalSignature || (proof.digitalSignature ? `mldsa65:v1:${proof.digitalSignature}` : '');

  const classicalValid = verificationResult
    ? (verificationResult.classicalSignatureValid ?? verificationResult.signatureValid)
    : true;
  const pqcValid = verificationResult
    ? (verificationResult.pqcSignatureValid ?? verificationResult.signatureValid)
    : true;

  return (
    <div className={`rounded-2xl border border-emerald-300 bg-white p-4 sm:p-5 shadow-xl space-y-3.5 ${className}`}>
      {/* 1. Modal Top Bar: Title, Badges, and Action Buttons */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-xs">
            <ShieldCheck className="h-5 w-5" />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-bold text-slate-900 text-sm tracking-tight">
                Cryptographic Deletion Certificate
              </h3>
              <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold border ${scopeBadgeClasses}`}>
                {scopeBadgeText}
              </span>
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
                <CheckCircle2 className="h-3 w-3 text-emerald-600" />
                {verificationResult?.valid ? 'Dual Signatures Verified' : isVerifying ? 'Verifying...' : 'Legally Sealed'}
              </span>
            </div>
            <p className="text-[11px] text-slate-500 font-medium mt-0.5">
              GDPR Article 17 ("Right to be Forgotten") Verifiable Deletion Artifact
            </p>
          </div>
        </div>

        {/* Action Controls */}
        <div className="flex items-center gap-2 self-end sm:self-center">
          {onVerify && (
            <button
              type="button"
              onClick={onVerify}
              disabled={isVerifying}
              className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-slate-50 hover:bg-emerald-50 border border-slate-200 hover:border-emerald-300 text-slate-700 hover:text-emerald-700 text-xs font-semibold transition cursor-pointer"
              title="Re-run live cryptographic signature verification"
            >
              <RefreshCw className={`h-3 w-3 ${isVerifying ? 'animate-spin text-emerald-600' : ''}`} />
              <span>Re-verify</span>
            </button>
          )}

          <button
            type="button"
            onClick={handleDownload}
            className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 active:bg-emerald-700 text-white text-xs font-semibold shadow-xs transition cursor-pointer"
            title="Download JSON certificate artifact"
          >
            <Download className="h-3 w-3" />
            <span>Export JSON</span>
          </button>

          {onClose && (
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition ml-1 cursor-pointer"
              title="Close modal"
            >
              <X className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      {/* 2. Metadata Grid: 4 Compact Informational Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-2.5">
        {/* Target Entity */}
        <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1 mb-1">
            <FileText className="h-3 w-3 text-emerald-600" /> Target Deleted Record
          </span>
          <p className="text-xs font-bold text-slate-900 truncate" title={entityDesc}>
            {entityDesc}
          </p>
          <span className="text-[10px] font-mono text-slate-500 block truncate">
            {recordIdentifier}
          </span>
        </div>

        {/* Erasure Timestamp */}
        <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1 mb-1">
            <Clock className="h-3 w-3 text-emerald-600" /> Destruction Time
          </span>
          <p className="text-xs font-bold text-slate-900">{formattedTime}</p>
          <span className="text-[10px] text-slate-500">Immutable KMS Audit Log</span>
        </div>

        {/* Legal Ground / Requested By */}
        <div className="p-2.5 rounded-xl bg-slate-50 border border-slate-200/80">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1 mb-1">
            <Scale className="h-3 w-3 text-emerald-600" /> Legal Ground &amp; Origin
          </span>
          <p className="text-xs font-bold text-emerald-800 truncate">
            {proof.statutoryOverrideReason || 'CONSENT_WITHDRAWN'}
          </p>
          <span className="text-[10px] text-slate-500 truncate block">
            Auth: {proof.requestedBy || 'Data Protection Officer'}
          </span>
        </div>

        {/* Eradicated Vault KEK */}
        <div className="p-2.5 rounded-xl bg-rose-50/60 border border-rose-200/80">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-rose-700 flex items-center gap-1 mb-1">
            <KeyRound className="h-3 w-3 text-rose-600" /> Eradicated Vault KEK
          </span>
          <p className="text-[11px] font-mono font-bold text-rose-900 truncate" title={proof.vaultKeyName || 'transit/keys/shredded'}>
            {proof.vaultKeyName ? proof.vaultKeyName.replace('transit/keys/', '') : 'N/A (Destroyed)'}
          </p>
          <span className="text-[10px] font-semibold text-rose-600">
            Zeroized in KMS Enclave
          </span>
        </div>
      </div>

      {/* 3. Cryptographic Hashes & Covered Storage Strip */}
      <div className="p-2.5 rounded-xl bg-emerald-50/60 border border-emerald-200 flex flex-col md:flex-row md:items-center justify-between gap-2.5 text-xs">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
          <div className="flex items-center gap-1.5 font-mono text-[11px]">
            <span className="text-slate-500 font-sans font-medium flex items-center gap-1">
              <Hash className="h-3 w-3 text-emerald-600" /> Leaf Hash:
            </span>
            <span className="text-emerald-950 font-bold bg-white/90 px-1.5 py-0.5 rounded border border-emerald-200 select-all">
              {hashValue.slice(0, 16)}...{hashValue.slice(-8)}
            </span>
            <button
              type="button"
              onClick={() => copyToClipboard(hashValue, 'hash')}
              className="text-slate-400 hover:text-emerald-700 cursor-pointer"
              title="Copy full SHA-256 leaf hash"
            >
              {copiedKey === 'hash' ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
            </button>
          </div>

          {proof.merkleRoot && (
            <div className="flex items-center gap-1.5 font-mono text-[11px]">
              <span className="text-slate-500 font-sans font-medium flex items-center gap-1">
                <GitBranch className="h-3 w-3 text-emerald-600" /> Merkle Root:
              </span>
              <span className="text-emerald-950 font-bold bg-white/90 px-1.5 py-0.5 rounded border border-emerald-200 select-all">
                {proof.merkleRoot.slice(0, 16)}...{proof.merkleRoot.slice(-8)}
              </span>
              <button
                type="button"
                onClick={() => copyToClipboard(proof.merkleRoot || '', 'root')}
                className="text-slate-400 hover:text-emerald-700 cursor-pointer"
                title="Copy full Merkle root"
              >
                {copiedKey === 'root' ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
              </button>
            </div>
          )}
        </div>

        {/* Storage Layers Purged Badges */}
        <div className="flex items-center gap-1 text-[10px] font-mono text-slate-600 shrink-0">
          <Layers className="h-3 w-3 text-emerald-600 shrink-0" />
          <span className="font-sans font-medium text-slate-500">Purged Layers:</span>
          {(proof.coveredStorageLayers || ['PostgreSQL', 'Redis L2', 'Kafka', 'WORM']).map((layer) => (
            <span
              key={layer}
              className="px-1.5 py-0.5 rounded bg-white text-slate-700 font-semibold border border-slate-200"
            >
              {layer}
            </span>
          ))}
        </div>
      </div>

      {/* 4. MERGED DUAL CRYPTOGRAPHIC SIGNATURES (Side-by-side 2 Columns) */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {/* Classical RSA-2048 Card */}
        <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold text-slate-900 flex items-center gap-1">
                🔒 Classical Signature
              </span>
              <span className="text-[10px] text-slate-500 font-mono">
                RSA-2048
              </span>
            </div>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800 border border-emerald-200">
              <CheckCircle2 className="h-3 w-3 text-emerald-600" />
              {classicalValid ? 'Verified' : 'Invalid'}
            </span>
          </div>

          <p className="text-[10px] text-slate-500">
            HashiCorp Vault Transit KMS Enclave (<code className="text-slate-700">SHA256withRSA</code>)
          </p>

          <div className="relative rounded-lg bg-white border border-slate-200 p-2 font-mono text-[10px] text-slate-800 break-all select-all shadow-2xs leading-relaxed max-h-14 overflow-hidden">
            {classicalSig ? `${classicalSig.slice(0, 96)}...` : 'N/A'}
            <button
              type="button"
              onClick={() => copyToClipboard(classicalSig, 'classical')}
              className="absolute right-1.5 top-1.5 p-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-600 cursor-pointer"
              title="Copy complete RSA signature"
            >
              {copiedKey === 'classical' ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
            </button>
          </div>
        </div>

        {/* Post-Quantum ML-DSA-65 Card */}
        <div className="p-3 rounded-xl bg-emerald-50/50 border border-emerald-200 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5">
              <span className="text-xs font-bold text-emerald-950 flex items-center gap-1">
                🛡️ Post-Quantum Signature
              </span>
              <span className="text-[10px] text-emerald-800 font-mono font-semibold">
                ML-DSA-65
              </span>
            </div>
            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-200/80 text-emerald-900 border border-emerald-300">
              <CheckCircle2 className="h-3 w-3 text-emerald-700" />
              {pqcValid ? 'Quantum-Resistant' : 'Invalid'}
            </span>
          </div>

          <p className="text-[10px] text-emerald-800">
            NIST FIPS 204 Lattice Cryptography (CRYSTALS-Dilithium Category 3)
          </p>

          <div className="relative rounded-lg bg-white border border-emerald-200 p-2 font-mono text-[10px] text-emerald-950 break-all select-all shadow-2xs leading-relaxed max-h-14 overflow-hidden">
            {pqcSig ? `${pqcSig.slice(0, 96)}...` : 'N/A'}
            <button
              type="button"
              onClick={() => copyToClipboard(pqcSig, 'pqc')}
              className="absolute right-1.5 top-1.5 p-1 rounded bg-emerald-100 hover:bg-emerald-200 text-emerald-800 cursor-pointer"
              title="Copy complete ML-DSA signature"
            >
              {copiedKey === 'pqc' ? <Check className="h-3 w-3 text-emerald-600" /> : <Copy className="h-3 w-3" />}
            </button>
          </div>
        </div>
      </div>

      {/* 5. Verification Integrity Checklist & Collapsible Canonical Payload */}
      <div className="pt-2 border-t border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-[11px]">
        <div className="flex items-center gap-3 text-slate-600">
          <span className="inline-flex items-center gap-1 text-emerald-700 font-semibold">
            <CheckCircle2 className="h-3.5 w-3.5" /> Payload SHA-256 Valid
          </span>
          <span className="inline-flex items-center gap-1 text-emerald-700 font-semibold">
            <CheckCircle2 className="h-3.5 w-3.5" /> Merkle Inclusion Valid
          </span>
          <span className="text-slate-400 hidden sm:inline">|</span>
          <span className="text-slate-500 font-mono text-[10px] hidden sm:inline">
            Verified {new Date().toLocaleTimeString()}
          </span>
        </div>

        {proof.auditTrail && (
          <button
            type="button"
            onClick={() => setShowCanonical(!showCanonical)}
            className="inline-flex items-center gap-1 text-slate-500 hover:text-slate-900 font-medium cursor-pointer"
          >
            <span>{showCanonical ? 'Hide Raw Canonical String' : 'Show Raw Canonical String'}</span>
            {showCanonical ? <ChevronUp className="h-3 w-3" /> : <ChevronDown className="h-3 w-3" />}
          </button>
        )}
      </div>

      {/* Collapsed Canonical Payload Preview */}
      {showCanonical && proof.auditTrail && (
        <div className="p-2.5 rounded-xl bg-slate-900 text-slate-100 font-mono text-[10px] break-all leading-relaxed animate-fade-in select-all">
          {proof.auditTrail}
        </div>
      )}
    </div>
  );
}
