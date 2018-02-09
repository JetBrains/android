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
 * A model of the data that should be shown in the properties panel.
 *
 * A client would implement this model and pass it to the top level
 * implementation of the properties view. The view would listen to
 * update events with a [PropertiesModelListener]. This puts the client
 * in control of when the properties view should be updated.
 *
 * @param P A client defined PropertyItem class.
 */
interface PropertiesModel<out P: PropertyItem> {
  /**
   * The properties that should be shown in the properties panel.
   */
  val properties: PropertiesTable<P>

  /**
   * The model should notify the properties of changes to the properties table above.
   */
  fun addListener(listener: PropertiesModelListener)

  /**
   * Remove a listener added with [addListener].
   */
  fun removeListener(listener: PropertiesModelListener)
}
