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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.annotations.concurrency.Slow
import com.android.annotations.concurrency.UiThread
import com.android.resources.ResourceFolderType
import com.android.tools.idea.fileTypes.FontFileType
import com.android.tools.idea.lang.aidl.AidlFileType
import com.android.tools.idea.lang.rs.AndroidRenderscriptFileType
import com.android.tools.idea.util.toPathString
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.util.Consumer
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.plugins.gradle.config.GradleFileType

/**
 * Project component that tracks events that are potentially relevant to Android-specific IDE
 * features.
 *
 * It dispatches these events to other services and components. Having such a centralized dispatcher
 * means we can reuse code written over time to correctly (hopefully) handle different supported
 * scenarios:
 * * Files being created, deleted or moved are handled on the VFS level by a [BulkFileListener].
 * * Changes to files with no cached [Document] or binary files are handled by a
 *   [FileDocumentManagerListener].
 * * Changes to files with a [Document] but no cached [PsiFile] are handled by [DocumentListener].
 * * Changes to files with a cached [PsiFile] are handled by a [PsiTreeChangeListener].
 *
 * Note that these cases are exclusive, so only one event is actually handled by the receiver, no
 * matter what action the user took. This includes cases like user typing with auto-save off
 * (modifies Document and PSI but not VFS), background git checkouts (modifies VFS, but not Document
 * or PSI in some cases).
 *
 * Information is forwarded to:
 * * [ResourceFolderRegistry] and from there to [ResourceFolderRepository] and [LayoutLibrary]
 * * [SampleDataListener] and from there to [SampleDataResourceRepository]
 * * [ResourceNotificationManager]
 * * [EditorNotifications] when a Gradle file is modified
 */
@Service(Service.Level.PROJECT)
class AndroidFileChangeListener(private val project: Project) : Disposable {

