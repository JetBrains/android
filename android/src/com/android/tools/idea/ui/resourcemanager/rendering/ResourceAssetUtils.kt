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
package com.android.tools.idea.ui.resourcemanager.rendering

import com.android.ide.common.rendering.api.ArrayResourceValue
import com.android.ide.common.rendering.api.PluralsResourceValue
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.configuration.ResourceQualifier
import com.android.resources.base.BasicFileResourceItem

private const val DEFAULT_CONFIGURATION = "default"
private const val NO_VALUE = "No value"

/**
 * Returns a simplified string of these configurations. If there's no configurations, returns "default".
 */
fun List<ResourceQualifier>.getReadableConfigurations(): String =
  this.joinToString("-") { it.folderSegment }.takeIf { it.isNotBlank() } ?: DEFAULT_CONFIGURATION

/**
 * Returns a simplified string of the configurations available for this resource. If there's no configurations, returns "default".
 */
fun ResourceItem.getReadableConfigurations(): String =
  this.configuration.qualifiers.joinToString("-") { it.folderSegment }.takeIf { it.isNotBlank() } ?: DEFAULT_CONFIGURATION

/**
 * Simplifies the resolved [ResourceValue] of a [ResourceItem]. E.g: When it references a path it should only return the file name, when it
 * references an Array resource it should try to list all values in the array.
 *
 * Returns "No value" if the resulting string would be empty or null.
 */
fun ResourceValue.getReadableValue(): String {
  // Some types of resource values require special handling.
  return when (this) {
    // Eg: "one: %s coin, many: %s coins"
    is PluralsResourceValue -> {
      val plurals = arrayOfNulls<String>(this.pluralsCount)
      for (index in plurals.indices) {
        plurals[index] = (this.getQuantity(index) + ": " + this.getValue(index))
      }
      plurals.joinToString(", ").takeIf { it.isNotBlank() }?: NO_VALUE
    }
    // Eg: "Monday, Tuesday, Wednesday"
    is ArrayResourceValue -> this.joinToString(", ").takeIf { it.isNotBlank() }?: NO_VALUE
    // Eg: "activity_main.xml"
    is BasicFileResourceItem -> this.source.fileName
    else -> this.value?.takeIf { it.isNotBlank() }?: NO_VALUE
  }
}
