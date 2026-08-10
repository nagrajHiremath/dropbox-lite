import { LayoutGridIcon, ListIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useViewModeStore } from '@/store/viewModeStore'

export function ViewModeToggle() {
  const mode = useViewModeStore((s) => s.mode)
  const setMode = useViewModeStore((s) => s.setMode)

  return (
    <div className="flex items-center rounded-md border p-0.5">
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={cn('size-7', mode === 'list' && 'bg-accent')}
        aria-pressed={mode === 'list'}
        aria-label="List view"
        onClick={() => setMode('list')}
      >
        <ListIcon className="size-4" />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        className={cn('size-7', mode === 'grid' && 'bg-accent')}
        aria-pressed={mode === 'grid'}
        aria-label="Grid view"
        onClick={() => setMode('grid')}
      >
        <LayoutGridIcon className="size-4" />
      </Button>
    </div>
  )
}
