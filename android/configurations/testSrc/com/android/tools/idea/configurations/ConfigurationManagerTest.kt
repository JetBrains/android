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

import com.android.ide.common.resources.Locale
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.devices.Device
import com.android.tools.configurations.Configuration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ref.GCUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet

class ConfigurationManagerTest : AndroidTestCase() {

  fun testGetLocales() {
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no-rNO/layout1.xml")
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no/layout1.xml")
    myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-se/layout2.xml")

    val facet = AndroidFacet.getInstance(myModule)
    assertNotNull(facet)
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(manager)
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule))

    val locales = manager.localesInProject
    assertEquals(listOf(Locale.create("no"), Locale.create("no-rNO"), Locale.create("se")), locales)
  }

  fun testCaching() {
    val file1 = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")
    val file2 = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout-no-rNO/layout1.xml")

    val facet = AndroidFacet.getInstance(myModule)
    assertNotNull(facet)
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(manager)
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule))

    var configuration1: ConfigurationForFile? = manager.getConfiguration(file1)
    var configuration2: ConfigurationForFile? = manager.getConfiguration(file2)
    assertNotSame(configuration1, configuration2)
    assertSame(configuration1, manager.getConfiguration(file1))
    assertSame(configuration2, manager.getConfiguration(file2))
    assertSame(file1, configuration1?.file)
    assertSame(file2, configuration2?.file)

    // GC test: Ensure that we keep a cache through the first GC, but not if
    // we nearly run out of memory:
    assertTrue(manager.hasCachedConfiguration(file1))
    assertTrue(manager.hasCachedConfiguration(file2))

    configuration1 = null
    configuration2 = null
    System.gc()
    assertTrue(manager.hasCachedConfiguration(file1))
    assertTrue(manager.hasCachedConfiguration(file2))

    var iterations = 0
    do {
      // The amount of memory this method allocates since merging 181.3263.15 is not enough to collect soft references. Since this is the
      // only Android test that uses that, we just try a couple of times in a loop.
      GCUtil.tryGcSoftlyReachableObjects()
      iterations++
    } while (manager.hasCachedConfiguration(file1) && iterations < 10)

    System.gc()
    assertFalse(manager.hasCachedConfiguration(file1))
    assertFalse(manager.hasCachedConfiguration(file2))
  }

  /**
   * Check that [ConfigurationManager.getConfiguration] does not need the read lock and will acquire it if needed. Regression test for
   * b/162537840
   */
  fun testNoReadAction() {
    val file1 =
      myFixture.addFileToProject("res/values/values.xml", "<resources> <color name=\"myColor\">#FF00FF</color> </resources>").virtualFile

    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(manager)

    val devices = manager.devices
    assertTrue("The existing device list is expected to contain at least 5 devices.", devices.size > 5)

    manager.selectDevice(devices[0])
    manager.selectDevice(devices[1])
    manager.selectDevice(devices[2])

    AppExecutorUtil.getAppExecutorService()
      .submit {
        try {
          assertNotNull(manager.getConfiguration(file1))
        } catch (t: Throwable) {
          fail("No exception expected calling ConfigurationManager#getConfiguration")
        }
      }
      .get()
  }

  fun testWearProjectUsesWearDeviceByDefault() {
    val manifest = AppExecutorUtil.getAppExecutorService().submit<Manifest?> { Manifest.getMainManifest(myFacet) }.get()
    assertNotNull(manifest)

    WriteCommandAction.runWriteCommandAction(project) {
      val feature = manifest!!.addUsesFeature()
      feature.name.stringValue = "android.hardware.type.watch"
    }

    val file = myFixture.addFileToProject("res/layout/layout.xml", LAYOUT_FILE_TEXT)

    val config: Configuration =
      AppExecutorUtil.getAppExecutorService()
        .submit<ConfigurationForFile> {
          try {
            ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file.virtualFile)
          } catch (t: Throwable) {
            throw AssertionError("No exception expected calling ConfigurationManager#getConfiguration", t)
          }
        }
        .get()

    assertTrue(Device.isWear(config.device))
  }

  fun testDefaultThemeCompute() {
    val manifest = AppExecutorUtil.getAppExecutorService().submit<Manifest?> { Manifest.getMainManifest(myFacet) }.get()
    assertNotNull(manifest)

    WriteCommandAction.runWriteCommandAction(project) { manifest!!.application.theme.stringValue = "@style/break" }

    val file = myFixture.addFileToProject("res/layout/layout.xml", LAYOUT_FILE_TEXT)
    val config = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file.virtualFile)
    assertEquals("@style/break", config.theme)
  }

  fun testPostSplashScreenThemeResolution() {
    myFixture.addFileToProject(
      "res/values/styles.xml",
      """
      <resources>
        <!-- Base application theme. -->
        <style name="Theme.TheTheme">
        </style>
        <!-- Base application theme. -->
        <style name="Theme.SplashTheme" parent="Theme">
            <item name="postSplashScreenTheme">@style/Theme.TheTheme</item>
        </style>
      </resources>
      """
        .trimIndent(),
    )

    val manifest = AppExecutorUtil.getAppExecutorService().submit<Manifest?> { Manifest.getMainManifest(myFacet) }.get()
    assertNotNull(manifest)

    WriteCommandAction.runWriteCommandAction(project) { manifest!!.application.theme.stringValue = "@style/Theme.SplashTheme" }

    val file = myFixture.addFileToProject("res/layout/layout.xml", LAYOUT_FILE_TEXT)
    val config = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(file.virtualFile)
    assertEquals("@style/Theme.TheTheme", config.theme)
  }

  fun testFileStateSaving() {
    val file1 = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml")

    val facet = AndroidFacet.getInstance(myModule)
    assertNotNull(facet)
    var manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotNull(manager)
    assertSame(manager, ConfigurationManager.getOrCreateInstance(myModule))

    var configuration: Configuration = manager.getConfiguration(file1)

    val state = configuration.deviceState
    assertNotNull(state)
    assertEquals("Portrait", state!!.name)

    val device = configuration.device
    assertNotNull(device)

    val landscapeState = device!!.allStates.first { it.name == "Landscape" }
    configuration.deviceState = landscapeState
    configuration.save()

    // Dispose the original manager and verify that the new one restores the correct configuration
    val previousManager = manager
    previousManager.dispose()
    manager = ConfigurationManager.getOrCreateInstance(myModule)
    assertNotSame(previousManager, manager)
    assertNotNull(manager)

    configuration = manager.getConfiguration(file1)
    val restoredState = configuration.deviceState
    assertNotNull(restoredState)
    assertEquals("Landscape", restoredState!!.name)
  }

  /**
   * The parent directory is used by the [ConfigurationManager] to determine the folder configuration. In some cases, like rendering a
   * temporary drawable in memory, there might not be a parent directory so the configuration should be determined as the default one.
   * Regression test for b/364904755.
   */
  fun testDefaultFolderConfigurationOnNoParent() {
    @Language("xml")
    val drawable =
      """
      <?xml version="1.0" encoding="utf-8"?>
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
          android:shape="rectangle"
          android:tint="#FF0000">
         </shape>
      """
        .trimIndent()
    val file1 = LightVirtualFile("drawable.xml", drawable)
    val manager = ConfigurationManager.getOrCreateInstance(myModule)

    val configuration = manager.getConfiguration(file1)
    assertNotNull(configuration)
    assertEquals(FolderConfiguration(), configuration.editedConfig)
  }

  companion object {
    @Language("xml")
    private val LAYOUT_FILE_TEXT =
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
      """
        .trimIndent()
  }
}
