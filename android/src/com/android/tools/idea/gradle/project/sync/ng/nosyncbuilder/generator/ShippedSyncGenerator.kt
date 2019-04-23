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
package com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.generator

import com.android.SdkConstants.DOT_GRADLE
import com.android.SdkConstants.FN_BUILD_GRADLE
import com.google.common.annotations.VisibleForTesting
import com.android.builder.model.level2.GlobalLibraryMap
import com.android.ide.common.repository.DEFAULT_GMAVEN_URL
import com.android.ide.common.repository.GMAVEN_BASE_URL
import com.android.tools.idea.gradle.project.sync.ng.SyncAction
import com.android.tools.idea.gradle.project.sync.ng.SyncActionOptions
import com.android.tools.idea.gradle.project.sync.ng.SyncModuleModels
import com.android.tools.idea.gradle.project.sync.ng.SyncProjectModels
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.AndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.interfaces.library.JavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.misc.*
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.androidproject.NewAndroidProject
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewAndroidLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewGlobalLibraryMap
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.library.NewJavaLibrary
import com.android.tools.idea.gradle.project.sync.ng.nosyncbuilder.newfacade.variant.NewVariant
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils
import org.apache.commons.io.FileUtils
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

const val ONLY_GIVEN_REPOS_FLAG = "--onlygivenrepos"

fun main(args: Array<String>) {
  val flags = args.filter { it.startsWith("--") }
  val paths = args.filter { !it.startsWith("--") }

  val flag = flags.firstOrNull()

  if ((flag != null && flag != ONLY_GIVEN_REPOS_FLAG) || paths.size < 3 || flags.size > 1) {
    println("Usage: ./ShippedSyncGenerator <input project> <SDK root> <output directory> [<offline repo path 1> <offline repo path 2>...]")
    println("Flags:")
    println("$ONLY_GIVEN_REPOS_FLAG use exclusively given repos for dependency resolution (ignore repos in gradle.build)")
    return
  }
  val (projectRoot, sdkRoot, outRoot) = paths.take(3).map(::File)
  val repoPaths = paths.drop(3)

  val useOnlyGivenRepos = flag == ONLY_GIVEN_REPOS_FLAG

  ShippedSyncGenerator(projectRoot, sdkRoot, repoPaths, useOnlyGivenRepos).use {
    val offlineRepoPath = outRoot.resolve(OFFLINE_REPO_PATH).toPath()
    val bundlePath = outRoot.resolve(BUNDLE_PATH).toPath()
    it.generateModels(outRoot.toPath())
    it.generateOfflineRepo(offlineRepoPath, bundlePath)
  }
}

