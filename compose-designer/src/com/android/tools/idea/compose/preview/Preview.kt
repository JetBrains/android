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
package com.android.tools.idea.compose.preview

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.actions.ZoomInAction
import com.android.tools.adtui.actions.ZoomLabelAction
import com.android.tools.adtui.actions.ZoomOutAction
import com.android.tools.adtui.actions.ZoomShortcut
import com.android.tools.adtui.actions.ZoomToFitAction
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.ComposePreviewToolbar.ForceCompileAndRefreshAction
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.util.listenUntilNextSync
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.KotlinFileType
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel

/** Only composables with this annotation will be rendered to the surface */
const val PREVIEW_ANNOTATION = "com.android.tools.preview.Preview"

/** View included in the runtime library that will wrap the @Composable element so it gets rendered by layoutlib */
const val COMPOSE_VIEW_ADAPTER = "com.android.tools.preview.ComposeViewAdapter"

/** [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call */
const val COMPOSABLE_NAME_ATTR = "tools:composableName"

/**
 * Generates the XML string wrapper for one [PreviewElement]
 */
private fun PreviewElement.toPreviewXmlString() =
  """
      <$COMPOSE_VIEW_ADAPTER xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:padding="5dp"
        $COMPOSABLE_NAME_ATTR="$method"/>
  """.trimIndent()

val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter
 * to be able to preview composable methods.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 */
private class ComposeAdapterLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

/**
 * A [FileEditor] that displays a preview of composable elements defined in the given [psiFile].
 *
 * The editor will display previews for all declared `@Composable` methods that also use the `@Preview` (see [PREVIEW_ANNOTATION])
 * annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` method.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 */
private class PreviewEditor(private val psiFile: PsiFile,
                            private val previews: () -> Set<PreviewElement>) : FileEditor, UserDataHolderBase() {
  private val project = psiFile.project
  private val virtualFile = psiFile.virtualFile

  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setSceneManagerProvider {
      surface, model -> NlDesignSurface.defaultSceneManagerProvider(surface, model).apply {
        enableTransparentRendering()
        enableShrinkRendering()
      }
    }
    .build()
    .apply {
      setScreenMode(SceneMode.SCREEN_COMPOSE_ONLY, true)
    }

  private val actionsToolbar = ActionsToolbar(this@PreviewEditor, surface)

  private val editorPanel = JPanel(BorderLayout()).apply {
    add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)
    add(surface, BorderLayout.CENTER)
  }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  private val workbench = WorkBench<DesignSurface>(project, "Compose Preview", this).apply {
    isOpaque = true
    init(editorPanel, surface, listOf())
    showLoading("Waiting for build to finish...")
  }

  /**
   * Initializes the preview editor and triggers a refresh.
   */
  private fun initComposePreview() {
    refreshPreview()

    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildStarted(context: BuildContext) {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
      }

      override fun buildFinished(status: BuildStatus, context: BuildContext?) {
        EditorNotifications.getInstance(project).updateNotifications(virtualFile)
        refreshPreview()
      }
    }, this@PreviewEditor)
  }

  init {
    project.runWhenSmartAndSyncedOnEdt(this, Consumer<ProjectSystemSyncManager.SyncResult> { result ->
      if (result.isSuccessful) {
        initComposePreview()
      }
      else {
        workbench.loadingStopped("Preview is unavailable until after a successful project sync")
        // The project failed to sync, run initialization when the project syncs correctly
        project.listenUntilNextSync(this, object : ProjectSystemSyncManager.SyncResultListener {
          override fun syncEnded(result: ProjectSystemSyncManager.SyncResult) {
            initComposePreview()
          }
        })
      }
    })
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   */
  private fun refreshPreview() {
    surface.models.forEach { surface.removeModel(it) }
    val renders = previews()
      .map { Pair(it, ComposeAdapterLightVirtualFile("testFile.xml", it.toPreviewXmlString())) }
      .map {
        val (previewElement, file) = it
        val facet = AndroidFacet.getInstance(psiFile)!!
        val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
        val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
        val model = NlModel.create(this@PreviewEditor,
                                   previewElement.name,
                                   facet,
                                   file,
                                   configuration,
                                   surface.componentRegistrar)

        Pair(previewElement, model)
      }
      .map {
        val (previewElement, model) = it

        previewElement.configuration.applyTo(model.configuration)

        model
      }
      .map { surface.addModel(it) }
      .toList()

    CompletableFuture.allOf(*(renders.toTypedArray()))
      .whenComplete { _, ex ->
        workbench.hideLoading()
        if (ex != null) {
          Logger.getInstance(PreviewEditor::class.java).warn(ex)
        }
      }

  }

  override fun getComponent(): JComponent = workbench
  override fun getPreferredFocusedComponent(): JComponent? = workbench
  override fun getName(): String = "Compose Preview"
  override fun setState(state: FileEditorState) {}
  override fun dispose() {}
  override fun selectNotify() {}
  override fun deselectNotify() {}
  override fun isValid(): Boolean = file.isValid
  override fun isModified(): Boolean = false
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
  override fun getCurrentLocation(): FileEditorLocation? = null
  override fun getStructureViewBuilder(): StructureViewBuilder? = null
  override fun getFile(): VirtualFile = virtualFile
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
private fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE

private fun requestBuild(project: Project, module: Module) {
  GradleBuildInvoker.getInstance(project).compileJava(arrayOf(module), TestCompileType.NONE)
}

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) : ToolbarActionGroups(surface) {
  /**
   * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
   * of the surface.
   */
  private inner class ForceCompileAndRefreshAction :
    AnAction("Build & Refresh", null, AllIcons.Actions.ForceRefresh) {
    override fun actionPerformed(e: AnActionEvent) {
      val module = surface.model?.module ?: return
      requestBuild(surface.project, module)
    }
  }

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(listOf(
    ForceCompileAndRefreshAction()
  ))

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup(listOf(
    ZoomShortcut.ZOOM_OUT.registerForAction(ZoomOutAction, mySurface, this),
    ZoomLabelAction,
    ZoomShortcut.ZOOM_IN.registerForAction(ZoomInAction, mySurface, this),
    ZoomShortcut.ZOOM_FIT.registerForAction(ZoomToFitAction, mySurface, this)
  ))
}

