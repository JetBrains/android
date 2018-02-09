/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.property2.api

/**
 * Table of properties indexed by namespace and name.
 */
interface PropertiesTable<out P: PropertyItem> {

  /**
   * Returns a property [P] for the given [namespace] and property [name].
   *
   * If such a property doesn't exist an exception is raised.
   */
  operator fun get(namespace: String, name: String): P

  /**
   * Returns a property [P] for the given [namespace] and property [name].
   *
   * Null is returned if such a property doesn't exist.
   */
  fun getOrNull(namespace: String, name: String): P?

  /**
   * Return a map from name to property given the specified [namespace].
   */
  fun getByNamespace(namespace: String): Map<String, P>

  /**
   * Return true if this table is empty.
   */
  val isEmpty: Boolean

  /**
   * Return an arbitrary property from the table.
   */
  val first: P?

  /**
   * Return the number of properties in the table.
   */
  val size: Int
}
