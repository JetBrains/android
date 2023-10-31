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
package com.android.tools.res.ids

import com.android.tools.res.ResourceNamespacing
import com.intellij.openapi.module.Module

private val STUB_MODULE = object : ResourceIdManagerModelModule {
  override val isAppOrFeature: Boolean = true
  override val namespacing: ResourceNamespacing = ResourceNamespacing.DISABLED
}

open class StubbedResourceIdManager : ResourceIdManagerBase(STUB_MODULE)

class TestResourceIdManager private constructor(module: Module) : StubbedResourceIdManager() {
  private var _finalIdsUsed = true
  override val finalIdsUsed: Boolean
    get() = _finalIdsUsed

  fun setFinalIdsUsed(finalIdsUsed: Boolean) {
    _finalIdsUsed = finalIdsUsed
  }

  fun resetFinalIdsUsed() {
    _finalIdsUsed = true
  }

  companion object {
    fun getManager(module: Module) = module.getService(ResourceIdManager::class.java) as TestResourceIdManager
  }
}