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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.utils.ILogger
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile
import java.util.WeakHashMap
import java.util.concurrent.Executor

internal interface PrecompileCallbacks {
  fun onPrecompileSuccess(ktFile: KtFile, irClasses: List<IrClass>)
  fun onPrecompileSkip(ktFile: KtFile)
  fun onPrecompileError(ktFile: KtFile, message: String, throwable: Throwable? = null)
}

internal fun interface PrecompileTask {
  fun submit(executor: Executor)
}

internal class PrecompileManager(private val project: Project, private val precompiler: Precompiler, private val logger: ILogger) {
  private enum class Status { CONTENTS_COPIED, PSI_CREATED, COMPILED }
  private class State private constructor(val status: Status, val fileContentCopy: CharSequence? , val ktFileCopy: KtFile?) {
    companion object {
      fun copied(fileContentCopy: CharSequence) = State(Status.CONTENTS_COPIED, fileContentCopy, null)
      fun psiCreated(ktFileCopy: KtFile) = State(Status.PSI_CREATED, null, ktFileCopy)
      fun compiled() = State(Status.COMPILED, null, null)
    }
  }

  private val precompileState = WeakHashMap<KtFile, State>()

  fun reset() {
    precompileState.clear()
  }

  fun copyForPrecompile(ktFile: KtFile) {
    if (ktFile in precompileState) {
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(ktFile.getVirtualFile()) ?: return
    precompileState[ktFile] = State.copied(document.immutableCharSequence)
  }

  fun getPrecompileTask(ktFile: KtFile, callbacks: PrecompileCallbacks): PrecompileTask {
    return PrecompileTask {
      ReadAction
        .nonBlocking<Boolean> { precompile(ktFile, callbacks) }
        .coalesceBy(this, ktFile.virtualFilePath)
        .submit(it)
    }
  }

  private fun precompile(ktFile: KtFile, callbacks: PrecompileCallbacks): Boolean {
    val state = precompileState[ktFile]
    if (state == null) {
      callbacks.onPrecompileError(ktFile, "No diff baseline was found for $ktFile. This should never happen; please report a bug.")
      return false
    }

    if (state.status == Status.COMPILED) {
      callbacks.onPrecompileSkip(ktFile)
      return true
    }

    return try {
      val irClasses = when (state.status) {
        Status.CONTENTS_COPIED -> {
          // Create a PSI object for the copied file contents, so it can be compiled
          val ktFileCopy = PsiFileFactory.getInstance(project).createFileFromText(state.fileContentCopy!!, ktFile) as KtFile
          ktFileCopy.originalFile = ktFile

          // Store the PSI, so it doesn't need to be recreated if the compilation is interrupted
          precompileState.replace(ktFile, State.psiCreated(ktFileCopy))
          precompiler.compile(ktFileCopy, ktFile.module).map { IrClass(it) }
        }
        Status.PSI_CREATED -> {
          precompiler.compile(state.ktFileCopy!!, ktFile.module).map { IrClass(it) }
        }
        else -> {
          throw IllegalStateException("Unexpected precompile state")
        }
      }

      callbacks.onPrecompileSuccess(ktFile, irClasses)
      precompileState.replace(ktFile, State.compiled())
      logger.info("Pre-compiled $ktFile")
      true
    } catch (p: ProcessCanceledException) {
      throw p
    } catch (t: Throwable) {
      val cause = t.cause
      if (cause is ProcessCanceledException) {
        throw cause
      }
      callbacks.onPrecompileError(ktFile, "Pre-compile of $ktFile failed with exception", t)
      logger.warning("Pre-compile of $ktFile failed: ${t.message}")
      false
    }
  }
}