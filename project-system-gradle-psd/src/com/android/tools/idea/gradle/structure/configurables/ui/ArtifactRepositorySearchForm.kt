/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.configurables.ui

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.repositories.search.SearchQuery
import com.android.tools.idea.gradle.repositories.search.SearchRequest
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.helpers.parseGradleVersion
import com.android.tools.idea.gradle.structure.model.meta.Annotated
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.KnownValues
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.meta.VariableMatchingStrategy
import com.android.tools.idea.gradle.structure.model.meta.annotateWithError
import com.android.tools.idea.gradle.structure.model.meta.annotated
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.table.TableView
import com.intellij.util.text.nullize
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.event.ActionListener
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel.SINGLE_SELECTION
import javax.swing.event.DocumentEvent

private const val SEARCHING_EMPTY_TEXT = "Searching..."
private const val NOTHING_TO_SHOW_EMPTY_TEXT = "Nothing to show"

class ArtifactRepositorySearchForm(
  val variables: PsVariablesScope,
  private val repositorySearch: ArtifactRepositorySearchService
) : ArtifactRepositorySearchFormUi() {
  private val resultsTable: TableView<FoundArtifact>
  private val versionsPanel: AvailableVersionsPanel
  private val eventDispatcher = SelectionChangeEventDispatcher<ParsedValue<String>>()

  private val selectedArtifact: FoundArtifact? get() = resultsTable.selection.singleOrNull()
  val panel: JPanel get() = myPanel
  val preferredFocusedComponent: JComponent get() = myArtifactQueryTextField
  var searchErrors: List<Exception> = listOf(); private set

  init {
    val inputChangedListener = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        clearResults()
        showSearchStopped()
      }
    }
    myArtifactQueryTextField.document.addDocumentListener(inputChangedListener)

    val actionListener = ActionListener {
      if (mySearchButton.isEnabled) {
        performSearch()
      }
    }

    mySearchButton.addActionListener(actionListener)

    myArtifactQueryTextField.addActionListener(actionListener)
    myArtifactQueryTextField.emptyText.apply {
      clear()
      appendText("Example: ", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      appendText("guava", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
      appendText(" or ", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      appendText("com.google.*:*", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
      // NOTE: While *guava* might also be supported by some search providers, it is not supported by mavenCentral().
      appendText(" or ", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
      appendText("guava*", SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
    }

    resultsTable = TableView(ResultsTableModel())

    resultsTable.setSelectionMode(SINGLE_SELECTION)
    resultsTable.autoCreateRowSorter = true
    resultsTable.setShowGrid(false)
    resultsTable.tableHeader.reorderingAllowed = false

    versionsPanel = AvailableVersionsPanel(Consumer { this.notifyVersionSelectionChanged(it) })

    resultsTable.selectionModel.addListSelectionListener {
      val artifact = selectedArtifact

      val searchQuery = currentSearchQuery
      if (searchQuery != null && artifact != null) {
        versionsPanel.setVersions(prepareArtifactVersionChoices(searchQuery, artifact, variables))
      }
      else {
        notifyVersionSelectionChanged(ParsedValue.NotSet)
      }
    }

    val splitter = OnePixelSplitter(false, 0.7f)
    splitter.firstComponent = createScrollPane(resultsTable)
    splitter.secondComponent = versionsPanel

    myResultsPanel.add(splitter, BorderLayout.CENTER)

    TableSpeedSearch.installOn(resultsTable)
  }

  private fun getQuery() = myArtifactQueryTextField.text.parseArtifactSearchQuery()

  private fun notifyVersionSelectionChanged(version: ParsedValue<GradleVersion>) {
    val selectedLibrary = selectedArtifact?.let { selectedArtifact ->
      when (version) {
        ParsedValue.NotSet -> ParsedValue.NotSet
        is ParsedValue.Set.Parsed -> versionToLibrary(selectedArtifact, version)
      }
    } ?: ParsedValue.NotSet
    eventDispatcher.selectionChanged(selectedLibrary)
  }

  private var currentSearchQuery: ArtifactSearchQuery? = null

  private fun performSearch() {
    mySearchButton.isEnabled = false
    versionsPanel.setEmptyText(SEARCHING_EMPTY_TEXT)
    resultsTable.emptyText.text = SEARCHING_EMPTY_TEXT
    resultsTable.setPaintBusy(true)
    clearResults()

    val searchQuery = getQuery().also { currentSearchQuery = it }
    val request = SearchRequest(searchQuery.toSearchQeury(), 50, 0)

    repositorySearch.search(request).continueOnEdt { results ->
      val foundArtifacts = results.artifacts.sorted().takeUnless { it.isEmpty() } ?: let {
        when {
          searchQuery.gradleCoordinates != null -> listOf(
            FoundArtifact("(none)", searchQuery.gradleCoordinates.groupId.orEmpty(), searchQuery.gradleCoordinates.artifactId.orEmpty(),
                          searchQuery.gradleCoordinates.version!!))
          else -> listOf()
        }
      }

      resultsTable.listTableModel.items = foundArtifacts
      resultsTable.updateColumnSizes()
      if (foundArtifacts.isNotEmpty()) {
        resultsTable.changeSelection(0, 0, false, false)
      }
      resultsTable.requestFocusInWindow()

      val errors = results.errors
      if (errors.isNotEmpty()) {
        searchErrors = errors
      }

      showSearchStopped()
    }
  }

  private fun clearResults() {
    currentSearchQuery = null
    resultsTable.listTableModel.items = emptyList()
    searchErrors = listOf()
    versionsPanel.clear()
  }

  private fun showSearchStopped() {
    mySearchButton.isEnabled = getQuery().let { (it.artifactName?.length ?: 0) + (it.groupId?.length ?: 0) >= 3 }

    resultsTable.setPaintBusy(false)
    resultsTable.emptyText.text = NOTHING_TO_SHOW_EMPTY_TEXT

    versionsPanel.setEmptyText(NOTHING_TO_SHOW_EMPTY_TEXT)
  }

  fun add(listener: SelectionChangeListener<ParsedValue<String>>, parentDisposable: Disposable) {
    eventDispatcher.addListener(listener, parentDisposable)
  }

  private class ResultsTableModel internal constructor() : ListTableModel<FoundArtifact>() {
    init {
      createAndSetColumnInfos()
      isSortable = true
    }

    private fun createAndSetColumnInfos() {
      fun column(title: String, preferredWidthTextSample: String? = null, valueOf: (FoundArtifact) -> String) =
        object : ColumnInfo<FoundArtifact, String>(title) {
          override fun valueOf(found: FoundArtifact): String = valueOf(found)

          @NonNls
          override fun getPreferredStringValue(): String? = preferredWidthTextSample
        }

      columnInfos = arrayOf(
        column("Group ID", preferredWidthTextSample = "abcdefghijklmno") { it.groupId },
        column("Artifact Name", preferredWidthTextSample = "abcdefg") { it.name },
        column("Repository") { it.repositoryNames.joinToString (separator = ", ") })
    }
  }
}

@VisibleForTesting
fun prepareArtifactVersionChoices(
  searchQuery: ArtifactSearchQuery,
  artifact: FoundArtifact,
  variablesScope: PsVariablesScope
): List<Annotated<ParsedValue.Set.Parsed<GradleVersion>>> {
  val versionPropertyContext = object : ModelPropertyContext<GradleVersion> {
    override fun parse(value: String): Annotated<ParsedValue<GradleVersion>> = parseGradleVersion(value)
    override fun format(value: GradleVersion): String = value.toString()

    override fun getKnownValues(): ListenableFuture<KnownValues<GradleVersion>> =
      Futures.immediateFuture(object : KnownValues<GradleVersion> {
        override val literals: List<ValueDescriptor<GradleVersion>> = artifact.versions.map { ValueDescriptor(it) }
        override fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<GradleVersion>>): Boolean =
          throw UnsupportedOperationException()
      })
  }

  val missingVersion = searchQuery
    .gradleCoordinates
    ?.takeIf {
      it.artifactId == artifact.name &&
      it.groupId == artifact.groupId &&
      it.version != null &&
      !artifact.versions.contains(it.version!!)
    }

  val versions =
    listOfNotNull(missingVersion?.let { ParsedValue.Set.Parsed(it.version!!, DslText.Literal).annotateWithError("not found") }) +
    artifact.versions.map { ParsedValue.Set.Parsed(it, DslText.Literal).annotated() }

  val suitableVariables =
    variablesScope
      .getAvailableVariablesFor(versionPropertyContext)
      .filter { VariableMatchingStrategy.WELL_KNOWN_VALUE.matches(it.value, artifact.versions.toSet()) }
      .map { it.value.annotated() }
  return (versions + suitableVariables).sortedByDescending { it.value.value }
}


@VisibleForTesting
fun versionToLibrary(
  artifact: FoundArtifact,
  version: ParsedValue.Set.Parsed<GradleVersion>
): ParsedValue<String> {
  val artifactGroupId = artifact.groupId
  val artifactName = artifact.name

  fun makeCompactNotation(version: String) =
    buildString(artifactGroupId.length + artifactName.length + version.length + 2 * GRADLE_PATH_SEPARATOR.length) {
      append(artifactGroupId)
      append(GRADLE_PATH_SEPARATOR)
      append(artifactName)
      append(GRADLE_PATH_SEPARATOR)
      append(version)
    }

  val compactNotationResolved = makeCompactNotation(version = version.value?.toString().orEmpty())

  fun makeInterpolatedCompactNotation(versionReference: String) =
    ParsedValue.Set.Parsed(compactNotationResolved, DslText.InterpolatedString(makeCompactNotation(version = versionReference)))

  return version.dslText.let {
    when (it) {
      is DslText.Literal -> ParsedValue.Set.Parsed(compactNotationResolved, DslText.Literal)
    // References.
      is DslText.Reference -> makeInterpolatedCompactNotation(versionReference = "\${${it.text}}")
      is DslText.OtherUnparsedDslText -> makeInterpolatedCompactNotation(versionReference = "\${${it.text}}")
    // Technically the following case should return:
    //     makeReferenceSelection(it.text)
    // but since it is not expecting to happen we throw an exception.
      is DslText.InterpolatedString -> throw IllegalStateException()
    }
  }
}

@VisibleForTesting
data class ArtifactSearchQuery(
  val groupId: String? = null,
  val artifactName: String? = null,
  val version: String? = null,
  val gradleCoordinates: GradleCoordinate? = null
)

@VisibleForTesting
fun String.parseArtifactSearchQuery(): ArtifactSearchQuery {
  val split = split(':', limit = 3).map { it.nullize(true) }
  return when {
    split.isEmpty() -> ArtifactSearchQuery()
    split.size == 1 && split[0]?.contains('.') == true -> ArtifactSearchQuery(groupId = split[0])
    split.size == 1 -> ArtifactSearchQuery(artifactName = split[0])
    split.size == 2 -> ArtifactSearchQuery(groupId = split[0], artifactName = split[1])
    split.size >= 3 ->
      ArtifactSearchQuery(
        groupId = split[0],
        artifactName = split[1],
        version = split[2],
        gradleCoordinates = GradleCoordinate.parseCoordinateString(this))
    else -> throw RuntimeException()
  }
}

private fun ArtifactSearchQuery.toSearchQeury() = SearchQuery(groupId = groupId, artifactName = artifactName)
