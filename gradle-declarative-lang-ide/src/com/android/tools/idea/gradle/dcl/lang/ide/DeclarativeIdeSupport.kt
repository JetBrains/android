/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.ide

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly

object DeclarativeIdeSupport {
  private fun getRegistryValue() = Registry.get("gradle.declarative.ide.support")

  @JvmStatic
  fun isEnabled(): Boolean = getRegistryValue().asBoolean()

  @TestOnly
  @JvmStatic
  fun override(value: Boolean) {
    getRegistryValue().setValue(value)
  }

  @TestOnly
  @JvmStatic
  fun clearOverride() {
    getRegistryValue().resetToDefault()
  }
}