import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AuthProvider } from '../../auth/AuthContext'
import { ApiError, api } from '../../lib/api'
import ReservationCard from '../ReservationCard'
import { ToastProvider } from '../Toasts'

function renderCard(props) {
  return render(
    <AuthProvider>
      <ToastProvider>
        <ReservationCard {...props} />
      </ToastProvider>
    </AuthProvider>,
  )
}

const pending = {
  id: 'r-1',
  seatCode: 'B-07',
  zoneCode: 'B',
  status: 'PENDING',
  bookingSource: 'AI_CHATBOT',
  reservedAt: new Date().toISOString(),
  expireAt: new Date(Date.now() + 90_000).toISOString(),
}

describe('ReservationCard', () => {
  afterEach(() => vi.restoreAllMocks())

  it('shows the countdown for a pending booking', () => {
    renderCard({ reservation: pending })
    expect(screen.getByText('B-07')).toBeInTheDocument()
    expect(screen.getByText(/Đặt qua trợ lý AI/)).toBeInTheDocument()
    expect(screen.getByText(/^01:(29|30)$/)).toBeInTheDocument()
  })

  it('asks for the QR code before calling the API', async () => {
    const spy = vi.spyOn(api, 'checkIn')
    renderCard({ reservation: pending })
    await userEvent.click(screen.getByRole('button', { name: 'Check-in' }))
    expect(spy).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(/quét hoặc nhập mã QR/i)
  })

  it('explains a missing sensor presence in Vietnamese', async () => {
    vi.spyOn(api, 'checkIn').mockRejectedValue(new ApiError(409, 'NO_PHYSICAL_PRESENCE', 'No presence'))
    renderCard({ reservation: pending })
    await userEvent.type(screen.getByLabelText('Mã QR trên bàn'), 'abc123')
    await userEvent.click(screen.getByRole('button', { name: 'Check-in' }))
    expect(api.checkIn).toHaveBeenCalledWith('r-1', 'abc123')
    expect(await screen.findByRole('alert')).toHaveTextContent('Cảm biến chưa phát hiện bạn ngồi vào ghế')
  })

  it('offers check-out for an active booking', async () => {
    const onChange = vi.fn()
    vi.spyOn(api, 'checkOut').mockResolvedValue({ ...pending, status: 'COMPLETED' })
    renderCard({ reservation: { ...pending, status: 'ACTIVE', checkInAt: new Date().toISOString() }, onChange })
    expect(screen.queryByRole('button', { name: 'Huỷ đặt chỗ' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Trả chỗ/ }))
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ status: 'COMPLETED' }))
  })
})
