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
package com.android.tools.idea.layoutinspector.pipeline.appinspection

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import layoutinspector.view.inspection.LayoutInspectorViewProtocol as ViewProtocol

private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)

@RunsInEdt
class AppInspectionPropertiesProviderTest {
  // Hand-crafted state loosely based on new basic activity app. Real data would look a lot more scattered.
  class FakeInspectorState(private val viewInspector: FakeViewLayoutInspector) {
    private val strings = listOf(
      // layout strings
      ViewString(1, "androidx.constraintlayout.widget"),
      ViewString(2, "ConstraintLayout"),
      ViewString(3, "androidx.fragment.app"),
      ViewString(4, "FragmentContainerView"),
      ViewString(5, "com.google.android.material.textview"),
      ViewString(6, "MaterialTextView"),
      ViewString(7, "com.google.android.material.button"),
      ViewString(8, "MaterialButton"),
      ViewString(9, "com.google.android.material.floatingactionbutton"),
      ViewString(10, "FloatingActionButton"),
      ViewString(11, "android.view"),
      ViewString(12, "View"),

      // property names
      // TODO(b/177231212): Test remaining property types
      ViewString(101, "text"), // STRING
      ViewString(102, "clickable"), // BOOLEAN
      // placeholder for BYTE property
      // placeholder for CHAR property
      // placeholder for DOUBLE property
      ViewString(106, "alpha"), // FLOAT
      // placeholder for INT16 property
      ViewString(108, "minWidth"), // INT32
      // placeholder for INT64 property
      // placeholder for OBJECT property
      ViewString(111, "background"), // COLOR
      ViewString(112, "gravity"), // GRAVITY
      ViewString(113, "orientation"), // INT_ENUM
      ViewString(114, "imeOptions"), // INT_FLAG
      ViewString(115, "id"), // RESOURCE
      ViewString(116, "src"), // DRAWABLE
      // placeholder for ANIM property
      ViewString(118, "stateListAnimator"), // ANIMATOR
      // placeholder for INTERPOLATOR property
      // placeholder for DIMENSION property

      // property values
      ViewString(201, "Next"),
      ViewString(202, "top"),
      ViewString(203, "start"),
      ViewString(204, "normal"),
      ViewString(205, "actionUnspecified"),
      ViewString(206, "id"),
      ViewString(207, "layout"),
      ViewString(208, "style"),
      ViewString(209, "android"),
      ViewString(210, "com.example"),
      ViewString(211, "fab"),
      ViewString(212, "activity_main"),
      ViewString(213, "fragment_first"),
      ViewString(214, "Widget.MaterialComponents.FloatingActionButton"),
      ViewString(215, "Widget.Design.FloatingActionButton"),
      ViewString(216, "Widget.MaterialComponents.Button"),
      ViewString(217, "Widget.AppCompat.Button"),
      ViewString(218, "Base.Widget.AppCompat.Button"),
      ViewString(219, "Widget.Material.Button"),
      ViewString(220, "vertical"),
    )

    private val layoutTrees = listOf(
      ViewNode {
        id = 1
        packageName = 1
        className = 2
        ViewNode {
          id = 2
          packageName = 3
          className = 4
          ViewNode {
            id = 3
            packageName = 5
            className = 6
          }
          ViewNode {
            id = 4
            packageName = 7
            className = 8
          }
          ViewNode {
            id = 5
            packageName = 9
            className = 10
          }
        }
      },
      ViewNode {
        id = 101
        packageName = 11
        className = 12
      }
    )

    private val propertyGroups = mutableMapOf<Long, List<ViewProtocol.PropertyGroup>>().apply {
      this[layoutTrees[0].id] = listOf(
        PropertyGroup {
          viewId = 3
          Property {
            name = 101
            type = ViewProtocol.Property.Type.STRING
            int32Value = 201
          }
          Property {
            name = 102
            type = ViewProtocol.Property.Type.BOOLEAN
            int32Value = 1
          }
          Property {
            name = 106
            type = ViewProtocol.Property.Type.FLOAT
            floatValue = 1.0f
          }
        },
        PropertyGroup {
          viewId = 4
          Property {
            name = 108
            type = ViewProtocol.Property.Type.INT32
            int32Value = 200
          }
          Property {
            name = 111
            type = ViewProtocol.Property.Type.COLOR
            int32Value = -13172557
          }
          Property {
            name = 112
            type = ViewProtocol.Property.Type.GRAVITY
            flagValueBuilder.addAllFlag(listOf(202, 203))
          }
          Property {
            name = 113
            type = ViewProtocol.Property.Type.INT_ENUM
            int32Value = 220
          }
        },
        PropertyGroup {
          viewId = 5
          Property {
            name = 114
            type = ViewProtocol.Property.Type.INT_FLAG
            flagValueBuilder.addAllFlag(listOf(204, 205))
          }
          Property {
            name = 115
            type = ViewProtocol.Property.Type.RESOURCE
            source = Resource(207, 206, 212)
            addAllResolutionStack(listOf(
              Resource(207, 210, 212),
              Resource(208, 210, 214),
              Resource(208, 210, 215),
            ))
            resourceValue = Resource(206, 210, 211)
          }
          Property {
            name = 116
            type = ViewProtocol.Property.Type.DRAWABLE
            source = Resource(208, 210, 216)
            addAllResolutionStack(listOf(
              Resource(207, 210, 213),
              Resource(208, 210, 216),
              Resource(208, 210, 217),
              Resource(208, 210, 218),
              Resource(208, 209, 219),
            ))
            int32Value = 141
          }
          Property {
            name = 118
            type = ViewProtocol.Property.Type.ANIMATOR
            source = Resource(208, 210, 216)
            addAllResolutionStack(listOf(
              Resource(207, 210, 213),
              Resource(208, 210, 216),
              Resource(208, 210, 217),
              Resource(208, 210, 218),
              Resource(208, 209, 219),
            ))
            int32Value = 146
          }
        })
      // As tests don't need them, just skip defining properties for anything in the second layout tree
      this[layoutTrees[1].id] = emptyList()
    }

