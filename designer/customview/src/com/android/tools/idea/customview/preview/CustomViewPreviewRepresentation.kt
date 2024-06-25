/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.annotations.concurrency.GuardedBy
import com.android.ide.common.rendering.api.Bridge
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.configurations.Configuration
import com.android.tools.configurations.updateScreenSize
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.setupChangeListener
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import javax.swing.JComponent
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

private fun fqcn2name(fcqn: String) = fcqn.substringAfterLast('.')

private fun layoutType(wrapContent: Boolean) = if (wrapContent) "wrap_content" else "match_parent"

@VisibleForTesting
fun getXmlLayout(qualifiedName: String, shrinkWidth: Boolean, shrinkHeight: Boolean): String {
  return """
<$qualifiedName
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="${layoutType(shrinkWidth)}"
    android:layout_height="${layoutType(shrinkHeight)}"/>"""
}

fun getBuildState(project: Project): CustomViewVisualStateTracker.BuildState {
  val buildManager = project.getProjectSystem().getBuildManager()
  val prevBuildStatus = buildManager.getLastBuildResult()
  // TODO(?) shouldn't we be doing something different if the last BuildResult is from a
  //  CLEAN build task?
  return when {
    buildManager.isBuilding -> CustomViewVisualStateTracker.BuildState.IN_PROGRESS
    prevBuildStatus.status == ProjectSystemBuildManager.BuildStatus.UNKNOWN ||
      prevBuildStatus.status == ProjectSystemBuildManager.BuildStatus.SUCCESS ->
      CustomViewVisualStateTracker.BuildState.SUCCESSFUL
    else -> CustomViewVisualStateTracker.BuildState.FAILED
  }
}

private val CUSTOM_VIEW_SUPPORTED_ACTIONS = setOf(NlSupportedActions.TOGGLE_ISSUE_PANEL)

/**
 * A preview for a file containing custom android view classes. Allows selecting between the classes
 * if multiple custom view classes are present in the file.
 */
