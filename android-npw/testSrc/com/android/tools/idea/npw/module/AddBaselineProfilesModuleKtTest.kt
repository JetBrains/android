/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.NewProjectWizardTestUtils.getAgpVersion
import com.android.tools.idea.testing.AndroidGradleProjectRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AddBaselineProfilesModuleKtTest(private val useGmdParam: Boolean) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGmdParam={0}")
    fun data(): List<Array<Any>> = listOf(arrayOf(true), arrayOf(false))
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule(agpVersionSoftwareEnvironment = getAgpVersion())

  @Test
  fun addNewBaselineProfilesModuleTest() {
    AddBaselineProfilesModuleTest.addNewBaselineProfilesModule(projectRule, useGmdParam, true)
  }
}