  class MyStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
      getInstance(project).onProjectOpened()
    }
  }

  private fun onProjectOpened() {
    PsiManager.getInstance(project).addPsiTreeChangeListener(MyPsiListener(project), this)
    EditorFactory.getInstance()
      .eventMulticaster
      .addDocumentListener(
        MyDocumentListener(project, ResourceFolderRegistry.getInstance(project)),
        this,
      )

    val connection = project.messageBus.connect(this)
    connection.subscribe(
      VirtualFileManager.VFS_CHANGES,
      MyVfsListener(ResourceFolderRegistry.getInstance(project)),
    )
    connection.subscribe(
      FileDocumentManagerListener.TOPIC,
      MyFileDocumentManagerListener(ResourceFolderRegistry.getInstance(project)),
    )
  }

  override fun dispose() {}

  /**
   * [BulkFileListener] which handles [VFileEvent]s for resource folder. When an event happens on a
   * file within a folder with a corresponding [ResourceFolderRepository], the event is delegated to
   * it.
   */
  internal class MyVfsListener(private val registry: ResourceFolderRegistry) : BulkFileListener {
    @UiThread
    override fun before(events: List<VFileEvent>) {
      for (event in events) {
        when (event) {
          is VFileMoveEvent -> onFileOrDirectoryRemoved(event.file)
          is VFileDeleteEvent -> onFileOrDirectoryRemoved(event.file)
          is VFilePropertyChangeEvent -> if (event.isRename) onFileOrDirectoryRemoved(event.file)
        }
      }
    }

    override fun after(events: List<VFileEvent>) {
      for (event in events) {
        when (event) {
          is VFileCreateEvent -> onFileOrDirectoryCreated(event.parent, event.childName)
          is VFileCopyEvent -> onFileOrDirectoryCreated(event.newParent, event.newChildName)
          is VFileMoveEvent -> onFileOrDirectoryCreated(event.newParent, event.file.name)
          is VFilePropertyChangeEvent ->
            if (event.isRename) {
              event.file.parent?.let { onFileOrDirectoryCreated(it, event.newValue as String) }
            }
        // VFileContentChangeEvent changes are not handled at the VFS level, but either in
        // fileWithNoDocumentChanged, documentChanged or MyPsiListener.
        }
      }
    }

    private fun onFileOrDirectoryCreated(parent: VirtualFile?, childName: String) {
      ResourceUpdateTracer.log {
        "AndroidFileChangeListener.MyVfsListener.onFileOrDirectoryCreated(${pathForLogging(parent, childName)})"
      }
      val created = parent?.takeIf(VirtualFile::exists)?.findChild(childName) ?: return
      val resDir = if (created.isDirectory) parent else parent.parent ?: return

      registry.dispatchToRepositories(resDir) { repo, _ -> onFileOrDirectoryCreated(created, repo) }
    }

    private fun pathForLogging(parent: VirtualFile?, childName: String) =
      if (parent == null) childName
      else {
        ResourceUpdateTracer.pathForLogging(
          parent.toPathString().resolve(childName),
          registry.project,
        )
      }

    private fun onFileOrDirectoryRemoved(file: VirtualFile) {
      registry.dispatchToRepositories(file) { repo, f -> repo.onFileOrDirectoryRemoved(f) }
    }

    companion object {
      private fun onFileOrDirectoryCreated(
        created: VirtualFile,
        repository: ResourceFolderRepository?,
      ) {
        if (repository == null) return

        ResourceUpdateTracer.log {
          "AndroidFileChangeListener.MyVfsListener.onFileOrDirectoryCreated($created, ${repository.displayName})"
        }
        if (!created.isDirectory) {
          repository.onFileCreated(created)
        } else {
          // ResourceFolderRepository doesn't handle event on a whole folder, so we pass all the
          // children.
          for (child in created.children) {
            if (!child.isDirectory) {
              // There is no need to visit subdirectories because Android does not support them.
              // If a base resource directory is created (e.g res/), a whole
              // ResourceFolderRepository will be created separately, so we don't need to handle
              // this case here.
              repository.onFileCreated(child)
            }
          }
        }
      }
    }
  }

  internal class MyFileDocumentManagerListener(private val registry: ResourceFolderRegistry) :
    FileDocumentManagerListener {
    override fun fileWithNoDocumentChanged(file: VirtualFile) {
      registry.dispatchToRepositories(file) { repo, f -> repo.scheduleScan(f) }
    }
  }

  internal class MyDocumentListener
  internal constructor(private val project: Project, private val registry: ResourceFolderRegistry) :
    DocumentListener {
    private val fileDocumentManager = FileDocumentManager.getInstance()
    private val psiDocumentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)

    override fun documentChanged(event: DocumentEvent) {
      // Note that event may arrive from any project, not only from the project parameter.
      // The project parameter can be temporarily disposed in light tests.
      if (project.isDisposed) return

      val document = event.document
      if (psiDocumentManager.getCachedPsiFile(document) == null) {
        val virtualFile = fileDocumentManager.getFile(document) ?: return
        if (virtualFile is LightVirtualFile || !isRelevantFile(virtualFile)) return
        runInWriteAction {
          registry.dispatchToRepositories(virtualFile) { repo, f -> repo.scheduleScan(f) }
        }
      }
    }

    private fun runInWriteAction(runnable: Runnable) {
      val application = ApplicationManager.getApplication()
      if (application.isWriteAccessAllowed) {
        runnable.run()
      } else {
        application.invokeLater { application.runWriteAction(runnable) }
      }
    }
  }

  private class MyPsiListener(private val project: Project) : PsiTreeChangeListener {
    private val sampleDataListener
      get() = SampleDataListener.getInstanceIfCreated(project)

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {}

    override fun childAdded(event: PsiTreeChangeEvent) {
      val psiFile = event.file
      when {
        psiFile == null -> {
          when (val child = event.child) {
            is PsiFile ->
              child.virtualFile?.let {
                computeModulesToInvalidateAttributeDefinitions(it)
                if (isRelevantFile(it)) dispatchChildAdded(event, it)
              }
            is PsiDirectory -> dispatchChildAdded(event, child.virtualFile)
          }
        }
        isRelevantFile(psiFile) -> dispatchChildAdded(event, psiFile.virtualFile)
        isGradleFile(psiFile) -> notifyGradleEdit()
      }

      sampleDataListener?.childAdded(event)
    }

    private fun dispatchChildAdded(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.childAdded(event) }
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}

    override fun childRemoved(event: PsiTreeChangeEvent) {
      val psiFile = event.file

      psiFile?.virtualFile?.let(::computeModulesToInvalidateAttributeDefinitions)

      when {
        psiFile == null -> {
          when (val child = event.child) {
            is PsiFile ->
              child.virtualFile?.takeIf(::isRelevantFile)?.let { dispatchChildRemoved(event, it) }
            is PsiDirectory ->
              if (ResourceFolderType.getFolderType(child.name) != null) {
                dispatchChildRemoved(event, child.virtualFile)
              }
          }
        }
        isRelevantFile(psiFile) -> dispatchChildRemoved(event, psiFile.virtualFile)
        isGradleFile(psiFile) -> notifyGradleEdit()
      }

      sampleDataListener?.childRemoved(event)
    }

    private fun dispatchChildRemoved(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.childRemoved(event) }
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}

    override fun childReplaced(event: PsiTreeChangeEvent) {
      val psiFile = event.file
      if (psiFile == null) {
        val parent = event.parent as? PsiDirectory ?: return
        dispatchChildReplaced(event, parent.virtualFile)
        return
      }
      val file = psiFile.virtualFile
      if (file != null) computeModulesToInvalidateAttributeDefinitions(file)

      when {
        isRelevantFile(psiFile) -> dispatchChildReplaced(event, file)
        isGradleFile(psiFile) -> notifyGradleEdit()
      }

      sampleDataListener?.childReplaced(event)
    }

    private fun dispatchChildReplaced(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.childReplaced(event) }
    }

    private fun notifyGradleEdit() {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      event.file?.takeIf(::isRelevantFile)?.let {
        dispatchBeforeChildrenChange(event, it.virtualFile)
      }
    }

    private fun dispatchBeforeChildrenChange(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.beforeChildrenChange(event) }
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      val psiFile = event.file ?: return
      val file = psiFile.virtualFile
      if (file != null) computeModulesToInvalidateAttributeDefinitions(file)

      if (isRelevantFile(psiFile)) dispatchChildrenChanged(event, file)

      sampleDataListener?.childrenChanged(event)
    }

    private fun dispatchChildrenChanged(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.childrenChanged(event) }
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {}

    override fun childMoved(event: PsiTreeChangeEvent) {
      val psiFile = event.file
      if (psiFile == null) {
        val child = (event.child as? PsiFile)?.takeIf(::isRelevantFile) ?: return
        child.virtualFile?.let {
          dispatchChildMoved(event, it)
          return
        }

        (event.oldParent as? PsiDirectory)?.let { dispatchChildMoved(event, it.virtualFile) }
      } else {
        // Change inside a file
        val file = psiFile.virtualFile ?: return
        computeModulesToInvalidateAttributeDefinitions(file)
        if (isRelevantFile(file)) dispatchChildMoved(event, file)

        sampleDataListener?.childMoved(event)
      }
    }

    private fun dispatchChildMoved(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.childMoved(event) }

      // If you moved the file between resource directories, potentially notify that previous
      // repository as well
      if (event.file == null) {
        val oldParent = event.oldParent as? PsiDirectory ?: return
        dispatch(oldParent.virtualFile) { it?.childMoved(event) }
      }
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName) {
        (event.child as? PsiFile)?.takeIf(::isRelevantFile)?.let {
          dispatchBeforePropertyChange(event, it.virtualFile)
        }
      }
    }

    private fun dispatchBeforePropertyChange(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.beforePropertyChange(event) }
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      if (PsiTreeChangeEvent.PROP_FILE_NAME == event.propertyName) {
        (event.element as? PsiFile)?.takeIf(::isRelevantFile)?.let {
          dispatchPropertyChanged(event, it.virtualFile)
        }
      }

      // TODO: Do we need to handle PROP_DIRECTORY_NAME for users renaming any of the resource
      // folders? And what about PROP_FILE_TYPES -- can users change the type of an XML File to
      // something else?
    }

    private fun dispatchPropertyChanged(event: PsiTreeChangeEvent, virtualFile: VirtualFile?) {
      dispatch(virtualFile) { it?.propertyChanged(event) }
    }

    /** Invalidates attribute definitions of relevant modules after changes to a given file */
    private fun computeModulesToInvalidateAttributeDefinitions(file: VirtualFile) {
      if (!isRelevantFile(file)) return
      val facet = AndroidFacet.getInstance(file, project) ?: return

      for (module in AndroidUtils.getSetWithBackwardDependencies(facet.module)) {
        AndroidFacet.getInstance(module)?.let {
          ModuleResourceManagers.getInstance(it)
            .localResourceManager
            .invalidateAttributeDefinitions()
        }
      }
    }

    private fun dispatch(file: VirtualFile?, invokeCallback: Consumer<PsiTreeChangeListener>) {
      if (file != null)
        ResourceFolderRegistry.getInstance(project).dispatchToRepositories(file, invokeCallback)
      ResourceNotificationManager.getInstance(project).psiListener?.let(invokeCallback::consume)
    }
  }

  companion object {
    fun getInstance(project: Project): AndroidFileChangeListener {
      return project.getService(AndroidFileChangeListener::class.java)
    }

    private fun isRelevantFileType(fileType: FileType): Boolean {
      // fail fast for vital file type
      if (fileType === JavaFileType.INSTANCE || fileType === KotlinFileType.INSTANCE) return false
      if (fileType === XmlFileType.INSTANCE) return true

      // TODO: ensure that only Android-compatible images are recognized.
      return fileType.isBinary &&
        (fileType === ImageFileTypeManager.getInstance().imageFileType ||
          fileType === FontFileType.INSTANCE)
    }

    /** Checks if the file is relevant. May perform file I/O. */
    @JvmStatic
    @Slow
    fun isRelevantFile(file: VirtualFile): Boolean {
      // VirtualFile.getFileType will try to read from the file the first time it's
      // called, so we try to avoid it as much as possible. Instead, we will just
      // try to infer the type based on the extension.
      val extension = file.extension
      if (StringUtil.isEmpty(extension)) return false
      if (JavaFileType.DEFAULT_EXTENSION == extension || KotlinFileType.EXTENSION == extension)
        return false
      if (XmlFileType.DEFAULT_EXTENSION == extension) return true
      if (SdkConstants.FN_ANDROID_MANIFEST_XML == file.name) return true
      if (AidlFileType.DEFAULT_ASSOCIATED_EXTENSION == extension) return true
      if (file.parent?.name?.startsWith(SdkConstants.FD_RES_RAW) == true) return true

      // Unable to determine based on filename, use the slow method.
      val fileType = file.fileType
      return fileType == AndroidRenderscriptFileType.INSTANCE || isRelevantFileType(fileType)
    }

    @JvmStatic
    fun isRelevantFile(file: PsiFile): Boolean {
      val fileType = file.fileType
      if (fileType === JavaFileType.INSTANCE || fileType === KotlinFileType.INSTANCE) return false
      if (isRelevantFileType(fileType)) return true

      return file.parent?.name?.startsWith(SdkConstants.FD_RES_RAW) ?: false
    }

    @JvmStatic
    fun isGradleFile(psiFile: PsiFile): Boolean {
      if (GradleFileType.isGradleFile(psiFile)) return true

      val fileType = psiFile.fileType
      val name = psiFile.name
      if (fileType.name == "Kotlin" && name.endsWith(SdkConstants.EXT_GRADLE_KTS)) return true

      // Do not test getFileType() as this will differ depending on whether the TOML plugin is
      // active.
      if (name.endsWith(SdkConstants.DOT_VERSIONS_DOT_TOML)) return true

      return fileType === PropertiesFileType.INSTANCE &&
        (SdkConstants.FN_GRADLE_PROPERTIES == name ||
          SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES == name ||
          (SdkConstants.FN_GRADLE_CONFIG_PROPERTIES == name &&
            SdkConstants.FD_GRADLE_CACHE == psiFile.parent?.name))
    }
  }
}
