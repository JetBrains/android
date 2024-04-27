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
package com.android.tools.idea.adb

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.getOrPutSynchronized
import com.android.adblib.tools.debugging.JdwpSessionPipeline
import com.android.adblib.tools.debugging.JdwpSessionPipelineFactory
import com.android.adblib.tools.debugging.addJdwpSessionPipelineFactory
import com.android.tools.idea.adblib.AdbLibApplicationService
import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.diagnostic.thisLogger

class StudioAdbLibSCacheJdwpSessionPipelineFactory : JdwpSessionPipelineFactory {
  private var logger = thisLogger()

  private val scacheLogger = StudioSCacheLogger()

  private var enabled: () -> Boolean = { true }

  /**
   * SCache has a low priority, meaning it should be as close as possible to the device
   * in the `(JDWP Device Process, pipeline1, pipeline2, ..., Java Debugger)` sequence
   */
  override val priority: Int
    get() = -1000

  override fun create(
    session: AdbSession,
    device: ConnectedDevice,
    pid: Int,
    previousPipeline: JdwpSessionPipeline
  ): JdwpSessionPipeline? {
    return when(enabled()) {
       true -> {
         // Enable only if the device is remote
         val enabledForDevice = if (StudioFlags.JDWP_SCACHE_REMOTE_ONLY.get()) {
           device.isRemote()
         }
         else {
           true
         }

         logger.info("SCache for device $device, process=$pid: enabled=$enabledForDevice")

         when (enabledForDevice) {
           true -> {
             // If we (i.e. SCache) are enabled, we (as opposed to adblib) take ownership of tracing
             // JDWP packets, because SCache is the only component that has access to the "journaling"
             // (i.e. emulated) JDWP traffic.
             val monitor = when (StudioFlags.JDWP_TRACER.get()) {
               true -> StudioAdbLibJdwpTracer()
               false -> null
             }

             StudioAdbLibSCacheJdwpSessionPipeline(session, scacheLogger, monitor, previousPipeline)
           }

           false -> {
             // Not enabled for this device
             null
           }
         }
       }

      false -> {
        // Not enabled
        null
      }
    }
  }

  private fun ConnectedDevice.isRemote(): Boolean {
    val deviceProvisioner = AdbLibApplicationService.getDeviceProvisionerForSession(session)
    val isDeviceRemote = deviceProvisioner?.devices?.value?.any { deviceHandle ->
      (deviceHandle.state.connectedDevice === this) &&
      (deviceHandle.state.properties.isRemote ?: false)
    } ?: false

    return isDeviceRemote
  }

  companion object {
    private val key = CoroutineScopeCache.Key<StudioAdbLibSCacheJdwpSessionPipelineFactory>(
      "StudioAdbLibSCacheJdwpSessionPipelineFactory session cache key")

    @JvmStatic
    fun install(session: AdbSession, enabled: () -> Boolean) {
      val factory = session.cache.getOrPutSynchronized(key) {
        StudioAdbLibSCacheJdwpSessionPipelineFactory().also {
          session.addJdwpSessionPipelineFactory(it)
        }
      }
      factory.enabled = enabled
    }
  }
}
