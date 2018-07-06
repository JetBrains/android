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
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearch
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.text.StringUtil.isNotEmpty
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

class ArtifactRepositorySearchForm(repositories: List<ArtifactRepository>) : ArtifactRepositorySearchFormUi() {
  private val repositorySearch: ArtifactRepositorySearch = ArtifactRepositorySearch(repositories)
  private val resultsTable: TableView<FoundArtifact>
  private val versionsPanel: AvailableVersionsPanel
  private val eventDispatcher = SelectionChangeEventDispatcher<ParsedValue<String>>()

  private val artifactName: String get() = myArtifactNameTextField.text.trim { it <= ' ' }
  private val groupId: String? get() = myGroupIdTextField.text.trim { it <= ' ' }.nullize()

  val selection: FoundArtifact? get() = resultsTable.selection.singleOrNull()
  val panel: JPanel get() = myPanel
  val preferredFocusedComponent: JComponent get() = myArtifactNameTextField
  var searchErrors: List<Exception> = listOf() ; private set

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
      val artifact = selection

      if (artifact != null) {
        versionsPanel.setVersions(artifact.versions)
      }
      else {
        notifyVersionSelectionChanged(null)
      }
    }

    val splitter = OnePixelSplitter(false, 0.7f)
    splitter.firstComponent = createScrollPane(resultsTable)
    splitter.secondComponent = versionsPanel

    myResultsPanel.add(splitter, BorderLayout.CENTER)

    TableSpeedSearch(resultsTable)
  }

  private fun notifyVersionSelectionChanged(version: String?) {
    val selected = selection?.let { selection ->
      val groupId = selection.groupId
      val name = selection.name
      val adjustedVersion = version ?: selection.versions.firstOrNull()?.toString() ?: ""

      buildString {
        append(groupId + GRADLE_PATH_SEPARATOR + name)
        if (isNotEmpty(adjustedVersion)) {
          append(GRADLE_PATH_SEPARATOR + adjustedVersion)
        }
      }
    }
    eventDispatcher.selectionChanged(ParsedValue.Set.Parsed(selected, DslText.Literal))
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

      val foundArtifacts = results.results.flatMap { it.artifacts }.sorted()

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
        column("Repository") { it.repositoryName })
    }
  }
}
