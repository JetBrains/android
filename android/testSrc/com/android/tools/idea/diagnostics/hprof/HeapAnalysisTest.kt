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
import java.io.File
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

  /**
   * Get the contents of the baseline file, with system-dependent line endings
   */
  private fun getBaselineContents(path: Path): String {
    return String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      .replace(Regex("(\r\n|\n)"), System.lineSeparator())
  }

  /**
   * Returns path to a baseline file. Baselines may be different for different runtime versions.
   */
  private fun getBaselinePath(fileName: String): Path {
    val javaSpecString = System.getProperty("java.specification.version")
    val filenameWithPath = "tools/adt/idea/android/testData/profiling/analysis-baseline/$fileName"
    val file = File(filenameWithPath)

    val name = file.nameWithoutExtension
    val extension = if (file.extension != "") "." + file.extension else ""

    val javaSpecSpecificFileName = File(file.parent, "$name.$javaSpecString$extension").toString()
    val javaSpecSpecificFile = File(TestUtils.getWorkspaceRoot(), javaSpecSpecificFileName)

    if (javaSpecSpecificFile.exists()) {
      return javaSpecSpecificFile.toPath()
    }

    return AndroidTestPaths.adtSources().resolve(filenameWithPath)
  }

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

  private fun runHProfScenario(scenario: HProfBuilder.() -> Unit,
                               baselineFileName: String,
                               nominatedClassNames: List<String>? = null,
                               classNameMapping: ((Class<*>) -> String)? = null) {
    val hprofFile = tmpFolder.newFile()
    HProfTestUtils.createHProfOnFile(hprofFile,
                                     scenario,
                                     classNameMapping)
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

    ReferenceStore().use { refStore ->
      val scenario: HProfBuilder.() -> Unit = {
        val a = TestClassA()
        addRootGlobalJNI(listOf(MyRef(MyRef(MyRef(a))),
                                TestClassA(),
                                refStore.createWeakReference(TestClassA()),
                                refStore.createWeakReference(a)))
        addRootUnknown(TestClassA())
      }
      runHProfScenario(scenario, "testPathsThroughDifferentFields.txt",
                       listOf("TestClassB",
                              "TestClassA",
                              "TestString"))
    }
  }

  @Test
  fun testClassNameClash() {
    class MyTestClass1
    class MyTestClass2

    val scenario: HProfBuilder.() -> Unit = {
      addRootUnknown(MyTestClass1())
      addRootGlobalJNI(MyTestClass2())

    }
    val classNameMapping: (Class<*>) -> String = { c ->
      if (c == MyTestClass1::class.java ||
          c == MyTestClass2::class.java) {
        "MyTestClass"
      } else {
        c.name
      }
    }

    runHProfScenario(scenario, "testClassNameClash.txt",
                     listOf("MyTestClass!1",
                            "MyTestClass!2"),
                            classNameMapping)
  }

  @Test
  fun testJavaFrameGCRootPriority() {
    class C1
    class C2

    val scenario: HProfBuilder.() -> Unit = {
      val o1 = C1()
      val o2 = C2()
      addRootUnknown(o1)
      val threadSerialNumber = addStackTrace(Thread.currentThread(), 2)
      // This java frame should be overshadowed by root unknown
      addRootJavaFrame(o1, threadSerialNumber, 1)
      // This objects sole reference is from a frame
      addRootJavaFrame(o2, threadSerialNumber, 1)
    }
    runHProfScenario(scenario, "testJavaFrameGCRootPriority.txt",
                     listOf("C1", "C2"))
  }
}

/**
 * Helper class to keep strong references to objects referenced by newly created weak references.
 */
private class ReferenceStore : AutoCloseable {
  private val set = HashSet<Any?>()

  fun <T> createWeakReference(obj: T): WeakReference<T> {
    set.add(obj)
    return WeakReference(obj)
  }

  override fun close() {
    set.clear()
  }
}
