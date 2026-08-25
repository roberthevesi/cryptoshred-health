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
      <div className="flex items-center gap-1.5 text-xs text-slate-500">
        <Icon className="h-3.5 w-3.5 text-emerald-600" />
        {label}
      </div>
      <p className={`text-sm text-slate-800 break-all ${mono ? 'font-mono' : ''}`}>{value}</p>
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
    const isVisit = proof.status === 'VISIT_DELETED' || (!proof.status?.includes('PATIENT') && !!proof.visitId);
    const identifier = isVisit
      ? (proof.visitId || proof.patientRecordId || 'visit')
      : (proof.patientId || proof.patientRecordId || 'patient');
    a.download = isVisit ? `proof-visit-${identifier}.json` : `proof-patient-${identifier}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="animate-slide-up rounded-2xl border border-emerald-200 bg-emerald-50/50 p-6 shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between gap-3 mb-5">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-100">
            <ShieldCheck className="h-5 w-5 text-emerald-600" />
          </div>
          <div>
            <h3 className="font-semibold text-slate-900">Signed Verifiable Proof of Deletion</h3>
            <p className="text-xs text-emerald-700 flex items-center gap-1 font-medium">
              <CheckCircle2 className="h-3 w-3" />
              GDPR Article 17 Compliant
            </p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={handleDownload}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition shadow-sm"
          >
            <Download className="h-3.5 w-3.5" />
            Download JSON
          </button>
          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">
            {proof.status}
          </span>
        </div>
      </div>

      {/* Divider */}
      <div className="my-4 border-t border-emerald-200" />

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
        <div className="mt-4 p-3 rounded-xl bg-white border border-emerald-200 space-y-2">
          {proof.merkleRoot && (
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-500 flex items-center gap-1">
                <GitBranch className="h-3.5 w-3.5 text-emerald-600" /> Merkle Tree Root Hash
              </span>
              <span className="font-mono text-emerald-800 break-all">{proof.merkleRoot}</span>
            </div>
          )}
          {proof.coveredStorageLayers && (
            <div className="flex items-center justify-between text-xs">
              <span className="text-slate-500 flex items-center gap-1">
                <Layers className="h-3.5 w-3.5 text-emerald-600" /> Verified Storage Coverage
              </span>
              <span className="text-slate-700">
                {proof.coveredStorageLayers.join(', ')}
              </span>
            </div>
          )}
        </div>
      )}

      {/* RSA Digital Signature */}
      {proof.digitalSignature && (
        <div className="mt-4">
          <p className="text-xs text-slate-500 mb-1 flex items-center gap-1">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" /> RSA Digital Signature (Base64)
          </p>
          <div className="rounded-xl bg-white border border-emerald-200 p-3">
            <p className="text-[10px] font-mono text-emerald-800 break-all leading-tight">
              {proof.digitalSignature}
            </p>
          </div>
        </div>
      )}

      {/* Audit trail */}
      <div className="mt-4">
        <p className="text-xs text-slate-500 mb-1 flex items-center gap-1">
          <FileText className="h-3.5 w-3.5 text-emerald-600" /> Canonical Audit Trail Payload
        </p>
        <div className="rounded-xl bg-white border border-slate-200 p-3">
          <pre className="text-xs font-mono text-slate-700 whitespace-pre-wrap break-all leading-relaxed">
            {proof.auditTrail}
          </pre>
        </div>
      </div>
    </div>
  );
}
