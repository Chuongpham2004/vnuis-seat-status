import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import FaceVerifyModal from '../components/FaceVerifyModal'
import { api } from '../lib/api'

/**
 * Booking behind the face check, shared by the map and the assistant.
 *
 *   const { start, modal } = useFaceBooking({ onBooked, onError })
 *   start('A-05', 'APP_MAP')   // render {modal} somewhere in the component
 *
 * Face check off -> books right away; not enrolled -> /face/enroll; else modal -> book with the token.
 */
export function useFaceBooking({ onBooked, onError }) {
  const navigate = useNavigate()
  const [pending, setPending] = useState(null) // { seatCode, source }
  const [busy, setBusy] = useState(false)

  const book = async (seatCode, source, faceToken) => {
    setBusy(true)
    try {
      onBooked?.(await api.reserve(seatCode, source, faceToken))
    } catch (e) {
      onError?.(e)
    } finally {
      setBusy(false)
    }
  }

  const start = async (seatCode, source = 'APP_MAP') => {
    setBusy(true)
    let status
    try {
      status = await api.faceStatus()
    } catch (e) {
      setBusy(false)
      onError?.(e)
      return
    }
    setBusy(false)
    if (!status.requiredForBooking) return book(seatCode, source)
    if (!status.enrolled) {
      navigate(`/face/enroll?seat=${encodeURIComponent(seatCode)}`)
      return
    }
    setPending({ seatCode, source })
  }

  const modal = pending && (
    <FaceVerifyModal
      seatCode={pending.seatCode}
      onClose={() => setPending(null)}
      onVerified={({ faceToken }) => {
        const { seatCode, source } = pending
        setPending(null)
        book(seatCode, source, faceToken)
      }}
    />
  )

  return { start, busy, modal }
}
