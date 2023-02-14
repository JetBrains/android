/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.details

import com.android.flags.junit.FlagRule
import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.legend.Legend
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.idea.appinspection.inspectors.network.ide.analytics.IdeNetworkInspectorTracker
import com.android.tools.idea.appinspection.inspectors.network.model.FakeCodeNavigationProvider
import com.android.tools.idea.appinspection.inspectors.network.model.FakeNetworkInspectorDataSource
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorClient
import com.android.tools.idea.appinspection.inspectors.network.model.NetworkInspectorModel
import com.android.tools.idea.appinspection.inspectors.network.model.TestNetworkInspectorServices
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.FAKE_THREAD_LIST
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpData
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.HttpDataModel
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.JavaThread
import com.android.tools.idea.appinspection.inspectors.network.model.httpdata.createFakeHttpData
import com.android.tools.idea.appinspection.inspectors.network.view.FakeUiComponentsProvider
import com.android.tools.idea.appinspection.inspectors.network.view.NetworkInspectorView
import com.android.tools.idea.appinspection.inspectors.network.view.TestNetworkInspectorUsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.google.wireless.android.sdk.stats.AppInspectionEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import java.awt.Component
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import studio.network.inspection.NetworkInspectorProtocol

private const val FAKE_TRACE = "com.google.downloadUrlToStream(ImageFetcher.java:274)"
private const val FAKE_RESPONSE_HEADERS =
  "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n"

val DEFAULT_DATA =
  createFakeHttpData(
    1,
    10000,
    25000,
    50000,
    100000,
    100000,
    responseFields = FAKE_RESPONSE_HEADERS,
    url = "dumbUrl",
    trace = FAKE_TRACE,
    method = "GET"
  )

/**
 * Header names chosen and intentionally unsorted, to make sure that they are shown in the UI in
 * sorted order.
 */
private const val TEST_HEADERS =
  "car = car-value \n border = border-value \n apple = apple-value \n 123 = numeric-value \n"

/** Will throw an exception if no match is found. */
private fun <C : Component> firstDescendantWithType(root: Component, type: Class<C>): C {
  return TreeWalker(root).descendants().filterIsInstance(type).first()
}

private fun <T : TabContent?> ConnectionDetailsView.findTab(tabClass: Class<T>): T? {
  return tabs.filterIsInstance(tabClass).firstOrNull()
}

private fun <C : Component> allDescendantsWithType(root: Component, type: Class<C>): List<C> {
  return TreeWalker(root).descendants().filterIsInstance(type)
}

