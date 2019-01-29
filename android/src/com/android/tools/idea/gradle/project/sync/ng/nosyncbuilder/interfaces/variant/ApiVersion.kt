/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.variant

import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.proto.VariantProto

/**
 * Represents the version of an Android Platform.
 *
 * A version is defined by an API level and an optional code name.
 *
 * Release versions of the Android platform are identified by their API level (integer),
 * (technically the code name for release version is "REL" but this class will return `null` instead.)
 *
 * Preview versions of the platform are identified by a code name.
 * Their API level is usually set to the value of the previous platform.
 */
interface ApiVersion {
  /**
   * The api level as an integer.
   *
   * For target that are in preview mode, this can be superseded by [codename].
   */
  val apiLevel: Int
  /**
   * The version code name if applicable, null otherwise.
   *
   * If the codename is non null, then the API level should be ignored, and this should be
   * used as a unique identifier of the target instead.
   */
  val codename: String?
  /**
   * The API value as a string.
   *
   * If there is a codename, it contains the codename, otherwise it contains the string version of the integer api level.
   */
  val apiString: String

  fun toProto(): VariantProto.ApiVersion {
    val minimalProto = VariantProto.ApiVersion.newBuilder()
      .setApiLevel(apiLevel)
      .setApiString(apiString)

    codename?.let { minimalProto.codename = codename }

    return minimalProto.build()!!
  }

}
