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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.repositories.search.ArtifactRepositorySearchService
import com.android.tools.idea.gradle.repositories.search.CachingRepositorySearchFactory
import com.android.tools.idea.gradle.repositories.search.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.structure.configurables.suggestions.SuggestionsPerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings
import com.android.tools.idea.gradle.structure.configurables.ui.continueOnEdt
import com.android.tools.idea.gradle.structure.configurables.ui.handleFailureOnEdt
import com.android.tools.idea.gradle.structure.daemon.PsAnalyzerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsLibraryUpdateCheckerDaemon
import com.android.tools.idea.gradle.structure.daemon.PsSdkIndexCheckerDaemon
import com.android.tools.idea.gradle.structure.daemon.analysis.PsAndroidModuleAnalyzer
import com.android.tools.idea.gradle.structure.daemon.analysis.PsJavaModuleAnalyzer
import com.android.tools.idea.gradle.structure.daemon.analysis.PsModelAnalyzer
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssueType
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.PsResolvedModuleModel
import com.android.tools.idea.structure.dialog.ProjectStructureConfigurable
import com.google.common.util.concurrent.ListenableFuture
import com.google.wireless.android.sdk.stats.PSDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place
import com.intellij.util.EventDispatcher
import java.util.function.Consumer

private val LOG = Logger.getInstance(PsContextImpl::class.java)
class PsContextImpl constructor(
  override val project: PsProjectImpl,
  parentDisposable: Disposable,
  disableAnalysis: Boolean = false,
  private val disableResolveModels: Boolean,
  private val cachingRepositorySearchFactory: RepositorySearchFactory = CachingRepositorySearchFactory()
) : PsContext, Disposable {
  override val analyzerDaemon: PsAnalyzerDaemon
  private val gradleSync: GradleResolver = GradleResolver()
  override val libraryUpdateCheckerDaemon: PsLibraryUpdateCheckerDaemon = PsLibraryUpdateCheckerDaemon(this, project, cachingRepositorySearchFactory)
  override val sdkIndexCheckerDaemon: PsSdkIndexCheckerDaemon = PsSdkIndexCheckerDaemon(this, project)

  private val gradleSyncEventDispatcher = EventDispatcher.create(PsContext.SyncListener::class.java)
  private var disableSync: Boolean = false
  private var disposed: Boolean = false

  override var selectedModule: String? = null; private set

  override val uiSettings: PsUISettings
    get() = PsUISettings.getInstance(project.ideProject)

  override val mainConfigurable: ProjectStructureConfigurable
    get() = ProjectStructureConfigurable.getInstance(project.ideProject)

  private val editedFields = mutableSetOf<PSDEvent.PSDField>()

  init {
    if (!disableAnalysis) {
      libraryUpdateCheckerDaemon.reset()
      libraryUpdateCheckerDaemon.queueUpdateCheck()
      sdkIndexCheckerDaemon.reset()
      sdkIndexCheckerDaemon.queueCheck()
    }

    analyzerDaemon = PsAnalyzerDaemon(
      this,
      project,
      libraryUpdateCheckerDaemon,
      sdkIndexCheckerDaemon,
      analyzersMapOf(
        PsAndroidModuleAnalyzer(this, PsPathRendererImpl().also { it.context = this }),
        PsJavaModuleAnalyzer(this))
    )

    if (!disableAnalysis) {
      project.onModuleChanged(this) { module ->
        analyzerDaemon.queueCheck(module)
        project
          .modules
          .filter { it.dependencies.modules.any { moduleDependency -> moduleDependency.gradlePath == module.gradlePath } }
          .forEach { analyzerDaemon.queueCheck(it) }
      }
      project.forEachModule(Consumer { module ->
        module.addDependencyChangedListener(this) { e -> if (e !is PsModule.DependenciesReloadedEvent) dependencyChanged() }
      })
    }

    mainConfigurable.add(
      object : ProjectStructureConfigurable.ProjectStructureListener {
        override fun projectStructureInitializing() {
          requestGradleModels()
          if (!disableAnalysis) {
            analyzerDaemon.reset()
            project.forEachModule(Consumer { analyzerDaemon.queueCheck(it) })
          }
        }

        override fun projectStructureChanged() {
          if (!disableSync) this@PsContextImpl.requestGradleModels()
        }
      }, this)

    Disposer.register(parentDisposable, this)
  }

  private fun dependencyChanged() {
    analyzerDaemon.recreateIssues()
  }

  private var future: ListenableFuture<List<PsResolvedModuleModel>>? = null

  @UiThread
  private fun requestGradleModels() {
    if (disableResolveModels) return
    val project = this.project.ideProject
    future?.cancel(true)
    gradleSyncEventDispatcher.multicaster.started()
    gradleSync
      .requestProjectResolved(project, this)
      .also { future = it }
      .handleFailureOnEdt { ex ->
        LOG.warn("PSD failed to fetch Gradle models.", ex)
        gradleSyncEventDispatcher.multicaster.ended()
      }
      .continueOnEdt {
        future = null
        if (it == null || disposed) return@continueOnEdt
        LOG.info("PSD fetched (${it.size} Gradle model(s). Refreshing the UI model.")
        this.project.refreshFrom(it)
        gradleSyncEventDispatcher.multicaster.ended()
        this.project.forEachModule(Consumer { analyzerDaemon.queueCheck(it) })
      }
  }

  override fun add(listener: PsContext.SyncListener, parentDisposable: Disposable) {
    if (future != null) {
      listener.started()
    }
    gradleSyncEventDispatcher.addListener(listener, parentDisposable)
  }


  override fun setSelectedModule(gradlePath: String, source: Any) {
    selectedModule = gradlePath
  }

  override fun dispose() {
    future?.cancel(true)
    disposed = true
  }

  /**
   * Gets a [ArtifactRepositorySearchService] that searches the repositories configured for `module`. The results are cached and
   * in the case of an exactly matching request reused.
   */
  override fun getArtifactRepositorySearchServiceFor(module: PsModule): ArtifactRepositorySearchService =
    cachingRepositorySearchFactory.create(module.getArtifactRepositories())

  override fun applyRunAndReparse(runnable: () -> Boolean) {
    disableSync = true
    try {
      future?.cancel(true)
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
      place.putPath(BASE_PERSPECTIVE_MODULE_PLACE_NAME, suggestionsView?.extraModules?.first()?.gradlePath.orEmpty())
      mainConfigurable.navigateTo(place, false)
    }

    future?.cancel(true)
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

  override fun logFieldEdited(fieldId: PSDEvent.PSDField) {
    editedFields.add(fieldId)
  }

  override fun getEditedFieldsAndClear(): List<PSDEvent.PSDField> =
    editedFields.toList().also {
      editedFields.clear()
    }
}

class PsPathRendererImpl : PsPathRenderer {
  var context: PsContext? = null
  override fun PsPath.renderNavigation(specificPlace: PsPath): String {
    val text = this.toString()
    val href = specificPlace.getHyperlinkDestination(context!!).orEmpty()
    return """<a href="$href">$text</a>"""
  }
}

private fun analyzersMapOf(vararg analyzers: PsModelAnalyzer<out PsModule>): Map<Class<*>, PsModelAnalyzer<out PsModule>> =
  analyzers.associateBy { it.supportedModelType }
