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
package com.android.tools.res.ids.apk

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.tools.res.apk.extractNameAndNamespace
import com.android.tools.res.apk.forEveryResource
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.res.ids.ResourceIdManagerBase
import com.android.tools.res.ids.ResourceIdManagerModelModule
import com.android.tools.res.ids.SingleNamespaceIdMapping
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.function.Consumer

/**
 * Resolve application resources from the information stored in the apk resource table (resources.arsc). Also, functionality from
 * [ResourceIdManagerBase] allows it to resolve android framework ids.
 */
class ApkResourceIdManager : ResourceIdManagerBase(
  ResourceIdManagerModelModule.NO_NAMESPACING_APP,
  true
) {
  private val apkResources = SingleNamespaceIdMapping(ResourceNamespace.RES_AUTO)

  // No-op, we should prevent loading from R-classes
  override fun resetCompiledIds(rClassProvider: Consumer<ResourceIdManager.RClassParser>) { }

  override fun findById(id: Int): ResourceReference? {
    return apkResources.findById(id) ?: super.findById(id)
  }

  override fun getCompiledId(resource: ResourceReference): Int? {
    return apkResources.getId(resource).let { if (it == 0) null else it }
  }

  fun loadApkResources(apkPath: String) {
    forEveryResource(apkPath) { _, resType, _, resId, typeChunkEntry ->
      val (_, name) = extractNameAndNamespace(typeChunkEntry.key())
      apkResources.fromIdMap.put(resId, resType to name)
      apkResources.toIdMap.getOrPut(resType, ::Object2IntOpenHashMap).put(name, resId)
    }
  }
}