class ShippedSyncGenerator(private val projectRoot: File,
                           private val sdkRoot: File,
                           repoPaths: List<String>,
                           private val useOnlyGivenRepos: Boolean) : Closeable {
  private lateinit var myProjectModels: SyncProjectModels
  private val glmBuilder = GlobalLibraryMapBuilder()
  private lateinit var myOfflineRepoInitScriptPath: Path
  private lateinit var myOriginalGradleBuildTempFile: File
  private val connection: ProjectConnection
  private val offlineRepoInitScriptContent: String?
  private var isInitialized: Boolean = false
    set(value) {
      if (field && !value) {
        throw IllegalArgumentException("It's impossible to uninitialize")
      }
      field = true
    }

  init {
    val fullRepoPaths = repoPaths.toMutableList()

    connection = GradleConnector.newConnector()
      .forProjectDirectory(projectRoot)
      .connect()

    if (useOnlyGivenRepos) {
      removeReposInGradleBuild()
    } else if (DEFAULT_GMAVEN_URL != GMAVEN_BASE_URL) {
      fullRepoPaths.add(GMAVEN_BASE_URL)
    }

    offlineRepoInitScriptContent = createLocalMavenRepoInitScriptContent(fullRepoPaths)

    if (offlineRepoInitScriptContent != null) {
      myOfflineRepoInitScriptPath = createTempFile(DOT_GRADLE).toPath()
      Files.write(myOfflineRepoInitScriptPath, offlineRepoInitScriptContent.toByteArray())
    }
  }

  override fun close() {
    fun restoreOriginalGradleBuild() {
      val originalGradleBuildFile = File(projectRoot, FN_BUILD_GRADLE)
      myOriginalGradleBuildTempFile.copyTo(originalGradleBuildFile, true)
    }

    if (::myOfflineRepoInitScriptPath.isInitialized) {
      Files.delete(myOfflineRepoInitScriptPath)
    }
    if (useOnlyGivenRepos) {
      restoreOriginalGradleBuild()
    }
    connection.close()
  }

  private fun removeReposInGradleBuild() {
    val originalGradleBuildFile = File(projectRoot, FN_BUILD_GRADLE)
    myOriginalGradleBuildTempFile = createTempFile(DOT_GRADLE)
    originalGradleBuildFile.copyTo(myOriginalGradleBuildTempFile, true)

    val gradleBuildContent = originalGradleBuildFile.readText()
    val newGradleBuildContent = clearRepositories(gradleBuildContent)
    Files.write(originalGradleBuildFile.toPath(), newGradleBuildContent.toByteArray())
  }

  private fun initialize() {
    val args = listOf(
      "-Djava.awt.headless=true",
      "-Pandroid.injected.build.model.only=true",
      "-Pandroid.injected.build.model.only.advanced=true",
      "-Pandroid.injected.invoked.from.ide=true",
      "-Pandroid.injected.build.model.only.versioned=3",
      "-Pandroid.injected.studio.version=3.3.0.3",
      "-Pandroid.injected.invoked.from.ide=true"
    ) + if (offlineRepoInitScriptContent != null) listOf("-I", "$myOfflineRepoInitScriptPath") else listOf()

    AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot)

    val syncAction = SyncAction(setOf(), setOf(), SyncActionOptions())
    val executor = connection.action(syncAction)
    myProjectModels = executor
      .withArguments(args)
      .run()

    isInitialized = true
  }

  // Decorator pattern
  private fun withInitialization(f: ()->Unit) {
    if (!isInitialized) {
      initialize()
    }
    f()
  }

  fun generateModels(path: Path) = withInitialization {
    val projectModel = getProjectModel()

    val projectSyncPath = path.resolve(projectModel.moduleName)
    Files.createDirectories(projectSyncPath)

    val rootConverter = PathConverter(projectRoot, File("/"), File("/"), File("/"))
    cacheProjectModel(projectModel, rootConverter, projectSyncPath)

    for (i in 1 until myProjectModels.moduleModels.size) {
      val moduleModel = myProjectModels.moduleModels[i]
      val converter = PathConverter(File(projectRoot, moduleModel.moduleName), sdkRoot, File("/"), File("/"))
      cacheModuleModel(moduleModel, converter, projectSyncPath)
    }

    // Contents of the global library map was filled while running [cacheModuleModel]
    cacheGlobalLibraryMap(rootConverter, projectSyncPath)
  }

  // If [bundlePath] is null then exploded AARs are not going to be generated
  fun generateOfflineRepo(offlineRepoPath: Path, bundlePath: Path?) = withInitialization {
    fun copyArtifact(path: Path, artifact: File, artifactAddress: String) {
      val newArtifactFolder = path.resolve(artifactAddressToRelativePath(artifactAddress))
      Files.createDirectories(newArtifactFolder)
      FileUtils.copyDirectory(artifact.parentFile, newArtifactFolder.toFile())
    }

    fun AndroidLibrary.copyArtifactTo(path: Path) = copyArtifact(path, artifact, artifactAddress!!)
    fun JavaLibrary.copyArtifactTo(path: Path) = copyArtifact(path, artifact, artifactAddress!!)

    fun AndroidLibrary.copyExplodedAarsTo(path: Path) {
      val relativePath = artifactAddressToRelativePath(artifactAddress!!)
      val newBundleFolder = path.resolve(relativePath)
      FileUtils.copyDirectory(bundleFolder, newBundleFolder.toFile())
    }

    for (i in 1 until myProjectModels.moduleModels.size) {
      val model = myProjectModels.moduleModels[i]
      val androidProject = model.findModel(OldAndroidProject::class.java)!!
      // Fill Global Library Map
      androidProject.variants.forEach { glmBuilder.addLibrariesFromVariantToGLM(it) }
    }

    val newGlobalLibraryMap = NewGlobalLibraryMap(GlobalLibraryMap { glmBuilder.build() })

    for (library in newGlobalLibraryMap.libraries) {
      when (library.value) {
        is NewAndroidLibrary -> {
          val androidLibrary = (library.value as AndroidLibrary)
          androidLibrary.copyArtifactTo(offlineRepoPath)
          bundlePath?.run { androidLibrary.copyExplodedAarsTo(this) }
        }
        is NewJavaLibrary -> (library.value as NewJavaLibrary).copyArtifactTo(offlineRepoPath)
      }
    }
  }

  private fun cacheProjectModel(model: SyncModuleModels, converter: PathConverter, syncPath: Path) {
    val gradleProject = model.findModel(GradleProject::class.java)

    val gradleRootProjectPath = syncPath.resolve(GRADLE_PROJECT_CACHE_PATH)
    val gradleRootProjectJSON = gradleProject!!.toJson(converter)
    Files.write(gradleRootProjectPath, gradleRootProjectJSON.toByteArray())
  }

  // TODO(qumeric): add java project support
  private fun cacheModuleModel(model: SyncModuleModels, converter: PathConverter, syncPath: Path) {
    val androidProject = model.findModel(OldAndroidProject::class.java)!!
    val gradleProject = model.findModel(GradleProject::class.java)

    val modulePath = syncPath.resolve(model.moduleName)
    Files.createDirectories(modulePath)
    val androidProjectPath = modulePath.resolve(ANDROID_PROJECT_CACHE_PATH)
    val projectDir = converter.knownDirs[PathConverter.DirType.MODULE]!!
    val newAndroidProject = NewAndroidProject(androidProject, projectDir.toFile())
    val androidProjectJSON = newAndroidProject.toJson(converter)
    Files.write(androidProjectPath, androidProjectJSON.toByteArray())

    val variantsDir = modulePath.resolve(VARIANTS_CACHE_DIR_PATH)
    Files.createDirectories(variantsDir)

    for (oldVariant in androidProject.variants) {
      val variantPath = variantsDir.resolve(oldVariant.name + ".json")
      val variant = NewVariant(oldVariant, androidProject)
      val variantJSON = variant.toJson(converter)
      Files.write(variantPath, variantJSON.toByteArray())

      glmBuilder.addLibrariesFromVariantToGLM(oldVariant)
    }

    val gradleModuleProjectPath = modulePath.resolve(GRADLE_PROJECT_CACHE_PATH)
    val gradleModuleProjectJSON = gradleProject!!.toJson(converter)
    Files.write(gradleModuleProjectPath, gradleModuleProjectJSON.toByteArray())
  }

  private fun cacheGlobalLibraryMap(converter: PathConverter, syncPath: Path) {
    val globalLibraryMapPath = syncPath.resolve(GLOBAL_LIBRARY_MAP_CACHE_PATH)

    val newGlobalLibraryMap = NewGlobalLibraryMap(GlobalLibraryMap { glmBuilder.build() })
    val globalLibraryMapJSON = newGlobalLibraryMap.toJson(converter)
    Files.write(globalLibraryMapPath, globalLibraryMapJSON.toByteArray())
  }

  // projectModel is always first but we do not rely on the order
  private fun getProjectModel(): SyncModuleModels {
    for (moduleModel in myProjectModels.moduleModels) {
      val gradleProject = moduleModel.findModel(GradleProject::class.java) ?:
                          throw NoSuchElementException("Found a model without GradleProject")
      if (gradleProject.path == ":") {
        return moduleModel
      }
    }
    throw NoSuchElementException("Could not found the root module")
  }
}

