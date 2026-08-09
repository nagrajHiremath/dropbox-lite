# Dropbox-Lite — UI Implementation Plan

**Source of truth for UX principles:** `docs/DROPBOX_UI.md`
**Source of truth for backend contracts:** current controller/DTO source (this doc's API
inventory below was read directly from source, not from `TECHNICAL_DESIGN.md`, since the
backend has moved on since that doc was last updated).
**Architecture authority for anything backend:** `docs/TECHNICAL_DESIGN.md` — this plan does
not change any service boundary, API contract, or schema. Where the UI needs something the
backend doesn't yet expose, it's called out explicitly below rather than assumed.

This is a planning document only. No frontend code exists yet. Review this, then we implement
phase-by-phase the same way the backend was built (one numbered task at a time).

---

# 1. Tech Stack

| Concern | Choice | Why |
|---|---|---|
| Framework | React 18 + TypeScript | `TECHNICAL_DESIGN.md` §5 already fixes "React Client" as the architecture. |
| Build tool | Vite | Pure SPA behind the gateway — no SSR/server-component requirement exists anywhere in the docs, so Next.js would add unused complexity. Vite is the minimal, fast option. |
| Routing | React Router v6 | Standard SPA routing, small footprint. |
| Server state | TanStack Query | Caching, background refetch, and optimistic updates map directly onto DROPBOX_UI.md's "Optimistic UI where safe" / "Refresh only affected lists/components." Avoids hand-rolling cache invalidation. |
| Local/UI state | Zustand | Selection set, upload queue, active modal — state that isn't server data and doesn't belong in Query's cache. Small, no boilerplate. |
| Styling | Tailwind CSS | Utility-first gives the consistent spacing/typography/radius DROPBOX_UI.md asks for without hand-maintaining a CSS system. |
| Component primitives | shadcn/ui (Radix UI + Tailwind) | Dialog, DropdownMenu, ContextMenu, Popover, Toast, Tooltip come pre-built with correct keyboard/focus/ARIA behavior — directly satisfies the Accessibility and Modals/Menus sections instead of us hand-rolling accessible dialogs. |
| Icons | lucide-react | Single consistent icon set (shadcn/ui's default pairing) — satisfies "Consistent file/folder icon treatment." |
| Uploads transport | `XMLHttpRequest` (not `fetch`) for part uploads specifically | `fetch` does not expose upload progress events in browsers; XHR does. DROPBOX_UI.md explicitly says "Don't use fake progress when real progress is available" — the backend already returns real per-part progress, so we use the one browser API that can report it. |
| Dates | `date-fns` (or plain `Intl` if we want zero extra dep) | Formatting only, small surface. |

No state-management framework beyond the two above, no CSS-in-JS, no UI kit beyond shadcn/ui's primitives, no drag-and-drop library — the native HTML5 Drag and Drop API is used directly (see §6), since off-the-shelf DnD libraries are built for OS-file-drop only and don't cleanly cover "drag an existing file onto a folder row to move it," which this app also needs.

---

# 2. Known Backend Gaps (found while grounding this plan in the actual API)

These are not being silently worked around — flagging them for a decision before the phases that touch them.

1. **Trashed folders cannot be listed.** `GET /api/v1/folders` / `.../children` are hardcoded to `FolderStatus.ACTIVE` (`FolderService.listChildren`, `FolderSpecifications.hasStatus(ACTIVE)`). Files have a working `status=trashed` filter (`GET /api/v1/files?status=trashed`); folders have no equivalent. **Impact:** the Trash view (UI-14) can show trashed files but not trashed folders in v1. Fixing this needs a small, symmetric backend addition (a `status` query param on `FolderController.listByParent`, mirroring `FileController`) — not a redesign, but it's a backend change and per CLAUDE.md I'm not making it without a go-ahead.
2. **No activity-feed read endpoint.** `Activity`/`ActivityRepository`/`ActivityConsumer` exist in metadata-service but are write-only (populated by the Kafka consumer, no controller reads them). DROPBOX_UI.md doesn't ask for an activity/history screen explicitly, so this plan doesn't include one — noting it only so it's a conscious omission, not an oversight.
3. **No sort control.** Every list endpoint has a hardcoded server-side sort (Folders → name asc, Files → updatedAt desc, Versions → versionNumber desc) with no client-supplied sort param. DROPBOX_UI.md asks for "clear column headers," not sortable ones — so v1 column headers are labels only, not click-to-sort controls. Flagging so nobody expects sort clicks to work against the live list.
4. **No token refresh.** Login returns a 1-hour access token with no refresh endpoint. Mid-session expiry means a hard redirect to `/login` on the next 401, losing in-progress context. Acceptable for MVP; noting it as a deliberate limitation rather than solving it with a backend change that wasn't asked for.

---

# 3. Auth Model (as implemented today)

- `POST /api/v1/auth/register` → `{id, email, displayName, createdAt}`
- `POST /api/v1/auth/login` → `{accessToken, tokenType, expiresInMs, userId, email}`
- Every other call sends `Authorization: Bearer <accessToken>`. The gateway validates the JWT and injects `X-User-Id` downstream — the frontend never sees or needs internal user IDs for authorization, only for display.
- Token is kept in memory (Zustand auth store) + mirrored to `localStorage` so a page refresh doesn't force re-login inside the 1-hour window. A single fetch wrapper (`api/client.ts`) attaches the header and, on any `401`, clears the store and hard-navigates to `/login` — this is the one central place that needs to know about auth failure, not every call site.

---

# 4. API Client Layer

One typed module per resource, each a thin wrapper over a shared `client.ts` (adds base URL from `VITE_API_BASE_URL`, auth header, JSON parsing, and normalizes backend error bodies into a single `ApiError` shape so components never branch on raw HTTP status):

```
src/api/
  client.ts       fetch wrapper: auth header, base URL, error normalization
  auth.ts         login, register
  folders.ts      create, get, listByParent, listChildren, update, trash, restore
  files.ts        get, list (folderId | recent | type | trashed), rename, move, trash, restore, permanentDelete
  uploads.ts      initiate (new file / new version), uploadPart (XHR, progress callback), getStatus, complete, abort
  versions.ts     list, restore
  shares.ts       create, list, revoke
  publicShares.ts get metadata (anonymous), content URL builder (anonymous)
  downloads.ts    content URL builder (current version / specific version, Range-aware via native <a>/fetch)
```

Every function's request/response shape matches the DTOs read from source (field names exactly as in `InitiateUploadRequest`, `FileResponse`, `PageResponse<T>`, etc. — no invented fields). `PageResponse<T>{content, page, size, totalElements, totalPages}` becomes one shared TypeScript generic used by every paginated hook.

---

# 5. Routing Map

| Path | Screen | Backend calls |
|---|---|---|
| `/login`, `/register` | Auth | account-service |
| `/files` , `/files/:folderId` | My Files (folder browser) | `GET /folders/{id}` (breadcrumb + validation), `GET /folders?parentId=`, `GET /files?folderId=` |
| `/recent` | Recent files | `GET /files?view=recent` |
| `/photos` | Images | `GET /files?type=image` |
| `/videos` | Videos | `GET /files?type=video` |
| `/trash` | Trash | `GET /files?status=trashed` (+ folders once gap #1 is resolved) |
| `/s/:token` | Public share landing (no app shell, no auth) | `GET /public/shares/{token}`, content via `GET /public/shares/{token}/content` |
| `*` | 404 | — |

**Breadcrumbs:** `FolderResponse` only carries `parentId`, not a full path. Breadcrumb trails are built client-side by walking `parentId` upward, resolved from whatever folders are already in the Query cache (the common case — user navigated down through them) and falling back to individual `GET /folders/{id}` calls for a cold/deep-linked URL. No backend change needed, just a client-side resolution strategy worth naming explicitly.

---

# 6. Upload Engine Design

This is the part worth the most care, since it's the one place DROPBOX_UI.md is strict ("must not block the whole application," "show individual progress," "don't use fake progress," "easy to retry," "concurrent... independently").

Backend contract recap: `POST /uploads` (or `POST /files/{id}/uploads` for a new version) returns `{uploadId, chunkSize, totalParts, status}`. Each part is `PUT /uploads/{uploadId}/parts/{n}` with a raw binary body. `POST /uploads/{uploadId}/complete` finalizes. `GET /uploads/{uploadId}` reports which parts already landed (resumability). `DELETE /uploads/{uploadId}` aborts.

Design:
- A framework-agnostic `UploadEngine` (plain TS class/module, not a React hook itself) owns one upload's lifecycle: slice the `File` object into `chunkSize`-sized blobs, PUT them with bounded concurrency (e.g. 3 parts in flight at once — real progress per part via XHR `upload.onprogress`, aggregated into one overall percentage), call `complete` when all parts succeed, expose state transitions (`queued → uploading → completing → done | failed`).
- A Zustand `uploadQueueStore` holds one entry per in-flight/recent upload (file name, bytes, progress, state, error). The Upload Tray (§7) is a pure view over this store — multiple uploads run concurrently and independently, so one failure never blocks the others.
- Failed parts retry automatically a bounded number of times inside the engine (network blip case); a fully failed upload surfaces a **Retry** action in the tray that re-uses the existing `uploadId` and re-queries `GET /uploads/{uploadId}` first to skip parts that already succeeded — this is the resumability the backend already supports, not something invented client-side.
- Dismissing a completed/failed tray entry only removes it from the local store; it never calls `DELETE /uploads/{uploadId}` for a completed upload (that endpoint is for aborting an incomplete one).
- New-version uploads reuse the exact same `UploadEngine`, just pointed at `POST /files/{id}/uploads` for initiation — everything past that call (part PUT/complete/status/abort) is identical, so this isn't a second code path.

---

# 7. Component Inventory

```
src/components/
  layout/       Sidebar, TopBar, Breadcrumbs, AppShell
  files/        FileTable, FileRow, FolderRow, FileIcon (by mimeType), SelectionToolbar,
                RowContextMenu, MultiSelectActionBar
  upload/       UploadDropZone (OS-file drag-in), UploadTray, UploadTrayItem
  dnd/          useInternalDrag / useDropTarget hooks (native HTML5 DnD, used by FileTable
                rows + Sidebar + Breadcrumbs as drop targets for move)
  modals/       CreateFolderDialog, RenameDialog, MoveDialog, ShareDialog,
                VersionHistoryDialog, ConfirmTrashDialog, ConfirmPermanentDeleteDialog,
                FilePreviewDialog
  common/       EmptyState, ListSkeleton, ErrorState (with Retry), PageHeader
  ui/           shadcn/ui generated primitives (button, dialog, dropdown-menu, toast, etc.)
```

Selection model: single click selects (replaces selection), ctrl/cmd+click toggles, shift+click range-selects, matching DROPBOX_UI.md's file-browser UX section. `SelectionToolbar` appears only when `selection.size > 0` and exposes the destructive-hierarchy actions (Move to Trash vs. Permanent Delete get visually distinct treatment per DROPBOX_UI.md's Destructive Actions section).

---

# 8. Feedback, Loading, Empty, Error States

Directly per DROPBOX_UI.md's checklist — implemented as shared primitives so every screen gets them for free rather than each screen reinventing them:
- `ListSkeleton` — shown while the first page of any list query is loading.
- `EmptyState` — parameterized per context (empty folder → Upload/Create folder CTAs; empty Trash; empty Recent/Photos/Videos).
- `ErrorState` — shown on query failure with a Retry button wired to Query's `refetch`.
- Toasts (shadcn/ui `sonner` or `toast`) fire on every mutation's settle (rename, move, trash, restore, permanent delete, share create/revoke, upload complete/fail) — not on plain navigation, per DROPBOX_UI.md's "don't overuse toasts" note.
- Mutations use TanStack Query's optimistic update pattern where safe (rename, move, trash — instant list update, rollback on error) and a normal invalidate-and-refetch where an optimistic guess isn't safe (permanent delete, share revoke).

---

# 9. Phased Task Breakdown

Mirrors the backend's task-ID convention so we can implement one at a time the same way.

**Phase 0 — Scaffold**
- `UI-00` Vite + React + TS + Tailwind + shadcn/ui + Router + Query + Zustand scaffold, env config (`VITE_API_BASE_URL`), lint/format setup, base `AppShell` with empty Sidebar/TopBar.

**Phase 1 — Auth**
- `UI-01` Login/Register pages, auth store, token persistence, protected-route guard, global 401 handling.

**Phase 2 — Shell & Navigation**
- `UI-02` Sidebar (My Files/Recent/Photos/Videos/Trash, active-section indicator), Breadcrumbs component, route skeleton for all screens, shared Empty/Error/Skeleton primitives.

**Phase 3 — File Browser Core**
- `UI-03` Folder/file list (table), breadcrumb resolution, single/multi-select, row context menu shell.
- `UI-04` Create folder, rename (file + folder), move (modal-driven), trash/restore, permanent delete — each with the correct confirmation tier.

**Phase 4 — Upload**
- `UI-05` `UploadEngine` + `uploadQueueStore` (chunking, XHR progress, bounded concurrency, resumable retry, abort).
- `UI-06` Upload Tray UI + OS drag-and-drop zone.

**Phase 5 — Organize via Drag & Drop**
- `UI-07` Internal drag-and-drop (file/folder row → folder row/sidebar/breadcrumb), valid/invalid target indication, drag preview, confirmation before a cross-folder move actually commits.

**Phase 6 — Versions & Sharing**
- `UI-08` New-version upload entry point + Version History modal (list + restore).
- `UI-09` Share modal (create with permission/expiry, copy link, list existing, revoke).
- `UI-10` Public share landing page (anonymous view + download, no app shell).

**Phase 7 — Preview & Details**
- `UI-11` File details panel (metadata, entry points to versions/share).
- `UI-12` Image preview; non-previewable types get a clean "no preview" state with a Download CTA.

**Phase 8 — Filtered Views & Trash**
- `UI-13` Recent / Photos / Videos screens.
- `UI-14` Trash screen (files now; folders once backend gap #1 is resolved).

**Phase 9 — Polish**
- `UI-15` Toast coverage audit across every mutation.
- `UI-16` Skeleton/empty-state coverage audit across every list surface.
- `UI-17` Accessibility pass (keyboard nav, focus trap/return in modals, ARIA labeling, contrast check).
- `UI-18` Responsive pass (desktop-first → tablet/narrow adjustments).

---

# 10. Open Questions For Review

1. OK to proceed with the stack in §1 (Vite/Tailwind/shadcn/TanStack Query/Zustand), or is there a preference to change any of them?
2. For backend gap #1 (trashed folders unlistable) — want the small `status` param added to `FolderController` as a short backend task before `UI-14`, or ship Trash as files-only for now?
3. Any of the 10 phases you want reordered or merged (e.g. pull Share/Version earlier, or Preview later) relative to how they're sequenced above?
