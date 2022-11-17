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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class PsVersionCatalogTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testGetBuildScriptVariables() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val catalogs = psProject.versionCatalogs
      MatcherAssert.assertThat(
        catalogs.items.map { it.variables.map { v -> v.name to v.value } }.flatten(),
        CoreMatchers.equalTo(
          listOf(
            "constraint-layout" to "1.0.2".asParsed(),
            "guava" to "19.0".asParsed(),
            "junit" to "4.12".asParsed()
          )
        )
      )
    }
  }

  @Test
  fun testGetBuildScriptVariablesMultiCatalogs() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_VERSION_CATALOG_SAMPLE_GROOVY)
    preparedProject.root.resolve("settings.gradle").appendText("""
      dependencyResolutionManagement {
          versionCatalogs {
              libs2 {
                  from(files("addition.versions.toml"))
              }
          }
      }
    """.trimIndent())
    preparedProject.root.resolve("addition.versions.toml").writeText("""
      [versions]
      log4j = "2.17"
      [libraries]
      log4j-core = {module ="org.apache.logging.log4j:log4j-core", version.ref = "log4j"}
    """.trimIndent())
    preparedProject.open { project ->
      val psProject = PsProjectImpl(project)
      val catalogs = psProject.versionCatalogs
      MatcherAssert.assertThat(
        catalogs.items.map{it.variables.map {v -> v.name to v.value } }.flatten(),
        CoreMatchers.equalTo(
          listOf(
            "constraint-layout" to "1.0.2".asParsed(),
            "guava" to "19.0".asParsed(),
            "junit" to "4.12".asParsed(),
            "log4j" to "2.17".asParsed()
          )
        )
      )
    }
  }
}