import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import SeatTile from '../SeatTile'

const base = {
  seatCode: 'A-01',
  zoneCode: 'A',
  status: 'AVAILABLE',
  deviceOnline: true,
  simulatedDevice: false,
  hasPowerOutlet: true,
  seatType: 'SINGLE',
}

describe('SeatTile', () => {
  it('labels the state with text, not colour alone', () => {
    render(<SeatTile seat={base} />)
    const btn = screen.getByRole('button')
    expect(btn).toHaveAccessibleName(/A-01: Còn trống.*có ổ điện.*cảm biến thật ESP32/)
    expect(btn).toHaveClass('status-available')
    expect(btn).not.toHaveClass('offline')
  })

  it("marks an offline sensor and the student's own seat", () => {
    render(<SeatTile seat={{ ...base, status: 'RESERVED', deviceOnline: false }} mine />)
    const btn = screen.getByRole('button')
    expect(btn).toHaveClass('offline')
    expect(btn).toHaveAccessibleName(/Đã đặt.*mất kết nối.*ghế của bạn/)
    expect(btn).toHaveTextContent('Của bạn')
  })

  it('reports clicks', async () => {
    const onSelect = vi.fn()
    render(<SeatTile seat={base} onSelect={onSelect} />)
    await userEvent.click(screen.getByRole('button'))
    expect(onSelect).toHaveBeenCalledWith(base)
  })
})
