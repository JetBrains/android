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
package com.android.tools.idea.uibuilder.handlers.motion.property

import com.android.SdkConstants
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs
import com.android.tools.idea.uibuilder.handlers.motion.property.testutil.MotionAttributeRule
import com.android.tools.idea.uibuilder.property.ui.spring.SpringModelChangeListener
import com.android.tools.idea.uibuilder.property.ui.spring.SpringParameter
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@RunsInEdt
class MotionLayoutSpringModelTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.withSdk()

  @JvmField @Rule val motionRule = MotionAttributeRule(projectRule)

  @JvmField @Rule val edtRule = EdtRule()

  @Mock private lateinit var listener: SpringModelChangeListener
  private lateinit var closeable: AutoCloseable

  @Before
  fun setUp() {
    closeable = MockitoAnnotations.openMocks(this)
  }

  @After
  fun tearDown() {
    closeable.close()
  }

  @Test
  fun listenerOnPropertiesModelChangeTest() {
    motionRule.selectTransition("start", "end")
    UIUtil.dispatchAllInvocationEvents()
    val springModel = MotionLayoutSpringModel(motionRule.attributesModel)

    springModel.addListener(listener)
    val transitionProps =
      motionRule.attributesModel.allProperties[
          MotionSceneAttrs.Tags.TRANSITION]
    transitionProps!![
        SdkConstants.AUTO_URI, MotionSceneAttrs.Transition.ATTR_DURATION]
      .value = "1000"
    UIUtil.dispatchAllInvocationEvents()

    motionRule.selectTransition("start", "end")
    UIUtil.dispatchAllInvocationEvents()
    verify(listener).onModelChanged()
  }

  @Ignore("b/324536796")
  @Test
  fun writePropertiesTest() {
    motionRule.selectTransition("start", "end")
    UIUtil.dispatchAllInvocationEvents()
    val springModel = MotionLayoutSpringModel(motionRule.attributesModel)

    val allProperties = motionRule.attributesModel.allProperties
    val transitionProp = allProperties[MotionSceneAttrs.Tags.TRANSITION]!!
    val onSwipeProp = allProperties[MotionSceneAttrs.Tags.ON_SWIPE]!!
    val durationProp =
      transitionProp[
        SdkConstants.AUTO_URI, MotionSceneAttrs.Transition.ATTR_DURATION]!!
    val massProp =
      onSwipeProp[
        SdkConstants.AUTO_URI, MotionSceneAttrs.OnSwipe.ATTR_SPRING_MASS]!!
    val dampingProp =
      onSwipeProp[
        SdkConstants.AUTO_URI,
        MotionSceneAttrs.OnSwipe.ATTR_SPRING_DAMPING]!!

    assertThat(durationProp.value).isEqualTo("2000")
    assertThat(massProp.value).isNull()
    assertThat(dampingProp.value).isNull()

    springModel.setValue(SpringParameter.DURATION, "1000")
    springModel.setValue(SpringParameter.MASS, "2")
    springModel.setValue(SpringParameter.DAMPING, "10")

    motionRule.selectTransition("start", "end")
    UIUtil.dispatchAllInvocationEvents()

    assertThat(durationProp.value).isEqualTo("1000")
    assertThat(massProp.value).isEqualTo("2")
    assertThat(dampingProp.value).isEqualTo("10")
  }
}
