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
import com.android.SdkConstants.URI_PREFIX
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.TestUtils
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.appinspection.test.DEFAULT_TEST_INSPECTION_STREAM
import com.android.tools.idea.layoutinspector.LayoutInspectorRule
import com.android.tools.idea.layoutinspector.MODERN_DEVICE
import com.android.tools.idea.layoutinspector.createProcess
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ComposeParametersCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterGroupItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ParameterItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.ShowMoreElementsItem
import com.android.tools.idea.layoutinspector.pipeline.appinspection.compose.parameterNamespaceOf
import com.android.tools.idea.layoutinspector.properties.DimensionUnits
import com.android.tools.idea.layoutinspector.properties.InspectorGroupPropertyItem
import com.android.tools.idea.layoutinspector.properties.InspectorPropertyItem
import com.android.tools.idea.layoutinspector.properties.NAMESPACE_INTERNAL
import com.android.tools.idea.layoutinspector.properties.PropertiesSettings
import com.android.tools.idea.layoutinspector.properties.PropertySection
import com.android.tools.idea.layoutinspector.properties.PropertyType
import com.android.tools.idea.layoutinspector.util.ReportingCountDownLatch
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.TestAndroidModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.property.panel.api.PropertiesTable
import com.android.tools.property.ptable.PTable
import com.android.tools.property.ptable.PTableGroupItem
import com.android.tools.property.ptable.PTableGroupModification
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.psi.PsiClass
import com.intellij.testFramework.DisposableRule
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/** Timeout used in this test. While debugging, you may want to extend the timeout */
private const val TIMEOUT = 3L
private val TIMEOUT_UNIT = TimeUnit.SECONDS
private val MODERN_PROCESS = MODERN_DEVICE.createProcess(streamId = DEFAULT_TEST_INSPECTION_STREAM.streamId)
private val PARAM_NS = parameterNamespaceOf(PropertySection.PARAMETERS)
private const val APP_NAMESPACE = "${URI_PREFIX}com.example"

class AppInspectionPropertiesProviderTest {
  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.withSdk()
  private val inspectionRule = AppInspectionInspectorRule(disposableRule.disposable, projectRule)
  private val inspectorRule = LayoutInspectorRule(listOf(inspectionRule.createInspectorClientProvider()), projectRule) {
    it.name == MODERN_PROCESS.name
  }

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(inspectionRule).around(inspectorRule).around(disposableRule)!!

  private lateinit var inspectorState: FakeInspectorState

  @Before
  fun setUp() {
    val propertiesComponent = PropertiesComponentMock()
    projectRule.replaceService(PropertiesComponent::class.java, propertiesComponent)
    PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS

    // Check that generated getComposablesCommands has the `extractAllParameters` set in snapshot mode.
    inspectionRule.composeInspector.listenWhen({it.hasGetComposablesCommand()}) { command ->
      assertThat(command.getComposablesCommand.extractAllParameters).isEqualTo(!InspectorClientSettings.isCapturingModeOn)
    }

    inspectorState = FakeInspectorState(inspectionRule.viewInspector, inspectionRule.composeInspector)
    inspectorState.createAllResponses()
    inspectorRule.attachDevice(MODERN_DEVICE)

    val fixture = projectRule.fixture
    fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/layout-inspector/testData/resource").toString()
    fixture.copyFileToProject("res/layout/activity_main.xml")
    fixture.copyFileToProject("res/values/styles.xml")
  }

