/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.rendering

/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.ide.common.util.PathString
import com.android.tools.idea.projectsystem.PROJECT_SYSTEM_SYNC_TOPIC
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.gradle.GradleClassFileFinder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.buildMainArtifactStub
import com.android.tools.perflogger.Benchmark
import com.android.tools.perflogger.Metric
import com.google.common.base.Stopwatch
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.io.createDirectory
import com.intellij.testFramework.utils.io.createFile
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.util.io.createDirectories
import com.intellij.util.ui.UIUtil
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.random.Random

private val NUMBER_OF_SAMPLES = 40

/**
 * Class with utilities to generate content for a compile root. This class will generate a number of unique classes and unique R.jar files
 * that can be used to test the [GradleClassFileFinder] lookup methods. The class files are not valid class files but this is ok since the
 * [GradleClassFileFinder] only locates them and does not instantiate them.
 */
private class ContentGenerator(
  /** Number of packages containing classes */
  val numberOfContentPackages: Int = 30,
  /** Number of classes in every package */
  val numberOfClassesPerPackage: Int = 40,
  /** Number of R.jar files */
  val numberOfRClassFiles: Int = 50,
  /** Number of different packages containing R class definitions in every R.jar */
  val numberOfRPackagesPerFile: Int = 5
) {
  private val compileRoot: Path = Files.createTempDirectory("compileRoot")
  val generatedClasses = mutableListOf<String>()

  private fun generateRandomPackagePrefix(): String =
    "package/".repeat(Random.Default.nextInt(0, 3))

  /**
   * Generates random content in the given file [path] of [size].
   */
  private fun generateFile(path: Path, size: Int) {
    path.parent.createDirectories()
    val classContents = ByteArray(size)
    Random.Default.nextBytes(classContents)
    Files.write(path, classContents)
  }

  private fun generateOutputRoot(compileRoot: Path): Path =
    compileRoot.createDirectory("classes").also { outDirectory ->
      repeat(numberOfContentPackages) { packageId ->
        val pkgName = "com/test/p$packageId/${generateRandomPackagePrefix()}"
        repeat(numberOfClassesPerPackage) { classId ->
          val classPath = "${pkgName}A${classId}.class"
          generateFile(outDirectory.createFile(classPath), Random.Default.nextInt(10, 5000))
          generatedClasses.add(classPath.removeSuffix(".class").replace("/", "."))
        }
      }
    }

  private fun createRJarFile(outputJar: Path, resourceRoot: String) {
    assertTrue(FileSystemProvider.installedProviders().any { it.scheme == "jar" })

    outputJar.parent.createDirectories()
    val outputJarPath = "/${PathString(outputJar).portablePath.removePrefix("/")}"
    FileSystems.newFileSystem(URI.create("jar:file:$outputJarPath"), mapOf("create" to "true"))
      .use { fileSystem ->
        repeat(numberOfRPackagesPerFile) {
          val basePath = "$resourceRoot/p$it"
          listOf(
              "$basePath/R.class",
              "$basePath/R\$style.class",
              "$basePath/R\$string.class",
              "$basePath/R\$color.class",
              "$basePath/R\$id.class",
              "$basePath/R\$value.class",
              "$basePath/R\$xml.class",
            )
            .forEach {
              generateFile(fileSystem.getPath(it), Random.Default.nextInt(600, 1500))
              generatedClasses.add(it.removeSuffix(".class").replace("/", "."))
            }
        }
      }
  }

  private fun generateRFiles(compileRoot: Path): List<File> =
    (0 until numberOfRClassFiles)
      .map {
        val baseResourcePath = "com/test/resource/p$it"
        val rJar = compileRoot.resolve("$baseResourcePath/R.jar")
        createRJarFile(rJar, baseResourcePath)
        rJar.toFile()
      }
      .toList()

  private fun generateAllCompileRoots(compileRoot: Path): List<File> =
    listOf(generateOutputRoot(compileRoot).toFile()) + generateRFiles(compileRoot)

  val outputRoots = generateAllCompileRoots(compileRoot)

  fun delete() {
    compileRoot.deleteRecursively()
  }
}

class PerfgateGradleClassFileFinderTest {
  companion object {
    val benchmark =
      Benchmark.Builder("DesignTools GradleClassFinder Manager Benchmark")
        .setProject("Design Tools")
        .setDescription("Base line for Jar file access (mean) after $NUMBER_OF_SAMPLES samples.")
        .build()
  }

  private val content = ContentGenerator()

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModel(
      AndroidProjectBuilder().withMainArtifactStub {
        buildMainArtifactStub("debug").copy(classesFolder = content.outputRoots)
      }
    )

  @Before
  fun setup() {
    // This is so the IdempotenceChecker does not recalculate cached values on every iteration
    ApplicationManagerEx.setInStressTest(true)
  }

  @After
  fun tearDown() {
    content.delete()
  }

  @Test
  @Throws(Exception::class)
  fun testWithCachedRoots() {
    val samples: MutableList<Metric.MetricSample> = ArrayList(NUMBER_OF_SAMPLES)

    // We use the generated classes 4 times and then shuffle them to ensure only taking 1 size worth of classes
    // to ensure that cache is can also be hit sometimes.
    val classesToQuery = (content.generatedClasses + content.generatedClasses + content.generatedClasses + content.generatedClasses)
      .shuffled()
      .take(content.generatedClasses.size)

    repeat(NUMBER_OF_SAMPLES) {
      val stopWatch = Stopwatch.createStarted()
      val gradleClassFinder = GradleClassFileFinder.createWithoutTests(projectRule.module)
      classesToQuery.forEach {
        assertNotNull(gradleClassFinder.findClassFile(it))
      }
      samples.add(Metric.MetricSample(System.currentTimeMillis(), stopWatch.elapsed().toMillis()))
    }
    Metric("gradle_class_finder_cached_roots_time").apply {
      addSamples(benchmark, *samples.toTypedArray())
      commit()
    }
  }

  @Test
  @Throws(Exception::class)
  fun testInvalidatingRoots() {
    val samples: MutableList<Metric.MetricSample> = ArrayList(NUMBER_OF_SAMPLES)

    // We use the generated classes 4 times and then shuffle them to ensure only taking 1 size worth of classes
    // to ensure that cache is can also be hit sometimes.
    val classesToQuery = (content.generatedClasses + content.generatedClasses + content.generatedClasses + content.generatedClasses)
      .shuffled()
      .take(content.generatedClasses.size)

    repeat(NUMBER_OF_SAMPLES) {
      // Invalidate roots to ensure they get recalculated in every iteration
      projectRule.project.messageBus.syncPublisher(PROJECT_SYSTEM_SYNC_TOPIC).syncEnded(ProjectSystemSyncManager.SyncResult.SUCCESS)
      runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
      val stopWatch = Stopwatch.createStarted()
      val gradleClassFinder = GradleClassFileFinder.createWithoutTests(projectRule.module)
      classesToQuery.forEach {
        assertNotNull(gradleClassFinder.findClassFile(it))
      }
      samples.add(Metric.MetricSample(System.currentTimeMillis(), stopWatch.elapsed().toMillis()))
    }
    Metric("gradle_class_finder_invalidated_roots_time").apply {
      addSamples(benchmark, *samples.toTypedArray())
      commit()
    }
  }
}
