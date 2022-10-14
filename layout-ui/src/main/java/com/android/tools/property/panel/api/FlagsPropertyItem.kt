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
package com.android.tools.property.panel.api

import com.android.tools.property.ptable.PTableGroupItem

/**
 * A data structure designed for a property which contains flags.
 *
 * The [value] of the property will be a string of flag names separated by
 * an implementation defined separator.
 *
 * @param T the actual type of a flag item
 */
interface FlagsPropertyItem<out T : FlagPropertyItem> : PropertyItem {
  /** The flags representing this flag property */
  val children: List<T>

  /** Find a flag with a given name */
  fun flag(itemName: String): T?

  /** The combined [maskValue] of each of the flags currently set in the [value] */
  val maskValue: Int
}

/**
 * A [FlagsPropertyItem] that will display the flags as children in a properties table.
 */
interface FlagsPropertyGroupItem<out T : FlagPropertyItem> : FlagsPropertyItem<T>, PTableGroupItem

/**
 * A single flag represented as a [PropertyItem].
 *
 * The [value] of the property is implementation defined but would typically be either
 * "true" or "false".
 */
interface FlagPropertyItem : PropertyItem {
  /** The flags property this flag belongs to */
  val flags: FlagsPropertyItem<*>

  /** Same as the [value] of the property as a Boolean */
  var actualValue: Boolean

  /** True if the bits in [maskValue] are set by the [FlagsPropertyItem]. */
  val effectiveValue: Boolean

  /** The bit value of this flag */
  val maskValue: Int
}
