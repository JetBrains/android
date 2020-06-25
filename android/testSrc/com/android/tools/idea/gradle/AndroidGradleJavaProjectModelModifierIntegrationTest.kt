// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.gradle

import com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult
import com.android.tools.idea.gradle.project.sync.precheck.PreSyncChecks
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.roots.JavaProjectModelModificationService
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.testFramework.replaceService
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.junit.Test
import org.junit.runners.Parameterized
import java.util.concurrent.TimeUnit

class AndroidGradleJavaProjectModelModifierIntegrationTest : GradleImportingTestCase() {
  override fun runInWriteAction(): Boolean = false

  override fun runInDispatchThread(): Boolean = false

  @Test
  fun testAddDependencyToProjectWithNoAndroid() {
    importProject("apply plugin: 'java'")
    ensureAndroidGradlePreSyncChecksFail()

    val modificationService = JavaProjectModelModificationService.getInstance(myProject)
    val module = ModuleManager.getInstance(myProject).findModuleByName("project.test")!!
    val dependencyPromise = modificationService.addDependency(module, ExternalLibraryDescriptor("junit", "junit", "4.13", null, "4.13"))
    dependencyPromise.blockingGet(90, TimeUnit.SECONDS)
    assertTrue(dependencyPromise.isSucceeded)

    val hasAddedDependency = checkModuleHasLibrary(module, "Gradle: junit:junit:4.13")
    assertTrue(hasAddedDependency)
  }

  private fun checkModuleHasLibrary(module: Module, libraryName: String): Boolean {
    var hasAddedDependency = false
    ModuleOrderEnumerator.orderEntries(module).forEachLibrary { lib ->
      if (lib.name == libraryName) hasAddedDependency = true
      return@forEachLibrary !hasAddedDependency
    }
    return hasAddedDependency
  }

  private fun ensureAndroidGradlePreSyncChecksFail() {
    val failingChecks = object : PreSyncChecks() {
      override fun canSyncAndTryToFix(project: Project): PreSyncCheckResult {
        return PreSyncCheckResult.failure("Test failure")
      }
    }
    ApplicationManager.getApplication().replaceService(PreSyncChecks::class.java, failingChecks, testRootDisposable)
  }


  companion object {
    /**
     * It's sufficient to run the test against one gradle version
     */
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests(): Collection<Array<out String>> = arrayListOf(arrayOf(GradleImportingTestCase.BASE_GRADLE_VERSION))
  }
}