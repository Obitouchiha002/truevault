package com.truevault.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.truevault.core.common.log.SecureLog
import com.truevault.core.model.SelectedSource
import com.truevault.core.model.mimeCategoryOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TreeWalker"

/** Hard ceiling so a pathological tree cannot make a scan run forever. */
private const val MAX_VISITED_DOCUMENTS = 50_000
private const val MAX_DEPTH = 12

/**
 * Walks a folder the user granted through `ACTION_OPEN_DOCUMENT_TREE`.
 *
 * This is the only way TrueVault ever enumerates files, and it can only see inside a tree the user
 * explicitly picked. There is no path here that reaches other apps' storage, and none that widens
 * the grant it was given.
 *
 * The walk is bounded in both breadth and depth: a provider that returns a cyclic or absurdly deep
 * tree stops the scan rather than hanging it.
 */
@Singleton
class DocumentTreeWalker @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun walk(
        treeUri: Uri,
        onProgress: (visited: Int) -> Unit = {},
        cancellationSignal: () -> Boolean = { false },
    ): List<SelectedSource> {
        val results = mutableListOf<SelectedSource>()
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return emptyList()

        val queue = ArrayDeque<Pair<String, Int>>()
        queue += rootId to 0
        var visited = 0
        val seenDocumentIds = HashSet<String>()

        while (queue.isNotEmpty()) {
            if (cancellationSignal() || visited >= MAX_VISITED_DOCUMENTS) break

            val (documentId, depth) = queue.removeFirst()
            if (depth > MAX_DEPTH || !seenDocumentIds.add(documentId)) continue

            val childrenUri = runCatching {
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            }.getOrNull() ?: continue

            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )

            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            if (cancellationSignal()) return results

                            val childId = cursor.getString(0)
                            val name = cursor.getString(1)
                            val mime = cursor.getString(2)
                            val size = if (cursor.isNull(3)) null else cursor.getLong(3)

                            visited++
                            onProgress(visited)

                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                queue += childId to (depth + 1)
                            } else {
                                val childUri = DocumentsContract
                                    .buildDocumentUriUsingTree(treeUri, childId)
                                results += SelectedSource(
                                    uriToken = childUri.toString(),
                                    displayName = name,
                                    sizeBytes = size,
                                    mimeType = mime,
                                    isFromPhotoPicker = false,
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                // One unreadable folder must not abandon the whole scan.
                SecureLog.w(TAG, "A folder could not be listed (${e.javaClass.simpleName})")
            }
        }

        return results
    }

    /** True when the walk stopped because it hit its ceiling rather than finishing. */
    fun wasTruncated(visited: Int): Boolean = visited >= MAX_VISITED_DOCUMENTS

    @Suppress("unused")
    private fun categoryOf(source: SelectedSource) =
        mimeCategoryOf(source.mimeType, source.displayName)
}
