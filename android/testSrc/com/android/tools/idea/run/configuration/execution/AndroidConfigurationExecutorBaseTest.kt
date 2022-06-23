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
import com.android.ddmlib.IShellOutputReceiver
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.run.ApkProvider
import com.android.tools.idea.run.ApkProvisionException
import com.android.tools.idea.run.ApplicationIdProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.Mockito
import java.io.File


abstract class AndroidConfigurationExecutorBaseTest {
  protected val appId = "com.example.app"
  protected val componentName = "com.example.app.Component"

  @get:Rule
  val projectRule = ProjectRule()

  @get:Rule
  var fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  val project: Project
    get() = projectRule.project

  //val testRootDisposable: Disposable
  //  get() = projectRule
  val myModule: com.intellij.openapi.module.Module
    get() = projectRule.module

  @Before
  fun setUp() {
    val projectSystemMock = createProjectSystemMock()
    whenever(projectSystemMock.getApkProvider(any(RunConfiguration::class.java))).thenReturn(TestApksProvider(appId))
    whenever(projectSystemMock.getApplicationIdProvider(any(RunConfiguration::class.java))).thenReturn(TestApplicationIdProvider(appId))
  }

  @After
  fun after() {
    //XDebuggerManager.getInstance(project).debugSessions.forEach {
    //  it.stop()
    //}
  }

  private fun createProjectSystemMock(): AndroidProjectSystem {
    val projectSystemMock = Mockito.mock(AndroidProjectSystem::class.java)
    val projectSystemService = Mockito.mock(ProjectSystemService::class.java)
    whenever(projectSystemService.projectSystem).thenReturn(projectSystemMock)
    project.replaceService(ProjectSystemService::class.java, projectSystemService, projectRule.project)
    return projectSystemMock
  }

  class TestReceiver(private val receiver: IShellOutputReceiver?) {
    fun addOutput(commandOutput: String) {
      val byteArray = "$commandOutput\n".toByteArray(Charsets.UTF_8)
      receiver?.addOutput(byteArray, 0, byteArray.size)
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
}