private class ComposeTextEditorWithPreview(editor: TextEditor, preview: PreviewEditor) :
  TextEditorWithPreview(editor, preview, "Compose Editor")

/**
 * [EditorNotifications.Provider] that displays the notification when the preview needs to be refreshed.
 */
class OutdatedPreviewNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.outdated")

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (fileEditor !is ComposeTextEditorWithPreview) return null

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
    return EditorNotificationPanel().apply {
      setText("The preview is out of date")

      createActionLabel("Refresh", Runnable {
        requestBuild(project, module)
      })
    }
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY
}

/**
 * Provider for Compose Preview editors.
 */
class ComposeFileEditorProvider : FileEditorProvider, DumbAware {
  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(object : DesignerEditorFileType {
        override fun isResourceTypeOf(file: PsiFile): Boolean =
          file.virtualFile is ComposeAdapterLightVirtualFile

        override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
          ComposePreviewToolbar(surface)
      })
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    // Indexing might not be ready so we use this hack for now (looking for the import FQCN)
    return VfsUtil.loadText(file).contains(PREVIEW_ANNOTATION)

    // Ideally, we should look at the AST for the @Preview annotations. This currently triggers an IndexNotReadyException
    // dumb mode but we should be able to look at the AST without hitting that.
    //return findPreviewMethods(project, file).isNotEmpty()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor

    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor,
                                                                PreviewEditor(
                                                                  psiFile = psiFile) {
                                                                  findPreviewMethods(
                                                                    project, file)
                                                                })

    // Queue to avoid refreshing notifications on every key stroke
    val modificationQueue = MergingUpdateQueue("Notifications Update queue",
                                               100,
                                               true,
                                               null,
                                               composeEditorWithPreview)
      .apply {
        setRestartTimerOnAdd(true)
      }
    val updateNotifications = object: Update(file) {
      override fun run() {
        if (composeEditorWithPreview.isModified) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    }

    project.messageBus.connect(composeEditorWithPreview).subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
      modificationQueue.queue(updateNotifications)
    })

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}