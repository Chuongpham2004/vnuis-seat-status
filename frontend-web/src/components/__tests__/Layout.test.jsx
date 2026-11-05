import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../../auth/AuthContext'
import Layout from '../Layout'

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<p>trang sơ đồ</p>} />
            <Route path="/forecast" element={<p>trang dự báo</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('Layout', () => {
  beforeEach(() => localStorage.clear())

  it('shows the 2026 footer', () => {
    renderAt('/')
    const footer = screen.getByRole('contentinfo')
    expect(within(footer).getByText(/© 2026 Thư viện VNU-IS/)).toBeInTheDocument()
    expect(within(footer).getByRole('link', { name: 'Dự báo' })).toHaveAttribute('href', '/forecast')
  })

  it('opens the mobile menu and closes it after navigating', async () => {
    renderAt('/')
    const toggle = screen.getByRole('button', { name: 'Menu' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await userEvent.click(toggle)
    const menu = document.getElementById('mobile-menu')
    expect(menu).toBeInTheDocument()
    expect(within(menu).getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument()

    await userEvent.click(within(menu).getByRole('link', { name: 'Dự báo' }))
    expect(screen.getByText('trang dự báo')).toBeInTheDocument()
    expect(document.getElementById('mobile-menu')).not.toBeInTheDocument()
  })

  it('hides student-only pages from anonymous visitors', () => {
    renderAt('/')
    const nav = screen.getAllByRole('navigation', { name: 'Điều hướng chính' })[0]
    expect(within(nav).queryByRole('link', { name: 'Chỗ của tôi' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Sơ đồ' })).toBeInTheDocument()
  })
})
