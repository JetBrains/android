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
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.util.containers.getIfSingle
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.URL
import javax.swing.JTabbedPane

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
    val animation = createComposeAnimation()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    val otherAnimation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationUnsubscribed(otherAnimation)
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun closingInspectorClearsSubscriptions() {
    ComposePreviewAnimationManager.onAnimationSubscribed(null, createComposeAnimation())
    assertFalse(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())

    ComposePreviewAnimationManager.closeCurrentInspector()
    assertTrue(ComposePreviewAnimationManager.subscribedAnimations.isEmpty())
  }

  @Test
  fun noAnimationsPanelShownWhenNoAnimationsAreSubscribed() {
    val inspector = createInspector()

    fun noAnimationsPanel() = TreeWalker(inspector).descendantStream().filter { it is JBLabel }.filter {
      it as JBLabel
      it.text.contains("no animations that can be inspected")
    }.getIfSingle()

    // When first opening the inspector, we show the panel informing there are no supported animations to be displayed
    assertNotNull(noAnimationsPanel())
    assertNull(inspector.animationsTabbedPane())

    // After subscribing an animation, we should display the tabbedPane
    val animation = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    assertNull(noAnimationsPanel())
    assertNotNull(inspector.animationsTabbedPane())

    // After unsubscribing all animations, we should hide the tabbed panel and again display the no animations panel
    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    assertNotNull(noAnimationsPanel())
    assertNull(inspector.animationsTabbedPane())
  }

  @Test
  fun oneTabPerSubscribedAnimation() {
    val inspector = createInspector()
    assertNull(inspector.animationsTabbedPane())

    val animation1 = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    var tabbedPane = inspector.animationsTabbedPane()!!
    assertEquals(1, tabbedPane.tabCount)

    val animation2 = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation2)
    UIUtil.pump() // Wait for the tab to be added on the UI thread
    tabbedPane = inspector.animationsTabbedPane()!!
    assertEquals(2, tabbedPane.tabCount)

    ComposePreviewAnimationManager.onAnimationUnsubscribed(animation1)
    UIUtil.pump() // Wait for the tab to be removed on the UI thread
    tabbedPane = inspector.animationsTabbedPane()!!
    assertEquals(1, tabbedPane.tabCount)
  }

  @Test
  fun comboBoxesDisplayComposeAnimationStates() {
    val inspector = createInspector()

    val animationStates = setOf("State1", "State2")

    val transitionAnimation = object : ComposeAnimation {
      override fun getAnimation() = Any()
      override fun getLabel(): String? = null
      override fun getType() = ComposeAnimationType.TRANSITION_ANIMATION
      override fun getStates() = animationStates
    }

    ComposePreviewAnimationManager.onAnimationSubscribed(null, transitionAnimation)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    // We can get any of the combo boxes, since "from" and "to" states should be the same.
    val stateComboBox = TreeWalker(inspector).descendantStream().filter { it is ComboBox<*> }.findAny().get() as ComboBox<*>
    assertEquals(2, stateComboBox.itemCount)
    val st1 = stateComboBox.getItemAt(0)
    val st2 = stateComboBox.getItemAt(1)
    assertTrue(animationStates.contains(st1))
    assertTrue(animationStates.contains(st2))
    // Sanity-check the states are different to guarantee we're checking "State1" and "State2"
    assertNotEquals(st1, st2)
  }

  @Test
  fun tabsAreNamedFromAnimationLabel() {
    val inspector = createInspector()

    val animation1 = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animation1)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animationWithSameLabel = createComposeAnimation("repeatedLabel")
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animationWithSameLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val animationWithNullLabel = createComposeAnimation()
    ComposePreviewAnimationManager.onAnimationSubscribed(null, animationWithNullLabel)
    UIUtil.pump() // Wait for the tab to be added on the UI thread

    val tabbedPane = inspector.animationsTabbedPane()!!
    assertEquals(3, tabbedPane.tabCount)

    assertEquals("repeatedLabel #1", tabbedPane.getTitleAt(0))
    assertEquals("repeatedLabel #2", tabbedPane.getTitleAt(1)) // repeated titles get their index incremented
    assertEquals("TransitionAnimation #1", tabbedPane.getTitleAt(2)) // null labels use default title
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

  private fun createInspector(): AnimationInspectorPanel {
    assertFalse(ComposePreviewAnimationManager.isInspectorOpen())
    ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, parentDisposable)
    assertTrue(ComposePreviewAnimationManager.isInspectorOpen())
    return ComposePreviewAnimationManager.currentInspector!!
  }

  private fun createComposeAnimation(label: String? = null) = object : ComposeAnimation {
    override fun getAnimation() = Any()
    override fun getLabel(): String? = label
    override fun getType() = ComposeAnimationType.ANIMATED_VALUE
  }

  private fun AnimationInspectorPanel.animationsTabbedPane() =
    TreeWalker(this).descendantStream().filter { it is JTabbedPane }.getIfSingle() as? JTabbedPane
}