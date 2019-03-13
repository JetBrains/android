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
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyScopeQuickFixPath
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.util.Disposer
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat

class PsModuleLibraryScopesAnalyzerTest : DependencyTestCase() {
  fun testObsoleteTestCompileScopeInLibrary() {
    loadProject(TestProjectPaths.PSD_UPGRADE)
    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }
    val disposable = Disposer.newDisposable()

    try {
      val context = PsContextImpl(project, disposable, disableAnalysis = true, disableResolveModels = true)
      val module = project.findModuleByName("obsoleteScopesLibrary") as PsAndroidModule

      val analyzer = PsAndroidModuleAnalyzer(context, PsPathRendererImpl().also { it.context = context })
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "junit:junit:4.12")
      val messages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(messages, equalTo(setOf(
        "Obsolete scope found: <b>testCompile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("testCompile" to "testImplementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf(
        "compile" to "api",
        "compile" to "implementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "junit:junit:4.12")
      val messages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(messages, equalTo(setOf(
        "Obsolete scope found: <b>testCompile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("testCompile" to "testImplementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("compile" to "implementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("compile" to "implementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("compile" to "implementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "junit:junit:4.12")
      val messages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(messages, equalTo(setOf(
        "Obsolete scope found: <b>testCompile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("testCompile" to "testImplementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("compile" to "api", "compile" to "implementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "junit:junit:4.12")
      val messages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(messages, equalTo(setOf(
        "Obsolete scope found: <b>testCompile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf("testCompile" to "testImplementation")))
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
      val issues = PsIssueCollection()
      analyzer.analyze(module, issues)

      val issueSet = issueSetFor(issues, "androidx.appcompat:appcompat:1.0.2")
      val issueMessages = issueSet.map { it.text to it.description!! }.toSet()

      assertThat(issueMessages, equalTo(setOf(
        "Obsolete scope found: <b>compile</b>" to ""
      )))
      val quickFixChanges = quickFixChangesFor(issueSet)

      assertThat(quickFixChanges, equalTo(setOf(
        "compile" to "api",
        "compile" to "implementation")))
    } finally {
      Disposer.dispose(disposable)
    }
  }

  // TODO(b/129135682): write DynamicFeature tests when bug is fixed

  private fun issueSetFor(issues: PsIssueCollection, name: String): Set<PsIssue> {
    return issues.values.filter {
      val dependencyName = (it.path as? PsLibraryDependencyNavigationPath)?.toString().orEmpty()
      dependencyName.equals(name)
    }.toSet()
  }

  private fun quickFixChangesFor(issueSet: Set<PsIssue>): Set<Pair<String,String>> {
    return issueSet
      .flatMap { it.quickFixes }
      .filterIsInstance<PsLibraryDependencyScopeQuickFixPath>()
      .map { quickFix -> quickFix.oldConfigurationName to quickFix.newConfigurationName }
      .toSet()
  }
}
