package com.android.tools.idea

import com.android.testutils.TestUtils
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.WindowDeviationAnalyzer
import com.intellij.util.io.ZipUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

const val MAC = "mac"

/**
 * This test tracks the file size information of Android Studio bundle.
 *
 * The following information are collected:
 *   1) Plugin size, for each plugin under plugins/
 *   2) Platform size, which is the combined size of all files outside of plugins/ directory
 *   3) File count, of all files in the bundle
 *
 * A perflogger benchmark (and json) is generated for each data point. Format is as follows:
 *   1) <plugin-name>_<platform>.json
 *   2) android-studio.<platform>_platform.json
 *   3) android-studio.<platform>_files.json
 *
 * Platform is one of linux, mac, or win.
 */
class AndroidStudioBundleSizeTest {
  /**
   * Represents the size information of a single plugin.
   */
  private class PluginSizeInfo(
    val name: String,
    val size: Long
  )

  /**
   * Represents the size information of a Studio bundle, which contains platform size as well as
   * the sizes of each plugin.
   */
  private class StudioBundleSizeInfo(
    val name: String,
    val platform: String,
    val platformSize: Long,
    val files: Long,
    val pluginSizeInfos: List<PluginSizeInfo>
  )

  private val benchmark: Benchmark = Benchmark.Builder("Android Studio Binary Size").build()

  @Test
  fun testLoggingBinarySize(): Unit = runBlocking {
    val files = listOf(
      "android-studio.win.zip",
      "android-studio.mac.zip",
      "android-studio.linux.zip"
    )

    // Unzip bundles
    val scratchDir = TestUtils.createTempDirDeletedOnExit()
    val unzippedDirs = files
      .map { zip ->
        async {
          val destDir = File(scratchDir, zip.substringBeforeLast('.'))
          ZipUtil.extract(TestUtils.getWorkspaceFile("tools/adt/idea/studio/$zip"), destDir, null)
          destDir
        }
      }.awaitAll()

    // Walk the file tree and tally size
    val sizeResults = unzippedDirs
      .map { topLevelDir ->
        async {
          val platform = topLevelDir.name.substringAfterLast('.')
          var platformSize: Long = 0
          val basePath = if (platform == MAC) {
            topLevelDir.toPath().resolve("Android Studio.app").resolve("Contents")
          }
          else {
            topLevelDir.toPath().resolve("android-studio")
          }
          val plugins = mutableMapOf<String, Long>()
          var files: Long = 0
          Files.walkFileTree(topLevelDir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
              files++
              val relativePath = basePath.relativize(file)
              if (relativePath.startsWith("plugins")) {
                // The plugin's name is the path component right after plugins/
                val pluginName = relativePath.getName(1).toString()
                plugins[pluginName] = (plugins[pluginName] ?: 0) + attrs.size()
              }
              else {
                platformSize += attrs.size()
              }
              return FileVisitResult.CONTINUE
            }
          })
          StudioBundleSizeInfo(
            topLevelDir.name,
            platform,
            platformSize,
            files,
            plugins.map { (name, size) -> PluginSizeInfo(name, size) }
          )
        }
      }
      .awaitAll()

    // Send results
    sizeResults
      .map { result ->
        launch {
          logFileSize("${result.name}_platform", result.platformSize)
          logFileSize("${result.name}_files", result.files)
          result.pluginSizeInfos.forEach { pluginResult ->
            logFileSize("${pluginResult.name}_${result.platform}", pluginResult.size)
          }
        }
      }
  }

  private fun logFileSize(file: String, size: Long) {
    benchmark.log(
      file,
      size,
      // we don't expect this to deviate so tighten parameters to detect slightest regression.
      WindowDeviationAnalyzer.Builder()
        .setRunInfoQueryLimit(5)
        .setRecentWindowSize(1)
        .addMeanTolerance(
          WindowDeviationAnalyzer.MeanToleranceParams.Builder()
            .setMeanCoeff(0.01)
            .setStddevCoeff(0.0)
            .build())
        .build())
  }
}