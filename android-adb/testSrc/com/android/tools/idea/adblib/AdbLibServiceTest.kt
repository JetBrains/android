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
import com.android.tools.idea.adb.FakeAdbServiceRule
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

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fakeAdbRule).around(fakeAdbServiceRule)!!

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
}
