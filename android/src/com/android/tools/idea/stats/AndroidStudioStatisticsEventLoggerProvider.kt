/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.stats

import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager

@Suppress("UnstableApiUsage")
class AndroidStudioStatisticsEventLoggerProvider : StatisticsEventLoggerProvider("FUS", 1, DEFAULT_SEND_FREQUENCY_MS, DEFAULT_MAX_FILE_SIZE_BYTES) {
  override val logger: StatisticsEventLogger by lazy { createLogger() }

  override fun isRecordEnabled(): Boolean {
    // This logic is needed to ensure we initialize the usage tracker and
    // IJ settings for data collection interaction properly.
    return !ApplicationManager.getApplication().isHeadlessEnvironment &&
           StatisticsUploadAssistant.isCollectAllowed()
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }

  private fun createLogger(): StatisticsEventLogger {
    if (!isRecordEnabled()) {
      return super.logger
    }
    return AndroidStudioEventLogger
  }
}
