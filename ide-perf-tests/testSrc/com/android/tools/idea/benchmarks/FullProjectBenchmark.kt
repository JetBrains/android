/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.benchmarks

import com.android.SdkConstants
import com.android.tools.idea.psi.TagToClassMapper
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.moveCaret
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.google.common.truth.Truth.assertThat
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.ex.GlobalInspectionContextBase
import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.descendantsOfType
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.nextLeafs
import org.junit.After
import org.junit.Ignore
import java.io.File


/**
 * Contains the inputs necessary to run a layout critical user journey completion run, @see layoutAttributeCompletionBenchmark
 */
data class LayoutCompletionInput(
  val activityPath: String,
  val activityWindow: String,
  val layoutPath: String,
  val layoutWindow: String
)

/**
 * Contains the results of a layout critical user journey completion run, @see layoutAttributeCompletionBenchmark
 */
data class LayoutCompletionSample(
  val fastPathTime: Metric.MetricSample,
  val mediumPathTime: Metric.MetricSample,
  val slowPathTime: Metric.MetricSample
)

/**
 * Contains information relevant to a completion action performed in a source file.
 *
 * Only the sample property is uploaded to perfgate, the fileName and completionCount properties are used for debugging purposes.
 *
 * @property fileName the name of the file in which the completion event occurred.
 * @property completionCount the amount of lookupElements returned from calling completion.
 * @property sample time taken for all completion results to be fetched.
 */
data class CompletionSample(
  val fileName: String,
  val completionCount: Int,
  val sample: Metric.MetricSample
)

/**
 * Contains performance tests for a particular project defined by the subclasses.
 *
 * The gradleRule is shared between tests to preserve the sync state for benchmarks between tests.
 * The subclass must provide an AndroidGradleProjectRule and call FullProjectBenchmark.loadProject in @BeforeClass function.
 */
@Ignore
abstract class FullProjectBenchmark {
  abstract val gradleRule: AndroidGradleProjectRule

  @After
  fun tearDown() {
    runInEdtAndWait {
      clearCaches()
    }
  }

  fun fullProjectHighlighting(fileTypes: List<FileType>, projectName: String) {
    runInEdtAndWait {
      for (fileType in fileTypes) {
        measureHighlighting(fileType, projectName)
      }
    }
  }

  open fun fullProjectLintInspection(fileTypes: List<FileType>, projectName: String) {
    runInEdtAndWait {
      for (fileType in fileTypes) {
        measureLintInspections(fileType, projectName)
      }
    }
  }

  fun layoutAttributeCompletion(layoutAttributeCompletionInput: LayoutCompletionInput, projectName: String) {
    testLayoutCompletion(layoutAttributeCompletionInput, projectName, "Attribute")
  }

  fun layoutTagCompletion(layoutTagCompletionInput: LayoutCompletionInput, projectName: String) {
    testLayoutCompletion(layoutTagCompletionInput, projectName, "Tag")
  }

  fun testLocalLevelCompletionForKotlin(projectName: String) {
    runBenchmark(
      collectElements = { collectSuitableFiles(KotlinFileType.INSTANCE as FileType, ProjectScope.getContentScope(gradleRule.project), 50) },
      warmupAction = { performLocalCompletionForFile(it, 1) },
      benchmarkAction = { performLocalCompletionForFile(it, 5) },
      commitResults = { commitCompletionSamplesToBenchmark(it, projectName, KotlinLanguage.INSTANCE, "Local") }
    )
  }

  fun testTopLevelCompletionForKotlin(projectName: String) {
    runBenchmark(
      collectElements = { collectSuitableFiles(KotlinFileType.INSTANCE as FileType, ProjectScope.getContentScope(gradleRule.project), 50) },
      warmupAction = { performTopLevelCompletionForFile(it) },
      benchmarkAction = { performTopLevelCompletionForFile(it) },
      commitResults = { commitCompletionSamplesToBenchmark(it, projectName, KotlinLanguage.INSTANCE, "TopLevel") }
    )
  }

