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

import com.android.testutils.TestUtils
import com.android.tools.idea.diagnostics.hprof.analysis.AnalysisConfig
import com.android.tools.idea.diagnostics.hprof.analysis.AnalysisContext
import com.android.tools.idea.diagnostics.hprof.analysis.AnalyzeGraph
import com.android.tools.idea.diagnostics.hprof.analysis.ClassNomination
import com.android.tools.idea.diagnostics.hprof.classstore.HProfMetadata
import com.android.tools.idea.diagnostics.hprof.histogram.Histogram
import com.android.tools.idea.diagnostics.hprof.navigator.ObjectNavigator
import com.android.tools.idea.diagnostics.hprof.parser.HProfEventBasedParser
import com.android.tools.idea.diagnostics.hprof.util.IntList
import com.android.tools.idea.diagnostics.hprof.util.UByteList
import com.android.tools.idea.diagnostics.hprof.visitors.RemapIDsVisitor
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

  private fun getBaselineContents(path: Path): String {
    return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  }

  private fun getBaselinePath(fileName: String) =
    AndroidTestPaths.adtSources().resolve("android/testData/profiling/analysis-baseline/$fileName")

  class MemoryBackedIntList(size: Int) : IntList {
    private val array = IntArray(size)

    override fun get(index: Int): Int = array[index]
    override fun set(index: Int, value: Int) {
      array[index] = value
    }
  }

  class MemoryBackedUByteList(size: Int) : UByteList {
    private val array = ShortArray(size)

    override fun get(index: Int): Int = array[index].toInt()
    override fun set(index: Int, value: Int) {
      array[index] = value.toShort()
    }
  }

  private fun compareReportToBaseline(hprofFile: File, baselineFileName: String, nominatedClassNames: List<String>? = null) {
    FileChannel.open(hprofFile.toPath(), StandardOpenOption.READ).use { hprofChannel ->

      val progress = object : AbstractProgressIndicatorBase() {
      }
      progress.isIndeterminate = false

      val parser = HProfEventBasedParser(hprofChannel)
      val hprofMetadata = HProfMetadata.create(parser)
      val histogram = Histogram.create(parser, hprofMetadata.classStore)
      val nominatedClasses = ClassNomination(histogram, 5).nominateClasses()

      val remapIDsVisitor = RemapIDsVisitor.createMemoryBased()
      parser.accept(remapIDsVisitor, "id mapping")
      parser.setIdRemappingFunction(remapIDsVisitor.getRemappingFunction())
      hprofMetadata.remapIds(remapIDsVisitor.getRemappingFunction())

      val navigator = ObjectNavigator.createOnAuxiliaryFiles(
        parser,
        openTempEmptyFileChannel(),
        openTempEmptyFileChannel(),
        hprofMetadata,
        histogram.instanceCount
      )

      val parentList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val sizesList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val visitedList = MemoryBackedIntList(navigator.instanceCount.toInt() + 1)
      val refIndexList = MemoryBackedUByteList(navigator.instanceCount.toInt() + 1)

      val nominatedClassNamesLocal = nominatedClassNames ?: nominatedClasses.map { it.classDefinition.name }
      val analysisConfig = AnalysisConfig(
        perClassOptions = AnalysisConfig.PerClassOptions(
          classNames = nominatedClassNamesLocal,
          treeDisplayOptions = AnalysisConfig.TreeDisplayOptions.all()
        ),
        histogramOptions = AnalysisConfig.HistogramOptions(
          includeByCount = true,
          includeBySize = false,
          classByCountLimit = Int.MAX_VALUE
        ),
        disposerOptions = AnalysisConfig.DisposerOptions(
          includeDisposerTree = false,
          includeDisposedObjectsDetails = false,
          includeDisposedObjectsSummary = false
        ),
        metaInfoOptions = AnalysisConfig.MetaInfoOptions(
          include = false
        )
      )
      val analysisContext = AnalysisContext(
        navigator,
        analysisConfig,
        parentList,
        sizesList,
        visitedList,
        refIndexList,
        histogram
      )

      val analysisReport = AnalyzeGraph(analysisContext).analyze(progress)

      val baselinePath = getBaselinePath(baselineFileName)
      val baseline = getBaselineContents(baselinePath)
      Assert.assertEquals("Report doesn't match the baseline from file:\n$baselinePath",
                          baseline,
                          analysisReport)
    }
  }

  private fun openTempEmptyFileChannel(): FileChannel {
    return FileChannel.open(tmpFolder.newFile().toPath(),
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.DELETE_ON_CLOSE)
  }

  private fun runHProfScenario(scenario: HProfBuilder.() -> Unit, baselineFileName: String, nominatedClassNames: List<String>? = null) {
    val hprofFile = tmpFolder.newFile()
    FileOutputStream(hprofFile).use { fos ->

      // Simplify inner class names
      val regex = Regex(".*HeapAnalysisTest\\\$.*\\\$")
      val classNameMapping: (Class<*>) -> String = { c ->
        c.name.replace(regex, "")
      }
      HProfBuilder(DataOutputStream(fos), classNameMapping).apply(scenario).create()
    }
    compareReportToBaseline(hprofFile, baselineFileName, nominatedClassNames)
  }

  @Test
  fun testPathsThroughDifferentFields() {
    class MyRef(val referent: Any)
    class TestString(val s: String)
    class TestClassB(private val b1string: TestString, private val b2string: TestString)
    class TestClassA {
      private val a1string = TestString("TestString")
      private val a2b = TestClassB(a1string, TestString("TestString2"))
      private val a3b = TestClassB(a1string, TestString("TestString3"))
    }

    val scenario: HProfBuilder.() -> Unit = {
      val a = TestClassA()
      addRootGlobalJNI(listOf(MyRef(MyRef(MyRef(a))), TestClassA(), WeakReference(TestClassA()), WeakReference(a)))
      addRootUnknown(TestClassA())
    }
    runHProfScenario(scenario, "testPathsThroughDifferentFields.txt",
                     listOf("TestClassB",
                            "TestClassA",
                            "TestString"))
  }
}