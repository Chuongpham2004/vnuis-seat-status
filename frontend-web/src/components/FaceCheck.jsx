import { useState } from 'react'
import { api } from '../lib/api'
import { errorMessage } from '../lib/errors'
import FaceCapture from './FaceCapture'

/**
 * One full face check: challenge -> camera -> enrol or verify.
 * mode "verify" resolves with { faceToken, score, expiresAt }; "enroll" with the face status.
 */
export default function FaceCheck({ mode = 'verify', consent = true, onSuccess, onCancel }) {
  const [phase, setPhase] = useState('idle') // idle | capture | checking | error
  const [challenge, setChallenge] = useState(null)
  const [error, setError] = useState(null)

  const start = async () => {
    setError(null)
    setPhase('checking')
    try {
      setChallenge(await api.faceChallenge())
      setPhase('capture')
    } catch (e) {
      setError(errorMessage(e))
      setPhase('error')
    }
  }

  const submit = async (frames) => {
    setPhase('checking')
    try {
      const result =
        mode === 'enroll'
          ? await api.faceEnroll(challenge.challengeId, frames, consent)
          : await api.faceVerify(challenge.challengeId, frames)
      onSuccess(result)
    } catch (e) {
      setError(errorMessage(e))
      setPhase(e.code === 'FACE_VERIFY_LOCKED' || e.code === 'FACE_ALREADY_ENROLLED' ? 'locked' : 'error')
    }
  }

  if (phase === 'capture') {
    return <FaceCapture steps={challenge.steps} onDone={submit} onCancel={onCancel} />
  }
  return (
    <div className="space-y-3">
      {phase === 'checking' && (
        <p className="text-sm secondary" role="status">
          {challenge ? 'Đang nhận dạng khuôn mặt…' : 'Đang chuẩn bị…'}
        </p>
      )}
      {error && (
        <p className="text-sm text-[var(--critical)]" role="alert">
          {error}
        </p>
      )}
      {(phase === 'idle' || phase === 'error') && (
        <button className="btn btn-primary w-full" onClick={start} disabled={!consent}>
          {phase === 'error' ? 'Thử lại' : mode === 'enroll' ? 'Mở camera để đăng ký' : 'Mở camera để xác thực'}
        </button>
      )}
      {onCancel && phase !== 'checking' && (
        <button className="btn btn-sm w-full" onClick={onCancel}>
          Huỷ
        </button>
      )}
    </div>
  )
}
