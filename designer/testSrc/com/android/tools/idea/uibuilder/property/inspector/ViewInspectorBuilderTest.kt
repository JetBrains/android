/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.AndroidXConstants.BOTTOM_NAVIGATION_VIEW
import com.android.AndroidXConstants.FLOATING_ACTION_BUTTON
import com.android.AndroidXConstants.TAB_LAYOUT
import com.android.AndroidXConstants.TEXT_INPUT_LAYOUT
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ADDITIONAL_PADDING_END_FOR_ICON
import com.android.SdkConstants.ATTR_ADDITIONAL_PADDING_START_FOR_ICON
import com.android.SdkConstants.ATTR_ADJUST_VIEW_BOUNDS
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_BACKGROUND_TINT
import com.android.SdkConstants.ATTR_BACKGROUND_TINT_MODE
import com.android.SdkConstants.ATTR_BORDER_WIDTH
import com.android.SdkConstants.ATTR_BOX_BACKGROUND_COLOR
import com.android.SdkConstants.ATTR_BOX_BACKGROUND_MODE
import com.android.SdkConstants.ATTR_BOX_COLLAPSED_PADDING_TOP
import com.android.SdkConstants.ATTR_BOX_STROKE_COLOR
import com.android.SdkConstants.ATTR_BOX_STROKE_WIDTH
import com.android.SdkConstants.ATTR_CHECKABLE
import com.android.SdkConstants.ATTR_CHECKED_CHIP
import com.android.SdkConstants.ATTR_CHECKED_ICON
import com.android.SdkConstants.ATTR_CHECKED_ICON_VISIBLE
import com.android.SdkConstants.ATTR_CHIP_ICON
import com.android.SdkConstants.ATTR_CHIP_ICON_VISIBLE
import com.android.SdkConstants.ATTR_CHIP_SPACING
import com.android.SdkConstants.ATTR_CHIP_SPACING_HORIZONTAL
import com.android.SdkConstants.ATTR_CHIP_SPACING_VERTICAL
import com.android.SdkConstants.ATTR_CLOSE_ICON
import com.android.SdkConstants.ATTR_CLOSE_ICON_VISIBLE
import com.android.SdkConstants.ATTR_COMPAT_PADDING
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_CORNER_RADIUS
import com.android.SdkConstants.ATTR_COUNTER_ENABLED
import com.android.SdkConstants.ATTR_COUNTER_MAX_LENGTH
import com.android.SdkConstants.ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_COUNTER_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_CROP_TO_PADDING
import com.android.SdkConstants.ATTR_ELEVATION
import com.android.SdkConstants.ATTR_ERROR_ENABLED
import com.android.SdkConstants.ATTR_ERROR_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_FAB_ALIGNMENT_MODE
import com.android.SdkConstants.ATTR_FAB_ANIMATION_MODE
import com.android.SdkConstants.ATTR_FAB_CRADLE_MARGIN
import com.android.SdkConstants.ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS
import com.android.SdkConstants.ATTR_FAB_CRADLE_VERTICAL_OFFSET
import com.android.SdkConstants.ATTR_FAB_CUSTOM_SIZE
import com.android.SdkConstants.ATTR_FAB_SIZE
import com.android.SdkConstants.ATTR_HELPER_TEXT
import com.android.SdkConstants.ATTR_HELPER_TEXT_ENABLED
import com.android.SdkConstants.ATTR_HELPER_TEXT_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_HIDE_MOTION_SPEC
import com.android.SdkConstants.ATTR_HINT
import com.android.SdkConstants.ATTR_HINT_ANIMATION_ENABLED
import com.android.SdkConstants.ATTR_HINT_ENABLED
import com.android.SdkConstants.ATTR_HINT_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_HOVERED_FOCUSED_TRANSLATION_Z
import com.android.SdkConstants.ATTR_ICON
import com.android.SdkConstants.ATTR_ICON_PADDING
import com.android.SdkConstants.ATTR_ICON_TINT
import com.android.SdkConstants.ATTR_ICON_TINT_MODE
import com.android.SdkConstants.ATTR_INSET_BOTTOM
import com.android.SdkConstants.ATTR_INSET_LEFT
import com.android.SdkConstants.ATTR_INSET_RIGHT
import com.android.SdkConstants.ATTR_INSET_TOP
import com.android.SdkConstants.ATTR_ITEM_BACKGROUND
import com.android.SdkConstants.ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED
import com.android.SdkConstants.ATTR_ITEM_ICON_TINT
import com.android.SdkConstants.ATTR_ITEM_TEXT_COLOR
import com.android.SdkConstants.ATTR_LABEL_VISIBILITY_MODE
import com.android.SdkConstants.ATTR_MAX_IMAGE_SIZE
import com.android.SdkConstants.ATTR_MENU
import com.android.SdkConstants.ATTR_ON_CLICK
import com.android.SdkConstants.ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_PASSWORD_TOGGLE_DRAWABLE
import com.android.SdkConstants.ATTR_PASSWORD_TOGGLE_ENABLED
import com.android.SdkConstants.ATTR_PASSWORD_TOGGLE_TINT
import com.android.SdkConstants.ATTR_PASSWORD_TOGGLE_TINT_MODE
import com.android.SdkConstants.ATTR_PRESSED_TRANSLATION_Z
import com.android.SdkConstants.ATTR_RIPPLE_COLOR
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.ATTR_SHOW_MOTION_SPEC
import com.android.SdkConstants.ATTR_SINGLE_LINE
import com.android.SdkConstants.ATTR_SINGLE_SELECTION
import com.android.SdkConstants.ATTR_SRC
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.ATTR_STATE_LIST_ANIMATOR
import com.android.SdkConstants.ATTR_STROKE_COLOR
import com.android.SdkConstants.ATTR_STROKE_WIDTH
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.ATTR_TAB_BACKGROUND
import com.android.SdkConstants.ATTR_TAB_CONTENT_START
import com.android.SdkConstants.ATTR_TAB_GRAVITY
import com.android.SdkConstants.ATTR_TAB_ICON_TINT
import com.android.SdkConstants.ATTR_TAB_ICON_TINT_MODE
import com.android.SdkConstants.ATTR_TAB_INDICATOR
import com.android.SdkConstants.ATTR_TAB_INDICATOR_ANIMATION_DURATION
import com.android.SdkConstants.ATTR_TAB_INDICATOR_COLOR
import com.android.SdkConstants.ATTR_TAB_INDICATOR_FULL_WIDTH
import com.android.SdkConstants.ATTR_TAB_INDICATOR_GRAVITY
import com.android.SdkConstants.ATTR_TAB_INDICATOR_HEIGHT
import com.android.SdkConstants.ATTR_TAB_INLINE_LABEL
import com.android.SdkConstants.ATTR_TAB_MAX_WIDTH
import com.android.SdkConstants.ATTR_TAB_MIN_WIDTH
import com.android.SdkConstants.ATTR_TAB_MODE
import com.android.SdkConstants.ATTR_TAB_PADDING
import com.android.SdkConstants.ATTR_TAB_PADDING_BOTTOM
import com.android.SdkConstants.ATTR_TAB_PADDING_END
import com.android.SdkConstants.ATTR_TAB_PADDING_START
import com.android.SdkConstants.ATTR_TAB_PADDING_TOP
import com.android.SdkConstants.ATTR_TAB_RIPPLE_COLOR
import com.android.SdkConstants.ATTR_TAB_SELECTED_TEXT_COLOR
import com.android.SdkConstants.ATTR_TAB_TEXT_APPEARANCE
import com.android.SdkConstants.ATTR_TAB_TEXT_COLOR
import com.android.SdkConstants.ATTR_TAB_UNBOUNDED_RIPPLE
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_COLOR_HINT
import com.android.SdkConstants.ATTR_THEME
import com.android.SdkConstants.ATTR_TINT
import com.android.SdkConstants.ATTR_VISIBILITY
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.BOTTOM_APP_BAR
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.CHIP
import com.android.SdkConstants.CHIP_GROUP
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.MATERIAL_BUTTON
import com.android.SdkConstants.TOOLS_URI
import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.property.testutils.InspectorTestUtil
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ViewInspectorBuilderTest {

  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath =
      resolveWorkspacePath("tools/adt/idea/designer/testData/property/").toString()
    addManifest(projectRule.fixture)
  }

  @Test
  fun testAllButtonProperties() {
    val util = InspectorTestUtil(projectRule, BUTTON)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    util.checkEditor(3, ANDROID_URI, ATTR_ON_CLICK)
    util.checkEditor(4, ANDROID_URI, ATTR_ELEVATION)
    util.checkEditor(5, ANDROID_URI, ATTR_BACKGROUND)
    util.checkEditor(6, ANDROID_URI, ATTR_BACKGROUND_TINT)
    util.checkEditor(7, ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
    assertThat(util.inspector.lines).hasSize(8)
  }

  @Test
  fun testButtonWithSomeMissingProperties() {
    val util = InspectorTestUtil(projectRule, BUTTON)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    util.removeProperty(ANDROID_URI, ATTR_BACKGROUND)
    util.removeProperty(ANDROID_URI, ATTR_BACKGROUND_TINT)
    util.removeProperty(ANDROID_URI, ATTR_ON_CLICK)
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    util.checkEditor(3, ANDROID_URI, ATTR_ELEVATION)
    util.checkEditor(4, ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
    assertThat(util.inspector.lines).hasSize(5)
  }

  @Test
  fun testImageViewWithAppCompatProperties() {
    val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addImageViewProperties(util, true)
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, AUTO_URI, ATTR_SRC_COMPAT)
    util.checkEditor(2, TOOLS_URI, ATTR_SRC_COMPAT)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_BACKGROUND)
    util.checkEditor(5, ANDROID_URI, ATTR_SCALE_TYPE)
    util.checkEditor(6, ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS)
    util.checkEditor(7, ANDROID_URI, ATTR_CROP_TO_PADDING)
    assertThat(util.inspector.lines).hasSize(8)
  }

  @Test
  fun testImageViewWithoutAppCompatProperties() {
    val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    addImageViewProperties(util, false)
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_SRC)
    util.checkEditor(2, TOOLS_URI, ATTR_SRC)
    util.checkEditor(3, ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    util.checkEditor(4, ANDROID_URI, ATTR_BACKGROUND)
    util.checkEditor(5, ANDROID_URI, ATTR_SCALE_TYPE)
    util.checkEditor(6, ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS)
    util.checkEditor(7, ANDROID_URI, ATTR_CROP_TO_PADDING)
    assertThat(util.inspector.lines).hasSize(8)
  }

  @Test
  fun testBottomAppBar() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "BottomAppBar.java",
      "src/java/com/google/android/material/bottomappbar/BottomAppBar.java"
    )
    val util = InspectorTestUtil(projectRule, BOTTOM_APP_BAR, parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_BACKGROUND_TINT)
    util.checkEditor(3, AUTO_URI, ATTR_FAB_ALIGNMENT_MODE)
    util.checkEditor(4, AUTO_URI, ATTR_FAB_ANIMATION_MODE)
    util.checkEditor(5, AUTO_URI, ATTR_FAB_CRADLE_MARGIN)
    util.checkEditor(6, AUTO_URI, ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS)
    util.checkEditor(7, AUTO_URI, ATTR_FAB_CRADLE_VERTICAL_OFFSET)
    assertThat(util.inspector.lines).hasSize(8)
  }

  @Test
  fun testMaterialButton() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "AppCompatButton.java",
      "src/java/android/support/v7/widget/MaterialButton.java"
    )
    projectRule.fixture.copyFileToProject(
      "MaterialButton.java",
      "src/java/com/google/android/material/button/MaterialButton.java"
    )
    val util = InspectorTestUtil(projectRule, MATERIAL_BUTTON, parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    assertThat(util.inspector.lines).hasSize(22)
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    util.checkEditor(3, ANDROID_URI, ATTR_ON_CLICK)
    util.checkEditor(4, ANDROID_URI, ATTR_ELEVATION)
    util.checkEditor(5, ANDROID_URI, ATTR_INSET_LEFT)
    util.checkEditor(6, ANDROID_URI, ATTR_INSET_RIGHT)
    util.checkEditor(7, ANDROID_URI, ATTR_INSET_TOP)
    util.checkEditor(8, ANDROID_URI, ATTR_INSET_BOTTOM)
    util.checkEditor(9, ANDROID_URI, ATTR_BACKGROUND)
    util.checkEditor(10, AUTO_URI, ATTR_BACKGROUND_TINT)
    util.checkEditor(11, AUTO_URI, ATTR_BACKGROUND_TINT_MODE)
    util.checkEditor(12, AUTO_URI, ATTR_ICON)
    util.checkEditor(13, AUTO_URI, ATTR_ICON_PADDING)
    util.checkEditor(14, AUTO_URI, ATTR_ICON_TINT)
    util.checkEditor(15, AUTO_URI, ATTR_ICON_TINT_MODE)
    util.checkEditor(16, AUTO_URI, ATTR_ADDITIONAL_PADDING_START_FOR_ICON)
    util.checkEditor(17, AUTO_URI, ATTR_ADDITIONAL_PADDING_END_FOR_ICON)
    util.checkEditor(18, AUTO_URI, ATTR_STROKE_COLOR)
    util.checkEditor(19, AUTO_URI, ATTR_STROKE_WIDTH)
    util.checkEditor(20, AUTO_URI, ATTR_CORNER_RADIUS)
    util.checkEditor(21, AUTO_URI, ATTR_RIPPLE_COLOR)
  }

  @Test
  fun testChipGroup() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "ChipGroup.java",
      "src/java/com/google/android/material/chip/ChipGroup.java"
    )
    val util = InspectorTestUtil(projectRule, CHIP_GROUP, parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_CHIP_SPACING)
    util.checkEditor(3, AUTO_URI, ATTR_CHIP_SPACING_HORIZONTAL)
    util.checkEditor(4, AUTO_URI, ATTR_CHIP_SPACING_VERTICAL)
    util.checkEditor(5, AUTO_URI, ATTR_SINGLE_LINE)
    util.checkEditor(6, AUTO_URI, ATTR_SINGLE_SELECTION)
    util.checkEditor(7, AUTO_URI, ATTR_CHECKED_CHIP)
    assertThat(util.inspector.lines).hasSize(8)
  }

  @Test
  fun testChip() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "AppCompatCheckBox.java",
      "src/java/android/support/v7/widget/AppCompatCheckBox.java"
    )
    projectRule.fixture.copyFileToProject(
      "Chip.java",
      "src/java/com/google/android/material/chip/Chip.java"
    )
    projectRule.fixture.copyFileToProject(
      "ChipGroup.java",
      "src/java/com/google/android/material/chip/ChipGroup.java"
    )
    val util = InspectorTestUtil(projectRule, CHIP, parentTag = CHIP_GROUP)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, ANDROID_URI, ATTR_CHECKABLE)
    util.checkEditor(3, ANDROID_URI, ATTR_TEXT)
    util.checkEditor(4, AUTO_URI, ATTR_CHIP_ICON)
    util.checkEditor(5, AUTO_URI, ATTR_CHIP_ICON_VISIBLE)
    util.checkEditor(6, AUTO_URI, ATTR_CHECKED_ICON)
    util.checkEditor(7, AUTO_URI, ATTR_CHECKED_ICON_VISIBLE)
    util.checkEditor(8, AUTO_URI, ATTR_CLOSE_ICON)
    util.checkEditor(9, AUTO_URI, ATTR_CLOSE_ICON_VISIBLE)
    assertThat(util.inspector.lines).hasSize(10)
  }

  @Test
  fun testBottomNavigationView() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "BottomNavigationView.java",
      "src/java/android/support/design/widget/BottomNavigationView.java"
    )
    val util =
      InspectorTestUtil(projectRule, BOTTOM_NAVIGATION_VIEW.oldName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED)
    util.checkEditor(3, AUTO_URI, ATTR_LABEL_VISIBILITY_MODE)
    util.checkEditor(4, AUTO_URI, ATTR_ITEM_ICON_TINT)
    util.checkEditor(5, AUTO_URI, ATTR_MENU)
    util.checkEditor(6, AUTO_URI, ATTR_ITEM_BACKGROUND)
    util.checkEditor(7, AUTO_URI, ATTR_ITEM_TEXT_COLOR)
    util.checkEditor(8, AUTO_URI, ATTR_ELEVATION)
    assertThat(util.inspector.lines).hasSize(9)
  }

  @Test
  fun testBottomNavigationViewX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "BottomNavigationViewX.java",
      "src/java/com/google/android/material/bottomnavigation/BottomNavigationView.java"
    )
    val util =
      InspectorTestUtil(projectRule, BOTTOM_NAVIGATION_VIEW.newName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED)
    util.checkEditor(3, AUTO_URI, ATTR_LABEL_VISIBILITY_MODE)
    util.checkEditor(4, AUTO_URI, ATTR_ITEM_ICON_TINT)
    util.checkEditor(5, AUTO_URI, ATTR_MENU)
    util.checkEditor(6, AUTO_URI, ATTR_ITEM_BACKGROUND)
    util.checkEditor(7, AUTO_URI, ATTR_ITEM_TEXT_COLOR)
    util.checkEditor(8, AUTO_URI, ATTR_ELEVATION)
    assertThat(util.inspector.lines).hasSize(9)
  }

  @Test
  fun testFloatingActionButton() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "FloatingActionButton.java",
      "src/java/android/support/design/floatingactionbutton/FloatingActionButton.java"
    )
    val util =
      InspectorTestUtil(projectRule, FLOATING_ACTION_BUTTON.oldName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_SRC)
    util.checkEditor(2, "", ATTR_STYLE)
    util.checkEditor(3, AUTO_URI, ATTR_BACKGROUND_TINT)
    util.checkEditor(4, AUTO_URI, ATTR_BACKGROUND_TINT_MODE)
    util.checkEditor(5, AUTO_URI, ATTR_RIPPLE_COLOR)
    util.checkEditor(6, ANDROID_URI, ATTR_TINT)
    util.checkEditor(7, AUTO_URI, ATTR_FAB_SIZE)
    util.checkEditor(8, AUTO_URI, ATTR_FAB_CUSTOM_SIZE)
    util.checkEditor(9, AUTO_URI, ATTR_ELEVATION)
    util.checkEditor(10, AUTO_URI, ATTR_HOVERED_FOCUSED_TRANSLATION_Z)
    util.checkEditor(11, AUTO_URI, ATTR_PRESSED_TRANSLATION_Z)
    util.checkEditor(12, AUTO_URI, ATTR_BORDER_WIDTH)
    util.checkEditor(13, AUTO_URI, ATTR_COMPAT_PADDING)
    util.checkEditor(14, AUTO_URI, ATTR_MAX_IMAGE_SIZE)
    util.checkEditor(15, AUTO_URI, ATTR_SHOW_MOTION_SPEC)
    util.checkEditor(16, AUTO_URI, ATTR_HIDE_MOTION_SPEC)
    assertThat(util.inspector.lines).hasSize(17)
  }

  @Test
  fun testFloatingActionButtonX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "FloatingActionButtonX.java",
      "src/java/com/google/android/material/floatingactionbutton/FloatingActionButton.java"
    )
    val util =
      InspectorTestUtil(projectRule, FLOATING_ACTION_BUTTON.newName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_SRC)
    util.checkEditor(2, "", ATTR_STYLE)
    util.checkEditor(3, AUTO_URI, ATTR_BACKGROUND_TINT)
    util.checkEditor(4, AUTO_URI, ATTR_BACKGROUND_TINT_MODE)
    util.checkEditor(5, AUTO_URI, ATTR_RIPPLE_COLOR)
    util.checkEditor(6, ANDROID_URI, ATTR_TINT)
    util.checkEditor(7, AUTO_URI, ATTR_FAB_SIZE)
    util.checkEditor(8, AUTO_URI, ATTR_FAB_CUSTOM_SIZE)
    util.checkEditor(9, AUTO_URI, ATTR_ELEVATION)
    util.checkEditor(10, AUTO_URI, ATTR_HOVERED_FOCUSED_TRANSLATION_Z)
    util.checkEditor(11, AUTO_URI, ATTR_PRESSED_TRANSLATION_Z)
    util.checkEditor(12, AUTO_URI, ATTR_BORDER_WIDTH)
    util.checkEditor(13, AUTO_URI, ATTR_COMPAT_PADDING)
    util.checkEditor(14, AUTO_URI, ATTR_MAX_IMAGE_SIZE)
    util.checkEditor(15, AUTO_URI, ATTR_SHOW_MOTION_SPEC)
    util.checkEditor(16, AUTO_URI, ATTR_HIDE_MOTION_SPEC)
    assertThat(util.inspector.lines).hasSize(17)
  }

  @Test
  fun testTabLayout() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "TabLayout.java",
      "src/java/android/support/design/TabLayout.java"
    )
    val util = InspectorTestUtil(projectRule, TAB_LAYOUT.oldName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_TAB_INDICATOR_COLOR)
    util.checkEditor(3, AUTO_URI, ATTR_TAB_INDICATOR_HEIGHT)
    util.checkEditor(4, AUTO_URI, ATTR_TAB_CONTENT_START)
    util.checkEditor(5, AUTO_URI, ATTR_TAB_BACKGROUND)
    util.checkEditor(6, AUTO_URI, ATTR_TAB_INDICATOR)
    util.checkEditor(7, AUTO_URI, ATTR_TAB_INDICATOR_GRAVITY)
    util.checkEditor(8, AUTO_URI, ATTR_TAB_INDICATOR_ANIMATION_DURATION)
    util.checkEditor(9, AUTO_URI, ATTR_TAB_INDICATOR_FULL_WIDTH)
    util.checkEditor(10, AUTO_URI, ATTR_TAB_MODE)
    util.checkEditor(11, AUTO_URI, ATTR_TAB_GRAVITY)
    util.checkEditor(12, AUTO_URI, ATTR_TAB_INLINE_LABEL)
    util.checkEditor(13, AUTO_URI, ATTR_TAB_MIN_WIDTH)
    util.checkEditor(14, AUTO_URI, ATTR_TAB_MAX_WIDTH)
    util.checkEditor(15, AUTO_URI, ATTR_TAB_TEXT_APPEARANCE)
    util.checkEditor(16, AUTO_URI, ATTR_TAB_TEXT_COLOR)
    util.checkEditor(17, AUTO_URI, ATTR_TAB_SELECTED_TEXT_COLOR)
    util.checkEditor(18, AUTO_URI, ATTR_TAB_PADDING)
    util.checkEditor(19, AUTO_URI, ATTR_TAB_PADDING_START)
    util.checkEditor(20, AUTO_URI, ATTR_TAB_PADDING_END)
    util.checkEditor(21, AUTO_URI, ATTR_TAB_PADDING_TOP)
    util.checkEditor(22, AUTO_URI, ATTR_TAB_PADDING_BOTTOM)
    util.checkEditor(23, AUTO_URI, ATTR_TAB_ICON_TINT)
    util.checkEditor(24, AUTO_URI, ATTR_TAB_ICON_TINT_MODE)
    util.checkEditor(25, AUTO_URI, ATTR_TAB_RIPPLE_COLOR)
    util.checkEditor(26, AUTO_URI, ATTR_TAB_UNBOUNDED_RIPPLE)
    util.checkEditor(27, ANDROID_URI, ATTR_THEME)
    util.checkEditor(28, ANDROID_URI, ATTR_BACKGROUND)
    assertThat(util.inspector.lines).hasSize(29)
  }

  @Test
  fun testTabLayoutX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "TabLayoutX.java",
      "src/java/com/google/android/material/tabs/TabLayout.java"
    )
    val util = InspectorTestUtil(projectRule, TAB_LAYOUT.newName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, "", ATTR_STYLE)
    util.checkEditor(2, AUTO_URI, ATTR_TAB_INDICATOR_COLOR)
    util.checkEditor(3, AUTO_URI, ATTR_TAB_INDICATOR_HEIGHT)
    util.checkEditor(4, AUTO_URI, ATTR_TAB_CONTENT_START)
    util.checkEditor(5, AUTO_URI, ATTR_TAB_BACKGROUND)
    util.checkEditor(6, AUTO_URI, ATTR_TAB_INDICATOR)
    util.checkEditor(7, AUTO_URI, ATTR_TAB_INDICATOR_GRAVITY)
    util.checkEditor(8, AUTO_URI, ATTR_TAB_INDICATOR_ANIMATION_DURATION)
    util.checkEditor(9, AUTO_URI, ATTR_TAB_INDICATOR_FULL_WIDTH)
    util.checkEditor(10, AUTO_URI, ATTR_TAB_MODE)
    util.checkEditor(11, AUTO_URI, ATTR_TAB_GRAVITY)
    util.checkEditor(12, AUTO_URI, ATTR_TAB_INLINE_LABEL)
    util.checkEditor(13, AUTO_URI, ATTR_TAB_MIN_WIDTH)
    util.checkEditor(14, AUTO_URI, ATTR_TAB_MAX_WIDTH)
    util.checkEditor(15, AUTO_URI, ATTR_TAB_TEXT_APPEARANCE)
    util.checkEditor(16, AUTO_URI, ATTR_TAB_TEXT_COLOR)
    util.checkEditor(17, AUTO_URI, ATTR_TAB_SELECTED_TEXT_COLOR)
    util.checkEditor(18, AUTO_URI, ATTR_TAB_PADDING)
    util.checkEditor(19, AUTO_URI, ATTR_TAB_PADDING_START)
    util.checkEditor(20, AUTO_URI, ATTR_TAB_PADDING_END)
    util.checkEditor(21, AUTO_URI, ATTR_TAB_PADDING_TOP)
    util.checkEditor(22, AUTO_URI, ATTR_TAB_PADDING_BOTTOM)
    util.checkEditor(23, AUTO_URI, ATTR_TAB_ICON_TINT)
    util.checkEditor(24, AUTO_URI, ATTR_TAB_ICON_TINT_MODE)
    util.checkEditor(25, AUTO_URI, ATTR_TAB_RIPPLE_COLOR)
    util.checkEditor(26, AUTO_URI, ATTR_TAB_UNBOUNDED_RIPPLE)
    util.checkEditor(27, ANDROID_URI, ATTR_THEME)
    util.checkEditor(28, ANDROID_URI, ATTR_BACKGROUND)
    assertThat(util.inspector.lines).hasSize(29)
  }

  @Test
  fun testTextInputLayout() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "TextInputLayout.java",
      "src/java/android/support/design/text/TextInputLayout.java"
    )
    val util =
      InspectorTestUtil(projectRule, TEXT_INPUT_LAYOUT.oldName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT_COLOR_HINT)
    util.checkEditor(2, ANDROID_URI, ATTR_HINT)
    util.checkEditor(3, AUTO_URI, ATTR_HINT_ENABLED)
    util.checkEditor(4, AUTO_URI, ATTR_HINT_ANIMATION_ENABLED)
    util.checkEditor(5, AUTO_URI, ATTR_HINT_TEXT_APPEARANCE)
    util.checkEditor(6, AUTO_URI, ATTR_HELPER_TEXT)
    util.checkEditor(7, AUTO_URI, ATTR_HELPER_TEXT_ENABLED)
    util.checkEditor(8, AUTO_URI, ATTR_HELPER_TEXT_TEXT_APPEARANCE)
    util.checkEditor(9, AUTO_URI, ATTR_ERROR_ENABLED)
    util.checkEditor(10, AUTO_URI, ATTR_ERROR_TEXT_APPEARANCE)
    util.checkEditor(11, AUTO_URI, ATTR_COUNTER_ENABLED)
    util.checkEditor(12, AUTO_URI, ATTR_COUNTER_MAX_LENGTH)
    util.checkEditor(13, AUTO_URI, ATTR_COUNTER_TEXT_APPEARANCE)
    util.checkEditor(14, AUTO_URI, ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE)
    util.checkEditor(15, AUTO_URI, ATTR_PASSWORD_TOGGLE_ENABLED)
    util.checkEditor(16, AUTO_URI, ATTR_PASSWORD_TOGGLE_DRAWABLE)
    util.checkEditor(17, AUTO_URI, ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION)
    util.checkEditor(18, AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT)
    util.checkEditor(19, AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT_MODE)
    util.checkEditor(20, AUTO_URI, ATTR_BOX_BACKGROUND_MODE)
    util.checkEditor(21, AUTO_URI, ATTR_BOX_COLLAPSED_PADDING_TOP)
    util.checkEditor(22, AUTO_URI, ATTR_BOX_STROKE_COLOR)
    util.checkEditor(23, AUTO_URI, ATTR_BOX_BACKGROUND_COLOR)
    util.checkEditor(24, AUTO_URI, ATTR_BOX_STROKE_WIDTH)
    assertThat(util.inspector.lines).hasSize(25)
  }

  @Test
  fun testTextInputLayoutX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject(
      "TextInputLayoutX.java",
      "src/java/com/google/android/material/textfield/TextInputLayout.java"
    )
    val util =
      InspectorTestUtil(projectRule, TEXT_INPUT_LAYOUT.newName(), parentTag = LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    val generator = CommonAttributesInspectorBuilder.TitleGenerator(util.inspector)
    util.loadProperties()
    runReadAction { builder.attachToInspector(util.inspector, util.properties) { generator.title } }
    util.checkTitle(0, InspectorSection.COMMON.title)
    util.checkEditor(1, ANDROID_URI, ATTR_TEXT_COLOR_HINT)
    util.checkEditor(2, ANDROID_URI, ATTR_HINT)
    util.checkEditor(3, AUTO_URI, ATTR_HINT_ENABLED)
    util.checkEditor(4, AUTO_URI, ATTR_HINT_ANIMATION_ENABLED)
    util.checkEditor(5, AUTO_URI, ATTR_HINT_TEXT_APPEARANCE)
    util.checkEditor(6, AUTO_URI, ATTR_HELPER_TEXT)
    util.checkEditor(7, AUTO_URI, ATTR_HELPER_TEXT_ENABLED)
    util.checkEditor(8, AUTO_URI, ATTR_HELPER_TEXT_TEXT_APPEARANCE)
    util.checkEditor(9, AUTO_URI, ATTR_ERROR_ENABLED)
    util.checkEditor(10, AUTO_URI, ATTR_ERROR_TEXT_APPEARANCE)
    util.checkEditor(11, AUTO_URI, ATTR_COUNTER_ENABLED)
    util.checkEditor(12, AUTO_URI, ATTR_COUNTER_MAX_LENGTH)
    util.checkEditor(13, AUTO_URI, ATTR_COUNTER_TEXT_APPEARANCE)
    util.checkEditor(14, AUTO_URI, ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE)
    util.checkEditor(15, AUTO_URI, ATTR_PASSWORD_TOGGLE_ENABLED)
    util.checkEditor(16, AUTO_URI, ATTR_PASSWORD_TOGGLE_DRAWABLE)
    util.checkEditor(17, AUTO_URI, ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION)
    util.checkEditor(18, AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT)
    util.checkEditor(19, AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT_MODE)
    util.checkEditor(20, AUTO_URI, ATTR_BOX_BACKGROUND_MODE)
    util.checkEditor(21, AUTO_URI, ATTR_BOX_COLLAPSED_PADDING_TOP)
    util.checkEditor(22, AUTO_URI, ATTR_BOX_STROKE_COLOR)
    util.checkEditor(23, AUTO_URI, ATTR_BOX_BACKGROUND_COLOR)
    util.checkEditor(24, AUTO_URI, ATTR_BOX_STROKE_WIDTH)
    assertThat(util.inspector.lines).hasSize(25)
  }

  private fun addImageViewProperties(util: InspectorTestUtil, withAppCompat: Boolean) {
    if (withAppCompat) {
      util.addProperty(AUTO_URI, ATTR_SRC_COMPAT, NlPropertyType.DRAWABLE)
    } else {
      util.addProperty(ANDROID_URI, ATTR_SRC, NlPropertyType.DRAWABLE)
    }
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NlPropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND, NlPropertyType.DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_SCALE_TYPE, NlPropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS, NlPropertyType.THREE_STATE_BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_CROP_TO_PADDING, NlPropertyType.THREE_STATE_BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_VISIBILITY, NlPropertyType.ENUM)
  }
}
