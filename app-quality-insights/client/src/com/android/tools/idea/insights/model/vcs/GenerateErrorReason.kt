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

enum class GenerateErrorReason(val message: String) {
  NO_SUPPORTED_VCS_FOUND(
    "The Android Gradle Plugin was unable to find a Git repository rooted in your app's project root when this version of your app was built."
  ),
  NO_VALID_GIT_FOUND(
    "The Android Gradle Plugin was unable to read the Git repository at the root project level. '.git/HEAD' or '.git/refs/heads/${'$'}{branch}' were not found or unreadable."
  ),
  UNSPECIFIED(
    "The Android Gradle Plugin was unable to save version control information for an unknown reason."
  );

  companion object {
    fun fromProto(proto: BuildStamp.GenerateErrorReason): GenerateErrorReason {
      return when (proto) {
        BuildStamp.GenerateErrorReason.NO_SUPPORTED_VCS_FOUND -> NO_SUPPORTED_VCS_FOUND
        BuildStamp.GenerateErrorReason.NO_VALID_GIT_FOUND -> NO_VALID_GIT_FOUND
        else -> UNSPECIFIED
      }
    }
  }
}
