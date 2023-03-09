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
package com.android.tools.idea.run.deployment.liveedit.desugaring

import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiledClass
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerOutput
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.r8.ClassFileResourceProvider
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Files
import java.nio.file.Paths
import com.intellij.openapi.module.Module;

internal class LiveEditDesugar : AutoCloseable{

  private val logger = DesugarLogger()
  private val jarResourceCacheManager = JarResourceCacheManager(logger)

  internal fun desugar(compiledFiles: LiveEditCompilerOutput) {
    val now = System.nanoTime()
    try {
      desugarClasses(compiledFiles.classes, compiledFiles.supportClasses)
    }
    catch (e: Exception) {
      e.printStackTrace()
    } finally {
      jarResourceCacheManager.done()
    }
    val durationMs = (System.nanoTime() - now) / 1000000
    logger.log("Runtime = $durationMs")
  }


  private fun getMinApiLevel(module: Module?) : Int {
    if (module == null) {
      logger.log("Cannot retrieve min API (no module)")
      return 0
    }

    val facet: AndroidFacet? = AndroidFacet.getInstance(module)
    if (facet == null) {
      logger.log("Cannot retrieve min API (no facet)")
      return 0
    }

    val minAPI = StudioAndroidModuleInfo.getInstance(facet).minSdkVersion.apiLevel

    logger.log("Target API = $minAPI")
    return minAPI
  }

  private fun getDesugarConfig(module: Module?): String? {
    if (module == null) {
      logger.log("Cannot retrieve desugar config (no module)")
      return null
    }

    val jsonConfigs = module.getModuleSystem().desugarLibraryConfigFiles
    if (jsonConfigs.isEmpty()) {
      logger.log("Not Library Config from Build System")
      return null
    }

    // R8 only requires a single json config file. Gradle returns a list if R8 even decides to return several.
    // We only get the first one.
    val path = jsonConfigs[0]
    val config = String(Files.readAllBytes(path))
    logger.log("Library Config = $path")
    return config
  }

  private fun getAndroidJar(module: Module?) : List<ClassFileResourceProvider> {
    if (module == null) {
      logger.log("Cannot retrieve android.jar (no module)")
      return emptyList()
    }

    val strings = module.project.getProjectSystem().getBootClasspath(module)

    logger.log("Android.jar = $strings")

    return strings.map{
      jarResourceCacheManager.getResourceCache(Paths.get(it))
    }
  }

  private fun getClassPathResourceProvider(module: Module?) : List<ClassFileResourceProvider> {
    if (module == null) {
      desugarFailure("Cannot retrieve classpath (no module)")
    }
    val classPath = module!!.project.getProjectSystem().getClassJarProvider().getModuleExternalLibraries(module).mapNotNull { it.toPath() }

    logger.log("Classpath = $classPath")

    // Go through classpath entries and build ClassFileResourceProvider for each jar encountered.
    return classPath.stream().map {
      jarResourceCacheManager.getResourceCache(it)
    }.toList()
  }

  private fun desugarClasses(classes : List<LiveEditCompiledClass>, supportClasses : List<LiveEditCompiledClass>) {

    // We batch class desugaring on a per-module basis to re-use common class desugaring configuration.
    // 1/ Flattened lists into a single one.
    // 2/ Group classes per-module name and desugar via R8
    // 3/ Write back desugared classes where they belong

    // 1
    logger.log("Request for:")
    val flattenedClasses = mutableMapOf<String, LiveEditCompiledClass>()
    flatten(classes, flattenedClasses, "classes")
    flatten(supportClasses, flattenedClasses, "support_classes")

    // 2
    val modulesSet = mutableMapOf<String, MutableList<LiveEditCompiledClass>>()
    flattenedClasses.forEach{
      if (it.value.module == null) {
        desugarFailure("Cannot process class '${it.value.name}' without module")
        return@forEach
      }
      val moduleName = it.value.module!!.name
      if (!modulesSet.contains(moduleName)) {
        modulesSet[moduleName] = ArrayList()
      }
      modulesSet[moduleName]!!.add(it.value)
    }

    // We store all desugared classes in there.
    val allDesugaredClasses = HashMap<String, ByteArray>()
    modulesSet.forEach{
      logger.log("Batch for module: ${it.key}")
      val module = it.value[0].module
      val desugaredModuleClasses = desugarClassesForModule(it.value, module)
      allDesugaredClasses.putAll(desugaredModuleClasses)
    }

    // 3
    replaceWithDesugared(allDesugaredClasses, flattenedClasses)
  }

  private fun flatten(classes: List<LiveEditCompiledClass>, flattenedClasses: MutableMap<String, LiveEditCompiledClass>, name: String) {
    logger.log(name)
    classes.forEach{
      logger.log(it.name)
      flattenedClasses[it.name] = it
    }
  }

  private fun desugarClassesForModule(classes : List<LiveEditCompiledClass>, module: Module?) : Map<String, ByteArray>{
      val memClassFileProvider = R8MemoryProgramResourceProvider(classes, logger)
      val memClassFileConsumer = R8MemoryClassFileConsumer(logger)

      val diagnosticHandler = R8DiagnosticHandler(logger)
      val command = D8Command.builder(diagnosticHandler)
        // Path of files to compile.
        .addProgramResourceProvider(memClassFileProvider)

        // Pass the min API.
        .setMinApiLevel(getMinApiLevel(module))

        // Set output to Cf in memory consumer
        .setProgramConsumer(memClassFileConsumer)

      // Pass android.jar of the target device (not the min-api)
      getAndroidJar(module).forEach {
        command.addLibraryResourceProvider(it)
      }

      // Pass the classpaths
      getClassPathResourceProvider(module).forEach{
        command.addClasspathResourceProvider(it)
      }

      /* If build.gradle enable library desugaring via,
         compileOptions {
           sourceCompatibility JavaVersion.VERSION_1_8
           targetCompatibility JavaVersion.VERSION_1_8
           coreLibraryDesugaringEnabled true
         }
         dependencies {
           coreLibraryDesugaring 'com.android.tools:desugar_jdk_libs:X.X.X'
         }

         then, we do get a json config file that we should forward to our own invocation.
      */
      // Enable desugared library if it was used.
      val desugarConfig = getDesugarConfig(module)
      if (desugarConfig != null) {
        command.addDesugaredLibraryConfiguration(desugarConfig)
      }

      // By default, D8 run on an executor with one thread per core
      D8.run(command.build())
      return memClassFileConsumer.classes
  }

  fun Map<String, Any>.toList() = this.map{it.key}.toList()

  private fun replaceWithDesugared(desugared: Map<String, ByteArray>, target: Map<String, LiveEditCompiledClass>) {
    target.forEach{
      if (!desugared.contains(it.key)) {
        desugarFailure("R8 did not desugar ${it.key}, desugared=${desugared.toList()}, target=${target.toList()}")
      }
      target[it.key]!!.data = desugared[it.key]!!
    }
  }

  override fun close() {
    jarResourceCacheManager.close();
  }

}
