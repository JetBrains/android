/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.nav.safeargs.project

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.util.PathString
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.nav.safeargs.module.ModuleNavigationResourcesModificationTracker
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResult
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager.SyncResultListener
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.util.LazyFileListenerSubscriber
import com.android.tools.idea.util.PoliteAndroidVirtualFileListener
import com.android.tools.idea.util.listenUntilNextSync
import com.intellij.AppTopics
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * A project-wide listener that determines which modules' navigation files are affected by VFS changes or Document
 * changes and tell the corresponding [ModuleNavigationResourcesModificationTracker]s and
 * [ProjectNavigationResourceModificationTracker]s to increment counter.
 *
 * [NavigationResourcesModificationListener] registers itself to start actively listening for VFS changes and Document
 * changes after the project opening.
 */
class NavigationResourcesModificationListener(
  project: Project
) : PoliteAndroidVirtualFileListener(project),
    DocumentListener,
    FileDocumentManagerListener {

  private val psiDocumentManager = PsiDocumentManager.getInstance(project)
  private val fileDocumentManager = FileDocumentManager.getInstance()

  // If a directory was deleted, we won't get a separate event for each descendant, so we
  // must let directories pass through this fail-fast filter in case they contain relevant files.
  override fun isPossiblyRelevant(file: VirtualFile): Boolean {
    return file.isDirectory || file.extension == "xml"
  }

  override fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    if (ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.NAVIGATION) {
      return true
    }

    if (!file.isDirectory) {
      return false
    }

    val navResourceVfs = StudioResourceRepositoryManager.getModuleResources(facet)
      .getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)
      .values()
      .mapNotNull(ResourceItem::getSourceAsVirtualFile)

    // If the directory is an ancestor of any navigation resource files.
    return navResourceVfs.any { navVFile ->
      VfsUtilCore.isAncestor(file, navVFile, false)
    }
  }

  override fun fileChanged(path: PathString, facet: AndroidFacet) {
    ModuleNavigationResourcesModificationTracker.getInstance(facet.module).navigationChanged()
    ProjectNavigationResourceModificationTracker.getInstance(facet.module.project).navigationChanged()
  }

  override fun contentsChanged(event: VirtualFileEvent) {
    // Content changes are not handled at the VFS level but either in fileWithNoDocumentChanged or documentChanged
  }

  override fun fileWithNoDocumentChanged(file: VirtualFile) = possiblyIrrelevantFileChanged(file)

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document
    val psiFile = psiDocumentManager.getCachedPsiFile(document)

    if (psiFile == null) {
      fileDocumentManager.getFile(document)?.let { possiblyIrrelevantFileChanged(it) }
    }
    else {
      psiFile.virtualFile?.let { possiblyIrrelevantFileChanged(it) }
    }
  }

  /**
   * [StartupActivity] responsible for ensuring that a [Project] has a [NavigationResourcesModificationListener]
   * subscribed to listen for both VFS and Document changes when opening projects.
   */
  class SubscriptionStartupActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      val resourceListener = NavigationResourcesModificationListener(project)
      val subscriber = object : LazyFileListenerSubscriber<NavigationResourcesModificationListener>(resourceListener, project) {
        override fun subscribe() {
          if (!StudioFlags.NAV_SAFE_ARGS_SUPPORT.get()) return

          // To receive all changes happening in the VFS. File modifications may
          // not be picked up immediately if such changes are not saved on the disk yet
          VirtualFileManager.getInstance().addVirtualFileListener(listener, parent)

          // To receive all changes to documents that are open in an editor
          EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parent)

          // To receive notifications when any Documents are saved or reloaded from disk
          project.messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, listener)
        }
      }
      project.listenUntilNextSync(listener = object : SyncResultListener {
        override fun syncEnded(result: SyncResult) = subscriber.ensureSubscribed()
      })
    }
  }

  companion object {
    /**
     * Normally, this listener waits for the project to finish syncing before subscribing
     * to events, but for tests, we sometimes have to kickstart the subscription process
     * manually.
     */
    @TestOnly
    fun ensureSubscribed(project: Project) {
      project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(SyncResult.SUCCESS)
    }
  }

}