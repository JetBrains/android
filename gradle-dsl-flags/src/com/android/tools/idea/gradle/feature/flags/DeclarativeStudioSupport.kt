// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.feature.flags

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly

object DeclarativeStudioSupport {
  private fun getRegistryValue() = Registry.Companion.get("gradle.declarative.studio.support")

  @JvmStatic
  fun isEnabled(): Boolean = getRegistryValue().asBoolean()

  @JvmStatic
  @TestOnly
  fun override(value: Boolean) { getRegistryValue().setValue(value) }

  @JvmStatic
  @TestOnly
  fun clearOverride() { getRegistryValue().resetToDefault() }
}