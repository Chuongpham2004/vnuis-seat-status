import FaceCheck from './FaceCheck'

/** Dialog running the face check before a booking. */
export default function FaceVerifyModal({ seatCode, onVerified, onClose }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--ink-primary)]/40 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="face-verify-title"
    >
      <div className="card w-full max-w-sm space-y-3 p-5">
        <div>
          <h2 id="face-verify-title" className="text-lg font-bold">
            Xác thực khuôn mặt
          </h2>
          <p className="text-sm secondary">Xác nhận đúng là bạn trước khi giữ ghế {seatCode}.</p>
        </div>
        <FaceCheck mode="verify" onSuccess={onVerified} onCancel={onClose} />
      </div>
    </div>
  )
}
