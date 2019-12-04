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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationListener
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.model.updateConfigurationScreenSize
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.util.function.BiFunction
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.OverlayLayout


private fun fqcn2name(fcqn: String) = fcqn.substringAfterLast('.')

private fun layoutType(wrapContent: Boolean) = if (wrapContent) "wrap_content" else "match_parent"

private fun getXmlLayout(qualifiedName: String, shrinkWidth: Boolean, shrinkHeight: Boolean): String {
  return """
<$qualifiedName
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="${layoutType(shrinkWidth)}"
    android:layout_height="${layoutType(shrinkHeight)}"/>"""
}

/**
 * A preview for a file containing custom android view classes. Allows selecting between the classes if multiple custom view classes are
 * present in the file.
 */
class CustomViewPreviewRepresentation(
  private val psiFile: PsiFile,
  persistenceProvider: (Project) -> PropertiesComponent = { p -> PropertiesComponent.getInstance(p)}) :
  PreviewRepresentation, CustomViewPreviewManager {

  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile!!
  private val persistenceManager = persistenceProvider(project)
  private var previewState: CustomViewPreviewManager.PreviewState = CustomViewPreviewManager.PreviewState.LOADING

  private val previewId = "$CUSTOM_VIEW_PREVIEW_ID${virtualFile.path}"
  private val currentStatePropertyName = "${previewId}_SELECTED"
  private fun dimensionsPropertyNameForClass(className: String) = "${previewId}_${className}_DIMENSIONS"
  private fun wrapContentWidthPropertyNameForClass(className: String) = "${previewId}_${className}_WRAP_CONTENT_W"
  private fun wrapContentHeightPropertyNameForClass(className: String) = "${previewId}_${className}_WRAP_CONTENT_H"

  private var classes = listOf<String>()
    set(value) {
      if (field != value) {
        field = value
        if (field.isEmpty()) {
          currentView = ""
        }
        else if (!views.contains(currentView)) {
          currentView = views.first()
        }
      }
    }

  // We use a list to preserve the order
  override val views: List<String>
    get() {
      return classes.map { fqcn2name(it) }
    }

  override var currentView: String = persistenceManager.getValue(currentStatePropertyName, "")
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(currentStatePropertyName, value)
        updateModel()
      }
    }

  override var shrinkHeight = persistenceManager.getValue(wrapContentHeightPropertyNameForClass(currentView), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentHeightPropertyNameForClass(currentView), value)
        updateModel()
      }
    }

  override var shrinkWidth = persistenceManager.getValue(wrapContentWidthPropertyNameForClass(currentView), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentWidthPropertyNameForClass(currentView), value)
        updateModel()
      }
    }

  override val state: CustomViewPreviewManager.PreviewState
    get() = previewState

  private val notificationsPanel = NotificationPanel(
    ExtensionPointName.create<EditorNotifications.Provider<EditorNotificationPanel>>(
      "com.android.tools.idea.customview.preview.customViewEditorNotificationProvider"))

  private val surface = NlDesignSurface.builder(project, this)
    .setDefaultSurfaceState(DesignSurface.State.SPLIT)
    .setSceneManagerProvider { surface, model ->
      NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
        setShrinkRendering(true)
      }
    }.build().apply {
      setScreenMode(SceneMode.RESIZABLE_PREVIEW, false)
    }

  private val actionsToolbar = ActionsToolbar(this@CustomViewPreviewRepresentation, surface)

  private val editorPanel = JPanel(BorderLayout()).apply {
    add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

    val overlayPanel = object : JPanel() {
      // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
      override fun isOptimizedDrawingEnabled(): Boolean = false
    }

    overlayPanel.apply {
      layout = OverlayLayout(this)

      add(notificationsPanel)
      add(surface)
    }

    add(overlayPanel, BorderLayout.CENTER)
  }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  private val workbench = WorkBench<DesignSurface>(project, "Main Preview", null, this).apply {
    init(editorPanel, surface, listOf(), false)
  }

  init {
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        refresh()
      }

      override fun buildFailed() {
        updatePreviewAndNotifications(CustomViewPreviewManager.PreviewState.NOT_COMPILED)
      }

      override fun buildStarted() {
        updatePreviewAndNotifications(CustomViewPreviewManager.PreviewState.BUILDING)
      }
    }, this)

    project.runWhenSmartAndSyncedOnEdt(this, Consumer { refresh() })

    val previewState = when (GradleBuildState.getInstance(project).summary?.status) {
      BuildStatus.SKIPPED, BuildStatus.SUCCESS, null -> CustomViewPreviewManager.PreviewState.LOADING
      else -> CustomViewPreviewManager.PreviewState.NOT_COMPILED
    }
    updatePreviewAndNotifications(previewState)
  }

  private fun updatePreviewAndNotifications(newState: CustomViewPreviewManager.PreviewState) {
    previewState = newState
    fun updatePreview() {
      val nothingToShow = classes.isEmpty()
      when (previewState) {
        CustomViewPreviewManager.PreviewState.LOADING -> {
          workbench.hideContent()
          workbench.showLoading("Waiting for gradle sync to finish...")
        }
        CustomViewPreviewManager.PreviewState.BUILDING -> {
          if (nothingToShow) {
            workbench.hideContent()
            workbench.showLoading("Waiting for build to finish...")
          }
        }
        CustomViewPreviewManager.PreviewState.RENDERING -> workbench.showLoading("Waiting for previews to render...")
        CustomViewPreviewManager.PreviewState.NOT_COMPILED -> {
          if (nothingToShow) {
            workbench.hideContent()
            workbench.loadingStopped("Preview is unavailable until after a successful project build.")
          }
        }
        CustomViewPreviewManager.PreviewState.OK -> {
          workbench.showContent()
          workbench.hideLoading()
        }
      }
    }

    UIUtil.invokeLaterIfNeeded {
      updatePreview()
    }

    EditorNotifications.getInstance(project).updateNotifications(virtualFile)
  }

  override val component = workbench

  override fun dispose() { }

  /**
   * Refresh the preview surfaces
   */
  private fun refresh() {
    updatePreviewAndNotifications(CustomViewPreviewManager.PreviewState.RENDERING)
    // We are in a smart mode here
    classes = (AndroidPsiUtils.getPsiFileSafely(project,
                                                virtualFile) as PsiClassOwner).classes.filter { it.name != null && it.extendsView() }.mapNotNull { it.qualifiedName }
    // This may happen if custom view classes got removed from the file
    if (classes.isEmpty()) {
      return
    }
    updateModel()
  }

  private fun updateModel() {
    surface.deactivate()
    surface.models.forEach { surface.removeModel(it) }
    surface.zoomToFit()
    val selectedClass = classes.firstOrNull { fqcn2name(it) == currentView }
    selectedClass?.let {
      val customPreviewXml = CustomViewLightVirtualFile("custom_preview.xml", getXmlLayout(selectedClass, shrinkWidth, shrinkHeight))
      val facet = AndroidFacet.getInstance(psiFile)!!
      val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
      val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
      val className = fqcn2name(selectedClass)

      // Load and set preview size if exists for this custom view
      persistenceManager.getValues(dimensionsPropertyNameForClass(className))?.let { previewDimensions ->
        updateConfigurationScreenSize(configuration, previewDimensions[0].toInt(), previewDimensions[1].toInt(), configuration.device)
      }

      val model = NlModel.create(this@CustomViewPreviewRepresentation,
                                 className,
                                 facet,
                                 virtualFile,
                                 configuration,
                                 surface.componentRegistrar,
                                 BiFunction { project, _ -> AndroidPsiUtils.getPsiFileSafely(project, customPreviewXml) as XmlFile })
      surface.addModel(model).whenComplete { _, ex ->
        surface.zoomToFit()
        surface.activate()
        configuration.addListener { flags ->
          if ((flags and ConfigurationListener.CFG_DEVICE_STATE) == ConfigurationListener.CFG_DEVICE_STATE) {
            val screen = configuration.device!!.defaultHardware.screen
            persistenceManager.setValues(
              dimensionsPropertyNameForClass(className), arrayOf("${screen.xDimension}", "${screen.yDimension}"))
          }
          true
        }

        if (ex != null) {
          Logger.getInstance(CustomViewPreviewRepresentation::class.java).warn(ex)
        }

        updatePreviewAndNotifications(CustomViewPreviewManager.PreviewState.OK)
      }
    }
  }

  override fun updateNotifications(parentEditor: FileEditor) {
    notificationsPanel.updateNotifications(virtualFile, parentEditor, project)
  }
}
