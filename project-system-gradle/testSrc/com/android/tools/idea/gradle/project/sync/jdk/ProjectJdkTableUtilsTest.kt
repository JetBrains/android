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
package com.android.tools.idea.gradle.project.sync.jdk

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.JdkConstants
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.kotlin.tools.projectWizard.core.asPath

class ProjectJdkTableUtilsTest : LightPlatformTestCase() {

  override fun tearDown() {
    runWriteActionAndWait {
      val jdkTable = ProjectJdkTable.getInstance()
      jdkTable.allJdks.forEach { jdkTable.removeJdk(it) }
    }
    super.tearDown()
  }

  fun `test Given ProjectTableJdk containing multiple entries When finding an existing and valid one Then expected entry is returned`() {
    val jdk21 = IdeSdks.getInstance().getOrCreateJdk(JdkConstants.JDK_21_PATH.asPath())!!
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk9())
      ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk17())
    }

    ProjectJdkTableUtils.findProjectTableJdkWithVersion(21).also {
      assertNotNull(it)
      assertEquals(jdk21.name, it?.name)
      assertEquals(jdk21.homePath, it?.homePath)
      assertEquals(jdk21.versionString, it?.versionString)
    }
  }

  fun `test Given ProjectTableJdk containing multiple entries When finding a not existing one Then null is returned`() {
    runWriteActionAndWait {
      ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk9())
      ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk17())
    }

    ProjectJdkTableUtils.findProjectTableJdkWithVersion(11).also { assertNull(it) }
  }

  fun `test Given ProjectTableJdk containing invalid entry When fetching it Then null is returned since is invalid JDK`() {
    runWriteActionAndWait { ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk9()) }

    ProjectJdkTableUtils.findProjectTableJdkWithVersion(9).also { assertNull(it) }
  }
}
