/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import org.mockito.Mockito.mockStatic

import com.android.ide.common.repository.AgpVersion.Companion.parse
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockedStatic
import org.mockito.MockitoAnnotations

@RunsInEdt
class RecommendedPluginVersionUpgradeSuppressionTest : UpgradeGradleFileModelTestCase() {

  override val projectRule = AndroidProjectRule.withAndroidModel()

  @Mock
  private lateinit var pluginInfo: AndroidPluginInfo

  @Before
  fun setup() {
    MockitoAnnotations.openMocks(this)

    `when`(pluginInfo.pluginVersion).thenReturn(parse("8.0.0"))
  }

  @Test
  fun testNoPluginUpgradeRecommendationWhenPromptIsDisabled() {
    val current = parse("8.0.0")
    val recommended = parse("8.1.0")
    val project = projectRule.project

    mockStatic(AndroidPluginInfo::class.java).use { androidPluginInfoMock: MockedStatic<AndroidPluginInfo> ->
      // Simulate android.disableAgpUpgradePrompt=true.
      TestProjectSystem(project).useInTests()

      androidPluginInfoMock.`when`<AndroidPluginInfo> { AndroidPluginInfo.find(project) }.thenReturn(pluginInfo)
      TestCase.assertFalse(shouldRecommendPluginUpgrade(project, current, recommended).upgrade)
    }
  }
}
