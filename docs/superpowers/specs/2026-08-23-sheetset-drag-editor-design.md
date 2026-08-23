# SheetSet drag ordering and annotation dock design

## Goal

Make setlist ordering direct and make annotation controls match the interaction model verified in ScorePDF 16.0.2 while preserving SheetSet's monochrome visual system.

## Setlist ordering

- Edit mode replaces visible up/down arrows with a three-line drag handle.
- Dragging the handle moves the row immediately and scrolls the list near its edges.
- The final move is persisted once when the pointer is released.
- Delete remains a separate action.
- Screen readers retain move up/down custom actions even though those buttons are no longer visible.

## Annotation dock

- Annotation mode always uses a two-row bottom dock on phones and tablets.
- The upper row keeps page navigation at the edges. Its scrollable center contains stroke width, a straight-line toggle, and eight direct color swatches.
- The lower row switches between Draw and Add groups. Draw contains pen, highlighter, and eraser. Add contains select, underline, strike-through, text, line, arrow, rectangle, and ellipse.
- Undo, redo, and Done remain fixed at the trailing edge.
- Pen and highlighter support freehand strokes. Straight-line mode reduces either stroke to its first and last points.
- Highlighter strokes use the configured opacity and a wider stroke.
- Color is visible and selectable in one tap. Color is the only non-monochrome toolbar element.

## Responsive and accessibility rules

- Touch targets are at least 48 dp.
- Narrow screens scroll only the tool or color sections; navigation and completion actions remain fixed.
- Tablet layout keeps the same bottom interaction instead of switching to a vertical side rail.
- Selected tools, modes, colors, and straight-line state expose Compose selected semantics.

## Release acceptance

- Reordering works from top to bottom and persists after reopening the setlist.
- Freehand pen, freehand highlighter, straight strokes, colors, undo, redo, and page navigation work in the editor.
- Existing text, markup, shape, resize, export, and reader navigation workflows still work.
- Unit tests, Android lint, debug/release builds, and focused emulator tests pass.
- GitHub release `v0.3.0-alpha.2` contains the APK, SHA-256 checksum, and user-facing release notes.
