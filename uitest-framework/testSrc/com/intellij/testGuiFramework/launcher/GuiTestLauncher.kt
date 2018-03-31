/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.launcher

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testGuiFramework.impl.GuiTestStarter
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import java.net.URL
import java.nio.file.Paths
import java.util.*
import kotlin.concurrent.thread

/**
 * [GuiTestLauncher] handles the mechanics of preparing to launch the client IDE and forking the process. It can do this two ways:
 *   1) "Locally," meaning it essentially runs 'java com.intellij.Main' with a classpath built from the classes loaded by
 *   GuiTestLauncher's ClassLoader augmented with some Jps magic. It also sets various system properties and VM options.
 *
 *   2) "By path," meaning it simply executes a given command. In particular, this can be studio.sh in the bin folder of a release build.
 *   No special system properties or VM options are set in this case.
 *
 * In both cases it adds arguments 'guitest' and 'port=######'.
 *
 * By default, option (1) is used. To use option (2), set the 'idea.gui.test.remote.ide.path' system property to the path to the desired
 * executable/script.
 *
 * See [GuiTestStarter] and [GuiTestThread] for details on what happens after the new process is forked.
 *
 * @author Sergey Karashevich
 */
object GuiTestLauncher {

  private val LOG = Logger.getLogger("#com.intellij.testGuiFramework.launcher.GuiTestLauncher")

  var process: Process? = null

  private const val TEST_GUI_FRAMEWORK_MODULE_NAME = "uitest-framework"
  private const val STUDIO_UITESTS_MAIN_MODULE_NAME = "android-uitests"
  private const val MAIN_CLASS_NAME = "com.intellij.idea.Main"

  init {
    LOG.level = Level.INFO
  }

  val project: JpsProject by lazy {
    val home = PathManager.getHomePath()
    val model = JpsElementFactory.getInstance().createModel()
    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    val jpsProject = model.project
    JpsProjectLoader.loadProject(jpsProject, pathVariables, home)
    jpsProject.changeOutputIfNeeded()
    jpsProject
  }
  private val modulesList: List<JpsModule> by lazy {
    project.modules
  }
  private val testGuiFrameworkModule: JpsModule by lazy {
    modulesList.module(TEST_GUI_FRAMEWORK_MODULE_NAME) ?: throw Exception("Unable to find module '$TEST_GUI_FRAMEWORK_MODULE_NAME'")
  }

  fun runIde (port: Int) {
    val path = GuiTestOptions.getRemoteIdePath()
    if (path == "undefined") {
      startIdeProcess(createArgs(port))
    } else {
      startIdeProcess(createArgsByPath(path, port))
    }
  }

  private fun startIdeProcess(args: List<String>) {
    thread(start = true, name = "IdeaTestThread") {
      process = ProcessBuilder().inheritIO().command(args).start()
    }
  }

  private fun createArgs(port: Int): List<String> {
    val args = listOf<String>()
        .plus(getCurrentJavaExec())
        .plus(getVmOptions())
        .plus("-classpath")
        .plus(getFullClasspath(STUDIO_UITESTS_MAIN_MODULE_NAME))
        .plus(MAIN_CLASS_NAME)
        .plus(GuiTestStarter.COMMAND_NAME)
        .plus("port=$port")
    return args
  }

  private fun createArgsByPath(path: String, port: Int): List<String> = listOf(path, GuiTestStarter.COMMAND_NAME, "port=$port")

