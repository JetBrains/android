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
import com.android.tools.idea.run.deployment.liveedit.LiveEditCompiledClass
import com.android.tools.idea.run.deployment.liveedit.LiveEditLogger
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.buildLibraryDesugarFailure
import com.android.tools.idea.run.deployment.liveedit.tokens.ApplicationLiveEditServices
import com.android.tools.idea.run.deployment.liveedit.tokens.DesugarConfigs
import com.android.tools.r8.ClassFileResourceProvider
import com.android.tools.r8.CompilationFailedException
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.nio.file.Files

typealias ApiLevel = Int
typealias MinApiLevel = Int
typealias ClassName = String
typealias ByteCode = ByteArray

internal class LiveEditDesugar(private val applicationLiveEditServices: ApplicationLiveEditServices) : AutoCloseable{

  private val logger = LiveEditLogger("LE Desugar")
  private val jarResourceCacheManager = JarResourceCacheManager(logger)

  internal fun desugar(request: LiveEditDesugarRequest): LiveEditDesugarResponse {
    val now = System.nanoTime()
    val response = LiveEditDesugarResponse(request.compilerOutput)

    if (!StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_R8_DESUGAR.get()) {
      // If desugaring is disabled we pass-through the compiler output as desugaring output
      request.apiVersions.forEach{ apiVersion ->
        response.addOutputSet(apiVersion, request.compilerOutput.classes.map{ it.name to it.data }.toMap())
      }
      return response
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


  private fun getDesugarConfig(): String? {
    val desguarConfigs = applicationLiveEditServices.getDesugarConfigs()
    if (desguarConfigs is DesugarConfigs.Known) {
      if (desguarConfigs.configs.isEmpty()) {
        logger.log("Empty Desguar JSON Config from Build System")
        return null
      }

      // R8 only requires a single json config file. Gradle returns a list if R8 ever decides to require several.
      // Since multiple JSON isn't currently supported by sync, it is safe to assume we only have a single item array.
      val path = desguarConfigs.configs[0]
      val config = String(Files.readAllBytes(path))
      logger.log("Library Config = $path")
      return config
    } else {
      logger.log("Desugar Config Not Known")
      return null;
    }
  }

  private fun getAndroidJar(module: ApplicationLiveEditServices.CompilationDependencies) : List<ClassFileResourceProvider> {
    val paths = module.getBootClasspath()

    logger.log("Android.jar = $paths")

    return paths.map{
      jarResourceCacheManager.getResourceCache(it)
    }
  }

  private fun getClassPathResourceProvider(module: ApplicationLiveEditServices.CompilationDependencies) : List<ClassFileResourceProvider> {
    val classPath = module.getExternalLibraries()

    logger.log("Classpath = $classPath")

    // Go through classpath entries and build ClassFileResourceProvider for each jar encountered.
    return classPath.stream().map {
      jarResourceCacheManager.getResourceCache(it)
    }.toList()
  }

  private fun desugarClasses(classes : List<LiveEditCompiledClass>, version : MinApiLevel) : Map<ClassName, ByteCode> {

    // We batch class desugaring on a per-module basis to re-use common class desugaring configuration.
    // 1/ Group classes by the set of compilation dependencies and desugar via R8
    // 2/ Write back desugared classes where they belong

    // 1
    val modulesSet = mutableMapOf<ApplicationLiveEditServices.CompilationDependencies, MutableList<LiveEditCompiledClass>>()
    classes.forEach{
      if (it.compilationDependencies == null) {
        throw desugarFailure("Cannot process class '${it.name}' without module")
      }
      val compilationDependencies = it.compilationDependencies
      modulesSet.getOrPut(compilationDependencies) { mutableListOf() }.add(it)
    }

    // We store all desugared classes in there.
    val allDesugaredClasses = HashMap<ClassName, ByteCode>()
    // 2
    modulesSet.forEach{
      val moduleName = it.key
      val compiledClasses = it.value
      logger.log("Batch for module: $moduleName")
      val module = it.key
      allDesugaredClasses.putAll(desugarClassesForModule(compiledClasses, module, version))
    }

    return allDesugaredClasses
  }

  // Utility method to let R8 know that it should cancel its desugaring command
  private fun isCancelled() : Boolean {
    try {
      ProgressManager.checkCanceled()
    } catch (e: ProcessCanceledException) {
      return true
    }
    return false
  }

  private fun desugarClassesForModule(
    classes: List<LiveEditCompiledClass>,
    module: ApplicationLiveEditServices.CompilationDependencies,
    minApiLevel: MinApiLevel
  ): Map<String, ByteArray> {
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

        // Allow Studio to cancel desugaring
        .setCancelCompilationChecker { isCancelled() }

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

     // TODO: This is duplicated. We don't need to call this twice.
     // Check if the build system support returning the json config library information
     val desugarConfigs = applicationLiveEditServices.getDesugarConfigs()
     if (desugarConfigs is DesugarConfigs.NotKnown) {
       // If the application services does not support config retrieval, we cannot proceed
       throw buildLibraryDesugarFailure("${desugarConfigs.message}")
     }

     // Enable desugared library if it was used.
     val desugarConfig = getDesugarConfig()
     if (desugarConfig != null) {
       command.addDesugaredLibraryConfiguration(desugarConfig)
     }

     try {
       // By default, D8 run on an executor with one thread per core
       D8.run(command.build())
     } catch (e: CompilationFailedException) {
       // Check if we were cancelled. If we were, this method will throw ProcessCanceledException
       ProgressManager.checkCanceled()

       // We were not cancelled. This is an actual compilation error.
       throw desugarFailure("R8 compilation error", cause = e)
     }
     return memClassFileConsumer.classes
  }

  override fun close() {
    jarResourceCacheManager.close();
  }

}
