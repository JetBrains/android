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
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.compose.preview.animation.managers.AnimatedVisibilityAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeSupportedAnimationManager
import com.android.tools.idea.compose.preview.animation.managers.ComposeUnsupportedAnimationManager
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.preview.animation.AllTabPanel
import com.android.tools.idea.preview.animation.AnimationPreview
import com.android.tools.idea.preview.animation.BottomPanel
import com.android.tools.idea.preview.animation.DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS
import com.android.tools.idea.preview.animation.InspectorLayout
import com.android.tools.idea.preview.animation.MINIMUM_TIMELINE_DURATION_MS
import com.android.tools.idea.preview.animation.PlaybackControls
import com.android.tools.idea.preview.animation.SliderClockControl
import com.android.tools.idea.preview.animation.timeline.TimelineElement
import com.android.tools.idea.preview.animation.timeline.TransitionCurve
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.executeInRenderSession
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.io.await
import java.awt.BorderLayout
import javax.swing.JComponent
import kotlin.math.max
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the
 * properties (e.g. `ColorPropKeys`) being animated grouped by animation (e.g.
 * `TransitionAnimation`, `AnimatedValue`). In addition, [Timeline] is a timeline view that can be
 * controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The
 * [ComposeAnimationPreview] therefore allows a detailed inspection of Compose animations.
 *
 * @param psiFilePointer a pointer to a [PsiFile] for current Preview in which Animation Preview is
 *   opened.
 */
