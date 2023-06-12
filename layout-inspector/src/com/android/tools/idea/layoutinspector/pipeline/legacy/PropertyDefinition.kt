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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.tools.idea.layoutinspector.properties.PropertyType

private val IDENTITY: (String) -> String = { it }

/**
 * A definition of a Property used to map legacy property names into attribute names.
 */
class PropertyDefinition(
  /**
   * A name of a runtime value in an Android View.
   *
   * If possible this should be the same as the attribute id available to the user in XML.
   */
  val name: String,

  /**
   * The type of this property.
   */
  val type: PropertyType,

  /**
   * A mapper lambda to convert legacy values into a more readable form.
   *
   * If possible the resulting value should be the same format that a user would
   * apply to the relevant XML attribute.
   */
  val value_mapper: (String) -> String = IDENTITY
)
