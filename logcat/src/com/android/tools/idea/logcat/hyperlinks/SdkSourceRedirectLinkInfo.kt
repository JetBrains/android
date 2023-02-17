/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.sources.SdkSourcePositionFinder
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.filters.HyperlinkInfoBase
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.VisibleForTesting

/**
 * A HyperlinkInfo that supports Android SDK Sources of the correct API level
 *
 * Based on `MultipleFilesHyperlinkInfo`
 */
internal class SdkSourceRedirectLinkInfo(
  private val project: Project,
  @VisibleForTesting val files: List<VirtualFile>,
  private val lineNumber: Int,
  @VisibleForTesting val apiLevel: Int,
) : HyperlinkInfoBase() {
  override fun navigate(project: Project, hyperlinkLocationPoint: RelativePoint?) {
    val psiManager = PsiManager.getInstance(project)
    val psiFiles = files.mapNotNull { psiManager.findFile(it) }
    when (psiFiles.size) {
      0 -> return
      1 -> openFile(project, psiFiles.first(), lineNumber)
      else -> openFileChooser(psiFiles, lineNumber, hyperlinkLocationPoint)
    }
  }

  private fun openFileChooser(files: List<PsiFile>, lineNumber: Int, hyperlinkLocationPoint: RelativePoint?) {
    val frame = WindowManager.getInstance().getFrame(project)
    val width = frame?.size?.width ?: 200
    val popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(files)
      .setRenderer(GotoFileCellRenderer(width))
      .setTitle(ExecutionBundle.message("popup.title.choose.target.file"))
      .setItemChosenCallback { file: PsiFile -> openFile(project, file, lineNumber) }
      .createPopup()
    if (hyperlinkLocationPoint != null) {
      popup.show(hyperlinkLocationPoint)
    }
    else {
      popup.showInFocusCenter()
    }
  }

  /**
   * Opens a file using [OpenFileHyperlinkInfo.navigate]
   */
  private fun openFile(project: Project, psiFile: PsiFile, lineNumber: Int) {
    val androidSdks = AndroidSdks.getInstance()
    val file = when {
      androidSdks.isInAndroidSdk(psiFile) -> psiFile.getAndroidSdkFile()
      else -> psiFile
    }
    OpenFileHyperlinkInfo(project, file.virtualFile, lineNumber).navigate(project)
  }

  private fun PsiFile.getAndroidSdkFile() = SdkSourcePositionFinder.getInstance(project).getSourcePosition(apiLevel, this, lineNumber).file
}
