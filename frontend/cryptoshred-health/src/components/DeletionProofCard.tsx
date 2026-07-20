import { CheckCircle2, Hash, Clock, User, FileText, ShieldCheck } from 'lucide-react';
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
        <Icon className="h-3.5 w-3.5" />
        {label}
      </div>
      <p className={`text-sm text-slate-200 break-all ${mono ? 'font-mono' : ''}`}>{value}</p>
    </div>
  );
}

export default function DeletionProofCard({ proof }: Props) {
  const formattedTime = new Date(proof.timestamp).toLocaleString();

  return (
    <div className="animate-slide-up rounded-2xl border border-emerald-800/50 bg-emerald-950/30 p-6">
      {/* Header */}
      <div className="flex items-center gap-3 mb-5">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-900/50">
          <ShieldCheck className="h-5 w-5 text-emerald-400" />
        </div>
        <div>
          <h3 className="font-semibold text-white">Signed Proof of Deletion</h3>
          <p className="text-xs text-emerald-400 flex items-center gap-1">
            <CheckCircle2 className="h-3 w-3" />
            GDPR Article 17 Compliant
          </p>
        </div>
        <span className="ml-auto badge bg-emerald-900/60 text-emerald-300 border border-emerald-700">
          {proof.status}
        </span>
      </div>

      {/* Divider */}
      <div className="my-4 border-t border-emerald-800/40" />

      {/* Proof fields */}
      <div className="space-y-4">
        <ProofRow icon={Clock}      label="Timestamp"         value={formattedTime} />
        <ProofRow icon={User}       label="Requested By"      value={proof.requestedBy} />
        <ProofRow icon={FileText}   label="Record ID"         value={proof.patientRecordId} mono />
        <ProofRow icon={Hash}       label="SHA-256 Proof Hash" value={proof.sha256Hash} mono />
      </div>

      {/* Audit trail */}
      <div className="mt-5">
        <p className="text-xs text-slate-500 mb-2 flex items-center gap-1">
          <FileText className="h-3.5 w-3.5" /> Audit Trail
        </p>
        <div className="rounded-xl bg-surface border border-slate-700 p-4">
          <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap break-all leading-relaxed">
            {proof.auditTrail}
          </pre>
        </div>
      </div>

      {/* Verification note */}
      <p className="mt-4 text-xs text-slate-600">
        This proof is cryptographically verifiable. The SHA-256 hash uniquely fingerprints the audit trail above.
      </p>
    </div>
  );
}
