import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import { Layout } from './components/Layout'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import FeedPage from './pages/FeedPage'
import NetworkPage from './pages/NetworkPage'
import ProfilePage from './pages/ProfilePage'
import SearchPage from './pages/SearchPage'
import NotificationsPage from './pages/NotificationsPage'
import type { ReactElement } from 'react'

function Protected({ children }: { children: ReactElement }) {
  const { session } = useAuth()
  return session ? children : <Navigate to="/login" replace />
}
function PublicOnly({ children }: { children: ReactElement }) {
  const { session } = useAuth()
  return session ? <Navigate to="/" replace /> : children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<PublicOnly><LoginPage /></PublicOnly>} />
      <Route path="/register" element={<PublicOnly><RegisterPage /></PublicOnly>} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route path="/" element={<FeedPage />} />
        <Route path="/network" element={<NetworkPage />} />
        <Route path="/profile/:userId" element={<ProfilePage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/notifications" element={<NotificationsPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
