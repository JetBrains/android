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
package com.android.tools.idea.diagnostics.hprof

import com.android.tools.idea.diagnostics.hprof.analysis.HProfAnalysis
import com.android.tools.idea.util.AndroidTestPaths
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class HeapAnalysisTest {

  private val tmpFolder: TemporaryFolder = TemporaryFolder()

  @Before
  fun setUp() {
    tmpFolder.create()
  }

  @After
  fun tearDown() {
    tmpFolder.delete()
  }

  private fun getBaselineContents(fileName: String): String {
    return String(Files.readAllBytes(
      AndroidTestPaths.adtSources().resolve("android/testData/profiling/analysis-baseline/$fileName")), StandardCharsets.UTF_8)
  }

  private fun compareReportToBaseline(hprofFile: File, baselineReport: String) {
    FileChannel.open(hprofFile.toPath(), StandardOpenOption.READ).use { hprofChannel ->
      val analysis = HProfAnalysis(hprofChannel, object : HProfAnalysis.TempFilenameSupplier {
        override fun getTempFilePath(type: String): Path {
          return tmpFolder.newFile().toPath()
        }
      })
      analysis.setIncludeMetaInfo(false)
      val progress = object : AbstractProgressIndicatorBase() {
      }
      progress.isIndeterminate = false
      val generatedReport = analysis.analyze(progress)

      Assert.assertEquals(baselineReport, generatedReport)
    }
  }

  private fun runHProfScenario(scenario: HProfBuilder.() -> Unit, baselineFileName: String) {
    val hprofFile = tmpFolder.newFile()
    FileOutputStream(hprofFile).use { fos ->

      // Simplify inner class names
      val regex = Regex("HeapAnalysisTest\\\$.*\\\$")
      val classNameMapping: (Class<*>) -> String = { c ->
        c.name.replace(regex, "")
      }
      HProfBuilder(DataOutputStream(fos), classNameMapping).apply(scenario).create()
    }
    val baseline = getBaselineContents(baselineFileName)
    compareReportToBaseline(hprofFile, baseline)
  }

  @Test
  fun testPathsThroughDifferentFields() {
    class TestString(val s: String)
    class TestClassB(private val b1string: TestString, private val b2string: TestString)
    class TestClassA {
      private val a1string = TestString("TestString")
      private val a2b = TestClassB(a1string, TestString("TestString2"))
      private val a3b = TestClassB(a1string, TestString("TestString3"))
    }
    class Root(val o: Any)

    val scenario: HProfBuilder.() -> Unit = {
      val a = TestClassA()
      addRootGlobalJNI(listOf(a, TestClassA(), WeakReference(TestClassA()), WeakReference(a)))
      addRootUnknown(TestClassA())
    }
    runHProfScenario(scenario, "testPathsThroughDifferentFields.txt")
  }
}