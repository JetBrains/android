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
package com.android.tools.idea.uibuilder.handlers.motion.property2

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.property2.NeleIdPropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import org.jetbrains.android.dom.attrs.AttributeDefinition

/**
 * Property item for an ID.
 */
class MotionIdPropertyItem(
  model: NelePropertiesModel,
  definition: AttributeDefinition?,
  componentName: String,
  components: List<NlComponent>,
  optionalValue1: Any? = null,
  optionalValue2: Any? = null
): NeleIdPropertyItem(model, definition, componentName, components, optionalValue1, optionalValue2) {

  /**
   * Override the default get method and delegate to the model.
   */
  override val rawValue: String?
    get() = model.getPropertyValue(this)
}
