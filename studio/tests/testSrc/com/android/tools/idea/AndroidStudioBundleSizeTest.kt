package com.android.tools.idea


import com.android.test.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.WindowDeviationAnalyzer
//import com.google.common.io.Files
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * This test tracks the file size information of Android Studio bundle in a perfgate benchmark with the following metrics.
 *
 * $platform.files                  - total number of files
 * $platform.total[.zip]            - total uncompressed [ compressed ] size
 * $platform.plugin[.zip].$name     - uncompressed [ compressed ] size per android plugin
 * $platform.plugin_pt[.zip].$name  - uncompressed [ compressed ] size per platform plugin
 * $platform.platform[.zip]         - total uncompressed [ compressed ] size
 *
 * Platform is one of linux, mac, or win.
 */
class AndroidStudioBundleSizeTest {

  data class Size(var size:Long, var compressedSize:Long) {
    operator fun plusAssign(other:Size) { size += other.size; compressedSize += other.compressedSize }
  }


  @Test
  fun testLoggingBinarySize(): Unit = runBlocking {
    // we don't expect this to deviate so tighten parameters to detect slightest regression.
    val benchmark: Benchmark = Benchmark.Builder("Android Studio Binary Size").build()
    val analyzer = WindowDeviationAnalyzer.Builder()
      .setRunInfoQueryLimit(5)
      .setRecentWindowSize(1)
      .addMeanTolerance(
        WindowDeviationAnalyzer.MeanToleranceParams.Builder()
          .setMeanCoeff(0.01)
          .setStddevCoeff(0.0)
          .build())
      .build()

    val plugins = Files.readAllLines(resolveWorkspacePath("tools/adt/idea/studio/android-studio.plugin.lst")).toSet();
    val pluginRegex = Regex("(android-studio/plugins/|Android Studio.*\\.app/Contents/plugins/)([^/]+)/.*")
    val platforms = listOf("win", "mac", "linux")

    platforms.map { platform -> async {
      val zipFile = resolveWorkspacePath("tools/adt/idea/studio/android-studio.$platform.zip").toFile()
      var files: Long = 0
      val totalSize = Size(0, 0)
      val platformSize = Size(0, 0)
      val pluginSize = mutableMapOf<String, Size>()


      ZipFile(zipFile).stream().forEach { entry ->
        val entrySize = Size(entry.size, entry.compressedSize)
        totalSize += entrySize
        files++
        val match = pluginRegex.matchEntire(entry.name)
        if (match != null) {
          val (_, plugin) = match.destructured
          pluginSize.getOrPut(plugin, { Size(0, 0) }) += entrySize
        } else {
          platformSize += entrySize
        }
      }

      benchmark.log("$platform.files", files, analyzer)
      benchmark.log("$platform.total.u", totalSize.size, analyzer)
      benchmark.log("$platform.total.c", totalSize.compressedSize, analyzer)
      benchmark.log("$platform.platform.u", platformSize.size, analyzer)
      benchmark.log("$platform.platform.c", platformSize.compressedSize, analyzer)
      for ((plugin, size) in pluginSize) {
        val type = if (plugins.contains(plugin)) "plugin" else "plugin_pt"
        benchmark.log("$platform.$type.u.$plugin", size.size, analyzer)
        benchmark.log("$platform.$type.c.$plugin", size.compressedSize, analyzer)
      }
    }}
  }
}