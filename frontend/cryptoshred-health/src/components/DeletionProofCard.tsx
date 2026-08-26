import React from 'react';
import {
  CheckCircle2,
  Hash,
  Clock,
  User,
  FileText,
  ShieldCheck,
  Download,
  KeyRound,
  Layers,
  GitBranch,
  FileCheck2,
  Building,
  Activity,
} from 'lucide-react';
import type { DeletionProof } from '../types';

interface Props {
  proof: DeletionProof;
  onVerify?: (proof: DeletionProof) => void;
  className?: string;
}

function ProofRow({
  icon: Icon,
  label,
  value,
  mono = false,
}: {
  icon: React.ElementType;
  label: string;
  value?: string | null;
  mono?: boolean;
}) {
  if (!value) return null;
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center gap-1.5 text-xs text-slate-500">
        <Icon className="h-3.5 w-3.5 text-emerald-600 shrink-0" />
        <span>{label}</span>
      </div>
      <p className={`text-sm text-slate-800 break-all ${mono ? 'font-mono' : ''}`}>{value}</p>
    </div>
  );
}

export default function DeletionProofCard({ proof, onVerify, className = '' }: Props) {
  const formattedTime = proof.timestamp ? new Date(proof.timestamp).toLocaleString() : 'Unknown';
  const hashValue = proof.auditTrailHash || proof.sha256Hash || '';

  const isVisit =
    proof.scope === 'CLINICAL_VISIT' ||
    proof.status === 'VISIT_DELETED' ||
    (!proof.status?.includes('PATIENT') && !!proof.visitId);

  const scopeBadgeText = isVisit ? '🏥 SINGLE CLINICAL VISIT' : '🏛️ FULL PATIENT PROFILE';
  const scopeBadgeClasses = isVisit
    ? 'bg-purple-100 text-purple-800 border-purple-200'
    : 'bg-blue-100 text-blue-800 border-blue-200';

  const recordIdentifier = isVisit
    ? proof.visitId || proof.patientRecordId || 'visit'
    : proof.patientId || proof.patientRecordId || 'patient';

  const entityDesc =
    proof.entityDescription ||
    (isVisit
      ? `Clinical Visit Chart: ${recordIdentifier}`
      : `Patient Demographic Profile: ${recordIdentifier}`);

  const handleDownload = () => {
    const jsonString = JSON.stringify(proof, null, 2);
    const blob = new Blob([jsonString], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = isVisit ? `proof-visit-${recordIdentifier}.json` : `proof-patient-${recordIdentifier}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  return (
    <div className={`animate-slide-up rounded-2xl border border-emerald-200 bg-emerald-50/50 p-6 shadow-sm ${className}`}>
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-5">
        <div className="flex items-start sm:items-center gap-3">
          <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-100 border border-emerald-200 shadow-2xs">
            <ShieldCheck className="h-6 w-6 text-emerald-600" />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="font-bold text-slate-900 text-base">Cryptographic Deletion Certificate</h3>
              <span className={`inline-flex items-center px-2.5 py-0.5 rounded-md text-xs font-bold border ${scopeBadgeClasses}`}>
                {scopeBadgeText}
              </span>
            </div>
            <p className="text-xs text-emerald-700 flex items-center gap-1 font-medium mt-0.5">
              <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600 shrink-0" />
              GDPR Article 17 ("Right to be Forgotten") Verifiable Artifact
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {onVerify && (
            <button
              onClick={() => onVerify(proof)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-white hover:bg-emerald-50 border border-emerald-300 text-emerald-700 text-xs font-semibold transition shadow-2xs cursor-pointer"
              title="Verify cryptographic signature and Merkle inclusion"
            >
              <FileCheck2 className="h-3.5 w-3.5 text-emerald-600" />
              Verify Proof
            </button>
          )}
          <button
            onClick={handleDownload}
            className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold transition shadow-sm cursor-pointer"
            title="Download JSON certificate artifact"
          >
            <Download className="h-3.5 w-3.5" />
            Download JSON
          </button>
          <span className="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-mono font-bold bg-emerald-100 text-emerald-900 border border-emerald-200">
            {proof.status}
          </span>
        </div>
      </div>

      {/* Target Description Banner */}
      <div className="mb-4 rounded-xl bg-white/80 border border-emerald-200/80 p-3 flex items-start gap-2 text-xs">
        <Activity className="h-4 w-4 text-emerald-600 shrink-0 mt-0.5" />
        <div>
          <span className="font-semibold text-slate-700 block">Target Deleted Entity:</span>
          <span className="text-slate-900 font-medium">{entityDesc}</span>
        </div>
      </div>

      {/* Divider */}
      <div className="my-4 border-t border-emerald-200" />

      {/* Proof fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
        <ProofRow icon={Clock} label="Timestamp" value={formattedTime} />
        <ProofRow icon={User} label="Requested By" value={proof.requestedBy} />
        <ProofRow icon={FileText} label={isVisit ? 'Visit Record ID' : 'Patient ID'} value={recordIdentifier} mono />
        {proof.patientId && isVisit && (
          <ProofRow icon={Building} label="Patient ID" value={proof.patientId} mono />
        )}
        {proof.vaultKeyName && (
          <ProofRow icon={KeyRound} label="Vault KEK Destroyed" value={proof.vaultKeyName} mono />
        )}
        <ProofRow icon={Hash} label="SHA-256 Audit Trail Hash" value={hashValue} mono />
        {proof.signatureAlgorithm && (
          <ProofRow icon={ShieldCheck} label="Signature Algorithm" value={proof.signatureAlgorithm} />
        )}
      </div>

      {/* Merkle Root & Covered Storage */}
      {(proof.merkleRoot || proof.coveredStorageLayers) && (
        <div className="mt-4 p-3.5 rounded-xl bg-white border border-emerald-200 space-y-2.5">
          {proof.merkleRoot && (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs gap-1">
              <span className="text-slate-500 flex items-center gap-1 font-medium">
                <GitBranch className="h-3.5 w-3.5 text-emerald-600 shrink-0" /> Merkle Tree Root Hash
              </span>
              <span className="font-mono text-emerald-900 break-all bg-emerald-50 px-2 py-0.5 rounded border border-emerald-100">
                {proof.merkleRoot}
              </span>
            </div>
          )}
          {proof.coveredStorageLayers && (
            <div className="flex flex-col sm:flex-row sm:items-center justify-between text-xs gap-1">
              <span className="text-slate-500 flex items-center gap-1 font-medium">
                <Layers className="h-3.5 w-3.5 text-emerald-600 shrink-0" /> Verified Storage Layers Shredded
              </span>
              <div className="flex flex-wrap gap-1">
                {proof.coveredStorageLayers.map((layer) => (
                  <span
                    key={layer}
                    className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 font-mono text-[10px] font-semibold border border-slate-200"
                  >
                    {layer}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {/* RSA Digital Signature */}
      {proof.digitalSignature && (
        <div className="mt-4">
          <p className="text-xs text-slate-600 font-medium mb-1.5 flex items-center gap-1">
            <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" /> RSA Digital Signature (Base64)
          </p>
          <div className="rounded-xl bg-white border border-emerald-200 p-3 shadow-2xs">
            <p className="text-[11px] font-mono text-emerald-900 break-all leading-relaxed select-all">
              {proof.digitalSignature}
            </p>
          </div>
        </div>
      )}

      {/* Audit trail */}
      <div className="mt-4">
        <p className="text-xs text-slate-600 font-medium mb-1.5 flex items-center gap-1">
          <FileText className="h-3.5 w-3.5 text-emerald-600" /> Canonical Audit Trail Payload
        </p>
        <div className="rounded-xl bg-white border border-slate-200 p-3 shadow-2xs">
          <pre className="text-xs font-mono text-slate-700 whitespace-pre-wrap break-all leading-relaxed select-all">
            {proof.auditTrail}
          </pre>
        </div>
      </div>
    </div>
  );
}
