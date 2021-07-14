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

import com.android.flags.junit.SetFlagRule
import com.android.tools.adtui.LegendComponent
import com.android.tools.adtui.TreeWalker
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.legend.Legend
import com.android.tools.adtui.stdui.TooltipLayeredPane
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.adtui.swing.enableHeadlessDialogs
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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Component
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextPane

private const val FAKE_TRACE = "com.google.downloadUrlToStream(ImageFetcher.java:274)"
private const val FAKE_RESPONSE_HEADERS = "null =  HTTP/1.1 302 Found \n Content-Type = 111 \n Content-Length = 222 \n"

private val DEFAULT_DATA = createFakeHttpData(1, 10000, 25000, 50000, 100000, 100000,
                                              responseFields = FAKE_RESPONSE_HEADERS, url = "dumbUrl", trace = FAKE_TRACE, method = "GET")

/**
 * Header names chosen and intentionally unsorted, to make sure that they are shown in the UI in sorted order.
 */
private const val TEST_HEADERS = "car = car-value \n border = border-value \n apple = apple-value \n 123 = numeric-value \n"

/**
 * Will throw an exception if no match is found.
 */
private fun <C : Component> firstDescendantWithType(root: Component, type: Class<C>): C {
  return TreeWalker(root).descendants().filterIsInstance(type).first()
}

private fun <T : TabContent?> ConnectionDetailsView.findTab(tabClass: Class<T>): T? {
  return tabs.filterIsInstance(tabClass).firstOrNull()
}

@RunsInEdt
class ConnectionDetailsViewTest {

  private class TestNetworkInspectorClient : NetworkInspectorClient {
    private var lastInterceptedUrl: String? = null
    private var lastInterceptedBody: String? = null

    override suspend fun getStartTimeStampNs() = 0L

    override suspend fun interceptResponse(url: String, body: String) {
      lastInterceptedUrl = url
      lastInterceptedBody = body
    }

    fun verifyInterceptResponse(url: String, body: String) {
      assertThat(lastInterceptedUrl).isEqualTo(url)
      assertThat(lastInterceptedBody).isEqualTo(body)
    }
  }

  private val setFlagRule = SetFlagRule(StudioFlags.ENABLE_NETWORK_INTERCEPTION, true)

  @get:Rule
  val ruleChain = RuleChain.outerRule(ProjectRule()).around(EdtRule()).around(setFlagRule)!!

  private lateinit var client: TestNetworkInspectorClient
  private lateinit var services: TestNetworkInspectorServices
  private lateinit var model: NetworkInspectorModel
  private lateinit var inspectorView: NetworkInspectorView
  private lateinit var detailsView: ConnectionDetailsView
  private val timer: FakeTimer = FakeTimer()
  private lateinit var scope: CoroutineScope
  private lateinit var disposable: Disposable

  @Before
  fun before() {
    val codeNavigationProvider = FakeCodeNavigationProvider()
    client = TestNetworkInspectorClient()
    services = TestNetworkInspectorServices(codeNavigationProvider, timer, client)
    model = NetworkInspectorModel(services, FakeNetworkInspectorDataSource(), object : HttpDataModel {
      private val dataList = listOf(DEFAULT_DATA)
      override fun getData(timeCurrentRangeUs: Range): List<HttpData> {
        return dataList.filter { it.requestStartTimeUs >= timeCurrentRangeUs.min && it.requestStartTimeUs <= timeCurrentRangeUs.max }
      }
    })
    val parentPanel = JPanel()
    val component = TooltipLayeredPane(parentPanel)
    scope = CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
    inspectorView = NetworkInspectorView(model, FakeUiComponentsProvider(), component, services, scope)
    parentPanel.add(inspectorView.component)
    detailsView = inspectorView.connectionDetails
    disposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    scope.cancel()
    Disposer.dispose(disposable)
  }

  @Test
  fun viewerForRequestPayloadIsPresentWhenRequestPayloadIsNotNull() {
    assertThat(HttpDataComponentFactory.findPayloadViewer(detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody())).isNull()
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(
      HttpDataComponentFactory.findPayloadViewer(detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody())).isNotNull()
  }

  @Test
  fun viewerForRequestPayloadIsAbsentWhenRequestPayloadIsNull() {
    val data = DEFAULT_DATA.copy(requestPayload = ByteString.EMPTY)
    detailsView.setHttpData(data)
    assertThat(HttpDataComponentFactory.findPayloadViewer(detailsView.findTab(RequestTabContent::class.java)!!.findPayloadBody())).isNull()
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
    val data = DEFAULT_DATA.copy(responseFields = "null =  HTTP/1.1 302 Found\n Content-Type = application/x-www-form-urlencoded")
    detailsView.setHttpData(data)
    val payloadBody = detailsView.findTab(ResponseTabContent::class.java)!!.findPayloadBody()!!
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Parsed" }).isTrue()
    assertThat(TreeWalker(payloadBody).descendants().any { c -> c.name == "View Source" }).isTrue()
  }

