package com.android.tools.idea.preview.modes

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.ApplicationServiceRule
import com.android.tools.preview.PreviewElement
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RuleChain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class CommonPreviewModeManagerTest {

  private val projectRule = AndroidProjectRule.inMemory()
  private val androidEditorSettings = AndroidEditorSettings()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      ApplicationServiceRule(AndroidEditorSettings::class.java, androidEditorSettings),
    )

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
    val previewElement = mock<PreviewElement<Unit>>()

    manager.setMode(PreviewMode.Interactive(previewElement))

    assertThat(manager.mode.value).isInstanceOf(PreviewMode.Interactive::class.java)

    manager.restorePrevious()
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default())

    manager.setMode(PreviewMode.Default(GRID_LAYOUT_OPTION))
    manager.setMode(
      PreviewMode.UiCheck(
        UiCheckInstance(previewElement, isWearPreview = false),
        GRID_NO_GROUP_LAYOUT_OPTION,
      )
    )
    manager.setMode(
      PreviewMode.UiCheck(
        UiCheckInstance(previewElement, isWearPreview = false),
        LIST_NO_GROUP_LAYOUT_OPTION,
      )
    )
    manager.restorePrevious()
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default(GRID_LAYOUT_OPTION))
  }

  @Test
  fun testChangeModeLayout(): Unit = runBlocking {
    val manager = CommonPreviewModeManager()
    val previewElement = mock<PreviewElement<Unit>>()

    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default())

    manager.setMode(PreviewMode.Gallery(previewElement))
    assertThat(manager.mode.value.layoutOption).isEqualTo(GALLERY_LAYOUT_OPTION)

    manager.setMode(PreviewMode.UiCheck(UiCheckInstance(previewElement, isWearPreview = false)))
    assertThat(manager.mode.value.layoutOption).isEqualTo(GRID_NO_GROUP_LAYOUT_OPTION)

    manager.setMode(PreviewMode.AnimationInspection(previewElement))
    assertThat(manager.mode.value.layoutOption).isEqualTo(GRID_NO_GROUP_LAYOUT_OPTION)

    manager.setMode(
      PreviewMode.UiCheck(
        UiCheckInstance(previewElement, isWearPreview = false),
        LIST_NO_GROUP_LAYOUT_OPTION,
      )
    )
    assertThat(manager.mode.value.layoutOption).isEqualTo(LIST_NO_GROUP_LAYOUT_OPTION)

    manager.setMode(PreviewMode.Interactive(previewElement))
    assertThat(manager.mode.value.layoutOption).isEqualTo(GRID_NO_GROUP_LAYOUT_OPTION)
  }

  @Test
  fun testNoDefaultLayoutModePreference_GridIsDefault(): Unit = runBlocking {
    // No preferences set
    val manager = CommonPreviewModeManager()

    // The default value is a default view mode with a grid layout option
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default())
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default(GRID_LAYOUT_OPTION))
  }

  @Test
  fun testGridLayoutModeDefaultPreference_GridIsDefault(): Unit = runBlocking {
    // Grid is set as a preference
    androidEditorSettings.globalState.preferredPreviewLayoutMode =
      AndroidEditorSettings.LayoutType.GRID

    val manager = CommonPreviewModeManager()

    // The default value is a default view mode with a grid layout option
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default(GRID_LAYOUT_OPTION))
  }

  @Test
  fun testGalleryLayoutModeDefaultPreference_GalleryIsDefault(): Unit = runBlocking {
    // Gallery is set as a preference
    androidEditorSettings.globalState.preferredPreviewLayoutMode =
      AndroidEditorSettings.LayoutType.GALLERY

    val manager = CommonPreviewModeManager()

    // The default value is a gallery view mode, no option set (null)
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Gallery(null))
  }

  @Test
  fun testListLayoutModeDefaultPreference_ListIsDefault(): Unit = runBlocking {
    // List is set as a preference
    androidEditorSettings.globalState.preferredPreviewLayoutMode =
      AndroidEditorSettings.LayoutType.LIST

    val manager = CommonPreviewModeManager()

    // The default value is a default view mode with a list layout option
    assertThat(manager.mode.value).isEqualTo(PreviewMode.Default(LIST_LAYOUT_OPTION))
  }
}
