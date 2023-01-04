/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution

import com.android.testutils.JarTestSuiteRunner
import com.android.testutils.junit4.OldAgpSuite
import com.android.tools.tests.GradleDaemonsRule
import com.android.tools.tests.IdeaTestSuiteBase
import com.android.tools.tests.MavenRepoRule
import org.junit.ClassRule
import org.junit.runner.RunWith

@RunWith(OldAgpSuite::class)
@JarTestSuiteRunner.ExcludeClasses(
  AndroidBuildAttributionTestSuite::class,
  AndroidBuildAttributionOldAgpTestSuite::class, // a suite mustn't contain itself
)
class AndroidBuildAttributionOldAgpTestSuite : IdeaTestSuiteBase() {

  companion object {
    @ClassRule
    @JvmField
    val daemons = GradleDaemonsRule()

    @ClassRule
    @JvmField
    val mavenRepos = MavenRepoRule.fromTestSuiteSystemProperty()
  }
}