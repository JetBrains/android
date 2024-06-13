/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.type

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.psi.PsiFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * This tests the helper function defined in FileTypeUtils.kt file.
 *
 * Note: We don't provide the test case for a file with [AdaptiveIconFileType] type here.
 *
 * [AdaptiveIconFileType] checks the type by checking if the parent folder is a resource folder in
 * the project system. But the resource folder is not defined in a legacy project system, so it
 * always returns false. See
 * [com.android.tools.idea.projectsystem.LegacyDelegate.resourcesDirectoryUrls] and
 * [com.android.tools.idea.projectsystem.LegacyDelegate.resourcesDirectories]
 */
class FileTypeUtilsTest {
  @JvmField @Rule val projectRule = AndroidProjectRule.inMemory()

  @After
  fun cleanup() {
    // Some test cases register the type, clear here.
    DesignerTypeRegistrar.clearRegisteredTypes()
  }

  /**
   * This tests if the [Configuration] is same as before when getting the [Configuration] from same
   * file again.
   */
  @Test
  fun testCreateConfigurationForVirtualFile() {
    // We didn't register any file type here, so the file is always recognized as
    // DefaultDesignerFileType.
    // This test the else branch of VirtualFile.getConfiguration().

    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    // The content doesn't matter
    val file = projectRule.fixture.addFileToProject("res/layout/my_layout.xml", "")

    val config = file.virtualFile.getConfiguration(manager)
    val device = createTestDevice()
    // We keep the state here. If we get the config from same file again, it should have the same
    // device state.
    config.setDevice(device, true)

    val config2 = file.virtualFile.getConfiguration(manager)

    assertEquals(config, config2)
    assertEquals(config.deviceState, config2.deviceState)
  }

  @Test
  fun testDrawableFileAlwaysUseSameDevice() {
    DesignerTypeRegistrar.register(TestDrawableFileType)

    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    // The content doesn't matter
    val file = projectRule.fixture.addFileToProject("res/drawable/my_drawable.xml", TEST_ROOT_TAG)
    val type = file.typeOf()
    assertTrue(
      type is DrawableFileType
    ) // The type must be DrawableFileType otherwise this testing is meaningless.

    val config = file.virtualFile.getConfiguration(manager)
    val configDevice = config.device
    config.setDevice(createTestDevice(), true)

    val config2 = file.virtualFile.getConfiguration(manager)
    // Even changing the device, next time will still use the specific device when creating
    // configuration.
    assertEquals(configDevice, config2.device)
  }

  @Test
  fun testSameDeviceForDifferentDrawableFiles() {
    DesignerTypeRegistrar.register(TestDrawableFileType)

    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    // The content doesn't matter
    val file1 = projectRule.fixture.addFileToProject("res/drawable/my_drawable1.xml", TEST_ROOT_TAG)
    assertTrue(
      file1.typeOf() is DrawableFileType
    ) // The type must be DrawableFileType otherwise this testing is meaningless.

    val file2 = projectRule.fixture.addFileToProject("res/drawable/my_drawable2.xml", TEST_ROOT_TAG)
    assertTrue(
      file2.typeOf() is DrawableFileType
    ) // The type must be DrawableFileType otherwise this testing is meaningless.

    assertEquals(
      file1.virtualFile.getConfiguration(manager).device,
      file2.virtualFile.getConfiguration(manager).device,
    )
  }

  private fun createTestDevice(): Device {
    return Device.Builder()
      .apply {
        setTagId("")
        setName("Test")
        setId(Configuration.CUSTOM_DEVICE_ID)
        setManufacturer("")
        addSoftware(Software())
        addState(
          State().apply {
            name = "TestState"
            isDefaultState = true
            hardware =
              Hardware().apply {
                screen = Screen().apply { pixelDensity = Density.XXXHIGH }
                orientation = ScreenOrientation.LANDSCAPE
              }
          }
        )
      }
      .build()
  }
}

private const val TEST_ROOT_TAG = "<test>"

// This class is used to test the default implementation of DrawableFileType
object TestDrawableFileType : DrawableFileType(setOf(TEST_ROOT_TAG)) {
  override fun isResourceTypeOf(file: PsiFile): Boolean = true

  override fun getToolbarActionGroups(surface: DesignSurface<*>): ToolbarActionGroups =
    ToolbarActionGroups(surface)
}
