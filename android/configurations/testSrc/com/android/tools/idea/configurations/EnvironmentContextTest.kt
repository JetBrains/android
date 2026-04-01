/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.IAndroidTarget
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.configurations.ConfigurationSettings
import com.android.tools.configurations.EnvironmentContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito

@RunWith(JUnit4::class)
class EnvironmentContextTest {

  private class StubContext : EnvironmentContext.Context {
    override val settings: ConfigurationSettings = Mockito.mock(ConfigurationSettings::class.java)
    override val editedConfig: FolderConfiguration = FolderConfiguration()
    override val preferredTheme: String = "@style/DefaultTheme"

    // Custom backing field for the test to manipulate
    var calculatedActivity: String? = null

    override fun calculateActivity(): String? {
      return calculatedActivity
    }

    override fun getTargetForRendering(target: IAndroidTarget?): IAndroidTarget? {
      return null
    }
  }

  @Test
  fun testActivityCachingAndCalculation() {
    val context = StubContext()
    val env = EnvironmentContext(context)

    assertNull("Activity should be null initially if context calculates null", env.activity)

    context.calculatedActivity = "com.example.MainActivity"
    assertNull("Activity should remain cached as null (via internal NO_ACTIVITY marker)", env.activity)

    val flags = env.setActivity("com.example.NewActivity")

    assertEquals(ConfigurationListener.CFG_ACTIVITY, flags)
    assertEquals("com.example.NewActivity", env.activity)
  }

  @Test
  fun testThemePrefixResolution() {
    val context = StubContext()
    val env = EnvironmentContext(context)

    assertEquals("@style/DefaultTheme", env.theme)

    val flags = env.setTheme("MyCustomTheme")
    assertEquals(ConfigurationListener.CFG_THEME, flags)

    // Verifies ResourceUtils automatically prepends @style/
    assertEquals("@style/MyCustomTheme", env.theme)
  }
}
