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
package com.android.tools.idea.uibuilder.editor

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.SmartAutoRefresher
import com.android.tools.idea.common.editor.SmartRefreshable
import com.android.tools.idea.common.editor.SourceCodeChangeListener
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationListener
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.hasClassesExtending
import com.android.tools.idea.uibuilder.model.updateConfigurationScreenSize
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import icons.StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW
import icons.StudioIcons.LayoutEditor.Toolbar.WRAP_HEIGHT
import icons.StudioIcons.LayoutEditor.Toolbar.WRAP_WIDTH
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinFileType
import java.awt.BorderLayout
import java.util.function.BiFunction
import javax.swing.JComponent
import javax.swing.JPanel


private const val CUSTOM_VIEW_PREVIEW_ID = "android-custom-view"

private val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

private class CustomViewLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

private fun VirtualFile.hasSourceFileExtension() = when (extension) {
  KotlinFileType.INSTANCE.defaultExtension, JavaFileType.INSTANCE.defaultExtension -> true
  else -> false
}

private fun layoutType(wrapContent: Boolean) = if (wrapContent) "wrap_content" else "match_parent"

private fun getXmlLayout(qualifiedName: String, shrinkWidth: Boolean, shrinkHeight: Boolean): String {
  return """
<$qualifiedName
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="${layoutType(shrinkWidth)}"
    android:layout_height="${layoutType(shrinkHeight)}"/>"""
}

private fun fqcn2name(fcqn: String) = fcqn.substringAfterLast('.')

private fun PsiClass.extendsView(): Boolean {
  return this.qualifiedName == "android.view.View" || this.extendsListTypes.any {
    it.resolve()?.extendsView() ?: false
  }
}

private val SUPPORTED_WIDGETS_FQCN = setOf(
  "android.view.ConstraintLayout",
  "android.view.LinearLayout",
  "android.view.RelativeLayout",
  "android.view.View",
  "android.view.ViewGroup",
  "android.widget.Button",
  "android.widget.CheckBox",
  "android.widget.CompoundButton",
  "android.widget.EditText",
  "android.widget.ImageView",
  "android.widget.TextView",
  "androidx.appcompat.widget.AppCompatButton",
  "androidx.appcompat.widget.AppCompatCheckBox",
  "androidx.appcompat.widget.AppCompatEditText",
  "androidx.appcompat.widget.AppCompatImageView",
  "androidx.appcompat.widget.AppCompatTextView"
)

private fun PsiFile.hasClassesExtendingWidget() = node.hasClassesExtending(SUPPORTED_WIDGETS_FQCN)

private fun PsiFile.hasViewSuccessor() =
  when (this) {
    is PsiClassOwner -> {
      if (DumbService.isDumb(this.project)) {
        this.hasClassesExtendingWidget() // Using heuristic when in Dumb mode
      }
      else {
        this.classes.any { it.extendsView() } // Properly detect inheritance from View in Smart mode
      }
    }
    else -> false
  }

/**
 * Text editor that enables preview if the file contains classes extending android.view.View
 */
private class TextEditorWithCustomViewPreview(textEditor: TextEditor, preview: CustomViewPreview) : SeamlessTextEditorWithPreview(
  textEditor, preview, "Custom View and Preview") {
  init {
    preview.editorWithPreview = this
    isPureTextEditor = preview.currentState.isEmpty()
  }
}

/**
 * This provider creates a special text editor extended with a preview for the files containing custom views. Namely, Java and Kotlin
 * source files containing classes that extend (explicitly or implicitly through  a chain of other classes) an android.view.View class
 * for which we can get a visual representation.
 */
class CustomViewEditorProvider : FileEditorProvider, DumbAware {
  init {
    DesignerTypeRegistrar.register(object : DesignerEditorFileType {
      override fun isResourceTypeOf(file: PsiFile) = file.virtualFile is CustomViewLightVirtualFile

      override fun getToolbarActionGroups(surface: DesignSurface) = ToolbarActionGroups(surface)

      override fun isEditable() = true
    })
  }

