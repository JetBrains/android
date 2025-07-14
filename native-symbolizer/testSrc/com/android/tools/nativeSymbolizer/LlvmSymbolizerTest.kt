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
package com.android.tools.nativeSymbolizer

import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.junit.Assert
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.io.IOException


class LlvmSymbolizerTest {

  val EXPECTED_SYMBOLS_FILE_NAME = "symbols.txt"
  val architectures = listOf("arm", "arm64", "x86", "x86_64")

  private val libFileName = File("libnative-lib.so")
  private val modulePath = File("/data/app/com.someapp.name-abcd09876abds==/lib/arm64/",
                                libFileName.name)

  @Test
  fun testLlvmSymbolizerFound() {
    Assert.assertTrue(File(getLlvmSymbolizerPath()).exists())
  }

  @Test
  fun testSymbolizeAll() {
    val symbolizer = createSymbolizer()
    for (arch in architectures) {
      val expectedSymbolsFile = getTestPath(arch, EXPECTED_SYMBOLS_FILE_NAME)
      Assert.assertTrue(expectedSymbolsFile.exists())
      for (line in expectedSymbolsFile.readLines()) {
        val symParts = line.split('|')
        val offset = symParts[0].toLong(16)
        val name = symParts[1]
        val sourceFile = symParts[2]
        val lineNumber = symParts[3].toInt()

        // +1 to get an address within the function, rather than function start address
        val offsetWithinFunction = offset + 1
        val symbol = symbolizer.symbolize(arch, modulePath, offsetWithinFunction)!!
        Assert.assertNotNull(symbol)
        Assert.assertEquals(name, symbol.name)
        Assert.assertEquals(sanitizeFilePathForComparison(sourceFile),
                            sanitizeFilePathForComparison(symbol.sourceFile))
        Assert.assertTrue(symbol.lineNumber >= lineNumber)
      }
    }
  }

  @Test
  fun testSymbolizeBinariesBuiltOnWindows() {
    val arch = "arm64"
    val binDir = "win"

    val symbolSource = DynamicSymbolSource().add(arch, getTestPath(binDir))
    val symLocator = SymbolFilesLocator(symbolSource)

    val symbolizer = LlvmSymbolizer(getLlvmSymbolizerPath(), symLocator)
    val expectedSymbolsFile = getTestPath(binDir, EXPECTED_SYMBOLS_FILE_NAME)
    Assert.assertTrue(expectedSymbolsFile.exists())

    for (line in expectedSymbolsFile.readLines()) {
      val symParts = line.split('|')
      val offset = symParts[0].toLong(16)
      val name = symParts[1]
      val sourceFile = symParts[2]
      val lineNumber = symParts[3].toInt()

      // +1 to get an address within the function, rather than function start address
      val offsetWithinFunction = offset + 1
      val symbol = symbolizer.symbolize(arch, modulePath, offsetWithinFunction)!!
      Assert.assertNotNull(symbol)
      Assert.assertEquals(name, symbol.name)
      Assert.assertEquals(sanitizeFilePathForComparison(sourceFile),
                          sanitizeFilePathForComparison(symbol.sourceFile))
      Assert.assertTrue(symbol.lineNumber >= lineNumber)
    }
  }

  @Test
  fun testSpaceInPath() {
    val tempDir = FileUtil.createTempDirectory("llvm-symbolizer", "space-test", true)
    val symbolDir = File(tempDir, "name with a space")
    FileUtil.copyDir(getTestPath("win"), symbolDir)
    val arch = "arm64"
    val symLocator = SymbolFilesLocator(DynamicSymbolSource().add(arch, symbolDir))
    val symbolizer = LlvmSymbolizer(getLlvmSymbolizerPath(), symLocator)
    val expectedSymbolsFile = File(symbolDir, EXPECTED_SYMBOLS_FILE_NAME)
    Assert.assertTrue(expectedSymbolsFile.exists())
    for (line in expectedSymbolsFile.readLines()) {
      val symParts = line.split('|')
      val offset = symParts[0].toLong(16)
      val name = symParts[1]
      val sourceFile = symParts[2]
      val lineNumber = symParts[3].toInt()

      // +1 to get an address within the function, rather than function start address
      val offsetWithinFunction = offset + 1
      val symbol = symbolizer.symbolize(arch, modulePath, offsetWithinFunction)!!
      Assert.assertNotNull(symbol)
      Assert.assertEquals(name, symbol.name)
      Assert.assertEquals(sanitizeFilePathForComparison(sourceFile),
                          sanitizeFilePathForComparison(symbol.sourceFile))
      Assert.assertTrue(symbol.lineNumber >= lineNumber)
    }
  }

