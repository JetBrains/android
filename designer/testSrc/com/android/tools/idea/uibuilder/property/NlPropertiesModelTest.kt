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
package com.android.tools.idea.uibuilder.property

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TEXT_APPEARANCE
import com.android.SdkConstants.BUTTON
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.testutils.waitForCondition
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil.model
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.component
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
import com.android.tools.property.panel.api.PropertiesModel
import com.android.tools.property.panel.api.PropertiesModelListener
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.update.MergingUpdateQueue
import java.util.concurrent.TimeUnit
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

@RunsInEdt
class NlPropertiesModelTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val chain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  @Test
  fun testPropertiesGeneratedEventWhenDesignSurfaceIsHookedUp() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.addListener(listener)

    // test
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    verify(listener).propertiesGenerated(model)
  }

  @Test
  fun testPropertiesGeneratedEventWhenSwitchingDesignSurface() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModelA = createNlModel(IMAGE_VIEW)
    val nlModelB = createNlModel(TEXT_VIEW)
    val textView = nlModelB.treeReader.find(TEXT_VIEW)!!
    nlModelB.surface.selectionModel.setSelection(listOf(textView))
    model.surface = nlModelA.surface
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)

    // test
    model.surface = nlModelB.surface
    waitUntilLastSelectionUpdateCompleted(model)
    verify(listener).propertiesGenerated(model)
    assertThat(model.properties[ANDROID_URI, ATTR_TEXT].components[0].model).isEqualTo(nlModelB)
  }

  @Test
  fun testPropertiesGeneratedEventAfterSelectionChange() {
    // setup
    @Suppress("UNCHECKED_CAST") val listener = TimingPropertiesModelListener()
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!

    // test
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    model.firePropertyValueChangeIfNeeded()
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(listener.wasValuePropertyGeneratedCalledBeforeValueChanged).isTrue()
  }

  @Test
  fun testPropertiesGeneratedEventBeforeValueChangedEventAfterSelectionChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!

    // test
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)
    verify(listener).propertiesGenerated(model)
  }

  @Test
  fun testPropertyValuesChangedEventAfterModelChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)

    nlModel.surface
      .getSceneManager(nlModel)!!
      .resourcesChanged(ImmutableSet.of(ResourceNotificationManager.Reason.EDIT))
    nlModel.updateQueue.flush()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(listener).propertyValuesChanged(model)
  }

  @Test
  fun testPropertyValuesChangedEventAfterLiveModelChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)

    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)

    nlModel.notifyLiveUpdate()
    nlModel.updateQueue.flush()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(listener, atLeast(1)).propertyValuesChanged(model)
  }

  @Test
  fun testPropertyValuesChangedEventAfterLiveComponentChange() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)
    model.addListener(listener)

    textView.fireLiveChangeEvent()
    nlModel.updateQueue.flush()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(listener).propertyValuesChanged(model)
  }

  @Test
  fun testAccessToDefaultPropertiesViaModel() {
    // setup
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    val view = nlModel.surface.focusedSceneView!!
    val manager = view.sceneManager as SyncLayoutlibSceneManager
    val property =
      NlPropertyItem(
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        null,
        "",
        "",
        model,
        listOf(textView),
      )
    manager.putDefaultPropertyValue(
      textView,
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    model.surface = nlModel.surface
    model.setResolver(nlModel.configuration.resourceResolver)
    waitUntilLastSelectionUpdateCompleted(model)

    // test
    assertThat(model.provideDefaultValue(property)).isEqualTo("@android:style/TextAppearance.Small")
  }

  @Test
  fun testPropertyValuesChangesAfterRendering() {
    // setup
    @Suppress("UNCHECKED_CAST")
    val listener =
      mock(PropertiesModelListener::class.java) as PropertiesModelListener<NlPropertyItem>
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    val view = nlModel.surface.focusedSceneView!!
    val manager = view.sceneManager as SyncLayoutlibSceneManager
    val property =
      NlPropertyItem(
        ANDROID_URI,
        ATTR_TEXT_APPEARANCE,
        NlPropertyType.STYLE,
        null,
        "",
        "",
        model,
        listOf(textView),
      )
    manager.putDefaultPropertyValue(
      textView,
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "?attr/textAppearanceSmall",
    )
    model.surface = nlModel.surface
    model.setResolver(nlModel.configuration.resourceResolver)
    waitUntilLastSelectionUpdateCompleted(model)
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.provideDefaultValue(property)).isEqualTo("@android:style/TextAppearance.Small")
    model.addListener(listener)

    // Value changed should not be reported if the default values are unchanged
    manager.requestRenderAsync()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(listener, never()).propertyValuesChanged(model)

    // Value changed notification is expected since the default values have changed
    manager.putDefaultPropertyValue(
      textView,
      ResourceNamespace.ANDROID,
      ATTR_TEXT_APPEARANCE,
      "@android:style/TextAppearance.Large",
    )
    manager.requestRenderAsync()
    nlModel.updateQueue.flush()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    verify(listener).propertyValuesChanged(model)
  }

  @Test
  fun testListenersAreConcurrentModificationSafe() {
    // Make sure that ConcurrentModificationException is NOT generated from the code below:
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)
    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)

    val listener = RecursiveValueChangedListener()
    model.addListener(listener)
    model.firePropertiesGenerated()
    model.firePropertyValueChangeIfNeeded()
    assertThat(listener.called).isEqualTo(2)
  }

  @Test
  fun testDoNotUpdateWhenOnlySecondarySelectionIsChanged() {
    val model = createModel()
    val nlModel = createNlModel(TEXT_VIEW, BUTTON)
    val textView = nlModel.treeReader.find(TEXT_VIEW)!!
    val button = nlModel.treeReader.find(BUTTON)!!
    model.surface = nlModel.surface
    waitUntilLastSelectionUpdateCompleted(model)

    nlModel.surface.selectionModel.setSelection(listOf(textView))
    waitUntilLastSelectionUpdateCompleted(model)

    val lastUpdateCount = model.updateCount

    nlModel.surface.selectionModel.setSecondarySelection(textView, null)
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(textView, null)
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(textView, Any())
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(textView, Any())
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(textView, null)
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(button, null)
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isNotEqualTo(lastUpdateCount)

    nlModel.surface.selectionModel.setSecondarySelection(textView, Any())
    waitUntilLastSelectionUpdateCompleted(model)
    assertThat(model.updateCount).isNotEqualTo(lastUpdateCount)
  }

  /**
   * Regression test for b/247726011. When sharing one [MergingUpdateQueue], different
   * [NlPropertiesModel] should still schedule one update per model. When sharing a queue, the
   * updates would be folded into the one incorrectly.
   */
  @Test
  fun testMultipleModelsSharingQueue() {
    // setup
    var generated = 0
    val listener =
      object : PropertiesModelListener<NlPropertyItem> {
        override fun propertiesGenerated(model: PropertiesModel<NlPropertyItem>) {
          generated++
        }
      }

    val facet = AndroidFacet.getInstance(projectRule.module)!!
    val testRootDisposable = projectRule.testRootDisposable
    val queue = MergingUpdateQueue("MQ", 100, false, null, testRootDisposable)
    val model1 = NlPropertiesModel(testRootDisposable, facet, queue)
    val model2 = NlPropertiesModel(testRootDisposable, facet, queue)
    val nlModel = createNlModel(TEXT_VIEW)
    model1.addListener(listener)
    model2.addListener(listener)

    // test
    model1.surface = nlModel.surface
    model2.surface = nlModel.surface
    queue.resume()
    waitUntilLastSelectionUpdateCompleted(model1)
    waitUntilLastSelectionUpdateCompleted(model2)
    assertThat(generated).isEqualTo(2)
  }

  private fun createNlModel(vararg tag: String): SyncNlModel {
    val builder =
      model(
        projectRule,
        SdkConstants.FD_RES_LAYOUT,
        "linear.xml",
        component(LINEAR_LAYOUT)
          .withBounds(0, 0, 1000, 1500)
          .id("@id/linear")
          .matchParentWidth()
          .matchParentHeight()
          .withAttribute(TOOLS_URI, ATTR_CONTEXT, "com.example.MyActivity")
          .children(
            *tag
              .map {
                component(it)
                  .withBounds(100, 100, 100, 100)
                  .id("@id/$it")
                  .width("wrap_content")
                  .height("wrap_content")
              }
              .toTypedArray()
          ),
      )
    return builder.build()
  }

  // The production code passes the property creation to a queue.
  // This code changes the queue to do a pass through during this test.
  private fun createModel(): NlPropertiesModel {
    val queue = MergingUpdateQueue("MQ", 100, true, null, projectRule.testRootDisposable)
    queue.isPassThrough = true
    return NlPropertiesModel(
      projectRule.testRootDisposable,
      AndroidFacet.getInstance(projectRule.module)!!,
      queue,
    )
  }

  private class RecursiveValueChangedListener : PropertiesModelListener<NlPropertyItem> {
    var called = 0

    override fun propertiesGenerated(model: PropertiesModel<NlPropertyItem>) {
      model.addListener(RecursiveValueChangedListener())
      called++
    }

    override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
      model.addListener(RecursiveValueChangedListener())
      called++
    }
  }

  private class TimingPropertiesModelListener : PropertiesModelListener<NlPropertyItem> {
    var generatedCalled = 0L
    var valuesChangedCalled = 0L

    override fun propertiesGenerated(model: PropertiesModel<NlPropertyItem>) {
      if (generatedCalled == 0L) {
        generatedCalled = System.currentTimeMillis()
      }
    }

    override fun propertyValuesChanged(model: PropertiesModel<NlPropertyItem>) {
      if (valuesChangedCalled == 0L) {
        valuesChangedCalled = System.currentTimeMillis()
      }
    }

    val wasValuePropertyGeneratedCalledBeforeValueChanged: Boolean
      get() {
        if (generatedCalled == 0L) {
          return false
        }
        if (valuesChangedCalled == 0L) {
          return true
        }
        return generatedCalled < valuesChangedCalled
      }
  }

  companion object {

    // Ugly hack:
    // The production code is executing the properties creation on a separate thread.
    // This code makes sure that the last scheduled worker thread is finished,
    // then we also need to wait for events on the UI thread.
    fun waitUntilLastSelectionUpdateCompleted(model: NlPropertiesModel) {
      model.updateQueue.flush()
      if (ApplicationManager.getApplication().isDispatchThread) {
        PlatformTestUtil.waitWithEventsDispatching(
          "Model was not updated",
          { model.lastUpdateCompleted },
          10,
        )
      } else {
        waitForCondition(10, TimeUnit.SECONDS) { model.lastUpdateCompleted }
      }
    }
  }
}
