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
package com.android.tools.idea.gradle.model

interface IdeApiVersion {
  /**
   * Returns the api level as an integer.
   *
   * For target that are in preview mode, this can be superseded by [codename].
   */
  val apiLevel: Int

  /**
   * Returns the version code name if applicable, null otherwise.
   *
   * If the codename is non null, then the API level should be ignored, and this should be
   * used as a unique identifier of the target instead.
   */
  val codename: String?

  /**
   * Returns the API value as a string.
   *
   * If there's a codename, this returns it, otherwise this returns the string version
   * of the integer api level.
   * @return a String.
   */
  val apiString: String

}
