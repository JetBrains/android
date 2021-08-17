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
package com.android.tools.idea.uibuilder.property.testutils

import com.android.SdkConstants.ATTR_CONTEXT
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.MinApiLayoutTestCase
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import com.android.tools.idea.uibuilder.scene.SyncLayoutlibSceneManager
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
  fun createComponents(vararg descriptors: ComponentDescriptor, parentTag: String = LINEAR_LAYOUT): List<NlComponent> {
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
        component(parentTag)
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
      result.add(nlModel.find(stripPrefixFromId(descriptor.id!!))!!)
    }
    return result
  }

  /**
   * Create a [NlPropertyItem] for testing purposes.
   */
  fun createPropertyItem(
    attrNamespace: String,
    attrName: String,
    type: NlPropertyType,
    components: List<NlComponent>,
    model: NlPropertiesModel = NlPropertiesModel(testRootDisposable, myFacet)
  ): NlPropertyItem {
    val nlModel = components[0].model as SyncNlModel
    model.surface = nlModel.surface
    val resourceManagers = ModuleResourceManagers.getInstance(myFacet)
    val frameworkResourceManager = resourceManagers.frameworkResourceManager
    val definition =
        frameworkResourceManager?.attributeDefinitions?.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, attrName))
    return NlPropertyItem(attrNamespace, attrName, type, definition, "", "", model, components)
  }

  fun getSceneManager(property: NlPropertyItem): SyncLayoutlibSceneManager {
    return property.model.surface!!.focusedSceneView!!.sceneManager as SyncLayoutlibSceneManager
  }
}
