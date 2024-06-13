/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.navigation.runsGradle

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class VersionCatalogFindGroovyUsageTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private val myFixture by lazy { projectRule.fixture }
  private val myProject by lazy { projectRule.project }

  @Test
  fun testHasUsages() {
    testVersionCatalogFindUsagesInSubmodule("""
      [libraries]
      groov${caret}y = "org.codehaus.groovy:groovy:2.7.3"
    """.trimIndent(), """
      dependencies {
        implementation libs.groovy
      }
    """.trimIndent()) {
      assertThat(it).hasSize(1)
      assertThat(it.first().file).isInstanceOf(GroovyFileBase::class.java)
    }
  }

  @Test
  fun testHasNoUsages() {
    testVersionCatalogFindUsagesInSubmodule("""
      [libraries]
      groov${caret}y = "org.codehaus.groovy:groovy:2.7.3"
    """.trimIndent(), """
      dependencies {
        implementation libs.groovy2
      }
    """.trimIndent()) {
      assertThat(it).isEmpty()
    }
  }

  @Test
  fun testHasUsagesWithUnderscoreAlias(){
    testVersionCatalogFindUsagesInSubmodule("""
      [libraries]
      groov${caret}y_core = { group="org.codehaus.groovy", name = "groovy", version ="2.7.3"}
    """.trimIndent(), """
      dependencies {
        implementation libs.groovy.core
      }
    """.trimIndent()) {
      assertThat(it).hasSize(1)
      assertThat(it.first().file ).isInstanceOf(GroovyFileBase::class.java)
    }
  }

  @Test
  fun testHasUsagesWithDotAlias() {
    testVersionCatalogFindUsagesInSubmodule("""
      [libraries]
      groov${caret}y.core = { group="org.codehaus.groovy", name = "groovy", version ="2.7.3"}
    """.trimIndent(), """
      dependencies {
        implementation libs.groovy.core
      }
    """.trimIndent()) {
      assertThat(it).hasSize(1)
      assertThat(it.first().file).isInstanceOf(GroovyFileBase::class.java)
    }
  }

  private fun testVersionCatalogFindUsagesInSubmodule(versionCatalogText: String, buildGradleText: String,
                                                      checker: (Collection<UsageInfo>) -> Unit) {
    projectRule.load(SIMPLE_APPLICATION_VERSION_CATALOG)
    val path = myProject.guessProjectDir()!!
    val versionCatalogFile = createFile(path, "gradle/libs.versions.toml", versionCatalogText)
    createFile(path, "app/build.gradle", buildGradleText)

    myFixture.configureFromExistingVirtualFile(versionCatalogFile)

    runReadAction {
      ProgressManager.getInstance().runProcess({
        val usages = myFixture.findUsages(myFixture.elementAtCaret)
        checker(usages)
      }, EmptyProgressIndicator())
    }
  }
}