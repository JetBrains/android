package com.android.tools.idea.preview.modes

import com.android.testutils.MockitoKt.mock
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewElement
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
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

    for (i in newModes.indices) {
      manager.mode = newModes[i]
      delayUntilCondition(200) { onExitArguments.size == i + 1 }
    }

    assertThat(onEnterArguments).isEqualTo(newModes)
    assertThat(onExitArguments).isEqualTo(oldModes)
  }
}
