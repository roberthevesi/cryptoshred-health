import React, { useState, useRef } from 'react';
import { X, Upload, CheckCircle2, XCircle, ShieldCheck, AlertTriangle, FileCode, FileText, ArrowDownToLine } from 'lucide-react';
import type { ProofVerificationResponse, DeletionProof } from '../types';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  token: string;
}

export default function VerifyProofModal({ isOpen, onClose, token }: Props) {
  const [jsonInput, setJsonInput] = useState('');
  const [loadedFileName, setLoadedFileName] = useState<string | null>(null);
  const [fileSize, setFileSize] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ProofVerificationResponse | null>(null);

  const fileInputRef = useRef<HTMLInputElement>(null);

  if (!isOpen) return null;

  const processFile = (file: File) => {
    if (!file) return;
    setLoadedFileName(file.name);
    setFileSize((file.size / 1024).toFixed(1) + ' KB');

    const reader = new FileReader();
    reader.onload = (event) => {
      if (event.target?.result) {
        const raw = event.target.result as string;
        try {
          // Prettify if valid JSON
          const parsed = JSON.parse(raw);
          setJsonInput(JSON.stringify(parsed, null, 2));
        } catch {
          setJsonInput(raw);
        }
        setError(null);
        setResult(null);
      }
    };
    reader.onerror = () => {
      setError('Failed to read the dropped file.');
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
    // Only set dragging to false if we're leaving the drop container
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
        setLoadedFileName('pasted-proof.json');
      } catch {
        setJsonInput(textData);
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
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
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
    } catch {
      setError('Invalid JSON format. Please ensure the payload is well-formed JSON.');
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
              <p className="text-xs text-slate-500">Validate RSA-2048 digital signatures, SHA-256 integrity, and Merkle tree inclusion</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg text-slate-400 hover:bg-slate-100 hover:text-slate-700 transition"
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
              : 'border-slate-200 bg-slate-50/50 hover:bg-slate-50'
          }`}
        >
          {/* Drag Overlay Hint */}
          {isDragging && (
            <div className="absolute inset-0 z-10 flex flex-col items-center justify-center rounded-2xl bg-emerald-50/90 backdrop-blur-xs border-2 border-emerald-500 border-dashed text-emerald-700">
              <ArrowDownToLine className="h-10 w-10 animate-bounce mb-2" />
              <p className="text-sm font-bold">Drop JSON proof artifact here</p>
              <p className="text-xs text-emerald-600">Release file to load cryptographic proof</p>
            </div>
          )}

          {/* Top Control Bar */}
          <div className="flex items-center justify-between text-xs text-slate-700">
            <label className="font-medium flex items-center gap-1.5">
              <FileCode className="h-4 w-4 text-emerald-600" />
              <span>Proof Artifact Payload</span>
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
                  className="text-xs text-slate-400 hover:text-rose-600 transition px-1.5 py-0.5"
                  title="Clear loaded file"
                >
                  Clear
                </button>
              )}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                className="flex items-center gap-1.5 px-3 py-1 rounded-lg bg-white hover:bg-slate-100 text-xs font-medium text-emerald-700 transition border border-slate-300 shadow-2xs cursor-pointer"
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
            }}
            placeholder='Drag & drop a .json proof file here, browse from your computer, or paste the raw JSON payload...'
            rows={7}
            className="w-full rounded-xl bg-white border border-slate-300 p-3 text-xs font-mono text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:border-transparent transition-all shadow-inner"
          />

          <div className="flex items-center justify-between text-[11px] text-slate-400 px-1">
            <span>Supports .json deletion proof artifacts from CryptoShred Health</span>
            <span>{jsonInput ? `${jsonInput.length} characters` : 'Drop zone active'}</span>
          </div>
        </div>

        {error && (
          <div className="flex items-center gap-2 p-3 rounded-xl bg-red-50 border border-red-200 text-xs text-red-700 animate-fade-in">
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
            disabled={loading || !jsonInput.trim()}
            className="flex items-center gap-2 px-5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium transition shadow-sm disabled:opacity-50"
          >
            {loading ? (
              <>
                <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                <span>Verifying Proof...</span>
              </>
            ) : (
              <span>Verify Cryptographic Signature</span>
            )}
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

