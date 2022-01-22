/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.editors.literals

import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.Executor

data class MethodReference(val file: PsiFile, val function: KtNamedFunction)

/**
 * Allows any component to listen to all method body edits of a project.
 */
@Service
class LiveEditService private constructor(project: Project, var listenerExecutor: Executor) : Disposable {

  constructor(project: Project) : this(project,
                                       AppExecutorUtil.createBoundedApplicationPoolExecutor(
                                         "Document changed listeners executor", 1))

  fun interface EditListener {
    operator fun invoke(method: MethodReference)
  }

  private val onEditListeners = ListenerCollection.createWithExecutor<EditListener>(listenerExecutor)

  fun addOnEditListener(listener: EditListener) {
    onEditListeners.add(listener)
  }

  init {
    // TODO: Deactivate this when not needed.
    val listener = MyPsiListener(::onMethodBodyUpdated)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditService::class.java)
  }

  @com.android.annotations.Trace
  private fun onMethodBodyUpdated(method: MethodReference) {
    onEditListeners.forEach {
      it(method)
    }
  }

  private class MyPsiListener(private val editListener: EditListener) : PsiTreeChangeListener {
    @com.android.annotations.Trace
    private fun handleChangeEvent(event: PsiTreeChangeEvent) {
      var parent = event.parent;

      // The code might not be valid at this point, so we should not be making any
      // assumption based on the Kotlin language structure.
      while (parent != null) {
        when (parent) {
          is KtNamedFunction -> {
            val ref = MethodReference(event.file!!, parent)
            editListener(ref)
            break;
          }
        }
        parent = parent.parent;
      }
    }

    override fun childAdded(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childRemoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childReplaced(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childrenChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun childMoved(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun propertyChanged(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    // We don't need to generate two events for every PSI change.
    override fun beforeChildAddition(event: PsiTreeChangeEvent) {}
    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {}
    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {}
    override fun beforeChildMovement(event: PsiTreeChangeEvent) {}
    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {}
    override fun beforePropertyChange(event: PsiTreeChangeEvent) {}
  }

  override fun dispose() {
    //TODO: "Not yet implemented"
  }
}