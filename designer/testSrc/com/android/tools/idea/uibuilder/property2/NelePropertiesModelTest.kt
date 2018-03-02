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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants
import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.property2.api.PropertiesModelListener
import com.android.tools.idea.uibuilder.LayoutTestCase
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.google.common.truth.Truth.assertThat
import com.intellij.util.ui.UIUtil
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class NelePropertiesModelTest: LayoutTestCase() {

  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    val listener = mock(PropertiesModelListener::class.java)
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.updateQueue.isPassThrough = true
    model.addListener(listener)

    // test
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated()
  }

  fun testPropertiesGeneratedEventWhenSwitchingDesignSurface() {
    // setup
    val listener = mock(PropertiesModelListener::class.java)
    val model = createModel()
    val nlModelA = createNlModel(IMAGE_VIEW)
    val nlModelB = createNlModel(TEXT_VIEW)
    val textView = nlModelB.find(TEXT_VIEW)!!
    nlModelB.surface.selectionModel.setSelection(listOf(textView))
    model.surface = nlModelA.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    // test
    model.surface = nlModelB.surface
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated()
    assertThat(model.properties[ANDROID_URI, ATTR_TEXT].components[0].model).isEqualTo(nlModelB)
  }

  fun testPropertiesGeneratedEventAfterSelectionChange() {
    // setup
    val listener = mock(PropertiesModelListener::class.java)
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)
    val textView = nlModel.find(TEXT_VIEW)!!

    // test
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilEventsProcessed(model)
    verify(listener).propertiesGenerated()
  }

  fun testPropertiesChangedEventAfterRendering() {
    // setup
    val listener = mock(PropertiesModelListener::class.java)
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilEventsProcessed(model)
    model.addListener(listener)

    // test emulate the completion of rendering in the current scene
    val manager = nlModel.surface.currentSceneView?.sceneManager!!
    val method = LayoutlibSceneManager::class.java.getDeclaredMethod("fireRenderListeners")
    method.isAccessible = true
    method.invoke(manager)
    UIUtil.dispatchAllInvocationEvents()

    verify(listener).propertyValuesChanged()
  }

  private fun createNlModel(tag: String): SyncNlModel {
    val builder = model(
        "linear.xml",
        component(SdkConstants.LINEAR_LAYOUT)
          .withBounds(0, 0, 1000, 1500)
          .id("@id/linear")
          .matchParentWidth()
          .matchParentHeight()
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT, "com.example.MyActivity")
          .children(
              component(tag)
                .withBounds(100, 100, 100, 100)
                .id("@id/" + tag)
                .width("wrap_content")
                .height("wrap_content")
          )
    )
    return builder.build()
  }

  // The production code passes the property creation to a queue.
  // This code changes the queue to do a pass through during this test.
  private fun createModel(): NelePropertiesModel {
    val model = NelePropertiesModel(testRootDisposable, myFacet)
    model.updateQueue.isPassThrough = true
    return model
  }

  // Ugly hack:
  // The production code is executing the properties creation on a separate thread.
  // This code makes sure that the last scheduled worker thread is finished,
  // then we also need to wait for events on the UI thread.
  private fun waitUntilEventsProcessed(model: NelePropertiesModel) {
    model.lastSelectionUpdate?.get()
    UIUtil.dispatchAllInvocationEvents()
  }
}
