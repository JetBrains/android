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
import com.android.test.testutils.TestUtils.resolveWorkspacePath
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * A specialized symbol source for these test. The thing that makes this symbol source "bad" is that
 * it does nothing to ensure "good" output. For example, by design it allows duplicate paths to be
 * returned.
 */
class BadSymbolSource: SymbolSource {
  // Do not use a set. We want this to store duplicates.
  private val paths = mutableListOf<File>()

  fun add(path: File): BadSymbolSource {
    paths.add(path)
    return this
  }

  override fun getDirsFor(abi: Abi): Collection<File> {
    return paths
  }
}

class SymbolFilesLocatorTest {

  // The initial values will not be used. It'll be reset in setUp().
  private var source: SymbolSource = DynamicSymbolSource()
  private var locator: SymbolFilesLocator = SymbolFilesLocator(source)

  @Before
  fun setUp() {
    source = DynamicSymbolSource()
      .add("arm", getTestPath("arm"))
      .add("arm64", getTestPath("arm64"))
      .add("x86", getTestPath("x86"))
      .add("x86_64", getTestPath("x86_64"))

    locator = SymbolFilesLocator(source)
  }

  @Test
  fun testFindNothingForInvalidArch() {
    val found = locator.getDirectories("not-a-real-arch")
    Assert.assertTrue(found.isEmpty())
  }

  @Test
  fun testFindsSomethingForValidArch() {
    val found = locator.getDirectories("arm")
    Assert.assertFalse(found.isEmpty())
  }

  @Test
  fun doesNotReturnDuplicateDirectories() {
    // Add the same directory to the symbol source multiple times so that it will return
    // duplicates. BadSymbolSource will return the paths regardless of ABI.
    source = BadSymbolSource()
      .add(getTestPath("arm"))
      .add(getTestPath("arm"))
    Assert.assertEquals(2, source.getDirsFor(Abi.ARMEABI).size)

    locator = SymbolFilesLocator(source)
    Assert.assertEquals(1, locator.getDirectories("arm").size)
  }

  @Test
  fun onlyReturnsDirectoriesWithLibraries() {
    // Make sure that the symbol directory exists (even though there are no symbols in it).
    val libDirectory = getTestPath("no_libraries")
    Assert.assertTrue(libDirectory.exists())
    Assert.assertNotEquals(null, libDirectory.listFiles());
    Assert.assertNotEquals(0, libDirectory.listFiles().size);

    // BadSymbolSource will return the same paths regardless of ABI.
    source = BadSymbolSource().add(libDirectory)
    Assert.assertEquals(1, source.getDirsFor(Abi.ARMEABI).size)

    locator = SymbolFilesLocator(source)
    Assert.assertTrue(locator.getDirectories("arm").isEmpty())
  }

  @Test
  fun findsSymbolFilesInDirectory() {
    // Because of how BadSymbolSource works, we'll get all paths for all architectures, so we
    // should get two paths ("arm" and "arm64"). Normally this would be bad (could get unusable
    // libraries) but it works for this test since we'll see that we're getting libraries from
    // multiple directories.
    source = BadSymbolSource().add(getTestPath("arm")).add(getTestPath("arm64"))
    locator = SymbolFilesLocator(source)

    val found = locator.getFiles("arm")
    Assert.assertEquals(2, found.size)

    Assert.assertTrue(found.contains(getTestPath("arm", "libnative-lib.so")))
    Assert.assertTrue(found.contains(getTestPath("arm64", "libnative-lib.so")))
  }

  /** Converts a file path relative to the test directory to an absolute file path. */
  private fun getTestPath(vararg part:String): File {
    var testDataDir = resolveWorkspacePath("tools/adt/idea/native-symbolizer/testData/bin/")

    for (p in part) {
      testDataDir = testDataDir.resolve(p)
    }

    return testDataDir.toAbsolutePath().toFile()
  }
}