  @Test
  fun canQueryPropertiesForViewsWithResourceResolver() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[3]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("text", PropertyType.STRING, "Next")
        assertProperty("clickable", PropertyType.BOOLEAN, "true")
        assertProperty("alpha", PropertyType.FLOAT, "1.0")
      }
    }

    inspectorRule.inspectorModel[4]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
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
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("imeOptions", PropertyType.INT_FLAG, "normal|actionUnspecified")
        assertProperty("id", PropertyType.RESOURCE, "@com.example:id/fab", source = layout("activity_main"))
        assertProperty("src", PropertyType.DRAWABLE, "@drawable/?", source = layout("activity_main"),
                       classLocation = "android.graphics.drawable.VectorDrawable")
        assertProperty("stateListAnimator", PropertyType.ANIMATOR, "@animator/?", source = layout("activity_main"),
                       resolutionStack = listOf(ResStackItem(style("Widget.Material.Button"), null)),
                       classLocation = "android.animation.StateListAnimator")
      }
    }

    inspectorRule.inspectorModel[8]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("backgroundTint", PropertyType.COLOR, "#4422FF00", namespace = APP_NAMESPACE)
      }
      // Assert that "android:backgroundTint" is omitted
      assertThat(result.table.getByNamespace(ANDROID_URI)).isEmpty()
    }
  }

  @Test
  fun canQueryPropertiesForViewsWithoutResourceResolver() {
    val facet = AndroidFacet.getInstance(projectRule.module)!!
    AndroidModel.set(facet, TestAndroidModel(applicationId = "com.nonmatching.app"))

    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    // The properties should be simple non-expanding properties
    inspectorRule.inspectorModel[5]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertProperty("imeOptions", PropertyType.INT_FLAG, "normal|actionUnspecified")
        assertProperty("id", PropertyType.RESOURCE, "@com.example:id/fab", source = layout("activity_main"))
        assertProperty("src", PropertyType.DRAWABLE, "@drawable/?", source = layout("activity_main"))
        assertProperty("stateListAnimator", PropertyType.ANIMATOR, "@animator/?", source = layout("activity_main"))
      }
    }
  }

  @Test
  fun syntheticPropertiesAlwaysAdded() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[1]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!

      // Technically the view with ID #1 has no properties, but synthetic properties are always added
      result.table.run {
        assertProperty("name", PropertyType.STRING, "androidx.constraintlayout.widget.ConstraintLayout",
                       group = PropertySection.VIEW, namespace = NAMESPACE_INTERNAL)
        assertProperty("x", PropertyType.DIMENSION, "0px", group = PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("y", PropertyType.DIMENSION, "0px", group = PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("width", PropertyType.DIMENSION, "0px", group = PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
        assertProperty("height", PropertyType.DIMENSION, "0px", group = PropertySection.DIMENSION, namespace = NAMESPACE_INTERNAL)
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
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #1
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #2

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
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

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

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Calling "get properties" at this point should work without talking to the device because everything should
    // be cached now.

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    for (id in listOf(3L, 4L, 5L)) {
      assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      inspectorRule.inspectorModel[id]!!.let { targetNode ->
        provider.requestProperties(targetNode).get()
        val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
        assertThat(result.view).isSameAs(targetNode)
        assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      }
    }
  }

  @Test
  fun canQueryParametersForComposables() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    inspectorRule.inspectorModel[-2]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertParameter("text", PropertyType.STRING, "placeholder")
        assertParameter("clickable", PropertyType.BOOLEAN, "true")
        assertParameter("count", PropertyType.INT32, "7", PropertySection.RECOMPOSITIONS)
        assertParameter("skips", PropertyType.INT32, "14", PropertySection.RECOMPOSITIONS)
      }
    }

    inspectorRule.inspectorModel[-3]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        assertParameter("maxLines", PropertyType.INT32, "16")
        assertParameter("color", PropertyType.COLOR, "#3700B3")
      }
    }

    inspectorRule.inspectorModel[-4]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)
      result.table.run {
        PropertiesSettings.dimensionUnits = DimensionUnits.PIXELS
        assertParameter("elevation", PropertyType.DIMENSION_DP, "1.5px")
        assertParameter("fontSize", PropertyType.DIMENSION_SP, "36.0px")
        assertParameter("textSize", PropertyType.DIMENSION_EM, "2.0em")
      }
      result.table.run {
        PropertiesSettings.dimensionUnits = DimensionUnits.DP
        assertParameter("elevation", PropertyType.DIMENSION_DP, "1.0dp")
        assertParameter("fontSize", PropertyType.DIMENSION_SP, "16.0sp")
        assertParameter("textSize", PropertyType.DIMENSION_EM, "2.0em")
      }
    }

    val parameter = inspectorRule.inspectorModel[-5]!!.let { targetNode ->
      provider.requestProperties(targetNode).get()
      val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
      assertThat(result.view).isSameAs(targetNode)

      result.table.run {
        assertParameter("onTextLayout", PropertyType.LAMBDA, "λ")
        assertParameter("dataObject", PropertyType.STRING, "PojoClass")
        val groupItem = this[PARAM_NS, "dataObject"] as ParameterGroupItem
        assertParameter(groupItem.children[0], "stringProperty", PropertyType.STRING, "stringValue")
        assertParameter(groupItem.children[1], "intProperty", PropertyType.INT32, "812")
        assertParameter(groupItem.children[2], "lines", PropertyType.STRING, "MyLineClass")
        assertThat(groupItem.children.size).isEqualTo(3)

        groupItem
      }
    }

    // The 1st element of parameter is a reference to parameter itself.
    // When the 1st sub element is expanded in the UI the child elements should be copied from parameter.
    val propertyExpandedLatch = ReportingCountDownLatch(1)
    propertyExpandedLatch.runInEdt {
      val first = parameter.children.first() as ParameterGroupItem
      assertThat(first.children).isEmpty()
      first.expandWhenPossible { restructured ->
        assertThat(restructured).isTrue()
        assertParameter(first.children[0], "stringProperty", PropertyType.STRING, "stringValue")
        assertParameter(first.children[1], "intProperty", PropertyType.INT32, "812")
        assertParameter(first.children[2], "lines", PropertyType.STRING, "MyLineClass")
        assertThat(first.children.size).isEqualTo(3)
        propertyExpandedLatch.countDown()
      }
    }
    propertyExpandedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // The last element of parameter is a reference to a value that has not been loaded from the agent yet.
    // When the last element is expanded in the UI the child elements will be loaded from the agent.
    val propertyDownloadedLatch = ReportingCountDownLatch(1)
    val last = parameter.children.last() as ParameterGroupItem
    propertyDownloadedLatch.runInEdt {
      assertThat(last.children).isEmpty()
      last.expandWhenPossible { restructured ->
        assertThat(restructured).isTrue()
        assertParameter(last.children[0], "firstLine", PropertyType.STRING, "Hello World")
        assertParameter(last.children[1], "lastLine", PropertyType.STRING, "End of Text")
        assertParameter(last.children[2], "list", PropertyType.ITERABLE, "List[12]")
        assertThat(last.children.size).isEqualTo(3)
        val list = last.children.last() as ParameterGroupItem
        assertParameter(list.children[0], "[0]", PropertyType.STRING, "a")
        assertParameter(list.children[1], "[3]", PropertyType.STRING, "b")
        assertThat(list.children[2]).isInstanceOf(ShowMoreElementsItem::class.java)
        assertThat(list.children[2].index).isEqualTo(4)
        assertThat(list.children.size).isEqualTo(3)
        propertyDownloadedLatch.countDown()
      }
    }
    propertyDownloadedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // The list parameter from the expanded parameter is a List of String where only a part of the elements
    // have been downloaded. Download some more elements (first time).
    val moreListElements1 = ReportingCountDownLatch(1)
    val table1 = spy(PTable.create(mock()))
    val event1: AnActionEvent = mock()
    doAnswer {
      val modification = it.getArgument<PTableGroupModification>(1)
      assertThat(modification.added).hasSize(2)
      assertThat(modification.removed).isEmpty()
      moreListElements1.countDown()
    }.whenever(table1).updateGroupItems(any(PTableGroupItem::class.java), any(PTableGroupModification::class.java))
    doAnswer {
      table1.component
    }.whenever(event1).getData(Mockito.eq(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    val list = last.children.last() as ParameterGroupItem
    moreListElements1.runInEdt {
      val showMoreItem = list.children[2] as ShowMoreElementsItem
      // Click the "Show more" link:
      showMoreItem.link.actionPerformed(event1)
    }
    moreListElements1.await(TIMEOUT, TIMEOUT_UNIT)
    assertParameter(list, "list", PropertyType.ITERABLE, "List[12]")
    assertThat(list.reference).isNotNull()
    assertParameter(list.children[0], "[0]", PropertyType.STRING, "a")
    assertParameter(list.children[1], "[3]", PropertyType.STRING, "b")
    assertParameter(list.children[2], "[4]", PropertyType.STRING, "c")
    assertParameter(list.children[3], "[6]", PropertyType.STRING, "d")
    assertThat(list.children[4]).isInstanceOf(ShowMoreElementsItem::class.java)
    assertThat(list.children[4].index).isEqualTo(7)
    assertThat(list.children.size).isEqualTo(5)

    // Expand the list a second time:
    val moreListElements2 = ReportingCountDownLatch(1)
    val table2 = spy(PTable.create(mock()))
    val event2: AnActionEvent = mock()
    doAnswer {
      val modification = it.getArgument<PTableGroupModification>(1)
      assertThat(modification.added).hasSize(3)
      assertThat(modification.removed).hasSize(1)
      moreListElements2.countDown()
    }.whenever(table2).updateGroupItems(any(PTableGroupItem::class.java), any(PTableGroupModification::class.java))
    doAnswer {
      table2.component
    }.whenever(event2).getData(Mockito.eq(PlatformCoreDataKeys.CONTEXT_COMPONENT))
    moreListElements2.runInEdt {
      val showMoreItem = list.children[4] as ShowMoreElementsItem
      // Click the "Show more" link:
      showMoreItem.link.actionPerformed(event2)
    }
    moreListElements2.await(TIMEOUT, TIMEOUT_UNIT)
    assertParameter(list, "list", PropertyType.ITERABLE, "List[12]")
    assertThat(list.reference).isNull()
    assertParameter(list.children[0], "[0]", PropertyType.STRING, "a")
    assertParameter(list.children[1], "[3]", PropertyType.STRING, "b")
    assertParameter(list.children[2], "[4]", PropertyType.STRING, "c")
    assertParameter(list.children[3], "[6]", PropertyType.STRING, "d")
    assertParameter(list.children[4], "[7]", PropertyType.STRING, "e")
    assertParameter(list.children[5], "[10]", PropertyType.STRING, "f")
    assertParameter(list.children[6], "[11]", PropertyType.STRING, "g")
    assertThat(list.children.size).isEqualTo(7)
  }

  @Test
  fun parametersAreCachedUntilNextLayoutEvent() {
    InspectorClientSettings.isCapturingModeOn = true // Enable live mode, so we only fetch properties on demand

    val modelUpdatedSignal = ArrayBlockingQueue<Unit>(2) // We should get no more than two updates before continuing
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedSignal.offer(Unit)
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #1
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!! // Event triggered by tree #2

    val provider = inspectorRule.inspectorClient.provider

    val composableNode = inspectorRule.inspectorModel[-2]!!
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(0)

    provider.requestProperties(composableNode).get() // First fetch, not cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    provider.requestProperties(composableNode).get()  // Should be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    provider.requestProperties(composableNode).get()  // Still cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(1)

    // Trigger a fake layout update in *just* the first tree, which should reset just its cache and
    // not that for the second tree
    inspectorState.triggerLayoutCapture(rootId = 1)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

    provider.requestProperties(composableNode).get() // First fetch after layout event, not cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)

    provider.requestProperties(composableNode).get()  // Should be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)

    // Trigger a fake layout update in *just* the second tree, which should not affect the cache of the
    // first
    inspectorState.triggerLayoutCapture(rootId = 101)
    modelUpdatedSignal.poll(TIMEOUT, TIMEOUT_UNIT)!!

    provider.requestProperties(composableNode).get()  // Should still be cached
    assertThat(inspectorState.getParametersRequestCountFor(composableNode.drawId)).isEqualTo(2)
  }

  @Test
  fun snapshotModeSendsAllParametersAtOnce() {
    InspectorClientSettings.isCapturingModeOn = false // i.e. snapshot mode

    val modelUpdatedLatch = ReportingCountDownLatch(2) // We'll get two tree layout events on start fetch
    inspectorRule.inspectorModel.modificationListeners.add { _, _, _ ->
      modelUpdatedLatch.countDown()
    }

    inspectorRule.processNotifier.fireConnected(MODERN_PROCESS)
    modelUpdatedLatch.await(TIMEOUT, TIMEOUT_UNIT)

    // Calling "get properties" at this point should work without talking to the device because everything should
    // be cached now.

    val provider = inspectorRule.inspectorClient.provider
    val resultQueue = ArrayBlockingQueue<ProviderResult>(1)
    provider.resultListeners.add { _, view, table ->
      resultQueue.add(ProviderResult(view, table, inspectorRule.inspectorModel, inspectorRule.parametersCache))
    }

    for (id in listOf(-2L, -3L, -4L)) {
      assertThat(inspectorState.getParametersRequestCountFor(id)).isEqualTo(0)
      inspectorRule.inspectorModel[id]!!.let { targetNode ->
        provider.requestProperties(targetNode).get()
        val result = resultQueue.poll(TIMEOUT, TIMEOUT_UNIT)!!
        assertThat(result.view).isSameAs(targetNode)
        assertThat(inspectorState.getPropertiesRequestCountFor(id)).isEqualTo(0)
      }
    }
  }

  private fun layout(name: String, namespace: String = APP_NAMESPACE): ResourceReference =
    ResourceReference(ResourceNamespace.fromNamespaceUri(namespace)!!, ResourceType.LAYOUT, name)

  private fun style(name: String, namespace: String = ANDROID_URI): ResourceReference =
    ResourceReference.style(ResourceNamespace.fromNamespaceUri(namespace)!!, name)

  private fun PropertiesTable<InspectorPropertyItem>.assertParameter(
    name: String,
    type: PropertyType,
    value: String,
    group: PropertySection = PropertySection.PARAMETERS,
    namespace: String = parameterNamespaceOf(group),
  ) = assertProperty(this[namespace, name], name, type, value, null, group, namespace)

  private fun PropertiesTable<InspectorPropertyItem>.assertProperty(
    name: String,
    type: PropertyType,
    value: String,
    source: ResourceReference? = null,
    group: PropertySection = PropertySection.DEFAULT,
    namespace: String = ANDROID_URI,
    classLocation: String? = null,
    resolutionStack: List<ResStackItem> = emptyList(),
  ) = assertProperty(this[namespace, name], name, type, value, source, group, namespace, classLocation, resolutionStack)

  private fun assertParameter(
    property: InspectorPropertyItem,
    name: String,
    type: PropertyType,
    value: String,
    group: PropertySection = PropertySection.PARAMETERS,
    namespace: String = parameterNamespaceOf(group),
  ) = assertProperty(property, name, type, value, null, group, namespace)

  private fun assertProperty(
    property: InspectorPropertyItem,
    name: String,
    type: PropertyType,
    value: String,
    source: ResourceReference? = null,
    group: PropertySection = PropertySection.DEFAULT,
    namespace: String = ANDROID_URI,
    classLocation: String? = null,
    resolutionStack: List<ResStackItem> = emptyList(),
  ) {
    assertThat(property.name).isEqualTo(name)
    assertThat(property.attrName).isEqualTo(name)
    assertThat(property.namespace).isEqualTo(namespace)
    assertThat(property.type).isEqualTo(type)
    assertThat(property.value).isEqualTo(value)
    assertThat(property.source).isEqualTo(source)
    assertThat(property.section).isEqualTo(group)
    if (property !is InspectorGroupPropertyItem) {
      assertThat(resolutionStack).isEmpty()
      assertThat(classLocation).isNull()
    }
    else {
      assertThat(property.classLocation?.source).isEqualTo(classLocation?.substringAfterLast('.'))
      assertThat((property.classLocation?.navigatable as? PsiClass)?.qualifiedName).isEqualTo(classLocation)
      property.children.zip(resolutionStack).forEach { (actual, expected) ->
        assertThat(actual.source).isEqualTo(expected.source)
        expected.value?.let { assertThat(actual.value).isEqualTo(it) }
      }
      assertThat(property.children.size).isEqualTo(resolutionStack.size)
      assertThat(property.lookup.resourceLookup.hasResolver).isTrue()
    }
  }

  /**
   * A ResolutionStackItem holder without a reference to the base property.
   */
  private class ResStackItem(
    val source: ResourceReference,
    val value: String?
  )

  /**
   * Helper class to receive a properties provider result.
   */
  private class ProviderResult(
    val view: ViewNode,
    val table: PropertiesTable<InspectorPropertyItem>,
    val model: InspectorModel,
    val cache: ComposeParametersCache?
  ) {

    init {
      table.values.forEach { property ->
        assertThat(property.viewId).isEqualTo(view.drawId)
        if (property is ParameterItem) {
          assertThat(property.lookup).isSameAs(cache!!)
        } else {
          assertThat(property.lookup).isSameAs(model)
        }
      }
    }
  }
}
