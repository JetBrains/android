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
package com.android.tools.profilers.memory.adapters.instancefilters

import com.android.tools.profilers.memory.adapters.InstanceObject

/**
 * Helper to filter and return a subset of the input InstanceObject's that match an arbitrary criteria. Note that this is different from
 * the string-based filter (see [FilterHandler]) that works at the class/package/callstack level but does not filter individual
 * instances within a class. This helper interface allows us to show a subset of instances that are of interest to the users.
 *
 * The only reason this class is "open" is because there is existing code that relies on the run-time class names of its subclasses.
 * Ideally, implementations only need to provide the right parameters rather than inheriting this class.
 */
open class CaptureObjectInstanceFilter(
  val displayName: String,
  /**
   * high-level summary of the instance filter's function.
   */
  val summaryDescription: String,
  /**
   * A more detailed explanation the instance filter's function if available, null otherwise.
   */
  val detailedDescription: String?,
  /**
   * link pointing to further documentation if available, null otherwise.
   */
  val documentationLink: String?,
  /**
   * The test on each individual instance
   */
  val instanceTest: (InstanceObject) -> Boolean) {

  /**
   * @param instances     The set of instances to filter
   * @return a subset of instances based on some arbitrary criteria.
   */
  fun filter(instances: Set<InstanceObject>) = instances.filterTo(HashSet(), instanceTest)
}