class ComposeAnimationPreview(
  project: Project,
  val tracker: ComposeAnimationTracker,
  private val sceneManagerProvider: () -> LayoutlibSceneManager?,
  private val rootComponent: JComponent,
  val psiFilePointer: SmartPsiElementPointer<PsiFile>,
) : AnimationPreview<ComposeAnimationManager>(project, sceneManagerProvider, tracker) {

  /** Generates unique tab names for each tab e.g "tabTitle(1)", "tabTitle(2)". */
  private val tabNames = TabNamesGenerator()

  /** Loading panel displayed when the preview has no animations subscribed. */
  private val noAnimationsPanel =
    JBLoadingPanel(BorderLayout(), this).apply {
      name = "Loading Animations Panel"
      setLoadingText(message("animation.inspector.loading.animations.panel.message"))
    }

  /**
   * If [loadingPanelVisible] is true - [noAnimationsPanel] is added to layout and animation tabs
   * are removed. If [loadingPanelVisible] is false - [noAnimationsPanel] is removed from layout and
   * animation tabs are added.
   */
  private val loadingPanelVisible: MutableStateFlow<Boolean> = MutableStateFlow(true)

  private val clockControl = SliderClockControl(timeline)

  private val playbackControls = PlaybackControls(clockControl, tracker, rootComponent, this)

  private val bottomPanel =
    BottomPanel(rootComponent, tracker).apply {
      timeline.addChangeListener { scope.launch(uiThread) { clockTimeMs = timeline.value } }
      addResetListener { scope.launch { resetTimelineAndUpdateWindowSize(false) } }
    }

  @VisibleForTesting
  val coordinationTab = AllTabPanel(this).apply { addPlayback(playbackControls.createToolbar()) }

  /**
   * Wrapper of the `PreviewAnimationClock` that animations inspected in this panel are subscribed
   * to. Null when there are no animations.
   */
  var animationClock: AnimationClock? = null

  private var maxDurationPerIteration = MutableStateFlow(DEFAULT_ANIMATION_PREVIEW_MAX_DURATION_MS)

  /** Update list of [TimelineElement] for selected [ComposeSupportedAnimationManager]s. */
  override suspend fun updateTimelineElements() {
    var minY = InspectorLayout.timelineHeaderHeightScaled()
    // Call once to update all sizes as all curves / lines required it.
    withContext(uiThread) {
      timeline.repaint()
      timeline.sliderUI.elements.forEach { it.dispose() }
      timeline.sliderUI.elements = run {
        val selected = selectedAnimation
        if (selected is ComposeSupportedAnimationManager) {
          // Paint single selected animation.
          val state = selected.elementState.value
          val curve =
            TransitionCurve.create(
              state.valueOffset,
              if (state.frozen) state.frozenValue else null,
              selected.currentTransition,
              minY,
              timeline.sliderUI.positionProxy,
            )
          selected.selectedPropertiesCallback = { curve.timelineUnits = it }
          curve.timelineUnits = selected.selectedProperties
          if (Disposer.tryRegister(this@ComposeAnimationPreview, curve)) {
            AndroidCoroutineScope(curve).launch {
              curve.offsetPx.collect {
                selected.elementState.value =
                  selected.elementState.value.copy(valueOffset = curve.getValueOffset(it))
              }
            }
          }
          listOf(curve)
        } else
          animations.map { tab ->
            tab.createTimelineElement(timeline, minY, timeline.sliderUI.positionProxy).apply {
              minY += heightScaled()
              if (Disposer.tryRegister(this@ComposeAnimationPreview, this)) {
                if (tab is ComposeSupportedAnimationManager) {
                  tab.card.expandedSize = TransitionCurve.expectedHeight(tab.currentTransition)
                  tab.card.setDuration(tab.currentTransition.duration)
                  AndroidCoroutineScope(this).launch {
                    offsetPx.collect {
                      tab.elementState.value =
                        tab.elementState.value.copy(valueOffset = getValueOffset(it))
                    }
                  }
                }
              }
            }
          }
      }
    }
    timeline.repaint()
    timeline.revalidate()
    coordinationTab.revalidate()
  }

  private fun TimelineElement.getValueOffset(offset: Int) =
    if (offset >= 0)
      timeline.sliderUI.positionProxy.valueForXPosition(minX + offset) -
        timeline.sliderUI.positionProxy.valueForXPosition(minX)
    else
      timeline.sliderUI.positionProxy.valueForXPosition(maxX + offset) -
        timeline.sliderUI.positionProxy.valueForXPosition(maxX)

  /**
   * Set clock time
   *
   * @param newValue new clock time in milliseconds.
   * @param longTimeout set true to use a long timeout.
   */
  override suspend fun setClockTime(newValue: Int, longTimeout: Boolean) {
    animationClock?.apply {
      val clockTimeMs = newValue.toLong()
      sceneManagerProvider()?.executeInRenderSession(longTimeout) {
        setClockTimes(
          animations.filterIsInstance<ComposeSupportedAnimationManager>().associate {
            val newTime =
              (if (it.elementState.value.frozen) it.elementState.value.frozenValue.toLong()
              else clockTimeMs) - it.elementState.value.valueOffset
            it.animation to newTime
          }
        )
      }

      animations.filterIsInstance<ComposeSupportedAnimationManager>().forEach {
        it.loadProperties()
      }
      renderAnimation()
    }
  }

  private suspend fun renderAnimation() {
    sceneManagerProvider()?.executeCallbacksAndRequestRender()?.await()
  }

  /**
   * Creates and adds [ComposeAnimationManager] for given [ComposeAnimation] to the panel.
   *
   * If this animation was already added, removes old [ComposeAnimationManager] first. Repaints
   * panel.
   */
  fun addAnimation(animation: ComposeAnimation) =
    scope.launch {
      removeAnimation(animation).join()
      val tab = withContext(uiThread) { createTab(animation) }
      tab.setup()
      withContext(uiThread) { addTab(tab) }
      updateTimelineElements()
    }

  private suspend fun resetTimelineAndUpdateWindowSize(longTimeout: Boolean) {
    // Set the timeline to 0
    setClockTime(0, longTimeout)
    updateMaxDuration(longTimeout)
    // Update the cached value manually to prevent the timeline to set the clock time to 0 using the
    // short timeout.
    timeline.cachedVal = 0
    // Move the timeline slider to 0.
    withContext(uiThread) { clockControl.jumpToStart() }
  }

  private fun updateTimelineMaximum() {
    val timelineMax =
      max(
        animations.maxOfOrNull { it.timelineMaximumMs }?.toLong() ?: maxDurationPerIteration.value,
        maxDurationPerIteration.value,
      )
    scope.launch {
      withContext(uiThread) {
        clockControl.updateMaxDuration(max(timelineMax, MINIMUM_TIMELINE_DURATION_MS))
      }
      updateTimelineElements()
    }
  }

  /**
   * Update the timeline window size, which is usually the duration of the longest animation being
   * tracked. However, repeatable animations are handled differently because they can have a large
   * number of iterations resulting in an unrealistic duration. In that case, we take the longest
   * iteration instead to represent the window size and set the timeline max loop count to be large
   * enough to display all the iterations.
   */
  private suspend fun updateMaxDuration(longTimeout: Boolean = false) {
    val clock = animationClock ?: return

    sceneManagerProvider()?.executeInRenderSession(longTimeout) {
      maxDurationPerIteration.value = clock.getMaxDurationMsPerIteration()
    }
  }

  /** Replaces the [tabbedPane] with [noAnimationsPanel]. */
  private fun showNoAnimationsPanel() {
    loadingPanelVisible.value = true
    // Reset tab names, so when new tabs are added they start as #1
    tabNames.clear()
    // Reset the timeline cached value, so when new tabs are added, any new value will trigger an
    // update
    timeline.cachedVal = -1
    // The animation panel might not have the focus when the "No animations" panel is displayed,
    // i.e. when code has changed in the editor using Fast Preview, and we need to refresh the
    // animation preview, so it displays the most up-to-date animations. For that reason, we need to
    // make
    // sure the animation panel is repainted correctly.
    animationPreviewPanel.repaint()
    playbackControls.pause()
  }

  /**
   * Creates an [ComposeSupportedAnimationManager] corresponding to the given [animation] and add it
   * to the [animations] map. Note: this method does not add the tab to [tabbedPane]. For that,
   * [addTab] should be used.
   */
  @UiThread
  private fun createTab(animation: ComposeAnimation): ComposeAnimationManager =
    when (animation.type) {
      ComposeAnimationType.ANIMATED_VISIBILITY ->
        AnimatedVisibilityAnimationManager(
          animation,
          tabNames.createName(animation),
          tracker,
          animationClock!!,
          maxDurationPerIteration,
          timeline,
          sceneManagerProvider(),
          tabbedPane,
          rootComponent,
          playbackControls,
          { longTimeout -> resetTimelineAndUpdateWindowSize(longTimeout) },
          { updateTimelineMaximum() },
          scope,
        )
      ComposeAnimationType.TRANSITION_ANIMATION,
      ComposeAnimationType.ANIMATE_X_AS_STATE,
      ComposeAnimationType.ANIMATED_CONTENT,
      ComposeAnimationType.INFINITE_TRANSITION ->
        ComposeSupportedAnimationManager(
          animation,
          tabNames.createName(animation),
          tracker,
          animationClock!!,
          maxDurationPerIteration,
          timeline,
          sceneManagerProvider(),
          tabbedPane,
          rootComponent,
          playbackControls,
          { longTimeout -> resetTimelineAndUpdateWindowSize(longTimeout) },
          { updateTimelineMaximum() },
          scope,
        )
      ComposeAnimationType.ANIMATED_VALUE,
      ComposeAnimationType.ANIMATABLE,
      ComposeAnimationType.ANIMATE_CONTENT_SIZE,
      ComposeAnimationType.DECAY_ANIMATION,
      ComposeAnimationType.TARGET_BASED_ANIMATION,
      ComposeAnimationType.UNSUPPORTED ->
        ComposeUnsupportedAnimationManager(animation, tabNames.createName(animation))
    }

  /** Adds an [ComposeAnimationManager] card to [coordinationTab]. */
  @UiThread
  private fun addTab(animationTab: ComposeAnimationManager) {
    val isAddingFirstTab = tabbedPane.tabCount == 0
    coordinationTab.addCard(animationTab.card)
    addAnimation(animationTab)

    if (isAddingFirstTab) {
      // There are no tabs, and we're about to add one. Replace the placeholder panel with the
      // TabbedPane.
      loadingPanelVisible.value = false
      tabbedPane.addTab(
        TabInfo(coordinationTab).apply {
          text = "${message("animation.inspector.tab.all.title")}  "
        },
        0,
      )
      coordinationTab.addTimeline(timeline)
    }
  }

  /**
   * Removes the [ComposeAnimationManager] card and tab corresponding to the given [animation] from
   * [tabbedPane].
   */
  fun removeAnimation(animation: ComposeAnimation) =
    scope.launch {
      withContext(uiThread) {
        animations
          .find { it.animation == animation }
          ?.let { tab ->
            coordinationTab.removeCard(tab.card)
            if (tab is ComposeSupportedAnimationManager)
              tabbedPane.tabs
                .find { it.component == tab.tabComponent }
                ?.let { tabbedPane.removeTab(it) }
            removeAnimation(tab)
            tab.destroy()
            if (animations.isEmpty()) {
              tabbedPane.removeAllTabs()
              tabNames.clear()
              // There are no more tabs. Replace the TabbedPane with the placeholder panel.
              showNoAnimationsPanel()
            } else if (tabbedPane.tabCount != 0) {
              tabbedPane.select(tabbedPane.getTabAt(0), true)
            }
          }
      }
      updateTimelineElements()
    }

  /**
   * Remove all tabs from [tabbedPane], replace it with [noAnimationsPanel], and clears the cached
   * animations.
   */
  fun invalidatePanel() =
    scope.launch {
      /**
       * Calling [removeAnimation] for all animations will properly remove the cards from
       * AllTabPanel, animationsMap, and tabs from tabbedPane. It will also show the
       * noAnimationsPanel when removing all tabs.
       */
      animations.forEach { removeAnimation(it.animation).join() }
    }

  override fun dispose() {
    scope.cancel()
    tabNames.clear()
  }

  init {
    scope.launch(uiThread) {
      loadingPanelVisible.collect {
        if (it) {
          noAnimationsPanel.startLoading()
          animationPreviewPanel.add(noAnimationsPanel, TabularLayout.Constraint(0, 0))
          animationPreviewPanel.remove(tabbedPane.component)
          animationPreviewPanel.remove(bottomPanel)
        } else {
          noAnimationsPanel.stopLoading()
          animationPreviewPanel.remove(noAnimationsPanel)
          animationPreviewPanel.add(tabbedPane.component, TabularLayout.Constraint(0, 0))
          animationPreviewPanel.add(bottomPanel, TabularLayout.Constraint(1, 0))
        }
      }
    }
    scope.launch { maxDurationPerIteration.collect { updateTimelineMaximum() } }
    timeline.dragEndListeners.add { updateTimelineMaximum() }
  }
}
