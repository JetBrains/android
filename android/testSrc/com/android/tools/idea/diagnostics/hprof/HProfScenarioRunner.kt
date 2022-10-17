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
import com.android.tools.idea.diagnostics.hprof.util.ListProvider
import com.android.tools.idea.diagnostics.hprof.util.UByteList
import com.android.tools.idea.diagnostics.hprof.util.UShortList
import com.android.tools.idea.diagnostics.hprof.visitors.RemapIDsVisitor
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import org.junit.Assert
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

open class HProfScenarioRunner(private val tmpFolder: TemporaryFolder,
                               private val remapInMemory: Boolean) {

  val regex = Regex("^com\\.android\\.tools\\.idea\\.diagnostics\\.hprof\\..*\\\$.*\\\$")

  open fun adjustConfig(config: AnalysisConfig): AnalysisConfig = config

  open fun mapClassName(clazz: Class<*>): String {
    // Simplify inner class names
    return clazz.name.replace(regex, "")
  }

  fun run(scenario: HProfBuilder.() -> Unit,
          baselineFileName: String,
          nominatedClassNames: List<String>?,
          shouldMapClassNames: Boolean = true) {
    val hprofFile = tmpFolder.newFile()
    HProfTestUtils.createHProfOnFile(hprofFile,
                                     scenario,
                                     { c -> if (shouldMapClassNames) mapClassName(c) else c.name })
    compareReportToBaseline(hprofFile, baselineFileName, nominatedClassNames)
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

      val remapIDsVisitor = if (remapInMemory)
        RemapIDsVisitor.createMemoryBased()
      else
        RemapIDsVisitor.createFileBased(openTempEmptyFileChannel(), histogram.instanceCount)

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
          includeDisposerTreeSummary = false,
          includeDisposedObjectsDetails = false,
          includeDisposedObjectsSummary = false
        ),
        metaInfoOptions = AnalysisConfig.MetaInfoOptions(
          include = false
        ),
        dominatorTreeOptions = AnalysisConfig.DominatorTreeOptions(
          includeDominatorTree = false
        ),
        innerClassOptions = AnalysisConfig.InnerClassOptions(
          includeInnerClassSection = false
        )
      ).let { adjustConfig(it) }
      val analysisContext = AnalysisContext(
        navigator,
        analysisConfig,
        parentList,
        sizesList,
        visitedList,
        refIndexList,
        histogram
      )

      val analysisReport = AnalyzeGraph(analysisContext, memoryBackedListProvider).analyze(progress).mainReport.toString()

      val baselinePath = getBaselinePath(baselineFileName)
      val baseline = getBaselineContents(baselinePath)
      Assert.assertEquals("Report doesn't match the baseline from file:\n$baselinePath",
                          baseline,
                          analysisReport)
    }
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
    val javaSpecSpecificFile = TestUtils.resolveWorkspacePathUnchecked(javaSpecSpecificFileName)

    if (Files.exists(javaSpecSpecificFile)) {
      return javaSpecSpecificFile
    }

    return TestUtils.resolveWorkspacePath(filenameWithPath)
  }

  class MemoryBackedIntList(size: Int) : IntList {
    private val array = IntArray(size)

    override fun get(index: Int): Int = array[index]
    override fun set(index: Int, value: Int) {
      array[index] = value
    }
  }

  class MemoryBackedUShortList(size: Int) : UShortList {
    private val array = ShortArray(size)

    override fun get(index: Int): Int = array[index].toInt()
    override fun set(index: Int, value: Int) {
      array[index] = value.toShort()
    }
  }

  class MemoryBackedUByteList(size: Int) : UByteList {
    private val array = ShortArray(size)

    override fun get(index: Int): Int = array[index].toInt()
    override fun set(index: Int, value: Int) {
      array[index] = value.toShort()
    }
  }

  object memoryBackedListProvider: ListProvider {
    override fun createUByteList(name: String, size: Long) = MemoryBackedUByteList(size.toInt())
    override fun createUShortList(name: String, size: Long) = MemoryBackedUShortList(size.toInt())
    override fun createIntList(name: String, size: Long) = MemoryBackedIntList(size.toInt())
  }

  private fun openTempEmptyFileChannel(): FileChannel {
    return FileChannel.open(tmpFolder.newFile().toPath(),
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.DELETE_ON_CLOSE)
  }
}

