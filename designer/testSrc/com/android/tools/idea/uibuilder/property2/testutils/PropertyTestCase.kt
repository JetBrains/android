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
package com.android.tools.idea.uibuilder.property2.testutils

import com.android.SdkConstants
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent

/**
 * Test base class used in some property tests.
 */
abstract class PropertyTestCase : MinApiLayoutTestCase() {

  /**
   * Create components corresponding to the specified component [descriptors].
   *
   * This method creates a NlModel with a LinearLayout that contains the
   * components specified.
   *  - Bounds are always added.
   *  - IDs are set to the tagName if an ID is not specified.
   */
  fun createComponents(vararg descriptors: ComponentDescriptor): List<NlComponent> {
    var y = 0
    for (descriptor in descriptors) {
      descriptor.withBounds(0, y, 100, 100)
      y += 100
      if (descriptor.id == null) {
        descriptor.id(SdkConstants.NEW_ID_PREFIX + descriptor.tagName)
      }
    }
    val builder = model(
        "linear.xml",
        component(SdkConstants.LINEAR_LAYOUT)
          .withBounds(0, 0, 1000, 1500)
          .id("@id/linear")
          .matchParentWidth()
          .matchParentHeight()
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_CONTEXT, "com.example.MyActivity")
          .children(*descriptors)
    )
    val nlModel = builder.build()
    val result = mutableListOf<NlComponent>()
    for (descriptor in descriptors) {
      result.add(nlModel.find(NlComponent.stripId(descriptor.id)!!)!!)
    }
    return result
  }
}
