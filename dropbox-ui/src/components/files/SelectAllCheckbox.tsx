import { CheckIcon, MinusIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface SelectAllCheckboxProps {
  total: number
  selectedCount: number
  onSelectAll: () => void
  onClear: () => void
}

/** Tri-state header checkbox for list view (FileTable/FilteredFilesView) -
 * clicking selects everything regardless of whether the current state is
 * "none" or "some" selected, and only clears once everything is already
 * selected (Gmail/Drive convention: partial -> all -> none, not a plain
 * toggle back to whatever it started from). Grid view has no header row, so
 * it relies on the Ctrl/Cmd+A shortcut (useSelectAllShortcut) instead. */
export function SelectAllCheckbox({
  total,
  selectedCount,
  onSelectAll,
  onClear,
}: SelectAllCheckboxProps) {
  const allSelected = total > 0 && selectedCount === total
  const someSelected = selectedCount > 0 && !allSelected

  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={allSelected ? true : someSelected ? 'mixed' : false}
      aria-label={allSelected ? 'Clear selection' : 'Select all'}
      onClick={allSelected ? onClear : onSelectAll}
      disabled={total === 0}
      className={cn(
        'flex size-4.5 shrink-0 cursor-pointer items-center justify-center rounded-sm border transition-colors disabled:cursor-default disabled:opacity-40',
        allSelected || someSelected
          ? 'bg-primary border-primary text-primary-foreground'
          : 'border-muted-foreground/40 bg-background hover:border-muted-foreground/70',
      )}
    >
      {allSelected && <CheckIcon className="size-3" aria-hidden="true" />}
      {someSelected && <MinusIcon className="size-3" aria-hidden="true" />}
    </button>
  )
}
