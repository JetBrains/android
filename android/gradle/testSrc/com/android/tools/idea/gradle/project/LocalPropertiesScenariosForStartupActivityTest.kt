/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.SdkConstants.FN_LOCAL_PROPERTIES
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.runDispatching
import com.android.tools.idea.ui.ApplicationUtils
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Tests for {@link AndroidGradleProjectStartupActivity} where local.properties isn't
 * there or definition in the file isn't as expected.
 */
@RunsInEdt
class LocalPropertiesScenariosForStartupActivityTest {

  private var myStartupActivity: AndroidGradleProjectStartupActivity? = null
  private var myRequest: GradleSyncInvoker.Request? = null
  private var myInfo: GradleProjectInfo? = null

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION)
    val syncInvoker = object : GradleSyncInvoker.FakeInvoker() {
      override fun requestProjectSync(
        project: Project,
        request: GradleSyncInvoker.Request,
        listener: GradleSyncListener?
      ) {
        assertThat(myRequest).isNull()
        myRequest = request
        super.requestProjectSync(project, request, listener)
      }
    }
    ApplicationManager.getApplication().replaceService(GradleSyncInvoker::class.java, syncInvoker, projectRule.project)
    myStartupActivity = AndroidGradleProjectStartupActivity()
  }

  @After
  fun after() {
    myRequest = null
    myInfo = null
    myStartupActivity = null
  }

  @Test
  fun `test no sync when local properties file present`() {
    executeMyStartupActivity()
    assertThat(myRequest).isNull()
  }

  @Test
  fun `test sync for missing local properties file`() {
    deleteLocalProperties()
    executeMyStartupActivity()
    assertThat(myRequest).isNotNull()
    assertThat(myRequest!!.trigger).isEqualTo(Trigger.TRIGGER_PROJECT_MODIFIED)
  }

  @Test
  fun `test sync for missing sdk dir in local properties file`() {
    deleteSdkDirFromLocalProperties()
    executeMyStartupActivity()
    assertThat(myRequest).isNotNull()
    assertThat(myRequest!!.trigger).isEqualTo(Trigger.TRIGGER_PROJECT_MODIFIED)
  }

  private fun executeMyStartupActivity() {
    runDispatching {
      myStartupActivity!!.execute(projectRule.project)
    }
  }

  private fun deleteLocalProperties() {
    val localPropertiesFile = File(projectRule.project.basePath, FN_LOCAL_PROPERTIES)
    assertThat(localPropertiesFile.exists()).isTrue()
    runWriteAction {
      localPropertiesFile.toVirtualFile()!!.delete(null)
    }
  }

  private fun deleteSdkDirFromLocalProperties() {
    val localPropertiesFile = File(projectRule.project.basePath, FN_LOCAL_PROPERTIES)
    runWriteAction {
      VfsUtil.saveText(localPropertiesFile.toVirtualFile()!!, "")
    }
  }
}
