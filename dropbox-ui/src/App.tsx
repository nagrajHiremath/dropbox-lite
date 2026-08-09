import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { AppRoutes } from './routes'

function App() {
  return (
    <TooltipProvider>
      <AppRoutes />
      <Toaster />
    </TooltipProvider>
  )
}

export default App
