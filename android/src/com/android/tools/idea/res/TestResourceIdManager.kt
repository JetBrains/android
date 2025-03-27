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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.res.ids.ResourceIdManager
import com.android.tools.res.ids.StubbedResourceIdManager
import com.intellij.openapi.module.Module

class TestResourceIdManager private constructor(module: Module) :
  StubbedResourceIdManager(StudioFlags.USE_BYTECODE_R_CLASS_PARSING.get()) {
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
    fun getManager(module: Module) =
      module.getService(ResourceIdManager::class.java) as TestResourceIdManager
  }
}
