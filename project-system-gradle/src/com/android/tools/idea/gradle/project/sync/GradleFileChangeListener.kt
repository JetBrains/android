/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.project.upgrade.AssistantInvoker
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.PsiWhiteSpace
import com.intellij.ui.EditorNotifications
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock

/**
 * Listens for changes to the PsiTree of gradle build files. If a tree changes in any
 * meaningful way then relevant file is recorded. A change is meaningful under the following
 * conditions:
 *
 * 1) Only whitespace has been added and deleted
 * 2) The whitespace doesn't affect the structure of the files psi tree
 *
 * For example, adding spaces to the end of a line is not a meaningful change, but adding a new
 * line in between a line i.e "apply plugin: 'java'" -> "apply plugin: \n'java'" will be meaningful.
 *
 * Note: We need to use both sets of before (beforeChildAddition, etc.) and after methods (childAdded, etc.)
 * on the listener. This is because, for some reason, the events we care about on some files are sometimes
 * only triggered with the children set in the after method and sometimes no after method is triggered
 * at all.
 */
class GradleFileChangeListener(val gradleFiles: GradleFiles) : PsiTreeChangeListener {
  fun handleEvent(event: PsiTreeChangeEvent, vararg elements: PsiElement) {
    val psiFile = event.file ?: return
    val isExternalBuildFile = gradleFiles.isExternalBuildFile(psiFile)
    if (!gradleFiles.isGradleFile(psiFile) && !isExternalBuildFile) return
    if (gradleFiles.containsChangedFile(psiFile.virtualFile)) {
      EditorNotifications.getInstance(psiFile.project).updateAllNotifications()
      return
    }
    if (gradleFiles.myProject != psiFile.project) return
    if (!gradleFiles.myScope.contains(psiFile.virtualFile)) return

    var foundChange = false
    for (element in elements) {
      if (element == null || element is PsiWhiteSpace || element is PsiComment) continue
      if (element.node.elementType == GroovyTokenTypes.mNLS) {
        val parent = element.parent ?: continue
        if (parent is GrCodeBlock || parent is PsiFile) continue
      }
      foundChange = true
      break
    }

    if (foundChange) {
      gradleFiles.addChangedFile(psiFile.virtualFile, isExternalBuildFile)
      EditorNotifications.getInstance(psiFile.project).updateAllNotifications()
      gradleFiles.myProject.getService(AssistantInvoker::class.java).expireProjectUpgradeNotifications(gradleFiles.myProject)
    }
  }

  override fun beforePropertyChange(event: PsiTreeChangeEvent) = Unit
  override fun propertyChanged(event: PsiTreeChangeEvent) = Unit

  override fun beforeChildAddition(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
  override fun beforeChildRemoval(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
  override fun beforeChildReplacement(event: PsiTreeChangeEvent) = handleEvent(event, event.newChild, event.oldChild)
  override fun beforeChildMovement(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
  override fun beforeChildrenChange(event: PsiTreeChangeEvent) = handleEvent(event, event.oldChild, event.newChild)
  override fun childAdded(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
  override fun childRemoved(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
  override fun childReplaced(event: PsiTreeChangeEvent) = handleEvent(event, event.newChild, event.oldChild)
  override fun childrenChanged(event: PsiTreeChangeEvent) = handleEvent(event, event.oldChild, event.newChild)
  override fun childMoved(event: PsiTreeChangeEvent) = handleEvent(event, event.child)
}