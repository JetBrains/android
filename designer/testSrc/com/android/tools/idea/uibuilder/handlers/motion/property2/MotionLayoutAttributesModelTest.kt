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
import com.android.tools.property.panel.api.PropertiesModelListener
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface
import com.android.tools.idea.uibuilder.api.AccessorySelectionListener
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.TransitionConstraintSetStart
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionDesignSurfaceEdits
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel
import com.android.tools.idea.uibuilder.property2.NelePropertiesModelTest.Companion.waitUntilEventsProcessed
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.surface.AccessoryPanel
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyZeroInteractions
import javax.swing.JPanel

class MotionLayoutAttributesModelTest: LayoutTestCase() {

  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    val nlModel = createNlModel()
    val motionPanel = retrieveMotionAccessoryPanel(nlModel)
    model.addListener(listener)
    model.updateQueue.isPassThrough = true

    // test
    model.surface = nlModel.surface
    assertThat(motionPanel.listenerCount).isEqualTo(1)
    verifyZeroInteractions(listener)

    val textView = nlModel.components[0].getChild(0)
    val scene = MotionSceneModel.parse(textView, project, file, xmlFile)
    motionPanel.select(scene.getTransitionTag(0).tag, textView)
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
  }

  fun testPropertiesGeneratedEventWhenSwitchingDesignSurface() {
    // setup
    myFixture.copyFileToProject("motion/attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    model.addListener(listener)
    val nlModelA = createNlModel()
    val nlModelB = createNlModel()
    val motionPanelA = retrieveMotionAccessoryPanel(nlModelA)
    val motionPanelB = retrieveMotionAccessoryPanel(nlModelB)
    val textViewA = nlModelA.find("widget")!!
    val textViewB = nlModelB.find("widget")!!
    val scene = MotionSceneModel.parse(textViewB, project, file, xmlFile)

    // test
    model.surface = nlModelA.surface
    model.surface = nlModelB.surface
    nlModelA.surface.selectionModel.setSelection(listOf(textViewA))
    motionPanelA.select(scene.getTransitionTag(0).tag, textViewA)
    waitUntilEventsProcessed(model)
    verifyZeroInteractions(listener)

    nlModelB.surface.selectionModel.setSelection(listOf(textViewB))
    motionPanelB.select(scene.getTransitionTag(0).tag, textViewB)
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
    assertThat(model.properties[SdkConstants.AUTO_URI, TransitionConstraintSetStart].components[0].model).isEqualTo(nlModelB)
  }

  fun testConstraintSet() {
    // setup
    myFixture.copyFileToProject("motion/attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject("motion/scene.xml", "res/xml/scene.xml")
    val xmlFile = AndroidPsiUtils.getPsiFileSafely(project, file) as XmlFile

    @Suppress("UNCHECKED_CAST")
    val listener = mock(PropertiesModelListener::class.java) as PropertiesModelListener<NelePropertyItem>
    val model = MotionLayoutAttributesModel(testRootDisposable, myFacet)
    val nlModel = createNlModel()
    val textView = nlModel.components[0].getChild(0)
    val timeline = retrieveMotionAccessoryPanel(nlModel)
    model.addListener(listener)
    val scene = MotionSceneModel.parse(textView, project, file, xmlFile)
    model.surface = nlModel.surface
    nlModel.surface.selectionModel.setSelection(nlModel.components)

    // test
    timeline.select(scene.startConstraintSet.getConstraintView("widget")!!.tag, textView)
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated(model)
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
    val timeline = MotionAccessoryPanel()
    val surface = model.surface as NlDesignSurface
    `when`(surface.accessoryPanel).thenReturn(panel)
    `when`(panel.currentPanel).thenReturn(timeline)
    return model
  }

  private fun retrieveMotionAccessoryPanel(model: SyncNlModel): MotionAccessoryPanel {
    val surface = model.surface as NlDesignSurface
    return surface.accessoryPanel.currentPanel as MotionAccessoryPanel
  }

  private class MotionAccessoryPanel: AccessoryPanelInterface, MotionDesignSurfaceEdits {
    val listeners = mutableListOf<AccessorySelectionListener>()
    var type: MotionEditorSelector.Type? = null
    var tags: Array<MTag>? = null

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

    override fun handlesWriteForComponent(id: String?): Boolean {
      throw Error("should not be called")
    }

    override fun getSelectedConstraint(): SmartPsiElementPointer<XmlTag> {
      throw Error("should not be called")
    }

    override fun getSelectedConstraintSet(): String {
      throw Error("should not be called")
    }

    override fun getTransitionFile(component: NlComponent?): XmlFile {
      throw Error("should not be called")
    }

    override fun getConstraintSet(file: XmlFile?, s: String?): XmlTag {
      throw Error("should not be called")
    }

    override fun getConstrainView(set: XmlTag?, id: String?): XmlTag {
      throw Error("should not be called")
    }

    override fun getKeyframes(file: XmlFile?, id: String?): MutableList<XmlTag> {
      throw Error("should not be called")
    }

    fun select(tagPointer: SmartPsiElementPointer<XmlTag>?, component: NlComponent?) {
      type = null
      tags = null
      val xmlTag = tagPointer?.element ?: return
      val motionTag = MotionSceneTag(xmlTag, null)

      type = mapTagNameToType(motionTag.tagName)
      tags = arrayOf(motionTag)

      val list = if (component != null) listOf(component) else emptyList()
      listeners.forEach { it.selectionChanged(this, list) }
    }

    override fun getSelectedAccessoryType(): Any? {
      return type
    }

    override fun getSelectedAccessory(): Any? {
      return tags
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
