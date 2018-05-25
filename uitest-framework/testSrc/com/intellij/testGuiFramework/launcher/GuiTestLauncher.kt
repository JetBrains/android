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

import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getWorkspaceRoot
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testGuiFramework.impl.GuiTestStarter
import org.apache.log4j.Level
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Paths
import java.util.*
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

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

  private val LOG = Logger.getInstance("#com.intellij.testGuiFramework.launcher.GuiTestLauncher")

  var process: Process? = null
  private var vmOptionsFile: File? = null

  private const val TEST_GUI_FRAMEWORK_MODULE_NAME = "intellij.android.guiTestFramework"
  private const val STUDIO_UITESTS_MAIN_MODULE_NAME = "intellij.android.guiTests"
  private const val MAIN_CLASS_NAME = "com.intellij.idea.Main"

  init {
    LOG.setLevel(Level.INFO)
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
      if (vmOptionsFile == null) {
        vmOptionsFile = createAugmentedVMOptionsFile(File(GuiTestOptions.getVmOptionsFilePath()), port)
      }
      startIdeProcess(createArgsByPath(path))
    }
  }

  private fun startIdeProcess(args: List<String>) {
    val processBuilder = ProcessBuilder().inheritIO().command(args)
    vmOptionsFile?.let {
      processBuilder.environment()["STUDIO_VM_OPTIONS"] = it.canonicalPath
    }
    process = processBuilder.start()
  }

  /**
   * Creates a copy of the given VM options file in the temp directory, appending the options to set the application starter and port.
   * This is necessary to run the IDE via a native launcher, which doesn't accept command-line arguments.
   */
  private fun createAugmentedVMOptionsFile(originalFile: File, port: Int) =
    FileUtil.createTempFile("studio_uitests.vmoptions", "", true).apply {
      FileUtil.writeToFile(this, """${originalFile.readText()}
-Didea.gui.test.port=$port
-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}
-Didea.gui.test.remote.ide.path=${GuiTestOptions.getRemoteIdePath()}
-Didea.config.path=${GuiTests.getConfigDirPath()}
-Didea.system.path=${GuiTests.getSystemDirPath()}""")
    }

  private fun createArgs(port: Int): List<String> {
    if (TestUtils.runningFromBazel()) {
      buildBazelClasspathJar()
    }
    val args = listOf<String>()
        .plus(getCurrentJavaExec())
        .plus(getVmOptions(port))
        .plus("-classpath")
        .plus(getFullClasspath(STUDIO_UITESTS_MAIN_MODULE_NAME))
        .plus(MAIN_CLASS_NAME)
    return args
  }

  private fun createArgsByPath(path: String): List<String> = listOf(path)

  /**
   * Default VM options to start IntelliJ IDEA (or IDEA-based IDE). To customize options use com.intellij.testGuiFramework.launcher.GuiTestOptions
   */
  private fun getVmOptions(port: Int): List<String> {
    // TODO(b/77341383): avoid having to sync manually with studio64.vmoptions
    var options = listOf(
      /* studio64.vmoptions */
      "-Xms256m",
      "-Xmx1280m",
      "-XX:ReservedCodeCacheSize=240m",
      "-XX:+UseConcMarkSweepGC",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-Dsun.io.useCanonCaches=false",
      "-Djava.net.preferIPv4Stack=true",
      "-Djna.nosys=true",
      "-Djna.boot.library.path=",
      "-XX:MaxJavaStackTraceDepth=-1",
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:-OmitStackTraceInFastThrow",
      "-ea",
      "-Dawt.useSystemAAFontSettings=lcd",
      "-Dsun.java2d.renderer=sun.java2d.marlin.MarlinRenderingEngine",
      /* studio.sh options */
      "-Xbootclasspath/p:${GuiTestOptions.getBootClasspath()}",
      "-Didea.platform.prefix=AndroidStudio",
      "-Didea.jre.check=true",
      /* testing-specific options */
      "-Didea.config.path=${GuiTests.getConfigDirPath()}",
      "-Didea.system.path=${GuiTests.getSystemDirPath()}",
      "-Dplugin.path=${GuiTestOptions.getPluginPath()}",
      "-Ddisable.android.first.run=true",
      "-Ddisable.config.import=true",
      "-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}",
      "-Didea.gui.test.port=$port")
    /* debugging options */
    if (GuiTestOptions.isDebug()) {
      options += "-Didea.debug.mode=true"
      options += "-Xdebug"
      options += "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}"
    }
    if (TestUtils.runningFromBazel()) {
      options += "-Didea.home=${IdeaTestSuiteBase.createTmpDir("tools/idea")}"
      options += "-Dgradle.user.home=${IdeaTestSuiteBase.createTmpDir("home")}"
      options += "-DANDROID_SDK_HOME=${IdeaTestSuiteBase.createTmpDir(".android")}"
      options += "-Dlayoutlib.thread.timeout=60000"
      options += "-Dresolve.descriptors.in.resources=true"
      options += "-Dstudio.dev.jdk=${getJdkPathForGradle()}"
    }
    return options
  }

  private fun getJdkPathForGradle(): String? {
    val jdk = File(getWorkspaceRoot(), "prebuilts/studio/jdk")
    if (jdk.exists()) {
      return File(jdk, "BUILD").toPath().toRealPath().toFile().getParentFile().absolutePath
    }
    return null
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
    if (TestUtils.runningFromBazel()) {
      return TestUtils.getWorkspaceFile("classpath.jar").canonicalPath
    } else {
      val classpath = getExtendedClasspath(moduleName)
      classpath.addAll(getTestClasspath())
      return classpath.toList().joinToString(separator = ":")
    }
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

  private fun buildBazelClasspathJar() {
    if (TestUtils.runningFromBazel()) {
      val jars = getTestClasspath()
      val classpath = StringBuilder().apply {
        for (jar in jars) {
          append("file:" + jar.absolutePath.replace(" ", "%20") + " ")
        }
      }

      val manifest = Manifest()
      manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
      manifest.mainAttributes[Attributes.Name.CLASS_PATH] = classpath.toString()

      JarOutputStream(FileOutputStream(File(TestUtils.getWorkspaceRoot(), "classpath.jar")), manifest).use {}
    }
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