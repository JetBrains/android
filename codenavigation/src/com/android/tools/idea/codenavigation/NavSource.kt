/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.intellij.pom.Navigatable

/**
 * An interface to get a [Navigatable] for a given [CodeLocation] using a specific strategy (e.g.
 * looking at the APK information).
 */
interface NavSource {
  /**
   * Gets a [Navigatable] for a [CodeLocation] for a specific architecture. [arch] will be CPU
   * arch as defined in the [Abi] enum. If no [Navigatable] can be found, null will be returned.
   */
  fun lookUp(location: CodeLocation, arch: String?): Navigatable?
}