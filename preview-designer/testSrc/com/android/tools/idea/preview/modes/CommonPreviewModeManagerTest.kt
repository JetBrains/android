package com.android.tools.idea.preview.modes

import com.android.testutils.MockitoKt.mock
import com.android.testutils.TestUtils
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewElement
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CommonPreviewModeManagerTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private lateinit var scope: CoroutineScope

  @Before
  fun setUp() {
    scope = AndroidCoroutineScope(projectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun testOnExitAndOnEnterAreCalledWithOldAndNewModes(): Unit = runBlocking {
    val modes =
      listOf(
        PreviewMode.Default, // default manager mode
        PreviewMode.Interactive(mock<PreviewElement>()),
        PreviewMode.Default,
        PreviewMode.UiCheck(mock<PreviewElement>(), atfChecksEnabled = true),
      )

    val newModes = modes.drop(1) // drop the first one as it's the default mode of the manager
    val oldModes = modes.dropLast(1) // onExit should not be called on the newest mode

    val onExitArguments = mutableListOf<PreviewMode>()
    val onEnterArguments = mutableListOf<PreviewMode>()

    val manager =
      CommonPreviewModeManager(
        scope = scope,
        onEnter = { onEnterArguments += it },
        onExit = { onExitArguments += it },
      )

    for (newMode in newModes) {
      manager.setMode(newMode)
      delayUntilCondition(250) { manager.mode == newMode }
    }

    assertThat(onEnterArguments).isEqualTo(newModes)
    assertThat(onExitArguments).isEqualTo(oldModes)
  }

  @Test
  fun testModeIsSwitchingUntilOnEnterAndOnExitHaveCompleted() {
    val onExitLatch = CountDownLatch(1)
    val onEnterLatch = CountDownLatch(1)
    val newMode = PreviewMode.Interactive(mock<PreviewElement>())

    val manager =
      CommonPreviewModeManager(
        scope = scope,
        onEnter = { onEnterLatch.await() },
        onExit = { onExitLatch.await() },
      )
    assertThat(manager.mode).isEqualTo(PreviewMode.Default)

    manager.setMode(newMode)
    assertThat(manager.mode)
      .isEqualTo(
        PreviewMode.Switching(
          currentMode = PreviewMode.Default,
          newMode = newMode,
        ),
      )

    onExitLatch.countDown()
    assertThat(manager.mode)
      .isEqualTo(
        PreviewMode.Switching(
          currentMode = PreviewMode.Default,
          newMode = newMode,
        ),
      )

    onEnterLatch.countDown()
    TestUtils.eventually { assertThat(manager.mode).isEqualTo(newMode) }
  }
}
