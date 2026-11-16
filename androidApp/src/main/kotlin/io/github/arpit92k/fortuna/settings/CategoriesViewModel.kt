package io.github.arpit92k.fortuna.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.arpit92k.fortuna.Core
import io.github.arpit92k.fortuna.core.domain.CategoryId
import io.github.arpit92k.fortuna.core.domain.CategoryKind
import io.github.arpit92k.fortuna.core.domain.Dataset
import io.github.arpit92k.fortuna.core.domain.Status
import io.github.arpit92k.fortuna.core.recording.RecordingError
import io.github.arpit92k.fortuna.core.recording.RecordingResult
import io.github.arpit92k.fortuna.core.recording.addCategory
import io.github.arpit92k.fortuna.core.recording.archiveCategory
import io.github.arpit92k.fortuna.core.recording.renameCategory
import io.github.arpit92k.fortuna.core.recording.restoreCategory
import io.github.arpit92k.fortuna.core.recording.setCategoryKind
import io.github.arpit92k.fortuna.core.session.SessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category on the categories screen, with how many sources are in it. */
data class CategoryRow(
    val id: CategoryId,
    val name: String,
    val kind: CategoryKind,
    val openSources: Int,
    val closedSources: Int,
) {
    /** Its kind is fixed once it has sources, closed ones included. */
    val kindFixed: Boolean get() = openSources + closedSources > 0

    /** It can be archived once no open source is in it. */
    val canArchive: Boolean get() = openSources == 0
}

/** Why a change to a category was refused. */
sealed interface CategoryProblem {
    data object NoName : CategoryProblem

    data class NameInUse(val name: String) : CategoryProblem

    /** Archiving is refused while open sources are in it; their number is [count]. */
    data class HasOpenSources(val count: Int) : CategoryProblem

    data object NotSaved : CategoryProblem
}

/** The categories screen (UF-08). */
data class CategoriesUi(
    val active: List<CategoryRow>,
    val archived: List<CategoryRow>,
    val problem: CategoryProblem? = null,
)

/** The categories of [data], active first in their order, then archived. */
fun categoriesOf(data: Dataset): CategoriesUi {
    val rows = data.categories.map { category ->
        val inIt = data.sources.filter { it.categoryId == category.id }
        CategoryRow(
            id = category.id,
            name = category.name,
            kind = category.kind,
            openSources = inIt.count { it.status == Status.ACTIVE },
            closedSources = inIt.count { it.status != Status.ACTIVE },
        ) to category.status
    }
    return CategoriesUi(
        active = rows.filter { it.second == Status.ACTIVE }.map { it.first },
        archived = rows.filter { it.second != Status.ACTIVE }.map { it.first },
    )
}

/**
 * Manage categories (UF-08): add one as an asset or a debt, rename it,
 * change its kind while it has no sources, archive it once no open source is
 * in it, and bring an archived one back. The core decides what is allowed.
 */
class CategoriesViewModel(private val core: Core) : ViewModel() {
    private val mutableUi = MutableStateFlow<CategoriesUi?>(null)
    val ui: StateFlow<CategoriesUi?> = mutableUi.asStateFlow()

    init {
        viewModelScope.launch {
            combine(core.state, core.dataset) { state, data -> data.takeIf { state == SessionState.Unlocked } }
                .collect { data ->
                    mutableUi.update { old -> data?.let { categoriesOf(it).copy(problem = old?.problem) } }
                }
        }
    }

    /** Adds a category; [onDone] runs once it is saved. */
    fun add(name: String, kind: CategoryKind, onDone: () -> Unit = {}) =
        change(onDone) { data -> data.addCategory(name, kind) }

    /** Saves a category's name and, while it has no sources, its kind; [onDone] runs once saved. */
    fun edit(id: CategoryId, name: String, kind: CategoryKind, onDone: () -> Unit = {}) =
        change(onDone) { data ->
            when (val renamed = data.renameCategory(id, name)) {
                is RecordingResult.Success -> renamed.dataset.setCategoryKind(id, kind)
                is RecordingResult.Failure -> renamed
            }
        }

    fun archive(id: CategoryId, onDone: () -> Unit = {}) = change(onDone) { data -> data.archiveCategory(id) }

    fun restore(id: CategoryId) = change(onDone = {}) { data -> data.restoreCategory(id) }

    fun clearProblem() = mutableUi.update { it?.copy(problem = null) }

    private fun change(onDone: () -> Unit, rule: (Dataset) -> RecordingResult) {
        viewModelScope.launch {
            val result = core.run { record { data, _ -> rule(data) } }
            val problem = when (val error = (result as? RecordingResult.Failure)?.error) {
                null -> null
                RecordingError.EmptyName -> CategoryProblem.NoName
                is RecordingError.NameInUse -> CategoryProblem.NameInUse(error.name)
                is RecordingError.CategoryHasSources -> CategoryProblem.HasOpenSources(
                    mutableUi.value?.active?.firstOrNull { it.id == error.id }?.openSources ?: 0,
                )
                else -> CategoryProblem.NotSaved
            }
            mutableUi.update { it?.copy(problem = problem) }
            if (problem == null) onDone()
        }
    }
}
