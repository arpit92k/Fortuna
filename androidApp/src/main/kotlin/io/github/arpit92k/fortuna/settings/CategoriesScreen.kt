package io.github.arpit92k.fortuna.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.arpit92k.fortuna.R
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.ui.IconTile
import io.github.arpit92k.fortuna.ui.categoryIcon

/**
 * Categories (UF-08), from Settings: the assets, then the debts, then any
 * archived. A category is added as an asset or a debt; tapping one renames
 * it, changes its kind while it has no sources, or archives it once no open
 * source is in it. An archived one can be brought back.
 */
@Composable
fun CategoriesScreen(
    ui: CategoriesUi?,
    onAdd: (name: String, kind: CategoryKind, onDone: () -> Unit) -> Unit,
    onEdit: (id: CategoryId, name: String, kind: CategoryKind, onDone: () -> Unit) -> Unit,
    onArchive: (id: CategoryId, onDone: () -> Unit) -> Unit,
    onRestore: (CategoryId) -> Unit,
    onClearProblem: () -> Unit,
    onBack: () -> Unit,
) {
    // Which category is being edited, by id; NEW for a new one. Kept as text so it survives rotation.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    Page(stringResource(R.string.categories_title), onBack, "categories") {
        if (ui == null) return@Page
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Button(
                    onClick = { onClearProblem(); editing = NEW },
                    modifier = Modifier.padding(8.dp).testTag("category-add"),
                ) { Text(stringResource(R.string.categories_add)) }
            }
            for ((kind, heading) in listOf(CategoryKind.ASSET to R.string.categories_assets, CategoryKind.LIABILITY to R.string.categories_debts)) {
                val rows = ui.active.filter { it.kind == kind }
                if (rows.isEmpty()) continue
                item(key = "heading-$kind") { Heading(stringResource(heading)) }
                items(rows, key = { it.id.value }) { row ->
                    CategoryItem(row, onClick = { onClearProblem(); editing = row.id.value.toString() })
                    HorizontalDivider()
                }
            }
            if (ui.archived.isNotEmpty()) {
                item(key = "heading-archived") { Heading(stringResource(R.string.categories_archived)) }
                items(ui.archived, key = { it.id.value }) { row ->
                    CategoryItem(row, onClick = null) {
                        TextButton(onClick = { onRestore(row.id) }, modifier = Modifier.testTag("category-restore-${row.id.value}")) {
                            Text(stringResource(R.string.categories_restore))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    // Restoring has no dialog of its own, so its refusal is shown here.
    val problem = ui?.problem
    if (problem != null && editing == null) {
        AlertDialog(
            onDismissRequest = onClearProblem,
            text = { Text(problemText(problem)) },
            confirmButton = { TextButton(onClick = onClearProblem) { Text(stringResource(R.string.date_ok)) } },
        )
    }

    val key = editing ?: return
    if (ui == null) return
    val close = { editing = null; onClearProblem() }
    if (key == NEW) {
        CategoryDialog(
            row = null,
            problem = problem,
            onSave = { name, kind -> onAdd(name, kind) { editing = null } },
            onArchive = null,
            onDismiss = close,
        )
    } else {
        val row = ui.active.firstOrNull { it.id.value.toString() == key } ?: return
        CategoryDialog(
            row = row,
            problem = problem,
            onSave = { name, kind -> onEdit(row.id, name, kind) { editing = null } },
            onArchive = { onArchive(row.id) { editing = null } },
            onDismiss = close,
        )
    }
}

private const val NEW = "new"

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CategoryItem(row: CategoryRow, onClick: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(8.dp, 12.dp)
            .testTag("category-${row.id.value}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categoryIcon(row.name, row.id)?.let { IconTile(it, size = 40.dp) }
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.categories_count, row.openSources, row.openSources) +
                    if (row.closedSources > 0) " · " + pluralStringResource(R.plurals.categories_closed_count, row.closedSources, row.closedSources) else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

/** Adding a category, or with [row], editing one: its name, its kind while it can change, and archiving. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    row: CategoryRow?,
    problem: CategoryProblem?,
    onSave: (String, CategoryKind) -> Unit,
    onArchive: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(row?.name.orEmpty()) }
    var kind by rememberSaveable { mutableStateOf(row?.kind ?: CategoryKind.ASSET) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (row == null) R.string.categories_add_title else R.string.categories_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().testTag("category-name"),
                )
                val fixed = row?.kindFixed == true
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().testTag("category-kind")) {
                    listOf(CategoryKind.ASSET to R.string.categories_kind_asset, CategoryKind.LIABILITY to R.string.categories_kind_debt)
                        .forEachIndexed { index, (choice, label) ->
                            SegmentedButton(
                                selected = kind == choice,
                                onClick = { kind = choice },
                                enabled = !fixed,
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                modifier = Modifier.testTag("category-kind-$choice"),
                            ) { Text(stringResource(label)) }
                        }
                }
                if (fixed) {
                    Text(
                        stringResource(R.string.categories_kind_fixed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (row != null && onArchive != null) {
                    TextButton(onClick = onArchive, modifier = Modifier.testTag("category-archive")) {
                        Text(stringResource(R.string.categories_archive))
                    }
                }
                problem?.let {
                    Text(problemText(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("category-problem"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, kind) }, modifier = Modifier.testTag("category-save")) {
                Text(stringResource(R.string.categories_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.date_cancel)) } },
        modifier = Modifier.testTag("category-dialog"),
    )
}

@Composable
private fun problemText(problem: CategoryProblem): String = when (problem) {
    CategoryProblem.NoName -> stringResource(R.string.categories_problem_no_name)
    is CategoryProblem.NameInUse -> stringResource(R.string.categories_problem_name_in_use, problem.name)
    is CategoryProblem.HasOpenSources ->
        pluralStringResource(R.plurals.categories_problem_has_sources, problem.count, problem.count)
    CategoryProblem.NotSaved -> stringResource(R.string.add_source_problem_not_saved)
}
