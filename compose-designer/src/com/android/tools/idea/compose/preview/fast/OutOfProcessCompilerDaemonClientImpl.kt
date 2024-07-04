/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.fast

import com.android.tools.compile.fast.CompilationResult
import com.android.tools.compile.fast.OutOfProcessCompilerDaemonClient
import com.android.tools.idea.editors.fast.CompilerDaemonClient
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.log.IJLogger
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.gradle.GradleClassFinderUtil
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import java.nio.file.Path
import java.util.EnumSet
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.android.sdk.getInstance
import org.jetbrains.android.uipreview.getLibraryDependenciesJars

/**
 * Default class path locator that returns the classpath for the module source code (excluding
 * dependencies).
 */
private fun defaultModuleCompileClassPathLocator(module: Module): List<String> =
  GradleClassFinderUtil.getModuleCompileOutputs(
      module,
      EnumSet.of(ScopeType.MAIN, ScopeType.ANDROID_TEST, ScopeType.SCREENSHOT_TEST),
    )
    .filter { it.exists() }
    .map { it.absolutePath.toString() }
    .toList()

/**
 * Default class path locator that returns the classpath containing the dependencies of [module] to
 * pass to the compiler.
 */
private fun defaultModuleDependenciesCompileClassPathLocator(module: Module): List<String> {
  val libraryDeps = module.getLibraryDependenciesJars().map { it.toString() }

  val bootclassPath = getInstance(module)?.target?.bootClasspath ?: listOf()
  // The Compose plugin is included as part of the fat daemon jar so no need to specify it

  return (libraryDeps + bootclassPath)
}

/**
 * Finds the folder containing `kotlin-compiler-daemon` jar. This method does not check the path
 * actually exists.
 */
private fun findDaemonJarRootPath(): Path {
  val homePath = FileUtil.toSystemIndependentName(PathManager.getHomePath())
  val jarRootPath =
    if (StudioPathManager.isRunningFromSources()) {
      StudioPathManager.resolvePathFromSourcesRoot("tools/adt/idea/compose-designer/lib/")
        .toString()
    } else {
      // When running as part of the distribution, we allow also to override the path to the daemon
      // via a system property.
      System.getProperty(
        "preview.live.edit.daemon.path",
        FileUtil.join(homePath, "plugins/design-tools/resources/"),
      )
    }

  return Path.of(jarRootPath)
}

/**
 * Implementation of the [CompilerDaemonClient] that talks to a kotlin daemon in a separate JVM. The
 * daemon is built as part of the androidx tree and passed as `daemonPath` to this class
 * constructor.
 *
 * This implementation starts the daemon in a separate JVM and uses stdout to communicate. The
 * daemon will wait for input before starting a compilation.
 *
 * The protocol is as follows:
 * - The daemon will wait for the compiler parameters that will be passed verbatim ot the kolinc
 *   compiler. The daemon will take parameters, one per line, until the string "done" is sent in a
 *   separate line.
 * - The daemon will then send all the compiler output back to Studio via stdout. Once the
 *   compilation is done the daemon will print "RESULT <exit_code>" to stout and will start waiting
 *   for a new command line.
 *
 * @param scope the [CoroutineScope] to be used by the coroutines in the daemon.
 * @param log [Logger] used to log the debug output of the daemon.
 *
 * TODO(b/328608974): move this whole class and file out of compose-designer
 */
@Suppress("BlockingMethodInNonBlockingContext") // All calls are running within the IO context
internal class OutOfProcessCompilerDaemonClientImpl(
  version: String,
  private val scope: CoroutineScope,
  private val log: Logger,
  private val moduleClassPathLocator: (Module) -> List<String> =
    ::defaultModuleCompileClassPathLocator,
  private val moduleDependenciesClassPathLocator: (Module) -> List<String> =
    ::defaultModuleDependenciesCompileClassPathLocator,
) : CompilerDaemonClient {
  private val daemonClient =
    OutOfProcessCompilerDaemonClient(
      version,
      scope,
      IdeSdks.getInstance().jdk?.homePath?.let { javaHomePath -> "$javaHomePath/bin/java" }
        ?: throw IllegalStateException("No SDK found"),
      findDaemonJarRootPath(),
      IJLogger(log),
      StudioFlags.COMPOSE_FAST_PREVIEW_DAEMON_DEBUG.get(),
    )

  override val isRunning: Boolean
    get() = daemonClient.isRunning

  override fun dispose() {
    daemonClient.destroy()
  }

  override suspend fun compileRequest(
    files: Collection<PsiFile>,
    module: Module,
    outputDirectory: Path,
    indicator: ProgressIndicator,
  ): CompilationResult {
    indicator.text = "Building classpath"
    val moduleClassPath = moduleClassPathLocator(module)
    val moduleDependenciesClassPath = moduleDependenciesClassPathLocator(module)

    return daemonClient.compile(
      files.map { it.virtualFile.path }.toList(),
      moduleClassPath,
      moduleClassPath + moduleDependenciesClassPath,
      outputDirectory,
    )
  }
}
