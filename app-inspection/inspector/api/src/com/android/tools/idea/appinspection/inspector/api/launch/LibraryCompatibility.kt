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
package com.android.tools.idea.appinspection.inspector.api.launch

import com.android.tools.app.inspection.AppInspection

/** Contains a library compatibility specification. */
data class LibraryCompatibility(
  /** The coordinate of the library artifact. */
  val coordinate: ArtifactCoordinate,
  /**
   * A list of fully qualified class names that is expected to be available from the library
   * specified by this artifact. This parameter is optional, and only affects the error code given
   * if the library specified cannot be found. The list provides some forward safety in case the
   * library class names change.
   */
  val expectedClassNames: List<String> = emptyList()
) {
  fun toLibraryCompatibilityProto(): AppInspection.LibraryCompatibility =
    AppInspection.LibraryCompatibility.newBuilder()
      .setCoordinate(coordinate.toArtifactCoordinateProto())
      .addAllExpectedLibraryClassNames(expectedClassNames)
      .build()
}
