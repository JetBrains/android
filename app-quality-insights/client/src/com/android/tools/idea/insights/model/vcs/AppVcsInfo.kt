/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.insights.model.vcs

import com.android.tools.idea.insights.proto.BuildStamp
import com.google.protobuf.TextFormat
import java.util.logging.Level
import java.util.logging.Logger

/** Representation of the version control system used by an App. */
sealed class AppVcsInfo {
  data class ValidInfo(val repoInfo: List<RepoInfo>) : AppVcsInfo()

  data class Error(val cause: GenerateErrorReason) : AppVcsInfo()

  object NONE : AppVcsInfo()

  companion object {
    fun fromProto(textProto: String): AppVcsInfo {
      val proto = decode(textProto)
      return fromProto(proto)
    }

    private fun fromProto(proto: BuildStamp): AppVcsInfo {
      return if (proto.hasGenerateErrorReason()) {
        Error(GenerateErrorReason.fromProto(proto.generateErrorReason))
      } else if (proto.repositoriesList.isNotEmpty()) {
        ValidInfo(repoInfo = proto.repositoriesList.mapNotNull { RepoInfo.fromProto(it) })
      } else {
        NONE
      }
    }
  }
}

fun decode(textProto: String): BuildStamp {
  return try {
    BuildStamp.newBuilder().apply { TextFormat.getParser().merge(textProto, this) }.build()
  } catch (exception: Exception) {
    Logger.getLogger("AppVcsInfo")
      .log(Level.WARNING, "Error when decoding from text proto ($textProto): $exception")
    BuildStamp.getDefaultInstance()
  }
}
