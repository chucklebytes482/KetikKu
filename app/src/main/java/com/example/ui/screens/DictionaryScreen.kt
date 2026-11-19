package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SettingsRepository
import com.example.model.KeyboardSettings
import com.example.model.PersonalDictionaryEntry

@Composable
fun DictionaryScreen(
    settings: KeyboardSettings,
    dictionary: List<PersonalDictionaryEntry>,
    folders: List<String> = emptyList(),
    onUpsertEntry: (word: String, shortcut: String, language: String, existingId: String?, folder: String) -> Unit,
    onDeleteEntry: (entryId: String) -> Unit,
    onCreateFolder: (folderPath: String) -> Unit = {},
    onRenameFolder: (oldPath: String, newName: String) -> Unit = { _, _ -> },
    onMoveItems: (entryIds: Set<String>, folderPaths: Set<String>, targetFolderPath: String) -> Unit = { _, _, _ -> },
    onDeleteItems: (entryIds: Set<String>, folderPaths: Set<String>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var dictSearchQuery by rememberSaveable { mutableStateOf("") }
    var currentFolderPath by rememberSaveable { mutableStateOf("") }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PersonalDictionaryEntry?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var renamingFolderPath by remember { mutableStateOf<String?>(null) }
    var moveRequest by remember { mutableStateOf<Pair<Set<String>, Set<String>>?>(null) }
    var deleteRequest by remember { mutableStateOf<Pair<Set<String>, Set<String>>?>(null) }

    fun clearSelection() {
        selectionMode = false
        selectedEntryIds = emptySet()
        selectedFolderPaths = emptySet()
    }

    BackHandler(enabled = selectionMode || currentFolderPath.isNotEmpty()) {
        when {
            selectionMode -> clearSelection()
            currentFolderPath.isNotEmpty() -> {
                currentFolderPath = currentFolderPath.substringBeforeLast('/', missingDelimiterValue = "")
            }
        }
    }

    val allOrderedFolders = remember(folders, dictionary) {
        SettingsRepository.normalizeOrderedFolders(folders, dictionary)
    }
    val activeEntriesAll = remember(dictionary) {
        dictionary.filter { !it.deleted }
    }

    val q = dictSearchQuery.trim().lowercase()
    val isSearching = q.isNotEmpty()

    // Direct child folders under currentFolderPath (order strictly preserved: Gmail, Nomor, newest custom folders first)
    val visibleFolders = remember(allOrderedFolders, currentFolderPath, q, isSearching) {
        if (isSearching) {
            allOrderedFolders.filter { it.lowercase().contains(q) }
        } else {
            allOrderedFolders.filter { path ->
                val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
                parent == currentFolderPath
            }
        }
    }

    // Entries visible in current folder (or global search across all folders)
    val visibleEntries = remember(activeEntriesAll, currentFolderPath, q, isSearching) {
        if (isSearching) {
            activeEntriesAll
                .filter { entry ->
                    entry.word.lowercase().contains(q) ||
                        entry.shortcut.lowercase().contains(q) ||
                        entry.folder.lowercase().contains(q)
                }
                .sortedBy { it.word.lowercase() }
        } else {
            activeEntriesAll
                .filter { entry ->
                    SettingsRepository.normalizeFolderPath(entry.folder) == currentFolderPath
                }
                .sortedBy { it.word.lowercase() }
        }
    }

    val folderStatsMap = remember(allOrderedFolders, activeEntriesAll) {
        allOrderedFolders.associateWith { folderPath ->
            val directSubfolders = allOrderedFolders.count { sub ->
                sub.substringBeforeLast('/', missingDelimiterValue = "") == folderPath
            }
            val directEntries = activeEntriesAll.count {
                SettingsRepository.normalizeFolderPath(it.folder) == folderPath
            }
            val recursiveEntries = activeEntriesAll.count {
                SettingsRepository.isSameOrDescendantFolder(it.folder, folderPath)
            }
            Triple(directSubfolders, directEntries, recursiveEntries)
        }
    }

    val totalCount = activeEntriesAll.size
    val totalSelectedCount = selectedEntryIds.size + selectedFolderPaths.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = dictSearchQuery,
                    onValueChange = { dictSearchQuery = it },
                    placeholder = { Text("Cari kata, pintasan, atau folder...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cari kamus")
                    },
                    trailingIcon = {
                        if (dictSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { dictSearchQuery = "" },
                                modifier = Modifier.testTag("clear_dictionary_search_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus pencarian kamus")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dictionary_search_input")
                )
            }

            if (currentFolderPath.isNotEmpty() && !isSearching) {
                item {
                    FolderBreadcrumbBar(
                        currentFolderPath = currentFolderPath,
                        onNavigateToFolder = { target ->
                            clearSelection()
                            currentFolderPath = target
                        }
                    )
                }
            }

            if (selectionMode) {
                item {
                    SelectionActionBar(
                        selectedCount = totalSelectedCount,
                        onSelectAll = {
                            selectedFolderPaths = visibleFolders.toSet()
                            selectedEntryIds = visibleEntries.map { it.id }.toSet()
                        },
                        onMoveSelected = {
                            if (totalSelectedCount > 0) {
                                moveRequest = selectedEntryIds to selectedFolderPaths
                            }
                        },
                        onDeleteSelected = {
                            if (totalSelectedCount > 0) {
                                deleteRequest = selectedEntryIds to selectedFolderPaths
                            }
                        },
                        onClose = { clearSelection() }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentFolderPath.isEmpty()) {
                                    "$totalCount kata • ${allOrderedFolders.size} folder"
                                } else {
                                    "${visibleEntries.size} kata • ${visibleFolders.size} subfolder"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Tahan lama item untuk memilih, pindah, atau hapus",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (visibleFolders.isNotEmpty() || visibleEntries.isNotEmpty()) {
                            IconButton(
                                onClick = { selectionMode = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("toggle_selection_mode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Checklist,
                                    contentDescription = "Mode Pilih Banyak",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showNewFolderDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_folder_button")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Folder")
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("add_dictionary_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Kata")
                        }
                    }
                }
            }

            if (visibleFolders.isEmpty() && visibleEntries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (currentFolderPath.isNotEmpty()) Icons.Default.Folder else Icons.Default.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = when {
                                    isSearching -> "Tidak ada folder atau kata yang cocok dengan \"$dictSearchQuery\"."
                                    currentFolderPath.isNotEmpty() -> "Folder \"$currentFolderPath\" masih kosong. Tambah subfolder atau kata di sini."
                                    else -> "Kamus pribadi masih kosong. Tekan Kata atau Folder untuk memulai."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // 1. Folder rows (Utama: Gmail -> Nomor -> Custom folders newest-first; Subfolder: child folders)
                items(visibleFolders, key = { "folder:$it" }) { folderPath ->
                    val stats = folderStatsMap[folderPath] ?: Triple(0, 0, 0)
                    val isSelected = folderPath in selectedFolderPaths
                    FolderRowCard(
                        folderPath = folderPath,
                        displayLabel = if (isSearching) folderPath else folderPath.substringAfterLast('/'),
                        subfolderCount = stats.first,
                        recursiveEntryCount = stats.third,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        onTap = {
                            if (selectionMode) {
                                selectedFolderPaths = if (isSelected) {
                                    selectedFolderPaths - folderPath
                                } else {
                                    selectedFolderPaths + folderPath
                                }
                            } else {
                                dictSearchQuery = ""
                                currentFolderPath = folderPath
                            }
                        },
                        onLongPress = {
                            selectionMode = true
                            selectedFolderPaths = selectedFolderPaths + folderPath
                        },
                        onRename = { renamingFolderPath = folderPath },
                        onMove = { moveRequest = emptySet<String>() to setOf(folderPath) },
                        onDelete = {
                            deleteRequest = emptySet<String>() to setOf(folderPath)
                        }
                    )
                }

                // 2. Entry rows (unfoldered entries below folders at Utama, or entries inside currentFolderPath)
                items(visibleEntries, key = { "entry:${it.id}" }) { entry ->
                    val isSelected = entry.id in selectedEntryIds
                    DictionaryItemCard(
                        entry = entry,
                        showFolderBadge = isSearching,
                        selectionMode = selectionMode,
                        selected = isSelected,
                        onTap = {
                            if (selectionMode) {
                                selectedEntryIds = if (isSelected) {
                                    selectedEntryIds - entry.id
                                } else {
                                    selectedEntryIds + entry.id
                                }
                            } else {
                                editingEntry = entry
                            }
                        },
                        onLongPress = {
                            selectionMode = true
                            selectedEntryIds = selectedEntryIds + entry.id
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAddDialog || editingEntry != null) {
        DictionaryEntryDialog(
            initialEntry = editingEntry,
            defaultFolderPath = currentFolderPath,
            onDismiss = {
                showAddDialog = false
                editingEntry = null
            },
            onSave = { word, shortcut, lang, folder ->
                onUpsertEntry(word, shortcut, lang, editingEntry?.id, folder)
                showAddDialog = false
                editingEntry = null
            }
        )
    }

    if (showNewFolderDialog) {
        FolderNameDialog(
            title = if (currentFolderPath.isEmpty()) "Buat Folder Baru" else "Buat Subfolder di $currentFolderPath",
            initialValue = "",
            confirmLabel = "Buat",
            hint = "Nama folder (boleh pakai / untuk subfolder)",
            onDismiss = { showNewFolderDialog = false },
            onConfirm = { rawName ->
                val combined = if (currentFolderPath.isEmpty()) rawName else "$currentFolderPath/$rawName"
                val norm = SettingsRepository.normalizeFolderPath(combined)
                if (norm.isNotEmpty()) {
                    onCreateFolder(norm)
                }
                showNewFolderDialog = false
            }
        )
    }

    renamingFolderPath?.let { targetOldPath ->
        FolderNameDialog(
            title = "Ubah Nama Folder",
            initialValue = targetOldPath.substringAfterLast('/'),
            confirmLabel = "Simpan",
            hint = "Nama folder baru",
            onDismiss = { renamingFolderPath = null },
            onConfirm = { newLeaf ->
                onRenameFolder(targetOldPath, newLeaf)
                if (SettingsRepository.isSameOrDescendantFolder(currentFolderPath, targetOldPath)) {
                    val parent = targetOldPath.substringBeforeLast('/', missingDelimiterValue = "")
                    val cleaned = newLeaf.replace('/', ' ').replace('\\', ' ').trim()
                    val newPrefix = SettingsRepository.normalizeFolderPath(
                        if (parent.isEmpty()) cleaned else "$parent/$cleaned"
                    )
                    if (newPrefix.isNotEmpty()) {
                        currentFolderPath = SettingsRepository.rewriteFolderPrefix(
                            currentFolderPath,
                            targetOldPath,
                            newPrefix
                        )
                    }
                }
                renamingFolderPath = null
            }
        )
    }

    moveRequest?.let { (entryIds, folderPaths) ->
        val label = buildList {
            if (folderPaths.isNotEmpty()) add("${folderPaths.size} folder")
            if (entryIds.isNotEmpty()) add("${entryIds.size} kata")
        }.joinToString(" & ").ifEmpty { "item" }

        MoveToFolderDialog(
            allFolders = allOrderedFolders,
            excludedFolderPrefixes = folderPaths,
            itemCountLabel = label,
            onDismiss = { moveRequest = null },
            onConfirmMove = { target ->
                onMoveItems(entryIds, folderPaths, target)
                moveRequest = null
                clearSelection()
            }
        )
    }

    deleteRequest?.let { (entryIds, folderPaths) ->
        ConfirmDeleteItemsDialog(
            entryCount = entryIds.size,
            folderCount = folderPaths.size,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                onDeleteItems(entryIds, folderPaths)
                if (folderPaths.any { SettingsRepository.isSameOrDescendantFolder(currentFolderPath, it) }) {
                    currentFolderPath = ""
                }
                deleteRequest = null
                clearSelection()
            }
        )
    }
}

@Composable
private fun FolderBreadcrumbBar(
    currentFolderPath: String,
    onNavigateToFolder: (String) -> Unit
) {
    val segments = remember(currentFolderPath) {
        currentFolderPath.split('/').filter { it.isNotBlank() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag("folder_breadcrumb_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onNavigateToFolder("") }
                .testTag("breadcrumb_root")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Utama",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Utama",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        segments.forEachIndexed { idx, segment ->
            val pathUpToHere = segments.subList(0, idx + 1).joinToString("/")
            val isCurrent = idx == segments.lastIndex
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCurrent) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !isCurrent) { onNavigateToFolder(pathUpToHere) }
            ) {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onMoveSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .testTag("selection_action_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("exit_selection_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = "Keluar dari mode pilih")
            }
            Text(
                text = "$selectedCount dipilih",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("select_all_items_button")
            ) {
                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pilih Semua")
            }
            IconButton(
                onClick = onMoveSelected,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("move_selected_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = "Pindahkan item terpilih",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_selected_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Hapus item terpilih",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRowCard(
    folderPath: String,
    displayLabel: String,
    subfolderCount: Int,
    recursiveEntryCount: Int,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val folderIcon = when (folderPath) {
        SettingsRepository.FOLDER_GMAIL -> Icons.Default.Email
        SettingsRepository.FOLDER_NOMOR -> Icons.Default.Phone
        else -> Icons.Default.Folder
    }
    val subtitle = buildList {
        if (subfolderCount > 0) add("$subfolderCount subfolder")
        add("$recursiveEntryCount kata")
    }.joinToString(" • ")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("folder_item_${folderPath.replace('/', '_')}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (selected) "Terpilih" else "Belum terpilih",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIcon,
                        contentDescription = "Folder $displayLabel",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!selectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("folder_menu_${folderPath.replace('/', '_')}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Opsi folder $displayLabel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Ubah Nama") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Pindahkan") },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus Folder") },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            modifier = Modifier.testTag("folder_delete_${folderPath.replace('/', '_')}")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DictionaryItemCard(
    entry: PersonalDictionaryEntry,
    showFolderBadge: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .testTag("dictionary_item_${entry.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectionMode) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = if (selected) "Terpilih" else "Belum terpilih",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.shortcut.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Pintasan: ${entry.shortcut}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Tanpa pintasan",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (showFolderBadge && entry.folder.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = entry.folder,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
