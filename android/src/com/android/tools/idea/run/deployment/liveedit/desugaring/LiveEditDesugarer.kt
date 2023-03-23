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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.LiveEditLogger
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiledClass
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.r8.ClassFileResourceProvider
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.intellij.openapi.module.Module
import java.nio.file.Files
import java.nio.file.Paths

typealias MinApiLevel = Int
typealias ClassName = String
typealias ByteCode = ByteArray

internal class LiveEditDesugar : AutoCloseable{

  private val logger = LiveEditLogger("LE Desugar")
  private val jarResourceCacheManager = JarResourceCacheManager(logger)

  internal fun desugar(request: LiveEditDesugarRequest): LiveEditDesugarResponse {
    val now = System.nanoTime()
    val response = LiveEditDesugarResponse(request.compilerOutput)

    if (StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR.get()) {
      // If desugaring is disabled we pass-through the compiler output as desugaring output
      request.apiVersions.forEach{ apiVersion ->
        response.addOutputSet(apiVersion, request.compilerOutput.classes.map{ it.name to it.data }.toMap())
      }
    }

    try {
      for (apiVersion in request.apiVersions) {
        val desugaredClasses = desugarClasses(request.compilerOutput.classes, apiVersion)
        response.addOutputSet(apiVersion, desugaredClasses)
      }
    } finally {
      jarResourceCacheManager.done()
    }
    val durationMs = (System.nanoTime() - now) / 1000000
    logger.log("Runtime = $durationMs")
    return response
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

  private fun desugarClasses(classes : List<LiveEditCompiledClass>, version : MinApiLevel) : Map<ClassName, ByteCode> {

    // We batch class desugaring on a per-module basis to re-use common class desugaring configuration.
    // 1/ Group classes per-module name and desugar via R8
    // 2/ Write back desugared classes where they belong

    // 1
    val modulesSet = mutableMapOf<String, MutableList<LiveEditCompiledClass>>()
    classes.forEach{
      if (it.module == null) {
        desugarFailure("Cannot process class '${it.name}' without module")
        return@forEach
      }
      val moduleName = it.module.name
      if (!modulesSet.contains(moduleName)) {
        modulesSet[moduleName] = ArrayList()
      }
      modulesSet[moduleName]!!.add(it)
    }

    // We store all desugared classes in there.
    val allDesugaredClasses = HashMap<ClassName, ByteCode>()
    // 2
    modulesSet.forEach{
      val moduleName = it.key
      val compiledClasses = it.value
      logger.log("Batch for module: $moduleName")
      val module = it.value[0].module
      if (module == null) {
        desugarFailure("Unable to desugar, no Module associated with $moduleName")
      }

      allDesugaredClasses.putAll(desugarClassesForModule(compiledClasses, module!!, version))
    }

    return allDesugaredClasses
  }

  private fun desugarClassesForModule(classes : List<LiveEditCompiledClass>, module: Module, minApiLevel: MinApiLevel) : Map<String, ByteArray>{
     val memClassFileProvider = R8MemoryProgramResourceProvider(classes, logger)
     val memClassFileConsumer = R8MemoryClassFileConsumer(logger)

     logger.log("minAPILevel =$minApiLevel")

     val diagnosticHandler = R8DiagnosticHandler(logger)
     val command = D8Command.builder(diagnosticHandler)
        // Path of files to compile.
        .addProgramResourceProvider(memClassFileProvider)

        // Pass the min API.
        .setMinApiLevel(minApiLevel)

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

     // Check if the build system support returning the json config library information
     val moduleSys = module.getModuleSystem()
     if (!moduleSys.desugarLibraryConfigFilesKnown) {
       // If AGP does not support config retrieval, we cannot proceed
       desugarFailure("${moduleSys.desugarLibraryConfigFilesNotKnownUserMessage}")
     }

     // Enable desugared library if it was used.
     val desugarConfig = getDesugarConfig(module)
     if (desugarConfig != null) {
       command.addDesugaredLibraryConfiguration(desugarConfig)
     }

     // By default, D8 run on an executor with one thread per core
     D8.run(command.build())
     return memClassFileConsumer.classes
  }

  override fun close() {
    jarResourceCacheManager.close();
  }

}
