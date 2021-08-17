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
package com.android.tools.idea.model

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.io.delete
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidRootUtil
import java.util.concurrent.TimeUnit

class MergedManifestModificationListenerTest : AndroidTestCase() {
  private lateinit var mergedManifestTracker: MergedManifestModificationTracker

  override fun setUp() {
    super.setUp()
    MergedManifestModificationListener.ensureSubscribed(project)
    mergedManifestTracker = MergedManifestModificationTracker.getInstance(myModule)
  }

  fun testManifestPsiFileUpdate() {
    // To ensure the PSI file corresponding to the primary manifest virtual file is requested
    Manifest.getMainManifest(myFacet)
    val baseMergedManifestTrackerCount: Long = mergedManifestTracker.modificationCount

    updatePrimaryManifestXml { addPermissionWithoutSaving("com.example.SEND_MESSAGE") }

    // picked up 1 document change(from a cached psi file)
    waitForCondition(2, TimeUnit.SECONDS) { mergedManifestTracker.modificationCount == baseMergedManifestTrackerCount + 1 }
  }

  fun testManifestVirtualFileUpdate() {
    val baseMergedManifestTrackerCount: Long = mergedManifestTracker.modificationCount
    updatePrimaryManifestXml { addPermissionWithSaving("com.example.SEND_MESSAGE") }

    // picked up 1 document(from virtual file corresponding to this specified document) change
    waitForCondition(2, TimeUnit.SECONDS) { mergedManifestTracker.modificationCount == baseMergedManifestTrackerCount + 1 }
  }

  fun testManifestVirtualFileAndPsiFileUpdate() {
    // to ensure the PSI file corresponding to the primary manifest virtual file is requested
    Manifest.getMainManifest(myFacet)
    val baseMergedManifestTrackerCount: Long = mergedManifestTracker.modificationCount
    updatePrimaryManifestXml { addPermissionWithSaving("com.example.SEND_MESSAGE") }

    // picked up 1 document(from a cached psi file) change
    waitForCondition(2, TimeUnit.SECONDS) { mergedManifestTracker.modificationCount == baseMergedManifestTrackerCount + 1 }
  }

  fun testAncestorDirectoryDeleted() {
    val baseMergedManifestTrackerCount: Long = mergedManifestTracker.modificationCount

    val manifestParent = AndroidRootUtil.getPrimaryManifestFile(myFacet)!!.parent!!.toIoFile()
    val manifestParentPath = manifestParent.toPath()
    manifestParentPath.delete(true)
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(manifestParent)

    waitForCondition(2, TimeUnit.SECONDS) { mergedManifestTracker.modificationCount == baseMergedManifestTrackerCount + 1 }
  }

  private fun updatePrimaryManifestXml(update: VirtualFile.() -> Unit) {
    runWriteCommandAction(project) {
      AndroidRootUtil.getPrimaryManifestFile(myFacet)?.let { update(it) }
    }
  }

  private fun VirtualFile.addPermissionWithoutSaving(permissionName: String) {
    with(FileDocumentManager.getInstance().getDocument(this)!!) {
      val text = charsSequence.toString().replace("</manifest>", "<permission android:name='$permissionName'/></manifest>")
      setText(text)
      PsiDocumentManager.getInstance(project).commitDocument(this)
    }
  }

  private fun VirtualFile.addPermissionWithSaving(permissionName: String) {
    with(FileDocumentManager.getInstance().getDocument(this)!!) {
      val text = charsSequence.toString().replace("</manifest>", "<permission android:name='$permissionName'/></manifest>")
      setText(text)
      PsiDocumentManager.getInstance(project).commitDocument(this)
      FileDocumentManager.getInstance().saveDocument(this)
    }
  }
}