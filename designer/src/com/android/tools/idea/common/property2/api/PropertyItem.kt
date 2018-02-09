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

import javax.swing.Icon

/**
 * Defines basic information about a property.
 */
interface PropertyItem {
  /**
   * The namespace of the property e.g. "http://schemas.android.com/apk/res/android"
   */
  val namespace: String

  /**
   * Optional icon to indicate a namespace
   */
  val namespaceIcon: Icon?
    get() = null

  /**
   * The name of the property e.g. "gravity"
   */
  val name: String

  /**
   * The current raw property value
   */
  var value: String?

  /**
   * Whether the [value] is a reference value
   */
  val isReference: Boolean

  /**
   * The tooltip for this property
   */
  val tooltip: String
    get() = ""

  /**
   * The current recursively resolved property value
   *
   * An implementation must override this method if
   * the value is a reference to something else, or
   * if the value is null but there is a default value.
   */
  val resolvedValue: String?
    get() = value

  /**
   * A validation method.
   *
   * @return an error message, or an empty string if there is no error.
   */
  fun validate(editedValue: String): String = ""

  /**
   * The matching design property, i.e. tools attribute
   */
  val designProperty: PropertyItem
    get() = throw IllegalStateException()
}
