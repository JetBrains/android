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
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class MotionSelectionTest {
  private val projectRule = AndroidProjectRule.withSdk()
  private val motionRule = MotionAttributeRule(projectRule)

  @get:Rule val ruleChain = RuleChain.outerRule(projectRule).around(motionRule).around(EdtRule())!!

  @Test
  fun testComponentForCustomAttributeCompletionsFromConstraint() {
    motionRule.selectConstraint("start", "widget")
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("widget")
  }

  @Test
  fun testComponentForCustomAttributeCompletionsFromKeyAttribute() {
    motionRule.selectKeyFrame(
      "start",
      "end",
      MotionSceneAttrs.Tags.KEY_ATTRIBUTE,
      99,
      "widget",
    )
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("widget")
  }

  @Test
  fun testComponentForCustomAttributeCompletionsFromKeyAttributeOnButton() {
    motionRule.selectKeyFrame(
      "start",
      "end",
      MotionSceneAttrs.Tags.KEY_ATTRIBUTE,
      13,
      "button",
    )
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("button")
  }

  @Test
  fun testComponentForCustomAttributeCompletionsFromKeyAttributeViaTagMatching() {
    motionRule.selectKeyFrame(
      "start",
      "end",
      MotionSceneAttrs.Tags.KEY_ATTRIBUTE,
      27,
      ".*Window",
    )
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("buttonEmptyConstraint")
  }

  @Test
  fun testComponentForCustomAttributeCompletionsFromKeyCycle() {
    motionRule.selectKeyFrame(
      "start",
      "end",
      MotionSceneAttrs.Tags.KEY_CYCLE,
      15,
      "widget",
    )
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("widget")
  }

  @Test
  fun testComponentForCustomAttributeCompletionsFromKeyTimeCycle() {
    motionRule.selectKeyFrame(
      "start",
      "end",
      MotionSceneAttrs.Tags.KEY_TIME_CYCLE,
      25,
      "widget",
    )
    val component = motionRule.selection.componentForCustomAttributeCompletions
    assertThat(component?.id).isEqualTo("widget")
  }
}
