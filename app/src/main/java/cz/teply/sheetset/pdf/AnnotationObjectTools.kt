package cz.teply.sheetset.pdf

internal data class PersistedObjectTool(
    val id: String,
    val readerTool: ReaderTool,
)

internal val PERSISTED_OBJECT_TOOLS = listOf(
    PersistedObjectTool("select", ReaderTool.SELECT),
    PersistedObjectTool("lasso", ReaderTool.LASSO),
    PersistedObjectTool("text-box", ReaderTool.TEXT_BOX),
    PersistedObjectTool("symbol", ReaderTool.SYMBOL),
    PersistedObjectTool("underline", ReaderTool.UNDERLINE),
    PersistedObjectTool("strike-through", ReaderTool.STRIKE_THROUGH),
    PersistedObjectTool("line", ReaderTool.LINE),
    PersistedObjectTool("arrow", ReaderTool.ARROW),
    PersistedObjectTool("rectangle", ReaderTool.RECTANGLE),
    PersistedObjectTool("ellipse", ReaderTool.ELLIPSE),
)

internal val DEFAULT_OBJECT_TOOL_ORDER = PERSISTED_OBJECT_TOOLS.map(PersistedObjectTool::id)

internal fun ReaderTool.persistedObjectToolId(): String? =
    PERSISTED_OBJECT_TOOLS.firstOrNull { it.readerTool == this }?.id
