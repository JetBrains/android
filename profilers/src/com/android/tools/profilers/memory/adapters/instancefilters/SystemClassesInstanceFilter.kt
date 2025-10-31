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
package com.android.tools.profilers.memory.adapters.instancefilters

import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.memory.adapters.InstanceObject

/**
 * A filter to locate all instances of classes that do not belong to the currently opened project (e.g. framework or library classes).
 */
class SystemClassesInstanceFilter(ideProfilerServices: IdeProfilerServices) : CaptureObjectInstanceFilter(
  displayName = "System classes",
  summaryDescription = "Show instances of classes from outside the current project.",
  detailedDescription = null,
  documentationLink = null,
  instanceTest = makeSystemClassTest(ideProfilerServices)
)

private fun makeSystemClassTest(ideProfilerServices: IdeProfilerServices): (InstanceObject) -> Boolean {
  // Lazily initialize the set of project classes only once.
  val projectClasses by lazy { ideProfilerServices.allProjectClasses }
  return { inst ->
    var className = inst.classEntry.className

    // Ignore inner classes since they can contain lambdas (e.g. topLevelClass$1).
    val innerClassStartIndex = className.indexOf("$")
    if (innerClassStartIndex != -1) {
      className = className.take(innerClassStartIndex)
    }
    !projectClasses.contains(className)
  }
}