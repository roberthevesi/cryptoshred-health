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
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to verify proof artifact';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4 animate-fade-in">
      <div className="relative w-full max-w-2xl rounded-2xl border border-slate-200 bg-white p-6 shadow-2xl space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 border border-emerald-200">
              <ShieldCheck className="h-5 w-5 text-emerald-600" />
            </div>
            <div>
              <h2 className="text-lg font-semibold text-slate-900">Verify Cryptographic Proof Artifact</h2>
              <p className="text-xs text-slate-500">Validate RSA signatures, SHA-256 payload integrity, and Merkle tree inclusion</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Input Area */}
        <div className="space-y-3">
          <div className="flex items-center justify-between text-xs text-slate-700">
            <label className="font-medium flex items-center gap-1">
              <FileCode className="h-4 w-4 text-emerald-600" /> JSON Proof Artifact Payload
            </label>
            <label className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-xs font-medium text-emerald-700 cursor-pointer transition border border-slate-200">
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
            className="w-full rounded-xl bg-white border border-slate-300 p-3 text-xs font-mono text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all"
          />
        </div>

        {error && (
          <div className="flex items-center gap-2 p-3 rounded-xl bg-red-50 border border-red-200 text-xs text-red-700">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            {error}
          </div>
        )}

        {/* Actions */}
        <div className="flex justify-end gap-3">
          <button
            onClick={onClose}
            className="px-4 py-2 rounded-xl border border-slate-300 hover:bg-slate-100 text-slate-700 text-xs font-medium transition"
          >
            Cancel
          </button>
          <button
            onClick={handleVerify}
            disabled={loading}
            className="flex items-center gap-2 px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition shadow-sm disabled:opacity-50"
          >
            {loading ? 'Verifying Proof...' : 'Verify Cryptographic Signature'}
          </button>
        </div>

        {/* Verification Result Card */}
        {result && (
          <div className={`p-4 rounded-xl border animate-slide-up space-y-3 ${
            result.valid
              ? 'bg-emerald-50 border-emerald-200 text-emerald-900'
              : 'bg-red-50 border-red-200 text-red-900'
          }`}>
            <div className="flex items-center gap-2 font-semibold text-sm">
              {result.valid ? (
                <>
                  <CheckCircle2 className="h-5 w-5 text-emerald-600" />
                  Proof Artifact Cryptographically Validated
                </>
              ) : (
                <>
                  <XCircle className="h-5 w-5 text-red-600" />
                  Proof Artifact Verification Failed
                </>
              )}
            </div>

            <p className="text-xs leading-relaxed">{result.verificationMessage}</p>

            <div className="grid grid-cols-3 gap-2 pt-2 border-t border-slate-200 text-xs">
              <div className="flex items-center gap-1.5">
                {result.signatureValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-red-600" />
                )}
                <span>RSA Signature</span>
              </div>
              <div className="flex items-center gap-1.5">
                {result.payloadIntegrityValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-red-600" />
                )}
                <span>Payload SHA-256</span>
              </div>
              <div className="flex items-center gap-1.5">
                {result.merkleInclusionValid ? (
                  <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                ) : (
                  <XCircle className="h-3.5 w-3.5 text-red-600" />
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
