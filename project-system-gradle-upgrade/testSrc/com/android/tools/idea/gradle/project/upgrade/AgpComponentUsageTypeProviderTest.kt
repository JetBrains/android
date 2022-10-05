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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.GradleVersion.AgpVersion
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
import com.android.tools.idea.gradle.project.upgrade.Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.jetbrains.android.AndroidTestCase

class AgpComponentUsageTypeProviderTest : AndroidTestCase() {
  // TODO(b/161888480): parameterize across Groovy/KotlinScript
  fun testAgpClasspathDependencyRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'com.android.tools.build:gradle:3.6.0'
        }
      }
      """.trimIndent())
    val processor = AgpVersionRefactoringProcessor(myFixture.project, AgpVersion.parse("3.6.0"), AgpVersion.parse("4.0.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    assertThat(usages[0].element).isNotNull()
    val usageType = getUsageType(usages[0].element!!)
    assertThat(usageType.toString()).isEqualTo("Update version string")
  }

  fun testGradleVersionRefactoringProcessor() {
    myFixture.addFileToProject("gradle/wrapper/gradle-wrapper.properties", """
      distributionUrl=https\://services.gradle.org/distributions/gradle-6.4-bin.zip
    """.trimIndent())
    val processor = GradleVersionRefactoringProcessor(myFixture.project, AgpVersion.parse("3.6.0"), AgpVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    assertThat(usages[0].element).isNotNull()
    val usageType = getUsageType(usages[0].element!!)
    assertThat(usageType.toString()).isEqualTo("Update Gradle distribution URL")
  }

  fun testGMavenRepositoryRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'com.android.tools.build:gradle:2.3.0'
        }
        repositories {
          jcenter()
        }
    """.trimIndent())
    val processor = GMavenRepositoryRefactoringProcessor(myFixture.project, AgpVersion.parse("2.3.0"), AgpVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    assertThat(usages[0].element).isNotNull()
    val usageType = getUsageType(usages[0].element!!)
    assertThat(usageType.toString()).isEqualTo("Add GMaven declaration")
  }

  fun testJava8DefaultRefactoringProcessorInsertOldDefault() {
    myFixture.addFileToProject("build.gradle", """
      plugins {
        id 'com.android.application'
      }
      android {
        compileOptions {
          sourceCompatibility = JavaVersion.VERSION_1_7
        }
      }
    """.trimIndent())
    val processor = Java8DefaultRefactoringProcessor(myFixture.project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
    processor.noLanguageLevelAction = INSERT_OLD_DEFAULT
    val usages = processor.findUsages()
    assertThat(usages).hasLength(2)
    assertThat(usages.mapNotNull { it.element?.let { e -> getUsageType(e).toString() } })
      .containsExactly("Existing language level directive (leave unchanged)", "Continue using Java 7 (insert language level directives)")
  }

  fun testJava8DefaultRefactoringProcessorAcceptNewDefault() {
    myFixture.addFileToProject("build.gradle", """
      plugins {
        id 'com.android.application'
      }
      android {
        compileOptions {
          sourceCompatibility = JavaVersion.VERSION_1_7
        }
      }
    """.trimIndent())
    val processor = Java8DefaultRefactoringProcessor(myFixture.project, AgpVersion.parse("4.0.0"), AgpVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
    processor.noLanguageLevelAction = ACCEPT_NEW_DEFAULT
    val usages = processor.findUsages()
    assertThat(usages).hasLength(2)
    assertThat(usages.mapNotNull { it.element?.let { e -> getUsageType(e).toString() } })
      .containsExactly("Existing language level directive (leave unchanged)", "Accept new default (leave unchanged)")
  }

  fun testCompileRuntimeConfigurationRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      plugins {
        id 'com.android.application'
      }
      configurations {
        paidReleaseCompile { }
      }
      dependencies {
        androidTestCompile 'org.junit:junit:4.11'
      }
    """.trimIndent())
    val processor = CompileRuntimeConfigurationRefactoringProcessor(myFixture.project, AgpVersion.parse("4.0.0"), AgpVersion.parse("5.0.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(2)
    assertThat(usages.mapNotNull { it.element?.let { e -> getUsageType(e).toString() } })
      .containsExactly("Change dependency configuration", "Rename configuration")
  }

  private fun getUsageType(element: PsiElement) : UsageType? {
    for (provider in UsageTypeProvider.EP_NAME.extensionList) {
      if (provider is UsageTypeProviderEx) {
        val targets = UsageTarget.EMPTY_ARRAY
        return provider.getUsageType(element, targets) ?: continue
      }
      else {
        return provider.getUsageType(element) ?: continue
      }
    }
    return null
  }
}
