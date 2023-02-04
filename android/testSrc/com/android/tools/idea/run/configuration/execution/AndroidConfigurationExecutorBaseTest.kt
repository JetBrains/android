/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.testutils.MockitoCleanerRule
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.xdebugger.XDebuggerManager
import org.junit.After
import org.junit.Rule
import java.io.File


abstract class AndroidConfigurationExecutorBaseTest {
  protected val appId = "com.example.app"
  protected val componentName = "com.example.app.Component"

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  var fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  @get:Rule
  val cleaner = MockitoCleanerRule()

  val project: Project
    get() = projectRule.project

  val myModule: com.intellij.openapi.module.Module
    get() = projectRule.module

  @After
  fun after() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  protected class TestApksProvider(private val appId: String) : ApkProvider {
    @Throws(ApkProvisionException::class)
    override fun getApks(device: IDevice): Collection<ApkInfo> {
      return listOf(ApkInfo(File("file"), appId))
    }
  }

  protected class TestApplicationIdProvider(private val appId: String) : ApplicationIdProvider {
    override fun getPackageName() = appId

    override fun getTestPackageName(): String? = null
  }

  protected fun getRunContentDescriptorForTests(runContentDescriptorProvider: () -> RunContentDescriptor): RunContentDescriptor {
    val runContentDescriptor = runContentDescriptorProvider.invoke()
    val processHandler = runContentDescriptor.processHandler!!
    Disposer.register(project) {
      processHandler.detachProcess()
    }

    if (!processHandler.isStartNotified) {
      processHandler.startNotify()
    }

    return runContentDescriptor
  }
}
