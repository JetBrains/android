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
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.TransitionConstraintSetStart
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttCommands
import com.android.tools.idea.uibuilder.handlers.motion.timeline.GanttEventListener
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.ActionEvent
import javax.swing.JPanel

class MotionLayoutAttributesModelTest: LayoutTestCase() {

  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = Mockito.mock(PropertiesModelListener::class.java) as PropertiesModelListener<MotionPropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    val nlModel = createNlModel()
    val timeline = retrieveTimeline(nlModel)
    model.addListener(listener)

    // test
    model.surface = nlModel.surface
    assertThat(timeline.listenerCount).isEqualTo(1)
    Mockito.verifyZeroInteractions(listener)

    val textView = nlModel.components[0].getChild(0)
    val scene = MotionSceneModel.parse(nlModel, project, file, xmlFile)
    timeline.select(scene.getTransitionTag(0).tag, textView)
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
    val nlModelA = createNlModel()
    val nlModelB = createNlModel()
    val timelineA = retrieveTimeline(nlModelA)
    val timelineB = retrieveTimeline(nlModelB)
    val textViewA = nlModelA.find("widget")!!
    val textViewB = nlModelB.find("widget")!!
    val scene = MotionSceneModel.parse(nlModelB, project, file, xmlFile)

    // test
    model.surface = nlModelA.surface
    model.surface = nlModelB.surface
    nlModelA.surface.selectionModel.setSelection(listOf(textViewA))
    timelineA.select(scene.getTransitionTag(0).tag, textViewA)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verifyZeroInteractions(listener)

    nlModelB.surface.selectionModel.setSelection(listOf(textViewB))
    timelineB.select(scene.getTransitionTag(0).tag, textViewB)
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
    val nlModel = createNlModel()
    val timeline = retrieveTimeline(nlModel)
    model.addListener(listener)
    val scene = MotionSceneModel.parse(nlModel, project, file, xmlFile)
    model.surface = nlModel.surface
    val textView = nlModel.components[0].getChild(0)
    nlModel.surface.selectionModel.setSelection(nlModel.components)

    // test
    timeline.select(scene.startConstraintSet.tag, textView)
    UIUtil.dispatchAllInvocationEvents()
    Mockito.verify(listener).propertiesGenerated(model)
    assertThat(model.properties[SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH].value).isEqualTo("64dp")
  }

  private fun createNlModel(): SyncNlModel {
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
    val panel = mock(AccessoryPanel::class.java)
    val timeline = Timeline()
    val surface = model.surface as NlDesignSurface
    `when`(surface.accessoryPanel).thenReturn(panel)
    `when`(panel.currentPanel).thenReturn(timeline)
    return model
  }

  private fun retrieveTimeline(model: SyncNlModel): Timeline {
    val surface = model.surface as NlDesignSurface
    return surface.accessoryPanel.currentPanel as Timeline
  }

  private class Timeline: AccessoryPanelInterface, GanttEventListener {
    val listeners = mutableListOf<AccessorySelectionListener>()
    var tag: SmartPsiElementPointer<XmlTag>? = null

    override fun getPanel(): JPanel {
      throw Error("should not be called")
    }

    override fun createPanel(type: AccessoryPanel.Type?): JPanel {
      throw Error("should not be called")
    }

    override fun updateAccessoryPanelWithSelection(type: AccessoryPanel.Type, selection: MutableList<NlComponent>) {
      throw Error("should not be called")
    }

    override fun deactivate() {
      throw Error("should not be called")
    }

    override fun updateAfterModelDerivedDataChanged() {
      throw Error("should not be called")
    }

    override fun setProgress(percent: Float) {
      throw Error("should not be called")
    }

    override fun buttonPressed(e: ActionEvent?, action: GanttEventListener.Actions?) {
      throw Error("should not be called")
    }

    override fun selectionEvent() {
      throw Error("should not be called")
    }

    override fun transitionDuration(duration: Int) {
      throw Error("should not be called")
    }

    override fun motionLayoutAccess(cmd: Int, type: String?, `in`: FloatArray?, inLength: Int, out: FloatArray?, outLength: Int) {
      throw Error("should not be called")
    }

    override fun onInit(commands: GanttCommands?) {
      throw Error("should not be called")
    }

    fun select(tag: SmartPsiElementPointer<XmlTag>?, component: NlComponent?) {
      this.tag = tag
      val list = if (component != null) listOf(component) else emptyList()
      listeners.forEach { it.selectionChanged(this, list) }
    }

    override fun getSelectedAccessory(): Any? {
      return tag
    }

    val listenerCount: Int
      get() = listeners.size

    override fun addListener(listener: AccessorySelectionListener) {
      listeners.add(listener)
    }

    override fun removeListener(listener: AccessorySelectionListener) {
      listeners.remove(listener)
    }
  }
}
