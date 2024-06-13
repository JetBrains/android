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
package com.android.tools.idea.appinspection.inspectors.network.model

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.LineChartModel
import com.android.tools.adtui.model.axis.AxisComponentModel
import com.android.tools.adtui.model.legend.LegendComponentModel
import com.android.tools.idea.appinspection.inspectors.network.model.connections.createFakeHttpData
import com.android.tools.idea.protobuf.ByteString
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.TimeUnit.SECONDS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Before
import org.junit.Test

class NetworkInspectorModelTest {
  private lateinit var model: NetworkInspectorModel
  private val timer = FakeTimer()

  @Before
  fun setUp() {
    val codeNavigationProvider = FakeCodeNavigationProvider()
    val services = TestNetworkInspectorServices(codeNavigationProvider, timer)
    model =
      NetworkInspectorModel(
        services,
        FakeNetworkInspectorDataSource(
          speedEventList =
            listOf(
              speedEvent(timestampNanos = 0, rxSpeed = 1, txSpeed = 2),
              speedEvent(timestampNanos = SECONDS.toNanos(10), rxSpeed = 3, txSpeed = 4),
            )
        ),
        CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher()),
      )
    model.timeline.dataRange.set(0.0, SECONDS.toMicros(20).toDouble())
    model.timeline.viewRange.set(0.0, SECONDS.toMicros(5).toDouble())
  }

  @Test
  fun getTrafficAxis() {
    val axis = model.trafficAxis
    assertThat(axis).isNotNull()
    assertThat(axis.range).isEqualTo(model.networkUsage.trafficRange)
  }

  @Test
  fun getLegends() {
    val networkLegends = model.legends
    assertThat(networkLegends.rxLegend.name).isEqualTo("Receiving")
    assertThat(networkLegends.txLegend.name).isEqualTo("Sending")
    assertThat(networkLegends.legends).hasSize(2)
    model.timeline.dataRange.set(0.0, 0.0)
    assertThat(networkLegends.rxLegend.value).isEqualTo("1 B/s")
    assertThat(networkLegends.txLegend.value).isEqualTo("2 B/s")
    model.timeline.dataRange.set(0.0, SECONDS.toMicros(10).toDouble())
    assertThat(networkLegends.rxLegend.value).isEqualTo("3 B/s")
    assertThat(networkLegends.txLegend.value).isEqualTo("4 B/s")
  }

  @Test
  fun setTrafficTooltip() {
    model.tooltip = (NetworkTrafficTooltipModel(model))
    assertThat(model.tooltip).isInstanceOf(NetworkTrafficTooltipModel::class.java)
    val tooltip = model.tooltip as NetworkTrafficTooltipModel
    val tooltipTime = SECONDS.toMicros(10).toDouble()
    model.timeline.tooltipRange.set(tooltipTime, tooltipTime)
    val networkLegends = tooltip.getLegends()
    assertThat(networkLegends.rxLegend.name).isEqualTo("Received")
    assertThat(networkLegends.txLegend.name).isEqualTo("Sent")
    assertThat(networkLegends.rxLegend.value).isEqualTo("3 B/s")
    assertThat(networkLegends.txLegend.value).isEqualTo("4 B/s")
    assertThat(networkLegends.legends).hasSize(2)
  }

  @Test
  fun getDetailedNetworkUsage() {
    val series = model.networkUsage.series
    assertThat(series).hasSize(2)
    val receiving = series[0]!!
    val sending = series[1]!!
    assertThat(receiving.name).isEqualTo("Receiving")
    assertThat(sending.name).isEqualTo("Sending")
    assertThat(receiving.series).hasSize(1)
    assertThat(receiving.series[0].x).isEqualTo(0)
    assertThat(receiving.series[0].value).isEqualTo(1)
    assertThat(sending.series).hasSize(1)
    assertThat(sending.series[0].x).isEqualTo(0)
    assertThat(sending.series[0].value).isEqualTo(2)
  }

  @Test
  fun updaterRegisteredCorrectly() {
    val observer = AspectObserver()
    var networkUsageUpdated = false
    var trafficAxisUpdated = false
    var legendsUpdated = false
    var tooltipLegendsUpdated = false

    model.networkUsage.addDependency(observer).onChange(LineChartModel.Aspect.LINE_CHART) {
      networkUsageUpdated = true
    }
    model.trafficAxis.addDependency(observer).onChange(AxisComponentModel.Aspect.AXIS) {
      trafficAxisUpdated = true
    }
    model.legends.addDependency(observer).onChange(LegendComponentModel.Aspect.LEGEND) {
      legendsUpdated = true
    }
    model.tooltipLegends.addDependency(observer).onChange(LegendComponentModel.Aspect.LEGEND) {
      tooltipLegendsUpdated = true
    }
    timer.tick(1)
    assertThat(networkUsageUpdated).isTrue()
    assertThat(trafficAxisUpdated).isTrue()
    assertThat(legendsUpdated).isFalse()
    assertThat(tooltipLegendsUpdated).isFalse()

    model.timeline.viewRange.set(SECONDS.toMicros(1).toDouble(), SECONDS.toMicros(2).toDouble())
    assertThat(networkUsageUpdated).isTrue()

    // Make sure the axis lerps correctly when we move the range there.
    model.timeline.dataRange.max = SECONDS.toMicros(101).toDouble()
    model.timeline.viewRange.set(SECONDS.toMicros(99).toDouble(), SECONDS.toMicros(101).toDouble())
    timer.tick(100)
    assertThat(legendsUpdated).isTrue()
    assertThat(trafficAxisUpdated).isTrue()
    model.timeline.tooltipRange.set(
      SECONDS.toMicros(100).toDouble(),
      SECONDS.toMicros(100).toDouble(),
    )
    assertThat(tooltipLegendsUpdated).isTrue()
  }

  @Test
  fun testSelectedConnection() {
    val data =
      createFakeHttpData(
        1,
        responseHeaders =
          listOf(
            httpHeader("null", "HTTP/1.1 302 Found"),
            httpHeader("Content-Type", "image/jpeg"),
          ),
        responsePayload = ByteString.copyFromUtf8("Content"),
      )
    val observer = AspectObserver()
    var connectionChanged = false
    model.aspect.addDependency(observer).onChange(NetworkInspectorAspect.SELECTED_CONNECTION) {
      connectionChanged = true
    }
    model.setSelectedConnection(data)
    assertThat(model.selectedConnection).isEqualTo(data)
    assertThat(connectionChanged).isEqualTo(true)
  }
}
