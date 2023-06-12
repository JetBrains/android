/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.testutils.junit4.OldAgpTest
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.configurables.PsPathRendererImpl
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.model.testResolve
import com.android.tools.idea.gradle.structure.quickfix.PsDependencyConfigurationQuickFixPath
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@OldAgpTest(agpVersions = ["3.5.0"], gradleVersions = ["5.5"])
@RunsInEdt
class PsModuleDependencyConfigurationsAnalyzerTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  private val gradleVersion = "5.5"

  @Test
  fun testObsoleteTestCompileConfigurationInLibrary() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesLibrary") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(
        issues.toList(),
        "junit:junit:4.12",
        setOf("Obsolete dependency configuration found: <b>testCompile</b>" to ""),
        setOf("testCompile" to "testImplementation")
      )
    }
  }

  @Test
  fun testObsoleteCompileConfigurationInLibrary() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesLibrary") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    }
  }

  @Test
  fun testObsoleteTestCompileConfigurationInApp() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("app") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "junit:junit:4.12",
                     setOf("Obsolete dependency configuration found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    }
  }

  @Test
  fun testObsoleteCompileConfigurationInApp() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("app") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
      checkIssuesFor(issues,
                     "obsoleteScopesLibrary",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
      checkIssuesFor(issues,
                     "compile/libs",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    }
  }

  // no testObsoleteCompileTestConfigurationInTest() because com.android.test does not support compileTest dependencies
  @Test
  fun testObsoleteCompileConfigurationInTest() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesTest") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    }
  }

  // testCompile not supported by com.android.instantapp plugin
  @Test
  fun testObsoleteCompileConfigurationInInstantApp() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("instantApp") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    }
  }

  // TODO(b/129135682): enable when bug is fixed. Also, should use "3.5.0"?
  @Ignore("b/129135682")
  @Test
  fun laterTestObsoleteTestCompileConfigurationInFeature() {
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesFeature") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "junit:junit:4.12",
                     setOf("Obsolete dependency configuration found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    }
  }

  // TODO(b/129135682): enable when bug is fixed. Also, should use "3.5.0"?
  @Ignore("b/129135682")
  @Test
  fun laterTestObsoleteCompileConfigurationInFeature() {
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesFeature") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    }
  }

  @Test
  fun testObsoleteTestCompileScopeInJava() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesJava") as PsJavaModule

      val analyzer = PsJavaModuleAnalyzer(context)
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "junit:junit:4.12",
                     setOf("Obsolete dependency configuration found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    }
  }

  @Test
  fun testObsoleteCompileConfigurationInJava() {
    // Use a plugin with instant app support
    val preparedProject =
      projectRule.prepareTestProject(AndroidCoreTestProject.PSD_UPGRADE, agpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_35)
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

      val context = PsContextImpl(project, projectRule.testRootDisposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesJava") as PsJavaModule

      val analyzer = PsJavaModuleAnalyzer(context)
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete dependency configuration found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    }
  }

  // TODO(b/129135682): write DynamicFeature tests when bug is fixed

  private fun checkIssuesFor(
    issues: List<PsIssue>,
    name: String,
    expectedMessages: Set<Pair<String, String>>,
    expectedChanges: Set<Pair<String, String>>
  ) {
    val issueSet = issueSetFor(issues, name)
    val issueMessages = issueSet.map { it.text to it.description!! }.toSet()
    assertThat(issueMessages, equalTo(expectedMessages))
    val quickFixChanges = quickFixChangesFor(issueSet)
    assertThat(quickFixChanges, equalTo(expectedChanges))
  }

  private fun issueSetFor(issues: List<PsIssue>, name: String): Set<PsIssue> {
    return issues.filter { it.path.toString() == name }.toSet()
  }

  private fun quickFixChangesFor(issueSet: Set<PsIssue>): Set<Pair<String,String>> {
    return issueSet
      .flatMap { it.quickFixes }
      .filterIsInstance<PsDependencyConfigurationQuickFixPath>()
      .map { quickFix -> quickFix.oldConfigurationName to quickFix.newConfigurationName }
      .toSet()
  }
}

