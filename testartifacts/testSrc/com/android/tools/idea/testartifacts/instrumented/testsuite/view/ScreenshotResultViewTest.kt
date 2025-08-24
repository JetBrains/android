/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.tools.idea.testartifacts.instrumented.testsuite.view

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.AppExecutorUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import javax.imageio.ImageIO
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JToggleButton

class ScreenshotResultViewTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var view: ScreenshotResultView
  private lateinit var appExecutorUtilMock: MockedStatic<AppExecutorUtil>

  @Before
  fun setUp() {
    // The @Before block runs on the main test thread, so we set up the mock here.
    appExecutorUtilMock = Mockito.mockStatic(AppExecutorUtil::class.java)

    // Mock a ScheduledExecutorService to run tasks immediately for synchronous tests.
    val directExecutor = Mockito.mock(ScheduledExecutorService::class.java)

    // Handle the .submit() call
    Mockito.doAnswer { invocation ->
      val runnable = invocation.getArgument<Runnable>(0)
      runnable.run()
      CompletableFuture.completedFuture(null)
    }.`when`(directExecutor).submit(Mockito.any(Runnable::class.java))

    // Handle the .schedule() call
    Mockito.doAnswer { invocation ->
      val runnable = invocation.getArgument<Runnable>(0)
      runnable.run()
      // schedule() must return a ScheduledFuture
      Mockito.mock(ScheduledFuture::class.java)
    }.`when`(directExecutor).schedule(Mockito.any(Runnable::class.java), Mockito.anyLong(), Mockito.any(java.util.concurrent.TimeUnit::class.java))

    appExecutorUtilMock.`when`<ScheduledExecutorService> { AppExecutorUtil.getAppExecutorService() }
      .thenReturn(directExecutor)
    appExecutorUtilMock.`when`<ScheduledExecutorService> { AppExecutorUtil.getAppScheduledExecutorService() }
      .thenReturn(directExecutor)

    runInEdtAndWait {
      view = ScreenshotResultView()
    }
  }

  @After
  fun tearDown() {
    // Closing the static mock to restore the original static method
    // and prevent test pollution in other tests.
    appExecutorUtilMock.close()
    runInEdtAndWait {
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  @Test
  fun initialTabIsSelected() = runInEdtAndWait {
    val tabBar = findComponent<JPanel>(view.myView) { it.components.any { c -> c is JToggleButton } }!!
    val allButton = findComponent<JToggleButton>(tabBar) { it.text == "All" }!!
    val newButton = findComponent<JToggleButton>(tabBar) { it.text == "New" }!!

    assertThat(allButton.isSelected).isTrue()
    assertThat(allButton.font.isBold).isTrue()
    assertThat(newButton.isSelected).isFalse()
    assertThat(newButton.font.isBold).isFalse()
  }

  @Test
  fun clickingTabChangesSelection() = runInEdtAndWait {
    val tabBar = findComponent<JPanel>(view.myView) { it.components.any { c -> c is JToggleButton } }!!
    val allButton = findComponent<JToggleButton>(tabBar) { it.text == "All" }!!
    val newButton = findComponent<JToggleButton>(tabBar) { it.text == "New" }!!

    // Initial state check
    assertThat(allButton.isSelected).isTrue()
    assertThat(newButton.isSelected).isFalse()

    // Click "New" tab
    newButton.doClick()

    // After click state check
    assertThat(allButton.isSelected).isFalse()
    assertThat(allButton.font.isBold).isFalse()
    assertThat(newButton.isSelected).isTrue()
    assertThat(newButton.font.isBold).isTrue()
  }

  @Test
  fun placeholderIsDisplayedWhenPathIsInvalid() = runInEdtAndWait {
    view.newImagePath = "invalid/path/does/not/exist.png"
    view.updateView()

    val newImagePanel = view.newImagePanel
    val scrollPane = findComponent<JScrollPane>(newImagePanel)!!
    PlatformTestUtil.waitWithEventsDispatching("Viewport view was not set with placeholder", { scrollPane.viewport.view != null }, 5)

    val viewportView = scrollPane.viewport.view as JLabel

    assertThat(viewportView.text).isEqualTo("No Preview Image")
    assertThat(viewportView.icon).isNull()
  }

  //TODO (b/440892989): Add more test cases

  /**
   * Iteratively searches a container to find the first component of a given type
   * that satisfies an optional predicate. This is non-recursive to support inlining.
   */
  private inline fun <reified T : Component> findComponent(container: Container, crossinline predicate: (T) -> Boolean = { true }): T? {
    val queue = ArrayDeque<Component>()
    queue.add(container)

    while (queue.isNotEmpty()) {
      val component = queue.removeFirst()
      if (component is T && predicate(component)) {
        return component
      }
      if (component is Container) {
        queue.addAll(component.components)
      }
    }
    return null
  }

  /**
   * Finds an [ActionButton] in a component hierarchy by its action's text.
   */
  private fun findActionButton(container: Container, actionText: String): ActionButton? {
    return findComponent(container) { button: ActionButton ->
      button.action.templatePresentation.text == actionText
    }
  }
}