@RunsInEdt
class ConnectionDetailsViewTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    override suspend fun getStartTimeStampNs() = 0L
    override suspend fun interceptResponse(command: NetworkInspectorProtocol.InterceptCommand) =
      Unit
  }

  @get:Rule val flagRule = FlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule val projectRule = AndroidProjectRule.inMemory().onEdt()

  private lateinit var client: TestNetworkInspectorClient
  private lateinit var tracker: TestNetworkInspectorUsageTracker
  private lateinit var services: TestNetworkInspectorServices
  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var detailsView: ConnectionDetailsView
  private val timer: FakeTimer = FakeTimer()
  private lateinit var scope: CoroutineScope
  private lateinit var disposable: Disposable

  @Before
  fun before() {
    disposable = Disposer.newDisposable()
    val codeNavigationProvider = FakeCodeNavigationProvider()
    client = TestNetworkInspectorClient()
    tracker = TestNetworkInspectorUsageTracker()
    Disposer.register(disposable, tracker)
    services =
      TestNetworkInspectorServices(
        codeNavigationProvider,
        timer,
        client,
        IdeNetworkInspectorTracker(projectRule.project)
      )
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    model =
      NetworkInspectorModel(
        services,
        FakeNetworkInspectorDataSource(),
        scope,
        object : HttpDataModel {
          private val dataList = listOf(DEFAULT_DATA)
          override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
            return dataList.filter {
              it.requestStartTimeUs >= timeCurrentRangeUs.min &&
                it.requestStartTimeUs <= timeCurrentRangeUs.max
            }
          }
        }
      )
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)

    inspectorView =
      NetworkInspectorView(
        projectRule.project,
        model,
        FakeUiComponentsProvider(),
        component,
        services,
        scope,
        disposable
      )
    parentPanel.add(inspectorView.component)
    detailsView = inspectorView.detailsPanel.connectionDetailsView
  }

  @After
  fun tearDown() {
    scope.cancel()
    Disposer.dispose(disposable)
  }

  @Test
  fun viewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    assertThat(
        HttpDataComponentFactory.findPayloadViewer(
          detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody()
        )
      )
      .isNull()
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(
        HttpDataComponentFactory.findPayloadViewer(
          detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody()
        )
      )
      .isNotNull()
  }

  @Test
  fun viewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    val data = DEFAULT_DATA.copy(requestPayload = ByteString.EMPTY)
    detailsView.setHttpData(data)
    assertThat(
        HttpDataComponentFactory.findPayloadViewer(
          detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody()
        )
      )
      .isNull()
  }

  @Test
  fun requestPayloadHasBothParsedViewAndRawDataView() {
    val data = DEFAULT_DATA.copy(requestFields = "Content-Type = application/x-www-form-urlencoded")
    detailsView.setHttpData(data)
    val payloadBody = detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody()!!
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Parsed" }).isTrue()
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Source" }).isTrue()
  }

  @Test
  fun responsePayloadHasBothParsedViewAndRawDataView() {
    val data =
      DEFAULT_DATA.copy(
        responseFields =
          "null =  HTTP/1.1 302 Found\n Content-Type = application/x-www-form-urlencoded"
      )
    detailsView.setHttpData(data)
    val payloadBody = detailsView.findTab(ResponseTabContent::class.java)!!.findPayloadBody()!!
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Parsed" }).isTrue()
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Source" }).isTrue()
  }

  @Test
  fun viewerExistsWhenPayloadIsPresent() {
    val data = DEFAULT_DATA.copy(responseFields = FAKE_RESPONSE_HEADERS)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findResponsePayloadViewer())
      .isNull()
    detailsView.setHttpData(data)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findResponsePayloadViewer())
      .isNotNull()
  }

  @Test
  fun contentTypeHasProperValueFromData() {
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue())
      .isNull()
    val data = DEFAULT_DATA.copy(responseFields = FAKE_RESPONSE_HEADERS)
    detailsView.setHttpData(data)
    val value = detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue()!!
    assertThat(value.text).isEqualTo("111")
  }

  @Test
  fun contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    detailsView.setHttpData(DEFAULT_DATA.copy(responseFields = ""))
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue())
      .isNull()
  }

  @Test
  fun initiatingThreadFieldIsPresent() {
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(DEFAULT_DATA.javaThreads).hasSize(1)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findInitiatingThreadValue())
      .isNotNull()
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findOtherThreadsValue())
      .isNull()
  }

  @Test
  fun otherThreadsFieldIsPresent() {
    val data = DEFAULT_DATA.copy(threads = FAKE_THREAD_LIST + listOf(JavaThread(2, "thread2")))
    assertThat(data.javaThreads).hasSize(2)
    detailsView.setHttpData(data)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findOtherThreadsValue())
      .isNotNull()
  }

  @Test
  fun urlHasProperValueFromData() {
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findUrlValue()).isNull()
    detailsView.setHttpData(DEFAULT_DATA)
    val value = detailsView.findTab(OverviewTabContent::class.java)!!.findUrlValue()!!
    assertThat(value.text).isEqualTo("dumbUrl")
  }

  @Test
  fun sizeHasProperValueFromData() {
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findSizeValue()).isNull()
    detailsView.setHttpData(DEFAULT_DATA)
    val value = detailsView.findTab(OverviewTabContent::class.java)!!.findSizeValue()!!
    assertThat(value.text).isEqualTo("222 B")
  }

  @Test
  fun timingFieldIsPresent() {
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findTimingBar()).isNull()
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findTimingBar()).isNotNull()
  }

  @Test
  fun headerSectionIsSorted() {
    val data: HttpData = DEFAULT_DATA.copy(requestFields = TEST_HEADERS)
    detailsView.setHttpData(data)
    val tabContent = detailsView.findTab(RequestTabContent::class.java)!!
    val labels =
      allDescendantsWithType(tabContent.component, NoWrapBoldLabel::class.java).map { it.text }
    val values =
      allDescendantsWithType(tabContent.component, WrappedTextArea::class.java).map { it.text }
    val labelsWithValues = labels.zip(values) { label, value -> "$label $value" }

    // Add a colon (":") to all labels since it is added to the label in the UI.
    assertUiContainsLabelAndValue(labelsWithValues[0], "123:", "numeric-value")
    assertUiContainsLabelAndValue(labelsWithValues[1], "apple:", "apple-value")
    assertUiContainsLabelAndValue(labelsWithValues[2], "border:", "border-value")
    assertUiContainsLabelAndValue(labelsWithValues[3], "car:", "car-value")
    assertThat(labels.indexOf("123:")).isGreaterThan(-1)
    assertThat(labels.indexOf("123:")).isLessThan(labels.indexOf("apple:"))
    assertThat(labels.indexOf("apple:")).isLessThan(labels.indexOf("border:"))
    assertThat(labels.indexOf("border:")).isLessThan(labels.indexOf("car:"))
  }

  private fun assertUiContainsLabelAndValue(uiText: String, label: String, value: String) {
    assert(uiText == "$label $value")
  }

  @Test
  fun expectedDisplayNameForContentTypes() {
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType(""))).isEqualTo("")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType(" "))).isEqualTo("")
    assertThat(
        HttpDataComponentFactory.getDisplayName(
          HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8")
        )
      )
      .isEqualTo("Form Data")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("text/html")))
      .isEqualTo("HTML")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("application/json")))
      .isEqualTo("JSON")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("image/jpeg")))
      .isEqualTo("Image")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("audio/webm")))
      .isEqualTo("Audio")
  }

  @Test
  fun callStackViewHasProperValueFromData() {
    val observer = AspectObserver()
    val stackFramesChangedCount = intArrayOf(0)
    val stackTraceView = detailsView.findTab(CallStackTabContent::class.java)!!.stackTraceView
    stackTraceView.model.addDependency(observer).onChange(StackTraceModel.Aspect.STACK_FRAMES) {
      stackFramesChangedCount[0]++
    }
    assertThat(stackFramesChangedCount[0]).isEqualTo(0)
    assertThat(stackTraceView.model.codeLocations).hasSize(0)
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(stackFramesChangedCount[0]).isEqualTo(1)
    assertThat(stackTraceView.model.codeLocations).hasSize(1)
    assertThat(stackTraceView.model.codeLocations[0].className).isEqualTo("com.google")
    assertThat(stackTraceView.model.codeLocations[0].fileName).isEqualTo("ImageFetcher.java")
    assertThat(stackTraceView.model.codeLocations[0].methodName).isEqualTo("downloadUrlToStream")
  }

  @Test
  fun sentReceivedLegendRendersCorrectly() {
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, 0, "*", "*")
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      TimeUnit.MILLISECONDS.toMicros(1000),
      0,
      "0 ms",
      "*"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      TimeUnit.MILLISECONDS.toMicros(2500),
      0,
      "1 s 500 ms",
      "*"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      TimeUnit.MILLISECONDS.toMicros(3000),
      TimeUnit.MILLISECONDS.toMicros(3000),
      "2 s",
      "0 ms"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      TimeUnit.MILLISECONDS.toMicros(3000),
      TimeUnit.MILLISECONDS.toMicros(4234),
      "2 s",
      "1 s 234 ms"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      0,
      TimeUnit.MILLISECONDS.toMicros(1000),
      "0 ms",
      "0 ms"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000),
      0,
      TimeUnit.MILLISECONDS.toMicros(2000),
      "1 s",
      "0 ms"
    )
  }

  @Test
  fun trackConnectionComponentSelections() {
    assertThat(
        HttpDataComponentFactory.findPayloadViewer(
          detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody()
        )
      )
      .isNull()
    model.setSelectedConnection(DEFAULT_DATA)
    tracker.verifyLatestEvent {
      assertThat(it.type)
        .isEqualTo(AppInspectionEvent.NetworkInspectorEvent.Type.CONNECTION_DETAIL_SELECTED)
    }
    detailsView.selectedIndex = 1
    tracker.verifyLatestEvent {
      assertThat(it.type)
        .isEqualTo(AppInspectionEvent.NetworkInspectorEvent.Type.RESPONSE_TAB_SELECTED)
    }
    detailsView.selectedIndex = 2
    tracker.verifyLatestEvent {
      assertThat(it.type)
        .isEqualTo(AppInspectionEvent.NetworkInspectorEvent.Type.REQUEST_TAB_SELECTED)
    }
    detailsView.selectedIndex = 3
    tracker.verifyLatestEvent {
      assertThat(it.type)
        .isEqualTo(AppInspectionEvent.NetworkInspectorEvent.Type.CALLSTACK_TAB_SELECTED)
    }
  }

  private fun assertExpectedTimingLegends(
    startTimeUs: Long,
    downloadingTimeUs: Long,
    endTimeUs: Long,
    sentLegend: String,
    receivedLegend: String
  ) {
    val data =
      createFakeHttpData(
        0,
        startTimeUs,
        startTimeUs,
        downloadingTimeUs,
        endTimeUs,
        endTimeUs,
        url = "unusedUrl"
      )
    detailsView.setHttpData(data)
    val legendComponent = firstDescendantWithType(detailsView, LegendComponent::class.java)
    val legends: List<Legend> = legendComponent.model.legends
    assertThat(legends[0].value).isEqualTo(sentLegend)
    assertThat(legends[1].value).isEqualTo(receivedLegend)
  }
}