  @Test
  fun sendsInterceptResponseFromDialog() {
    enableHeadlessDialogs(disposable)
    val newText = "NEW TEXT"
    val dialog = ResponseTabContent.ResponseInterceptDialog(DEFAULT_DATA, services, scope)
    createModalDialogAndInteractWithIt({ dialog.show() }) {
      val textArea = firstDescendantWithType(dialog.contentPane, JTextArea::class.java)
      assertThat(textArea.isEditable).isTrue()
      assertThat(textArea.text).isEqualTo("RESPONSE")
      textArea.text = newText
      dialog.clickDefaultButton()
    }
    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.OK_EXIT_CODE)
    client.verifyInterceptResponse(DEFAULT_DATA.url, newText)
  }

  @Test
  fun viewIsVisibleWhenDataIsNotNull() {
    detailsView.isVisible = false
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(detailsView.isVisible).isTrue()
  }

  @Test
  fun viewIsNotVisibleWhenDataIsNull() {
    detailsView.isVisible = true
    detailsView.setHttpData(null)
    assertThat(detailsView.isVisible).isFalse()
  }

  @Test
  fun viewerExistsWhenPayloadIsPresent() {
    val data = DEFAULT_DATA.copy(responseFields = FAKE_RESPONSE_HEADERS)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findResponsePayloadViewer()).isNull()
    detailsView.setHttpData(data)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findResponsePayloadViewer()).isNotNull()
  }

  @Test
  fun contentTypeHasProperValueFromData() {
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue()).isNull()
    val data = DEFAULT_DATA.copy(responseFields = FAKE_RESPONSE_HEADERS)
    detailsView.setHttpData(data)
    val value = detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue()!!
    assertThat(value.text).isEqualTo("111")
  }

  @Test
  fun contentTypeIsAbsentWhenDataHasNoContentTypeValue() {
    detailsView.setHttpData(DEFAULT_DATA.copy(responseFields = ""))
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findContentTypeValue()).isNull()
  }

  @Test
  fun initiatingThreadFieldIsPresent() {
    detailsView.setHttpData(DEFAULT_DATA)
    assertThat(DEFAULT_DATA.javaThreads).hasSize(1)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findInitiatingThreadValue()).isNotNull()
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findOtherThreadsValue()).isNull()
  }

  @Test
  fun otherThreadsFieldIsPresent() {
    val data = DEFAULT_DATA.copy(threads = FAKE_THREAD_LIST + listOf(JavaThread(2, "thread2")))
    assertThat(data.javaThreads).hasSize(2)
    detailsView.setHttpData(data)
    assertThat(detailsView.findTab(OverviewTabContent::class.java)!!.findOtherThreadsValue()).isNotNull()
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
    val text = firstDescendantWithType(tabContent.component, JTextPane::class.java).text
    assertUiContainsLabelAndValue(text, "123", "numeric-value")
    assertUiContainsLabelAndValue(text, "apple", "apple-value")
    assertUiContainsLabelAndValue(text, "border", "border-value")
    assertUiContainsLabelAndValue(text, "car", "car-value")
    assertThat(text.indexOf("123")).isGreaterThan(-1)
    assertThat(text.indexOf("123")).isLessThan(text.indexOf("apple"))
    assertThat(text.indexOf("apple")).isLessThan(text.indexOf("border"))
    assertThat(text.indexOf("border")).isLessThan(text.indexOf("car"))
  }

  private fun assertUiContainsLabelAndValue(uiText: String, label: String, value: String) {
    assertThat(uiText).containsMatch(String.format("\\b%s\\b.+\\b%s\\b", label, value))
  }

  @Test
  fun expectedDisplayNameForContentTypes() {
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType(""))).isEqualTo("")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType(" "))).isEqualTo("")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("application/x-www-form-urlencoded; charset=utf-8")))
      .isEqualTo("Form Data")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("text/html"))).isEqualTo("HTML")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("application/json"))).isEqualTo("JSON")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("image/jpeg"))).isEqualTo("Image")
    assertThat(HttpDataComponentFactory.getDisplayName(HttpData.ContentType("audio/webm"))).isEqualTo("Audio")
  }

  @Test
  fun callStackViewHasProperValueFromData() {
    val observer = AspectObserver()
    val stackFramesChangedCount = intArrayOf(0)
    val stackTraceView = detailsView.findTab(CallStackTabContent::class.java)!!.stackTraceView
    stackTraceView.model.addDependency(observer).onChange(StackTraceModel.Aspect.STACK_FRAMES) { stackFramesChangedCount[0]++ }
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
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(1000), 0, "0 ms", "*")
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(2500), 0, "1 s 500 ms", "*")
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
      TimeUnit.MILLISECONDS.toMicros(3000), "2 s", "0 ms"
    )
    assertExpectedTimingLegends(
      TimeUnit.MILLISECONDS.toMicros(1000), TimeUnit.MILLISECONDS.toMicros(3000),
      TimeUnit.MILLISECONDS.toMicros(4234), "2 s", "1 s 234 ms"
    )
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(1000), "0 ms", "0 ms")
    assertExpectedTimingLegends(TimeUnit.MILLISECONDS.toMicros(1000), 0, TimeUnit.MILLISECONDS.toMicros(2000), "1 s", "0 ms")
  }

  private fun assertExpectedTimingLegends(
    startTimeUs: Long,
    downloadingTimeUs: Long,
    endTimeUs: Long,
    sentLegend: String,
    receivedLegend: String
  ) {
    val data = createFakeHttpData(0, startTimeUs, startTimeUs, downloadingTimeUs, endTimeUs, endTimeUs, url = "unusedUrl")
    detailsView.setHttpData(data)
    val legendComponent = firstDescendantWithType(detailsView, LegendComponent::class.java)
    val legends: List<Legend> = legendComponent.model.legends
    assertThat(legends[0].value).isEqualTo(sentLegend)
    assertThat(legends[1].value).isEqualTo(receivedLegend)
  }
}