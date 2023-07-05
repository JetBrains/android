/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.android.tools.property.panel.impl.model.util.FakeInspectorPanel
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import icons.StudioIcons
import javax.swing.JLabel
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class SelectedTargetBuilderTest {

  @JvmField @Rule val projectRule = AndroidProjectRule.onDisk()

  @JvmField @Rule val motionRule = MotionAttributeRule(projectRule)

  @JvmField @Rule val edtRule = EdtRule()

  @Test
  fun testConstraintSet() {
    motionRule.selectConstraintSet("start")
    val inspector = FakeInspectorPanel()
    val builder = SelectedTargetBuilder()
    builder.attachToInspector(inspector, motionRule.attributesModel.properties)
    assertThat(inspector.lines.size).isEqualTo(1)
    val first = inspector.lines[0].component
    assertThat(first).isInstanceOf(ConstraintSetPanel::class.java)
    assertThat((first!!.getComponent(0) as JLabel).text).isEqualTo("start Constraint Set")
  }

  @Test
  fun testConstraint() {
    motionRule.selectConstraint("start", "widget")
    val inspector = FakeInspectorPanel()
    val builder = SelectedTargetBuilder()
    builder.attachToInspector(inspector, motionRule.attributesModel.properties)
    assertThat(inspector.lines.size).isEqualTo(2)
    val first = inspector.lines[0].component
    val second = inspector.lines[1].component
    assertThat(first).isInstanceOf(ConstraintSetPanel::class.java)
    assertThat((first?.getComponent(0) as JLabel).text).isEqualTo("start Constraint Set")
    assertThat((first.getComponent(0) as JLabel).text).isEqualTo("start Constraint Set")
    assertThat(second).isInstanceOf(SelectedTagPanel::class.java)
    assertThat((second?.getComponent(0) as JLabel).text).isEqualTo("Constraint")
    assertThat((second.getComponent(0) as JLabel).icon)
      .isSameAs(StudioIcons.LayoutEditor.Palette.TEXT_VIEW)
    assertThat((second.getComponent(1) as JLabel).text).isEqualTo("widget")
  }

  @Test
  fun testTransition() {
    motionRule.selectTransition("start", "end")
    val inspector = FakeInspectorPanel()
    val builder = SelectedTargetBuilder()
    builder.attachToInspector(inspector, motionRule.attributesModel.properties)
    assertThat(inspector.lines.size).isEqualTo(1)
    val component = inspector.lines[0].component
    assertThat(component).isInstanceOf(TransitionPanel::class.java)
    assertThat((component?.getComponent(0) as JLabel).text).isEqualTo("start")
    assertThat((component.getComponent(1) as JLabel).icon)
      .isSameAs(StudioIcons.LayoutEditor.Motion.TRANSITION)
    assertThat((component.getComponent(2) as JLabel).text).isEqualTo("end")
  }

  @Test
  fun testKeyFrame() {
    motionRule.selectKeyFrame("start", "end", MotionSceneAttrs.Tags.KEY_POSITION, 51, "widget")
    val inspector = FakeInspectorPanel()
    val builder = SelectedTargetBuilder()
    builder.attachToInspector(inspector, motionRule.attributesModel.properties)
    assertThat(inspector.lines.size).isEqualTo(2)
    val first = inspector.lines[0].component
    val second = inspector.lines[1].component
    assertThat(first).isInstanceOf(TransitionPanel::class.java)
    assertThat((first?.getComponent(0) as JLabel).text).isEqualTo("start")
    assertThat((first.getComponent(1) as JLabel).icon)
      .isSameAs(StudioIcons.LayoutEditor.Motion.TRANSITION)
    assertThat((first.getComponent(2) as JLabel).text).isEqualTo("end")
    assertThat(second).isInstanceOf(SelectedTagPanel::class.java)
    assertThat((second?.getComponent(0) as JLabel).text).isEqualTo("KeyPosition")
    assertThat((second.getComponent(0) as JLabel).icon)
      .isSameAs(StudioIcons.LayoutEditor.Motion.KEYFRAME)
    assertThat((second.getComponent(1) as JLabel).text).isEqualTo("")
  }
}