class CustomViewPreviewRepresentation(
  psiFile: PsiFile,
  persistenceProvider: (Project) -> PropertiesComponent = { p ->
    PropertiesComponent.getInstance(p)
  },
  buildStateProvider: (Project) -> CustomViewVisualStateTracker.BuildState = ::getBuildState,
) :
  PreviewRepresentation,
  CustomViewPreviewManager,
  UserDataHolderEx by UserDataHolderBase(),
  AndroidCoroutinesAware {

  companion object {
    private val LOG = Logger.getInstance(CustomViewPreviewRepresentation::class.java)
  }

  private val project = psiFile.project
  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  private val persistenceManager = persistenceProvider(project)
  private var stateTracker: CustomViewVisualStateTracker
  private val _isActive = AtomicBoolean(false)
  private val _hasPendingRefresh = AtomicBoolean(true)

  private val previewId = "$CUSTOM_VIEW_PREVIEW_ID${psiFile.virtualFile!!.path}"
  private val currentStatePropertyName = "${previewId}_SELECTED"
  override val preferredInitialVisibility: PreferredVisibility? = null

  private fun dimensionsPropertyNameForClass(className: String) =
    "${previewId}_${className}_DIMENSIONS"

  private fun wrapContentWidthPropertyNameForClass(className: String) =
    "${previewId}_${className}_WRAP_CONTENT_W"

  private fun wrapContentHeightPropertyNameForClass(className: String) =
    "${previewId}_${className}_WRAP_CONTENT_H"

  private val classesLock = Any()
  @GuardedBy("classesLock")
  private var classes = listOf<String>()
    set(value) {
      if (field != value) {
        field = value
        if (field.isEmpty()) {
          currentView = ""
        } else if (!views.contains(currentView)) {
          currentView = views.first()
        }
      }
    }

  // We use a list to preserve the order
  override val views: List<String>
    get() {
      synchronized(classesLock) {
        return classes.map { fqcn2name(it) }
      }
    }

  override var currentView: String = persistenceManager.getValue(currentStatePropertyName, "")
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(currentStatePropertyName, value)
        shrinkHeight =
          persistenceManager
            .getValue(wrapContentHeightPropertyNameForClass(value), "false")
            .toBoolean()
        shrinkWidth =
          persistenceManager
            .getValue(wrapContentWidthPropertyNameForClass(value), "false")
            .toBoolean()
        updateModel()
      }
    }

  override var shrinkHeight =
    persistenceManager
      .getValue(wrapContentHeightPropertyNameForClass(currentView), "false")
      .toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentHeightPropertyNameForClass(currentView), value)
        updateModel()
      }
    }

  override var shrinkWidth =
    persistenceManager
      .getValue(wrapContentWidthPropertyNameForClass(currentView), "false")
      .toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentWidthPropertyNameForClass(currentView), value)
        updateModel()
      }
    }

  override val notificationsState: CustomViewPreviewManager.NotificationsState
    get() = stateTracker.notificationsState

  private val view = invokeAndWaitIfNeeded {
    CustomViewPreviewView(
      NlDesignSurface.builder(project, this) { surface, model ->
          NlDesignSurface.defaultSceneManagerProvider(surface, model, null).apply {
            setShrinkRendering(true)
          }
        }
        .setSupportedActions(CUSTOM_VIEW_SUPPORTED_ACTIONS)
        .setScreenViewProvider(NlScreenViewProvider.RESIZABLE_PREVIEW, false),
      this,
      project,
    )
  }

  private val surface = view.surface

  /** [WorkBench] used to contain all the preview elements. */
  private val workbench = view.workbench

  @Volatile private var lastBuildStartedNanos = 0L

  private val scope = AndroidCoroutineScope(this)
  private val uniqueUpdateModelLauncher =
    UniqueTaskCoroutineLauncher(scope, "CustomViewPreviewRepresentation updateModel")

  @get:TestOnly
  val hasPendingRefresh: Boolean
    get() = _hasPendingRefresh.get()

  @get:TestOnly
  val isActive: Boolean
    get() = _isActive.get()

  init {
    val buildState = buildStateProvider(project)
    val fileState =
      if (FileDocumentManager.getInstance().isFileModified(psiFile.virtualFile))
        CustomViewVisualStateTracker.FileState.MODIFIED
      else CustomViewVisualStateTracker.FileState.UP_TO_DATE
    stateTracker =
      CustomViewVisualStateTracker(
        buildState = buildState,
        fileState = fileState,
        onNotificationStateChanged = {
          val file =
            AndroidPsiUtils.getPsiFileSafely(psiFilePointer)
              ?: run {
                LOG.warn("onNotificationStateChanged with invalid PsiFile")
                return@CustomViewVisualStateTracker
              }

          EditorNotifications.getInstance(project).updateNotifications(file.virtualFile)
        },
        onPreviewStateChanged = {
          UIUtil.invokeLaterIfNeeded {
            when (it) {
              CustomViewVisualStateTracker.PreviewState.BUILDING -> {
                workbench.hideContent()
                workbench.showLoading("Waiting for build to finish...")
              }
              CustomViewVisualStateTracker.PreviewState.RENDERING -> {
                workbench.hideContent()
                workbench.showLoading("Waiting for previews to render...")
              }
              CustomViewVisualStateTracker.PreviewState.BUILD_FAILED -> {
                workbench.hideContent()
                workbench.loadingStopped(
                  "Preview is unavailable until after a successful project build."
                )
              }
              CustomViewVisualStateTracker.PreviewState.OK -> {
                workbench.showContent()
                workbench.hideLoading()
              }
            }
          }
        },
      )

    setupBuildListener(
      project,
      object : BuildListener {
        override fun buildSucceeded() {
          AndroidPsiUtils.getPsiFileSafely(psiFilePointer)
            ?: run {
              LOG.warn("invalid PsiFile")
              return
            }

          stateTracker.setBuildState(CustomViewVisualStateTracker.BuildState.SUCCESSFUL)
          if (!_isActive.get()) {
            LOG.debug("Preview was not active, scheduling refresh for later")
            _hasPendingRefresh.set(true)
          } else {
            refresh()
          }
        }

        override fun buildFailed() {
          stateTracker.setBuildState(CustomViewVisualStateTracker.BuildState.FAILED)
        }

        override fun buildStarted() {
          lastBuildStartedNanos = System.nanoTime()
          stateTracker.setFileState(CustomViewVisualStateTracker.FileState.UP_TO_DATE)
          stateTracker.setBuildState(CustomViewVisualStateTracker.BuildState.IN_PROGRESS)
        }
      },
      this,
    )

    setupChangeListener(
      project,
      psiFile,
      { lastUpdatedNanos ->
        if (lastUpdatedNanos > lastBuildStartedNanos) {
          stateTracker.setFileState(CustomViewVisualStateTracker.FileState.MODIFIED)
        }
      },
      this,
    )
  }

  override val component = workbench

  override fun dispose() {}

  /** Refresh the preview surfaces */
  private fun refresh() {
    LOG.debug("Refresh")
    if (Bridge.hasNativeCrash()) {
      workbench.handleLayoutlibNativeCrash { refresh() }
      return
    }
    _hasPendingRefresh.set(false)
    val psiFile =
      AndroidPsiUtils.getPsiFileSafely(psiFilePointer)
        ?: run {
          LOG.warn("refresh with invalid PsiFile")
          return
        }

    stateTracker.setVisualState(CustomViewVisualStateTracker.VisualState.RENDERING)
    scope.launch {
      smartReadAction(project) {
        val calculatedClasses =
          (AndroidPsiUtils.getPsiFileSafely(project, psiFile.virtualFile) as PsiClassOwner)
            .classes
            .filter { it.name != null && it.extendsView() }
            .mapNotNull { it.qualifiedName }

        synchronized(classesLock) {
          classes = calculatedClasses
          // This may happen if custom view classes got removed from the file
          if (classes.isEmpty()) {
            return@smartReadAction
          }
        }
        updateModel()
      }
    }
  }

  private fun updateModel() =
    scope.launch { uniqueUpdateModelLauncher.launch { updateModelAsync() } }

  private fun updateModelAsync() =
    scope.launch {
      val psiFile =
        readAction { psiFilePointer.element }
          ?: run {
            LOG.warn("updateModelSync with invalid PsiFile")
            return@launch
          }

      val selectedClass =
        synchronized(classesLock) {
          classes.firstOrNull { fqcn2name(it) == currentView } ?: return@launch
        }

      val fileContent = getXmlLayout(selectedClass, shrinkWidth, shrinkHeight)
      val facet =
        AndroidFacet.getInstance(psiFile)
          ?: run {
            LOG.warn("No facet for PsiFile $psiFile")
            return@launch
          }
      val configurationManager = ConfigurationManager.getOrCreateInstance(facet.module)
      val className = fqcn2name(selectedClass)

      val model =
        if (surface.models.isEmpty()) {
          val customPreviewXml =
            CustomViewLightVirtualFile("custom_preview.xml", fileContent, psiFile.virtualFile)
          val config =
            Configuration.create(configurationManager, FolderConfiguration.createDefault())
          NlModel.Builder(
              this@CustomViewPreviewRepresentation,
              BuildTargetReference.from(facet, psiFile.virtualFile),
              customPreviewXml,
              config,
            )
            .withXmlProvider(
              BiFunction { project, _ ->
                AndroidPsiUtils.getPsiFileSafely(project, customPreviewXml) as XmlFile
              }
            )
            .withComponentRegistrar(NlComponentRegistrar)
            .build()
            .apply { displaySettings.setDisplayName(className) }
        } else {
          // We want to deactivate the surface so that configuration changes do not trigger scene
          // repaint.
          surface.deactivate()
          surface.models.first().let { model ->
            surface.getSceneManager(model)!!.forceReinflate()
            model.updateFileContentBlocking(fileContent)
          }
        }
      val configuration = model.configuration

      // Load and set preview size if exists for this custom view
      withContext(uiThread) {
        persistenceManager.getValues(dimensionsPropertyNameForClass(className))?.let {
          previewDimensions ->
          configuration.updateScreenSize(
            previewDimensions[0].toInt(),
            previewDimensions[1].toInt(),
            configuration.device,
          )
        }

        surface.models.forEach { surface.removeModel(it) }
        surface.addAndRenderModel(model).await()
        surface.activate()

        stateTracker.setVisualState(CustomViewVisualStateTracker.VisualState.OK)
      }
    }

  override fun onActivate() {
    _isActive.set(true)
    if (_hasPendingRefresh.getAndSet(false)) refresh()
  }

  override fun onDeactivate() {
    _isActive.set(false)

    // Persist the current dimensions
    surface.models.firstOrNull()?.configuration?.let { configuration ->
      val selectedClass =
        synchronized(classesLock) { classes.firstOrNull { fqcn2name(it) == currentView } } ?: return
      val className = fqcn2name(selectedClass)
      val screen = configuration.device!!.defaultHardware.screen
      persistenceManager.setList(
        dimensionsPropertyNameForClass(className),
        listOf("${screen.xDimension}", "${screen.yDimension}"),
      )
    }
  }

  override fun updateNotifications(parentEditor: FileEditor) {
    val psiFile =
      AndroidPsiUtils.getPsiFileSafely(psiFilePointer)
        ?: run {
          LOG.warn("updateNotifications with invalid PsiFile")
          return
        }

    view.notificationsPanel.updateNotifications(psiFile.virtualFile, parentEditor, project)
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface)
      .registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }
}
