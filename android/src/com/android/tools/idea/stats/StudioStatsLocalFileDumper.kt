/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.google.protobuf.TextFormat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.isDirectory

object StudioStatsLocalFileDumper {
  const val STUDIO_EVENT_DUMP_DIR = "studio.event.dump.dir"

  private val LOG = Logger.getInstance(StudioStatsLocalFileDumper::class.java)

  @JvmStatic
  fun registerStudioEventFileDumper(disposable: Disposable) {
    //StatisticsViewerListener.register(disposable, ::dumpStudioEventToDirectory)
  }

  @Suppress("SpellCheckingInspection")
  private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

  private fun formatTime(time: ZonedDateTime): String = dateFormat.format(time)

  private fun dumpStudioEventToDirectory(studioEvent: AndroidStudioEvent.Builder) {
    System.getProperty(STUDIO_EVENT_DUMP_DIR)?.let { traceDir ->
      val traceDirPath = Path.of(traceDir)
      if (traceDirPath.isDirectory()) {
        val now = ZonedDateTime.now()
        val studioEventFile =
          traceDirPath.resolve(
            "${studioEvent.kind.name}-${formatTime(now)}-${now.toInstant().toEpochMilli()}.textproto"
          )
        try {
          Files.createFile(studioEventFile)
          Files.writeString(studioEventFile, TextFormat.printer().printToString(studioEvent))
        } catch (e: IOException) {
          LOG.warn(e)
        }
      }
    }
  }
}