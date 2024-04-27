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
package com.android.tools.idea.testing

import com.android.SdkConstants
import com.android.ide.common.repository.GradleVersion
import com.android.test.testutils.TestUtils
import com.android.tools.idea.util.toVirtualFile
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.io.ZipUtil
import java.io.File

private val MAVEN_REPO = TestUtils.getPrebuiltOfflineMavenRepo()
private val LEGACY_FOLDER = "$MAVEN_REPO/com/android/support/"
private val ANDROIDX_FOLDER = "$MAVEN_REPO/androidx/"
private val GOOGLE_FOLDER = "$MAVEN_REPO/com/google/android/"

/**
 * Adds a dependency from prebuilts to an existing test fixture.
 *
 * The resources from the library are added to the resource manager,
 * and the classes from the library are added to the psi.
 */
object Dependencies {

  /**
   * Add the [dependencyNames] to the specified [fixture].
   *
   * Example of [dependencyNames]:
   *  - "appcompat-v7" for the legacy appcompat library
   *  - "appcompat" for the androidx appcompat library
   *
   *  The name specified must be a folder in either [ANDROIDX_FOLDER] or [LEGACY_FOLDER].
   */
  fun add(fixture: CodeInsightTestFixture, vararg dependencyNames: String): Map<String, GradleVersion> {
    val loader = DependencyLoader(fixture)
    return loader.loadAll(*dependencyNames)
  }

  private class DependencyLoader(val fixture: CodeInsightTestFixture) {

    fun loadAll(vararg dependencyNames: String): Map<String, GradleVersion> {
      return dependencyNames.map { Pair(it, load(it)) }.toMap()
    }

    private fun load(dependency: String): GradleVersion {
      val root = File(PathManager.getHomePath())
      val legacyFolder = root.resolve(LEGACY_FOLDER)
      val androidFolder = root.resolve(ANDROIDX_FOLDER)
      val googleFolder = root.resolve(GOOGLE_FOLDER)
      val legacyFile = legacyFolder.resolve(dependency)
      val androidxFile = androidFolder.resolve(dependency)
      val googleFile = googleFolder.resolve(dependency)
      return when {
        androidxFile.exists() -> loadLatestVersion(map(androidxFile))
        legacyFile.exists() -> loadLatestVersion(map(legacyFile))
        googleFile.exists() -> loadLatestVersion(map(googleFile))
        else -> error("Dependency not found in prebuilts: $dependency")
      }
    }

    // ConstraintLayout is added in a unique way in prebuilts/tools
    private fun map(original: File): File =
      when (original.name) {
        "constraint" -> original.resolve("constraint-layout")      // legacy
        "constraintlayout" -> original.resolve("constraintlayout") // androidx
        else -> original
      }

    // TODO(b/135483675): Read the pom file and load all transitive dependencies as well.
    // TODO: Also update the API above such that Androidx only will have to specify the artifactId. Not: "appcompat/appcompat"
    private fun loadLatestVersion(folder: File): GradleVersion {
      val name = folder.name
      val version = folder.list()?.maxOfOrNull { GradleVersion.parse(it) } ?: error("No versions found in folder: ${folder.path}")
      val versionFolder = File(folder, version.toString())
      val aarFile = File(versionFolder, "$name-$version.aar")
      val aarDir = FileUtil.createTempDirectory(name, "_exploded")
      ZipUtil.extract(aarFile, aarDir, null)
      val resDir = aarDir.resolve(SdkConstants.FD_RES)
      val classesJar = aarDir.resolve(SdkConstants.FN_CLASSES_JAR)
      val jarDir = FileUtil.createTempDirectory(name, "_exploded_jar")
      ZipUtil.extract(classesJar, jarDir, null)
      val classesRoots = listOfNotNull(resDir.toVirtualFile(refresh = true), jarDir.toVirtualFile(refresh = true))
      val library = PsiTestUtil.addProjectLibrary(fixture.module, "$name.aar", classesRoots, emptyList())
      ModuleRootModificationUtil.addDependency(fixture.module, library, DependencyScope.PROVIDED, true)
      return version
    }
  }
}