  private fun performLocalCompletionForFile(file: VirtualFile, maxNumberOfFunctions: Int): List<CompletionSample> {
    val fixture = gradleRule.fixture
    fixture.openFileInEditor(file)
    val psiFile = PsiManager.getInstance(gradleRule.project).findFile(file) as? PsiElement ?: return emptyList()
    val functions = psiFile.descendantsOfType<KtFunction>().filter { it.hasBlockBody() && it.bodyExpression != null }.toMutableList()
    val samples = mutableListOf<CompletionSample>()
    functions.take(maxNumberOfFunctions).forEach { function ->
      // Performing completion before the end of the function
      val offset = function.bodyExpression!!.endOffset - 1
      fixture.editor.caretModel.moveToOffset(offset)
      var lookupElementCount = 0
      val elapsedMillis = measureElapsedMillis {
        val arrayOfLookupElements = fixture.completeBasic()
        lookupElementCount = arrayOfLookupElements.size
      }
      samples.add(
        CompletionSample(
          file.name,
          lookupElementCount,
          Metric.MetricSample(System.currentTimeMillis(), elapsedMillis)))
    }
    return samples
  }

  private fun performTopLevelCompletionForFile(file: VirtualFile): List<CompletionSample> {
    val fixture = gradleRule.fixture
    fixture.openFileInEditor(file)
    val psiFile = PsiManager.getInstance(gradleRule.project).findFile(file) as? KtFile ?: return emptyList()
    val samples = mutableListOf<CompletionSample>()

    // Perform completion for function after import statement
    val offset = (psiFile.importList?.nextLeafs?.firstOrNull() as? PsiWhiteSpace)?.endOffset ?: 0
    fixture.editor.caretModel.moveToOffset(offset)
    var lookupElementCount = 0
    val elapsedMillis = measureElapsedMillis {
      fixture.type("fun ")
      val arrayOfLookupElements = fixture.completeBasic()
      lookupElementCount = arrayOfLookupElements.size
    }
    samples.add(CompletionSample(file.name, lookupElementCount, Metric.MetricSample(System.currentTimeMillis(), elapsedMillis)))
    UndoManager.getInstance(fixture.project).undo(TextEditorProvider.getInstance().getTextEditor(fixture.editor))

    // Perform completion for function before the end of the first available class, if any.
    val classes = (psiFile as PsiElement).descendantsOfType<KtClassOrObject>().filter { it.body != null }
    val body = classes.firstOrNull()?.body ?: return samples
    val innerOffset = body.endOffset - 1
    fixture.editor.caretModel.moveToOffset(innerOffset)
    lookupElementCount = 0
    val elapsedMillisInClass = measureElapsedMillis {
      fixture.type("fun ")
      val innerLookupElements = fixture.completeBasic()
      lookupElementCount = innerLookupElements.size
    }
    samples.add(CompletionSample(file.name, lookupElementCount, Metric.MetricSample(System.currentTimeMillis(), elapsedMillisInClass)))
    UndoManager.getInstance(fixture.project).undo(TextEditorProvider.getInstance().getTextEditor(fixture.editor))

    return samples
  }

  private fun commitCompletionSamplesToBenchmark(
    samples: List<CompletionSample>,
    projectName: String,
    language: Language,
    completionType: String
  ) {
    writeCsv(
      samples,
      projectName,
      completionBenchmark,
      resultName = completionType,
      columns = listOf("fileName", "completionCount", "time"),
      values = { listOf(fileName, completionCount, sample.sampleData) }
    )

    val metric = Metric("${projectName}_${language.displayName}_${completionType}")
    metric.addSamples(completionBenchmark, *samples.map { it.sample }.toTypedArray())
    metric.commit()
  }

  private fun testLayoutCompletion(layoutCompletionInput: LayoutCompletionInput, projectName: String, completionType: String) {
    runBenchmark(
      recordResults = { runLayoutEditingCuj(layoutCompletionInput) },
      runBetweenIterations = { clearCaches() },
      commitResults = { commitLayoutCompletionSamplesToBenchmark(it, projectName, completionType) }
    )
  }

  private fun runLayoutEditingCuj(layoutCompletionInput: LayoutCompletionInput): LayoutCompletionSample {
    val project = gradleRule.project
    val activityFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.basePath + layoutCompletionInput.activityPath)!!
    val layoutFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(project.basePath + layoutCompletionInput.layoutPath)!!
    val psiFile = PsiManager.getInstance(project).findFile(activityFile)
    val classMapper = TagToClassMapper.getInstance(ModuleUtilCore.findModuleForFile(psiFile)!!)
    val fixture = gradleRule.fixture

    // Measure completion time, slow path
    fixture.openFileInEditor(layoutFile)
    fixture.editor.caretModel.moveToOffset(0)
    fixture.moveCaret(layoutCompletionInput.layoutWindow)
    assertThat(classMapper.getClassMapFreshness(SdkConstants.CLASS_VIEW))
      .isEqualTo(TagToClassMapper.ClassMapFreshness.REBUILD_ENTIRE_CLASS_MAP)
    val slowPathSample = Metric.MetricSample(
      System.currentTimeMillis(),
      measureElapsedMillis {
        val lookupElements = fixture.completeBasic()
        assertThat(lookupElements).isNotEmpty()
      })

