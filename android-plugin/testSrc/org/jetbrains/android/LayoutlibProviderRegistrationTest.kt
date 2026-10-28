// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.ExtensionDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.LightPlatformTestCase

private const val LAYOUTLIB_PROVIDER_EP = "com.android.tools.idea.layoutlib.layoutLibraryProvider"
private const val LAYOUTLIB_PROVIDER = "com.android.layoutlib.LayoutlibProvider"
private const val ANDROID_PLUGIN = "org.jetbrains.android"
private const val DESIGN_TOOLS_PLUGIN = "com.android.tools.design"
private const val LAYOUTLIB_PROVIDER_SERVICE =
  "META-INF/services/com.android.tools.idea.layoutlib.LayoutLibraryLoader\$LayoutLibraryProvider"
private const val LAYOUTLIB_PROVIDER_BRIDGE = "com.android.tools.idea.layoutlib.LayoutLibraryProviderBridge"

class LayoutlibProviderRegistrationTest : LightPlatformTestCase() {
  fun testBothPluginsRegisterLayoutlibProvider() {
    assertEquals(listOf(LAYOUTLIB_PROVIDER), layoutlibProviders(ANDROID_PLUGIN).map { it.implementation })
    assertEquals(listOf(LAYOUTLIB_PROVIDER), layoutlibProviders(DESIGN_TOOLS_PLUGIN).map { it.implementation })
    assertNotNull(Class.forName(LAYOUTLIB_PROVIDER, false, javaClass.classLoader))
  }

  fun testDesignToolsProviderComesFirstWhenBothPluginsAreEnabled() {
    // The registrations go in plugin load order, and Design Tools loads after the Android plugin because it depends on it.
    val registrations = listOf(ANDROID_PLUGIN, DESIGN_TOOLS_PLUGIN)
      .flatMap { pluginId -> layoutlibProviders(pluginId).map { ProviderRegistration(pluginId, it) } }
      .toMutableList()
    LoadingOrder.sortByLoadingOrder(registrations)
    assertEquals(listOf(DESIGN_TOOLS_PLUGIN, ANDROID_PLUGIN), registrations.map { it.pluginId })
  }

  fun testOnlyTheBridgeIsDeclaredAsLayoutlibProviderService() {
    val serviceFiles = javaClass.classLoader.getResources(LAYOUTLIB_PROVIDER_SERVICE).toList()
    assertEquals(serviceFiles.toString(), 1, serviceFiles.size)
    val providers = serviceFiles.single().readText().lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    assertEquals(listOf(LAYOUTLIB_PROVIDER_BRIDGE), providers)
  }

  private fun layoutlibProviders(pluginId: String): List<ExtensionDescriptor> {
    val plugin = checkNotNull(PluginManagerCore.findPlugin(PluginId.getId(pluginId))) { "$pluginId is not on the test classpath" }
    return plugin.extensions[LAYOUTLIB_PROVIDER_EP].orEmpty()
  }

  private class ProviderRegistration(val pluginId: String, descriptor: ExtensionDescriptor) : LoadingOrder.Orderable {
    override val orderId: String? = descriptor.orderId
    override val order: LoadingOrder = descriptor.order
  }
}
