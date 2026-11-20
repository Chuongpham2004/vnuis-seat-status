import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../../lib/api'
import { useFaceBooking } from '../useFaceBooking'

// The camera is replaced by a capture that "records" one frame per step immediately.
vi.mock('../../components/FaceCapture', () => ({
  default: ({ steps, onDone }) => (
    <button onClick={() => onDone(steps.map((step) => ({ step, image: 'ZmFrZQ==' })))}>Bắt đầu</button>
  ),
}))

function Harness({ onBooked, onError }) {
  const { start, modal } = useFaceBooking({ onBooked, onError })
  return (
    <>
      <button onClick={() => start('A-05', 'APP_MAP')}>Đặt ghế A-05</button>
      {modal}
    </>
  )
}

function renderHarness(props = {}) {
  const onBooked = vi.fn()
  const onError = vi.fn()
  render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<Harness onBooked={onBooked} onError={onError} {...props} />} />
        <Route path="/face/enroll" element={<p>Trang đăng ký khuôn mặt</p>} />
      </Routes>
    </MemoryRouter>,
  )
  return { onBooked, onError }
}

const reservation = { id: 'r1', seatCode: 'A-05', status: 'PENDING' }

describe('useFaceBooking', () => {
  beforeEach(() => {
    vi.spyOn(api, 'reserve').mockResolvedValue(reservation)
    vi.spyOn(api, 'faceChallenge').mockResolvedValue({ challengeId: 'c1', steps: ['CENTER', 'LEFT', 'RIGHT'] })
  })
  afterEach(() => vi.restoreAllMocks())

  it('books directly when the face check is switched off', async () => {
    vi.spyOn(api, 'faceStatus').mockResolvedValue({ enrolled: false, requiredForBooking: false })
    const { onBooked } = renderHarness()
    await userEvent.click(screen.getByText('Đặt ghế A-05'))
    await waitFor(() => expect(onBooked).toHaveBeenCalledWith(reservation))
    expect(api.reserve).toHaveBeenCalledWith('A-05', 'APP_MAP', undefined)
  })

  it('sends a student without an enrolled face to the enrolment page', async () => {
    vi.spyOn(api, 'faceStatus').mockResolvedValue({ enrolled: false, requiredForBooking: true })
    renderHarness()
    await userEvent.click(screen.getByText('Đặt ghế A-05'))
    expect(await screen.findByText('Trang đăng ký khuôn mặt')).toBeInTheDocument()
    expect(api.reserve).not.toHaveBeenCalled()
  })

  it('verifies the face, then books with the one-time token', async () => {
    vi.spyOn(api, 'faceStatus').mockResolvedValue({ enrolled: true, requiredForBooking: true })
    vi.spyOn(api, 'faceVerify').mockResolvedValue({ faceToken: 'tok-1', score: 0.83 })
    const { onBooked } = renderHarness()
    await userEvent.click(screen.getByText('Đặt ghế A-05'))
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
    await userEvent.click(screen.getByText('Mở camera để xác thực'))
    await userEvent.click(await screen.findByText('Bắt đầu'))
    await waitFor(() => expect(onBooked).toHaveBeenCalledWith(reservation))
    expect(api.faceVerify).toHaveBeenCalledWith('c1', [
      { step: 'CENTER', image: 'ZmFrZQ==' },
      { step: 'LEFT', image: 'ZmFrZQ==' },
      { step: 'RIGHT', image: 'ZmFrZQ==' },
    ])
    expect(api.reserve).toHaveBeenCalledWith('A-05', 'APP_MAP', 'tok-1')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('shows the reason and offers a retry when the face does not match', async () => {
    vi.spyOn(api, 'faceStatus').mockResolvedValue({ enrolled: true, requiredForBooking: true })
    vi.spyOn(api, 'faceVerify').mockRejectedValue(Object.assign(new Error('x'), { code: 'FACE_NOT_MATCHED' }))
    renderHarness()
    await userEvent.click(screen.getByText('Đặt ghế A-05'))
    await userEvent.click(await screen.findByText('Mở camera để xác thực'))
    await userEvent.click(await screen.findByText('Bắt đầu'))
    expect(await screen.findByRole('alert')).toHaveTextContent('Khuôn mặt không khớp')
    expect(screen.getByText('Thử lại')).toBeInTheDocument()
    expect(api.reserve).not.toHaveBeenCalled()
  })
})
