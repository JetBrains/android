/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.resources.stripPrefixFromId
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.NlPropertyType
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.ModuleResourceManagers

object ComponentUtil {
  fun component(tag: String): ComponentDescriptor = ComponentDescriptor(tag)

  fun createComponents(
    projectRule: AndroidProjectRule,
    vararg descriptors: ComponentDescriptor,
    parentTag: String = SdkConstants.LINEAR_LAYOUT,
    resourceFolder: String = SdkConstants.FD_RES_LAYOUT,
  ): List<NlComponent> {
    var y = 0
    for (descriptor in descriptors) {
      descriptor.withBounds(0, y, 100, 100)
      y += 100
      if (descriptor.id == null && resourceFolder == SdkConstants.FD_RES_LAYOUT) {
        descriptor.id(SdkConstants.NEW_ID_PREFIX + descriptor.tagName)
      }
    }
    val builder =
      when (resourceFolder) {
        SdkConstants.FD_RES_XML ->
          NlModelBuilderUtil.model(
            projectRule,
            resourceFolder,
            "preferences.xml",
            component(parentTag).withBounds(0, 0, 1000, 1500).children(*descriptors),
          )
        SdkConstants.FD_RES_LAYOUT ->
          NlModelBuilderUtil.model(
            projectRule,
            resourceFolder,
            "linear.xml",
            component(parentTag)
              .withBounds(0, 0, 1000, 1500)
              .id("@id/linear")
              .matchParentWidth()
              .matchParentHeight()
              .withAttribute(
                SdkConstants.TOOLS_URI,
                SdkConstants.ATTR_CONTEXT,
                "com.example.MyActivity",
              )
              .children(*descriptors),
          )
        else -> throw NotImplementedError()
      }
    val nlModel = builder.build()
    val result = mutableListOf<NlComponent>()
    when (resourceFolder) {
      SdkConstants.FD_RES_XML ->
        descriptors.mapNotNullTo(result) { descriptor ->
          nlModel.treeReader.find { it.tagName == descriptor.tagName }
        }
      else ->
        descriptors.mapNotNullTo(result) { nlModel.treeReader.find(stripPrefixFromId(it.id!!)) }
    }
    return result
  }

  /** Create a [NlPropertyItem] for testing purposes. */
  fun createPropertyItem(
    projectRule: AndroidProjectRule,
    attrNamespace: String,
    attrName: String,
    type: NlPropertyType,
    components: List<NlComponent>,
    model: NlPropertiesModel =
      NlPropertiesModel(
        projectRule.testRootDisposable,
        AndroidFacet.getInstance(projectRule.module)!!,
      ),
  ): NlPropertyItem {
    val nlModel = components[0].model as SyncNlModel
    model.surface = nlModel.surface
    val resourceManagers =
      ModuleResourceManagers.getInstance(AndroidFacet.getInstance(projectRule.module)!!)
    val frameworkResourceManager = resourceManagers.frameworkResourceManager
    val definition =
      frameworkResourceManager
        ?.attributeDefinitions
        ?.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, attrName))
    return NlPropertyItem(attrNamespace, attrName, type, definition, "", "", model, components)
      .also {
        runBlocking {
          // Wait for the ResourceResolver to be initialized avoiding the first lookup to be done
          // asynchronously.
          delayUntilCondition(10) { it.model.resolver != null }
        }
      }
  }
}
