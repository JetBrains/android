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
import com.android.tools.idea.nav.safeargs.module.ModuleNavigationResourcesModificationTracker
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.getSourceAsVirtualFile
import com.android.tools.idea.util.LazyFileListenerSubscriber
import com.android.tools.idea.util.PoliteAndroidVirtualFileListener
import com.android.tools.idea.util.listenUntilNextSync
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.messages.Topic
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

/**
 * A project-wide listener that determines which modules' navigation files are affected by VFS
 * changes or Document changes and sends a [NAVIGATION_RESOURCES_CHANGED] event to tell the
 * corresponding [ModuleNavigationResourcesModificationTracker]s and
 * [ProjectNavigationResourceModificationTracker]s to increment counter.
 *
 * [NavigationResourcesModificationListener] registers itself to start actively listening for VFS
 * changes and Document changes after the project opening.
 */
class NavigationResourcesModificationListener(project: Project) :
  PoliteAndroidVirtualFileListener(project), DocumentListener, FileDocumentManagerListener {

  private val psiDocumentManager = PsiDocumentManager.getInstance(project)
  private val fileDocumentManager = FileDocumentManager.getInstance()

  // If a directory was deleted, we won't get a separate event for each descendant, so we
  // must let directories pass through this fail-fast filter in case they contain relevant files.
  override fun isPossiblyRelevant(file: VirtualFile): Boolean {
    return file.isDirectory || file.extension == "xml"
  }

  override fun isRelevant(file: VirtualFile, facet: AndroidFacet): Boolean {
    if (
      ResourceFolderType.getFolderType(file.parent?.name.orEmpty()) == ResourceFolderType.NAVIGATION
    ) {
      return true
    }

    if (!file.isDirectory) {
      return false
    }

    // If module resources aren't cached, we don't want to load them now on the event thread; just
    // say the file is relevant. In the case
    // where it's not truly relevant but we increment the modification trackers anyway, we may have
    // some unnecessary cache invalidation.
    val moduleResources =
      StudioResourceRepositoryManager.getInstance(facet).cachedModuleResources ?: return true
    val navResourceVfs =
      moduleResources
        .getResources(ResourceNamespace.RES_AUTO, ResourceType.NAVIGATION)
        .values()
        .mapNotNull(ResourceItem::getSourceAsVirtualFile)

    // If the directory is an ancestor of any navigation resource files.
    return navResourceVfs.any { navVFile -> VfsUtilCore.isAncestor(file, navVFile, false) }
  }

  override fun fileChanged(path: PathString, facet: AndroidFacet) {
    dispatchResourcesChanged(facet.module)
  }

  override fun contentsChanged(event: VirtualFileEvent) {
    // Content changes are not handled at the VFS level but either in fileWithNoDocumentChanged or
    // documentChanged
  }

  override fun fileWithNoDocumentChanged(file: VirtualFile) = possiblyIrrelevantFileChanged(file)

  override fun documentChanged(event: DocumentEvent) {
    val document = event.document
    val psiFile = psiDocumentManager.getCachedPsiFile(document)

    if (psiFile == null) {
      fileDocumentManager.getFile(document)?.let { possiblyIrrelevantFileChanged(it) }
    } else {
      psiFile.virtualFile?.let { possiblyIrrelevantFileChanged(it) }
    }
  }

  /**
   * [StartupActivity] responsible for ensuring that a [Project] has a
   * [NavigationResourcesModificationListener] subscribed to listen for both VFS and Document
   * changes when opening projects.
   */
  class SubscriptionStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
      project.getService(Subscriber::class.java).onProjectOpened()
    }
  }

  @Service(Service.Level.PROJECT)
  private class Subscriber(private val project: Project) : Disposable.Default {
    private val subscriber =
      object :
        LazyFileListenerSubscriber<NavigationResourcesModificationListener>(
          NavigationResourcesModificationListener(project),
          this,
        ) {
        override fun subscribe() {
          // To receive all changes happening in the VFS. File modifications may
          // not be picked up immediately if such changes are not saved on the disk yet
          VirtualFileManager.getInstance().addVirtualFileListener(listener, parent)

          // To receive all changes to documents that are open in an editor
          EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, parent)

          // To receive notifications when any Documents are saved or reloaded from disk
          project.messageBus.connect(parent).subscribe(FileDocumentManagerListener.TOPIC, listener)
        }
      }

    fun onProjectOpened() {
      project.listenUntilNextSync(this) { subscriber.ensureSubscribed() }
      // Send a project-wide resource-change event once indexes are ready, to ensure that all
      // modification trackers are updated and all old cached data is cleared.
      DumbService.getInstance(project).runWhenSmart {
        subscriber.listener.dispatchResourcesChanged(null)
      }
    }

    fun ensureSubscribed(project: Project) {
      subscriber.ensureSubscribed()
    }
  }

  private fun dispatchResourcesChanged(module: Module?) {
    project.messageBus
      .syncPublisher(NAVIGATION_RESOURCES_CHANGED)
      .onNavigationResourcesChanged(module)
  }

  companion object {
    /**
     * Normally, this listener waits for the project to finish syncing before subscribing to events,
     * but for tests, we sometimes have to kickstart the subscription process manually.
     */
    @TestOnly
    fun ensureSubscribed(project: Project) {
      project.getService(Subscriber::class.java).ensureSubscribed(project)
    }
  }
}

fun interface NavigationResourcesChangeListener {
  /**
   * Called when the navigation resources for a given module have changed.
   *
   * If [module] is `null`, this is the result of a project-wide change, and all modules should be
   * considered changed.
   */
  fun onNavigationResourcesChanged(module: Module?)
}

/** An event fired on the project message bus when navigation resources are updated. */
val NAVIGATION_RESOURCES_CHANGED: Topic<NavigationResourcesChangeListener> =
  Topic(NavigationResourcesChangeListener::class.java, Topic.BroadcastDirection.TO_CHILDREN, true)
