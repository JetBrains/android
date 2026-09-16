/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.analysis

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createTempDirectory
import kotlin.io.path.name
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.name.NameUtils

@Service(Service.Level.PROJECT)
class AnalysisScriptService(private val project: Project, private val scope: CoroutineScope) {
  val tempDir by lazy { FileUtilRt.createTempDirectory("run-analysis-script", null, true) }

  fun runAndOutputAnalysisScript(file: VirtualFile): Job {
    return scope.launch {
      val result: String? = withContext(Dispatchers.IO) { runAnalysisScript(file) }
      // TODO: catch exceptions and output as String?

      withContext(Dispatchers.EDT) {
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        val descriptor = RunContentDescriptor(consoleView, null, consoleView.component, "Script Output")
        RunContentManager.getInstance(project).showRunContent(DefaultRunExecutor.getRunExecutorInstance(), descriptor)
        if (result != null) {
          consoleView.print(result, ConsoleViewContentType.NORMAL_OUTPUT)
        } else {
          consoleView.print("null", ConsoleViewContentType.ERROR_OUTPUT)
        }
      }
    }
  }

  private suspend fun runAnalysisScript(file: VirtualFile): String? {
    if (!file.isInLocalFileSystem) throw IllegalArgumentException("Analysis script file must be in local file system: $file")
    if (!file.name.endsWith(ANALYSIS_SCRIPT_EXTENSION))
      throw IllegalArgumentException("Analysis script file must have $ANALYSIS_SCRIPT_EXTENSION extension: $file")

    return withBackgroundProgress(project, "Executing analysis script...", cancellable = true) {
      val javaHome = System.getProperty("java.home") ?: throw RuntimeException("Could not get java.home directory of IDE")
      val pluginClassloader = AnalysisScriptService::class.java.classLoader
      val pluginClasspath = classpathFromClassloader(pluginClassloader) ?: throw RuntimeException("Could not get IDE classpath")
      createTempDirectory(tempDir.toPath(), "run").useDirectory { temp ->
        val argsFile = temp.resolve("args.txt")
        val outDir = temp.resolve("out")

        argsFile.bufferedWriter().use { writer ->
          writer.write(
            "-Xuse-fir-lt=false " +
              "-Xallow-any-scripts-in-source-roots " +
              "-P plugin:kotlin.scripting:script-templates=${AnalysisScript::class.qualifiedName} " +
              "-P plugin:kotlin.scripting:disable-script-definitions-autoloading=true " +
              "-P plugin:kotlin.scripting:disable-standard-script=true " +
              "-d ${outDir.name} " +
              "${file.path} " +
              "-cp "
          )
          pluginClasspath.joinTo(writer, separator = File.pathSeparator)
        }
        val commandLine =
          GeneralCommandLine(KotlinArtifacts.kotlinc.absolutePath, "@${argsFile.name}")
            .withWorkingDirectory(temp)
            .withEnvironment("JAVA_HOME", javaHome)

        val processResult = ExecUtil.execAndGetOutput(commandLine)
        if (!processResult.checkSuccess(LOGGER)) return@withBackgroundProgress null

        // Load and run.
        val classLoader = URLClassLoader(arrayOf(outDir.toUri().toURL()), pluginClassloader)
        val className = NameUtils.getScriptNameForFile(file.name).toString()
        val clazz = classLoader.loadClass(className)
        val constructor = clazz.getConstructor(Project::class.java)
        val saveClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = clazz.javaClass.classLoader
        try {
          val instance = constructor.newInstance(project) as AnalysisScript
          instance.result
        } finally {
          Thread.currentThread().contextClassLoader = saveClassLoader
        }
      }
    }
  }

  companion object {
    val LOGGER = Logger.getInstance(AnalysisScriptService::class.java)
  }
}

private inline fun <R> Path.useDirectory(block: (Path) -> R): R {
  return try {
    block(this)
  } finally {
    FileUtilRt.delete(this.toFile())
  }
}
