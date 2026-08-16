import { useState, useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X, FileText, Download, ShieldCheck, ShieldAlert, Lock } from 'lucide-react';
import type { PatientAttachment } from '../types';
import apiClient from '../lib/axios';

interface Props {
  recordId: string;
  attachment: PatientAttachment;
  onClose: () => void;
}

export default function PdfViewerModal({ recordId, attachment, onClose }: Props) {
  const [fileUrl, setFileUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let objectUrl: string | null = null;

    if (attachment.shredded) {
      setIsLoading(false);
      setError('This document payload has been permanently crypto-shredded.');
      return;
    }

    setIsLoading(true);
    apiClient
      .get(`/records/${recordId}/attachments/${attachment.id}`, { responseType: 'blob' })
      .then((res) => {
        const blob = new Blob([res.data], { type: attachment.contentType || 'application/pdf' });
        objectUrl = URL.createObjectURL(blob);
        setFileUrl(objectUrl);
      })
      .catch((err) => {
        const msg =
          err.response?.status === 410 || err.response?.data?.message
            ? 'Document payload has been crypto-shredded and is unreadable.'
            : 'Failed to decrypt and load PDF attachment.';
        setError(msg);
      })
      .finally(() => setIsLoading(false));

    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [recordId, attachment]);

  const formatSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  return createPortal(
    <div
      className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 p-4"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="w-full max-w-4xl h-[85vh] flex flex-col overflow-hidden animate-slide-up bg-white border border-slate-200 shadow-2xl rounded-2xl">
        {/* Modal Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200 bg-slate-50/80">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 ring-1 ring-blue-200">
              <FileText className="h-5 w-5 text-blue-600" />
            </div>
            <div>
              <h3 className="font-semibold text-slate-900 text-base flex items-center gap-2">
                {attachment.fileName}
                {attachment.shredded ? (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-red-50 text-red-700 border border-red-200">
                    SHREDDED
                  </span>
                ) : (
                  <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                    AES-256 DECRYPTED
                  </span>
                )}
              </h3>
              <p className="text-xs text-slate-500">
                {formatSize(attachment.fileSize)} • Uploaded {new Date(attachment.createdAt).toLocaleDateString()}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {fileUrl && !attachment.shredded && (
              <a
                href={fileUrl}
                download={attachment.fileName}
                className="px-3 py-1.5 rounded-xl border border-slate-300 hover:bg-slate-100 text-slate-700 text-xs font-medium flex items-center gap-1.5 transition"
              >
                <Download className="h-4 w-4" /> Download
              </a>
            )}
            <button onClick={onClose} className="p-2 rounded-xl text-slate-400 hover:text-slate-700 hover:bg-slate-100 transition">
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Security Banner */}
        <div className="px-6 py-2.5 bg-slate-50 border-b border-slate-200 flex items-center justify-between text-xs">
          <div className="flex items-center gap-2 text-slate-600">
            <Lock className="h-3.5 w-3.5 text-blue-600" />
            <span>End-to-End Key Protected Document Payload</span>
          </div>
          {attachment.shredded ? (
            <span className="text-red-700 flex items-center gap-1 font-mono">
              <ShieldAlert className="h-3.5 w-3.5 text-red-600" /> Key Material Nullified
            </span>
          ) : (
            <span className="text-emerald-700 flex items-center gap-1">
              <ShieldCheck className="h-3.5 w-3.5 text-emerald-600" /> Key Validated
            </span>
          )}
        </div>

        {/* Body Viewer Content */}
        <div className="flex-1 bg-slate-100 relative flex items-center justify-center p-3">
          {isLoading && (
            <div className="flex flex-col items-center gap-3">
              <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
              <p className="text-sm text-slate-600 font-mono">Decrypting payload from storage...</p>
            </div>
          )}

          {error && (
            <div className="flex flex-col items-center gap-3 text-center max-w-md p-6 bg-white border border-red-200 rounded-2xl shadow-lg">
              <ShieldAlert className="h-12 w-12 text-red-500" />
              <h4 className="text-lg font-semibold text-slate-900">Access Unavailable</h4>
              <p className="text-sm text-red-700">{error}</p>
              <div className="mt-2 text-xs text-slate-600 font-mono bg-slate-50 p-3 rounded-xl w-full text-left border border-slate-200">
                ERR_KEY_DESTROYED: Encryption key associated with this file object was invalidated.
              </div>
            </div>
          )}

          {fileUrl && !isLoading && !error && (
            <iframe
              src={fileUrl}
              title={attachment.fileName}
              className="w-full h-full rounded-xl border border-slate-200 bg-white"
            />
          )}
        </div>
      </div>
    </div>,
    document.body
  );
}
