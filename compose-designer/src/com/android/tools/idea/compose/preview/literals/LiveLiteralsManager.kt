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
package com.android.tools.idea.compose.preview.literals

import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.common.util.setupChangeListener
import com.android.tools.idea.editors.literals.EmptyLiteralReferenceSnapshot
import com.android.tools.idea.editors.literals.LiteralReferenceSnapshot
import com.android.tools.idea.editors.literals.LiteralsManager
import com.android.tools.idea.editors.literals.highlightSnapshotInEditor
import com.android.tools.idea.rendering.classloading.ConstantRemapperManager
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiEditorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import java.awt.Font

class LiveLiteralsManager(private val project: Project,
                          private val parentDisposable: Disposable,
                          private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
                          private val requestRefresh: () -> Unit) : Disposable {
  private val LOG = Logger.getInstance(LiveLiteralsManager::class.java)
  private val LITERAL_TEXT_ATTRIBUTE = TextAttributes(UIUtil.getActiveTextColor(),
                                                      null,
                                                      UIUtil.getInactiveTextColor(),
                                                      EffectType.ROUNDED_BOX,
                                                      Font.BOLD)

  private val updateMergingQueue = MergingUpdateQueue("Live literals change queue",
                                                      200,
                                                      true,
                                                      null,
                                                      parentDisposable,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)

  private val literalsManager = LiteralsManager()
  private var lastSnapshot: LiteralReferenceSnapshot = EmptyLiteralReferenceSnapshot
  private var activationDisposable: Disposable? = null
  var isEnabled = false
    set(value) {
      if (value != field) {
        field = value
        if (value) activate() else deactivate()
      }
    }

  init {
    Disposer.register(parentDisposable, this)
  }

  @Synchronized
  private fun onDocumentUpdated(@Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    var requestRefresh = false
    lastSnapshot.modified.forEach {
      val constantValue = it.constantValue ?: return@forEach
      it.usages.forEach { elementPath ->
        ConstantRemapperManager.getConstantRemapper().addConstant(
          null, elementPath, it.initialConstantValue, constantValue)
        LOG.debug("[${it.uniqueId}] Constant updated to ${it.text} path=${elementPath}")
      }
      requestRefresh = true
    }

    if (requestRefresh) {
      requestRefresh()
    }
  }

  @Synchronized
  private fun activate() {
    psiFilePointer.element?.let { file ->
      // Take a snapshot
      lastSnapshot = literalsManager.findLiterals(file)

      lastSnapshot.all.forEach {
        val elementPathString = it.usages.joinToString("\n") { element -> element.toString() }
        LOG.debug("[${it.uniqueId}] Found constant ${it.text} \n$elementPathString\n\n")
      }

      val newActivationDisposable = Disposer.newDisposable()

      val editor = PsiEditorUtil.findEditor(file)
      if (editor != null) {
        val highlightManager = HighlightManager.getInstance(project)
        val outHighlighters = mutableSetOf<RangeHighlighter>()
        lastSnapshot.highlightSnapshotInEditor(project, editor, LITERAL_TEXT_ATTRIBUTE, outHighlighters)
        if (outHighlighters.isNotEmpty()) {
          // Remove the highlights if the manager is deactivated
          Disposer.register(newActivationDisposable, Disposable {
            outHighlighters.forEach { highlightManager.removeSegmentHighlighter(editor, it) }
          })
        }
      }

      setupChangeListener(project, file, ::onDocumentUpdated, newActivationDisposable, updateMergingQueue)
      setupBuildListener(project, object : BuildListener {
        override fun buildSucceeded() {
          ConstantRemapperManager.getConstantRemapper().clearConstants(null)
        }

        override fun buildFailed() {
          ConstantRemapperManager.getConstantRemapper().clearConstants(null)
        }

        override fun buildStarted() {
          // Stop the literals listening while the build happens
          deactivate()
        }
      }, newActivationDisposable)

      // Register so the listener is disposed if we are disposed
      Disposer.register(parentDisposable, newActivationDisposable)
      activationDisposable = newActivationDisposable
    }
  }

  @Synchronized
  private fun deactivate() {
    isEnabled = false
    ConstantRemapperManager.getConstantRemapper().clearConstants(null)
    activationDisposable?.let {
      Disposer.dispose(it)
    }
    activationDisposable = null
    requestRefresh()
  }

  override fun dispose() {
    deactivate()
  }
}