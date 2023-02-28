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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.run.deployment.liveedit.desugaring.R8DiagnosticHandler
import com.android.tools.idea.run.deployment.liveedit.desugaring.R8MemoryClassFileConsumer
import com.android.tools.idea.run.deployment.liveedit.desugaring.R8MemoryProgramResourceProvider
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import org.jetbrains.android.facet.AndroidFacet
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private fun log(message: String) {
  if (!LiveEditAdvancedConfiguration.getInstance().useDebugMode) {
    return
  }
  println("LE Desugar: " + message)
}

private fun getMinApiLevel(module: com.intellij.openapi.module.Module?) : Int {
  if (module == null) {
    log("Cannot retrieve min API (no module)")
    return 0
  }

  val facet: AndroidFacet? = AndroidFacet.getInstance(module)
  if (facet == null) {
    log("Cannot retrieve min API (no facet)")
    return 0
  }

  val minAPI = StudioAndroidModuleInfo.getInstance(facet).minSdkVersion.apiLevel

  log("Target API = $minAPI")
  return minAPI
}


private fun getClassPath(module: com.intellij.openapi.module.Module?) : List<Path> {
  if (module == null) {
    log("Cannot retrieve classpath (no module)")
    return emptyList()
  }
  val classPath = module.project.getProjectSystem().getClassJarProvider().getModuleExternalLibraries(module).mapNotNull { it.toPath() }

  log("Classpath = $classPath")
  return classPath
}

private fun getAndroidJar(module: com.intellij.openapi.module.Module?) : Collection<Path> {
  if (module == null) {
    log("Cannot retrieve android.jar (no module)")
    return emptyList()
  }

  val paths = mutableListOf<Path>()
  val strings = module.project.getProjectSystem().getBootClasspath(module)
  strings.forEach{
      paths.add(Paths.get(it))
  }

  log("Android.jar = $paths")
  return paths
}

private fun getDesugarConfig(module: com.intellij.openapi.module.Module?): String? {
  if (module == null) {
    log("Cannot retrieve desugar config (no module)")
    return null
  }

  val jsonConfigs = module.getModuleSystem().desugarLibraryConfigFiles
  if (jsonConfigs.isEmpty()) {
    log("Not Library Config from Build System")
    return null
  }

  // R8 only requires a single json config file. Gradle returns a list if R8 even decides to return several.
  // We only get the first one.
  val path = jsonConfigs[0]
  val config = String(Files.readAllBytes(path))
  log("Library Config = $path")
  return config
}



// Desugar classes in place
private fun desugarClasses(classes : List<LiveEditCompiledClass>) {
  classes.forEach{
    val memClassFileProvider = R8MemoryProgramResourceProvider(it.data)
    val memClassFileConsumer = R8MemoryClassFileConsumer()

    // Don't use a diagnosis handler for now?
    val diagnosticHandler = R8DiagnosticHandler()
    val command = D8Command.builder(diagnosticHandler)
      // Var args Path of files to compile.
      .addProgramResourceProvider(memClassFileProvider)

      // The minimum would be to pass classes up in the hierarchy and the rest of the nest.
      .addClasspathFiles(getClassPath(it.module))

      // Pass android.jar of the target device (not the min-api)
      .addLibraryFiles(getAndroidJar(it.module))

      // Pass the min API.
      .setMinApiLevel(getMinApiLevel(it.module))

      // Set output to Cf in memory consumer
      .setProgramConsumer(memClassFileConsumer)

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
    val desugarConfig = getDesugarConfig(it.module)
    if (desugarConfig != null) {
      command.addDesugaredLibraryConfiguration(desugarConfig)
    }

    // Run on current thread (no executorService)
    D8.run(command.build())
    if (diagnosticHandler.hasError) {
      diagnosticHandler.diagnosticError?.let {
        LiveEditUpdateException.desugarFailure(it.diagnosticMessage)
      }
    }

    it.data = memClassFileConsumer.data
  }
}

class LiveEditDesugar() {
  fun desugar(compiledFiles: LiveEditCompilerOutput) {
    val now = System.nanoTime()
    try {
      desugarClasses(compiledFiles.classes)
      desugarClasses(compiledFiles.supportClasses)

      val durationMs = (System.nanoTime() - now) / 1000000
      log("Runtime = $durationMs")
    }
    catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
