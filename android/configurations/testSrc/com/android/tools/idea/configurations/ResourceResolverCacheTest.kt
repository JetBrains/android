/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations

import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.testing.waitForUpdates
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

class ResourceResolverCacheTest : AndroidTestCase() {

  @Throws(Exception::class)
  fun test() {
    val file1 = myFixture.copyFileToProject("render/layout1.xml", "res/layout/layout1.xml")
    val file2 = myFixture.copyFileToProject("render/layout2.xml", "res/layout/layout2.xml")
    val file3 = myFixture.copyFileToProject("configurations/strings.xml", "res/values/strings.xml")

    assertNotNull(file1)
    assertNotNull(file2)
    assertNotNull(file3)

    val facet = AndroidFacet.getInstance(myModule)
    assertNotNull(facet)

    val currentProject = project
    val psiFile1 = PsiManager.getInstance(currentProject).findFile(file1!!)
    assertNotNull(psiFile1)

    val psiFile2 = PsiManager.getInstance(currentProject).findFile(file2!!)
    assertNotNull(psiFile2)

    val psiFile3 = PsiManager.getInstance(currentProject).findFile(file3!!)
    assertNotNull(psiFile3)

    val configurationManager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(configurationManager)

    val configuration1 = configurationManager.getConfiguration(file1)
    val configuration2 = configurationManager.getConfiguration(file2)

    assertNotNull(configuration1.theme)
    assertEquals(configuration2.theme, configuration1.theme)

    val resolver1 = configuration1.resourceResolver
    val resolver2 = configuration2.resourceResolver
    assertSame(resolver1, resolver2)
    assertSame(resolver1, configuration1.resourceResolver)

    // Use explicit setter to avoid 'val cannot be reassigned' due to @Nullable mismatch in Java
    configuration1.setTheme("Theme.Light")
    val resolver1b = configuration1.resourceResolver
    assertNotSame(resolver1b, resolver1)
    assertNotSame(resolver1b, resolver2)
    assertSame(resolver1b, configuration1.resourceResolver)

    configuration2.setTheme("Theme.Light")
    assertSame(resolver1b, configuration2.resourceResolver)

    configuration1.setGestureNav(false)
    val resolver1c = configuration1.resourceResolver
    assertNotSame(resolver1c, resolver1b)
    assertNotSame(resolver1c, resolver2)
    assertSame(resolver1c, configuration1.resourceResolver)

    configuration2.setGestureNav(false)
    assertSame(resolver1c, configuration2.resourceResolver)

    // Test project resource changes, should invalidate
    val resources = StudioResourceRepositoryManager.getModuleResources(myFacet)
    assertNotNull(resources)
    assertEquals("Cancel", configuration1.resourceResolver.findResValue("@string/cancel", false)!!.value)

    val generation = resources!!.modificationCount
    val rescans = resources.fileRescans

    WriteCommandAction.runWriteCommandAction(null) {
      val value = (psiFile3 as XmlFile).rootTag!!.subTags[1].value
      assertEquals("Cancel", value.trimmedText)
      value.setText("\"FooBar\"") // Explicit setter for XmlTagValue
    }

    waitForUpdates(resources)

    assertThat(resources.modificationCount).isGreaterThan(generation)
    assertThat(resources.fileRescans).isEqualTo(rescans + 1)
    assertNotSame(resolver1c, configuration1.resourceResolver)
    assertEquals("FooBar", configuration1.resourceResolver.findResValue("@string/cancel", false)!!.value)

    val cache = configuration1.settings.resolverCache
    assertSame(cache, configuration2.settings.resolverCache)
  }

  fun testCustomConfiguration() {
    val file1 = myFixture.copyFileToProject("render/layout1.xml", "res/layout/layout1.xml")
    val configurationManager = ConfigurationManager.getOrCreateInstance(myModule)
    val configuration = configurationManager.getConfiguration(file1!!)
    val cache = configurationManager.resolverCache

    assertTrue(cache.resolverMap.isEmpty())
    assertTrue(cache.appResourceMap.isEmpty())
    assertTrue(cache.frameworkResourceMap.isEmpty())

    var resolver = configuration.resourceResolver

    assertEquals(1, cache.resolverMap.size)
    assertEquals(1, cache.appResourceMap.size)
    assertEquals(1, cache.frameworkResourceMap.size)

    val originalResolverMapKey = cache.resolverMap.keys.firstOrNull()
    val originalFrameworkResourceMapKey = cache.frameworkResourceMap.keys.firstOrNull()
    val originalAppResourceMapKey = cache.appResourceMap.keys.firstOrNull()

    // Framework and App resource maps use the same key up to overlays
    val frameworkKeyWithoutOverlays = originalFrameworkResourceMapKey?.let { it.substring(0, it.indexOf("-Overlays")) }
    assertEquals(originalAppResourceMapKey, frameworkKeyWithoutOverlays)

    val original = configuration.device
    val builder = Device.Builder(original)
    builder.setName("Custom")
    builder.setId(Configuration.CUSTOM_DEVICE_ID)
    val customDevice = builder.build()

    customDevice.allStates.forEach { state ->
      val screen = state.hardware.screen
      // Explicit setters for screen dimensions
      screen.setXDimension(100)
      screen.setYDimension(100)
    }

    configuration.setEffectiveDevice(customDevice, customDevice.getState("Portrait"))
    var newResolver = configuration.resourceResolver

    // The original config should be there plus we've added the custom one
    assertEquals(2, cache.resolverMap.size)
    assertEquals(2, cache.appResourceMap.size)
    assertEquals(2, cache.frameworkResourceMap.size)
    assertContainsElements(cache.resolverMap.keys, originalResolverMapKey!!)
    assertContainsElements(cache.appResourceMap.keys, originalAppResourceMapKey!!)
    assertContainsElements(cache.frameworkResourceMap.keys, originalFrameworkResourceMapKey!!)

    // Get the custom key used for this device
    val customResolverMapKey = cache.resolverMap.keys.first { it != originalResolverMapKey }
    val customResourceMapKey = cache.appResourceMap.keys.first { it != originalAppResourceMapKey }

    assertNotSame(resolver, newResolver)
    assertSame(newResolver, configuration.resourceResolver)
    resolver = newResolver

    // No new configuration created
    assertEquals(2, cache.resolverMap.size)

    configuration.setTheme("android:Theme.Material")
    newResolver = configuration.resourceResolver
    assertNotSame(resolver, newResolver)

    // This should replace only the custom config
    assertEquals(2, cache.resolverMap.size)
    assertEquals(2, cache.appResourceMap.size)
    assertEquals(2, cache.frameworkResourceMap.size)
    assertContainsElements(cache.resolverMap.keys, originalResolverMapKey)
    assertContainsElements(cache.appResourceMap.keys, originalAppResourceMapKey)
    assertContainsElements(cache.frameworkResourceMap.keys, originalFrameworkResourceMapKey)

    // We've only changed the theme so the resource maps won't change. They are indexed per device config.
    assertDoesntContain(cache.resolverMap.keys, customResolverMapKey)
  }
}