    /**
     * Map of "view ID" to number of times properties were requested for it.
     *
     * This is useful for verifying that data is being cached to avoid subsequent fetches.
     */
    private val getPropertiesRequestCount = mutableMapOf<Long, Int>()

    init {
      viewInspector.addCommandListener(ViewProtocol.Command.SpecializedCase.START_FETCH_COMMAND) { command ->
        // Send all root IDs, which needs to happen before we sent our first layout capture
        viewInspector.connection.sendEvent {
          rootsEventBuilder.apply {
            layoutTrees.forEach { tree -> addIds(tree.id) }
          }
        }

        layoutTrees.forEach { tree -> triggerLayoutCapture(rootId = tree.id, isLastCapture = !command.startFetchCommand.continuous) }
      }

      viewInspector.addCommandInterceptor(ViewProtocol.Command.SpecializedCase.GET_PROPERTIES_COMMAND) { command ->
        getPropertiesRequestCount.compute(command.getPropertiesCommand.viewId) { _, prev -> (prev ?: 0) + 1 }

        val propertyGroup = propertyGroups[command.getPropertiesCommand.rootViewId]!!
                              .firstOrNull { it.viewId == command.getPropertiesCommand.viewId }
                            // As this test data is hand defined, treat undefined view IDs as views with an empty properties group
                            ?: PropertyGroup {
                              viewId = command.getPropertiesCommand.viewId
                            }
        ViewProtocol.Response.newBuilder().setGetPropertiesResponse(
          ViewProtocol.GetPropertiesResponse.newBuilder().apply {
            addAllStrings(strings)
            this.propertyGroup = propertyGroup
          }
        ).build()
      }
    }

    fun getPropertiesRequestCountFor(viewId: Long): Int {
      return getPropertiesRequestCount[viewId] ?: 0
    }

    /**
     * The real inspector triggers occasional captures as the UI changes, but for tests, we'll
     * expose this method so it can be triggered manually.
     */
    fun triggerLayoutCapture(rootId: Long, isLastCapture: Boolean = false) {
      val rootView = layoutTrees.first { it.id == rootId }
      viewInspector.connection.sendEvent {
        layoutEventBuilder.apply {
          addAllStrings(strings)
          this.rootView = rootView
        }
      }
      if (isLastCapture) {
        viewInspector.connection.sendEvent {
          propertiesEventBuilder.apply {
            this.rootId = rootId
            addAllStrings(strings)
            addAllPropertyGroups(propertyGroups[rootId])
          }
        }
      }
    }
  }

