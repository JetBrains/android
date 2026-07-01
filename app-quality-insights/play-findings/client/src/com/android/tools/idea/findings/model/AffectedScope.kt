/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.findings.model

/** Represents a scope within the app that a finding refers to. */
sealed interface AffectedScope {
  /**
   * Represents an artifact location (e.g. an APK or AAB).
   *
   * @property versionCode The version code of the affected artifact.
   */
  data class Artifact(val versionCode: String) : AffectedScope

  /**
   * Represents a release location.
   *
   * @property releaseName The name of the affected release.
   */
  data class Release(val releaseName: String) : AffectedScope
}
