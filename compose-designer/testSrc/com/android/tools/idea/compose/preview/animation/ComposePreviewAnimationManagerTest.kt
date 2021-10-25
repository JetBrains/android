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
import com.android.SdkConstants
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.TreeWalker
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.rendering.classloading.NopClassLocator
import com.android.tools.idea.rendering.classloading.PreviewAnimationClockMethodTransform
import com.android.tools.idea.rendering.classloading.loaders.AsmTransformingLoader
import com.android.tools.idea.rendering.classloading.loaders.ClassLoaderLoader
import com.android.tools.idea.rendering.classloading.loaders.DelegatingClassLoader
import com.android.tools.idea.rendering.classloading.toClassTransform
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.uipreview.createUrlClassLoader
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.stream.Collectors

class ComposePreviewAnimationManagerTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private lateinit var parentDisposable: Disposable

  private lateinit var surface: DesignSurface

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
    val model = runInEdtAndGet {
      NlModelBuilderUtil.model(
        projectRule,
        "layout",
        "layout.xml",
        ComponentDescriptor(SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER)
      ).build()
    }
    surface = NlDesignSurface.builder(projectRule.project, parentDisposable).build()
    surface.addModelWithoutRender(model)
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
    assertTrue(inspector.tabbedPane.isEmptyVisible)

    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNull(noAnimationsPanel())
    assertFalse(inspector.tabbedPane.isEmptyVisible)

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no animations panel
    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertNotNull(noAnimationsPanel())
    assertTrue(inspector.tabbedPane.isEmptyVisible)
  }

  @Test
  fun oneTabPerSubscribedAnimation() {
    val inspector = createAndOpenInspector()
    assertTrue(inspector.tabbedPane.isEmptyVisible)

    val animation1 = createComposeAnimation()
    val clock = TestClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertFalse(inspector.tabbedPane.isEmptyVisible)
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
    assertTrue(inspector.tabbedPane.isEmptyVisible)

    val clock = TestClock()
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, createComposeAnimation())
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertFalse(inspector.tabbedPane.isEmptyVisible)
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
      override val animationObject = object {
        @Suppress("unused") // Method is called via reflection.
        fun getCurrentState() = "State1"
      }
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
  fun animatedVisibilityComboBoxDisplayAllVisibilitySates() {
    val inspector = createAndOpenInspector()

    val animatedVisibilityAnimation = object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.ANIMATED_VISIBILITY
      override val states = setOf("Enter", "Exit")
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), animatedVisibilityAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val stateComboBoxes = TreeWalker(inspector).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    assertEquals(1, stateComboBoxes.size) // AnimatedVisibility has a single combo box
    val animatedVisibilityComboBox = stateComboBoxes[0] as ComboBox<*>

    assertEquals(2, animatedVisibilityComboBox.itemCount)
    assertEquals("Enter", animatedVisibilityComboBox.getItemAt(0))
    assertEquals("Exit", animatedVisibilityComboBox.getItemAt(1))
    assertEquals("Enter", animatedVisibilityComboBox.selectedItem)
  }

  @Test
  fun animationStatesInferredForBoolean() {
    val inspector = createAndOpenInspector()
    val transitionAnimation = object : ComposeAnimation {
      override val animationObject = Any()
      override val type = ComposeAnimationType.TRANSITION_ANIMATION
      override val states = setOf(true) // Note that `false` is not provided
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(TestClock(), transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val stateComboBoxes = TreeWalker(inspector).descendantStream().filter { it is ComboBox<*> }.collect(Collectors.toList())
    val startStateComboBox = stateComboBoxes[0] as ComboBox<*>

    assertEquals(2, startStateComboBox.itemCount)
    assertEquals(true, startStateComboBox.getItemAt(0))
    assertEquals(false, startStateComboBox.getItemAt(1)) // false inferred because the animation states received had a boolean
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

    val animatedValueWithNullLabel = createComposeAnimation(type = ComposeAnimationType.ANIMATED_VALUE)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animatedValueWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val transitionAnimationWithNullLabel = createComposeAnimation(type = ComposeAnimationType.TRANSITION_ANIMATION)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, transitionAnimationWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animatedVisibilityWithNullLabel = createComposeAnimation(type = ComposeAnimationType.ANIMATED_VISIBILITY)
    ComposePreviewAnimationManager.onAnimationSubscribed(clock, animatedVisibilityWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    assertEquals(5, inspector.tabCount())

    assertEquals("repeatedLabel", inspector.getTabTitleAt(0))
    assertEquals("repeatedLabel (1)", inspector.getTabTitleAt(1)) // repeated titles get their index incremented
    assertEquals("Animated Value", inspector.getTabTitleAt(2)) // null labels use default title
    assertEquals("Transition Animation", inspector.getTabTitleAt(3)) // null labels use default title
    assertEquals("Animated Visibility", inspector.getTabTitleAt(4)) // null labels use default title
  }

  @Test
  @Throws(IOException::class, ClassNotFoundException::class)
  fun classLoaderRedirectsSubscriptionToAnimationManager() {
    class PreviewAnimationClockClassLoader : DelegatingClassLoader(this.javaClass.classLoader,
                                                                   AsmTransformingLoader(
                                                                     toClassTransform({ PreviewAnimationClockMethodTransform(it) }),
                                                                     ClassLoaderLoader(
                                                                       createUrlClassLoader(listOf(
                                                                         resolveWorkspacePath(
                                                                           "tools/adt/idea/compose-designer/testData/classloader").resolve(
                                                                           "composeanimation.jar")
                                                                       ))
                                                                     ),
                                                                     NopClassLocator
                                                                   )) {
      fun loadPreviewAnimationClock(): Class<*> =
        loadClass("androidx.compose.ui.tooling.animation.PreviewAnimationClock")
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

  @Test
  fun animationClockWrapsComposeClockViaReflection() {
    val animationClock = AnimationClock(TestClock())
    // Check that we can find a couple of methods from TestClock
    assertNotNull(animationClock.getAnimatedPropertiesFunction)
    assertNotNull(animationClock.updateAnimatedVisibilityStateFunction)
    // getAnimatedVisibilityState is mangled in TestClock, but we should be able to find it.
    assertNotNull(animationClock.getAnimatedVisibilityStateFunction)

    try {
      // We should throw an Exception if we can't find the given function in the underlying clock, and it's up to the caller to handle this.
      animationClock.findClockFunction("unknownFunction")
      fail("Expected to fail, as `unknownFunction` is not a function of TestClock.")
    }
    catch (ignored: NullPointerException) { }

    // getAnimatedVisibilityState is a supported function, but its name is mangled. We should find it when looking for the function without
    // the hash suffix, not when we specify it.
    assertNotNull(animationClock.findClockFunction("getAnimatedVisibilityState"))
    try {
      animationClock.findClockFunction("getAnimatedVisibilityState-xga21d")
      fail("Expected to fail, as `getAnimatedVisibilityState-xga21d` should not be found when looking for the mangled name.")
    }
    catch (ignored: NullPointerException) { }
  }

  private fun createAndOpenInspector(): AnimationInspectorPanel {
    assertFalse(ComposePreviewAnimationManager.isInspectorOpen())
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable) { }
    assertTrue(ComposePreviewAnimationManager.isInspectorOpen())
    return ComposePreviewAnimationManager.currentInspector!!
  }

  private fun createComposeAnimation(label: String? = null, type: ComposeAnimationType = ComposeAnimationType.ANIMATED_VALUE) =
    object : ComposeAnimation {
      override val animationObject = Any()
      override val type = type
      override val label = label
      override val states = setOf(Any())
    }

  private fun AnimationInspectorPanel.tabCount() = invokeAndWaitIfNeeded { tabbedPane.tabCount }

  private fun AnimationInspectorPanel.getTabTitleAt(index: Int) = invokeAndWaitIfNeeded { tabbedPane.getTabAt(index).text }

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
    fun updateAnimatedVisibilityState(animation: Any, state: Any) {}
    fun `getAnimatedVisibilityState-xga21d`(animation: Any) = "Enter"
  }
}