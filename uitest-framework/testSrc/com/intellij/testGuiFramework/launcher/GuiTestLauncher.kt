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

import com.android.prefs.AbstractAndroidLocations
import com.android.testutils.TestUtils
import com.android.tools.idea.tests.gui.framework.AnalyticsTestUtils
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.aspects.AspectsAgentLogUtil
import com.android.tools.tests.IdeaTestSuiteBase
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testGuiFramework.impl.GuiTestStarter
import com.intellij.util.lang.JavaVersion
import org.apache.log4j.Level
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
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
 */
object GuiTestLauncher {

  private val LOG = Logger.getInstance(GuiTestLauncher::class.java)

  var process: Process? = null
  private var vmOptionsFile: File? = null

  private const val MAIN_CLASS_NAME = "com.intellij.idea.Main"

  private val classpathJar = File(GuiTests.getGuiTestRootDirPath(), "classpath.jar")

  init {
    LOG.setLevel(Level.INFO)
    buildClasspathJar()
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
    /* Force headful execution in Mac OS b/175816469 */
    if (SystemInfo.isMac) {
      processBuilder.environment()["AWT_FORCE_HEADFUL"] = "true"
    }
    setAspectsAgentEnv(processBuilder)
    process = processBuilder.start()
  }

  private fun setAspectsAgentEnv(processBuilder: ProcessBuilder) {
    val aspectsAgentLogPath = AspectsAgentLogUtil.getAspectsAgentLog()?.absolutePath
    if (aspectsAgentLogPath != null) {
      processBuilder.environment()["ASPECTS_AGENT_LOG"] = aspectsAgentLogPath
    }
    val activeStackTracesLog = AspectsAgentLogUtil.getAspectsActiveStackTracesLog()?.absolutePath
    if (activeStackTracesLog != null) {
      processBuilder.environment()["ASPECTS_ACTIVE_BASELINE_STACKTRACES"] = activeStackTracesLog
    }
  }

  /**
   * Creates a copy of the given VM options file in the temp directory, appending the options to set the application starter and port.
   * This is necessary to run the IDE via a native launcher, which doesn't accept command-line arguments.
   */
  private fun createAugmentedVMOptionsFile(originalFile: File, port: Int) =
    FileUtil.createTempFile("studio_uitests.vmoptions", "", true).apply {
      FileUtil.writeToFile(this, """${originalFile.readText()}
-Didea.gui.test.port=$port
-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}""" + if (GuiTestOptions.isDebug()) """
-Didea.debug.mode=true
-Xdebug
-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}""" else "" )
    }

  private fun createArgs(port: Int) =
    listOf<String>()
        .plus(getCurrentJavaExec())
        .plus(getVmOptions(port))
        .plus("-classpath")
        .plus(classpathJar.absolutePath)
        .plus(MAIN_CLASS_NAME)
        .plus(GuiTestStarter.COMMAND_NAME)

  private fun createArgsByPath(path: String): List<String> = listOf(path)

