package com.doujinmenu.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.doujinmenu.android.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val groupColors = listOf(Color(0xFF75CFFA), Color(0xFFF6C85F), Color(0xFF93D6A1),
    Color(0xFFC5A4ED), Color(0xFFF3A6B6), Color(0xFFB4BCC5))

@Composable
internal fun BrowserTabsOverview(
    workspace: BrowserWorkspace,
    isGallery: Boolean,
    previews: BrowserTabPreviewCache,
    onSelect: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onUpdate: (BrowserWorkspace) -> Unit,
    onDismiss: () -> Unit,
) {
    var groupsOnly by rememberSaveable { mutableStateOf(false) }
    var openGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<BrowserTabGroup?>(null) }
    var creatingGroup by remember { mutableStateOf(false) }
    var movingTabs by remember { mutableStateOf<Set<String>?>(null) }
    var deletingGroup by remember { mutableStateOf<BrowserTabGroup?>(null) }
    var dragWorkspace by remember { mutableStateOf<BrowserWorkspace?>(null) }
    val displayedWorkspace = dragWorkspace ?: workspace
    val fallback = if (isGallery) BrowserPage.LibraryHome() else BrowserPage.Search()
    val openGroup = displayedWorkspace.groups.firstOrNull { it.id == openGroupId }
    val groupTabs = if (openGroup?.isArchived == true) openGroup.archivedTabs else displayedWorkspace.tabs.filter { it.groupId == openGroupId }
    val baseItems = when {
        openGroup != null -> groupTabs.map { BrowserOverviewItem.Tab(it) }
        groupsOnly -> displayedWorkspace.groups.filter { it.isArchived }.map { BrowserOverviewItem.Group(it, it.archivedTabs) }
        else -> displayedWorkspace.overviewItems
    }
    val items = baseItems.filter { item -> when (item) {
        is BrowserOverviewItem.Tab -> item.tab.currentPage.toolbarTitle.contains(query, ignoreCase = true)
        is BrowserOverviewItem.Group -> item.group.name.contains(query, ignoreCase = true) ||
            item.tabs.any { it.currentPage.toolbarTitle.contains(query, ignoreCase = true) }
    } }
    val latestWorkspace by rememberUpdatedState(workspace)
    val latestUpdate by rememberUpdatedState(onUpdate)
    val grid = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableStateOf(Offset.Zero) }
    var dragSize by remember { mutableStateOf(IntSize.Zero) }
    var hover by remember { mutableStateOf<String?>(null) }
    var mergeTarget by remember { mutableStateOf<String?>(null) }

    fun dragAt(position: Offset) {
        pointer = position
        val key = dragging ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != key && position.x >= info.offset.x && position.x < info.offset.x + info.size.width &&
                position.y >= info.offset.y && position.y < info.offset.y + info.size.height
        }
        val current = dragWorkspace ?: latestWorkspace
        val canGroup = openGroupId == null && !groupsOnly && key.startsWith("tab:")
        val inCenter = target != null && isTabDropCenter(position.x - target.offset.x, position.y - target.offset.y,
            target.size.width.toFloat(), target.size.height.toFloat())
        if (canGroup && inCenter) hover = target.key as? String
        else {
            hover = null; mergeTarget = null
            val targetKey = target?.key as? String ?: return
            val next = when {
                openGroupId != null -> current.moveTabTo(key.removePrefix("tab:"), targetKey.removePrefix("tab:"))
                groupsOnly -> current.moveArchivedGroup(key.removePrefix("group:"), targetKey.removePrefix("group:"))
                else -> current.moveOverviewItem(key, targetKey)
            }
            if (next != current) dragWorkspace = next
        }
    }
    val latestDragAt by rememberUpdatedState<(Offset) -> Unit>(::dragAt)
    LaunchedEffect(hover) {
        mergeTarget = null
        if (hover != null) { delay(600); mergeTarget = hover }
    }
    LaunchedEffect(dragging) {
        while (isActive && dragging != null) {
            val speed = when {
                pointer.y < 72f -> -18f
                pointer.y > grid.layoutInfo.viewportEndOffset - 72f -> 18f
                else -> 0f
            }
            if (speed != 0f) { grid.scrollBy(speed); latestDragAt(pointer) }
            delay(16)
        }
    }
    LaunchedEffect(workspace.tabs, workspace.groups) {
        selected = selected.intersect(workspace.tabs.mapTo(mutableSetOf()) { it.id })
        if (openGroupId != null && openGroup == null) openGroupId = null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (openGroup != null) IconButton(onClick = { openGroupId = null },
                        modifier = Modifier.semantics { contentDescription = "모든 탭으로 돌아가기" }) { Text("‹") }
                    else FilledTonalIconButton(onClick = {
                        onUpdate(workspace.openTab(fallback, groupId = null)); onDismiss()
                    }, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "새 탭" }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                    if (openGroup != null) {
                        Text(openGroup.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (openGroup.isArchived) TextButton(onClick = {
                            onUpdate(workspace.restoreGroup(openGroup.id)); onDismiss()
                        }) { Text("전체 복원") }
                    } else {
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.padding(4.dp)) {
                        TextButton(onClick = { groupsOnly = false; selecting = false; selected = emptySet() }) {
                            Text("탭 ${workspace.tabs.size}", color = if (!groupsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { groupsOnly = true; selecting = false; selected = emptySet() }) {
                            Text("그룹 ${workspace.groups.count { it.isArchived }}", color = if (groupsOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    if (openGroup != null && !openGroup.isArchived) IconButton(onClick = {
                        onUpdate(workspace.openTab(fallback, groupId = openGroupId)); onDismiss()
                    }, modifier = Modifier.semantics { contentDescription = "새 탭" }) { Text("+", style = MaterialTheme.typography.headlineMedium) }
                    if (!groupsOnly || openGroup != null) Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.semantics { contentDescription = "탭 관리 메뉴" }) { Text("⋮") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!groupsOnly && openGroup?.isArchived != true) DropdownMenuItem(text = { Text(if (selecting) "선택 끝내기" else "탭 선택") }, onClick = {
                                selecting = !selecting; selected = emptySet(); menuOpen = false
                            })
                            if (openGroup != null) {
                                DropdownMenuItem(text = { Text("이름·색 변경") }, onClick = { editingGroup = openGroup; menuOpen = false })
                                if (!openGroup.isArchived) {
                                    DropdownMenuItem(text = { Text("그룹 보관") }, onClick = {
                                        onUpdate(workspace.archiveGroup(openGroup.id, fallback)); openGroupId = null; menuOpen = false
                                    })
                                    DropdownMenuItem(text = { Text("그룹 해제") }, onClick = { onUpdate(workspace.ungroup(openGroup.id)); openGroupId = null; menuOpen = false })
                                } else DropdownMenuItem(text = { Text("그룹 삭제") }, onClick = { deletingGroup = openGroup; menuOpen = false })
                            }
                        }
                    } else Spacer(Modifier.size(48.dp))
                }
                OutlinedTextField(query, onValueChange = { query = it }, label = { Text("탭 검색") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(28.dp))
                if (selecting) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${selected.size}개 선택", Modifier.weight(1f))
                    TextButton(onClick = { creatingGroup = true }, enabled = selected.isNotEmpty()) { Text("그룹 만들기") }
                    TextButton(onClick = { movingTabs = selected }, enabled = selected.isNotEmpty()) { Text("이동") }
                }
                Text(if (groupsOnly) "보관한 그룹을 열어 전체 복원할 수 있습니다."
                    else "길게 눌러 이동 · 좌우 스와이프로 탭 닫기 / 그룹 보관",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), state = grid, contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().pointerInput(selecting, openGroupId, groupsOnly, openGroup?.isArchived) {
                            if (!selecting && openGroup?.isArchived != true) detectDragGesturesAfterLongPress(
                                onDragStart = { position ->
                                    val info = grid.layoutInfo.visibleItemsInfo.firstOrNull {
                                        position.x >= it.offset.x && position.x < it.offset.x + it.size.width &&
                                            position.y >= it.offset.y && position.y < it.offset.y + it.size.height
                                    }
                                    dragging = info?.key as? String; pointer = position
                                    dragWorkspace = latestWorkspace
                                    dragSize = info?.size ?: IntSize.Zero
                                    grabOffset = info?.let { position - Offset(it.offset.x.toFloat(), it.offset.y.toFloat()) } ?: Offset.Zero
                                    if (dragging != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount -> if (dragging != null) { change.consume(); latestDragAt(pointer + amount) } },
                                onDragCancel = { dragging = null; hover = null; mergeTarget = null; dragWorkspace = null },
                                onDragEnd = {
                                    val source = dragging
                                    val target = mergeTarget?.takeIf { it == hover }
                                    val ordered = latestWorkspace.commitTabDragOrder(dragWorkspace ?: latestWorkspace)
                                    val result = when {
                                        source != null && target != null -> ordered.mergeTabDrop(source, target)
                                        source != null && hover != null -> ordered.moveOverviewItem(source, requireNotNull(hover))
                                        else -> ordered
                                    }
                                    latestUpdate(result)
                                    dragging = null; hover = null; mergeTarget = null; dragWorkspace = null
                                },
                            )
                        }) {
                        items(items, key = { it.key }) { item ->
                            val memberIds = when (item) {
                                is BrowserOverviewItem.Tab -> setOf(item.tab.id)
                                is BrowserOverviewItem.Group -> item.tabs.mapTo(mutableSetOf()) { it.id }
                            }
                            OverviewCard(item, workspace, previews, isGallery,
                                selected = selecting && selected.containsAll(memberIds), merging = mergeTarget == item.key,
                                canSwipe = !selecting && dragging == null && openGroup?.isArchived != true && !groupsOnly,
                                modifier = Modifier.animateItem().graphicsLayer { alpha = if (dragging == item.key) 0f else 1f },
                                onClick = {
                                    if (selecting) selected = if (selected.containsAll(memberIds)) selected - memberIds else selected + memberIds
                                    else when (item) {
                                        is BrowserOverviewItem.Tab -> if (openGroup?.isArchived == true) {
                                            onUpdate(workspace.restoreGroup(openGroup.id).selectTab(item.tab.id)); onDismiss()
                                        } else onSelect(item.tab.id)
                                        is BrowserOverviewItem.Group -> { openGroupId = item.group.id; query = "" }
                                    }
                                },
                                onDismiss = { when (item) {
                                    is BrowserOverviewItem.Tab -> onCloseTab(item.tab.id)
                                    is BrowserOverviewItem.Group -> onUpdate(workspace.archiveGroup(item.group.id, fallback))
                                } },
                                onEditGroup = { editingGroup = it }, onUngroup = { onUpdate(workspace.ungroup(it)) },
                                onDeleteGroup = { deletingGroup = it }, onMoveTab = { movingTabs = setOf(it) },
                                onDetachTab = { onUpdate(workspace.assignTabToGroup(it, null)) }, archived = openGroup?.isArchived == true)
                        }
                    }
                    items.firstOrNull { it.key == dragging }?.let { item ->
                        OverviewCard(item, displayedWorkspace, previews, isGallery, selected = false, merging = false, canSwipe = false,
                            modifier = Modifier.zIndex(2f)
                                .offset { IntOffset((pointer.x - grabOffset.x).toInt(), (pointer.y - grabOffset.y).toInt()) }
                                .width(with(density) { dragSize.width.toDp() })
                                .graphicsLayer { scaleX = 1.04f; scaleY = 1.04f; shadowElevation = 16f },
                            onClick = {}, onDismiss = {}, onEditGroup = {}, onUngroup = {}, onDeleteGroup = {},
                            onMoveTab = {}, onDetachTab = {}, archived = openGroup?.isArchived == true)
                    }
                    if (items.isEmpty()) Text(if (groupsOnly) "보관한 그룹이 없습니다." else "일치하는 탭이 없습니다.", Modifier.align(Alignment.Center))
                }
            }
        }
        BackHandler { if (openGroupId != null) openGroupId = null else onDismiss() }
        if (creatingGroup || editingGroup != null) GroupEditor(editingGroup, onDismiss = { creatingGroup = false; editingGroup = null }) { name, color ->
            onUpdate(editingGroup?.let { workspace.updateGroup(it.id, name, color) } ?: workspace.createGroup(selected, name, color))
            creatingGroup = false; editingGroup = null; selecting = false; selected = emptySet()
        }
        movingTabs?.let { ids ->
            AlertDialog(onDismissRequest = { movingTabs = null }, title = { Text("그룹으로 이동") }, text = {
                Column {
                    TextButton(onClick = { selected = ids; creatingGroup = true; movingTabs = null }) { Text("새 그룹 만들기") }
                    workspace.groups.filterNot { it.isArchived }.forEach { group ->
                        TextButton(onClick = {
                            onUpdate(ids.fold(workspace) { current, id -> current.assignTabToGroup(id, group.id) })
                            movingTabs = null; selecting = false; selected = emptySet()
                        }) { Text(group.name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { movingTabs = null }) { Text("취소") } })
        }
        deletingGroup?.let { group ->
            AlertDialog(onDismissRequest = { deletingGroup = null }, title = { Text("보관 그룹 삭제") },
                text = { Text("${group.name} 그룹의 저장된 탭 기록을 삭제합니다.") },
                confirmButton = { TextButton(onClick = { onUpdate(workspace.deleteArchivedGroup(group.id)); deletingGroup = null; openGroupId = null }) { Text("삭제") } },
                dismissButton = { TextButton(onClick = { deletingGroup = null }) { Text("취소") } })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewCard(
    item: BrowserOverviewItem, workspace: BrowserWorkspace, previews: BrowserTabPreviewCache, isGallery: Boolean,
    selected: Boolean, merging: Boolean, canSwipe: Boolean, modifier: Modifier,
    onClick: () -> Unit, onDismiss: () -> Unit, onEditGroup: (BrowserTabGroup) -> Unit,
    onUngroup: (String) -> Unit, onDeleteGroup: (BrowserTabGroup) -> Unit,
    onMoveTab: (String) -> Unit, onDetachTab: (String) -> Unit, archived: Boolean,
) {
    var menu by remember { mutableStateOf(false) }
    val group = (item as? BrowserOverviewItem.Group)?.group
    val tabs = when (item) { is BrowserOverviewItem.Tab -> listOf(item.tab); is BrowserOverviewItem.Group -> item.tabs }
    val active = tabs.any { it.id == workspace.activeTabId } && group?.isArchived != true && !archived
    val color = group?.let { groupColors[it.colorIndex.coerceIn(groupColors.indices)] } ?: MaterialTheme.colorScheme.primary
    val dismiss = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.35f })
    val latestDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) latestDismiss() }
    SwipeToDismissBox(state = dismiss, modifier = modifier, enableDismissFromStartToEnd = canSwipe, enableDismissFromEndToStart = canSwipe,
        backgroundContent = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (group == null) "닫기" else "그룹 보관") } }) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(0.72f), shape = RoundedCornerShape(22.dp),
            color = if (selected || active || merging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = if (active || selected || merging) BorderStroke(if (merging) 4.dp else 2.dp, color) else null) {
            Column(Modifier.padding(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (group != null) Box(Modifier.padding(start = 6.dp).size(10.dp).background(color, CircleShape))
                    Text(if (group != null) "${group.name} · ${tabs.size}" else tabs.first().currentPage.toolbarTitle,
                        Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    if (group != null || !archived) Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp).semantics { contentDescription = "탭 작업" }) { Text("⋮") }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            if (group != null) {
                                DropdownMenuItem(text = { Text("이름·색 변경") }, onClick = { menu = false; onEditGroup(group) })
                                if (group.isArchived) DropdownMenuItem(text = { Text("그룹 삭제") }, onClick = { menu = false; onDeleteGroup(group) })
                                else {
                                    DropdownMenuItem(text = { Text("그룹 해제") }, onClick = { menu = false; onUngroup(group.id) })
                                    DropdownMenuItem(text = { Text("그룹 보관") }, onClick = { menu = false; onDismiss() })
                                }
                            } else if (!archived) {
                                val tab = tabs.first()
                                DropdownMenuItem(text = { Text("그룹으로 이동") }, onClick = { menu = false; onMoveTab(tab.id) })
                                if (tab.groupId != null) DropdownMenuItem(text = { Text("그룹에서 빼기") }, onClick = { menu = false; onDetachTab(tab.id) })
                                DropdownMenuItem(text = { Text("탭 닫기") }, onClick = { menu = false; onDismiss() })
                            }
                        }
                    }
                    if (group == null && !archived) IconButton(onClick = onDismiss,
                        modifier = Modifier.size(40.dp).semantics { contentDescription = "탭 닫기" }) { Text("×") }
                }
                if (group == null) PreviewImage(tabs.first(), previews, isGallery, Modifier.fillMaxWidth().weight(1f))
                else Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(2) { row -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(2) { column ->
                            val tab = tabs.getOrNull(row * 2 + column)
                            if (tab == null) Spacer(Modifier.weight(1f)) else PreviewImage(tab, previews, isGallery, Modifier.weight(1f).fillMaxHeight())
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun PreviewImage(tab: BrowserTab, previews: BrowserTabPreviewCache, isGallery: Boolean, modifier: Modifier) {
    val preview = previews.preview(isGallery, tab)
    val modified = preview?.lastModified()
    val context = LocalContext.current
    val request = remember(preview, modified, context) {
        preview?.let { ImageRequest.Builder(context).data(it).memoryCacheKey("${it.absolutePath}:$modified").build() }
    }
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        if (request != null) AsyncImage(model = request, contentDescription = "${tab.currentPage.toolbarTitle} 마지막 화면",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else Text(tab.currentPage.toolbarTitle, Modifier.padding(12.dp), maxLines = 4, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GroupEditor(group: BrowserTabGroup?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var name by remember(group?.id) { mutableStateOf(group?.name.orEmpty()) }
    var color by remember(group?.id) { mutableIntStateOf(group?.colorIndex ?: 0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (group == null) "새 그룹" else "그룹 편집") }, text = {
        Column {
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("그룹 이름") }, singleLine = true)
            Row(Modifier.padding(top = 12.dp)) {
                groupColors.forEachIndexed { index, value ->
                    IconButton(onClick = { color = index }, modifier = Modifier.weight(1f).semantics { contentDescription = "그룹 색상 ${index + 1}" }) {
                        Surface(Modifier.size(28.dp), shape = CircleShape, color = value,
                            border = if (color == index) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null) {}
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(name, color) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}

internal fun isTabDropCenter(x: Float, y: Float, width: Float, height: Float): Boolean =
    x in width * 0.25f..width * 0.75f && y in height * 0.25f..height * 0.75f

internal fun BrowserWorkspace.mergeTabDrop(sourceKey: String, targetKey: String): BrowserWorkspace {
    val tabId = sourceKey.removePrefix("tab:")
    if (!sourceKey.startsWith("tab:") || tabs.none { it.id == tabId }) return this
    return when {
        targetKey.startsWith("group:") -> assignTabToGroup(tabId, targetKey.removePrefix("group:"))
        targetKey.startsWith("tab:") && targetKey != sourceKey -> createGroup(setOf(tabId, targetKey.removePrefix("tab:")))
        else -> this
    }
}

internal fun BrowserWorkspace.commitTabDragOrder(draft: BrowserWorkspace): BrowserWorkspace {
    val tabPositions = draft.tabs.withIndex().associate { it.value.id to it.index }
    val groupPositions = draft.groups.withIndex().associate { it.value.id to it.index }
    return copy(
        tabs = tabs.sortedBy { tabPositions[it.id] ?: Int.MAX_VALUE },
        groups = groups.sortedBy { groupPositions[it.id] ?: Int.MAX_VALUE },
        overviewOrder = draft.overviewOrder,
    )
}

private fun BrowserWorkspace.moveArchivedGroup(groupId: String, targetId: String): BrowserWorkspace {
    val from = groups.indexOfFirst { it.id == groupId && it.isArchived }
    val to = groups.indexOfFirst { it.id == targetId && it.isArchived }
    if (from < 0 || to < 0 || from == to) return this
    val reordered = groups.toMutableList()
    reordered.add(to, reordered.removeAt(from))
    return copy(groups = reordered)
}
