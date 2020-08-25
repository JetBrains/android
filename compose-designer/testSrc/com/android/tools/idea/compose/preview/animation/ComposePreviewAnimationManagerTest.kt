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

import androidx.compose.animation.tooling.ComposeAnimation
import androidx.compose.animation.tooling.ComposeAnimationType
import com.android.testutils.TestUtils
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.idea.rendering.classloading.RenderClassLoader
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.impl.JBEditorTabs
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.stream.Collectors

class ComposePreviewAnimationManagerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var parentDisposable: Disposable

  private lateinit var surface: NlDesignSurface

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
    ComposePreviewAnimationManager.closeCurrentInspector()
  }

  @Test
  fun subscribeAndUnsubscribe() {
    createAndOpenInspector()

    val animation = createComposeAnimation()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val otherAnimation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun closingInspectorClearsSubscriptions() {
    createAndOpenInspector()

    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), createComposeAnimation())
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.closeCurrentInspector()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun noAnimationsPanelShownWhenNoAnimationsAreSubscribed() {
    val inspector = createAndOpenInspector()

    fun noAnimationsPanel() = TreeWalker(inspector).descendantStream().filter { it.name == "Loading Animations Panel" }.getIfSingle()

    // When first opening the inspector, we show the panel informing there are no supported animations to be displayed
    assertNotNull(noAnimationsPanel())
    assertTrue(inspector.tabbedPane().isEmptyVisible)

    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNull(noAnimationsPanel())
    assertFalse(inspector.tabbedPane().isEmptyVisible)

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no animations panel
    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertNotNull(noAnimationsPanel())
    assertTrue(inspector.tabbedPane().isEmptyVisible)
  }

  @Test
  fun oneTabPerSubscribedAnimation() {
    val inspector = createAndOpenInspector()
    assertTrue(inspector.tabbedPane().isEmptyVisible)

    val animation1 = createComposeAnimation()
    val clock = TestClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertFalse(inspector.tabbedPane().isEmptyVisible)
    assertEquals(1, inspector.tabCount())

    val animation2 = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation2)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(2, inspector.tabCount())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation1)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertEquals(1, inspector.tabCount())
  }

  @Test
  fun subscriptionNewClockClearsPreviousClockAnimations() {
    val inspector = createAndOpenInspector()
    val tabbedPane = inspector.tabbedPane
    assertTrue(inspector.tabbedPane().isEmptyVisible)

    val clock = TestClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertFalse(inspector.tabbedPane().isEmptyVisible)
    assertEquals(1, inspector.tabCount())

    val anotherClock = TestClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(anotherClock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(1, inspector.tabCount())

    ComposePreviewAnimationManager.onAnimationSubscribed(anotherClock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertEquals(2, inspector.tabCount())
  }

  @Test
  fun onOpenNewInspectorCallbackClearedWhenClosingInspector() {
    var callbackCalls = 0
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable) { callbackCalls++ }
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)

    ComposePreviewAnimationManager.closeCurrentInspector()
    ComposePreviewAnimationManager.onAnimationInspectorOpened()
    assertEquals(2, callbackCalls)
  }

  @Test
  fun comboBoxesDisplayComposeAnimationStates() {
    val inspector = createAndOpenInspector()

    val animationStates = setOf("State1", "State2", "State3")

    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = animationStates
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val stateComboBoxes = TreeWalker(inspector).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    assertEquals(2, stateComboBoxes.size) // "start" combobox and  "end" combobox.
    val startStateComboBox = stateComboBoxes[0] as ComboBox<*>
    val endStateComboBox = stateComboBoxes[1] as ComboBox<*>

    assertEquals(3, startStateComboBox.itemCount)
    assertEquals("State1", startStateComboBox.getItemAt(0))
    assertEquals("State2", startStateComboBox.getItemAt(1))
    assertEquals("State3", startStateComboBox.getItemAt(2))

    assertEquals("State1", startStateComboBox.selectedItem)
    // The "end" combo box does not display the same state as the "start" combo box if possible
    assertEquals("State2", endStateComboBox.selectedItem)
  }

  @Test
  fun tabsAreNamedFromAnimationLabel() {
    val inspector = createAndOpenInspector()
    val clock = TestClock()

    val animation1 = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animationWithSameLabel = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animationWithSameLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animationWithNullLabel = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animationWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    assertEquals(3, inspector.tabCount())

    assertEquals("repeatedLabel #1", inspector.getTabTitleAt(0))
    assertEquals("repeatedLabel #2", inspector.getTabTitleAt(1)) // repeated titles get their index incremented
    assertEquals("TransitionAnimation #1", inspector.getTabTitleAt(2)) // null labels use default title
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
    class PreviewAnimationClockClassLoader : RenderClassLoader(this.javaClass.classLoader, { PreviewAnimationClockMethodTransform(it) }) {
      override fun getExternalJars(): List<URL> {
        val basePath = TestUtils.getWorkspaceFile("tools/adt/idea/compose-designer/testData/classloader").path
        val jarSource = File(basePath, "composeanimation.jar")
        return ImmutableList.of(jarSource.toURI().toURL())
      }

      fun loadPreviewAnimationClock(): Class<*> {
        return loadClassFromNonProjectDependency("androidx.ui.tooling.preview.animation.PreviewAnimationClock")
      }
    }
    createAndOpenInspector()

    val previewAnimationClockClassLoader = PreviewAnimationClockClassLoader()
    val previewAnimationClock = previewAnimationClockClassLoader.loadPreviewAnimationClock()
    val notifySubscribe = previewAnimationClock.getDeclaredMethod("notifySubscribe", ComposeAnimation::class.java)
    val animation = createComposeAnimation()
    notifySubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val notifyUnsubscribe = previewAnimationClock.getDeclaredMethod("notifyUnsubscribe", ComposeAnimation::class.java)
    notifyUnsubscribe.invoke(previewAnimationClock.newInstance(), animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  private fun createAndOpenInspector(): AnimationInspectorPanel {
    assertFalse(ComposePreviewAnimationManager.isInspectorOpen())
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable) { }
    assertTrue(ComposePreviewAnimationManager.isInspectorOpen())
    return ComposePreviewAnimationManager.currentInspector!!
  }

  private fun createComposeAnimation(label: String? = null) = object : ComposeAnimation {
    override val animationObject = Any()
    override val type = ComposeAnimationType.ANIMATED_VALUE
    override val label = label
  }

  private fun AnimationInspectorPanel.tabbedPane() = tabbedPane.component as JBEditorTabs

  private fun AnimationInspectorPanel.tabCount() = invokeAndWaitIfNeeded { tabbedPane.tabCount }

  private fun AnimationInspectorPanel.getTabTitleAt(index: Int) = invokeAndWaitIfNeeded { tabbedPane.getTitleAt(index) }


  /**
   * Fake class with methods matching PreviewAnimationClock method signatures, so the code doesn't break when the test tries to call them
   * via reflection.
   */
  private class TestClock {
    fun getAnimatedProperties(animation: Any) = emptyList<Any>()
    fun getMaxDuration() = 0L
    fun getMaxDurationPerIteration() = 0L
    fun updateAnimationStates() {}
    fun updateSeekableAnimation(animation: Any, fromState: Any, toState: Any) {}
    fun setClockTime(time: Long) {}
  }
}