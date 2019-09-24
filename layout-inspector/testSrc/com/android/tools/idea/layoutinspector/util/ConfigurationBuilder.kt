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
package com.android.tools.idea.layoutinspector.util

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.fir.resolve.getOrPut

class ConfigurationBuilder(facet: AndroidFacet) {
  private val defaultPackage = Manifest.getMainManifest(facet)!!.`package`.value!!
  private var stringId = 0
  private val strings = mutableMapOf<String, Int>()

  fun makeConfiguration(): Pair<LayoutInspectorProto.ResourceConfiguration, StringTable> {
    val config = LayoutInspectorProto.ResourceConfiguration.newBuilder()
      .setAppPackageName(addString(defaultPackage))
      .setTheme(addResource(ResourceReference.style(ResourceNamespace.TODO(), "AppTheme")))
      .build()
    val stringTable = StringTable(strings.map { LayoutInspectorProto.StringEntry.newBuilder().setId(it.value).setStr(it.key).build() })
    return Pair(config, stringTable)
  }

  private fun addString(string: String): Int = strings.getOrPut(string) { ++stringId }

  private fun addResource(resource: ResourceReference): LayoutInspectorProto.Resource {
    return LayoutInspectorProto.Resource.newBuilder()
      .setNamespace(addString(resource.namespace.packageName ?: defaultPackage))
      .setName(addString(resource.name))
      .setType(addString(resource.resourceType.getName()))
      .build()
  }
}
