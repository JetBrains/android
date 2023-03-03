/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview.lifecycle

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.concurrency.createChildScope
import com.android.tools.idea.concurrency.scopeDisposable
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import org.jetbrains.annotations.TestOnly
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Class that manages preview [PreviewRepresentation.onActivate]/[PreviewRepresentation.onDeactivate] lifecycle. It allows to specify
 * actions that should be executed when the lifecycle events happen and execute custom code scoped to the active mode only.
 *
 * @param parentScope the [PreviewRepresentation] [CoroutineScope]
 * @param onInitActivate the code that should be executed on the very first activation
 * @param onResumeActivate the code that should be executed on the following activations but not the first one
 * @param onDeactivate the code that should be executed right away after deactivation
 * @param onDelayedDeactivate the deactivation code that can be delayed and not needed to be executed right away after the deactivation.
 * This could be because this deactivation will make the next activation take a long time and we want to make sure that we only fully
 * deactivate when we unlikely to activate again.
 */
class PreviewLifecycleManager private constructor(
  private val parentScope: CoroutineScope,
  private val onInitActivate: CoroutineScope.() -> Unit,
  private val onResumeActivate: CoroutineScope.() -> Unit,
  private val onDeactivate: () -> Unit,
  private val onDelayedDeactivate: () -> Unit,
  private val scheduleDelayed: (Disposable, () -> Unit) -> Unit
) {

  /**
   * @param project the project for the [PreviewRepresentation]
   * @param parentScope the [PreviewRepresentation] [CoroutineScope]
   * @param onInitActivate the code that should be executed on the very first activation
   * @param onResumeActivate the code that should be executed on the following activations but not the first one
   * @param onDeactivate the code that should be executed right away after deactivation
   * @param onDelayedDeactivate the deactivation code that can be delayed and not needed to be executed right away after the deactivation.
   * This could be because this deactivation will make the next activation take a long time and we want to make sure that we only fully
   * deactivate when we unlikely to activate again.
   */
  constructor(
    project: Project,
    parentScope: CoroutineScope,
    onInitActivate: CoroutineScope.() -> Unit,
    onResumeActivate: CoroutineScope.() -> Unit,
    onDeactivate: () -> Unit,
    onDelayedDeactivate: () -> Unit
  ) : this(
    parentScope, onInitActivate, onResumeActivate, onDeactivate, onDelayedDeactivate, project
      .getService(PreviewDeactivationProjectService::class.java)
      .deactivationQueue
    ::addDelayedAction
  )

  private val scopeDisposable = parentScope.scopeDisposable()

  /**
   * [CoroutineScope] that is valid while this is active. The scope will be cancelled as soon as this becomes inactive. This scope is used to
   * launch the tasks that only make sense while in the active mode.
   */
  @get:Synchronized
  @set:Synchronized
  private var activationScope: CoroutineScope? = null

  /**
   * Lock used during the [onInitActivate]/[onResumeActivate]/[onDeactivate]/[onDelayedDeactivate] to avoid activations happening in the
   * middle.
   */
  private val activationLock = ReentrantLock()

  /**
   * Tracks whether this is active or not. The value tracks the [activate] and [deactivate] calls.
   */
  private val isActive = AtomicBoolean(false)

  /**
   * Tracks whether [activate] call has been before or not. This is used to decide whether [onInitActivate] or [onResumeActivate] must be
   * called.
   */
  @GuardedBy("activationLock")
  private var isFirstActivation = true

  /**
   * The user should call this to indicate that the parent was activated.
   */
  fun activate() = activationLock.withLock {
    activationScope?.cancel()
    val scope = parentScope.createChildScope(true)
    activationScope = scope

    isActive.set(true)
    if (isFirstActivation) {
      isFirstActivation = false
      scope.onInitActivate()
    } else {
      scope.onResumeActivate()
    }
  }

  private fun delayedDeactivate() = activationLock.withLock {
    if (!isActive.get()) {
      onDelayedDeactivate()
    }
  }

  /**
   * The user should call this to indicate that the parent was deactivated.
   */
  fun deactivate() = activationLock.withLock {
    activationScope?.cancel()
    activationScope = null
    isActive.set(false)

    onDeactivate()

    if (PreviewPowerSaveManager.isInPowerSaveMode) {
      // When on power saving mode, deactivate immediately to free resources.
      onDelayedDeactivate()
    } else {
      scheduleDelayed(scopeDisposable, this::delayedDeactivate)
    }
  }

  /**
   * Allows to execute code that only makes sense in the active mode.
   */
  fun <T> executeIfActive(block: CoroutineScope.() -> T): T? = activationScope?.block()

  companion object {
    @TestOnly
    fun createForTest(parentScope: CoroutineScope,
                      onInitActivate: CoroutineScope.() -> Unit = {},
                      onResumeActivate: CoroutineScope.() -> Unit = {},
                      onDeactivate: () -> Unit = {},
                      onDelayedDeactivate: () -> Unit = {},
                      scheduleDelayed: (Disposable, () -> Unit) -> Unit = { _, _ -> }): PreviewLifecycleManager =
      PreviewLifecycleManager(parentScope, onInitActivate, onResumeActivate, onDeactivate, onDelayedDeactivate, scheduleDelayed)
  }
}