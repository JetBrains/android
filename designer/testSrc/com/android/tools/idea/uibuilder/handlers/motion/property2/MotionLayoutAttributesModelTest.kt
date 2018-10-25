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
package com.android.tools.idea.uibuilder.handlers.motion.property2

import com.android.SdkConstants
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property2.api.PropertiesModelListener
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.TransitionConstraintSetStart
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.xml.XmlFile
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito

class MotionLayoutAttributesModelTest: LayoutTestCase() {

  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = Mockito.mock(PropertiesModelListener::class.java) as PropertiesModelListener<MotionPropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    val (nlModel, timeline) = createNlModel()
    model.addListener(listener)

    // test
    model.surface = nlModel.surface
    assertThat(timeline.listenerCount).isEqualTo(0)
    nlModel.surface.selectionModel.setSelection(nlModel.components)
    assertThat(timeline.listenerCount).isEqualTo(1)
    Mockito.verifyZeroInteractions(listener)

    val textView = nlModel.components[0].getChild(0)
    val scene = MotionSceneModel.parse(nlModel, project, file, xmlFile)
    timeline.updateTransition(scene.getTransitionTag(0), textView)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verify(listener).propertiesGenerated(model)
  }

  fun testPropertiesGeneratedEventWhenSwitchingDesignSurface() {
    // setup
    myFixture.copyFileToProject("motion/attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = Mockito.mock(PropertiesModelListener::class.java) as PropertiesModelListener<MotionPropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    model.addListener(listener)
    val (nlModelA, timelineA) = createNlModel()
    val (nlModelB, timelineB) = createNlModel()
    val textViewA = nlModelB.find("widget")!!
    val textViewB = nlModelB.find("widget")!!
    val scene = MotionSceneModel.parse(nlModelB, project, file, xmlFile)

    // test
    model.surface = nlModelA.surface
    model.surface = nlModelB.surface
    nlModelA.surface.selectionModel.setSelection(listOf(textViewA))
    timelineA.updateTransition(scene.getTransitionTag(0), textViewA)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verifyZeroInteractions(listener)

    nlModelB.surface.selectionModel.setSelection(listOf(textViewB))
    timelineB.updateTransition(scene.getTransitionTag(0), textViewB)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verify(listener).propertiesGenerated(model)
    assertThat(model.properties[SdkConstants.AUTO_URI, TransitionConstraintSetStart].component.model).isEqualTo(nlModelB)
  }

  fun testConstraintSet() {
    // setup
    myFixture.copyFileToProject("motion/attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = Mockito.mock(PropertiesModelListener::class.java) as PropertiesModelListener<MotionPropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    val (nlModel, timeline) = createNlModel()
    model.addListener(listener)
    val scene = MotionSceneModel.parse(nlModel, project, file, xmlFile)
    model.surface = nlModel.surface
    val textView = nlModel.components[0].getChild(0)
    nlModel.surface.selectionModel.setSelection(nlModel.components)

    // test
    timeline.updateConstraintSet(scene.startConstraintSet, textView)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verify(listener).propertiesGenerated(model)
    assertThat(model.properties[SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH].value).isEqualTo("64dp")
  }

  private fun createNlModel(): Pair<SyncNlModel, MockTimelineOwner> {
    val builder = model(
      "motion.xml",
      component(SdkConstants.MOTION_LAYOUT.newName())
        .withBounds(0, 0, 1000, 1500)
        .id("@id/motion")
        .matchParentWidth()
        .matchParentHeight()
        .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT, "com.example.MyActivity")
        .children(
          component(SdkConstants.TEXT_VIEW)
            .withBounds(100, 100, 100, 100)
            .id("@+id/widget")
            .width("wrap_content")
            .height("wrap_content")
        )
    )
    val model = builder.build()
    val timeline = MockTimelineOwner()
    model.components[0].putClientProperty(TimelineOwner.TIMELINE_PROPERTY, timeline)
    return Pair(model, timeline)
  }

  private class MockTimelineOwner : TimelineOwner {
    private val listeners = mutableListOf<TimelineListener>()

    override fun addTimelineListener(listener: TimelineListener) {
      listeners.add(listener)
    }

    override fun removeTimeLineListener(listener: TimelineListener) {
      listeners.remove(listener)
    }

    val listenerCount: Int
      get() = listeners.size

    fun updateTransition(transition: MotionSceneModel.TransitionTag, component: NlComponent?) {
      listeners.forEach { it.updateTransition(transition, component) }
    }

    fun updateConstraintSet(constraintSet: MotionSceneModel.ConstraintSet, component: NlComponent?) {
      listeners.forEach { it.updateConstraintSet(constraintSet, component) }
    }

    fun updateSelection(keyFrame: MotionSceneModel.KeyFrame, component: NlComponent?) {
      listeners.forEach { it.updateSelection(keyFrame, component) }
    }
  }
}
