import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import { ToastProvider } from '../../components/Toasts'
import { api, tokenStore } from '../../lib/api'
import MapPage from '../MapPage'

// No RealtimeProvider here: subscriptions become no-ops, which is what an anonymous
// visitor with a dropped socket would see.

const zones = [{ code: 'A', name: 'Khu A - Tự học yên tĩnh', floorNumber: 2, quietZone: true, totalSeats: 3 }]
const seat = (code, x, status, extra = {}) => ({
  seatCode: code,
  zoneCode: 'A',
  seatType: 'SINGLE',
  hasPowerOutlet: x % 2 === 0,
  quietZone: true,
  coordX: x,
  coordY: 0,
  status,
  physicalOccupied: status === 'OCCUPIED',
  deviceOnline: true,
  simulatedDevice: code !== 'A-01',
  deviceId: code,
  updatedAt: '2026-11-05T08:00:00Z',
  ...extra,
})
const seats = [seat('A-01', 0, 'AVAILABLE'), seat('A-02', 1, 'OCCUPIED'), seat('A-03', 2, 'AWAY', { deviceOnline: false })]

function renderPage() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ToastProvider>
          <MapPage />
        </ToastProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('MapPage', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.spyOn(api, 'zones').mockResolvedValue(zones)
    vi.spyOn(api, 'seats').mockResolvedValue(seats)
  })
  afterEach(() => vi.restoreAllMocks())

  it('renders the live map for an anonymous visitor', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Khu A - Tự học yên tĩnh' })).toBeInTheDocument()
    expect(screen.getByText(/1 trên 3 chỗ đang trống/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /A-03: Tạm vắng, cảm biến mất kết nối/ })).toHaveClass('offline')

    await userEvent.click(screen.getByRole('button', { name: /^Ghế A-01/ }))
    const panel = screen.getByRole('region', { name: 'Chi tiết ghế A-01' })
    expect(within(panel).getByText('ESP32 thật, đang hoạt động')).toBeInTheDocument()
    expect(within(panel).getByRole('link', { name: 'Đăng nhập để đặt ghế' })).toBeInTheDocument()
  })

  it('lets a signed-in student book an available seat', async () => {
    tokenStore.set('jwt')
    vi.spyOn(api, 'me').mockResolvedValue({
      id: 'u1',
      studentCode: 'SV001',
      fullName: 'Nguyễn Văn An',
      role: 'STUDENT',
      creditScore: 100,
      canBook: true,
    })
    vi.spyOn(api, 'activeReservation').mockResolvedValue(null)
    vi.spyOn(api, 'reserve').mockResolvedValue({
      id: 'r1',
      seatCode: 'A-01',
      zoneCode: 'A',
      status: 'PENDING',
      bookingSource: 'APP_MAP',
      reservedAt: new Date().toISOString(),
      expireAt: new Date(Date.now() + 60_000).toISOString(),
    })

    renderPage()
    await userEvent.click(await screen.findByRole('button', { name: /^Ghế A-01/ }))
    await userEvent.click(await screen.findByRole('button', { name: 'Đặt ghế A-01' }))

    expect(api.reserve).toHaveBeenCalledWith('A-01')
    // The reservation card replaces the booking button.
    expect(await screen.findByRole('region', { name: 'Lượt đặt chỗ hiện tại' })).toBeInTheDocument()
    expect(screen.getByText('Đây là ghế bạn đang giữ.')).toBeInTheDocument()
  })

  it('dims seats that do not match the filters', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Khu A - Tự học yên tĩnh' })
    await userEvent.click(screen.getByText('Chỉ ghế trống'))
    expect(screen.getByRole('button', { name: /^Ghế A-02/ })).toHaveClass('dimmed')
    expect(screen.getByRole('button', { name: /^Ghế A-01/ })).not.toHaveClass('dimmed')
  })
})
