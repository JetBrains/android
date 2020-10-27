/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.tools.adtui.model.stdui.ValueChangedListener
import javax.swing.Icon

/**
 * A model for a SelectedComponentPanel
 */
interface SelectedComponentModel {

  /**
   * Returns the id of a component
   */
  val id: String

  /**
   * Returns the icon of a component
   */
  val icon: Icon?

  /**
   * Returns the description of a component
   */
  val description: String

  /**
   * Register a [ValueChangedListener] to be notified whenever the component attributes above have changed.
   */
  fun addValueChangedListener(listener: ValueChangedListener) {}

  /**
   *  Remove a [ValueChangedListener] registered by [addValueChangedListener].
   */
  fun removeValueChangedListener(listener: ValueChangedListener) {}
}
