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

import com.android.builder.model.v2.ide.AndroidLibraryData
import com.android.builder.model.v2.ide.Library
import com.android.builder.model.v2.ide.LibraryInfo
import com.android.builder.model.v2.ide.LibraryType
import com.android.builder.model.v2.ide.ProjectInfo
import com.android.ide.common.gradle.Component
import com.android.tools.idea.gradle.model.IdeArtifactLibrary
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.LibraryReference
import com.android.tools.idea.gradle.model.impl.IdeAndroidLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeJavaLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdePreResolvedModuleLibraryImpl
import com.android.tools.idea.gradle.model.impl.IdeUnresolvedModuleLibraryImpl
import com.google.common.truth.Truth.assertThat
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

    val ref = internedModels.internAndroidLibrary(unnamed) { unnamed }
    internedModels.prepare()
    val named = ref.lookup()

    assertThat(named).isEqualTo(unnamed.copy(name = "com.example:lib:1.0"))
  }

  @Test
  fun `get android library`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)

    val unnamedCopy = unnamed.copy()
    val namedRef = internedModels.internAndroidLibrary(unnamed) { unnamed }
    val namedCopyRef = internedModels.internAndroidLibrary(unnamedCopy) { unnamedCopy }

    internedModels.prepare()

    assertTrue(unnamed !== unnamedCopy)
    assertTrue(unnamed == unnamedCopy)
    assertTrue(namedRef == namedCopyRef)
    assertTrue(namedRef.lookup() === namedCopyRef.lookup())
  }

  @Test
  fun `distinct keys with same artifact are collapsed into one, and is picked deterministically`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)
    val libraryInfo = FakeLibrary.FakeLibraryInfo("com.example", "lib", "1.0")

    val unnamedCopy = unnamed.copy()
    val library1 = FakeLibrary(File(artifact), libraryInfo = libraryInfo)
    val library2 = FakeLibrary(File(artifact), libraryInfo = libraryInfo)

    internedModels.internAndroidLibraryV2(library1) { unnamed }
    internedModels.internAndroidLibraryV2(library2) { unnamedCopy }

    val internedModels2 = InternedModels(File(BUILD_ROOT))
    internedModels2.internAndroidLibraryV2(library2) { unnamedCopy }
    internedModels2.internAndroidLibraryV2(library1) { unnamed }

    val named = unnamed.copy(name="com.example:lib:1.0")

    assertThat(internedModels.createLibraryTable().libraries).containsExactly(named)
    assertThat(internedModels2.createLibraryTable().libraries).containsExactly(named)
  }

  @Test
  fun `libraries - same keys with same artifact, but different folders are collapsed into one, and is picked deterministically`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact).copy(folder = File("extractFolder1"))
    val androidLibraryData1 = FakeLibrary.FakeAndroidLibraryData(File("extractFolder1").resolve("res"))
    val androidLibraryData2 = FakeLibrary.FakeAndroidLibraryData(File("extractFolder2").resolve("res"))
    val libraryInfo = FakeLibrary.FakeLibraryInfo("com.example", "lib", "1.0")


    val unnamedCopy = unnamed.copy(folder = File("extractFolder2"))
    val library1 = FakeLibrary(File(artifact), androidLibraryData1, libraryInfo)
    val library2 = FakeLibrary(File(artifact), androidLibraryData2, libraryInfo)


    internedModels.internAndroidLibraryV2(library1) { unnamed }
    internedModels.internAndroidLibraryV2(library2) { unnamedCopy }

    val internedModels2 = InternedModels(File(BUILD_ROOT))
    internedModels2.internAndroidLibraryV2(library2) { unnamedCopy }
    internedModels2.internAndroidLibraryV2(library1) { unnamed }

    val named = unnamed.copy(name="com.example:lib:1.0")

    assertThat(internedModels.createLibraryTable().libraries).containsExactly(named)
    assertThat(internedModels2.createLibraryTable().libraries).containsExactly(named)
  }

  @Test
  fun `libraries - distinct keys with same artifact are collaped into one, and can be queried from each key`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)
    val unnamedCopy = unnamed.copy()
    val libraryInfo = FakeLibrary.FakeLibraryInfo("com.example", "lib", "1.0")

    val library1 = FakeLibrary(File(artifact), libraryInfo = libraryInfo)
    val library2 = FakeLibrary(File(artifact), libraryInfo = libraryInfo)

    internedModels.internAndroidLibraryV2(library1) { unnamed }
    internedModels.internAndroidLibraryV2(library2) { unnamedCopy }


    val named = unnamed.copy(name="com.example:lib:1.0")
    assertThat(internedModels.createLibraryTable().libraries).containsExactly(named)

    val ref1 = internedModels.getLibraryByKey(LibraryIdentity.fromLibrary(library1))
    val ref2 = internedModels.getLibraryByKey(LibraryIdentity.fromLibrary(library2))

    assertThat(ref1).isNotNull()
    assertThat(internedModels.lookup(ref1!!)).isEqualTo(named)

    assertThat(ref2).isNotNull()
    assertThat(internedModels.lookup(ref2!!)).isEqualTo(named)

    assertThat(ref1).isEqualTo(ref2)
  }

  @Test
  fun `different artifacts with same name are named deterministically`() {
    val libRoot = "/tmp/libs/lib"
    val artifact = "$libRoot/artifactFile"
    val artifactWithCapability = "$libRoot/artifactFileWithCapability"
    val unnamed = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifact)
    val unnamedWithCapability = ideAndroidLibrary(libRoot, "com.example:lib:1.0", artifactWithCapability)
    val libraryInfo = FakeLibrary.FakeLibraryInfo("com.example", "lib", "1.0")
    val libraryInfoWithCapabilities = FakeLibrary.FakeLibraryInfo("com.example", "lib", "1.0", capabilities = listOf("cap1"))

    val library1 = FakeLibrary(File(artifact), libraryInfo = libraryInfo)
    val library2 = FakeLibrary(File(artifactWithCapability), libraryInfo = libraryInfoWithCapabilities)

    internedModels.internAndroidLibraryV2(library1) { unnamed }
    internedModels.internAndroidLibraryV2(library2) { unnamedWithCapability }

    val internedModels2 = InternedModels(File(BUILD_ROOT))
    internedModels2.internAndroidLibraryV2(library2) { unnamedWithCapability }
    internedModels2.internAndroidLibraryV2(library1) { unnamed }

    val named = unnamed.copy(name="com.example:lib:1.0")
    val namedWithCapability = unnamedWithCapability.copy(name="com.example:lib:1.0 (1)")

    assertThat(internedModels.createLibraryTable().libraries).containsExactly(named, namedWithCapability)
    assertThat(internedModels2.createLibraryTable().libraries).containsExactly(named, namedWithCapability)
  }


  @Test
  fun `create java library`() {
    val libRoot = "/tmp/libs/lib"
    val unnamed = IdeJavaLibraryImpl(
      artifactAddress = "com.example:lib:1.0",
      component = Component.parse("com.example:lib:1.0"),
      name = "",
      artifact = File("$libRoot/artifactFile"),
      srcJar = null,
      docJar = null,
      samplesJar = null
    )

    val ref = internedModels.internJavaLibrary(LibraryIdentity.fromIdeModel(unnamed)) { unnamed }
    internedModels.prepare()
    val named = ref.lookup()

    assertTrue(named == unnamed.copy(name = "com.example:lib:1.0"))
  }

  @Test
  fun `get java library`() {
    val libRoot = "/tmp/libs/lib"
    val unnamed = IdeJavaLibraryImpl(
      artifactAddress = "com.example:lib:1.0",
      component = Component.parse("com.example:lib:1.0"),
      name = "",
      artifact = File("$libRoot/artifactFile"),
      srcJar = null,
      docJar = null,
      samplesJar = null
    )

    val unnamedCopy = unnamed.copy()
    val namedRef = internedModels.internJavaLibrary(LibraryIdentity.fromIdeModel(unnamed))  { unnamed }
    val namedCopyRef = internedModels.internJavaLibrary(LibraryIdentity.fromIdeModel(unnamedCopy))  { unnamedCopy }

    internedModels.prepare()

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
    val module1 = internedModels.internModuleLibrary(LibraryIdentity.fromIdeModel(module)) { module }
    val module2 = internedModels.internModuleLibrary(LibraryIdentity.fromIdeModel(copy)) { copy }

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
    val module1 = internedModels.internModuleLibrary(LibraryIdentity.fromIdeModel(module)) { module }
    val module2 = internedModels.internModuleLibrary(LibraryIdentity.fromIdeModel(copy)) { copy }

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

    val ref1 = internedModels.internAndroidLibrary(unnamed1) { unnamed1 }
    val ref2 = internedModels.internAndroidLibrary(unnamed2) { unnamed2 }

    internedModels.prepare()

    val named1 = ref1.lookup()
    val named2 = ref2.lookup()


    assertTrue(unnamed1.artifactAddress == unnamed2.artifactAddress)
    assertTrue(named1.artifactAddress == named2.artifactAddress)
    assertTrue(named1.name != named2.name)

    assertEquals("com.example:lib:1.0", named1.name)
    assertEquals("com.example:lib:1.0 (1)", named2.name)
  }

  @Test
  fun `name local aar library`() {
    val unnamed = let {
      val libRoot = "$BUILD_ROOT/app/libs"
      val artifact = "$libRoot/artifactFile"
      ideAndroidLibrary(libRoot, "${ModelCache.LOCAL_AARS}:$artifact", artifact, component = null)
    }

    val ref = internedModels.internAndroidLibrary(unnamed) { unnamed }
    internedModels.prepare()
    val named = ref.lookup()

    assertTrue(named.artifactAddress == unnamed.artifactAddress)
    assertEquals("./app/libs/artifactFile", named.name)
  }

  private fun ideAndroidLibrary(
    libRoot: String,
    address: String,
    artifact: String,
    component: Component? = Component.parse(address)
  ) = IdeAndroidLibraryImpl.create(
    artifactAddress = address,
    component = component,
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
    srcJar = "$libRoot/srcJar.jar",
    docJar = "$libRoot/docJar.jar",
    samplesJar = "$libRoot/samplesJar.jar",
    externalAnnotations = "$libRoot/externalAnnotations",
    publicResources = "$libRoot/publicResources",
    artifact = File(artifact),
    symbolFile = "$libRoot/symbolFile",
    deduplicate = internedModels::intern
  )

  class FakeLibrary(
    override val artifact: File? = null,
    override val androidLibraryData: AndroidLibraryData? = null,
    override val libraryInfo: LibraryInfo? = null
  ) : Library {
    override val key: String get() = error("unused")
    override val type: LibraryType = if (androidLibraryData != null) LibraryType.ANDROID_LIBRARY else LibraryType.JAVA_LIBRARY
    override val projectInfo: ProjectInfo get() = error("unused")
    override val lintJar: File get() = error("unused")
    override val srcJar: File get() = error("unused")
    override val docJar: File get() = error("unused")
    override val samplesJar get() = error("unused")

    class FakeAndroidLibraryData(override val resFolder: File) : AndroidLibraryData {
      override val manifest: File get() = error("unused")
      override val compileJarFiles: List<File> get() = error("unused")
      override val runtimeJarFiles: List<File> get() = error("unused")
      override val resStaticLibrary: File get() = error("unused")
      override val assetsFolder: File get() = error("unused")
      override val jniFolder: File get() = error("unused")
      override val aidlFolder: File get() = error("unused")
      override val renderscriptFolder: File get() = error("unused")
      override val proguardRules: File get() = error("unused")
      override val externalAnnotations: File get() = error("unused")
      override val publicResources: File get() = error("unused")
      override val symbolFile: File get() = error("unused")
    }

    class FakeLibraryInfo(override val group: String,
                          override val name: String,
                          override val version: String,
                          override val attributes: Map<String, String> = emptyMap(),
                          override val capabilities: List<String> = emptyList()) : LibraryInfo {
      override val buildType: String get() = error("unused")
      override val productFlavors: Map<String, String> get() = error("unused")
      override val isTestFixtures: Boolean get() = error("unused")
    }
  }
}
