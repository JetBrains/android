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
package com.android.tools.idea.compose.preview.animation

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.compose.preview.animation.managers.AnimatedVisibilityAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeSupportedAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeUnsupportedAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.FromToSupportedAnimationManager
import com.android.tools.idea.compose.preview.animation.state.BooleanFromToState
import com.android.tools.idea.compose.preview.animation.state.ComposeColorState
import com.android.tools.idea.compose.preview.animation.state.EnumFromToState
import com.android.tools.idea.compose.preview.animation.state.FromToStateComboBox
import com.android.tools.idea.compose.preview.animation.state.PickerState
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.animation.AnimationUnit
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import javax.swing.JComponent
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays details about animations belonging to a Compose Preview.
 *
 * @param psiFilePointer a pointer to a [PsiFile] for current Preview in which Animation Preview is
 *   opened.
 */
class ComposeAnimationPreview(
  project: Project,
  val tracker: ComposeAnimationTracker,
  sceneManagerProvider: () -> LayoutlibSceneManager?,
  private val rootComponent: JComponent,
  val psiFilePointer: SmartPsiElementPointer<PsiFile>,
) :
  AnimationPreview<ComposeAnimationManager>(project, sceneManagerProvider, rootComponent, tracker),
  ComposeAnimationHandler {

  /** Generates unique tab names for each tab e.g "tabTitle(1)", "tabTitle(2)". */
  private val tabNames = TabNamesGenerator()

  /**
   * Wrapper of the `PreviewAnimationClock` that animations inspected in this panel are subscribed
   * to. Null when there are no animations.
   */
  override var animationClock: AnimationClock? = null

  /**
   * Set clock time
   *
   * @param newValue new clock time in milliseconds.
   * @param longTimeout set true to use a long timeout.
   */
  override suspend fun setClockTime(newValue: Int, longTimeout: Boolean) {
    animationClock?.apply {
      val clockTimeMs = newValue.toLong()
      val animationsToUpdate = animations.filterIsInstance<ComposeSupportedAnimationManager>()

      executeInRenderSession(longTimeout) {
        setClockTimes(
          animationsToUpdate.associate {
            val newTime =
              (if (it.frozenState.value.isFrozen) it.frozenState.value.frozenAt.toLong()
              else clockTimeMs) - it.offset.value
            it.animation to newTime
          }
        )
      }
    }
  }

  /**
   * Creates and adds [ComposeAnimationManager] for given [ComposeAnimation] to the panel.
   *
   * If this animation was already added, removes old [ComposeAnimationManager] first. Repaints
   * panel.
   */
  override fun addAnimation(animation: ComposeAnimation) =
    scope.launch {
      removeAnimation(animation).join()
      val tab = withContext(uiThread) { createAnimationManager(animation) }
      tab.setup()
      addAnimationManager(tab)
    }

  override suspend fun updateMaxDuration(longTimeout: Boolean) {
    val clock = animationClock ?: return

    executeInRenderSession(longTimeout) {
      maxDurationPerIteration.value = clock.getMaxDurationMsPerIteration()
    }
  }

  /**
   * Creates an [ComposeAnimationManager] corresponding to the given [animation]. Note: this method
   * does not add the tab to [tabbedPane]. For that, [addAnimationManager] should be used.
   */
  @UiThread
  private fun createAnimationManager(animation: ComposeAnimation): ComposeAnimationManager =
    when (animation.type) {
      ComposeAnimationType.ANIMATED_VISIBILITY ->
        AnimatedVisibilityAnimationManager(
          animation,
          tabNames.createName(animation),
          tracker,
          animationClock!!,
          maxDurationPerIteration,
          getCurrentTime = { clockControl.currentValue() },
          ::executeInRenderSession,
          tabbedPane,
          rootComponent,
          playbackControls,
          {
            updateTimelineElements()
            renderAnimation()
          },
          scope,
        )
      ComposeAnimationType.TRANSITION_ANIMATION,
      ComposeAnimationType.ANIMATE_X_AS_STATE,
      ComposeAnimationType.ANIMATED_CONTENT,
      ComposeAnimationType.INFINITE_TRANSITION -> {
        val states = animation.states
        val currentState = animation.getCurrentState()
        val unit = ComposeUnit.parseStateUnit(currentState)
        val state =
          when {
            unit is ComposeUnit.Color ->
              ComposeColorState(tracker, unit, states.lastOrNull()?.let { unit.create(it) })
            unit !is AnimationUnit.UnitUnknown ->
              PickerState(tracker, states.firstOrNull(), states.lastOrNull())
            currentState is Boolean -> BooleanFromToState(tracker, currentState)
            currentState is Enum<*> ->
              EnumFromToState(tracker, states.toSet() as Set<Enum<*>>, currentState)
            else -> FromToStateComboBox(tracker, states, currentState)
          }
        FromToSupportedAnimationManager(
          animation,
          tabNames.createName(animation),
          tracker,
          animationClock!!,
          maxDurationPerIteration,
          getCurrentTime = { clockControl.currentValue() },
          ::executeInRenderSession,
          tabbedPane,
          rootComponent,
          playbackControls,
          {
            updateTimelineElements()
            renderAnimation()
          },
          scope,
          state,
        )
      }
      ComposeAnimationType.ANIMATED_VALUE,
      ComposeAnimationType.ANIMATABLE,
      ComposeAnimationType.ANIMATE_CONTENT_SIZE,
      ComposeAnimationType.DECAY_ANIMATION,
      ComposeAnimationType.TARGET_BASED_ANIMATION,
      ComposeAnimationType.UNSUPPORTED ->
        ComposeUnsupportedAnimationManager(animation, tabNames.createName(animation))
    }

  /**
   * Removes the [ComposeAnimationManager] card and tab corresponding to the given [animation] from
   * [tabbedPane].
   */
  override fun removeAnimation(animation: ComposeAnimation) =
    scope.launch {
      animations.find { it.animation == animation }?.let { removeAnimationManager(it) }
    }

  override fun invalidatePanel() =
    scope.launch {
      super.invalidatePanel().join()
      tabNames.clear()
    }
}

private fun ComposeAnimation.getCurrentState() =
  when (type) {
    ComposeAnimationType.TRANSITION_ANIMATION ->
      animationObject::class
        .java
        .methods
        .singleOrNull { it.name == "getCurrentState" }
        ?.let {
          it.isAccessible = true
          it.invoke(animationObject)
        } ?: states.firstOrNull()
    else -> states.firstOrNull()
  }
