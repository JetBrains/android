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
package com.android.tools.idea.gradle.navigation

import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.usageView.UsageInfo
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Test

class VersionCatalogFindKtsUsagesTest: AndroidTestCase()  {

  @Test
  fun testHasUsages(){
    testVersionCatalogFindUsages("""
      [libraries]
      groov${caret}y-core = "org.codehaus.groovy:groovy:2.7.3"
    """.trimIndent(), """
      dependencies {
        implementation(libs.groovy.core)
      }
    """.trimIndent()) {
      assertThat(it).hasSize(1)
      assertThat(it.first().file ).isInstanceOf(KtFile::class.java)
    }
  }

  @Test
  fun testHasNoUsages() {
    testVersionCatalogFindUsages( """
      [libraries]
      groov${caret}y-core = "org.codehaus.groovy:groovy:2.7.3"
    """.trimIndent(), """
      dependencies {
        implementation(libs.groovy)
      }
    """.trimIndent()) {
      assertThat(it).isEmpty()
    }
  }

  private fun testVersionCatalogFindUsages(versionCatalogText: String, buildGradleText: String,
                                           checker: (Collection<UsageInfo>) -> Unit) {
    val buildFile = myFixture.addFileToProject("build.gradle.kts", buildGradleText)
    myFixture.configureFromExistingVirtualFile(buildFile.virtualFile)

    myFixture.addFileToProject("settings.gradle.kts", "")

    val psiFile = myFixture.addFileToProject("gradle/libs.versions.toml", versionCatalogText)
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    myFixture.openFileInEditor(psiFile.virtualFile)
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)

    val usages = myFixture.findUsages(myFixture.elementAtCaret)
    checker(usages)
  }
}