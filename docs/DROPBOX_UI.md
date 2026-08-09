# Mature Dropbox / Google Drive UX Requirements

The UI must feel like a mature consumer file-storage product, not a CRUD/admin dashboard.

## Visual Design
- Clean, minimal, spacious layout
- Strong visual hierarchy
- Consistent typography, spacing, border radius and iconography
- Avoid excessive cards, borders, gradients, or decorative elements
- Use subtle borders/shadows only where they improve hierarchy
- Professional neutral color palette with one consistent accent color
- Consistent file/folder icon treatment
- Desktop-first, but responsive

## File Browser UX
- Breadcrumbs should always clearly show the current location
- Folder/file rows should have comfortable click targets
- Single click selects
- Double click opens
- Context menu / `...` menu for actions
- Selection state must be visually obvious
- Support multi-select where practical
- Keyboard-friendly interactions
- Hover actions should appear naturally without clutter
- Column headers should be clear in list view
- Preserve sorting/filter state when navigating where practical

## Drag & Drop UX
- Dragging files from OS → clear upload drop zone
- Dragging existing files/folders → visually identify valid folder targets
- Invalid drop targets should be clearly indicated
- Use subtle drag preview
- Show destination before completing a move where appropriate
- Never make destructive actions happen accidentally through drag/drop

## Upload UX
- Upload panel should remain accessible while browsing
- Uploads must not block the whole application
- Show individual progress
- Show completed/failed states
- Failed uploads should be easy to retry
- Allow dismissing completed uploads
- Show a concise overall upload status
- Don't use fake progress when real progress is available

## Modals / Menus
- Modals should be focused and lightweight
- Clear title + explanation + primary/secondary actions
- Destructive actions use clear warning treatment
- Escape closes dialogs
- Clicking outside closes non-destructive dialogs where appropriate
- Context menus should close after an action
- Avoid nested modal chains

## Feedback
Every mutation should give immediate feedback:
- Rename → success/error toast
- Move → success/error toast
- Upload → progress + completion
- Trash → confirmation/success
- Restore → success
- Share → link copied/success
- Revoke → success/error

Do not overuse toast notifications for simple navigation.

## Loading / Empty / Error States
Never leave the user looking at a blank screen.

Provide:
- Skeleton loading for main file lists
- Useful empty-folder state with "Upload" / "Create folder"
- Empty Trash state
- Empty Recent/Photos/Videos state
- Friendly error state with Retry
- Network/service unavailable state

## Navigation
- Sidebar clearly indicates the active section
- Breadcrumbs are clickable
- Back navigation should behave naturally
- Preserve folder context after mutations
- Avoid full-page reloads for normal operations

## File Preview / Details
- Clicking/opening a file should feel natural
- Images should have a clean preview
- File details should show useful metadata without overwhelming the user
- Version history and sharing should be accessible from a clear details/action surface

## Destructive Actions
Use a hierarchy:

Normal:
Download / Open / Share / Rename / Move

Destructive:
Move to Trash

Highly destructive:
Permanent Delete

Permanent Delete must require explicit confirmation.

## Performance Feel
The application should feel fast even when backend operations are asynchronous:
- Optimistic UI where safe
- Immediate visual feedback
- Don't freeze the browser during uploads
- Avoid unnecessary full-page reloads
- Refresh only affected lists/components
- Preserve scroll position where practical

## Accessibility
- Keyboard navigation for major actions
- Visible focus states
- Proper button/label semantics
- Sufficient contrast
- Don't rely on color alone to communicate state
- Accessible dialog and menu behavior

## Important Product Principle

The user should not need to understand the backend architecture.

Do NOT expose:
- Kafka
- Redis
- Outbox
- Microservices
- Internal IDs
- Internal service errors
- Object keys

The product should simply feel like:

"Upload → file appears → organize → share → download → manage versions."

The sophisticated backend should remain invisible underneath the experience.