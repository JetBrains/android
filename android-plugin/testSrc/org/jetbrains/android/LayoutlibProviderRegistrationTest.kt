// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LightPlatformTestCase

private const val LAYOUTLIB_PROVIDER_EP = "com.android.tools.idea.layoutlib.layoutLibraryProvider"
private const val LAYOUTLIB_PROVIDER = "com.android.layoutlib.LayoutlibProvider"

class LayoutlibProviderRegistrationTest : LightPlatformTestCase() {
  fun testAndroidPluginRegistersLayoutlibProviderWithoutDesignTools() {
    assertEquals(listOf(LAYOUTLIB_PROVIDER), layoutlibProviders("org.jetbrains.android"))
    assertEquals(emptyList<String>(), layoutlibProviders("com.android.tools.design"))
    assertNotNull(Class.forName(LAYOUTLIB_PROVIDER, false, javaClass.classLoader))
  }

  private fun layoutlibProviders(pluginId: String): List<String?> {
    val plugin = checkNotNull(PluginManagerCore.findPlugin(PluginId.getId(pluginId))) { "$pluginId is not on the test classpath" }
    return plugin.extensions[LAYOUTLIB_PROVIDER_EP].orEmpty().map { it.implementation }
  }
}
