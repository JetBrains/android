/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat

class PsVariablesTest : AndroidGradleTestCase() {

  fun testGetModuleVariables() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProject(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    val variables = psAppModule.variables.getModuleVariables()
    assertThat(variables.size, equalTo(7))
    assertThat(
      variables.map { it.name },
      hasItems("myVariable", "variable1", "anotherVariable", "moreVariable", "varInt", "varBool", "varRefString")
    )
  }

  fun testGetAvailableVariablesForType() {
    loadProject(TestProjectPaths.PSD_SAMPLE)
    val psProject = PsProject(project)
    val psAppModule = psProject.findModuleByName("app") as PsAndroidModule
    run {
      val variables = psAppModule.variables.getAvailableVariablesForType(String::class.java)
      assertThat(variables.size, equalTo(6))
      assertThat(
        variables,
        hasItems<Pair<String, String?>>(
          "myVariable" to "26.1.0",
          "variable1" to "1.3",
          "anotherVariable" to "3.0.1",
          "moreVariable" to "1234",
          "varInt" to "1",  // Integers are compatible with Strings. This is often used in Gradle configs.
          "varRefString" to "1.3"
        )
      )
    }

    run {
      // Note: when boxed it is still java.lang.Integer rather than Kotlin Int type.
      val variables = psAppModule.variables.getAvailableVariablesForType(Integer::class.java)
      assertThat(variables.size, equalTo(1))
      assertThat(
        variables,
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        hasItems<Pair<String, Integer?>>(
          "varInt" to (1 as Integer)
        )
      )
    }
  }
}
