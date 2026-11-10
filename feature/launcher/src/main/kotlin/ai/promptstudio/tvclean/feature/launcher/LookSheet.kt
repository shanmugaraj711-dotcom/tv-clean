package ai.promptstudio.tvclean.feature.launcher

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import ai.promptstudio.tvclean.core.flows.HomeScreenIntent
import ai.promptstudio.tvclean.core.flows.HomeScreenState
import ai.promptstudio.tvclean.ui.design.component.LookEditor
import ai.promptstudio.tvclean.ui.design.component.LookEditorNav
import ai.promptstudio.tvclean.ui.design.component.LookEditorSheet
import ai.promptstudio.tvclean.ui.design.component.LookEditorState
import ai.promptstudio.tvclean.ui.design.component.LookPage
import ai.promptstudio.tvclean.ui.design.component.LookSendFooter
import ai.promptstudio.tvclean.ui.design.component.PhotoPicker
import ai.promptstudio.tvclean.ui.design.component.rememberLookEditorNav
import ai.promptstudio.tvclean.ui.design.component.toEditor

// look2 Look sheet: the Look editor of :ui:design (the same pages as Setup's look step), with the
// Look sheet's footer: "Put it on the TV", the one guarded write (R4-W8), and the way back under
// it. Every tap is a HomeScreenIntent.Look edit, which only changes the draft (HomeScreenMachine
// keeps it); nothing reaches the TV until "Put it on the TV". The sheet closes on it, as in 0.4.0,
// and Home shows the progress; a failed send keeps the draft for Open Look.
@Composable
internal fun LookSheet(state: HomeScreenState, onIntent: (HomeScreenIntent) -> Unit, photos: PhotoPicker? = null) {
    val editor = state.lookEditor() ?: return
    LookEditorSheet(
        editor, onEdit = { onIntent(HomeScreenIntent.Look(it)) },
        onPhoto = { jpeg, dim -> onIntent(HomeScreenIntent.SetOwnPicture(jpeg, dim)) },
        onClose = { onIntent(HomeScreenIntent.CloseLook) },
        footer = { page -> LookFooter(editor, page, onIntent) },
        photos = photos, onUpdateHomeScreen = updateRoute(state, onIntent),
    )
}

// The same editor without the modal window, for the screenshot and behaviour tests. It needs a
// bounded height, as the sheet gives it. `pictures`: the pictures by file (the app reads the files).
@Composable
internal fun LookContent(
    state: HomeScreenState,
    onIntent: (HomeScreenIntent) -> Unit,
    modifier: Modifier = Modifier,
    pictures: Map<String, ImageBitmap>? = null,
    photos: PhotoPicker? = null,
    nav: LookEditorNav = rememberLookEditorNav(),
) {
    val editor = state.lookEditor() ?: return
    LookEditor(
        editor, onEdit = { onIntent(HomeScreenIntent.Look(it)) },
        onPhoto = { jpeg, dim -> onIntent(HomeScreenIntent.SetOwnPicture(jpeg, dim)) },
        onClose = { onIntent(HomeScreenIntent.CloseLook) },
        footer = { page -> LookFooter(editor, page, onIntent) },
        photos = photos, pictures = pictures, onUpdateHomeScreen = updateRoute(state, onIntent), modifier = modifier, nav = nav,
    )
}

@Composable
private fun ColumnScope.LookFooter(editor: LookEditorState, page: LookPage, onIntent: (HomeScreenIntent) -> Unit) =
    LookSendFooter(editor, page, onPut = { onIntent(HomeScreenIntent.UseLook) }, onEdit = { onIntent(HomeScreenIntent.Look(it)) })

// The older home screen banner's Update: closes the sheet and opens the update step, when one is ready.
private fun updateRoute(state: HomeScreenState, onIntent: (HomeScreenIntent) -> Unit): (() -> Unit)? =
    if (state.updateTo == null) null else ({ onIntent(HomeScreenIntent.CloseLook); onIntent(HomeScreenIntent.PreviewUpdate) })

// The sheet's choice as the editor shows it: the one mapping, LookEditorFrom.
internal fun HomeScreenState.lookEditor(): LookEditorState? =
    look?.toEditor(pictures, ownPicture, ownDim, newPicture, newDim, earlierPicture)
