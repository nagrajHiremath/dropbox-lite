import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import { TransferTray } from '@/components/transfers/TransferTray'

export default function AppShell() {
  return (
    <div className="flex h-svh overflow-hidden">
      <Sidebar />
      <div className="flex flex-1 flex-col overflow-hidden">
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
      <TransferTray />
    </div>
  )
}
