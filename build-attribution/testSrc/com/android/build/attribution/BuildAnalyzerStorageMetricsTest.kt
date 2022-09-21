import com.android.build.attribution.BuildAnalysisResults
import com.android.build.attribution.BuildAnalysisResultsMessage
import com.android.build.attribution.BuildAnalyzerStorageManagerImpl
import com.android.build.attribution.analyzers.AlwaysRunTasksAnalyzer
import com.android.build.attribution.analyzers.AnalyzerNotRun
import com.android.build.attribution.analyzers.AnnotationProcessorsAnalyzer
import com.android.build.attribution.analyzers.CriticalPathAnalyzer
import com.android.build.attribution.analyzers.DownloadsAnalyzer
import com.android.build.attribution.analyzers.GarbageCollectionAnalyzer
import com.android.build.attribution.analyzers.JetifierUsageAnalyzerResult
import com.android.build.attribution.analyzers.NoDataFromSavedResult
import com.android.build.attribution.analyzers.NoncacheableTasksAnalyzer
import com.android.build.attribution.analyzers.ProjectConfigurationAnalyzer
import com.android.build.attribution.analyzers.TaskCategoryWarningsAnalyzer
import com.android.build.attribution.analyzers.TasksConfigurationIssuesAnalyzer
import com.android.build.attribution.data.AlwaysRunTaskData
import com.android.build.attribution.data.AnnotationProcessorData
import com.android.build.attribution.data.GarbageCollectionData
import com.android.build.attribution.data.PluginBuildData
import com.android.build.attribution.data.PluginData
import com.android.build.attribution.data.ProjectConfigurationData
import com.android.build.attribution.data.TaskData
import com.android.build.attribution.data.TasksSharingOutputData
import com.android.build.attribution.proto.converters.BuildResultsProtoMessageConverter
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.util.BuildMode
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.project.guessProjectDir
import org.junit.After
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.time.Duration
import java.util.UUID

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
@Ignore("Performance experiments, should be invoked manually only")
class BuildAnalyzerStorageMetricsTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  @After
  fun cleanup() {
    StudioFlags.BUILD_ANALYZER_HISTORY.clearOverride()
  }

  @Test
  fun fileSizes() {
    println("small amount of data: ${storeBuildResultsPlaceholderData(10)!!.length()/1000} kilobytes")
    println("medium amount of data: ${storeBuildResultsPlaceholderData(100)!!.length()/1000} kilobytes")
    println("large amount of data: ${storeBuildResultsPlaceholderData(1000)!!.length()/1000} kilobytes")
    println("larger amount of data: ${storeBuildResultsPlaceholderData(10000)!!.length()/1000} kilobytes")
  }

  //Divide each of these by 11
  @Test
  fun smallDataStorageTime() {
    for(i in 0..10) {
      val startTime = System.currentTimeMillis()
      storeBuildResultsPlaceholderData(10)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to construct and store build results in file")
    }
  }

  @Test
  fun mediumDataStorageTime() {
    for(i in 0..10) {
      val startTime = System.currentTimeMillis()
      storeBuildResultsPlaceholderData(100)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to construct and store build results in file")
    }
  }

  @Test
  fun largeDataStorageTime() {
    for(i in 0..10) {
      val startTime = System.currentTimeMillis()
      storeBuildResultsPlaceholderData(1000)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to construct and store build results in file")
    }
  }

  @Test
  fun largerDataStorageTime() {
    for(i in 0..10) {
      val startTime = System.currentTimeMillis()
      storeBuildResultsPlaceholderData(10000)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to construct and store build results in file")
    }
  }

  @Test
  fun smallDataRetrievalTime() {
    for(i in 0..10) {
      val file = storeBuildResultsPlaceholderData(10)
      val startTime = System.currentTimeMillis()
      getHistoricBuildResultsFromFile(file!!)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to retrieve build results from file")
    }
  }

  @Test
  fun mediumDataRetrievalTime() {
    for(i in 0..10) {
      val file = storeBuildResultsPlaceholderData(100)
      val startTime = System.currentTimeMillis()
      getHistoricBuildResultsFromFile(file!!)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to retrieve build results from file")
    }
  }

  @Test
  fun largeDataRetrievalTime() {
    for(i in 0..10) {
      val file = storeBuildResultsPlaceholderData(1000)
      val startTime = System.currentTimeMillis()
      getHistoricBuildResultsFromFile(file!!)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to retrieve build results from file")
    }
  }

  @Test
  fun largerDataRetrievalTime() {
    for(i in 0..10) {
      val file = storeBuildResultsPlaceholderData(10000)
      val startTime = System.currentTimeMillis()
      getHistoricBuildResultsFromFile(file!!)
      val finishTime = System.currentTimeMillis()
      println("${finishTime-startTime} milliseconds taken to retrieve build results from file")
    }
  }

  private fun getHistoricBuildResultsFromFile(file: File) {
    val stream = FileInputStream(file)
    val message = BuildAnalysisResultsMessage.parseDelimitedFrom(stream)
    BuildResultsProtoMessageConverter.convertBuildAnalysisResultsFromBytesToObject(message)
  }

  private fun storeBuildResultsPlaceholderData(listSize: Int): File? {
    val annotationProcessorData = mutableListOf<AnnotationProcessorData>()
    val nonIncrementalAnnotationProcessorData = mutableListOf<AnnotationProcessorData>()
    for (i in 0 until listSize) {
      annotationProcessorData.add(AnnotationProcessorData("annotationProcessorClass$i", Duration.ofMillis(i.toLong())))
    }
    for (i in 0 until listSize) {
      nonIncrementalAnnotationProcessorData.add(AnnotationProcessorData("annotationProcessorClass$i", Duration.ofMillis(i.toLong())))
    }
    val annotationProcessorsAnalyzerResult = AnnotationProcessorsAnalyzer.Result(
      annotationProcessorData,
      nonIncrementalAnnotationProcessorData
    )

    // create and populate plugins cache
    val pluginCache = mutableMapOf<String, PluginData>()
    for (i in 0 until listSize) {
      val name = "plugin.name.$i"
      pluginCache[name] = PluginData(PluginData.PluginType.BINARY_PLUGIN, name)
    }
    // create and populate tasks cache
    val taskCache = mutableMapOf<String, TaskData>()
    for (i in 0 until listSize) {
      val plugin = PluginData(PluginData.PluginType.BINARY_PLUGIN, "plugin.name.$i")
      val task = TaskData(
        taskName = "taskName$i",
        projectPath = ":project$i",
        originPlugin = plugin,
        executionStartTime = (i + 1234567).toLong(),
        executionEndTime = (i + 1234567).toLong(),
        executionMode = TaskData.TaskExecutionMode.FULL,
        executionReasons = listOf(i.toString(), (i + 1).toString(), (i + 2).toString())
      )
      taskCache[task.getTaskPath()] = task
    }

    val alwaysRunTaskData = mutableListOf<AlwaysRunTaskData>()
    for (i in 0 until listSize) {
      alwaysRunTaskData.add(
        AlwaysRunTaskData(taskCache.values.asSequence().drop(i).first(), AlwaysRunTaskData.Reason.NO_OUTPUTS_WITH_ACTIONS))
    }
    val alwaysRunTaskDataResult = AlwaysRunTasksAnalyzer.Result(alwaysRunTaskData)

    val criticalPathPluginData = mutableListOf<PluginBuildData>()
    val criticalPathData = mutableListOf<TaskData>()
    for (i in 0 until listSize) {
      criticalPathData.add(taskCache.values.asSequence().drop(i).first())
      criticalPathPluginData.add(PluginBuildData(pluginCache.values.asSequence().drop(i).first(), i.toLong()))
    }
    val criticalPathAnalyzerResult = CriticalPathAnalyzer.Result(
      criticalPathData,
      criticalPathPluginData,
      12345,
      67891
    )
    val noncacheableTasksAnalyzerTaskData = mutableListOf<TaskData>()
    for (i in 0 until listSize) {
      noncacheableTasksAnalyzerTaskData.add(taskCache.values.asSequence().drop(i).first())
    }
    val noncacheableTasksAnalyzerResult = NoncacheableTasksAnalyzer.Result(noncacheableTasksAnalyzerTaskData)
    val garbageCollectionDataList = mutableListOf<GarbageCollectionData>()
    // This will never be more than 10
    for (i in 0 until 10) {
      garbageCollectionDataList.add(GarbageCollectionData("gc_name", i.toLong()))
    }
    val garbageCollectionAnalyzerResult = GarbageCollectionAnalyzer.Result(garbageCollectionDataList, 12345, true)
    val pluginsConfigurationDataMap = mutableMapOf<PluginData, Long>()
    val projectConfigurationData = mutableListOf<ProjectConfigurationData>()
    val allAppliedPlugins = mutableMapOf<String, List<PluginData>>()
    for (i in 0 until listSize) {
      val plugin = pluginCache.values.asSequence().drop(i).first()
      pluginsConfigurationDataMap[plugin] = 12345
      val projectPath = ":project:path:$i"
      projectConfigurationData.add(ProjectConfigurationData(projectPath, 12345, listOf(), listOf()))
      allAppliedPlugins[projectPath] = listOf(plugin)
    }
    val projectConfigurationAnalyzerResult = ProjectConfigurationAnalyzer.Result(
      pluginsConfigurationDataMap,
      projectConfigurationData,
      allAppliedPlugins
    )
    val taskConfigurationData = mutableListOf<TaskData>()
    for (i in 0 until listSize) {
      taskConfigurationData.add(taskCache.values.asSequence().drop(i).first())
    }
    val tasksSharingOutputData = listOf(TasksSharingOutputData("/home/user/path/to/the/project/root/build/shared", taskConfigurationData))
    val taskConfigurationAnalyzerResult = TasksConfigurationIssuesAnalyzer.Result(tasksSharingOutputData)
    val configurationCachingCompatibilityAnalyzerResult = NoDataFromSavedResult
    val jetifierUsageAnalyzerResult = JetifierUsageAnalyzerResult(AnalyzerNotRun)
    val downloadAnalyzerResult = DownloadsAnalyzer.AnalyzerIsDisabled
    val results = BuildAnalysisResults(
      GradleBuildInvoker.Request.RequestData(
        BuildMode.DEFAULT_BUILD_MODE,
        File("/home/user/path/to/the/project/root"),
        listOf("task1", "task2"),
        listOf("e1", "e2"),
        listOf("c1", "c2"),
        mapOf(Pair("a", "b"), Pair("c", "d")),
        false
      ),
      annotationProcessorsAnalyzerResult,
      alwaysRunTaskDataResult,
      criticalPathAnalyzerResult,
      noncacheableTasksAnalyzerResult,
      garbageCollectionAnalyzerResult,
      projectConfigurationAnalyzerResult,
      taskConfigurationAnalyzerResult,
      configurationCachingCompatibilityAnalyzerResult,
      jetifierUsageAnalyzerResult,
      downloadAnalyzerResult,
      TaskCategoryWarningsAnalyzer.Result(listOf()),
      UUID.randomUUID().toString(),
      taskCache as HashMap<String, TaskData>,
      pluginCache as HashMap<String, PluginData>
    )
     //Uncomment to see the nicely printed result:
    //val resultsMessage = BuildResultsProtoMessageConverter.convertBuildAnalysisResultsFromObjectToBytes(
    //      results,
    //      results.getPluginMap(),
    //      results.getTaskMap()
    //    )
    //println(TextFormat.printer().printToString(resultsMessage))
    BuildAnalyzerStorageManagerImpl(projectRule.project).storeBuildResultsInFile(results)
    return projectRule.project.guessProjectDir()?.toIoFile()?.resolve("build-analyzer-history-data")?.resolve(results.getBuildSessionID())!!
  }
}