  override fun accept(project: Project, file: VirtualFile) =
    StudioFlags.NELE_CUSTOM_VIEW_PREVIEW.get() && file.hasSourceFileExtension()

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
    val designEditor = CustomViewPreview(psiFile)
    val editorWithPreview = TextEditorWithCustomViewPreview(textEditor, designEditor)

    // Queue to avoid refreshing notifications on every key stroke
    val modificationQueue = MergingUpdateQueue("Notifications Update queue",
                                               100,
                                               true,
                                               null,
                                               editorWithPreview)
      .apply {
        setRestartTimerOnAdd(true)
      }

    val updateNotifications = object : Update("updateNotifications") {
      override fun run() {
        if (editorWithPreview.isModified) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    }

    PsiManager.getInstance(project).addPsiTreeChangeListener(SourceCodeChangeListener(psiFile) {
      modificationQueue.queue(updateNotifications)
    }, editorWithPreview)

    return editorWithPreview
  }

  override fun getEditorTypeId() = CUSTOM_VIEW_PREVIEW_ID

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * ActionManager that creates a dropdown button allowing to select between the custom views available from the current file.
 */
private class CustomViewPreviewActionManager(
  designSurface: NlDesignSurface, private val previewEditor: CustomViewPreview) : ActionManager<NlDesignSurface>(designSurface) {
  override fun registerActionsShortcuts(component: JComponent, parentDisposable: Disposable?) {}

  override fun getPopupMenuActions(leafComponent: NlComponent?): DefaultActionGroup {
    return DefaultActionGroup()
  }

  override fun getToolbarActions(component: NlComponent?, newSelection: MutableList<NlComponent>): DefaultActionGroup {
    val customViewPreviewActions = DefaultActionGroup()
    val customViews = object : DropDownAction(null, "Custom View for Preview", CUSTOM_VIEW) {
      override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.setText(previewEditor.currentState, false)
      }

      override fun displayTextInToolbar() = true
    }
    previewEditor.states.forEach {
      val state = it
      customViews.add(object : AnAction(it) {
        override fun actionPerformed(e: AnActionEvent) {
          previewEditor.currentState = state
        }
      })
    }

    val wrapWidth = object : ToggleAction(null, null, WRAP_WIDTH) {
      override fun isSelected(e: AnActionEvent) = previewEditor.shrinkWidth

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        previewEditor.shrinkWidth = state
      }
    }

    val wrapHeight = object : ToggleAction(null, null, WRAP_HEIGHT) {
      override fun isSelected(e: AnActionEvent) = previewEditor.shrinkHeight

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        previewEditor.shrinkHeight = state
      }
    }

    customViewPreviewActions.add(customViews)
    customViewPreviewActions.add(wrapWidth)
    customViewPreviewActions.add(wrapHeight)

    return customViewPreviewActions
  }
}

/**
 * A preview for a file containing custom android view classes. Allows selecting between the classes if multiple custom view classes are
 * present in the file.
 */
private class CustomViewPreview(private val psiFile: PsiFile) : SmartRefreshable, DesignFileEditor(psiFile.virtualFile!!) {
  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile!!

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
  val states: List<String>
    get() {
      return classes.map { fqcn2name(it) }
    }

  var currentState: String = PropertiesComponent.getInstance(project).getValue(currentStatePropertyName, "")
    set(value) {
      if (field != value) {
        field = value
        PropertiesComponent.getInstance(project).setValue(currentStatePropertyName, value)
        updateModel()
      }
    }