    // Measure completion time, fast path
    // TODO: http://b/162400668
    //assertThat(classMapper.getClassMapFreshness(SdkConstants.CLASS_VIEW))
    //  .isEqualTo(TagToClassMapper.ClassMapFreshness.VALID_CLASS_MAP)
    fixture.editor.caretModel.moveToOffset(0)
    fixture.moveCaret(layoutCompletionInput.layoutWindow)
    val fastPathSample = Metric.MetricSample(
      System.currentTimeMillis(),
      measureElapsedMillis {
        val lookupElements = fixture.completeBasic()
        assertThat(lookupElements).isNotEmpty()
      })

    // Edit something in the activity file
    fixture.openFileInEditor(activityFile)
    fixture.moveCaret(layoutCompletionInput.activityWindow)
    fixture.type("\nprintln(\"hello\")")
    UndoManager.getInstance(fixture.project).undo(TextEditorProvider.getInstance().getTextEditor(fixture.editor))

    // Measure attribute completion time, medium path
    fixture.openFileInEditor(layoutFile)
    fixture.editor.caretModel.moveToOffset(0)
    fixture.moveCaret(layoutCompletionInput.layoutWindow)
    // TODO: http://b/162400668
    //assertThat(classMapper.getClassMapFreshness(SdkConstants.CLASS_VIEW))
    //  .isEqualTo(TagToClassMapper.ClassMapFreshness.REBUILD_PARTIAL_CLASS_MAP)
    val mediumPathSample = Metric.MetricSample(
      System.currentTimeMillis(),
      measureElapsedMillis {
        val lookupElements = fixture.completeBasic()
        assertThat(lookupElements).isNotEmpty()
      })

