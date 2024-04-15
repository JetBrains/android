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
package com.android.tools.idea.compose.preview.animation

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

/**
 * Responsible for opening the [ComposeAnimationPreview] and managing its state. It passes
 * [ComposeAnimationPreview] to [ComposeAnimationSubscriber] so that subscription events update the
 * [ComposeAnimationPreview] content accordingly, adding tabs for newly subscribed animations and
 * closing tabs corresponding to unsubscribed animations.
 */
object ComposeAnimationInspectorManager {
  internal var currentInspector: ComposeAnimationPreview? = null
    private set

  private var newInspectorOpenedCallback: (() -> Unit)? = null

  @UiThread
  fun createAnimationInspectorPanel(
    surface: DesignSurface<LayoutlibSceneManager>,
    parent: Disposable,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    onNewInspectorOpen: () -> Unit,
  ): ComposeAnimationPreview {
    newInspectorOpenedCallback = onNewInspectorOpen
    val animationInspectorPanel =
      ComposeAnimationPreview(
        surface.project,
        ComposeAnimationTracker(AnimationToolingUsageTracker.getInstance(surface)),
        { surface.sceneManager },
        surface,
        psiFilePointer,
      )
    animationInspectorPanel.tracker.openAnimationInspector()
    Disposer.register(parent) { closeCurrentInspector() }
    currentInspector = animationInspectorPanel
    ComposeAnimationSubscriber.setHandler(animationInspectorPanel)
    return animationInspectorPanel
  }

  fun closeCurrentInspector() {
    currentInspector?.let {
      Disposer.dispose(it)
      it.tracker.closeAnimationInspector()
    }
    currentInspector = null
    ComposeAnimationSubscriber.setHandler(null)
    newInspectorOpenedCallback = null
    ComposeAnimationSubscriber.removeAllAnimations()
  }

  /**
   * Expected to be called just before opening an inspector, e.g. before triggering the toolbar
   * action. Invokes [newInspectorOpenedCallback] to handle potential operations that need to be
   * done before opening the new inspector, e.g. closing another one that might be open.
   */
  fun onAnimationInspectorOpened() {
    newInspectorOpenedCallback?.invoke()
  }

  /** Whether the animation inspector is open. */
  fun isInspectorOpen() = currentInspector != null

  /**
   * Invalidates the current animation inspector, so it doesn't display animations out-of-date. Only
   * invalidate for the same [psiFilePointer] as [invalidate] could be called from [Preview] without
   * [ComposeAnimationPreview].
   */
  fun invalidate(psiFilePointer: SmartPsiElementPointer<PsiFile>): Job {
    currentInspector?.let {
      if (
        PsiManager.getInstance(it.psiFilePointer.project)
          .areElementsEquivalent(psiFilePointer.element, it.psiFilePointer.element)
      ) {
        return it.invalidatePanel()
      }
    }
    return CompletableDeferred(Unit)
  }
}
