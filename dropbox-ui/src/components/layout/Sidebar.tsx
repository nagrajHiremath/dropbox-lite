/**
 * UI-00: structural placeholder only. Nav links, active-section indication,
 * and the My Files/Recent/Photos/Videos/Trash items land in UI-02.
 */
export default function Sidebar() {
  return (
    <aside className="bg-sidebar text-sidebar-foreground flex w-60 shrink-0 flex-col border-r">
      <div className="flex h-14 items-center px-4 text-lg font-semibold">Dropbox Lite</div>
    </aside>
  )
}
