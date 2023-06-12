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
package com.android.tools.idea.testing

import com.android.tools.idea.gradle.model.IdeCustomSourceDirectory
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.getAndroidFacets
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.utils.FileUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.systemIndependentPath
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.getManifestFiles
import java.io.File

@Suppress("DEPRECATION")
fun Project.dumpSourceProviders(): String {
  val projectRootPath = File(basePath)
  return buildString {
    var prefix = ""

    fun out(s: String) = appendln("$prefix$s")

    fun <T> nest(title: String? = null, code: () -> T): T {
      if (title != null) {
        out(title)
      }
      prefix = "    $prefix"
      val result = code()
      prefix = prefix.substring(4)
      return result
    }

    fun String.toPrintablePath(): String = this.replace(projectRootPath.absolutePath.toSystemIndependent(), ".", false)

    fun <T, F> T.dumpPathsCore(name: String, getter: (T) -> Iterable<F>, mapper: (F) -> String?) {
      val entries = getter(this).toList()
      if (entries.isEmpty()) return
      out("$name:")
      nest {
        entries
          .mapNotNull(mapper)
          .forEach {
            out(it.toPrintablePath())
          }
      }
    }

    fun IdeSourceProvider.dumpPaths(name: String, getter: (IdeSourceProvider) -> Collection<File>) =
      dumpPathsCore(name, getter) { it.path.toSystemIndependent() }

    fun IdeaSourceProvider.dumpUrls(name: String, getter: (IdeaSourceProvider) -> Iterable<String>) =
      dumpPathsCore(name, getter) { it }

    fun IdeaSourceProvider.dumpPaths(name: String, getter: (IdeaSourceProvider) -> Iterable<VirtualFile?>) =
      dumpPathsCore(name, getter) { it?.url }

    fun IdeCustomSourceDirectory.dump() {
      nest("CustomSourceDirectory") {
        out(sourceTypeName)
        out(directory.systemIndependentPath.toPrintablePath())
      }
    }

    fun IdeSourceProvider.dump() {
      out(name)
      nest {
        dumpPaths("Manifest") { listOf(manifestFile) }
        dumpPaths("AidlDirectories") { it.aidlDirectories }
        dumpPaths("AssetsDirectories") { it.assetsDirectories }
        dumpPaths("JavaDirectories") { it.javaDirectories }
        dumpPaths("KotlinDirectories") { it.kotlinDirectories }
        dumpPaths("JniLibsDirectories") { it.jniLibsDirectories }
        dumpPaths("RenderscriptDirectories") { it.renderscriptDirectories }
        dumpPaths("ResDirectories") { it.resDirectories }
        dumpPaths("ResourcesDirectories") { it.resourcesDirectories }
        dumpPaths("ShadersDirectories") { it.shadersDirectories }
        dumpPaths("MlModelsDirectories") { it.mlModelsDirectories }
        dumpPaths("BaselineProfileDirectories") { it.baselineProfileDirectories }
        customSourceDirectories.forEach { it.dump() }
      }
    }

    fun IdeaSourceProvider.dump(name: String) {
      out("${name} (IDEA)")
      nest {
        out("ScopeType: $scopeType")
        dumpUrls("ManifestFileUrls") { it.manifestFileUrls }
        dumpPaths("ManifestFiles") { it.manifestFiles }
        dumpUrls("ManifestDirectoryUrls") { it.manifestDirectoryUrls }
        dumpPaths("ManifestDirectories") { it.manifestDirectories }
        dumpUrls("AidlDirectoryUrls") { it.aidlDirectoryUrls }
        dumpPaths("AidlDirectories") { it.aidlDirectories }
        dumpUrls("AssetsDirectoryUrls") { it.assetsDirectoryUrls }
        dumpPaths("AssetsDirectories") { it.assetsDirectories }
        dumpUrls("JavaDirectoryUrls") { it.javaDirectoryUrls }
        dumpPaths("JavaDirectories") { it.javaDirectories }
        dumpUrls("KotlinDirectoryUrls") { it.kotlinDirectoryUrls }
        dumpPaths("KotlinDirectories") { it.kotlinDirectories }
        dumpUrls("JniLibsDirectoryUrls") { it.jniLibsDirectoryUrls }
        dumpPaths("JniLibsDirectories") { it.jniLibsDirectories }
        dumpUrls("RenderscriptDirectoryUrls") { it.renderscriptDirectoryUrls }
        dumpPaths("RenderscriptDirectories") { it.renderscriptDirectories }
        dumpUrls("ResDirectoryUrls") { it.resDirectoryUrls }
        dumpPaths("ResDirectories") { it.resDirectories }
        dumpUrls("ResourcesDirectoryUrls") { it.resourcesDirectoryUrls }
        dumpPaths("ResourcesDirectories") { it.resourcesDirectories }
        dumpUrls("ShadersDirectoryUrls") { it.shadersDirectoryUrls }
        dumpPaths("ShadersDirectories") { it.shadersDirectories }
        dumpUrls("MlModelsDirectoryUrls") { it.mlModelsDirectoryUrls }
        dumpPaths("MlModelsDirectories") { it.mlModelsDirectories }
      }
    }

    fun NamedIdeaSourceProvider.dump() {
      dump(name)
    }

    this@dumpSourceProviders.getAndroidFacets()
      .sortedBy { it.holderModule.name }
      .forEach { facet ->
        out("MODULE: ${facet.holderModule.name}")
        nest {
          nest("by Facet:") {
            val sourceProviderManager = SourceProviderManager.getInstance(facet)
            sourceProviderManager.mainIdeaSourceProvider.dump()
          }
          val model = GradleAndroidModel.get(facet)

          fun IdeSourceProvider.adjustedName() =
            if (name == "main") "_" else name

          fun NamedIdeaSourceProvider.adjustedName() =
            if (name == "main") "_" else name

          if (model != null) {
            nest("by AndroidModel:") {
              model.defaultSourceProvider.dump()
              nest("Active:") { model.activeSourceProviders.forEach { it.dump() } }
              nest("All:") { model.allSourceProviders.sortedBy { it.adjustedName() }.forEach { it.dump() } }
              nest("UnitTest:") { model.unitTestSourceProviders.forEach { it.dump() } }
              nest("AndroidTest:") { model.androidTestSourceProviders.forEach { it.dump() } }
              nest("TestFixtures:") { model.testFixturesSourceProviders.forEach { it.dump() } }
            }
          }
          nest("by IdeaSourceProviders:") {
            val sourceProviderManager = SourceProviderManager.getInstance(facet)
            dumpPathsCore("Manifests", { getManifestFiles(facet) }, { it.url })
            nest("Sources:") { sourceProviderManager.sources.dump("Sources") }
            nest("UnitTestSources:") { sourceProviderManager.unitTestSources.dump("UnitTestSources") }
            nest("AndroidTestSources:") { sourceProviderManager.androidTestSources.dump("AndroidTestSources") }
            nest("TestFixturesSources:") { sourceProviderManager.testFixturesSources.dump("TestFixturesSources") }
            nest("GeneratedSources:") { sourceProviderManager.generatedSources.dump("GeneratedSources") }
            nest("GeneratedUnitTestSources:") { sourceProviderManager.generatedUnitTestSources.dump("GeneratedUnitTestSources") }
            nest("GeneratedAndroidTestSources:") { sourceProviderManager.generatedAndroidTestSources.dump("GeneratedAndroidTestSources") }
            nest("GeneratedTestFixturesSources:") { sourceProviderManager.generatedTestFixturesSources.dump("GeneratedTestFixturesSources") }
            nest(
              "CurrentAndSomeFrequentlyUsedInactiveSourceProviders:"
            ) {
              sourceProviderManager.currentAndSomeFrequentlyUsedInactiveSourceProviders.sortedBy { it.adjustedName() }
                .forEach { it.dump() }
            }
            nest("CurrentSourceProviders:") { sourceProviderManager.currentSourceProviders.forEach { it.dump() } }
            nest("CurrentUnitTestSourceProviders:") { sourceProviderManager.currentUnitTestSourceProviders.forEach { it.dump() } }
            nest("CurrentAndroidTestSourceProviders:") { sourceProviderManager.currentAndroidTestSourceProviders.forEach { it.dump() } }
            nest("CurrentTestFixturesSourceProviders:") { sourceProviderManager.currentTestFixturesSourceProviders.forEach { it.dump() } }
          }
        }
      }
    val buildConfigurationFiles = getProjectSystem().getBuildConfigurationSourceProvider()?.getBuildConfigurationFiles().orEmpty()
    if (buildConfigurationFiles.isNotEmpty()) {
      nest("Build configuration files:") {
        buildConfigurationFiles.sortedBy { it.groupOrder }.forEach {
          out("${it.file.url.toPrintablePath()} : ${it.displayName} [${it.groupOrder}]")
        }
      }
    }
  }
    .trimIndent()
}

private fun String.toSystemIndependent() = FileUtils.toSystemIndependentPath(this)
