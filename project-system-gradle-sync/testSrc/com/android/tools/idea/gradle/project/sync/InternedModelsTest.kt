/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedModuleLibraryImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val BUILD_ROOT = "/tmp/abc"

class InternedModelsTest {

  private val internedModels = InternedModels(File(BUILD_ROOT))

  private fun LibraryReference.lookup(): IdeArtifactLibrary = internedModels.lookup(this) as IdeArtifactLibrary

  @Test
  fun `intern string`() {
    val s1 = "123123".substring(0..2)
    val s2 = "123123".substring(3..5)
    val i1 = internedModels.intern(s1)
    val i2 = internedModels.intern(s2)
    assertTrue(s1 !== s2)
    assertTrue(i1 === i2)
  }

  @Test
  fun `create android library`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)

    val named = internedModels.getOrCreate(unnamed).lookup()

    assertTrue(named == unnamed.copy(name = "com.example:lib:1.0"))
  }

  @Test
  fun `get android library`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)

    val unnamedCopy = unnamed.copy()
    val namedRef = internedModels.getOrCreate(unnamed)
    val namedCopyRef = internedModels.getOrCreate(unnamedCopy)

    assertTrue(unnamed !== unnamedCopy)
    assertTrue(unnamed == unnamedCopy)
    assertTrue(namedRef == namedCopyRef)
    assertTrue(namedRef.lookup() === namedCopyRef.lookup())
  }

  @Test
  fun `create java library`() {
    val libRoot = "/tmp/libs/lib"
    val unnamed = IdeJavaLibraryImpl(
      artifactAddress = "com.example:lib:1.0",
      name = "",
      artifact = File("$libRoot/artifactFile"),
    )

    val named = internedModels.getOrCreate(unnamed).lookup()

    assertTrue(named == unnamed.copy(name = "com.example:lib:1.0"))
  }

  @Test
  fun `get java library`() {
    val libRoot = "/tmp/libs/lib"
    val unnamed = IdeJavaLibraryImpl(
      artifactAddress = "com.example:lib:1.0",
      name = "",
      artifact = File("$libRoot/artifactFile"),
    )

    val unnamedCopy = unnamed.copy()
    val namedRef = internedModels.getOrCreate(unnamed)
    val namedCopyRef = internedModels.getOrCreate(unnamedCopy)

    assertTrue(unnamed !== unnamedCopy)
    assertTrue(unnamed == unnamedCopy)
    assertTrue(namedRef == namedCopyRef)
    assertTrue(namedRef.lookup() === namedCopyRef.lookup())
  }

  @Test
  fun `get module library`() {
    val module = IdePreResolvedModuleLibraryImpl(
      buildId = "/tmp/build",
      projectPath = ":app",
      variant = "debug",
      lintJar = null,
      sourceSet = IdeModuleWellKnownSourceSet.MAIN
    )

    val copy = module.copy()
    val module1 = internedModels.getOrCreate(module)
    val module2 = internedModels.getOrCreate(copy)

    assertTrue(module !== copy)
    assertTrue(module == copy)
    assertTrue(module1 === module2)
  }

  @Test
  fun `get unresolved module library`() {
    val module = IdeUnresolvedModuleLibraryImpl(
      buildId = "/tmp/build",
      projectPath = ":app",
      variant = "debug",
      lintJar = null,
      artifact = File("/tmp/a.jar")
    )

    val copy = module.copy()
    val module1 = internedModels.getOrCreate(module)
    val module2 = internedModels.getOrCreate(copy)

    assertTrue(module !== copy)
    assertTrue(module == copy)
    assertTrue(module1 === module2)
  }

  @Test
  fun `name library with matching artifact name`() {
    val unnamed1 = let {
      val libRoot = "/tmp/libs/lib1"
      val artifact = "$libRoot/artifactFile"
      ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)
    }

    val unnamed2 = let {
      val libRoot = "/tmp/libs/lib2" // A different directory.
      val artifact = "$libRoot/artifactFile"
      ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)
    }

    val named1 = internedModels.getOrCreate(unnamed1).lookup()
    val named2 = internedModels.getOrCreate(unnamed2).lookup()

    assertTrue(unnamed1.artifactAddress == unnamed2.artifactAddress)
    assertTrue(named1.artifactAddress == named2.artifactAddress)
    assertTrue(named1.name != named2.name)

    assertEquals("com.example:lib:1.0 (1)", named2.name)
  }

  @Test
  fun `name local aar library`() {
    val unnamed = let {
      val libRoot = "$BUILD_ROOT/app/libs"
      val artifact = "$libRoot/artifactFile"
      ideAndroidLibrary(libRoot, "${ModelCache.LOCAL_AARS}:$artifact", artifact)
    }

    val named = internedModels.getOrCreate(unnamed).lookup()

    assertTrue(named.artifactAddress == unnamed.artifactAddress)
    assertEquals("./app/libs/artifactFile", named.name)
  }

  private fun ideAndroidLibrary(
    libRoot: String,
    address: String,
    artifact: String
  ) = IdeAndroidLibraryImpl.create(
    artifactAddress = address,
    name = "",
    folder = File(libRoot),
    manifest = "$libRoot/AndroidManifest.xml",
    compileJarFiles = listOf("$libRoot/file.jar"),
    runtimeJarFiles = listOf("$libRoot/api.jar"),
    resFolder = "$libRoot/res",
    resStaticLibrary = File("$libRoot/res.apk"),
    assetsFolder = "$libRoot/assets",
    jniFolder = "$libRoot/jni",
    aidlFolder = "$libRoot/aidl",
    renderscriptFolder = "$libRoot/renderscriptFolder",
    proguardRules = "$libRoot/proguardRules",
    lintJar = "$libRoot/lint.jar",
    externalAnnotations = "$libRoot/externalAnnotations",
    publicResources = "$libRoot/publicResources",
    artifact = File(artifact),
    symbolFile = "$libRoot/symbolFile",
    deduplicate = internedModels::intern
  )
}
