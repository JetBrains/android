/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.metrics

import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorAutoConnectInfo
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import layout_inspector.LayoutInspector

/**
 * Utility class used to log metrics for [ForegroundProcessDetection] to Studio metrics.
 *
 */
class ForegroundProcessDetectionMetrics(private val layoutInspectorMetrics: LayoutInspectorMetrics) {
  /**
   * Used to log the result of a handshake. SUPPORTED, NOT_SUPPORTED or UNKNOWN.
   * In case of NOT_SUPPORTED we also log the reason why.
   */
  fun logHandshakeResult(handshakeInfo: LayoutInspector.TrackingForegroundProcessSupported) {
    layoutInspectorMetrics.logEvent(
      DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.AUTO_CONNECT_INFO,
      DisconnectedClient.stats,
      autoConnectInfo = buildAutoConnectInfo(handshakeInfo)
    )
  }

  /**
   * Used to log the conversion of a device with UNKNOWN support to SUPPORTED or NOT_SUPPORTED.
   */
  fun logConversion(conversion: DynamicLayoutInspectorAutoConnectInfo.HandshakeUnknownConversion) {
    layoutInspectorMetrics.logEvent(
      DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.AUTO_CONNECT_INFO,
      DisconnectedClient.stats,
      autoConnectInfo = buildAutoConnectInfo(conversion)
    )
  }

  private fun buildAutoConnectInfo(supportInfo: LayoutInspector.TrackingForegroundProcessSupported): DynamicLayoutInspectorAutoConnectInfo {
    val builder = DynamicLayoutInspectorAutoConnectInfo.newBuilder()
    val supportType = supportInfo.supportType
    if (supportType != null) {
      builder.handshakeResult = supportType.toHandshakeResult()
    }

    if (supportType == LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED) {
      val reasonNotSupported = supportInfo.reasonNotSupported.toAutoConnectReasonNotSupported()
      builder.reasonNotSupported = reasonNotSupported
    }

    return builder.build()
  }

  private fun buildAutoConnectInfo(
    handshakeUnknownConversion: DynamicLayoutInspectorAutoConnectInfo.HandshakeUnknownConversion
  ): DynamicLayoutInspectorAutoConnectInfo {
    return DynamicLayoutInspectorAutoConnectInfo.newBuilder().setHandshakeConversion(handshakeUnknownConversion).build()
  }

  private fun LayoutInspector.TrackingForegroundProcessSupported.SupportType.toHandshakeResult()
  : DynamicLayoutInspectorAutoConnectInfo.HandshakeResult {
    return when (this) {
      LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNKNOWN ->
        DynamicLayoutInspectorAutoConnectInfo.HandshakeResult.SUPPORT_UNKNOWN
      LayoutInspector.TrackingForegroundProcessSupported.SupportType.SUPPORTED ->
        DynamicLayoutInspectorAutoConnectInfo.HandshakeResult.SUPPORTED
      LayoutInspector.TrackingForegroundProcessSupported.SupportType.NOT_SUPPORTED ->
        DynamicLayoutInspectorAutoConnectInfo.HandshakeResult.NOT_SUPPORTED
      LayoutInspector.TrackingForegroundProcessSupported.SupportType.UNRECOGNIZED ->
        DynamicLayoutInspectorAutoConnectInfo.HandshakeResult.UNSPECIFIED_RESULT
    }
  }

  private fun LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.toAutoConnectReasonNotSupported()
  : DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported {
    return when (this) {
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NOT_FOUND ->
        DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported.DUMPSYS_NOT_FOUND
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.GREP_NOT_FOUND ->
        DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported.GREP_NOT_FOUND
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NO_TOP_ACTIVITY_NO_SLEEPING_ACTIVITIES ->
        DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported.DUMPSYS_NO_TOP_ACTIVITY_NO_SLEEPING_ACTIVITIES
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.DUMPSYS_NO_TOP_ACTIVITY_BUT_HAS_AWAKE_ACTIVITIES ->
        DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported.DUMPSYS_NO_TOP_ACTIVITY_BUT_HAS_AWAKE_ACTIVITIES
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.UNRECOGNIZED,
      LayoutInspector.TrackingForegroundProcessSupported.ReasonNotSupported.UNKNOWN_REASON ->
        DynamicLayoutInspectorAutoConnectInfo.AutoConnectReasonNotSupported.UNSPECIFIED_REASON
    }
  }
}