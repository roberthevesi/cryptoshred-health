import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';
import {
  AlertTriangle,
  Trash2,
  ShieldAlert,
  Info,
  X,
  Check,
  ShieldOff,
} from 'lucide-react';

export type ConfirmationVariant = 'danger' | 'warning' | 'info' | 'shred';

interface ConfirmationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: React.ReactNode;
  detail?: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: ConfirmationVariant;
  isLoading?: boolean;
  children?: React.ReactNode;
}

export default function ConfirmationModal({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  detail,
  confirmLabel = 'Confirm Action',
  cancelLabel = 'Cancel',
  variant = 'danger',
  isLoading = false,
  children,
}: ConfirmationModalProps) {
  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isOpen && !isLoading) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, isLoading, onClose]);

  if (!isOpen) return null;

  const variantStyles = {
    danger: {
      iconBg: 'bg-rose-50 border-rose-200 text-rose-600',
      icon: <Trash2 className="h-6 w-6" />,
      button: 'bg-rose-600 hover:bg-rose-500 active:bg-rose-700 text-white shadow-sm',
      bannerBg: 'bg-rose-50/70 border-rose-200 text-rose-900',
    },
    shred: {
      iconBg: 'bg-red-100 border-red-300 text-red-700',
      icon: <ShieldOff className="h-6 w-6" />,
      button: 'bg-red-700 hover:bg-red-600 active:bg-red-800 text-white shadow-sm',
      bannerBg: 'bg-red-50 border-red-200 text-red-950',
    },
    warning: {
      iconBg: 'bg-amber-50 border-amber-200 text-amber-600',
      icon: <AlertTriangle className="h-6 w-6" />,
      button: 'bg-amber-600 hover:bg-amber-500 active:bg-amber-700 text-white shadow-sm',
      bannerBg: 'bg-amber-50/70 border-amber-200 text-amber-900',
    },
    info: {
      iconBg: 'bg-blue-50 border-blue-200 text-blue-600',
      icon: <Info className="h-6 w-6" />,
      button: 'bg-blue-600 hover:bg-blue-500 active:bg-blue-700 text-white shadow-sm',
      bannerBg: 'bg-blue-50/70 border-blue-200 text-blue-900',
    },
  }[variant];

  return createPortal(
    <div className="fixed inset-0 z-[100] !m-0 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white border border-slate-200 rounded-2xl shadow-xl max-w-md w-full overflow-hidden animate-fade-in">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
          <div className="flex items-center gap-3">
            <div
              className={`flex h-10 w-10 items-center justify-center rounded-xl border ${variantStyles.iconBg}`}
            >
              {variantStyles.icon}
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-900">{title}</h2>
              <p className="text-xs text-slate-500">Confirmation Required</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="p-2 rounded-xl hover:bg-slate-100 text-slate-400 hover:text-slate-600 transition-colors disabled:opacity-50"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-4">
          <div className="text-sm text-slate-700 leading-relaxed">{message}</div>

          {detail && (
            <div
              className={`rounded-xl border p-3.5 text-xs font-mono break-words ${variantStyles.bannerBg}`}
            >
              {detail}
            </div>
          )}

          {variant === 'shred' && (
            <div className="flex items-start gap-2.5 rounded-xl bg-red-50 border border-red-200 p-3 text-xs text-red-800">
              <ShieldAlert className="h-4 w-4 text-red-600 shrink-0 mt-0.5" />
              <span>
                <strong>Warning:</strong> Cryptographic erasure destroys the Vault KMS Transit encryption key. All encrypted records will become permanently and mathematically unrecoverable.
              </span>
            </div>
          )}

          {children}
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-slate-200 bg-slate-50 flex items-center justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            disabled={isLoading}
            className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-colors disabled:opacity-50"
          >
            {cancelLabel}
          </button>

          <button
            type="button"
            onClick={() => {
              onConfirm();
              onClose();
            }}
            disabled={isLoading}
            className={`inline-flex items-center gap-1.5 px-4 py-2 rounded-xl text-xs font-semibold transition-colors disabled:opacity-50 ${variantStyles.button}`}
          >
            {isLoading ? (
              <>
                <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Processing...
              </>
            ) : (
              <>
                <Check className="h-3.5 w-3.5" />
                {confirmLabel}
              </>
            )}
          </button>
        </div>
      </div>
    </div>,
    document.body
  );
}
