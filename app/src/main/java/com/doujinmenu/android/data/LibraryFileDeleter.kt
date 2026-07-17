package com.doujinmenu.android.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.doujinmenu.android.model.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryFileDeleter(context: Context) {
    private val contentResolver = context.contentResolver

    suspend fun delete(book: LibraryBook) = withContext(Dispatchers.IO) {
        require(!book.isCloud) { "로컬 갤러리만 직접 삭제할 수 있습니다." }
        val targetUri = Uri.parse(book.folderUri)
        require(targetUri.scheme == "content") { "삭제할 로컬 파일 URI가 올바르지 않습니다." }

        val targetDocumentId = DocumentsContract.getDocumentId(targetUri)
        val libraryRootId = DocumentsContract.getTreeDocumentId(Uri.parse(book.locationUri))
        require(targetDocumentId != libraryRootId) {
            "라이브러리 최상위 폴더는 안전을 위해 삭제할 수 없습니다. 하위 폴더로 옮긴 뒤 다시 시도해주세요."
        }

        check(DocumentsContract.deleteDocument(contentResolver, targetUri)) {
            "저장소 제공자가 파일 삭제를 거부했습니다."
        }
    }
}
