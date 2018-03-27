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

import com.android.SdkConstants.*
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.SyncLayoutlibSceneManager
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.android.tools.idea.uibuilder.property2.NelePropertyType
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

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
        descriptor.id(NEW_ID_PREFIX + descriptor.tagName)
      }
    }
    val builder = model(
        "linear.xml",
        component(LINEAR_LAYOUT)
          .withBounds(0, 0, 1000, 1500)
          .id("@id/linear")
          .matchParentWidth()
          .matchParentHeight()
          .withAttribute(TOOLS_URI, ATTR_CONTEXT, "com.example.MyActivity")
          .children(*descriptors)
    )
    val nlModel = builder.build()
    val result = mutableListOf<NlComponent>()
    for (descriptor in descriptors) {
      result.add(nlModel.find(NlComponent.stripId(descriptor.id)!!)!!)
    }
    return result
  }

  /**
   * Create a [NelePropertyItem] for testing purposes.
   */
  fun createPropertyItem(
    attrName: String,
    type: NelePropertyType,
    components: List<NlComponent>,
    model: NelePropertiesModel = NelePropertiesModel(testRootDisposable, myFacet)
  ): NelePropertyItem {
    val nlModel = components[0].model as SyncNlModel
    model.surface = nlModel.surface
    val resourceManagers = ModuleResourceManagers.getInstance(myFacet)
    val systemResourceManager = resourceManagers.systemResourceManager
    val definition = systemResourceManager?.attributeDefinitions?.getAttrDefByName(attrName)
    return NelePropertyItem(ANDROID_URI, attrName, type, definition, "", model, components)
  }

  fun getSceneManager(property: NelePropertyItem): SyncLayoutlibSceneManager {
    return property.model.surface!!.currentSceneView!!.sceneManager as SyncLayoutlibSceneManager
  }
}
