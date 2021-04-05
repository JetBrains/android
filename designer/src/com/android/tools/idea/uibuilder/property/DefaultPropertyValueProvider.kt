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
package com.android.tools.idea.uibuilder.property

/**
 * Specifies how default property values are provided.
 */
interface DefaultPropertyValueProvider {

  /**
   * Return the default value of the specified [property] as a string.
   */
  fun provideDefaultValue(property: NelePropertyItem): String? = null

  /**
   * Return true if the default values may have changed since they were generated last.
   */
  fun hasDefaultValuesChanged(): Boolean = false

  /**
   * Clear any cache property values.
   */
  fun clearCache() {}
}
