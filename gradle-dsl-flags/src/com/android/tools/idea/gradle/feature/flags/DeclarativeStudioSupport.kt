// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
// this package should remain different from the one in AOSP to avoid issues with class loading.
package com.android.tools.idea.gradle.feature.flags

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.TestOnly

object DeclarativeStudioSupport {
  private fun getRegistryValue() = Registry.get("gradle.declarative.studio.support")

  @JvmStatic
  fun isEnabled(): Boolean = getRegistryValue().asBoolean()

  @JvmStatic
  @TestOnly
  fun override(value: Boolean) { getRegistryValue().setValue(value) }

  @JvmStatic
  @TestOnly
  fun clearOverride() { getRegistryValue().resetToDefault() }
}