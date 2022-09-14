/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.ConstraintSet.DERIVE_CONSTRAINTS_FROM
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.OnClick.ATTR_TARGET_ID
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CONSTRAINT
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CONSTRAINTSET
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.LAYOUT
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.ON_CLICK
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.TRANSITION
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Transition.ATTR_CONSTRAINTSET_START
import com.android.tools.idea.uibuilder.handlers.motion.property.CustomAttributeType.CUSTOM_STRING
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.PropertiesModelListener
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

private const val SCENE_FILE = "scene.xml"

@RunsInEdt
class MotionLayoutAttributesModelTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val motionRule = MotionAttributeRule(projectRule)

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Mock
  private lateinit var listener: PropertiesModelListener<NlPropertyItem>

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testPropertiesEventsAfterHookupAndSelection() {
    val model = motionRule.attributesModel
    val surface = model.surface
    model.surface = null

    model.addListener(listener)
    model.surface = surface
    verifyNoMoreInteractions(listener)

    // Verify that we get a generated event:
    motionRule.selectConstraint("start", "widget")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertiesGenerated(model)
    reset(listener)

    // Verify that if the same motion tag is selected again we get a changed event instead:
    motionRule.selectConstraint("start", "widget")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener, times(1)).propertyValuesChanged(model)
    reset(listener)

    // Verify that if the same motion tag is selected again after the model is deactivated, we get a generated event:
    motionRule.attributesModel.deactivate()
    motionRule.selectConstraint("start", "widget")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertiesGenerated(model)
  }

  @Test
  fun testSelectBetweenTwoConstraintSets() {
    val model = motionRule.attributesModel
    val surface = model.surface
    model.surface = null

    model.addListener(listener)
    model.surface = surface
    verifyNoMoreInteractions(listener)

    // Verify that we get a generated event:
    motionRule.selectConstraint("start", "buttonEmptyConstraint")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertiesGenerated(model)
    reset(listener)

    // Verify that we get a generated event after selecting a different empty constraint:
    motionRule.selectConstraint("end", "buttonEmptyConstraint")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertiesGenerated(model)
    reset(listener)

    // Verify that we get a generated event after selecting a different empty constraint:
    motionRule.selectConstraint("start", "buttonEmptyConstraint")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertiesGenerated(model)
    reset(listener)
  }

  @Test
  fun testRenameOfConstraintId() {
    val model = motionRule.attributesModel
    motionRule.selectConstraintSet("start")
    val property = model.allProperties[CONSTRAINTSET]!![ANDROID_URI, ATTR_ID]
    property.value = "different_start"
    motionRule.update()

    motionRule.selectConstraintSet("different_start")
    assertThat(model.allProperties[CONSTRAINTSET]!![ANDROID_URI, ATTR_ID].rawValue)
      .isEqualTo("@+id/different_start")
    motionRule.selectTransition("different_start", "end")
    assertThat(model.allProperties[TRANSITION]!![AUTO_URI, ATTR_CONSTRAINTSET_START].rawValue)
      .isEqualTo("@id/different_start")
  }

  @Test
  fun testSetPropertyOnMotionTag() {
    motionRule.selectConstraintSet("start")
    val model = motionRule.attributesModel
    val property = model.allProperties[CONSTRAINTSET]!![AUTO_URI, DERIVE_CONSTRAINTS_FROM]!!
    property.value = "@id/end"
    assertThat(motionRule.sceneFileLines(5..7)).isEqualTo("<ConstraintSet\n" +
                                                          "     android:id=\"@+id/start\"\n" +
                                                          "     motion:deriveConstraintsFrom=\"@id/end\">")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set ConstraintSet.deriveConstraintsFrom to @id/end")
  }

  @Test
  fun testSetCustomProperty() {
    motionRule.selectConstraint("start", "widget")
    val model = motionRule.attributesModel
    val property = model.allProperties[CUSTOM_ATTRIBUTE]!!["", ATTR_TEXT_SIZE]!!
    property.value = "7sp"
    assertThat(motionRule.sceneFileLines(27..29)).isEqualTo("<CustomAttribute\n" +
                                                            "     motion:attributeName=\"textSize\"\n" +
                                                            "     motion:customPixelDimension=\"7sp\"/>")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.textSize to 7sp")
  }

  @Test
  fun testSetSectionedProperty() {
    motionRule.selectConstraint("start", "button")
    val model = motionRule.attributesModel
    val property = model.allProperties[LAYOUT]!![ANDROID_URI, ATTR_LAYOUT_HEIGHT]!!
    property.value = "10dp"
    assertThat(motionRule.sceneFileLines(33..35)).isEqualTo("<Layout\n" +
                                                            "     android:layout_width=\"1dp\"\n" +
                                                            "     android:layout_height=\"10dp\"/>")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set Layout.layout_height to 10dp")
  }

  @Test
  fun testSetSectionedPropertyInheritedFromLayout() {
    motionRule.selectConstraint("base", "button")
    val model = motionRule.attributesModel
    val property = model.allProperties[LAYOUT]!![ANDROID_URI, ATTR_LAYOUT_HEIGHT]!!
    property.value = "30dp"
    assertThat(motionRule.sceneFileLines(96..104)).isEqualTo("<Layout\n" +
                                                             "     android:layout_width=\"32dp\"\n" +
                                                             "     android:layout_height=\"30dp\"\n" +
                                                             "     motion:layout_constraintTop_toTopOf=\"@id/image\"\n" +
                                                             "     motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                                                             "     motion:layout_editor_absoluteY=\"30dp\"\n" +
                                                             "     motion:layout_editor_absoluteX=\"10dp\"\n" +
                                                             "     motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                                                             "     motion:layout_constraintHorizontal_bias=\"0.5\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set Layout.layout_height to 30dp")
  }

  @Test
  fun testSetPropertyConstraintsFromLayout() {
    motionRule.selectConstraint("start", "buttonEmptyConstraint")
    val model = motionRule.attributesModel
    val property = model.allProperties[CONSTRAINT]!![ANDROID_URI, ATTR_LAYOUT_HEIGHT]!!
    property.value = "30dp"
    assertThat(motionRule.sceneFileLines(37..46)).isEqualTo("<Constraint\n" +
                                                            "     android:id=\"@+id/buttonEmptyConstraint\"\n" +
                                                            "     motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                                                            "     android:layout_width=\"32dp\"\n" +
                                                            "     android:layout_height=\"30dp\"\n" +
                                                            "     motion:layout_constraintTag=\"BigButtonWindow\"\n" +
                                                            "     motion:layout_constraintTop_toTopOf=\"@id/button\"\n" +
                                                            "     motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                                                            "     motion:layout_editor_absoluteY=\"40dp\"\n" +
                                                            "     motion:layout_editor_absoluteX=\"10dp\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set Constraint.layout_height to 30dp")
  }

  @Test
  fun testSetOnClickProperty() {
    motionRule.selectTransition("start", "end")
    val model = motionRule.attributesModel
    val property = model.allProperties[ON_CLICK]!![AUTO_URI, ATTR_TARGET_ID]!!
    property.value = "@+id/widget"
    assertThat(motionRule.sceneFileLines(106..106)).isEqualTo("<OnClick motion:targetId=\"@+id/widget\"/>")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set OnClick.targetId to @+id/widget")
  }

  @Test
  fun testBrowse() {
    motionRule.enableFileOpenCaptures()
    motionRule.selectConstraint("start", "widget")
    val model = motionRule.attributesModel
    val property = model.allProperties[CONSTRAINT]!![ANDROID_URI, ATTR_LAYOUT_HEIGHT]!!
    model.browseToValue(property)
    motionRule.checkEditor(SCENE_FILE, 12, "android:layout_height=\"64dp\"")
  }

  @Test
  fun testCreateCustomConstraintTag() {
    motionRule.selectConstraint("start", "widget")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT, "Hello", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "            motion:attributeName=\"text\"\n" +
                                            "            motion:customStringValue=\"Hello\" />")
    assertThat(motionRule.sceneFileLines(30..32)).isEqualTo("<CustomAttribute\n" +
                                                            "     motion:attributeName=\"text\"\n" +
                                                            "     motion:customStringValue=\"Hello\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.text to Hello")
  }

  @Test
  fun testCreateCustomConstraintTagInNonExistingConstraint() {
    motionRule.selectConstraint("start", "buttonEmptyConstraint")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT, "Hello", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "              motion:attributeName=\"text\"\n" +
                                            "              motion:customStringValue=\"Hello\" />")
    assertThat(motionRule.sceneFileLines(37..51)).isEqualTo("<Constraint\n" +
                                                            "     android:id=\"@+id/buttonEmptyConstraint\"\n" +
                                                            "     motion:layout_constraintEnd_toStartOf=\"parent\"\n" +
                                                            "     android:layout_width=\"32dp\"\n" +
                                                            "     android:layout_height=\"10dp\"\n" +
                                                            "     motion:layout_constraintTag=\"BigButtonWindow\"\n" +
                                                            "     motion:layout_constraintTop_toTopOf=\"@id/button\"\n" +
                                                            "     motion:layout_constraintStart_toStartOf=\"parent\"\n" +
                                                            "     motion:layout_editor_absoluteY=\"40dp\"\n" +
                                                            "     motion:layout_editor_absoluteX=\"10dp\">\n" +
                                                            "     <CustomAttribute\n" +
                                                            "         motion:attributeName=\"text\"\n" +
                                                            "         motion:customStringValue=\"Hello\" />\n" +
                                                            " </Constraint>")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.text to Hello")
  }

  @Test
  fun testCreateCustomTagForKeyAttribute() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_ATTRIBUTE, 60, "widget")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT_SIZE, "50sp", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "              motion:attributeName=\"textSize\"\n" +
                                            "              motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.sceneFileLines(150..152)).isEqualTo("<CustomAttribute\n" +
                                                            "     motion:attributeName=\"textSize\"\n" +
                                                            "     motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.textSize to 50sp")
  }

  @Test
  fun testCreateCustomTagForKeyCycle() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_CYCLE, 15, "widget")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT_SIZE, "50sp", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "              motion:attributeName=\"textSize\"\n" +
                                            "              motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.sceneFileLines(165..167)).isEqualTo("<CustomAttribute\n" +
                                                              "     motion:attributeName=\"textSize\"\n" +
                                                              "     motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.textSize to 50sp")
  }

  @Test
  fun testCreateCustomTagForKeyTimeCycle() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_TIME_CYCLE, 25, "widget")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT_SIZE, "50sp", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "              motion:attributeName=\"textSize\"\n" +
                                            "              motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.sceneFileLines(173..175)).isEqualTo("<CustomAttribute\n" +
                                                              "     motion:attributeName=\"textSize\"\n" +
                                                              "     motion:customStringValue=\"50sp\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.textSize to 50sp")
  }

  @Test
  fun testCreateExistingCustomConstraintTag() {
    motionRule.selectConstraint("start", "widget")
    val model = motionRule.attributesModel
    var tag: MotionSceneTag? = null
    model.createCustomXmlTag(motionRule.selection, ATTR_TEXT_SIZE, "20sp", CUSTOM_STRING) { tag = it }
    assertThat(tag!!.xmlTag.text).isEqualTo("<CustomAttribute\n" +
                                            "          motion:attributeName=\"textSize\"\n" +
                                            "          motion:customStringValue=\"20sp\" />")
    assertThat(motionRule.sceneFileLines(27..29)).isEqualTo("<CustomAttribute\n" +
                                                            "     motion:attributeName=\"textSize\"\n" +
                                                            "     motion:customStringValue=\"20sp\" />")
    assertThat(motionRule.lastUndoDescription).isEqualTo("Undo Set CustomAttribute.textSize to 20sp")
  }
}