  /**
   * Default VM options to start IntelliJ IDEA (or IDEA-based IDE). To customize options use com.intellij.testGuiFramework.launcher.GuiTestOptions
   */
  private fun getVmOptions(): List<String> {
    // TODO(b/77341383): avoid having to sync manually with studio64.vmoptions
    var options = mutableListOf<String>()
      /* studio64.vmoptions */
      .plus("-Xms256m")
      .plus("-Xmx1280m")
      .plus("-XX:ReservedCodeCacheSize=240m")
      .plus("-XX:+UseConcMarkSweepGC")
      .plus("-XX:SoftRefLRUPolicyMSPerMB=50")
      .plus("-Dsun.io.useCanonCaches=false")
      .plus("-Djava.net.preferIPv4Stack=true")
      .plus("-Djna.nosys=true")
      .plus("-Djna.boot.library.path=")
      .plus("-XX:MaxJavaStackTraceDepth=-1")
      .plus("-XX:+HeapDumpOnOutOfMemoryError")
      .plus("-XX:-OmitStackTraceInFastThrow")
      .plus("-ea")
      .plus("-Dawt.useSystemAAFontSettings=lcd")
      .plus("-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine")
      /* studio.sh options */
      .plus("-Xbootclasspath/p:${GuiTestOptions.getBootClasspath()}")
      .plus("-Didea.platform.prefix=AndroidStudio")
      .plus("-Didea.jre.check=true")
      /* testing-specific options */
      .plus("-Didea.config.path=${GuiTestOptions.getConfigPath()}")
      .plus("-Didea.system.path=${GuiTestOptions.getSystemPath()}")
      .plus("-Dplugin.path=${GuiTestOptions.getPluginPath()}")
      .plus("-Ddisable.android.first.run=true")
      .plus("-Ddisable.config.import=true")
    /* debugging options */
    if (GuiTestOptions.isDebug()) {
      options = options.plus("-Didea.debug.mode=true")
        .plus("-Xdebug")
        .plus("-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}")
    }
    return options
  }

  private fun getCurrentJavaExec(): String {
    val homePath = System.getProperty("java.home")
    val jreDir = File(homePath)
    val homeDir = File(jreDir.parent)
    val binDir = File(homeDir, "bin")
    val javaName: String = if (System.getProperty("os.name").toLowerCase().contains("win")) "java.exe" else "java"
    return File(binDir, javaName).path
  }

  /**
   * return union of classpaths for current test (get from classloader) and classpaths of main and testGuiFramework modules*
   */
  private fun getFullClasspath(moduleName: String): String {
    val classpath = getExtendedClasspath(moduleName)
    classpath.addAll(getTestClasspath())
    return classpath.toList().joinToString(separator = ":")
  }

  private fun getTestClasspath(): List<File> {
    val classLoader = this.javaClass.classLoader
    val urlClassLoaderClass = classLoader.javaClass
    val getUrlsMethod = urlClassLoaderClass.methods.firstOrNull { it.name.toLowerCase() == "geturls" }!!
    @Suppress("UNCHECKED_CAST")
    val urlsListOrArray = getUrlsMethod.invoke(classLoader)
    var urls = (urlsListOrArray as? List<*> ?: (urlsListOrArray as Array<*>).toList()).filterIsInstance(URL::class.java)
    return urls.map { Paths.get(it.toURI()).toFile() }
  }


  /**
   * return union of classpaths for @moduleName and testGuiFramework modules
   */
  private fun getExtendedClasspath(moduleName: String): MutableSet<File> {
    val resultSet = LinkedHashSet<File>()
    val module = modulesList.module(moduleName) ?: throw Exception("Unable to find module with name: $moduleName")
    resultSet.addAll(module.getClasspath())
    resultSet.addAll(testGuiFrameworkModule.getClasspath())
    return resultSet
  }

  private fun List<JpsModule>.module(moduleName: String): JpsModule? =
    this.firstOrNull { it.name == moduleName }

  private fun JpsModule.getClasspath(): MutableCollection<File> =
    JpsJavaExtensionService.dependencies(this).productionOnly().runtimeOnly().recursively().classes().roots


  private fun getOutputRootFromClassloader(): File {
    val pathFromClassloader = PathManager.getJarPathForClass(GuiTestLauncher::class.java)
    val productionDir = File(pathFromClassloader).parentFile
    assert(productionDir.isDirectory)
    val outputDir = productionDir.parentFile
    assert(outputDir.isDirectory)
    return outputDir
  }

  /**
   * @return true if classloader's output path is the same to module's output path (and also same to project)
   */
  private fun needToChangeProjectOutput(project: JpsProject): Boolean =
    JpsJavaExtensionService.getInstance().getProjectExtension(project)?.outputUrl ==
        getOutputRootFromClassloader().path


  private fun JpsProject.changeOutputIfNeeded() {
    if (!needToChangeProjectOutput(this)) {
      val projectExtension = JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(this)
      projectExtension.outputUrl = VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(getOutputRootFromClassloader().path))
    }
  }

}