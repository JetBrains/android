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
package com.android.tools.idea.res

import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.res.ids.ResourceIdManagerBase
import com.intellij.openapi.module.Module

/** Studio-specific implementation of [ResourceIdManager]. */
class StudioResourceIdManager private constructor(module: Module) :
  ResourceIdManagerBase(AndroidFacetResourceIdManagerModelModule(module)) {
  companion object {
    @JvmStatic fun get(module: Module) = module.getService(ResourceIdManager::class.java)!!

    /**
     * Return a [com.android.tools.idea.res.StudioResourceIdManager] instance if it already exists.
     * Use this method if you want to do something only if the service already exists, like clearing
     * the caches.
     */
    @JvmStatic
    fun getInstanceIfCreated(module: Module) =
      module.getServiceIfCreated(ResourceIdManager::class.java)
  }
}
