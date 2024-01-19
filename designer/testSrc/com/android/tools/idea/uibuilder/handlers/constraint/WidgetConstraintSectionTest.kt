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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants
import com.android.tools.idea.refactoring.rtl.RtlSupportProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConstraintSectionTest {

  @Test
  fun getAttributesBelowRtlApiVersion() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START - 1
    val rtl = true // doesn't matter

    val leftAttrs = getAttributesForConstraint(SecondarySelector.Constraint.LEFT, api, rtl)
    assertEquals(2, leftAttrs.size)
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF))

    val rightAttrs = getAttributesForConstraint(SecondarySelector.Constraint.RIGHT, api, rtl)
    assertEquals(2, rightAttrs.size)
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF))

    val topAttrs = getAttributesForConstraint(SecondarySelector.Constraint.TOP, api, rtl)
    assertEquals(2, topAttrs.size)
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF))

    val bottomAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BOTTOM, api, rtl)
    assertEquals(2, bottomAttrs.size)
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF))

    val baselineAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BASELINE, api, rtl)
    assertEquals(1, baselineAttrs.size)
    assertTrue(baselineAttrs.contains(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF))
  }

  @Test
  fun getAttributes() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START
    val rtl = false

    val leftAttrs = getAttributesForConstraint(SecondarySelector.Constraint.LEFT, api, rtl)
    assertEquals(4, leftAttrs.size)
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_START_TO_START_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_START_TO_END_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF))

    val rightAttrs = getAttributesForConstraint(SecondarySelector.Constraint.RIGHT, api, rtl)
    assertEquals(4, rightAttrs.size)
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_END_TO_START_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_END_TO_END_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF))

    val topAttrs = getAttributesForConstraint(SecondarySelector.Constraint.TOP, api, rtl)
    assertEquals(2, topAttrs.size)
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF))

    val bottomAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BOTTOM, api, rtl)
    assertEquals(2, bottomAttrs.size)
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF))

    val baselineAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BASELINE, api, rtl)
    assertEquals(1, baselineAttrs.size)
    assertTrue(baselineAttrs.contains(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF))
  }

  @Test
  fun getAttributesRtl() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START
    val rtl = true

    val leftAttrs = getAttributesForConstraint(SecondarySelector.Constraint.LEFT, api, rtl)
    assertEquals(4, leftAttrs.size)
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_END_TO_START_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_END_TO_END_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF))
    assertTrue(leftAttrs.contains(SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF))

    val rightAttrs = getAttributesForConstraint(SecondarySelector.Constraint.RIGHT, api, rtl)
    assertEquals(4, rightAttrs.size)
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_START_TO_START_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_START_TO_END_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF))
    assertTrue(rightAttrs.contains(SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF))

    val topAttrs = getAttributesForConstraint(SecondarySelector.Constraint.TOP, api, rtl)
    assertEquals(2, topAttrs.size)
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF))
    assertTrue(topAttrs.contains(SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF))

    val bottomAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BOTTOM, api, rtl)
    assertEquals(2, bottomAttrs.size)
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF))
    assertTrue(bottomAttrs.contains(SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF))

    val baselineAttrs = getAttributesForConstraint(SecondarySelector.Constraint.BASELINE, api, rtl)
    assertEquals(1, baselineAttrs.size)
    assertTrue(baselineAttrs.contains(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF))
  }

  @Test
  fun getConstraintBelowRtlApiVersion() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START - 1
    val rtl = true // doesn't matter

    val leftAttrs =
      listOf(SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF)
    assertTrue(
      leftAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.LEFT }
    )

    val rightAttrs =
      listOf(SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF)
    assertTrue(
      rightAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.RIGHT
      }
    )

    val topAttrs =
      listOf(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF)
    assertTrue(
      topAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.TOP }
    )

    val bottomAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      )
    assertTrue(
      bottomAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BOTTOM
      }
    )

    val baselineAttrs = listOf(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF)
    assertTrue(
      baselineAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BASELINE
      }
    )

    assertTrue(NON_CONSTRAINT_ATTRIBUTES.all { getConstraintForAttribute(it, api, rtl) == null })
  }

  @Test
  fun getConstraintRtl() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START
    val rtl = true

    val leftAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
      )
    assertTrue(
      leftAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.LEFT }
    )

    val rightAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
      )
    assertTrue(
      rightAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.RIGHT
      }
    )

    val topAttrs =
      listOf(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF)
    assertTrue(
      topAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.TOP }
    )

    val bottomAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      )
    assertTrue(
      bottomAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BOTTOM
      }
    )

    val baselineAttrs = listOf(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF)
    assertTrue(
      baselineAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BASELINE
      }
    )

    assertTrue(NON_CONSTRAINT_ATTRIBUTES.all { getConstraintForAttribute(it, api, rtl) == null })
  }

  @Test
  fun getConstraint() {
    val api = RtlSupportProcessor.RTL_TARGET_SDK_START
    val rtl = false

    val leftAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_START_TO_START_OF,
      )
    assertTrue(
      leftAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.LEFT }
    )

    val rightAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF,
        SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF,
        SdkConstants.ATTR_LAYOUT_END_TO_END_OF,
        SdkConstants.ATTR_LAYOUT_END_TO_START_OF,
      )
    assertTrue(
      rightAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.RIGHT
      }
    )

    val topAttrs =
      listOf(SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF)
    assertTrue(
      topAttrs.all { getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.TOP }
    )

    val bottomAttrs =
      listOf(
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF,
        SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF,
      )
    assertTrue(
      bottomAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BOTTOM
      }
    )

    val baselineAttrs = listOf(SdkConstants.ATTR_LAYOUT_BASELINE_TO_BASELINE_OF)
    assertTrue(
      baselineAttrs.all {
        getConstraintForAttribute(it, api, rtl) == SecondarySelector.Constraint.BASELINE
      }
    )

    assertTrue(NON_CONSTRAINT_ATTRIBUTES.all { getConstraintForAttribute(it, api, rtl) == null })
  }
}

private val NON_CONSTRAINT_ATTRIBUTES =
  CONSTRAINT_WIDGET_SECTION_ITEMS.map { it.attribute }.filterNot { it in CONSTRAINT_ATTRIBUTES }
