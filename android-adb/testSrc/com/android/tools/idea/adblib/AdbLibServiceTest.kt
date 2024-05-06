/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.adblib

import com.android.ddmlib.testing.FakeAdbRule
import com.android.flags.junit.FlagRule
import com.android.tools.idea.adb.FakeAdbServiceRule
import com.android.tools.idea.flags.StudioFlags.ADBLIB_ONE_SESSION_PER_PROJECT
import com.google.common.truth.Truth
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class AdbLibServiceTest {
  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbRule()
  private val fakeAdbServiceRule = FakeAdbServiceRule({ projectRule.project }, fakeAdbRule)
  private val oneSessionPerProject = FlagRule(ADBLIB_ONE_SESSION_PER_PROJECT)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(oneSessionPerProject).around(fakeAdbRule).around(fakeAdbServiceRule)!!

  private val project
    get() = projectRule.project

  @Test
  fun hostServicesShouldWork() {
    // Prepare
    val session = AdbLibService.getSession(project)

    // Act
    val version = runBlocking {
      session.hostServices.version()
    }

    // Assert
    Truth.assertThat(version).isGreaterThan(1)
  }

  @Test
  fun adbSessionInstanceShouldBeTheSameAsTheApplicationInstance() {
    // Act
    ADBLIB_ONE_SESSION_PER_PROJECT.override(false)
    val applicationSession = AdbLibApplicationService.instance.session
    val projectSession = AdbLibService.getSession(project)

    // Assert
    Truth.assertThat(projectSession).isSameAs(applicationSession)
  }

  @Test
  fun applicationAdbSessionInstanceShouldBeTheSameAsTheProjectInstance() {
    // Act
    ADBLIB_ONE_SESSION_PER_PROJECT.override(false)
    val projectSession = AdbLibService.getSession(project)
    val applicationSession = AdbLibApplicationService.instance.session

    // Assert
    Truth.assertThat(applicationSession).isSameAs(projectSession)
  }

  @Test
  fun adbSessionInstanceShouldNotBeTheSameAsTheApplicationInstance() {
    // Act
    ADBLIB_ONE_SESSION_PER_PROJECT.override(true)
    val applicationSession = AdbLibApplicationService.instance.session
    val projectSession = AdbLibService.getSession(project)

    // Assert
    Truth.assertThat(projectSession).isNotSameAs(applicationSession)
  }

  @Test
  fun applicationAdbSessionInstanceShouldNotBeTheSameAsTheProjectInstance() {
    // Act
    ADBLIB_ONE_SESSION_PER_PROJECT.override(true)
    val projectSession = AdbLibService.getSession(project)
    val applicationSession = AdbLibApplicationService.instance.session

    // Assert
    Truth.assertThat(applicationSession).isNotSameAs(projectSession)
  }

  @Test
  fun projectShouldBeRegisteredIfUsingAdbLibService() {
    // Prepare
    val applicationService = AdbLibApplicationService.instance

    // Act
    AdbLibService.getSession(project)

    // Assert
    Truth.assertThat(applicationService.registerProject(project)).isFalse()
  }
}
