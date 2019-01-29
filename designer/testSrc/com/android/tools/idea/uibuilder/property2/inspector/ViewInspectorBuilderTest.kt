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
package com.android.tools.idea.uibuilder.property2.inspector

import com.android.SdkConstants.*
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.addManifest
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import com.android.tools.idea.uibuilder.property2.testutils.FakeInspectorLine
import com.android.tools.idea.uibuilder.property2.testutils.InspectorTestUtil
import com.android.tools.idea.uibuilder.property2.testutils.LineType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ViewInspectorBuilderTest {
  @JvmField @Rule
  val edtRule = EdtRule()

  @JvmField @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = PathManager.getHomePath() + "/../adt/idea/designer/testData/property/"
    addManifest(projectRule.fixture)

    // Hack to make the namespace of the library attributes AUTO_URI. Fix this in a later CL.
    AndroidFacet.getInstance(projectRule.module)?.properties?.ALLOW_USER_CONFIGURATION = false
  }

  @Test
  fun testAllButtonProperties() {
    val util = InspectorTestUtil(projectRule, BUTTON)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    checkTitle(util.inspector.lines[0], "Button")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_ON_CLICK)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_ELEVATION)
    checkProperty(util.inspector.lines[5], ANDROID_URI, ATTR_BACKGROUND)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_BACKGROUND_TINT)
    checkProperty(util.inspector.lines[7], ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
  }

  @Test
  fun testButtonWithSomeMissingProperties() {
    val util = InspectorTestUtil(projectRule, BUTTON)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    util.removeProperty(ANDROID_URI, ATTR_BACKGROUND)
    util.removeProperty(ANDROID_URI, ATTR_BACKGROUND_TINT)
    util.removeProperty(ANDROID_URI, ATTR_ON_CLICK)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(5)
    checkTitle(util.inspector.lines[0], "Button")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_ELEVATION)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
  }

  @Test
  fun testImageViewWithAppCompatProperties() {
    val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    addImageViewProperties(util, true)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    checkTitle(util.inspector.lines[0], "ImageView")
    checkProperty(util.inspector.lines[1], AUTO_URI, ATTR_SRC_COMPAT)
    checkProperty(util.inspector.lines[2], TOOLS_URI, ATTR_SRC_COMPAT)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_BACKGROUND)
    checkProperty(util.inspector.lines[5], ANDROID_URI, ATTR_SCALE_TYPE)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS)
    checkProperty(util.inspector.lines[7], ANDROID_URI, ATTR_CROP_TO_PADDING)
  }

  @Test
  fun testImageViewWithoutAppCompatProperties() {
    val util = InspectorTestUtil(projectRule, IMAGE_VIEW)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    addImageViewProperties(util, false)
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    checkTitle(util.inspector.lines[0], "ImageView")
    checkProperty(util.inspector.lines[1], ANDROID_URI, ATTR_SRC)
    checkProperty(util.inspector.lines[2], TOOLS_URI, ATTR_SRC)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_BACKGROUND)
    checkProperty(util.inspector.lines[5], ANDROID_URI, ATTR_SCALE_TYPE)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS)
    checkProperty(util.inspector.lines[7], ANDROID_URI, ATTR_CROP_TO_PADDING)
  }

  @Test
  fun testBottomAppBar() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("BottomAppBar.java", "src/java/com/google/android/material/bottomappbar/BottomAppBar.java")
    val util = InspectorTestUtil(projectRule, BOTTOM_APP_BAR, LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    checkTitle(util.inspector.lines[0], "BottomAppBar")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_BACKGROUND_TINT)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_FAB_ALIGNMENT_MODE)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_FAB_ANIMATION_MODE)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_FAB_CRADLE_MARGIN)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_FAB_CRADLE_ROUNDED_CORNER_RADIUS)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_FAB_CRADLE_VERTICAL_OFFSET)
  }

  @Test
  fun testMaterialButton() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("AppCompatButton.java", "src/java/android/support/v7/widget/MaterialButton.java")
    projectRule.fixture.copyFileToProject("MaterialButton.java", "src/java/com/google/android/material/button/MaterialButton.java")
    val util = InspectorTestUtil(projectRule, MATERIAL_BUTTON, LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(22)
    checkTitle(util.inspector.lines[0], "MaterialButton")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_STATE_LIST_ANIMATOR)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_ON_CLICK)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_ELEVATION)
    checkProperty(util.inspector.lines[5], ANDROID_URI, ATTR_INSET_LEFT)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_INSET_RIGHT)
    checkProperty(util.inspector.lines[7], ANDROID_URI, ATTR_INSET_TOP)
    checkProperty(util.inspector.lines[8], ANDROID_URI, ATTR_INSET_BOTTOM)
    checkProperty(util.inspector.lines[9], ANDROID_URI, ATTR_BACKGROUND)
    checkProperty(util.inspector.lines[10], ANDROID_URI, ATTR_BACKGROUND_TINT)
    checkProperty(util.inspector.lines[11], ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_ICON)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_ICON_PADDING)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_ICON_TINT)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_ICON_TINT_MODE)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_ADDITIONAL_PADDING_START_FOR_ICON)
    checkProperty(util.inspector.lines[17], AUTO_URI, ATTR_ADDITIONAL_PADDING_END_FOR_ICON)
    checkProperty(util.inspector.lines[18], AUTO_URI, ATTR_STROKE_COLOR)
    checkProperty(util.inspector.lines[19], AUTO_URI, ATTR_STROKE_WIDTH)
    checkProperty(util.inspector.lines[20], AUTO_URI, ATTR_CORNER_RADIUS)
    checkProperty(util.inspector.lines[21], AUTO_URI, ATTR_RIPPLE_COLOR)
  }

  @Test
  fun testChipGroup() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("ChipGroup.java", "src/java/com/google/android/material/chip/ChipGroup.java")
    val util = InspectorTestUtil(projectRule, CHIP_GROUP, LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(8)
    checkTitle(util.inspector.lines[0], "ChipGroup")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], AUTO_URI, ATTR_CHIP_SPACING)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_CHIP_SPACING_HORIZONTAL)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_CHIP_SPACING_VERTICAL)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_SINGLE_LINE)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_SINGLE_SELECTION)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_CHECKED_CHIP)
  }

  @Test
  fun testChip() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("AppCompatCheckBox.java", "src/java/android/support/v7/widget/AppCompatCheckBox.java")
    projectRule.fixture.copyFileToProject("Chip.java", "src/java/com/google/android/material/chip/Chip.java")
    val util = InspectorTestUtil(projectRule, CHIP, CHIP_GROUP)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(10)
    checkTitle(util.inspector.lines[0], "Chip")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_CHECKABLE)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_TEXT)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_CHIP_ICON)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_CHIP_ICON_VISIBLE)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_CHECKED_ICON)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_CHECKED_ICON_VISIBLE)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_CLOSE_ICON)
    checkProperty(util.inspector.lines[9], AUTO_URI, ATTR_CLOSE_ICON_VISIBLE)
  }

  @Test
  fun testBottomNavigationView() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("BottomNavigationView.java", "src/java/android/support/design/widget/BottomNavigationView.java")
    val util = InspectorTestUtil(projectRule, BOTTOM_NAVIGATION_VIEW.oldName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(9)
    checkTitle(util.inspector.lines[0], "BottomNavigationView")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], AUTO_URI, ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_LABEL_VISIBILITY_MODE)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_ITEM_ICON_TINT)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_MENU)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_ITEM_BACKGROUND)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_ITEM_TEXT_COLOR)
    checkProperty(util.inspector.lines[8], ANDROID_URI, ATTR_ELEVATION)
  }

  @Test
  fun testBottomNavigationViewX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("BottomNavigationViewX.java",
                                          "src/java/com/google/android/material/bottomnavigation/BottomNavigationView.java")
    val util = InspectorTestUtil(projectRule, BOTTOM_NAVIGATION_VIEW.newName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(9)
    checkTitle(util.inspector.lines[0], "BottomNavigationView")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], AUTO_URI, ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_LABEL_VISIBILITY_MODE)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_ITEM_ICON_TINT)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_MENU)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_ITEM_BACKGROUND)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_ITEM_TEXT_COLOR)
    checkProperty(util.inspector.lines[8], ANDROID_URI, ATTR_ELEVATION)
  }

  @Test
  fun testFloatingActionButton() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("FloatingActionButton.java",
                                          "src/java/android/support/design/floatingactionbutton/FloatingActionButton.java")
    val util = InspectorTestUtil(projectRule, FLOATING_ACTION_BUTTON.oldName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(17)
    checkTitle(util.inspector.lines[0], "FloatingActionButton")
    checkProperty(util.inspector.lines[1], ANDROID_URI, ATTR_SRC)
    checkProperty(util.inspector.lines[2], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_BACKGROUND_TINT)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_RIPPLE_COLOR)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_TINT)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_FAB_SIZE)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_FAB_CUSTOM_SIZE)
    checkProperty(util.inspector.lines[9], ANDROID_URI, ATTR_ELEVATION)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_HOVERED_FOCUSED_TRANSLATION_Z)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_PRESSED_TRANSLATION_Z)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_BORDER_WIDTH)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_COMPAT_PADDING)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_MAX_IMAGE_SIZE)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_SHOW_MOTION_SPEC)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_HIDE_MOTION_SPEC)
  }

  @Test
  fun testFloatingActionButtonX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("FloatingActionButtonX.java",
                                          "src/java/com/google/android/material/floatingactionbutton/FloatingActionButton.java")
    val util = InspectorTestUtil(projectRule, FLOATING_ACTION_BUTTON.newName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(17)
    checkTitle(util.inspector.lines[0], "FloatingActionButton")
    checkProperty(util.inspector.lines[1], ANDROID_URI, ATTR_SRC)
    checkProperty(util.inspector.lines[2], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[3], ANDROID_URI, ATTR_BACKGROUND_TINT)
    checkProperty(util.inspector.lines[4], ANDROID_URI, ATTR_BACKGROUND_TINT_MODE)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_RIPPLE_COLOR)
    checkProperty(util.inspector.lines[6], ANDROID_URI, ATTR_TINT)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_FAB_SIZE)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_FAB_CUSTOM_SIZE)
    checkProperty(util.inspector.lines[9], ANDROID_URI, ATTR_ELEVATION)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_HOVERED_FOCUSED_TRANSLATION_Z)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_PRESSED_TRANSLATION_Z)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_BORDER_WIDTH)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_COMPAT_PADDING)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_MAX_IMAGE_SIZE)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_SHOW_MOTION_SPEC)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_HIDE_MOTION_SPEC)
  }

  @Test
  fun testTabLayout() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("TabLayout.java", "src/java/android/support/design/TabLayout.java")
    val util = InspectorTestUtil(projectRule, TAB_LAYOUT.oldName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(29)
    checkTitle(util.inspector.lines[0], "TabLayout")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], AUTO_URI, ATTR_TAB_INDICATOR_COLOR)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_TAB_INDICATOR_HEIGHT)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_TAB_CONTENT_START)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_TAB_BACKGROUND)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_TAB_INDICATOR)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_TAB_INDICATOR_GRAVITY)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_TAB_INDICATOR_ANIMATION_DURATION)
    checkProperty(util.inspector.lines[9], AUTO_URI, ATTR_TAB_INDICATOR_FULL_WIDTH)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_TAB_MODE)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_TAB_GRAVITY)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_TAB_INLINE_LABEL)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_TAB_MIN_WIDTH)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_TAB_MAX_WIDTH)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_TAB_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_TAB_TEXT_COLOR)
    checkProperty(util.inspector.lines[17], AUTO_URI, ATTR_TAB_SELECTED_TEXT_COLOR)
    checkProperty(util.inspector.lines[18], AUTO_URI, ATTR_TAB_PADDING)
    checkProperty(util.inspector.lines[19], AUTO_URI, ATTR_TAB_PADDING_START)
    checkProperty(util.inspector.lines[20], AUTO_URI, ATTR_TAB_PADDING_END)
    checkProperty(util.inspector.lines[21], AUTO_URI, ATTR_TAB_PADDING_TOP)
    checkProperty(util.inspector.lines[22], AUTO_URI, ATTR_TAB_PADDING_BOTTOM)
    checkProperty(util.inspector.lines[23], AUTO_URI, ATTR_TAB_ICON_TINT)
    checkProperty(util.inspector.lines[24], AUTO_URI, ATTR_TAB_ICON_TINT_MODE)
    checkProperty(util.inspector.lines[25], AUTO_URI, ATTR_TAB_RIPPLE_COLOR)
    checkProperty(util.inspector.lines[26], AUTO_URI, ATTR_TAB_UNBOUNDED_RIPPLE)
    checkProperty(util.inspector.lines[27], ANDROID_URI, ATTR_THEME)
    checkProperty(util.inspector.lines[28], ANDROID_URI, ATTR_BACKGROUND)
  }

  @Test
  fun testTabLayoutX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("TabLayoutX.java", "src/java/com/google/android/material/tabs/TabLayout.java")
    val util = InspectorTestUtil(projectRule, TAB_LAYOUT.newName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(29)
    checkTitle(util.inspector.lines[0], "TabLayout")
    checkProperty(util.inspector.lines[1], "", ATTR_STYLE)
    checkProperty(util.inspector.lines[2], AUTO_URI, ATTR_TAB_INDICATOR_COLOR)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_TAB_INDICATOR_HEIGHT)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_TAB_CONTENT_START)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_TAB_BACKGROUND)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_TAB_INDICATOR)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_TAB_INDICATOR_GRAVITY)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_TAB_INDICATOR_ANIMATION_DURATION)
    checkProperty(util.inspector.lines[9], AUTO_URI, ATTR_TAB_INDICATOR_FULL_WIDTH)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_TAB_MODE)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_TAB_GRAVITY)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_TAB_INLINE_LABEL)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_TAB_MIN_WIDTH)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_TAB_MAX_WIDTH)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_TAB_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_TAB_TEXT_COLOR)
    checkProperty(util.inspector.lines[17], AUTO_URI, ATTR_TAB_SELECTED_TEXT_COLOR)
    checkProperty(util.inspector.lines[18], AUTO_URI, ATTR_TAB_PADDING)
    checkProperty(util.inspector.lines[19], AUTO_URI, ATTR_TAB_PADDING_START)
    checkProperty(util.inspector.lines[20], AUTO_URI, ATTR_TAB_PADDING_END)
    checkProperty(util.inspector.lines[21], AUTO_URI, ATTR_TAB_PADDING_TOP)
    checkProperty(util.inspector.lines[22], AUTO_URI, ATTR_TAB_PADDING_BOTTOM)
    checkProperty(util.inspector.lines[23], AUTO_URI, ATTR_TAB_ICON_TINT)
    checkProperty(util.inspector.lines[24], AUTO_URI, ATTR_TAB_ICON_TINT_MODE)
    checkProperty(util.inspector.lines[25], AUTO_URI, ATTR_TAB_RIPPLE_COLOR)
    checkProperty(util.inspector.lines[26], AUTO_URI, ATTR_TAB_UNBOUNDED_RIPPLE)
    checkProperty(util.inspector.lines[27], ANDROID_URI, ATTR_THEME)
    checkProperty(util.inspector.lines[28], ANDROID_URI, ATTR_BACKGROUND)
  }

  @Test
  fun testTextInputLayout() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("TextInputLayout.java", "src/java/android/support/design/text/TextInputLayout.java")
    val util = InspectorTestUtil(projectRule, TEXT_INPUT_LAYOUT.oldName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(25)
    checkTitle(util.inspector.lines[0], "TextInputLayout")
    checkProperty(util.inspector.lines[1], ANDROID_URI, ATTR_TEXT_COLOR_HINT)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_HINT)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_HINT_ENABLED)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_HINT_ANIMATION_ENABLED)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_HINT_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_HELPER_TEXT)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_HELPER_TEXT_ENABLED)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_HELPER_TEXT_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[9], AUTO_URI, ATTR_ERROR_ENABLED)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_ERROR_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_COUNTER_ENABLED)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_COUNTER_MAX_LENGTH)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_COUNTER_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_PASSWORD_TOGGLE_ENABLED)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_PASSWORD_TOGGLE_DRAWABLE)
    checkProperty(util.inspector.lines[17], AUTO_URI, ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION)
    checkProperty(util.inspector.lines[18], AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT)
    checkProperty(util.inspector.lines[19], AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT_MODE)
    checkProperty(util.inspector.lines[20], AUTO_URI, ATTR_BOX_BACKGROUND_MODE)
    checkProperty(util.inspector.lines[21], AUTO_URI, ATTR_BOX_COLLAPSED_PADDING_TOP)
    checkProperty(util.inspector.lines[22], AUTO_URI, ATTR_BOX_STROKE_COLOR)
    checkProperty(util.inspector.lines[23], AUTO_URI, ATTR_BOX_BACKGROUND_COLOR)
    checkProperty(util.inspector.lines[24], AUTO_URI, ATTR_BOX_STROKE_WIDTH)
  }

  @Test
  fun testTextInputLayoutX() {
    projectRule.fixture.copyFileToProject("material.xml", "res/values/material.xml")
    projectRule.fixture.copyFileToProject("TextInputLayoutX.java", "src/java/com/google/android/material/textfield/TextInputLayout.java")
    val util = InspectorTestUtil(projectRule, TEXT_INPUT_LAYOUT.newName(), LINEAR_LAYOUT)
    val builder = ViewInspectorBuilder(projectRule.project, util.editorProvider)
    util.loadProperties()
    builder.attachToInspector(util.inspector, util.properties)
    assertThat(util.inspector.lines).hasSize(25)
    checkTitle(util.inspector.lines[0], "TextInputLayout")
    checkProperty(util.inspector.lines[1], ANDROID_URI, ATTR_TEXT_COLOR_HINT)
    checkProperty(util.inspector.lines[2], ANDROID_URI, ATTR_HINT)
    checkProperty(util.inspector.lines[3], AUTO_URI, ATTR_HINT_ENABLED)
    checkProperty(util.inspector.lines[4], AUTO_URI, ATTR_HINT_ANIMATION_ENABLED)
    checkProperty(util.inspector.lines[5], AUTO_URI, ATTR_HINT_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[6], AUTO_URI, ATTR_HELPER_TEXT)
    checkProperty(util.inspector.lines[7], AUTO_URI, ATTR_HELPER_TEXT_ENABLED)
    checkProperty(util.inspector.lines[8], AUTO_URI, ATTR_HELPER_TEXT_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[9], AUTO_URI, ATTR_ERROR_ENABLED)
    checkProperty(util.inspector.lines[10], AUTO_URI, ATTR_ERROR_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[11], AUTO_URI, ATTR_COUNTER_ENABLED)
    checkProperty(util.inspector.lines[12], AUTO_URI, ATTR_COUNTER_MAX_LENGTH)
    checkProperty(util.inspector.lines[13], AUTO_URI, ATTR_COUNTER_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[14], AUTO_URI, ATTR_COUNTER_OVERFLOW_TEXT_APPEARANCE)
    checkProperty(util.inspector.lines[15], AUTO_URI, ATTR_PASSWORD_TOGGLE_ENABLED)
    checkProperty(util.inspector.lines[16], AUTO_URI, ATTR_PASSWORD_TOGGLE_DRAWABLE)
    checkProperty(util.inspector.lines[17], AUTO_URI, ATTR_PASSWORD_TOGGLE_CONTENT_DESCRIPTION)
    checkProperty(util.inspector.lines[18], AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT)
    checkProperty(util.inspector.lines[19], AUTO_URI, ATTR_PASSWORD_TOGGLE_TINT_MODE)
    checkProperty(util.inspector.lines[20], AUTO_URI, ATTR_BOX_BACKGROUND_MODE)
    checkProperty(util.inspector.lines[21], AUTO_URI, ATTR_BOX_COLLAPSED_PADDING_TOP)
    checkProperty(util.inspector.lines[22], AUTO_URI, ATTR_BOX_STROKE_COLOR)
    checkProperty(util.inspector.lines[23], AUTO_URI, ATTR_BOX_BACKGROUND_COLOR)
    checkProperty(util.inspector.lines[24], AUTO_URI, ATTR_BOX_STROKE_WIDTH)
  }

  private fun checkTitle(line: FakeInspectorLine, title: String) {
    assertThat(line.type).isEqualTo(LineType.TITLE)
    assertThat(line.title).isEqualTo(title)
  }

  private fun checkProperty(line: FakeInspectorLine, namespace: String, propertyName: String) {
    assertThat(line.type).isEqualTo(LineType.PROPERTY)
    assertThat(line.editorModel?.property?.name).isEqualTo(propertyName)
    assertThat(line.editorModel?.property?.namespace).isEqualTo(namespace)
  }

  private fun addImageViewProperties(util: InspectorTestUtil, withAppCompat: Boolean) {
    if (withAppCompat) {
      util.addProperty(AUTO_URI, ATTR_SRC_COMPAT, NelePropertyType.COLOR_OR_DRAWABLE)
    }
    else {
      util.addProperty(ANDROID_URI, ATTR_SRC, NelePropertyType.COLOR_OR_DRAWABLE)
    }
    util.addProperty(ANDROID_URI, ATTR_CONTENT_DESCRIPTION, NelePropertyType.STRING)
    util.addProperty(ANDROID_URI, ATTR_BACKGROUND, NelePropertyType.COLOR_OR_DRAWABLE)
    util.addProperty(ANDROID_URI, ATTR_SCALE_TYPE, NelePropertyType.INTEGER)
    util.addProperty(ANDROID_URI, ATTR_ADJUST_VIEW_BOUNDS, NelePropertyType.THREE_STATE_BOOLEAN)
    util.addProperty(ANDROID_URI, ATTR_CROP_TO_PADDING, NelePropertyType.THREE_STATE_BOOLEAN)
  }
}
