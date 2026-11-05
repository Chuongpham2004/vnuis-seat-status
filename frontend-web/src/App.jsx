import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import RequireAuth from './components/RequireAuth'
import AdminAi from './pages/admin/AdminAi'
import AdminDashboard from './pages/admin/AdminDashboard'
import AdminDevices from './pages/admin/AdminDevices'
import AdminQr from './pages/admin/AdminQr'
import AdminUsers from './pages/admin/AdminUsers'
import AssistantPage from './pages/AssistantPage'
import ForecastPage from './pages/ForecastPage'
import LoginPage from './pages/LoginPage'
import MapPage from './pages/MapPage'
import MyReservationsPage from './pages/MyReservationsPage'
import NotFoundPage from './pages/NotFoundPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<MapPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="forecast" element={<ForecastPage />} />
        <Route path="me" element={<RequireAuth><MyReservationsPage /></RequireAuth>} />
        <Route path="assistant" element={<RequireAuth><AssistantPage /></RequireAuth>} />
        <Route path="admin" element={<RequireAuth admin><AdminDashboard /></RequireAuth>} />
        <Route path="admin/devices" element={<RequireAuth admin><AdminDevices /></RequireAuth>} />
        <Route path="admin/users" element={<RequireAuth admin><AdminUsers /></RequireAuth>} />
        <Route path="admin/qr" element={<RequireAuth admin><AdminQr /></RequireAuth>} />
        <Route path="admin/ai" element={<RequireAuth admin><AdminAi /></RequireAuth>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