  private val projectRule = AndroidProjectRule.withSdk()
  private val inspectionRule = AppInspectionInspectorRule()
  private val inspectorRule = LayoutInspectorRule(inspectionRule.createInspectorClientProvider(), projectRule) {
    listOf(MODERN_PROCESS.name)
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(inspectionRule).around(inspectorRule)

  private lateinit var inspectorState: FakeInspectorState

  @Before
  fun setUp() {
    val propertiesComponent = PropertiesComponentMock()
    projectRule.replaceService(PropertiesComponent::class.java, propertiesComponent)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS

    inspectorState = FakeInspectorState(inspectionRule.viewInspector)
  }

  @Test
  fun canQueryPropertiesForViews() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = CountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await()

    val provider = inspectorRule.inspectorClient.provider
    var lastResult: ProviderResult? = null
    provider.resultListeners.add { _, view, table ->
      lastResult = ProviderResult(view, table, inspectorRule.inspectorModel)
    }

    inspectorRule.inspectorModel[3]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = lastResult!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("text", PropertyType.STRING, "Next")
        assertProperty("clickable", PropertyType.BOOLEAN, "true")
        assertProperty("alpha", PropertyType.FLOAT, "1.0")
      }
    }

    inspectorRule.inspectorModel[4]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = lastResult!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("minWidth", PropertyType.INT32, "200")
        assertProperty("background", PropertyType.COLOR, "#3700B3")
        assertProperty("gravity", PropertyType.GRAVITY, "top|start")
        assertProperty("orientation", PropertyType.INT_ENUM, "vertical")
      }
    }

    inspectorRule.inspectorModel[5]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = lastResult!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("imeOptions", PropertyType.INT_FLAG, "normal|actionUnspecified")
        assertProperty("id", PropertyType.RESOURCE, "@com.example:id/fab")
        assertProperty("src", PropertyType.DRAWABLE, "@drawable/?")
        assertProperty("stateListAnimator", PropertyType.ANIMATOR, "@animator/?")
      }
    }
  }

  @Test
  fun syntheticPropertiesAlwaysAdded() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = CountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await()

    val provider = inspectorRule.inspectorClient.provider
    var lastResult: ProviderResult? = null
    provider.resultListeners.add { _, view, table ->
      lastResult = ProviderResult(view, table, inspectorRule.inspectorModel)
    }

    inspectorRule.inspectorModel[1]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = lastResult!!

      // Technically the view with ID #1 has no properties, but synthetic properties are always added
      result.table.run {
        assertProperty("name", PropertyType.STRING, "androidx.constraintlayout.widget.ConstraintLayout",
                       PropertySection.VIEW, NAMESPACE_INTERNAL)
        assertProperty("x", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("y", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("width", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("height", PropertyType.DIMENSION, "0px", PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
      }
    }
  }

  @Test
  fun propertiesAreCachedUntilNextLayoutEvent() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedSignal = ArrayBlockingQueue<Unit>(2) // We should get no more than two updates before continuing
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedSignal.offer(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedSignal.take() // Event triggered by tree #1
    modelUpdatedSignal.take() // Event triggered by tree #2

    val provider = inspectorRule.inspectorClient.provider

    // Get properties for views from the two different layout trees so we can verify that the cache of each
    // layout tree is maintained separately.
    val nodeInTree1 = inspectorRule.inspectorModel[3]!!
    val nodeInTree2 = inspectorRule.inspectorModel[101]!!

    // Tree #1
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(0)

    provider.requestProperties(nodeInTree1).get() // First fetch, not cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree1).get() // Should be cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree1).get() // Still cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(1)

    // Tree #2
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(0)

    provider.requestProperties(nodeInTree2).get() // Not cached yet
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)

    provider.requestProperties(nodeInTree2).get() // Cached now
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)

    // Trigger a fake layout update in *just* the first tree, which should reset just its cache and
    // not that for the second tree
    inspectorState.triggerLayoutCapture(rootId = 1)
    modelUpdatedSignal.take()

    provider.requestProperties(nodeInTree1).get() // First fetch after layout event, not cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(2)

    provider.requestProperties(nodeInTree1).get() // Tree #1 node should be cached again
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree1.drawId)).isEqualTo(2)

    provider.requestProperties(nodeInTree2).get() // Tree #2 node should have remained cached
    assertThat(inspectorState.getPropertiesRequestCountFor(nodeInTree2.drawId)).isEqualTo(1)
  }

  @Test
  fun snapshotModeSendsAllPropertiesAtOnce() {
    InspectorClientSettings.isCapturingModeOn = false // i.e. snapshot mode

    val modelUpdatedLatch = CountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await()

    // Calling "get properties" at this point should work without talking to the device because everything should
    // be cached now.

    val provider = inspectorRule.inspectorClient.provider
    var lastResult: ProviderResult? = null
    provider.resultListeners.add { _, view, table ->
      lastResult = ProviderResult(view, table, inspectorRule.inspectorModel)
    }

    for (id in listOf(3L, 4L, 5L)) {
      assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      inspectorRule.inspectorModel[id]!!.let { targetNode ->
        provider.requestProperties(targetNode).get()
        assertThat(lastResult!!.view).isSameAs(targetNode)
        assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      }
    }
  }

  // TODO(b/177231212): Test Composables

  private fun PropertiesTable<InspectorPropertyItem>.assertProperty(
    name: String,
    type: PropertyType,
    value: String,
    group: PropertySection = PropertySection.DEFAULT,
    namespace: String = ANDROID_URI,
  ) {
    val property = this[namespace, name]
    assertThat(property.name).isEqualTo(name)
    assertThat(property.attrName).isEqualTo(name)
    assertThat(property.namespace).isEqualTo(namespace)
    assertThat(property.type).isEqualTo(type)
    assertThat(property.value).isEqualTo(value)
    assertThat(property.group).isEqualTo(group)
  }

  /**
   * Helper class to receive a properties provider result.
   */
  private class ProviderResult(
    val view: ViewNode,
    val table: PropertiesTable<InspectorPropertyItem>,
    val model: InspectorModel) {

    init {
      table.values.forEach { property ->
        assertThat(property.viewId).isEqualTo(view.drawId)
        assertThat(property.lookup).isSameAs(model)
      }
    }
  }
}
