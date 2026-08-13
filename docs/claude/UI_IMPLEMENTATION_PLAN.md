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

1. ~~**Trashed folders cannot be listed.**~~ **Resolved.** `GET /api/v1/folders?status=trashed` now returns an account-wide trashed-folders listing and `DELETE /api/v1/folders/{id}/permanent` was added, mirroring the files trash lifecycle exactly (see `IMPLEMENTATION_PLAN.md` §31 META-06's implementation notes). UI-14 (Trash) can show both trashed files and trashed folders.
2. **No activity-feed read endpoint.** `Activity`/`ActivityRepository`/`ActivityConsumer` exist in metadata-service but are write-only (populated by the Kafka consumer, no controller reads them). DROPBOX_UI.md doesn't ask for an activity/history screen explicitly, so this plan doesn't include one — noting it only so it's a conscious omission, not an oversight.
3. **No sort control.** Every list endpoint has a hardcoded server-side sort (Folders → name asc, Files → updatedAt desc, Versions → versionNumber desc) with no client-supplied sort param. DROPBOX_UI.md asks for "clear column headers," not sortable ones — so v1 column headers are labels only, not click-to-sort controls. Flagging so nobody expects sort clicks to work against the live list.
4. ~~**No token refresh.**~~ **Resolved.** account-service now issues a rotating refresh token alongside the access token (see `IMPLEMENTATION_PLAN.md` §8 ACC-02's implementation notes). UI-01's `api/client.ts` uses it: any `401` triggers one single-flight silent refresh-and-retry before falling back to a hard redirect to `/login` — mid-session expiry no longer loses context in the common case.

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
| `/trash` | Trash | `GET /files?status=trashed`, `GET /folders?status=trashed` |
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

**Phase 1 — Auth** ✅
- `UI-01` Login/Register pages, auth store, token persistence, protected-route guard, global 401 handling. Implemented: `store/authStore.ts` (Zustand + `persist` → localStorage, holds access+refresh tokens), `api/client.ts` (attaches `Authorization`, normalizes error bodies into `ApiError{status,code,message,fieldErrors}`, single-flight refresh-on-401-then-retry-once, hard-redirects to `/login` only if refresh itself fails), `api/auth.ts` (typed `login`/`register`), real Login/Register forms (client-side validation matching backend `@Size`/`@Email` constraints, inline error display, register→login handoff via a success toast), `components/auth/RequireAuth.tsx` guarding the `AppShell` route group and preserving the originally-requested location for post-login redirect. Verified: type-check/build/lint/format clean; live-verified wire compatibility against the real backend (register/login/refresh/401 payload shapes byte-for-byte match `api/auth.ts`'s types) and that the dev server serves `/login`/`/register`/`/files` correctly. **Not verified**: actual interactive browser use (clicking through the forms) — no browser automation tool is available in this environment; recommend a quick manual click-through before moving on.

**Phase 2 — Shell & Navigation** ✅
- `UI-02` Sidebar (My Files/Recent/Photos/Videos/Trash, active-section indicator), Breadcrumbs component, route skeleton for all screens, shared Empty/Error/Skeleton primitives. Implemented: real `layout/Sidebar.tsx` (`NavLink`-based, active-state styling off the `--sidebar-*` tokens), `layout/Breadcrumbs.tsx` (purely presentational - `{label, to?}[]` items; real folder-chain resolution is UI-03's job per §5), `TopBar` now renders a route-derived single-level crumb as a placeholder until UI-03 wires real chains, and `common/{PageHeader,EmptyState,ErrorState,ListSkeleton}` per §7/§8. All five section pages (My Files/Recent/Photos/Videos/Trash) now render real `PageHeader` + context-specific `EmptyState` copy instead of a bare heading - deliberately no Upload/Create-folder action buttons yet, since wiring dead/non-functional buttons would violate DROPBOX_UI.md's "don't fake it" spirit; those land for real in UI-04/UI-06. Verified: type-check/build/lint/format clean, dev server serves all routes and the new modules transform without error. **Not verified**: interactive browser use (same limitation as UI-01 - no browser automation tool available here).

**Phase 3 — File Browser Core** ✅
- `UI-03` Folder/file list (table), breadcrumb resolution, single/multi-select, row context menu shell. Implemented: `api/{folders,files}.ts` (read-only for now - `getFolder`/`listFolders`/`getFile`/`listFiles`; mutations land in UI-04 when something actually calls them), `hooks/useBreadcrumbs.ts` (one `useQuery` whose queryFn walks `parentId` upward via `queryClient.fetchQuery` per hop - cache-first, falls back to a real `GET /folders/{id}` only on a cold/deep-linked URL, capped at the backend's own 1000-hop guard), `hooks/useSelection.ts` (click replaces / ctrl-click toggles / shift-click range-selects, anchor-based), `lib/fileIcons.ts` (mimeType→icon mapping + the `toBrowserEntry` discriminated-union constructor, reused by later phases), `components/files/{EntryRow,FileTable,SelectionToolbar}.tsx`, and a full `MyFilesPage` rewrite. `TopBar` now calls `useBreadcrumbs` itself off the active route's `:folderId` param (a layout component can read nested-route params via `useParams` without prop-drilling) for the `/files*` section only - other sections keep UI-02's single-label placeholder.

  Design deviations from the original plan, now that real implementation details are known: **(1)** selection is local `useState` in `useSelection`, not a global Zustand store - it's folder-scoped and only read by `MyFilesPage`'s own children, so a global store would just need manual clearing logic for no benefit; Zustand stays reserved for genuinely cross-page state (upload queue, UI-05). **(2)** one `EntryRow` component handles both folders and files (discriminated union prop) instead of separate `FileRow`/`FolderRow` - less duplication. **(3)** no pagination controls - fetches a single page at the backend's max size (100) per directory; not its own task anywhere in the plan and unlikely to matter for a hackathon demo. **(4)** context-menu items for not-yet-implemented actions (Download, Rename, Move, Share, Version History, Move to Trash) are `disabled`, not toast-on-click - honest about what works today without click-noise; only folder "Open" is live. **(5)** no file-size column - `FileResponse` doesn't include size (it lives on `FileVersion`, resolved via `currentVersionId`), and populating it would mean an N+1 per-file version lookup on every list render; not doing that silently. Flagging as a possible small backend enhancement (an optional `currentVersionSizeBytes` on `FileResponse`) if a size column turns out to matter later.

  Verified: type-check/build/lint/format clean; live-verified wire compatibility against the real backend (created real folders/subfolders, confirmed `GET /folders`, `GET /folders?parentId=`, `GET /folders/{id}`, `GET /files?folderId=` response shapes match `api/folders.ts`/`api/files.ts`'s types exactly) and that the dev server serves `/files` and `/files/{realFolderId}` correctly with the new modules transforming without error. **Not verified**: interactive browser use (same limitation as UI-01/02).
- `UI-04` ✅ Create folder, rename (file + folder), move (modal-driven), trash/restore, permanent delete — each with the correct confirmation tier. Completed the write side of `api/folders.ts`/`api/files.ts` to match the backend 1:1 (`create/rename/move/trash/restore/permanentlyDelete` for both). New `hooks/{useFolderMutations,useFileMutations}.ts` centralize cache invalidation per mutation (one place per action, not duplicated per call site) via a new shared `hooks/queryKeys.ts` so list-query keys and invalidation keys can never drift apart. New `components/modals/{CreateFolderDialog,RenameDialog,MoveDialog}.tsx`; `MoveDialog` is a small in-dialog folder browser (reuses `listFolders`), not drag-and-drop (that's UI-07). `EntryRow`'s context-menu shell now has Rename/Move/Move-to-Trash live (Download/Share/Version-history still disabled - later phases). `SelectionToolbar` gained bulk Move-to-Trash. `MyFilesPage` gained a "New folder" `PageHeader` action (still no "Upload" button - not faking it before UI-05/06 exist).

  **Real backend asymmetry found while wiring this, not assumed**: `UpdateFolderRequest`'s own javadoc says a `null` `parentId` means "leave unchanged," not "move to root" - folders **cannot** be moved to root in this MVP, while files genuinely can (`MoveFileRequest`'s `folderId: null` is honored as root by `FileService.moveFile`). Live-verified: `PATCH /folders/{id}` with `{parentId:null}` on a non-root folder returns `200` with the folder **unchanged**, not an error and not moved - a real silent-no-op trap. `MoveDialog` accounts for this by disabling "Home" as a destination when moving a folder, with an inline note explaining why, rather than letting a user "successfully" move-to-root and wonder why nothing happened.

  Other design decisions (full rationale in the approved UI-04 plan): Move-to-Trash has no blocking confirm dialog (only Permanent Delete gets one, per DROPBOX_UI.md's tier split) - immediate action + a success toast with an **Undo** action instead; Permanent Delete's mutation hook exists now but has no UI trigger until UI-14's Trash page has trashed rows to attach it to; bulk actions cover trash only, not move/rename (marginal gain vs. complexity); dialog state lives in `MyFilesPage`, not per-row.

  Verified: type-check/build/lint/format clean; live-verified wire compatibility for create/rename/move/trash/restore against the real backend, **including the root-move-on-folder no-op** proven live (not just theorized from source) - `{parentId:null}` on `SubRenamed` (parented under `FolderB`) returned `200` with `parentId` still `FolderB`. File move-to-root specifically was reasoned from `FileService.moveFile` source (already read this session, unambiguous - unconditional `setFolderId`, no null-guard) rather than live-tested, since exercising it live would have meant booting upload-service just for one confirmatory data point. Dev server serves `/files`/`/files/{realFolderId}` and the new modules transform without error. **Not verified**: interactive browser use (same limitation as UI-01/02/03).

**Phase 4 — Upload**
- `UI-05` ✅ `UploadEngine` + `uploadQueueStore` (chunking, XHR progress, bounded concurrency, resumable retry, abort). Engine/store only, no visible UI yet - `enqueueUpload` has no caller until UI-06 wires a drop zone to it. New `api/uploads.ts` (initiate/initiateVersion/status/complete/abort, all through the existing `apiFetch` - gets refresh-on-401 for free). New `lib/uploadEngine.ts`: a plain `UploadEngine` class (not a hook) with a hand-rolled bounded-concurrency pump (3 parts in flight), automatic per-part retry (3 attempts, linear backoff) separate from manual whole-upload retry (which re-fetches `GET /uploads/{uploadId}` first and only re-sends parts absent from `uploadedParts` - server state is authoritative over client bookkeeping), cooperative abort (an `aborted` flag the scheduler checks so an aborted XHR's error isn't misread as a real part failure), and one `Idempotency-Key` (existing backend feature) generated per engine instance to protect the initiate call across manual retries. New `store/uploadQueueStore.ts`: engine instances live in a module-level `Map`, not in Zustand state itself (XHRs/AbortControllers aren't meaningful React state); the store holds only the plain display fields UI-06's tray will render.

  **Confirmed critical detail from source, not assumed**: `UploadPartService.expectedPartSize` computes each part's expected byte length itself and streams MinIO with that exact length - it doesn't trust a client-declared size. The client's slicing must match the server's chunkSize/totalParts formula exactly (`file.slice((n-1)*chunkSize, min(n*chunkSize, file.size))`), which it does by construction since it only ever uses the chunkSize/totalParts the server returned from initiate, never inventing its own.

  **Disclosed limitation**: only the part PUT itself uses raw XHR (needed for real progress events, which `fetch` can't report) and so does not get `client.ts`'s automatic refresh-on-401 the way every other API call does - if the access token expires mid-upload with no unrelated API activity to trigger a refresh, in-flight parts will 401 and the upload fails, recoverable via the same manual Retry already needed for network failures. Not auto-healing, but not silent data loss either; a second refresh path for XHR specifically was judged not worth the complexity for an edge case requiring both a long upload and total inactivity elsewhere.

  Verified: type-check/build/lint/format clean (note: had to fix one real TS issue - this project's `erasableSyntaxOnly` tsconfig option rejects constructor parameter-property shorthand, caught immediately by `tsc`). Live end-to-end wire-compat against the real backend (the first UI-05 curl attempt hit a `DEPENDENCY_UNAVAILABLE` on the part PUT because `minio-init` hadn't run this session - fixed by running it, not a client bug): initiate → PUT part 1 → `GET` status (confirmed `uploadedParts:[1]`) → complete (`fileId`/`versionId` returned) → confirmed the resulting file appears in `GET /files`, every response shape matching `api/uploads.ts`'s types exactly. No dev-server/browser check this phase - nothing renders yet (`UploadEngine` has no UI trigger until UI-06).

- `UI-06` ✅ Upload Tray UI + OS drag-and-drop zone. New `components/upload/{UploadTray,UploadTrayItem,UploadDropZone}.tsx` - `UploadTray` is mounted once in `AppShell` (not per-page) so it stays visible while browsing per DROPBOX_UI.md, and is a pure view over `uploadQueueStore` (UI-05); `UploadDropZone` wraps `MyFilesPage`'s content, using a drag-enter/leave *counter* (not a boolean) since those events fire on every descendant boundary crossing, not just the outer container - a naive boolean flickers. `MyFilesPage` gained a real "Upload" button (hidden file input + click) alongside "New folder," and the empty-folder state (`FileTable`'s new `emptyAction` prop, threading into `EmptyState`'s existing `action` slot from UI-02) now has working Upload + New Folder buttons for the first time - both were deliberately left un-wired as dead buttons in UI-02/04 until the mutations behind them were real; closing that loop now that they are.

  Verified: type-check/build/lint/format clean (bundle size grew vs. UI-05's build, confirming `uploadEngine`/`uploadQueueStore` are now actually reachable/tree-shaken-in, not dead code - UI-05 had built clean too but with an *identical* bundle size to before it, since nothing imported those modules yet). Dev server serves `/files` and the new upload components transform without error. Backend upload-flow correctness itself was already proven end-to-end live in UI-05 (same engine, same API calls) - not re-verified here since this phase only adds a view layer on top. **Not verified**: interactive browser use / actual drag-and-drop (same limitation as every prior phase - no browser automation available here).

**Phase 5 — Organize via Drag & Drop** ✅
- `UI-07` ✅ Internal drag-and-drop (file/folder row → folder row/sidebar/breadcrumb), valid/invalid target indication, drag preview, confirmation before a cross-folder move actually commits. New `store/dragStore.ts` (Zustand, tracks the currently-dragged entry - needed because HTML5 DnD's `dataTransfer.getData()` is only readable on the actual `drop` event, not during `dragover`/`dragenter`, so live valid/invalid styling needs the dragged item's identity from somewhere else). New `hooks/useDropTarget.ts` (counter-based enter/leave, same reason `UploadDropZone` from UI-06 needs one) and `hooks/useDragMove.ts` (the actual move-on-drop + Undo-toast logic, reusing UI-04's `useMoveFolder`/`useMoveFile` unchanged). "Confirmation before a move commits" is the valid-target highlight itself (shown *during* the drag, before release) plus a success toast with **Undo** after - not a blocking dialog, mirroring UI-04's Move-to-Trash precedent exactly (same tier reasoning: this is not "Highly destructive"). Drag preview uses the row's own DOM node via `dataTransfer.setDragImage` - no separate preview element needed. Trash is deliberately not a drop target (blurring move/trash semantics via drag is exactly the accidental-destructive-action risk DROPBOX_UI.md warns against); drag is single-item only, not the whole multi-select (mirrors UI-04's own bulk-move scope trim).

  **Design evolved during implementation**: the plan originally had `MyFilesPage` supply the `onDrop` callback (prop-threaded through `FileTable`→`EntryRow`), matching how `trashEntry` works. Once the Sidebar/Breadcrumbs targets were being built - which have no line back to `MyFilesPage` at all - it became clear they'd need to call the mutation hooks directly themselves regardless. Since `useMoveFolder`/`useMoveFile` already own their own cache invalidation (UI-04's design), there was no actual benefit to routing the call through `MyFilesPage` for `EntryRow` specifically, just extra prop plumbing for no behavioral difference. Switched all three targets to call `useDragMove()` directly - **`MyFilesPage.tsx` ended up needing zero changes this phase.**

  **Root-move limitation reused, not re-derived**: the Sidebar's "My Files" item and each Breadcrumb's root crumb are valid drop targets for files only, folders are excluded - reusing UI-04's live-confirmed finding that a folder can never move to root in this MVP, rather than rediscovering it. `useDragMove` also omits the Undo action specifically when a folder's *original* location was root, since undoing that would itself require the impossible root-move.

  Verified: type-check/build/lint/format clean. Dev server serves `/files` and every new/modified module (`dragStore.ts`, `useDropTarget.ts`, `useDragMove.ts`, `EntryRow.tsx`, `Sidebar.tsx`, `Breadcrumbs.tsx`) transforms without error. The underlying move mutations themselves were already live-verified end-to-end in UI-04. **Not verified**: the actual drag gesture/interaction - HTML5 drag-and-drop cannot be exercised via curl or any tool available in this environment, so this phase's interaction correctness is code-review-level, not live-tested; called out explicitly rather than glossed over, same as the browser-automation limitation on every prior phase.

**Phase 6 — Versions & Sharing**
- `UI-08` ✅ New-version upload entry point + Version History modal (list + restore). New `api/versions.ts` (`listVersions`/`restoreVersion`, matching `metadata-service`'s non-destructive restore - it creates a new version rather than deleting history). New `hooks/useVersionMutations.ts` (`useRestoreVersion`, invalidates both `versionsKey(fileId)` and `filesKey(folderId)` since restoring changes the file's `currentVersionId`). New `hooks/queryKeys.ts:versionsKey`. New `components/modals/VersionHistoryDialog.tsx` - lists versions newest-first, "Current" badge + disabled Restore button on the version matching the file's `currentVersionId`, plus an "Upload new version" trigger (hidden file input → `enqueueUpload(file, {kind:'new-version', fileId})`, closes the dialog immediately since the upload continues via the existing tray). `EntryRow`'s "Version history" menu item is now live (files only), wired through `FileTable`→`MyFilesPage` via the same `onRequest*`/`ActiveDialog` pattern as Rename/Move.
  
  **Bug fixed alongside (not separately scoped, but required for this phase to actually show fresh data)**: `uploadQueueStore`'s `onStatusChange` never invalidated any TanStack Query cache on upload completion, so a finished upload (new file *or* new version) didn't refresh the visible file list / version history without an unrelated refetch happening to occur - a latent gap since UI-06, not just a UI-08 issue. Fixed via a new `lib/queryClient.ts` exporting a singleton `QueryClient` (so `uploadQueueStore`, which runs outside React, can call `invalidateQueries` the same way a component does via `useQueryClient()`); `main.tsx` now imports this shared instance instead of constructing its own. On `status === 'done'`, the store invalidates the broad `['files']` prefix, plus `versionsKey(fileId)` specifically for `new-version` uploads.

  Verified: type-check/build/lint/format clean. Dev server serves `/files` and every new/modified module (`api/versions.ts`, `lib/queryClient.ts`, `hooks/useVersionMutations.ts`, `VersionHistoryDialog.tsx`, `EntryRow.tsx`, `uploadQueueStore.ts`, `MyFilesPage.tsx`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase (unlike UI-05's live upload verification), so the actual `GET .../versions` / `POST .../versions/{id}/restore` request/response shapes are confirmed by reading `metadata-service` source only, not exercised end-to-end - flagged explicitly rather than glossed over.
- `UI-09` ✅ Share modal (create with permission/expiry, copy link, list existing, revoke). New `api/shares.ts` (`createShare`/`listShares`/`revokeShare`, matching `metadata-service`'s `FileShareController`/`ShareController` exactly - shares are files-only, there's no folder-share endpoint; `listShares` is a plain array, not paginated). New `hooks/queryKeys.ts:sharesKey` and `hooks/useShareMutations.ts` (`useCreateShare`/`useRevokeShare`, both invalidate `sharesKey(fileId)`). New `components/modals/ShareDialog.tsx`: permission (`VIEW`/`DOWNLOAD` - the only two values ever seen server-side; `CreateShareRequest.permission` isn't enum-validated but the UI only offers these) and expiry (1/7/30 days or never, computed client-side into an ISO timestamp) selects, "Create link" button, a one-time-reveal box for the created link (`${origin}/s/{token}`, since the backend only returns the raw token from the create response - `listShares` returns hash-backed entries with no token field, matching `ShareCreatedResponse` vs `ShareResponse`), Copy button via `navigator.clipboard`, and a scrollable list of existing shares with a disabled-when-already-revoked Revoke action. `EntryRow`'s "Share" menu item is now live for files only (still disabled for folders, since the backend has no folder-share endpoint), wired through `FileTable`→`MyFilesPage` via the same `onRequest*`/`ActiveDialog` pattern as Rename/Move/Version history. No new dependency for the permission/expiry selects - plain native `<select>` styled to match `Input`, since this is the only place in the app needing one so far.

  Verified: type-check/build/lint/format clean. Dev server serves `/files` and every new/modified module (`api/shares.ts`, `hooks/useShareMutations.ts`, `ShareDialog.tsx`, `EntryRow.tsx`, `FileTable.tsx`, `MyFilesPage.tsx`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase, so the actual `POST/GET .../shares` and `DELETE /shares/{id}` request/response shapes are confirmed by reading `metadata-service` source directly (`CreateShareRequest`, `ShareCreatedResponse`, `ShareResponse` records), not exercised end-to-end - flagged explicitly rather than glossed over, same disclosed limitation as UI-08.
- `UI-10` ✅ Public share landing page (anonymous view + download, no app shell). New `api/publicShares.ts`: `getPublicShare(token)` hits `GET /api/v1/public/shares/{token}` (metadata-service, `permitAll`) with `auth: false` - not just because no token is needed, but so `apiFetch`'s 401-refresh-then-redirect-to-`/login` interceptor never fires on this page, which must stay usable for anonymous and logged-in-with-a-stale-token visitors alike. `publicShareContentUrl(token)` builds the direct download URL (`.../public/shares/{token}/content`, download-service) - no fetch/blob handling needed since that endpoint streams with `Content-Disposition: attachment` already set server-side, so a plain `<a href>` triggers a correct-filename download. Rewrote the `PublicSharePage.tsx` placeholder: loading skeleton, a distinguishable "Link not found" state for expired/revoked/invalid tokens (backend returns 404 for all three - it doesn't distinguish them either, to avoid leaking which case applies), and on success a centered card (matching `LoginPage`'s no-app-shell layout) showing the file's icon/name/size and a Download button gated on `permission === 'DOWNLOAD'`. Small refactor: extracted `getIconForMimeType` out of `lib/fileIcons.ts`'s `getEntryIcon` so this page can reuse the same icon logic from a bare mimetype string, without needing a fake `BrowserEntry`.

  **Backend behavior noted, not changed**: `ShareService.resolvePublicShareContent` doesn't actually check `permission` before allowing a content download - both `VIEW` and `DOWNLOAD` shares can hit the content endpoint today, so gating the Download button client-side on `permission === 'DOWNLOAD'` is a UI-level courtesy matching the share creator's stated intent, not a real access control (the backend enforces token validity/expiry/status, not permission level, for content access). Flagging this rather than silently "fixing" it, since permission enforcement is a backend concern out of this task's frontend-only scope per CLAUDE.md.

  Verified: type-check/build/lint/format clean. Dev server serves `/`, `/s/:token` (with an arbitrary token, since no backend was running), and every new/modified module (`api/publicShares.ts`, `lib/fileIcons.ts`, `PublicSharePage.tsx`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase, so the actual `GET /public/shares/{token}` and `.../content` responses are confirmed by reading `metadata-service`/`download-service` source directly, not exercised end-to-end - same disclosed limitation as UI-08/UI-09.

**Phase 7 — Preview & Details**
- `UI-11` ✅ File details panel (metadata, entry points to versions/share). New `components/modals/DetailsDialog.tsx`: icon/name header, Type/Size/Version (files only)/Created/Modified rows, and for files a "Version history"/"Share" button pair that hand off to `VersionHistoryDialog`/`ShareDialog` by calling the same `setActiveDialog` transition `MyFilesPage` already uses for those - no separate open/close choreography needed since only one dialog is ever active at a time. File size comes from the versions list (`FileResponse` itself has no `sizeBytes` - only `FileVersion` does), reusing `versionsKey`/`listVersions` - a cache hit if `VersionHistoryDialog` already fetched it for the same file, not a second request. `EntryRow` gained an always-enabled "Details" menu item (both folders and files) plus: double-clicking a **file** row now opens Details instead of doing nothing, since files have no navigation target and no preview yet (UI-12) - folders still navigate on double-click, unchanged. Threaded `onRequestDetails` through `FileTable`→`MyFilesPage` via the same `onRequest*`/`ActiveDialog` pattern as the other entry actions.

  Verified: type-check/build/lint/format clean. Dev server serves `/`, `/files`, and every new/modified module (`DetailsDialog.tsx`, `EntryRow.tsx`, `FileTable.tsx`, `MyFilesPage.tsx`) transforms without error. This phase touches no new backend contracts (reuses `FileResponse`/`FolderResponse`/`FileVersion` shapes already confirmed in UI-08), so there's no live-backend caveat to disclose this time.
- `UI-12` ✅ Image preview; non-previewable types get a clean "no preview" state with a Download CTA. New `lib/download.ts`: `fetchFileBlob`/`downloadFile` against `GET /api/v1/files/{fileId}/content` (download-service, authenticated) - reads the access token straight from `authStore` rather than going through `apiFetch`, the same pattern `uploadEngine.ts`'s `xhrPutPart` already established, since a plain `<img>`/`<a>` can't attach a Bearer header and `apiFetch` only handles JSON responses. Same disclosed limitation as that precedent: this path doesn't get `apiFetch`'s automatic refresh-on-401. New `components/modals/FilePreviewDialog.tsx`: for `image/*` mimeTypes, fetches the content as a blob and renders it via `URL.createObjectURL` (revoked on close/entry-change to avoid leaking object URLs); everything else - including previewable-in-principle types with no viewer built yet (PDF/video/audio) - gets the same clean "no preview available" state, deliberately not a half-built type-specific viewer. A Download button (using the same `downloadFile` helper) is always in the footer regardless of preview outcome.

  **Two previously-disabled `EntryRow` menu placeholders enabled alongside this, since the download mechanism they needed now exists**: "Preview" (files; still "Open" for folders, unchanged) now opens `FilePreviewDialog`, and "Download" (files only - no folder/zip download support) now calls `downloadFile` directly with an error toast on failure. Double-clicking a file row now opens Preview (superseding UI-11's interim "opens Details" behavior, which was itself explicitly framed as a placeholder for "files have no preview yet") - Details remains reachable via its own always-present menu item, unchanged. Threaded `onRequestPreview` through `FileTable`→`MyFilesPage` via the same `onRequest*`/`ActiveDialog` pattern as every other entry action this phase.

  Verified: type-check/build/lint/format clean. Dev server serves `/`, `/files`, and every new/modified module (`lib/download.ts`, `FilePreviewDialog.tsx`, `EntryRow.tsx`, `FileTable.tsx`, `MyFilesPage.tsx`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase, so the actual authenticated blob fetch/image render and download-trigger behavior are confirmed by reading `FileDownloadController`/`DownloadResponseBuilder` source (same streaming/`Content-Disposition` behavior already read for UI-10's public download path), not exercised end-to-end - same disclosed limitation as UI-08 through UI-10.

**Phase 8 — Filtered Views & Trash**
- `UI-13` ✅ Recent / Photos / Videos screens. The backend already had these views fully built and just unused by the frontend: `GET /api/v1/files?view=recent` and `?type=image|video` are account-wide (not folder-scoped, confirmed in `FileService.listFiles`), sorted by `updatedAt` descending server-side - `type` only accepts `image`/`video` (`FileService.ALLOWED_TYPES`), enforced with a 400 for anything else. New `api/files.ts` functions `listRecentFiles`/`listFilesByType` and matching `queryKeys.ts` entries (`recentFilesKey`, `filesByTypeKey`). New `components/files/FilteredFilesView.tsx`: a shared files-only list view (selection, single/bulk trash with undo, and the full Rename/Move/Share/Version-history/Details/Preview dialog set, reusing `EntryRow` and every existing modal unchanged) parameterized by query key/fn and empty-state copy - built as one shared component rather than tripling ~150 lines of near-identical state/dialog-wiring logic across three pages. `RecentPage`/`PhotosPage`/`VideosPage` are now thin wrappers passing their query and copy. No upload/create-folder here (there's no folder context to upload "into"); Move still works normally since `MoveDialog` is its own folder picker, independent of the current page.

  **Cache-invalidation gap fixed alongside, required for these new views to work correctly**: `useFileMutations.ts` (rename/move/trash/restore) and `useVersionMutations.ts`'s restore previously invalidated only `filesKey(folderId)` - too narrow now that Recent/Photos/Videos are separate query keys (`['files','recent']`, `['files','type',...]`) over the same underlying files. Without this fix, e.g. trashing a file from the Recent view would leave it visibly stuck in that list. Broadened both to invalidate the `['files']` prefix, matching the same "broad is simpler/safer than precise" precedent already established by `uploadQueueStore`'s UI-08 fix and UI-04's breadcrumb invalidation.

  Verified: type-check/build/lint/format clean. Dev server serves `/recent`, `/photos`, `/videos`, and every new/modified module (`FilteredFilesView.tsx`, the three pages, `api/files.ts`, `useFileMutations.ts`, `useVersionMutations.ts`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase, so the `view=recent`/`type=image|video` query behavior is confirmed by reading `FileController`/`FileService` source directly, not exercised end-to-end - same disclosed limitation as UI-08 through UI-12.
- `UI-14` ✅ Trash screen (files and folders both - backend gap #1 was already resolved by the earlier META-06 backend task, so this shipped full scope, not the files-only fallback originally anticipated). New `api/files.ts:listTrashedFiles` and `api/folders.ts:listTrashedFolders` (both `status=trashed`, account-wide, ignoring folder/parent scoping entirely - confirmed in `FileService`/`FolderController` source). New `queryKeys.ts` entries `trashedFilesKey`/`trashedFoldersKey`. New `components/modals/PermanentDeleteDialog.tsx`: a blocking confirm dialog, not an undo-toast - DROPBOX_UI.md marks Permanent Delete as the app's one "Highly destructive" tier action requiring explicit confirmation, unlike Move to Trash's fire-immediately-with-Undo pattern used everywhere else. Rewrote `TrashPage.tsx`: lists trashed folders then trashed files with a small dedicated `TrashRow` (icon/name/trashed-date + Restore/Permanently-delete buttons only) - deliberately *not* a reuse of `EntryRow`, since rename/move/share/version-history/preview all require `ACTIVE` status server-side and would be dead menu items on a trashed item. No bulk selection (mirrors UI-04's/UI-07's earlier "single-item is enough, bulk is marginal-gain-for-complexity" trims).

  **Two dead-code gaps closed alongside, both required for this page to work at all**: `usePermanentlyDeleteFile`/`usePermanentlyDeleteFolder` had sat since UI-04/the META-06 backend task with *no* `onSuccess`/cache invalidation at all (their doc comments literally said "no active UI trigger yet, UI-14 surfaces this") - added invalidation of the `['files']`/`['folders']` prefixes respectively, so a permanently-deleted item actually disappears from the Trash view. Also broadened `useTrashFolder`/`useRestoreFolder` from the narrow `foldersKey(parentId)` to the `['folders']` prefix, mirroring UI-13's identical `['files']` fix - the trashed-folders view is a separate query key over the same folders, so a parent-scoped invalidation alone would've left it stale after a trash/restore.

  Verified: type-check/build/lint/format clean. Dev server serves `/trash` and every new/modified module (`TrashPage.tsx`, `PermanentDeleteDialog.tsx`, `api/files.ts`, `api/folders.ts`, `useFileMutations.ts`, `useFolderMutations.ts`) transforms without error. **Not live-verified against the backend**: no infra/backend services were running in this environment during this phase, so `status=trashed` behavior for both endpoints is confirmed by reading `FileService`/`FolderController`/`FolderService` source directly (including the shallow, non-cascading semantics of both permanent-delete methods, which is why the confirm dialog's copy doesn't claim to cascade-delete contents), not exercised end-to-end - same disclosed limitation as UI-08 through UI-13.

  This closes out the entire `UI_IMPLEMENTATION_PLAN.md` phase list (UI-00 through UI-14).

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
