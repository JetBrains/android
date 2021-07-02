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

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getQualifiedName
import com.android.tools.idea.util.ListenerCollection
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.kotlin.nj2k.postProcessing.type
import org.jetbrains.kotlin.psi.KtNamedDeclarationUtil
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.util.concurrent.Executor
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

// TODO: Reuse the live literal rate for now.
private val DOCUMENT_CHANGE_COALESCE_TIME_MS = StudioFlags.COMPOSE_LIVE_LITERALS_UPDATE_RATE

/**
 * Allows any component to listen to all method body edits of a project.
 */
@Service
class LiveEditService private constructor(private var project: Project, var listenerExecutor: Executor) : Disposable {

  /**
   * @param className Name of the class. For example: java.lang.String
   * @param methodSignature JVM style signature. For example foo(IILjava/lang/String;)V
   */
  data class MethodReference(val file: PsiFile, val className: String, val methodSignature: String)

  val aggregatedEvents = mutableSetOf<MethodReference>()

  constructor(project: Project) : this(project,
                                       AppExecutorUtil.createBoundedApplicationPoolExecutor(
                                         "Document changed listeners executor", 1))


  private val onEditListeners = ListenerCollection.createWithExecutor<(List<MethodReference>) -> Unit>(listenerExecutor)

  private val updateMergingQueue = MergingUpdateQueue("Live Update change queue",
                                                      DOCUMENT_CHANGE_COALESCE_TIME_MS.get(),
                                                      true,
                                                      null,
                                                      this,
                                                      null,
                                                      false).setRestartTimerOnAdd(true)

  fun addOnEditListener(listener: (List<MethodReference>) -> Unit) {
    onEditListeners.add(listener = listener)
  }

  init {
    // TODO: Deactivate this when not needed.
    PsiManager.getInstance(project).addPsiTreeChangeListener(
      MyPsiListener(::onMethodBodyUpdated, updateMergingQueue) { System.nanoTime() }, this)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditService::class.java)
  }

  private fun onMethodBodyUpdated(methods: List<MethodReference>, @Suppress("UNUSED_PARAMETER") lastUpdateNanos: Long) {
    onEditListeners.forEach {
      it(methods)
    }
  }

  /**
   * Listens to changes of method bodies.
   */
  private class MyPsiListener(
    private val onMethodBodyUpdated: (List<MethodReference>, Long) -> Unit,
    private val updateMergingQueue : MergingUpdateQueue,
    private val timeNanosProvider: () -> Long) : PsiTreeChangeListener  {
    private val aggregatedEventsLock = ReentrantLock()

    @GuardedBy("aggregatedEventsLock")
    val aggregatedEvents = mutableSetOf<MethodReference>()

    @GuardedBy("aggregatedEventsLock")
    var lastUpdatedNanos = 0L

    // TODO: Merge changes within the same method.
    private fun onDocumentChanged(events: Set<MethodReference>) {
      val documents = events
        .map { it }
        .distinct()
      onMethodBodyUpdated(documents, aggregatedEventsLock.withLock { lastUpdatedNanos })
    }

    private fun handleChangeEvent(event : PsiTreeChangeEvent) {
      var parent = event.parent;
      var method = ""
      var clazz = ""

      // The code might not be valid at this point so we should not be making any
      // assumption based on the Koltin language structure.
      while (parent != null) {
        when (parent) {
          is KtNamedFunction -> {
            method = parent.name + "("
            var paramSigs = ArrayList<String>()
            parent.valueParameters.forEach {
              paramSigs.add(it.type()?.getQualifiedName().toString())
            }
            method += paramSigs.joinToString{it}
            method += ")"
            var returnType = parent.type()?.getQualifiedName().toString()
            method += returnType;

            clazz = KtNamedDeclarationUtil.getParentFqName(parent).toString()
            break;
          }
        }
        parent = parent.parent;
      }

      if (!clazz.isEmpty() && !method.isEmpty()) {
        val ref = MethodReference(event.file!!, clazz, method)
        aggregatedEventsLock.withLock {
          aggregatedEvents.add(ref)
          lastUpdatedNanos = timeNanosProvider()
        }

        updateMergingQueue.queue(object: Update(ref) {
          override fun run() {
            onDocumentChanged(aggregatedEventsLock.withLock {
              val aggregatedEventsCopy = aggregatedEvents.toSet()
              aggregatedEvents.clear()
              aggregatedEventsCopy
            })
          }})
      }
    }

    override fun beforeChildAddition(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildMovement(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
    }

    override fun beforePropertyChange(event: PsiTreeChangeEvent) {
      handleChangeEvent(event);
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
  }

  override fun dispose() {
    //TODO: "Not yet implemented"
  }
}