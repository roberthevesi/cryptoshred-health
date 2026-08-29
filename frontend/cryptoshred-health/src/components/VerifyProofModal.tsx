import ProofVerificationModal, { type ProofVerificationModalProps } from './ProofVerificationModal';

export type { ProofVerificationModalProps };

interface Props {
  isOpen: boolean;
  onClose: () => void;
  token?: string;
  proof?: ProofVerificationModalProps['proof'];
}

export default function VerifyProofModal({ isOpen, onClose, token, proof }: Props) {
  return (
    <ProofVerificationModal
      isOpen={isOpen}
      onClose={onClose}
      token={token}
      proof={proof}
    />
  );
}
