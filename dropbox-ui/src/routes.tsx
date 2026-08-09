import { Navigate, Route, Routes } from 'react-router-dom'
import AppShell from '@/components/layout/AppShell'
import RequireAuth from '@/components/auth/RequireAuth'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import MyFilesPage from '@/pages/MyFilesPage'
import RecentPage from '@/pages/RecentPage'
import PhotosPage from '@/pages/PhotosPage'
import VideosPage from '@/pages/VideosPage'
import TrashPage from '@/pages/TrashPage'
import PublicSharePage from '@/pages/PublicSharePage'
import NotFoundPage from '@/pages/NotFoundPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/s/:token" element={<PublicSharePage />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/" element={<Navigate to="/files" replace />} />
          <Route path="/files" element={<MyFilesPage />} />
          <Route path="/files/:folderId" element={<MyFilesPage />} />
          <Route path="/recent" element={<RecentPage />} />
          <Route path="/photos" element={<PhotosPage />} />
          <Route path="/videos" element={<VideosPage />} />
          <Route path="/trash" element={<TrashPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
