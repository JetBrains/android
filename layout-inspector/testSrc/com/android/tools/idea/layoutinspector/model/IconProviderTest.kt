/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.model

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.support.AndroidxName
import com.google.common.truth.Truth.assertThat
import icons.StudioIcons
import org.junit.Test

private val FQCN_CONTENT_FRAME_LAYOUT = AndroidxName("android.support.v7.widget.ContentFrameLayout", "androidx.appcompat.widget.ContentFrameLayout")

class IconProviderTest {

  @Test
  fun testTextViewIcon() {
    assertThat(iconOfView(SdkConstants.FQCN_TEXT_VIEW)).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(iconOfView("androidx.appcompat.widget.AppCompatTextView")).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat(iconOfView("com.google.android.material.textview.MaterialTextView")).isEqualTo(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
  }

  @Test
  fun testViewPagerIcon() {
    assertThat(iconOfView(AndroidXConstants.VIEW_PAGER.oldName())).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_PAGER)
    assertThat(iconOfView(AndroidXConstants.VIEW_PAGER.newName())).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_PAGER)
    assertThat(iconOfView(SdkConstants.VIEW_PAGER2)).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_PAGER)
  }

  @Test
  fun testViewStubIcon() {
    assertThat(iconOfView(SdkConstants.CLASS_VIEWSTUB)).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_STUB)
    assertThat(iconOfView("androidx.appcompat.widget.ViewStubCompat")).isEqualTo(StudioIcons.LayoutEditor.Palette.VIEW_STUB)
  }

  @Test
  fun testLinearLayoutIcon() {
    assertThat(iconOfView(SdkConstants.FQCN_LINEAR_LAYOUT)).isEqualTo(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ)
    assertThat(iconOfView("androidx.appcompat.widget.FitWindowsLinearLayout"))
      .isEqualTo(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ)
    assertThat(iconOfView(AndroidXConstants.CLASS_ACTION_MENU_VIEW.oldName())).isEqualTo(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ)
    assertThat(iconOfView(AndroidXConstants.CLASS_ACTION_MENU_VIEW.newName())).isEqualTo(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ)
    assertThat(iconOfView("com.google.android.material.tabs.SlidingTabIndicator"))
      .isEqualTo(StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ)
  }

  @Test
  fun testRecyclerViewIcon() {
    assertThat(iconOfView(AndroidXConstants.RECYCLER_VIEW.oldName())).isEqualTo(StudioIcons.LayoutEditor.Palette.RECYCLER_VIEW)
    assertThat(iconOfView(AndroidXConstants.RECYCLER_VIEW.newName())).isEqualTo(StudioIcons.LayoutEditor.Palette.RECYCLER_VIEW)
    assertThat(iconOfView("androidx.viewpager2.widget.RecyclerViewImpl")).isEqualTo(StudioIcons.LayoutEditor.Palette.RECYCLER_VIEW)
  }

  @Test
  fun testFrameLayoutIcon() {
    assertThat(iconOfView(SdkConstants.FQCN_FRAME_LAYOUT)).isEqualTo(StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT)
    assertThat(iconOfView(FQCN_CONTENT_FRAME_LAYOUT.oldName())).isEqualTo(StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT)
    assertThat(iconOfView(FQCN_CONTENT_FRAME_LAYOUT.newName())).isEqualTo(StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT)
    assertThat(iconOfView("androidx.fragment.app.FragmentContainerView")).isEqualTo(StudioIcons.LayoutEditor.Palette.FRAME_LAYOUT)
  }

  private fun iconOfView(view: String) = IconProvider.getIconForView(view, false)
}
