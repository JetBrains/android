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
package com.android.tools.adtui.categorytable

import javax.swing.SortOrder

/**
 * An attribute of a [CategoryTable]'s value class that can be extracted, sorted, and possibly
 * grouped by. It provides the non-UI functionality of a [Column].
 *
 * Note that the row type T is expected to be an immutable class with value-based equality, which
 * generally implies that its attribute types should be as well. However, it is possible for the
 * value type to be a stateful object with identity-based equality if its sort order, toString(),
 * and UI presentation (see [Column.updateValue]) are stable.
 *
 * @param T the value type we extract from (the row type of the CategoryTable)
 * @param C the type that is extracted (a column type of the CategoryTable)
 */
interface Attribute<in T, C> {

  /** If present, a [Comparator] that can be used to sort values of this attribute. */
  val sorter: Comparator<C>?

  /**
   * Extracts the attribute value from this row value. This function is expected to consistently
   * return the same value for a given input and be quick to execute, like a getter for a data
   * class.
   */
  fun value(t: T): C

  /**
   * Indicates if the table should support grouping by this attribute. Currently, groupable columns
   * must be sortable.
   *
   * TODO: Consider adding a grouping function to allow grouping into buckets for high-cardinality
   *   attributes.
   */
  val isGroupable: Boolean
    get() = sorter != null

  object Unit : Attribute<Any, kotlin.Unit> {
    override val sorter = null
    override fun value(t: Any) = kotlin.Unit
  }

  companion object {
    fun <T> stringAttribute(
      sorter: Comparator<String> = naturalOrder(),
      isGroupable: Boolean = true,
      valueFn: (t: T) -> String
    ): Attribute<T, String> =
      object : Attribute<T, String> {
        override val sorter = sorter
        override fun value(t: T) = valueFn(t)
        override val isGroupable = isGroupable
      }
  }
}

typealias AttributeList<T> = List<Attribute<T, *>>

/**
 * A Category is a specific value of an attribute. For example, "Form factor: Tablet" or "API: 33".
 */
data class Category<T, C>(val attribute: Attribute<T, C>, val value: C) {
  fun matches(t: T) = attribute.value(t) == value
}

/** Combines this [Attribute] with a value to produce a [Category]. */
fun <T, C> Attribute<T, C>.withValue(row: T) = Category(this, value(row))

/** An immutable list of [categories][Category]. */
typealias CategoryList<T> = List<Category<T, *>>

/** Return the value of the given Attribute in this CategoryList, if present. */
operator fun <T, C> CategoryList<T>.get(key: Attribute<T, C>): C? = findCategory(key)?.value

/** Return the Category with the given Attribute in this CategoryList, if present. */
@Suppress("UNCHECKED_CAST") // safe by construction
fun <T, C> CategoryList<T>.findCategory(key: Attribute<T, C>): Category<T, C>? =
  find { it.attribute === key } as? Category<T, C>?

private fun <C> Comparator<C>?.withOrder(sortOrder: SortOrder): Comparator<C>? =
  this?.let {
    when (sortOrder) {
      SortOrder.ASCENDING -> it
      SortOrder.DESCENDING -> it.reversed()
      SortOrder.UNSORTED -> null
    }
  }

fun <T, C> Attribute<T, C>.valueSorter(sortOrder: SortOrder): Comparator<T>? =
  sorter?.withOrder(sortOrder)?.let { compareBy(it, this::value) }

data class ColumnSortOrder<T>(val attribute: Attribute<T, *>, val sortOrder: SortOrder)
