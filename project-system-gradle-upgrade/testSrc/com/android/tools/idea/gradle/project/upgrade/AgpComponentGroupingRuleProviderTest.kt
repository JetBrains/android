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

import com.android.ide.common.repository.AgpVersion
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import com.intellij.usageView.UsageInfo
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewSettings
import com.intellij.usages.impl.rules.UsageTypeGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRuleProvider
import org.jetbrains.android.AndroidTestCase
import java.util.ArrayList

class AgpComponentGroupingRuleProviderTest : AndroidTestCase() {
  fun testRuleIsActive() {
    val groupingRules = getActiveGroupingRules(myFixture.project)
    assertThat(groupingRules.filterIsInstance(ComponentGroupingRule::class.java)).isNotEmpty()
    assertThat(groupingRules.indexOfFirst { it is ComponentGroupingRule })
      .isLessThan(groupingRules.indexOfFirst { it is UsageTypeGroupingRule })
  }

  // TODO(b/161888480): parameterize by Groovy/KotlinScript
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
    val group = getParentComponentGroupFor(usages[0])
    assertThat(group.presentableGroupText).isEqualTo("Upgrade AGP dependency from 3.6.0 to 4.0.0")
  }

  fun testAgpGradleVersionRefactoringProcessor() {
    myFixture.addFileToProject("gradle/wrapper/gradle-wrapper.properties", """
      distributionUrl=https\://services.gradle.org/distributions/gradle-6.4-bin.zip
    """.trimIndent())
    val processor = GradleVersionRefactoringProcessor(myFixture.project, AgpVersion.parse("3.6.0"), AgpVersion.parse("4.1.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    val group = getParentComponentGroupFor(usages[0])
    assertThat(group.presentableGroupText).isEqualTo("Upgrade Gradle version to 6.5")
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
    val group = getParentComponentGroupFor(usages[0])
    assertThat(group.presentableGroupText).isEqualTo("Add google() GMaven to buildscript repositories")
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
    processor.noLanguageLevelAction = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.INSERT_OLD_DEFAULT
    val usages = processor.findUsages()
    assertThat(usages).hasLength(2)
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText }.toSet())
      .containsExactly("Add directives to keep using Java 7")
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
    processor.noLanguageLevelAction = Java8DefaultRefactoringProcessor.NoLanguageLevelAction.ACCEPT_NEW_DEFAULT
    val usages = processor.findUsages()
    assertThat(usages).hasLength(2)
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText }.toSet())
      .containsExactly("Add directives to keep using Java 7")
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
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText }.toSet())
      .containsExactly("Replace deprecated configurations")
  }

  fun testFabricCrashlyticsRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      buildscript {
        dependencies {
          classpath 'io.fabric.tools:gradle:1.2.3'
        }
      }
    """.trimIndent())
    val processor = FabricCrashlyticsRefactoringProcessor(myFixture.project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.2.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(3)
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText }.toSet())
      .containsExactly("Migrate crashlytics from fabric to firebase")
  }

  fun testMigrateToBuildFeaturesRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      android {
        viewBinding {
          enabled true
        }
      }
    """.trimIndent())
    val processor = MIGRATE_TO_BUILD_FEATURES_INFO.RefactoringProcessor(myFixture.project,
                                                                        AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText })
      .containsExactly("Migrate enabled booleans to buildFeatures")
  }

  fun testRemoveSourceSetJniRefactoringProcessor() {
    myFixture.addFileToProject("build.gradle", """
      android {
        sourceSets {
          foo {
            jni {
              srcDirs 'abcd'
            }
          }
        }
      }
    """.trimIndent())

    val processor = REMOVE_SOURCE_SET_JNI_INFO.RefactoringProcessor(myFixture.project, AgpVersion.parse("4.2.0"), AgpVersion.parse("7.0.0"))
    assertTrue(processor.isEnabled)
    val usages = processor.findUsages()
    assertThat(usages).hasLength(1)
    assertThat(usages.map { getParentComponentGroupFor(it).presentableGroupText })
      .containsExactly("Remove jni source directory from sourceSets")
  }

  /**
   * this mirrors [com.intellij.usages.impl.UsageViewImpl.getActiveGroupingRules]
   */
  private fun getActiveGroupingRules(project: Project): Array<UsageGroupingRule> {
    val providers = UsageGroupingRuleProvider.EP_NAME.extensionList
    val usageViewSettings = UsageViewSettings.instance
    val list = ArrayList<UsageGroupingRule>(providers.size)
    for (provider in providers) {
      list.addAll(provider.getActiveRules(project, usageViewSettings, null))
    }

    list.sortBy { it.rank }
    return list.toArray(UsageGroupingRule.EMPTY_ARRAY)
  }

  private fun getParentComponentGroupFor(usageInfo: UsageInfo): UsageGroup {
    val groupingRule = ComponentGroupingRule()
    val groups = groupingRule.getParentGroupsFor(UsageInfo2UsageAdapter(usageInfo), UsageTarget.EMPTY_ARRAY)
    assertThat(groups).hasSize(1)
    return groups[0]
  }
}