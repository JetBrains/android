package com.android.tools.idea.preview.modes

import com.android.testutils.MockitoKt.mock
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
  fun testRestoreMode(): Unit = runBlocking {
    val manager = CommonPreviewModeManager()

    manager.setMode(PreviewMode.Interactive(mock<PreviewElement>()))

    assertThat(manager.mode.value).isInstanceOf(PreviewMode.Interactive::class.java)

    manager.restorePrevious()
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default)
  }
}
