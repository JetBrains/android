/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_PROJECT_JDK
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions

class UseProjectJdkAsGradleJvmListenerTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  @Test
  fun `changes to USE_PROJECT_JDK when used`() {
    gradleProjectRule.load(SIMPLE_APPLICATION)
    val project = gradleProjectRule.project
    val projectSettings = GradleProjectSettingsFinder.getInstance().findGradleProjectSettings(project)
    assertThat(projectSettings).isNotNull()
    projectSettings!!.gradleJvm = USE_JAVA_HOME
    val spySettings = spy(projectSettings)
    val listener = UseProjectJdkAsGradleJvmListener(project)
    listener.changeGradleProjectSetting(spySettings)
    verify(spySettings).gradleJvm = USE_PROJECT_JDK
    verifyNoMoreInteractions(spySettings)
  }
}