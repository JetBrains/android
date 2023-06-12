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
package com.android.tools.idea.instrumentation.threading

import com.android.tools.idea.util.StudioPathManager.isRunningFromSources
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import com.intellij.execution.process.OSProcessUtil
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.thisLogger
import com.sun.tools.attach.VirtualMachine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class ThreadingChecker : ApplicationInitializedListener {

  /** Start receiving notifications from the threading agent. */
  override fun componentsInitialized() {
    val agentLoadedAtStartup = try {
      Class.forName("com.android.tools.instrumentation.threading.agent.Agent", false, null)
      true
    }
    catch (e: ClassNotFoundException) {
      false
    }

    if (agentLoadedAtStartup) {
      ThreadingCheckerTrampoline.installHook(ThreadingCheckerHookImpl())
      thisLogger().debug("ThreadingChecker listener has been installed.")
    } else {
      // Attempt to load the threading agent dynamically when running an EAP build of Android Studio.
      // As for the tests and running Android Studio from IntelliJ during development use the -javaagent JVM option instead.
      maybeAttachThreadingAgent()
    }
  }

  private fun maybeAttachThreadingAgent() {
    if (!ApplicationManager.getApplication().isEAP) {
      // We only allow dynamic attachment of the threading agent in EAP builds
      return
    }

    if (isRunningFromSources()) {
      // Dynamic attachment of the threading agent is disallowed when running from sources
      return
    }

    if (ApplicationManager.getApplication().isUnitTestMode) {
      // Dynamic attachment of the threading agent is disallowed when running from sources
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread { attachThreadingAgent() }
  }

  private fun attachThreadingAgent() {
    var vm: VirtualMachine? = null
    try {
      val threadingAgentJarPath = getThreadingAgentJarLocation()
      if (threadingAgentJarPath == null) {
        thisLogger().warn("Couldn't locate threading_agent.jar.")
        return
      }
      vm = VirtualMachine.attach(OSProcessUtil.getApplicationPid())
      vm.loadAgent(threadingAgentJarPath.toString())
      ThreadingCheckerTrampoline.installHook(ThreadingCheckerHookImpl())
      thisLogger().debug("ThreadingChecker listener has been installed (after threading agent was dynamically loaded).")
    }
    catch (e: Exception) {
      thisLogger().error(e)
    }
    finally {
      vm?.detach()
    }
  }

  private fun getThreadingAgentJarLocation(): Path? {
    // Path for the installed Studio.
    val homePath: Path = Paths.get(PathManager.getHomePath())
    val jarFile: Path = homePath.resolve("plugins/android/resources/threading_agent.jar")
    if (!Files.exists(jarFile)) {
      return null
    }
    return jarFile
  }
}