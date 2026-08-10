import React from 'react';
import { CheckCircle2, Hash, Clock, User, FileText, ShieldCheck, Download, KeyRound, Layers, GitBranch } from 'lucide-react';
import type { DeletionProof } from '../types';

interface Props {
  proof: DeletionProof;
}

function ProofRow({ icon: Icon, label, value, mono = false }: {
  icon: React.ElementType; label: string; value: string; mono?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-1.5 text-xs text-slate-400">
        <Icon className="h-3.5 w-3.5 text-emerald-400" />
        {label}
      </div>
      <p className={`text-sm text-slate-200 break-all ${mono ? 'font-mono' : ''}`}>{value}</p>
    </div>
  );
}

export default function DeletionProofCard({ proof }: Props) {
  const formattedTime = new Date(proof.timestamp).toLocaleString();
  const hashValue = proof.auditTrailHash || proof.sha256Hash || '';

  const handleDownload = () => {
    const jsonString = JSON.stringify(proof, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `proof-patient-${proof.patientRecordId}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="animate-slide-up rounded-2xl border border-emerald-800/50 bg-emerald-950/30 p-6 shadow-xl">
      {/* Header */}
      <div className="flex items-center justify-between gap-3 mb-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-900/50">
            <ShieldCheck className="h-5 w-5 text-emerald-400" />
          </div>
          <div>
            <h3 className="font-semibold text-white">Signed Verifiable Proof of Deletion</h3>
            <p className="text-xs text-emerald-400 flex items-center gap-1">
              <CheckCircle2 className="h-3 w-3" />
              GDPR Article 17 Compliant
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleDownload}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition"
          >
            <Download className="h-3.5 w-3.5" />
            Download JSON
          </button>
          <span className="badge bg-emerald-900/60 text-emerald-300 border border-emerald-700">
            {proof.status}
          </span>
        </div>
      </div>

      {/* Divider */}
      <div className="my-4 border-t border-emerald-800/40" />

      {/* Proof fields */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <ProofRow icon={Clock}      label="Timestamp"         value={formattedTime} />
        <ProofRow icon={User}       label="Requested By"      value={proof.requestedBy} />
        <ProofRow icon={FileText}   label="Record ID"         value={proof.patientRecordId} mono />
        {proof.vaultKeyName && (
          <ProofRow icon={KeyRound}  label="Vault KEK Destroyed" value={proof.vaultKeyName} mono />
        )}
        <ProofRow icon={Hash}       label="SHA-256 Audit Hash" value={hashValue} mono />
        {proof.signatureAlgorithm && (
          <ProofRow icon={ShieldCheck} label="Signature Algorithm" value={proof.signatureAlgorithm} />
        )}
      </div>

      {/* Merkle Root & Covered Storage */}
      {(proof.merkleRoot || proof.coveredStorageLayers) && (
        <div className="mt-4 p-3 rounded-xl bg-emerald-950/40 border border-emerald-800/30 space-y-2">
          {proof.merkleRoot && (
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400 flex items-center gap-1">
                <GitBranch className="h-3.5 w-3.5 text-emerald-400" /> Merkle Tree Root Hash
              </span>
              <span className="font-mono text-emerald-300 break-all">{proof.merkleRoot}</span>
            </div>
          )}
          {proof.coveredStorageLayers && (
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-400 flex items-center gap-1">
                <Layers className="h-3.5 w-3.5 text-emerald-400" /> Verified Storage Coverage
              </span>
              <span className="text-slate-300">
                {proof.coveredStorageLayers.join(', ')}
              </span>
            </div>
          )}
        </div>
      )}

      {/* RSA Digital Signature */}
      {proof.digitalSignature && (
        <div className="mt-4">
          <p className="text-xs text-slate-400 mb-1 flex items-center gap-1">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-400" /> RSA Digital Signature (Base64)
          </p>
          <div className="rounded-xl bg-black/40 border border-emerald-900/60 p-3">
            <p className="text-[10px] font-mono text-emerald-400 break-all leading-tight">
              {proof.digitalSignature}
            </p>
          </div>
        </div>
      )}

      {/* Audit trail */}
      <div className="mt-4">
        <p className="text-xs text-slate-400 mb-1 flex items-center gap-1">
          <FileText className="h-3.5 w-3.5 text-emerald-400" /> Canonical Audit Trail Payload
        </p>
        <div className="rounded-xl bg-slate-950 border border-slate-800 p-3">
          <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap break-all leading-relaxed">
            {proof.auditTrail}
          </pre>
        </div>
      </div>
    </div>
  );
}
