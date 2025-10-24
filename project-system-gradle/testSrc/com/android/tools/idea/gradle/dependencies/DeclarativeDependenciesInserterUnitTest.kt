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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dependencies.DeclarativeDependenciesInserter.Companion.getNameIfBuildDependency
import com.android.tools.idea.gradle.dependencies.DeclarativeDependenciesInserter.Companion.getNameIfFlavorDependency
import kotlin.test.assertEquals
import org.junit.Test

class DeclarativeDependenciesInserterUnitTest {

  @Test
  fun getBuildTypeNameTestNoUserTypes() {
    doBuildTypeTest("debugApi", "debug" to "api")
    doBuildTypeTest("releaseImplementation", "release" to "implementation")
    doBuildTypeTest("debug", null)
    doBuildTypeTest("someString", null)
    doBuildTypeTest("implementation", null)
    // should be capital after prefix
    doBuildTypeTest("releaseimplementation", null)
    doBuildTypeTest("releaseSomething", "release" to "something")
    doBuildTypeTest("", null)
  }

  @Test
  fun getBuildTypeNameTestWithUserTypes() {
    doBuildTypeTest("stagingApi", setOf("staging"), "staging" to "api")
    doBuildTypeTest("debugApi", setOf("staging"), "debug" to "api")
    doBuildTypeTest("stagingapi", setOf("staging"), null)
    doBuildTypeTest("demoApi", setOf("staging"), null)
    doBuildTypeTest("releaseImplementation", setOf("release", "debug"), "release" to "implementation")
    doBuildTypeTest("debug", setOf("release", "debug"), null)
    doBuildTypeTest("StagingApi", setOf("Staging"), "Staging" to "api")

  }

  @Test
  fun getFlavorTypeNameTestNoUserTypes() {
    doFlavorTypeTest("androidTestImplementation", null)
    doFlavorTypeTest("someAndroidTestImplementation", "some" to "androidTestImplementation")
    doFlavorTypeTest("testImplementation", null)
    doFlavorTypeTest("someTestImplementation", "some" to "testImplementation")
    doFlavorTypeTest("implementation", null)
    doFlavorTypeTest("someImplementation", "some" to "implementation")
    // should be capital after prefix
    doFlavorTypeTest("someimplementation", null)
    doFlavorTypeTest("randomname", null)
    doFlavorTypeTest("", null)
  }

  @Test
  fun getFlavorTypeNameTestWithUserTypes() {
    doFlavorTypeTest("demoConfig", setOf("demo"), "demo" to "config")
    doFlavorTypeTest("demoImplementation", setOf("demo"), "demo" to "implementation")
    doFlavorTypeTest("someImplementation", setOf("demo"), "some" to "implementation")
    doFlavorTypeTest("demoimplementation", setOf("demo"),null)
    doFlavorTypeTest("DemoImplementation", setOf("Demo"),"Demo" to "implementation")
  }

  private fun doBuildTypeTest(configuration: String, output: Pair<String, String>?) {
    assertEquals(getNameIfBuildDependency(configuration, setOf()), output)
  }

  private fun doFlavorTypeTest(configuration: String, output: Pair<String, String>?) {
    assertEquals(getNameIfFlavorDependency(configuration, setOf()), output)
  }

  private fun doBuildTypeTest(configuration: String, existingTypes: Set<String>, output: Pair<String, String>?) {
    assertEquals(getNameIfBuildDependency(configuration, existingTypes), output)
  }

  private fun doFlavorTypeTest(configuration: String, existingTypes: Set<String>, output: Pair<String, String>?) {
    assertEquals(getNameIfFlavorDependency(configuration, existingTypes), output)
  }
}