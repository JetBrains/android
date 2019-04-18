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

import com.android.tools.idea.gradle.structure.configurables.PsContextImpl
import com.android.tools.idea.gradle.structure.configurables.PsPathRendererImpl
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.quickfix.PsDependencyScopeQuickFixPath
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.util.Disposer
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

class PsModuleDependencyScopesAnalyzerTest : DependencyTestCase() {
  fun testObsoleteTestCompileScopeInLibrary() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesLibrary") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "junit:junit:4.12",
                     setOf("Obsolete scope found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  fun testObsoleteCompileScopeInLibrary() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesLibrary") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  fun testObsoleteTestCompileScopeInApp() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("app") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "junit:junit:4.12",
                     setOf("Obsolete scope found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  fun testObsoleteCompileScopeInApp() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("app") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
      checkIssuesFor(issues,
                     "obsoleteScopesLibrary",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
      checkIssuesFor(issues,
                     "compile/libs",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  // no testObsoleteCompileTestScopeInTest() because com.android.test does not support compileTest dependencies
  fun testObsoleteCompileScopeInTest() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesTest") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  // testCompile not supported by com.android.instantapp plugin
  fun testObsoleteCompileScopeInInstantApp() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("instantApp") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  // TODO(b/129135682): enable when bug is fixed
  fun laterTestObsoleteTestCompileScopeInFeature() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesFeature") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "junit:junit:4.12",
                     setOf("Obsolete scope found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  // TODO(b/129135682): enable when bug is fixed
  fun laterTestObsoleteCompileScopeInFeature() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesFeature") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  fun testObsoleteTestCompileScopeInJava() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesJava") as PsJavaModule

      val analyzer = PsJavaModuleAnalyzer(context)
      val issues = analyzer.analyze(module)

      checkIssuesFor(issues.toList(),
                     "junit:junit:4.12",
                     setOf("Obsolete scope found: <b>testCompile</b>" to ""),
                     setOf("testCompile" to "testImplementation"))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  fun testObsoleteCompileScopeInJava() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesJava") as PsJavaModule

      val analyzer = PsJavaModuleAnalyzer(context)
      val issues = analyzer.analyze(module).toList()

      checkIssuesFor(issues,
                     "androidx.appcompat:appcompat:1.0.2",
                     setOf("Obsolete scope found: <b>compile</b>" to ""),
                     setOf("compile" to "api", "compile" to "implementation"))
    } finally {
      Disposer.dispose(disposable)
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
      .filterIsInstance<PsDependencyScopeQuickFixPath>()
      .map { quickFix -> quickFix.oldConfigurationName to quickFix.newConfigurationName }
      .toSet()
  }
}
