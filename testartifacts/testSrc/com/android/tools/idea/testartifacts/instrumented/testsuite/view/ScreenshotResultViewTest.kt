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
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
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
import java.awt.image.BufferedImage
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import javax.swing.JLabel
import javax.swing.JScrollPane

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
  fun placeholderIsDisplayedWhenPathIsInvalid() = runInEdtAndWait {
    view.newImagePath = "invalid/path/does/not/exist.png"
    view.updateView()

    val newImagePanel = view.newImagePanelSingle
    val scrollPane = findComponent<JScrollPane>(newImagePanel)!!
    PlatformTestUtil.waitWithEventsDispatching("Viewport view was not set with placeholder", { scrollPane.viewport.view != null }, 5)

    val viewportView = scrollPane.viewport.view as JLabel

    assertThat(viewportView.text).isEqualTo("No Preview Image")
    assertThat(viewportView.icon).isNull()
  }

  @Test
  fun initialTabIsSelected() = runInEdtAndWait {
    assertThat(view.selectedTab).isEqualTo(ScreenshotResultView.ScreenshotViewType.ALL.displayText)
  }

  @Test
  fun clickingTabChangesSelection() = runInEdtAndWait {
    // Initial state check
    assertThat(view.selectedTab).isEqualTo(ScreenshotResultView.ScreenshotViewType.ALL.displayText)

    // "Click" the "New" tab
    view.selectTab(ScreenshotResultView.ScreenshotViewType.NEW.displayText)

    // After click state check
    assertThat(view.selectedTab).isEqualTo(ScreenshotResultView.ScreenshotViewType.NEW.displayText)
  }

  @Test
  fun diffPlaceholderChangesBasedOnTestResult() = runInEdtAndWait {
    // Case 1: Test failed, placeholder should indicate no diff image
    view.testFailed = true
    view.diffImagePath = "invalid/path.png"
    view.updateView()

    val diffPanelFailed = view.diffImagePanelSingle
    val scrollPaneFailed = findComponent<JScrollPane>(diffPanelFailed)!!
    PlatformTestUtil.waitWithEventsDispatching("Viewport view was not set", { scrollPaneFailed.viewport.view != null }, 5)
    val placeholderFailed = scrollPaneFailed.viewport.view as JLabel
    assertThat(placeholderFailed.text).isEqualTo("No Diff Image")

    // Case 2: Test passed, placeholder should indicate no difference
    view.testFailed = false
    view.updateView() // updateView re-triggers the load

    val diffPanelPassed = view.diffImagePanelSingle
    val scrollPanePassed = findComponent<JScrollPane>(diffPanelPassed)!!
    PlatformTestUtil.waitWithEventsDispatching("Viewport view was not set", { scrollPanePassed.viewport.view != null }, 5)
    val placeholderPassed = scrollPanePassed.viewport.view as JLabel
    assertThat(placeholderPassed.text).isEqualTo("No Difference")
  }

  @Test
  fun singleViewZoomActionsAreDisabledWhenNoImageIsPresent() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    imagePanel.setImage(null) // Ensure no image is set

    val event = TestActionEvent.createTestEvent()

    imagePanel.zoomInAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    imagePanel.zoomOutAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    imagePanel.oneToOneAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    imagePanel.fitToScreenAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    imagePanel.toggleGridViewAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    imagePanel.toggleChessboardAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun commonZoomActionsAreDisabledWhenNoImageIsPresent() = runInEdtAndWait {
    view.newImagePanel.setImage(null)
    view.diffImagePanel.setImage(null)
    view.refImagePanel.setImage(null)

    val event = TestActionEvent.createTestEvent()

    view.commonZoomInAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    view.commonZoomOutAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    view.commonOneToOneAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    view.commonFitToScreenAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    view.commonToggleGridViewAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()

    view.commonToggleChessboardAction.update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun commonZoomInActionIncreasesScaleOnAllPanels() = runInEdtAndWait {
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    val panels = listOf(view.newImagePanel, view.diffImagePanel, view.refImagePanel)
    panels.forEach {
      it.setImage(image)
      it.currentScale = 1.0
    }

    view.commonZoomInAction.actionPerformed(TestActionEvent.createTestEvent())

    panels.forEach {
      assertThat(it.currentScale).isGreaterThan(1.0)
    }
  }

  @Test
  fun commonZoomOutActionDecreasesScaleOnAllPanels() = runInEdtAndWait {
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    val panels = listOf(view.newImagePanel, view.diffImagePanel, view.refImagePanel)
    panels.forEach {
      it.setImage(image)
      it.currentScale = 1.0
    }

    view.commonZoomOutAction.actionPerformed(TestActionEvent.createTestEvent())

    panels.forEach {
      assertThat(it.currentScale).isLessThan(1.0)
    }
  }

  @Test
  fun zoomInActionIncreasesScale() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)
    imagePanel.currentScale = 1.0 // Start at 1:1

    imagePanel.zoomInAction.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(imagePanel.currentScale).isGreaterThan(1.0)
  }

  @Test
  fun zoomOutActionDecreasesScale() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)
    imagePanel.currentScale = 1.0 // Start at 1:1

    imagePanel.zoomOutAction.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(imagePanel.currentScale).isLessThan(1.0)
  }

  @Test
  fun actualSizeActionResetsScaleToOne() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)
    imagePanel.currentScale = 1.5 // Start at a different scale

    imagePanel.oneToOneAction.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(imagePanel.currentScale).isEqualTo(1.0)
  }

  @Test
  fun fitToScreenActionEnablesAutoFit() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)
    imagePanel.isAutoFitting = false // Ensure it's off initially

    imagePanel.fitToScreenAction.actionPerformed(TestActionEvent.createTestEvent())

    assertThat(imagePanel.isAutoFitting).isTrue()
  }

  @Test
  fun gridActionTogglesGridVisibility() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)

    val gridAction = imagePanel.toggleGridViewAction
    val event = TestActionEvent.createTestEvent()

    // Initially not selected
    assertThat(gridAction.isSelected(event)).isFalse()

    // Toggle on
    gridAction.setSelected(event, true)
    assertThat(gridAction.isSelected(event)).isTrue()

    // Toggle off
    gridAction.setSelected(event, false)
    assertThat(gridAction.isSelected(event)).isFalse()
  }

  @Test
  fun chessboardActionTogglesChessboardVisibility() = runInEdtAndWait {
    val imagePanel = view.newImagePanelSingle
    val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
    imagePanel.setImage(image)

    val chessboardAction = imagePanel.toggleChessboardAction
    val event = TestActionEvent.createTestEvent()

    // Initially not selected
    assertThat(chessboardAction.isSelected(event)).isFalse()

    // Toggle on
    chessboardAction.setSelected(event, true)
    assertThat(chessboardAction.isSelected(event)).isTrue()

    // Toggle off
    chessboardAction.setSelected(event, false)
    assertThat(chessboardAction.isSelected(event)).isFalse()
  }

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
}