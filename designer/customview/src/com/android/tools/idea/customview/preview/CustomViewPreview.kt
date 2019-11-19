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
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationListener
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.uibuilder.model.updateConfigurationScreenSize
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotifications
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.util.function.BiFunction
import javax.swing.JPanel


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
internal class CustomViewPreview(private val psiFile: PsiFile, persistenceProvider: (Project) -> PropertiesComponent) : Disposable, CustomViewPreviewManager, DesignFileEditor(psiFile.virtualFile!!) {
  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile!!
  private val persistenceManager = persistenceProvider(project)

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
          currentState = ""
        }
        else if (!states.contains(currentState)) {
          currentState = states.first()
        }
      }
    }

  // We use a list to preserve the order
  override val states: List<String>
    get() {
      return classes.map { fqcn2name(it) }
    }

  override var currentState: String = persistenceManager.getValue(currentStatePropertyName, "")
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(currentStatePropertyName, value)
        updateModel()
      }
    }

  override var shrinkHeight = persistenceManager.getValue(wrapContentHeightPropertyNameForClass(currentState), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentHeightPropertyNameForClass(currentState), value)
        updateModel()
      }
    }

  override var shrinkWidth = persistenceManager.getValue(wrapContentWidthPropertyNameForClass(currentState), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        persistenceManager.setValue(wrapContentWidthPropertyNameForClass(currentState), value)
        updateModel()
      }
    }

  private val surface = NlDesignSurface.builder(project, this).setSceneManagerProvider { surface, model ->
    NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
      setShrinkRendering(true)
    }
  }.build().apply {
    setScreenMode(SceneMode.RESIZABLE_PREVIEW, false)
  }

  private val actionsToolbar = ActionsToolbar(this@CustomViewPreview, surface)

  private val editorPanel = JPanel(BorderLayout()).apply {
    add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)
    add(surface, BorderLayout.CENTER)
  }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  val workbench = WorkBench<DesignSurface>(project, "Main Preview", this, this).apply {
    init(editorPanel, surface, listOf(), false)
    showLoading("Waiting for build to finish...")
  }

  init {
    component.add(workbench)

    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
        refresh()
      }

      override fun buildFailed() {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
        workbench.loadingStopped("Preview is unavailable until after a successful project sync")
      }
    }, this)
  }

  /**
   * Refresh the preview surfaces
   */
  private fun refresh() {
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
    val selectedClass = classes.firstOrNull { fqcn2name(it) == currentState }
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

      val model = NlModel.create(this@CustomViewPreview,
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
        workbench.hideLoading()
        if (ex != null) {
          Logger.getInstance(CustomViewPreview::class.java).warn(ex)
        }
      }
    }
    editorWithPreview?.isPureTextEditor = selectedClass == null
  }

  override fun getName(): String = "Custom View Preview"

  var editorWithPreview: TextEditorWithCustomViewPreview? = null
}