    return LayoutCompletionSample(fastPathSample, mediumPathSample, slowPathSample)
  }

  private fun <T> writeCsv(
    samples: List<T>,
    projectName: String,
    benchmark: Benchmark,
    resultName: String?,
    columns: List<String>,
    values: (T).() -> List<Any>
  ) {
    val outputsDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR")
    if (outputsDir != null) {
      val fileName = listOfNotNull(projectName, benchmark.name, resultName).joinToString("_") { it.replace(' ', '_') } + ".csv"
      val resultsFile = File(outputsDir, fileName)

      resultsFile.printWriter().use { writer ->
        writer.println(columns.joinToString(separator = ","))
        for (sample in samples) {
          writer.println(sample.values().joinToString(separator = ","))
        }
      }

      println("Results written to ${resultsFile}")
    }
  }

  private fun commitLayoutCompletionSamplesToBenchmark(
    samples: List<LayoutCompletionSample>,
    projectName: String,
    completionType: String
  ) {
    val slowSamples = samples.map { it.slowPathTime }
    val mediumSamples = samples.map { it.mediumPathTime }
    val fastSamples = samples.map { it.fastPathTime }

    // Diagnostic logging.
    println("""
      ===
      Project: $projectName
      Benchmark name: ${layoutCompletionBenchmark.name}
      Completion type: $completionType
      Average Slow time: ${slowSamples.map { it.sampleData }.average()} ms
      Average Medium time: ${mediumSamples.map { it.sampleData }.average()} ms
      Average Fast time: ${fastSamples.map { it.sampleData }.average()} ms
      ===
    """.trimIndent())

    writeCsv(
      samples,
      projectName,
      layoutCompletionBenchmark,
      resultName = completionType,
      columns = listOf("fastPathTime", "mediumPathTime", "slowPathTime"),
      values = { listOf(fastPathTime.sampleData, mediumPathTime.sampleData, slowPathTime.sampleData) }
    )

    val slowPathMetric = Metric("${projectName}_${completionType}_Slow_Path")
    slowPathMetric.addSamples(layoutCompletionBenchmark, *slowSamples.toTypedArray())
    slowPathMetric.commit()
    val mediumPathMetric = Metric("${projectName}_${completionType}_Medium_Path")
    mediumPathMetric.addSamples(layoutCompletionBenchmark, *mediumSamples.toTypedArray())
    mediumPathMetric.commit()
    val fastPathMetric = Metric("${projectName}_${completionType}_Fast_Path")
    fastPathMetric.addSamples(layoutCompletionBenchmark, *fastSamples.toTypedArray())
    fastPathMetric.commit()
  }

  companion object {
    fun loadProject(gradleRule: AndroidGradleProjectRule, projectName: String) {
      val modulePath = AndroidTestBase.getModulePath("ide-perf-tests")
      gradleRule.fixture.testDataPath = modulePath + File.separator + "testData"
      disableExpensivePlatformAssertions(gradleRule.fixture)
      enableAllDefaultInspections(gradleRule.fixture)

      gradleRule.load(projectName)
      // TODO(b/149240940): gradleRule.generateSources() // Gets us closer to a production setup.
      waitForAsyncVfsRefreshes() // Avoids write actions during highlighting.
    }

    private fun collectSuitableFiles(fileType: FileType, scope: GlobalSearchScope, limit: Int = 100): List<VirtualFile> {
      val files = FileTypeIndex.getFiles(fileType, scope)
      assert(files.isNotEmpty())
      return files.sortedBy { it.name }.take(limit)
    }

    // Note: metadata for this benchmark is uploaded by IdeBenchmarkTestSuite.
    val highlightingBenchmark = Benchmark.Builder("Full Project Highlighting")
      .setDescription("""
        For each test project, this benchmark runs syntax highlighting on all files of a given file type
        and records the time elapsed per file. All measurements are given in milliseconds.

        Perfgate will by default only show the *average* per-file highlighting time. To get a
        better sense of performance on large/complex files, enable the 95th percentile trend line.
        This is especially important for XML because many XML files are trivial string resources.

        Note: "syntax highlighting" includes Android Lint and other inspections that are enabled by default.
      """.trimIndent())
      .setProject(EDITOR_PERFGATE_PROJECT_NAME)
      .build()

    // Note: metadata for this benchmark is uploaded by IdeBenchmarkTestSuite.
    val lintInspectionBenchmark = Benchmark.Builder("Full Project Lint Inspection")
      .setDescription("""
        For each test project, this benchmark runs the full suite of Android Lint inspections,
        on all files of a given file type and records the time elapsed per file. All measurements are
        given in milliseconds.

        Perfgate will by default only show the *average* per-file inspection time. To get a
        better sense of performance on large/complex files, enable the 95th percentile trend line.
        This is especially important for XML because many XML files are trivial string resources.
      """.trimIndent())
      .setProject(EDITOR_PERFGATE_PROJECT_NAME)
      .build()


    // Note: metadata for this benchmark is uploaded by IdeBenchmarkTestSuite.
    val layoutCompletionBenchmark = Benchmark.Builder("Layout Completion Benchmark")
      .setDescription("""
        This test record the time taken to provide completion results for view attributes and tags in layout 
          files in three typical scenarios. All measurements are given in milliseconds.

        Fast Path relates to time where completion results are cached and should be near instant.
        Medium Path relates to time where completion results are only cached for SDK and library results,
          all local completion elements must be recalculated. This happens after a change is made in Kotlin or Java
          files.
        Slow Path relates to time where there is no cache. Local, SDK and library results must be recalculated.
          This happens on project open, or after Gradle sync.
      """.trimIndent())
      .setProject(EDITOR_PERFGATE_PROJECT_NAME)
      .build()

    // Note: metadata for this benchmark is uploaded by IdeBenchmarkTestSuite.
    val completionBenchmark = Benchmark.Builder("Completion Benchmark")
      .setDescription("""
        This test records the time taken to provide completion results in various languages.

        This measures the total time to collect all completion results in top level and local scenarios, given
        little context or restraints. Therefore the completion results are many. This is a worst case scenario.

        This does not measure completion insert latency or popup latency.
      """.trimIndent())
      .setProject(EDITOR_PERFGATE_PROJECT_NAME)
      .build()
  }

  private fun clearCaches() {
    PsiManager.getInstance(gradleRule.project).dropPsiCaches()
    System.gc() // May help reduce noise, but it's just a hope. Investigate as needed.
    // Reset TagToClassMap cache
    gradleRule.project.allModules().forEach { TagToClassMapper.getInstance(it).resetAllClassMaps() }
  }

  private fun measureLintInspections(fileType: FileType, projectName: String) {
    // Setup
    val project = gradleRule.project
    val context = createGlobalContextForTool(AnalysisScope(project), project)
    val files = FileTypeIndex.getFiles(fileType, ProjectScope.getContentScope(project))
    assert(files.isNotEmpty())

    // Configure inspection for Android Lint
    val profileManager = InspectionProjectProfileManager.getInstance(project)
    val profile = profileManager.currentProfile

    // Non-Lint inspection tools to be re-enabled after run
    val disabledTools = mutableListOf<String>()

    for (tool in profile.getAllEnabledInspectionTools(project)) {
      if (!tool.tool.groupDisplayName.contains("Android Lint")) {
        val name = tool.tool.shortName
        profile.setToolEnabled(name, false, project)
        disabledTools.add(name)
      }
    }

    // Warmup
    for (file in files) {
      val psiFile = gradleRule.fixture.psiManager.findFile(file)!!
      (context as GlobalInspectionContextBase).doInspections(AnalysisScope(psiFile))

      do {
        UIUtil.dispatchAllInvocationEvents()
      }
      while (!context.isFinished)
    }

    // Reset
    clearCaches()

    // Measure
    var totalTimeMs = 0L
    val samples = mutableListOf<Metric.MetricSample>()
    val timePerFile = mutableListOf<Pair<String, Long>>()

    for (file in files) {
      val psiFile = gradleRule.fixture.psiManager.findFile(file)!!
      val timeMs = measureElapsedMillis {
        (context as GlobalInspectionContextBase).doInspections(AnalysisScope(psiFile))

        do {
          UIUtil.dispatchAllInvocationEvents()
        }
        while (!context.isFinished)
      }
      samples.add(Metric.MetricSample(System.currentTimeMillis(), timeMs))
      timePerFile.add(Pair(file.name, timeMs))
      totalTimeMs += timeMs
    }

    // Reset inspection profile
    for (name in disabledTools) {
      profile.setToolEnabled(name, true, project)
    }

    // Diagnostic logging
    println("""
      ===
      Project: $projectName
      File type: ${fileType.description}
      Total files: ${files.size}
      Total time: $totalTimeMs ms
      Average time: ${totalTimeMs / files.size} ms
      ===
    """.trimIndent())

    timePerFile.sortByDescending { (_, timeMs) -> timeMs }
    for ((fileName, timeMs) in timePerFile) {
      println("$timeMs ms --- $fileName")
    }

    writeCsv(
      timePerFile,
      projectName,
      lintInspectionBenchmark,
      resultName = fileType.name,
      columns = listOf("fileName", "time"),
      values = { toList() }
    )

    // Perfgate
    val metric = Metric("${projectName}_${fileType.description}")
    metric.addSamples(lintInspectionBenchmark, *samples.toTypedArray())
    metric.commit()
  }

  /** Measures highlighting performance for all project source files of the given type. */
  private fun measureHighlighting(fileType: FileType, projectName: String) {
    // Collect files.
    val project = gradleRule.project
    val files = FileTypeIndex.getFiles(fileType, ProjectScope.getContentScope(project))
    assert(files.isNotEmpty())

    // Warmup.
    val fixture = gradleRule.fixture
    var errorCount = 0
    for (file in files) {
      fixture.openFileInEditor(file)
      val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
      errorCount += errors.size

      // If the test happens to be broken, then the highlighting errors might hint at why.
      if (errors.isNotEmpty()) {
        println("There are ${errors.size} errors in ${file.name}")
        val errorList = errors.joinToString("\n") { it.description }
        println(errorList.prependIndent("    "))
      }
    }

    // Reset.
    clearCaches()

    // Measure.
    var totalTimeMs = 0L
    val samples = mutableListOf<Metric.MetricSample>()
    val timePerFile = mutableListOf<Pair<String, Long>>()
    for (file in files) {
      fixture.openFileInEditor(file)
      val timeMs = measureElapsedMillis {
        fixture.doHighlighting(HighlightSeverity.ERROR)
      }
      samples.add(Metric.MetricSample(System.currentTimeMillis(), timeMs))
      timePerFile.add(Pair(file.name, timeMs))
      totalTimeMs += timeMs
    }

    // Diagnostic logging.
    println("""
      ===
      Project: $projectName
      File type: ${fileType.description}
      Total files: ${files.size}
      Total errors: $errorCount
      Total time: $totalTimeMs ms
      Average time: ${totalTimeMs / files.size} ms
      ===
    """.trimIndent())
    timePerFile.sortByDescending { (_, timeMs) -> timeMs }
    for ((fileName, timeMs) in timePerFile) {
      println("$timeMs ms --- ${fileName}")
    }

    writeCsv(
      timePerFile,
      projectName,
      highlightingBenchmark,
      resultName = fileType.name,
      columns = listOf("fileName", "time"),
      values = { toList() }
    )

    // Perfgate.
    val metric = Metric("${projectName}_${fileType.description}")
    metric.addSamples(highlightingBenchmark, *samples.toTypedArray())
    metric.commit()
  }
}