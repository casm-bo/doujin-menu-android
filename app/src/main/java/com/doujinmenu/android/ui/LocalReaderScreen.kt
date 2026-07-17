package com.doujinmenu.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doujinmenu.android.model.LibraryBook
import com.doujinmenu.android.model.ViewerPreferences

@Composable
fun LocalReaderScreen(
    book: LibraryBook?,
    initialPage: Int,
    favorite: Boolean,
    preferences: ViewerPreferences,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onProgress: (Int) -> Unit,
    nextBookTitle: String?,
    onOpenNextBook: () -> Unit,
    onPreferencesChange: (ViewerPreferences) -> Unit,
) {
    HideSystemBars()
    if (book == null || book.pages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("책을 열 수 없습니다.", color = Color.White)
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("돌아가기") }
            }
        }
        return
    }

    val context = LocalContext.current
    val pages = remember(book.id, book.pages, book.cloudToken) {
        book.pages.map { page ->
            ReaderPageModel(
                key = page.uri,
                model = libraryImageRequest(context, page.uri, book.cloudToken),
            )
        }
    }
    UnifiedReader(
        readerKey = "local:${book.id}",
        title = book.title,
        pages = pages,
        initialPage = initialPage,
        preferences = preferences,
        onBack = onBack,
        onProgress = onProgress,
        onPreferencesChange = onPreferencesChange,
        favorite = favorite,
        onToggleFavorite = onToggleFavorite,
        nextBookTitle = nextBookTitle,
        onOpenNextBook = onOpenNextBook,
    )
}
