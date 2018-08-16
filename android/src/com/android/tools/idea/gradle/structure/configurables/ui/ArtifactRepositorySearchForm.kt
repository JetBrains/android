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
import com.android.annotations.VisibleForTesting
import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.android.tools.idea.gradle.structure.model.helpers.parseGradleVersion
import com.android.tools.idea.gradle.structure.model.meta.*
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearch
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.table.TableView
import com.intellij.util.text.nullize
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.Dimension
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
  repositories: Collection<ArtifactRepository>
) : ArtifactRepositorySearchFormUi() {
  private val repositorySearch: ArtifactRepositorySearch = ArtifactRepositorySearch(repositories)
  private val resultsTable: TableView<FoundArtifact>
  private val versionsPanel: AvailableVersionsPanel
  private val eventDispatcher = SelectionChangeEventDispatcher<ParsedValue<String>>()

  private val artifactName: String get() = myArtifactNameTextField.text.trim { it <= ' ' }
  private val groupId: String? get() = myGroupIdTextField.text.trim { it <= ' ' }.nullize()

  private val selectedArtifact: FoundArtifact? get() = resultsTable.selection.singleOrNull()
  val panel: JPanel get() = myPanel
  val preferredFocusedComponent: JComponent get() = myArtifactNameTextField
  var searchErrors: List<Exception> = listOf(); private set

  init {
    myArtifactNameTextField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        clearResults()
        showSearchStopped()
      }
    })

    val actionListener = ActionListener {
      if (mySearchButton.isEnabled) {
        performSearch()
      }
    }

    mySearchButton.addActionListener(actionListener)

    myArtifactNameLabel.labelFor = myArtifactNameTextField
    myArtifactNameTextField.addActionListener(actionListener)
    myArtifactNameTextField.emptyText.text = "Example: \"guava\""

    myGroupIdLabel.labelFor = myGroupIdTextField
    myGroupIdTextField.addActionListener(actionListener)
    myGroupIdTextField.emptyText.text = "Example: \"com.google.guava\""

    resultsTable = TableView(ResultsTableModel())
    resultsTable.preferredSize = Dimension(520, 320)

    resultsTable.setSelectionMode(SINGLE_SELECTION)
    resultsTable.autoCreateRowSorter = true
    resultsTable.setShowGrid(false)
    resultsTable.tableHeader.reorderingAllowed = false

    versionsPanel = AvailableVersionsPanel(Consumer { this.notifyVersionSelectionChanged(it) })

    resultsTable.selectionModel.addListSelectionListener {
      val artifact = selectedArtifact

      if (artifact != null) {
        versionsPanel.setVersions(prepareArtifactVersionChoices(artifact, variables))
      }
      else {
        notifyVersionSelectionChanged(ParsedValue.NotSet)
      }
    }

    val splitter = OnePixelSplitter(false, 0.7f)
    splitter.firstComponent = createScrollPane(resultsTable)
    splitter.secondComponent = versionsPanel

    myResultsPanel.add(splitter, BorderLayout.CENTER)

    TableSpeedSearch(resultsTable)
  }

  private fun notifyVersionSelectionChanged(version: ParsedValue<GradleVersion>) {
    val selectedLibrary = selectedArtifact?.let { selectedArtifact ->
      when (version) {
        ParsedValue.NotSet -> ParsedValue.NotSet
        is ParsedValue.Set.Parsed -> versionToLibrary(selectedArtifact, version)
      }
    } ?: ParsedValue.NotSet
    eventDispatcher.selectionChanged(selectedLibrary)
  }

  private fun performSearch() {
    mySearchButton.isEnabled = false
    versionsPanel.setEmptyText(SEARCHING_EMPTY_TEXT)
    resultsTable.emptyText.text = SEARCHING_EMPTY_TEXT
    resultsTable.setPaintBusy(true)
    clearResults()

    val request = SearchRequest(artifactName, groupId, 50, 0)

    repositorySearch.search(request).continueOnEdt { results ->
      val errors = results.errors
      if (errors.isNotEmpty()) {
        showSearchStopped()
        searchErrors = errors
        return@continueOnEdt
      }

      val foundArtifacts = results.artifacts.sorted()

      resultsTable.listTableModel.items = foundArtifacts
      resultsTable.updateColumnSizes()
      showSearchStopped()
      if (foundArtifacts.isNotEmpty()) {
        resultsTable.changeSelection(0, 0, false, false)
      }
      resultsTable.requestFocusInWindow()
    }
  }

  private fun clearResults() {
    resultsTable.listTableModel.items = emptyList()
    searchErrors = listOf()
    versionsPanel.clear()
  }

  private fun showSearchStopped() {
    mySearchButton.isEnabled = artifactName.length >= 3

    resultsTable.setPaintBusy(false)
    resultsTable.emptyText.text = NOTHING_TO_SHOW_EMPTY_TEXT

    versionsPanel.clear()
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
          override fun valueOf(found: FoundArtifact): String? = valueOf(found)

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
  artifact: FoundArtifact,
  variablesScope: PsVariablesScope
): List<ParsedValue.Set.Parsed<GradleVersion>> {
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
  val versions = artifact.versions.map { ParsedValue.Set.Parsed(it, DslText.Literal) }
  val suitableVariables =
    variablesScope
      .getAvailableVariablesFor(versionPropertyContext)
      .filter { VariableMatchingStrategy.WELL_KNOWN_VALUE.matches(it.value, artifact.versions.toSet()) }
      .map { it.value }
  return (versions + suitableVariables).sortedByDescending { it.value }
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