fun createLocalMavenRepoInitScriptContent(repoPaths: List<String>): String? {
  if (repoPaths.isEmpty()) {
    return null
  }

  val paths = repoPaths.joinToString("\n") { "    maven { url '${escapeGroovyStringLiteral(it)}' }" }

  return "allprojects {\n" +
         "  buildscript {\n" +
         "    repositories {\n" + paths + "\n" +
         "    }\n" +
         "  }\n" +
         "  repositories {\n" + paths + "\n" +
         "  }\n" +
         "}\n"
}

// We can't use GradleImport.escapeGroovyStringLiteral because GradleImport uses ServiceManager in static initialization
private fun escapeGroovyStringLiteral(s: String) = s.map { if (it == '\\' || it == '\'') "\\$it" else "$it"}.joinToString("")

@VisibleForTesting
@Throws(java.lang.IllegalArgumentException::class)
fun clearRepositories(gradleBuildContent: String): String {
  // Returns matching bracket position for the first opening bracket, beginning at the specified [startIndex].
  // Returns -1 if there is no such bracket or the bracket sequence is invalid (e.g "repository }{ ...").
  fun findMatchingClosingBracket(content: String, startIndex: Int): Int {
    val stack = Stack<Int>()
    for (i in startIndex until content.length) {
      val c = content[i]
      if (c == '{') {
        stack.push(i)
      } else if (c == '}') {
        if (stack.empty()) {
          break
        } else if (stack.size > 1){
          stack.pop()
        } else {
          return i
        }
      }
    }
    return -1
  }

  fun IntRange.length() = endInclusive - start + 1

  // [ranges] must be sorted
  fun removeSubstringInEachRange(content: String, ranges: Sequence<IntRange>): String {
    var newContent: String = content
    var removedSymbols = 0
    for (range in ranges) {
      newContent = newContent.replaceRange(range.start-removedSymbols, range.endInclusive-removedSymbols+1, "")
      removedSymbols += range.length()
    }
    return newContent
  }

  val regex = Regex("""repositories\s*\{""")

  val occurrences = regex.findAll(gradleBuildContent)
  val repositoriesContentRanges = occurrences.map {
    val matchingClosingBracket = findMatchingClosingBracket(gradleBuildContent, it.range.endInclusive)
    if (matchingClosingBracket == -1) {
      throw IllegalArgumentException("build.gradle is invalid (check brackets)")
    }
    IntRange(it.range.endInclusive+1, matchingClosingBracket-1)
  }

  return removeSubstringInEachRange(gradleBuildContent, repositoriesContentRanges)
}