  /**
   * Default VM options to start IntelliJ IDEA (or IDEA-based IDE). To customize options use com.intellij.testGuiFramework.launcher.GuiTestOptions
   */
  private fun getVmOptions(port: Int): List<String> {
    val options = mutableListOf(
      /* studio64.vmoptions */
      "@" + TestUtils.getBinPath("tools/adt/idea/studio/default_user_jvm_args.txt"),
      /* Studio launcher JVM args */
      "@" + TestUtils.getBinPath("tools/adt/idea/studio/required_jvm_args.txt"),
      /* testing-specific options */
      "-Xmx4096m",
      "-ea",
      "-Didea.jre.check=true",
      "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}",
      "-Duser.home=${System.getProperty("java.io.tmpdir")}",
      "-Didea.config.path=${GuiTests.getConfigDirPath()}",
      "-Didea.system.path=${GuiTests.getSystemDirPath()}",
      "-Dplugin.path=${GuiTestOptions.getPluginPath()}",
      "-Dkotlin.script.classpath=",  // TODO(b/213385827): Fix Kotlin script classpath calculation during tests
      "-Didea.force.use.core.classloader=true",
      "-Didea.trust.all.projects=true",
      "-Ddisable.android.first.run=true",
      "-Ddisable.config.import=true",
      "-Didea.application.starter.command=${GuiTestStarter.COMMAND_NAME}",
      "-Didea.gui.test.port=$port",
    )
    /* b/246634435 */
    if (System.getProperty("embedded.jdk.path") != null) {
      options += "-Dembedded.jdk.path=${System.getProperty("embedded.jdk.path")}"
    }

    /* Move Menu bar into IDEA window on Mac OS. Required for test framework to access Menu */
    if (SystemInfo.isMac) {
      options += "-Dapple.laf.useScreenMenuBar=false"
      options += "-DjbScreenMenuBar.enabled=false"
    }
    /* aspects agent options */
    if (JavaVersion.current().feature < 9) {  // b/134524025
      options += "-javaagent:${GuiTestOptions.getAspectsAgentJar()}=${GuiTestOptions.getAspectsAgentRules()};${GuiTestOptions.getAspectsAgentBaseline()}"
      options += "-Daspects.baseline.export.path=${GuiTestOptions.getAspectsBaselineExportPath()}"
    }
    /* options for BLeak */
    if (System.getProperty("enable.bleak") == "true") {
      options += "-Denable.bleak=true"
      options += "-Xmx16g"
      val jvmtiAgent =
        TestUtils.resolveWorkspacePathUnchecked("_solib_k8/libtools_Sadt_Sidea_Sbleak_Ssrc_Scom_Sandroid_Stools_Sidea_Sbleak_Sagents_Slibjnibleakhelper.so").toRealPath()
      if (Files.exists(jvmtiAgent)) {
        options += "-agentpath:$jvmtiAgent"
        options += "-Dbleak.jvmti.enabled=true"
        options += "-Djava.library.path=${System.getProperty("java.library.path")}:${jvmtiAgent.parent}"
      } else {
        println("BLeak JVMTI agent not found. Falling back to Java implementation: application threads will not be paused, and traversal roots will be different")
      }
    }
    /* debugging options */
    if (GuiTestOptions.isDebug()) {
      options += "-Didea.debug.mode=true"
      options += "-Xdebug"
      options += "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=${GuiTestOptions.getDebugPort()}"
    }
    /**
     * Disable analytic consent dialog by default.
     * For tests that require it, the system property "enable.android.analytics.consent.dialog.for.test"
     * can be set in the Build file as one of the jvm_flags
     */
    options += AnalyticsTestUtils.vmDialogOption
    options += AnalyticsTestUtils.vmLoggingOption
    /* options for tests with native libraries */
    if (!options.any { it.startsWith("-Djava.library.path=") }) {
      options += "-Djava.library.path=${System.getProperty("java.library.path")}"
    }
    if (TestUtils.runningFromBazel()) {
      options += "-Didea.system.path=${IdeaTestSuiteBase.createTmpDir("idea/system")}"
      options += "-Didea.config.path=${IdeaTestSuiteBase.createTmpDir("idea/config")}"
      options += "-Dgradle.user.home=${IdeaTestSuiteBase.createTmpDir("home")}"
      options += "-D${AbstractAndroidLocations.ANDROID_PREFS_ROOT}=${IdeaTestSuiteBase.createTmpDir(".android")}"
      options += "-Dlayoutlib.thread.timeout=60000"
      options += "-Dresolve.descriptors.in.resources=true"
    }
    return options
  }

  private fun getCurrentJavaExec(): String {
    val homeDir = File(System.getProperty("java.home"))
    val binDir = File(if (JavaVersion.current().feature >= 9) homeDir else homeDir.parentFile, "bin")
    val javaName = if (SystemInfo.isWindows) "java.exe" else "java"
    return File(binDir, javaName).path
  }

  private fun getTestClasspath(): List<File> {
    val classLoader = this.javaClass.classLoader
    val urlClassLoaderClass = classLoader.javaClass
    if (urlClassLoaderClass.name == "com.intellij.util.lang.UrlClassLoader") {
      val getUrlsMethod = urlClassLoaderClass.methods.firstOrNull { it.name.toLowerCase() == "geturls" }!!
      @Suppress("UNCHECKED_CAST")
      val urlsListOrArray = getUrlsMethod.invoke(classLoader)
      var urls = (urlsListOrArray as? List<*> ?: (urlsListOrArray as Array<*>).toList()).filterIsInstance(URL::class.java)
      return urls.filter { !it.toString().contains("android.core.tests") }.map { Paths.get(it.toURI()).toFile() }
    } else {
      // under JDK 11, when run from the IDE, the ClassLoader in question here will be ClassLoaders$AppClassLoader.
      // Fortunately, under these circumstances, java.class.path has everything we need.
      return System.getProperty("java.class.path").split(File.pathSeparator).map(::File)
    }
  }


  private fun buildClasspathJar() {
    val files = getTestClasspath()
    val prefix = if (SystemInfo.isWindows) "file:/" else "file:"
    val classpath = StringBuilder().apply {
      for (file in files) {
        append(prefix + file.absolutePath.replace(" ", "%20").replace("\\", "/") + if (file.isDirectory) "/ " else " ")
      }
    }

    val manifest = Manifest()
    manifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    manifest.mainAttributes[Attributes.Name.CLASS_PATH] = classpath.toString()

    JarOutputStream(FileOutputStream(classpathJar), manifest).use {}
  }

}
