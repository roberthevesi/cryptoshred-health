import React, { useState } from 'react';
import { X, Upload, CheckCircle2, XCircle, ShieldCheck, AlertTriangle, FileCode } from 'lucide-react';
import type { ProofVerificationResponse, DeletionProof } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  token: string;
}

export default function VerifyProofModal({ isOpen, onClose, token }: Props) {
  const [jsonInput, setJsonInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ProofVerificationResponse | null>(null);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      if (event.target?.result) {
        setJsonInput(event.target.result as string);
        setError(null);
      }
    };
    reader.readAsText(file);
  };

  const handleVerify = async () => {
    setError(null);
    setResult(null);

    if (!jsonInput.trim()) {
      setError('Please paste a JSON proof artifact or upload a .json file');
      return;
    }

    let parsedArtifact: DeletionProof;
    try {
      parsedArtifact = JSON.parse(jsonInput);
    } catch {
      setError('Invalid JSON format. Please ensure the file contains valid JSON.');
      return;
    }

    setLoading(true);

    try {
      const response = await fetch('/api/erasure/verify-proof', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ proofArtifact: parsedArtifact }),
      });

      if (!response.ok) {
        throw new Error(`Server returned HTTP ${response.status}`);
      }

      const data: ProofVerificationResponse = await response.json();
      setResult(data);
    } catch (err: any) {
      setError(err.message || 'Failed to verify proof artifact');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4 animate-fade-in">
      <div className="relative w-full max-w-2xl rounded-2xl border border-slate-700 bg-slate-900 p-6 shadow-2xl space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-900/40 border border-emerald-700/50">
              <ShieldCheck className="h-5 w-5 text-emerald-400" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-white">Verify Cryptographic Proof Artifact</h2>
              <p className="text-xs text-slate-400">Validate RSA signatures, SHA-256 payload integrity, and Merkle tree inclusion</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-800 hover:text-white transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Input Area */}
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs text-slate-300">
            <label className="font-medium flex items-center gap-1">
              <FileCode className="h-4 w-4 text-emerald-400" /> JSON Proof Artifact Payload
            </label>
            <label className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-medium text-emerald-400 cursor-pointer transition border border-slate-700">
              <Upload className="h-3.5 w-3.5" />
              Upload .json File
              <input type="file" accept=".json" onChange={handleFileUpload} className="hidden" />
            </label>
          </div>

          <textarea
            value={jsonInput}
            onChange={(e) => { setJsonInput(e.target.value); setError(null); }}
            placeholder='Paste JSON proof artifact here...'
            rows={8}
            className="w-full rounded-xl bg-slate-950 border border-slate-800 p-3 text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-2 focus:ring-emerald-500/50"
          />
        </div>

        {error && (
          <div className="flex items-center gap-2 p-3 rounded-xl bg-rose-950/40 border border-rose-800/40 text-xs text-rose-300">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-medium transition"
          >
            Cancel
          </button>
          <button
            onClick={handleVerify}
            disabled={loading}
            className="flex items-center gap-2 px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition disabled:opacity-50"
          >
            {loading ? 'Verifying Proof...' : 'Verify Cryptographic Signature'}
          </button>
        </div>

        {/* Verification Result Card */}
        {result && (
          <div className={`p-4 rounded-xl border animate-slide-up space-y-3 ${
            result.valid
              ? 'bg-emerald-950/40 border-emerald-800/60 text-emerald-200'
              : 'bg-rose-950/40 border-rose-800/60 text-rose-200'
          }`}>
            <div className="flex items-center gap-2 font-semibold text-sm">
              {result.valid ? (
                <>
                  <CheckCircle2 className="h-5 w-5 text-emerald-400" />
                  Proof Artifact Cryptographically Validated
                </>
              ) : (
                <>
                  <XCircle className="h-5 w-5 text-rose-400" />
                  Proof Artifact Verification Failed
                </>
              )}
            </div>

            <p className="text-xs opacity-90 leading-relaxed">{result.verificationMessage}</p>

            <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-800/60 text-xs">
              <div className="flex items-center gap-1.5">
                {result.signatureValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-rose-400" />
                )}
                <span>RSA Signature</span>
              </div>
              <div className="flex items-center gap-1.5">
                {result.payloadIntegrityValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-rose-400" />
                )}
                <span>Payload SHA-256</span>
              </div>
              <div className="flex items-center gap-1.5">
                {result.merkleInclusionValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-rose-400" />
                )}
                <span>Merkle Path</span>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
