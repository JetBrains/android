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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF
import com.android.SdkConstants.ATTR_TEXT_SIZE
import com.android.SdkConstants.AUTO_URI
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.MOTION_MOTION_STAGGER
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.MOTION_PATH_MOTION_ARC
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CONSTRAINT
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class MotionDefaultPropertyValueProviderTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.onDisk()

  @JvmField @Rule val motionRule = MotionAttributeRule(projectRule)

  @Test
  fun testConstraintFromLayout() {
    motionRule.selectConstraint("start", "buttonEmptyConstraint")
    assertThat(lookup(CONSTRAINT, ANDROID_URI, ATTR_LAYOUT_HEIGHT)).isEqualTo("10dp")
    assertThat(lookup(CONSTRAINT, AUTO_URI, ATTR_LAYOUT_TOP_TO_TOP_OF)).isEqualTo("@id/button")
  }

  @Test
  fun testConstraintFromBaseConstraintSet() {
    motionRule.selectConstraint("start", "button")
    assertThat(lookup(CONSTRAINT, AUTO_URI, MOTION_MOTION_STAGGER)).isEqualTo("1")
    assertThat(lookup(CONSTRAINT, AUTO_URI, MOTION_PATH_MOTION_ARC)).isEqualTo("startHorizontal")
    assertThat(lookup(CUSTOM_ATTRIBUTE, "", ATTR_TEXT_SIZE)).isEqualTo("2sp")
  }

  private fun lookup(section: String, namespace: String, attrName: String): String? {
    val model = motionRule.attributesModel
    val property = model.allProperties[section]!![namespace, attrName]!!
    val defaultProvider = MotionDefaultPropertyValueProvider()
    return defaultProvider.provideDefaultValue(property)
  }
}
