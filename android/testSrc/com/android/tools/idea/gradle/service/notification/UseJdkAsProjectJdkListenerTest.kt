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

import com.android.tools.idea.gradle.project.AndroidStudioGradleInstallationManager
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.eq
import org.mockito.Mockito.mockStatic

class UseJdkAsProjectJdkListenerTest {
  @JvmField
  @Rule
  val gradleProjectRule = AndroidGradleProjectRule()

  @Test
  fun `setJdkAsProjectJdk is called when used`() {
    gradleProjectRule.load(SIMPLE_APPLICATION)
    val project = gradleProjectRule.project
    val jdkPath = IdeSdks.getInstance().jdk!!.homePath!!
    val suffix = ".id.suffix"
    val listener = UseJdkAsProjectJdkListener(project, jdkPath, suffix)
    assertThat(listener.id).isEqualTo("${UseJdkAsProjectJdkListener.baseId()}$suffix")
    mockStatic(AndroidStudioGradleInstallationManager::class.java).use {
      listener.changeGradleProjectSetting()
      it.verify { AndroidStudioGradleInstallationManager.setJdkAsProjectJdk(eq(project), eq(jdkPath)) }
    }
  }
}