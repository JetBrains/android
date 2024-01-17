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
package com.android.tools.idea.module

import com.android.tools.module.ModuleKey
import com.intellij.openapi.module.Module
import java.util.WeakHashMap

/**
 * Class that maintains a 1:1 mapping between a [Module] and a [ModuleKey]. The key can be used as a replacement key
 * in maps without forcing a strong reference to a Module that would make leaks harder to debug.
 *
 * You are not meant to use this as a two way mapping so there is no way to get the [Module] from the [ModuleKey].
 */
object ModuleKeyManager {
  private val moduleKeyMapping = WeakHashMap<Module, ModuleKey>()

  fun getKey(module: Module): ModuleKey =
    moduleKeyMapping.getOrPut(module) { ModuleKey() }
}
