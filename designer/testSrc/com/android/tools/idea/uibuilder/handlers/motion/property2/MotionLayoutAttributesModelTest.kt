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
package com.android.tools.idea.uibuilder.handlers.motion.property2

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.property2.testutil.MotionAttributeRule
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.property.panel.api.PropertiesModelListener
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.MockitoAnnotations

@RunsInEdt
class MotionLayoutAttributesModelTest {
  @JvmField
  @Rule
  val projectRule = AndroidProjectRule.withSdk()

  @JvmField
  @Rule
  val motionRule = MotionAttributeRule(projectRule, "layout.xml", "scene.xml")

  @JvmField
  @Rule
  val edtRule = EdtRule()

  @Mock
  private lateinit var listener: PropertiesModelListener<NelePropertyItem>

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)
  }

  @Test
  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    val model = motionRule.attributesModel
    val surface = model.surface
    model.surface = null

    model.addListener(listener)
    model.surface = surface
    verifyZeroInteractions(listener)

    motionRule.selectConstraint("start", "widget")
    verify(listener).propertiesGenerated(model)

    // Verify that if the same motion tag is selected again we get a different notification:
    motionRule.selectConstraint("start", "widget")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).propertyValuesChanged(model)
  }
}