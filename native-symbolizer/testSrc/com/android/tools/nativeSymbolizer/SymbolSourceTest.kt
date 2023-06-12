/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.nativeSymbolizer

import com.android.sdklib.devices.Abi
import com.android.tools.idea.projectsystem.NamedIdeaSourceProviderBuilder.Companion.create
import com.android.tools.idea.projectsystem.SourceProviderManager.Companion.replaceForTest
import com.android.tools.idea.testing.AndroidProjectRule.Companion.inMemory
import com.google.common.truth.Truth
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class SymbolSourceTest {

  @Rule @JvmField
  var projectRule = inMemory().initAndroid(true)

  @Test
  fun dynamicSymbolSourceReturnsDirectoriesPerArch() {
    val armLibraries = File("path/to/arm/libraries")
    val moreArmLibraries = File("path/to/arm/more_libraries")
    val x86Libraries = File("path/to/x86/libraries")
    val moreX86Libraries = File("path/to/x86/more_libraries")

    val source = DynamicSymbolSource()
      .add(Abi.ARMEABI.cpuArch, armLibraries)
      .add(Abi.ARMEABI.cpuArch, moreArmLibraries)
      .add(Abi.X86.cpuArch, x86Libraries)
      .add(Abi.X86.cpuArch, moreX86Libraries)

    Assert.assertEquals(2, source.getDirsFor(Abi.ARMEABI).size)
    Assert.assertTrue(source.getDirsFor(Abi.ARMEABI).containsAll(listOf(armLibraries,
                                                                        moreArmLibraries)))

    Assert.assertEquals(2, source.getDirsFor(Abi.X86).size)
    Assert.assertTrue(source.getDirsFor(Abi.X86).containsAll(listOf(x86Libraries,
                                                                    moreX86Libraries)))

    Assert.assertTrue(source.getDirsFor(Abi.MIPS).isEmpty())
  }

  @Test
  fun mergeSymbolSourceCombinesSources() {
    val armLibraries = File("path/to/arm/libraries")
    val moreArmLibraries = File("path/to/arm/more_libraries")

    val x86Libraries = File("path/to/x86/libraries")
    val moreX86Libraries = File("path/to/x86/more_libraries")

    val armSource = DynamicSymbolSource()
      .add(Abi.ARMEABI.cpuArch, armLibraries)
      .add(Abi.ARMEABI.cpuArch, moreArmLibraries)

    val x86Source = DynamicSymbolSource()
      .add(Abi.X86.cpuArch, x86Libraries)
      .add(Abi.X86.cpuArch, moreX86Libraries)

    // Even though the arm and x86 sources are separated, when we use our merge source, it should
    // act as if everything was added to the same DynamicSymbolSource.
    val source = MergeSymbolSource(listOf(armSource, x86Source))

    Assert.assertEquals(2, source.getDirsFor(Abi.ARMEABI).size)
    Assert.assertTrue(source.getDirsFor(Abi.ARMEABI).containsAll(listOf(armLibraries,
                                                                        moreArmLibraries)))

    Assert.assertEquals(2, source.getDirsFor(Abi.X86).size)
    Assert.assertTrue(source.getDirsFor(Abi.X86).containsAll(listOf(x86Libraries,
                                                                    moreX86Libraries)))

    Assert.assertTrue(source.getDirsFor(Abi.MIPS).isEmpty())
  }

  @Test
  fun emptyJniLibs() {
    val src = JniSymbolSource(projectRule.module)
    val result: Collection<File?> = src.getDirsFor(Abi.ARM64_V8A)
    Truth.assertThat(result).isEmpty()
  }

  @Test
  fun withJniLibs() {
    val src = JniSymbolSource(projectRule.module)

    // Create foo.so file and get the jniLibs URL.
    val jniLibsUrl = projectRule.fixture
      .addFileToProject("jniLibs/arm64-v8a/foo.so", "hello, world!")
      .virtualFile
      .parent
      .parent
      .url
    Truth.assertThat(jniLibsUrl).endsWith("jniLibs")

    // Create a new source provider that contains the new jniLibsUrl, and inject it for the test.
    replaceForTest(
      AndroidFacet.getInstance(projectRule.module)!!,
      projectRule.fixture.projectDisposable,
      create("main", "AndroidManifest.xml")
        .withJniLibsDirectoryUrls(listOf(jniLibsUrl))
        .build())
    val result: Collection<File?> = src.getDirsFor(Abi.ARM64_V8A)
    Truth.assertThat(result).isNotEmpty()
  }
}