  var shrinkHeight = PropertiesComponent.getInstance(project).getValue(wrapContentHeightPropertyNameForClass(currentState), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        PropertiesComponent.getInstance(project).setValue(wrapContentHeightPropertyNameForClass(currentState), value)
        updateModel()
      }
    }

  var shrinkWidth = PropertiesComponent.getInstance(project).getValue(wrapContentWidthPropertyNameForClass(currentState), "false").toBoolean()
    set(value) {
      if (field != value) {
        field = value
        PropertiesComponent.getInstance(project).setValue(wrapContentWidthPropertyNameForClass(currentState), value)
        updateModel()
      }
    }

  private val surface = NlDesignSurface.builder(project, this).setSceneManagerProvider { surface, model ->
    NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
      setShrinkRendering(true)
    }
  }.setActionManagerProvider { surface ->
    CustomViewPreviewActionManager(surface as NlDesignSurface, this)
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
  override val workbench = WorkBench<DesignSurface>(project, "Main Preview", this, this).apply {
    init(editorPanel, surface, listOf())
    showLoading("Waiting for build to finish...")
  }

  private val refresher = SmartAutoRefresher(psiFile, this)

  /**
   * Refresh the preview surfaces
   */
  override fun refresh() {
    // We are in a smart mode here
    classes = (AndroidPsiUtils.getPsiFileSafely(project,
                                                virtualFile) as PsiClassOwner).classes.filter { it.name != null && it.extendsView() }.mapNotNull { it.qualifiedName }
    // This may happen if custom view classes got removed from the file
    if (classes.isEmpty()) {
      return
    }
    updateModel()
  }

  override fun buildFailed() {
    workbench.loadingStopped("Preview is unavailable until after a successful project sync")
  }

  private fun updateModel() {
    surface.deactivate()
    surface.models.forEach { surface.removeModel(it) }
    val selectedClass = classes.firstOrNull { fqcn2name(it) == currentState }
    selectedClass?.let {
      val customPreviewXml = CustomViewLightVirtualFile("custom_preview.xml", getXmlLayout(selectedClass, shrinkWidth, shrinkHeight))
      val facet = AndroidFacet.getInstance(psiFile)!!
      val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
      val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
      val className = fqcn2name(selectedClass)

      // Load and set preview size if exists for this custom view
      PropertiesComponent.getInstance(project).getValues(dimensionsPropertyNameForClass(className))?.let { previewDimensions ->
        updateConfigurationScreenSize(configuration, previewDimensions[0].toInt(), previewDimensions[1].toInt())
      }

      val model = NlModel.create(this@CustomViewPreview,
                                 className,
                                 facet,
                                 virtualFile,
                                 configuration,
                                 surface.componentRegistrar,
                                 BiFunction { project, _ -> AndroidPsiUtils.getPsiFileSafely(project, customPreviewXml) as XmlFile })
      surface.addModel(model).whenComplete { _, ex ->
        surface.activate()
        configuration.addListener { flags ->
          if ((flags and ConfigurationListener.CFG_DEVICE_STATE) == ConfigurationListener.CFG_DEVICE_STATE) {
            val screen = configuration.device!!.defaultHardware.screen
            PropertiesComponent.getInstance(project).setValues(
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

class OutdatedCustomViewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("com.android.tools.idea.uibuilder.editor.custom.outdated")

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (fileEditor !is TextEditorWithCustomViewPreview) return null

    // Do not show the notification while the build is in progress
    if (GradleBuildState.getInstance(project).isBuildInProgress) return null

    val isModified = FileDocumentManager.getInstance().isFileModified(file)
    if (!isModified) {
      // The file was saved, check the compilation time
      val modificationStamp = file.timeStamp
      val lastBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).lastBuildTimestamp ?: -1
      if (lastBuildTimestamp >= modificationStamp) return null
    }

    val module = ModuleUtil.findModuleForFile(file, project) ?: return null

    val psiFile = PsiManager.getInstance(project).findFile(file)!!

    val hasCustomView = psiFile.hasViewSuccessor()

    // If they are different -- nothing has changed, we do not need to display anything
    if (hasCustomView != fileEditor.isPureTextEditor) return null

    val message = when {
      hasCustomView and fileEditor.isPureTextEditor -> "You have custom view classes in the source code. Preview will appear after a successful build."
      else -> "You no longer have custom view classes in the source code. Preview will disappear after a successful build."
    }

    return EditorNotificationPanel().apply {
      setText(message)
      createActionLabel("Refresh", Runnable {
        GradleBuildInvoker.getInstance(project).compileJava(arrayOf(module), TestCompileType.NONE)
      })
    }
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}
