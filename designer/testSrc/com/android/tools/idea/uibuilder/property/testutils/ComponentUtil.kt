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
import com.android.ide.common.resources.stripPrefixFromId
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil

object ComponentUtil {
  fun component(tag: String): ComponentDescriptor = ComponentDescriptor(tag)

  fun createComponents(
    projectRule: AndroidProjectRule,
    vararg descriptors: ComponentDescriptor,
    parentTag: String = SdkConstants.LINEAR_LAYOUT,
    resourceFolder: String = SdkConstants.FD_RES_LAYOUT
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
            component(parentTag).withBounds(0, 0, 1000, 1500).children(*descriptors)
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
                "com.example.MyActivity"
              )
              .children(*descriptors)
          )
        else -> throw NotImplementedError()
      }
    val nlModel = builder.build()
    val result = mutableListOf<NlComponent>()
    when (resourceFolder) {
      SdkConstants.FD_RES_XML ->
        descriptors.mapNotNullTo(result) { descriptor ->
          nlModel.find { it.tagName == descriptor.tagName }
        }
      else -> descriptors.mapNotNullTo(result) { nlModel.find(stripPrefixFromId(it.id!!)) }
    }
    return result
  }
}
