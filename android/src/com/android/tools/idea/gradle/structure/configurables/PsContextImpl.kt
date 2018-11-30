/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables

import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.configurables.suggestions.SuggestionsPerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.continueOnEdt
import com.android.tools.idea.gradle.structure.configurables.ui.handleFailureOnEdt
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place
import com.intellij.util.EventDispatcher
import com.intellij.util.ExceptionUtil
import java.util.function.Consumer

class PsContextImpl constructor(
  override val project: PsProjectImpl,
  parentDisposable: Disposable,
  disableAnalysis: Boolean = false,
  private val cachingRepositorySearchFactory: RepositorySearchFactory = CachingRepositorySearchFactory()
) : PsContext, Disposable {
  override val analyzerDaemon: PsAnalyzerDaemon
  private val gradleSync: GradleResolver = GradleResolver()
  override val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon

  private val gradleSyncEventDispatcher = EventDispatcher.create(
    GradleSyncListener::class.java)
  private var disableSync: Boolean = false

  override var selectedModule: String? = null; private set

  override val uiSettings: PsUISettings
    get() = PsUISettings.getInstance(project.ideProject)

  override val mainConfigurable: ProjectStructureConfigurable
    get() = ProjectStructureConfigurable.getInstance(project.ideProject)

  init {
    mainConfigurable.add(
      object : ProjectStructureConfigurable.ProjectStructureChangeListener {
        override fun projectStructureChanged() {
          if (!disableSync) this@PsContextImpl.requestGradleModels()
        }
      }, this)
    // The UI has not yet subscribed to notifications which is fine since we don't want to see "Loading..." at startup.
    requestGradleModels()

    libraryUpdateCheckerDaemon = PsLibraryUpdateCheckerDaemon(this, project, cachingRepositorySearchFactory)
    if (!disableAnalysis) {
      libraryUpdateCheckerDaemon.reset()
      libraryUpdateCheckerDaemon.queueAutomaticUpdateCheck()
    }

    analyzerDaemon = PsAnalyzerDaemon(this, project, libraryUpdateCheckerDaemon)
    if (!disableAnalysis) {
      analyzerDaemon.reset()
      project.forEachModule(Consumer { analyzerDaemon.queueCheck(it) })
    }

    project.onModuleChanged(this) { module ->
      analyzerDaemon.queueCheck(module)
      project
        .modules
        .filter { it.dependencies.modules.any { moduleDependency -> moduleDependency.gradlePath == module.gradlePath } }
        .forEach { analyzerDaemon.queueCheck(it) }
    }

    Disposer.register(parentDisposable, this)
  }

  private fun requestGradleModels() {
    val project = this.project.ideProject
    gradleSyncEventDispatcher.multicaster.syncStarted(project, false, false)
    gradleSync
      .requestProjectResolved(project, this)
      .handleFailureOnEdt {
        gradleSyncEventDispatcher.multicaster.syncFailed(project, it?.let { e -> ExceptionUtil.getRootCause(e).message }.orEmpty())
      }
      .continueOnEdt {
        this.project.refreshFrom(it)
        gradleSyncEventDispatcher.multicaster.syncSucceeded(project)
      }
  }

  override fun add(listener: GradleSyncListener, parentDisposable: Disposable) =
    gradleSyncEventDispatcher.addListener(listener, parentDisposable)


  override fun setSelectedModule(moduleName: String, source: Any) {
    selectedModule = moduleName
  }

  override fun dispose() {}

  /**
   * Gets a [ArtifactRepositorySearchService] that searches the repositories configured for `module`. The results are cached and
   * in the case of an exactly matching request reused.
   */
  override fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService =
    cachingRepositorySearchFactory.create(module.getArtifactRepositories())

  override fun applyRunAndReparse(runnable: () -> Boolean) {
    disableSync = true
    try {
      project.applyRunAndReparse(runnable)
    }
    finally {
      disableSync = false
    }
    requestGradleModels()
  }

  override fun applyChanges() {

    fun activateSuggestionsView() {
      val place = Place()
      val suggestionsView = mainConfigurable.findConfigurable(SuggestionsPerspectiveConfigurable::class.java)
      place.putPath(ProjectStructureConfigurable.CATEGORY_NAME, suggestionsView?.displayName.orEmpty())
      place.putPath(BASE_PERSPECTIVE_MODULE_PLACE_NAME, suggestionsView?.extraModules?.first()?.name.orEmpty())
      mainConfigurable.navigateTo(place, false)
    }

    if (project.isModified) {
      val validationIssues =
        project.modules.asSequence().flatMap { analyzerDaemon.validate(it) }.filter { it.severity == PsIssue.Severity.ERROR }.toList()
      if (validationIssues.isNotEmpty()) {
        activateSuggestionsView()
        // Display errors and make sure the view is refreshed before the message box below changes the current modality.
        ApplicationManager.getApplication().invokeAndWait(
          {
            analyzerDaemon.issues.remove(PsIssueType.PROJECT_ANALYSIS)
            analyzerDaemon.addAll(validationIssues, now = true)
          },
          ModalityState.any() // Any modality to let the UI update itself while showing the message box.
        )
        if (Messages.showDialog(project.ideProject,
                                "Potential problems found in the configuration. Would you like to review them?",
                                "Problems Found",
                                arrayOf("Review", "Ignore and Apply"),
                                0,
                                null)
          != 1) {
          throw ProcessCanceledException()
        }
      }
      project.applyChanges()
    }
  }

  override fun PsPath.renderNavigation(specificPlace: PsPath): String =
    """<a href="${specificPlace.getHyperlinkDestination(this@PsContextImpl)}">${this@renderNavigation.toString()}</a>"""
}
