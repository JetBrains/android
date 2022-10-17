/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths
import com.android.utils.FileUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestCase.synchronizeTempDirVfs
import com.intellij.testFramework.PlatformTestUtil
import java.io.File

/**
 * Tests for [PsModuleCollection].
 */
class PsModuleCollectionTest : DependencyTestCase() {

  private var patchProject: ((VirtualFile) -> Unit)? = null

  override fun patchPreparedProject(projectRoot: File,
                                    gradleVersion: String?,
                                    graldePluginVersion: String?,
                                    kotlinVersion: String?,
                                    ndkVersion: String?,
                                    compileSdk: String?,
                                    vararg localRepos: File) {
    AndroidGradleTests.defaultPatchPreparedProject(projectRoot, gradleVersion, graldePluginVersion, kotlinVersion, ndkVersion, compileSdk, *localRepos)
    synchronizeTempDirVfs(PlatformTestUtil.getOrCreateProjectBaseDir(project))
    patchProject?.run {
      ApplicationManager.getApplication().runWriteAction {
        invoke(PlatformTestUtil.getOrCreateProjectBaseDir(project))
      }
      ApplicationManager.getApplication().saveAll()
    }
  }

  fun loadProject(path: String, patch: (VirtualFile) -> Unit) {
    patchProject = patch
    return try {
      super.loadProject(path)
    }
    finally {
      patchProject = null
    }
  }

  fun testNotSyncedModules() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY) {
      it.findFileByRelativePath("settings.gradle")!!.let {
        it.setBinaryContent("include ':app', ':lib', ':dyn_feature' ".toByteArray(it.charset))
      }
    }

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject)
    assertThat(project.findModuleByName("jav")).isNull()

    // Edit the settings file, but do not sync.
    val virtualFile = PlatformTestUtil.getOrCreateProjectBaseDir(this.project).findFileByRelativePath("settings.gradle")!!
    runWriteAction { virtualFile.setBinaryContent("include ':app', ':lib', ':jav' ".toByteArray()) }
    PsiDocumentManager.getInstance(this.project).commitAllDocuments()

    project = PsProjectImpl(resolvedProject)

    assertThat(moduleWithSyncedModel(project, "app").projectType).isEqualTo(PsModuleType.ANDROID_APP)
    assertThat(moduleWithSyncedModel(project, "lib").projectType).isEqualTo(PsModuleType.ANDROID_LIBRARY)
    assertThat(moduleWithSyncedModel(project, "jav").projectType).isEqualTo(PsModuleType.JAVA)
  }

  fun testNonAndroidGradlePluginFirst() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    // Edit the settings file, but do not sync.
    val virtualFile = PlatformTestUtil.getOrCreateProjectBaseDir(this.project).findFileByRelativePath("app/build.gradle")!!
    myFixture.openFileInEditor(virtualFile)
    myFixture.type("apply plugin: 'something' \n")
    PsiDocumentManager.getInstance(this.project).commitAllDocuments()


    val resolvedProject = myFixture.project
    // Make sure we have correctly patched the build file.
    assertThat(
        GradleModelProvider
            .getInstance()
            .getProjectModel(resolvedProject)
            .getModuleBuildModel(File(resolvedProject.basePath, "app"))
            ?.plugins()
            ?.firstOrNull()
            ?.name()
            ?.getValue(STRING_TYPE)
    ).isEqualTo("something")

    val project = PsProjectImpl(resolvedProject)
    assertThat(project.modules.map { it.gradlePath }).contains(":app")
  }

  fun testNestedModules() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val module = project.findModuleByGradlePath(":nested1:deep")
    assertThat(module).isNotNull()
    assertThat(module!!.parentModule).isSameAs(project.findModuleByGradlePath(":nested1")!!)
    assertThat(module.variables.getVariableScopes().map { it.name })
        .containsExactly("${project.name} (build script)", "${project.name} (project)", ":nested1", ":nested1:deep")
  }

  fun testRelocatedModules_withoutResolvedModels() {
    loadProject(TestProjectPaths.PSD_PROJECT_DIR)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)
    val basePath = resolvedProject.basePath

    assertThat(project.modules.map { it.gradlePath }).containsExactly(":app", ":lib")
    (project.findModuleByGradlePath(":app") as PsAndroidModule).run {
      val root = parsedModel?.moduleRootDirectory
      assertThat(root).isEqualTo(File(FileUtils.toSystemDependentPath("$basePath/app")))
    }
    (project.findModuleByGradlePath(":lib") as PsAndroidModule).run {
      val root = parsedModel?.moduleRootDirectory
      assertThat(root).isEqualTo(File(FileUtils.toSystemDependentPath("$basePath/module/lib")))
    }
  }

  fun testRelocatedModules_withResolvedModel() {
    loadProject(TestProjectPaths.PSD_PROJECT_DIR)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject).also { it.testResolve() }

    assertThat(project.modules.map { it.gradlePath }).containsExactly(":app", ":lib", ":jav")

    // And make sure the build file is parsed.
    val javModule = project.findModuleByGradlePath(":jav") as? PsJavaModule
    assertThat(javModule?.dependencies?.findLibraryDependencies("junit", "junit")?.firstOrNull()?.version).isEqualTo("4.12".asParsed())
    javModule!!.run {
      // We can't work out where the module is from the project settings, but our implementation will assume that the module root
      // is where the build.gradle file is (which happens to be correct in this case)
      val root = parsedModel?.moduleRootDirectory
      assertThat(root).isEqualTo(File(FileUtils.toSystemDependentPath("${resolvedProject.basePath}/module/jav")))
    }
  }

  fun testEmptyParentsInNestedModules() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val resolvedProject = myFixture.project
    val project = PsProjectImpl(resolvedProject)

    assertThat(project.modules.map { it.gradlePath }).containsExactly(
      ":app",
      ":lib",
      ":jav",
      ":nested1",
      ":nested2",
      ":nested1:deep",
      ":nested2:deep",
      ":nested2:trans",
      ":nested2:trans:deep2",
      ":dyn_feature")

    assertThat(project.findModuleByGradlePath(":nested2:trans")?.moduleKind).isEqualTo(ModuleKind.EMPTY)
  }

}

private fun moduleWithSyncedModel(project: PsProject, name: String): PsModule = project.findModuleByName(name) as PsModule
