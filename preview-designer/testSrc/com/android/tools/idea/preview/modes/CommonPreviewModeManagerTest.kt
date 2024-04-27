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
    val previewElement = mock<PreviewElement>()

    manager.setMode(PreviewMode.Interactive(previewElement))

    assertThat(manager.mode.value).isInstanceOf(PreviewMode.Interactive::class.java)

    manager.restorePrevious()
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default())

    manager.setMode(PreviewMode.Default(GRID_LAYOUT_MANAGER_OPTIONS))
    manager.setMode(PreviewMode.UiCheck(previewElement, GRID_LAYOUT_MANAGER_OPTIONS))
    manager.setMode(PreviewMode.UiCheck(previewElement, LIST_LAYOUT_MANAGER_OPTION))
    manager.restorePrevious()
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default(GRID_LAYOUT_MANAGER_OPTIONS))
  }

  @Test
  fun testChangeModeLayout(): Unit = runBlocking {
    val manager = CommonPreviewModeManager()
    val previewElement = mock<PreviewElement>()

    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default())

    manager.setMode(PreviewMode.Gallery(previewElement))
    assertThat(manager.mode.value.layoutOption).isEqualTo(PREVIEW_LAYOUT_GALLERY_OPTION)

    manager.setMode(PreviewMode.UiCheck(previewElement))
    assertThat(manager.mode.value.layoutOption).isEqualTo(GRID_LAYOUT_MANAGER_OPTIONS)

    manager.setMode(PreviewMode.UiCheck(previewElement, LIST_LAYOUT_MANAGER_OPTION))
    assertThat(manager.mode.value.layoutOption).isEqualTo(LIST_LAYOUT_MANAGER_OPTION)
  }
}
