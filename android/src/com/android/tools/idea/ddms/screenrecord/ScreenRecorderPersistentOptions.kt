/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenrecord

import com.android.ddmlib.ScreenRecorderOptions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.awt.Dimension
import kotlin.math.roundToInt

private const val DEFAULT_BIT_RATE_MBPS = 4
private const val DEFAULT_RESOLUTION_PERCENT = 100

@State(name = "ScreenRecorderOptions", storages = [Storage("screenRecorderOptions.xml")])
class ScreenRecorderPersistentOptions : PersistentStateComponent<ScreenRecorderPersistentOptions> {

  var bitRateMbps = DEFAULT_BIT_RATE_MBPS
  var resolutionPercent: Int = DEFAULT_RESOLUTION_PERCENT
  var showTaps = false
  var useEmulatorRecording = true

  companion object {
    @JvmStatic
    fun getInstance(): ScreenRecorderPersistentOptions =
      ApplicationManager.getApplication().getService(ScreenRecorderPersistentOptions::class.java)
  }

  override fun getState(): ScreenRecorderPersistentOptions = this

  override fun loadState(state: ScreenRecorderPersistentOptions) {
    XmlSerializerUtil.copyBean(state, this)
  }

  fun toScreenRecorderOptions(size: Dimension?): ScreenRecorderOptions {
    val builder = ScreenRecorderOptions.Builder()
      .setBitRate(bitRateMbps)
      .setShowTouches(showTaps)
    if (size != null && resolutionPercent != 100) {
      val ratio = resolutionPercent.toDouble() / 100
      builder.setSize(roundToMultipleOf16(size.width * ratio), roundToMultipleOf16(size.height * ratio))
    }
    return builder.build()
  }
}

private fun roundToMultipleOf16(n: Double): Int = ((n / 16).roundToInt() * 16)

