// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing.syncAction

import com.intellij.openapi.externalSystem.util.ListenerAssertion
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.util.use
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions.assertModules
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test

class GradleDeclarativeSyncTest : GradlePhasedSyncTestCase() {

  @Test
  @TargetVersions("8.9+")
  fun `test project root creation in the simple Gradle project`() {
    val projectRoot = projectRoot.toNioPath()
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()

    createProjectSubFile("settings.gradle.dcl", """
      pluginManagement {
          repositories {
              google()
              gradlePluginPortal()
          }
      }
      
      plugins {
          id("org.gradle.experimental.jvm-ecosystem").version("0.1.21")
      }
      
      rootProject.name = "test-dcl"
      """)
    createProjectSubFile("build.gradle.dcl", """
      javaApplication {
          javaVersion = 23
          mainClass = "com.example.App"

          dependencies {
              implementation("com.google.guava:guava:32.1.3-jre")
          }

          testing {
              // test on 21
              testJavaVersion = 21

              dependencies {
                  implementation("org.junit.jupiter:junit-jupiter:5.10.2")
              }
          }
      }
    """.trimIndent())
    createProjectSubFile("src/main/java/org/example/MyClass.java",
                         """
      package com.example;
      class MyClass {}
    """.trimIndent())

    createProjectSubFile("src/test/java/org/example/Test.java",
                         """
                           package com.example;
                           import org.junit.Test;
                           public class Test {}
                         """.trimIndent())

    Disposer.newDisposable().use { disposable ->

      val projectRootContributorAssertion = ListenerAssertion()

      whenResolveProjectInfoStarted(disposable) { _, storage ->
        projectRootContributorAssertion.trace {
          // this should contain more stuff
          assertModules(storage, "project")
          assertContentRoots(virtualFileUrlManager, storage, "project", projectRoot)
        }
      }

      val settings = GradleSettings.getInstance(project)
      val projectSettings = GradleProjectSettings(projectRoot.toCanonicalPath())
      settings.linkProject(projectSettings)

      ExternalSystemUtil.refreshProject(projectRoot.toCanonicalPath(), createImportSpec())

      assertModules(project, "test-dcl", "test-dcl.main", "test-dcl.test")

      assertModuleModuleDeps("test-dcl.test", "test-dcl.main")
      assertModuleLibDep("test-dcl.main", "Gradle: com.google.guava:guava:32.1.3-jre")
      assertModuleLibDep("test-dcl.test", "Gradle: org.junit.jupiter:junit-jupiter:5.10.2")

      assertContentRoots(project, "test-dcl", projectRoot)
      assertContentRoots(project, "test-dcl.main", projectRoot.resolve("src/main"))
      assertContentRoots(project, "test-dcl.test", projectRoot.resolve("src/test"))

      projectRootContributorAssertion.assertListenerFailures()
      projectRootContributorAssertion.assertListenerState(1) {
        "The project info resolution should be started only once."
      }
    }
  }
}