  @Test
  fun testExeRestart() {
    val symbolizer = createSymbolizer()
    for (arch in architectures) {
      val expectedSymbolsFile = getTestPath(arch, EXPECTED_SYMBOLS_FILE_NAME)
      Assert.assertTrue(expectedSymbolsFile.exists())
      for (line in expectedSymbolsFile.readLines()) {
        val symParts = line.split('|')
        val offset = symParts[0].toLong(16)
        val name = symParts[1]

        val offsetWithinFunction = offset + 1
        val symbol = symbolizer.symbolize(arch, modulePath, offsetWithinFunction)!!
        Assert.assertNotNull(symbol)
        Assert.assertEquals(name, symbol.name)
        symbolizer.stop()
      }
    }
  }

  @Test(expected = IOException::class)
  fun testSymbolizerExeMissing() {
    val symLocator = SymbolFilesLocator(createSymbolSource())
    val notExistingPapth = getLlvmSymbolizerPath().replace("llvm-symbolizer", "not-llvm-symbolizer")
    val symbolizer = LlvmSymbolizer(notExistingPapth, symLocator)
    symbolizer.symbolize("x86", libFileName, 11)
    Assert.fail("IOException is expected to be thrown by the line above")
  }

  @Test
  fun testLlvmSymbolizerProcFreeze() {
    Assume.assumeFalse(SystemInfo.isWindows) // Windows doesn't have 'yes'
    val symLocator = SymbolFilesLocator(createSymbolSource())
    val yesPath = "yes" // call 'yes' instead llvm-symbolizer to simulate freezing symbolizer
    val symbolizer = LlvmSymbolizer(yesPath, symLocator, 50)
    for (addr in 0L..6L) {
      Assert.assertNull(symbolizer.symbolize("x86", libFileName, addr))
    }
  }

  @Test
  fun testUnknownSymbols() {
    val missingLibPath = File("/p/libnotexists.so")

    val symbolizer = createSymbolizer()
    var sym = symbolizer.symbolize("arm", missingLibPath, 12345)
    Assert.assertNull(sym)

    sym = symbolizer.symbolize("arm", libFileName, 0xffffffffff)
    Assert.assertNull(sym)
  }

  private fun createSymbolSource(): SymbolSource {
    var source = DynamicSymbolSource()

    for (arch in architectures) {
      source.add(arch, getTestPath(arch))
    }

    return source
  }

  private fun createSymbolizer(): NativeSymbolizer {
    val symLocator = SymbolFilesLocator(createSymbolSource())
    return LlvmSymbolizer(getLlvmSymbolizerPath(), symLocator)
  }

  /** Converts a file path relative to the test directory to an absolute file path. */
  private fun getTestPath(vararg part:String): File {
    var testDataDir = resolveWorkspacePath("tools/adt/idea/native-symbolizer/testData/bin/")

    for (p in part) {
      testDataDir = testDataDir.resolve(p)
    }

    return testDataDir.toAbsolutePath().toFile()
  }

  /**
   * Converts platform-specific path separators to a single common separator, making path
   * comparisons easier. This function is ONLY for comparing two paths and should be applied to both
   * values in the comparison.
   */
  private fun sanitizeFilePathForComparison(path: String): String {
    return File(path).normalize().absolutePath.replace('\\', '/')
  }
}
