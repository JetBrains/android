// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.project.importing

import com.android.tools.idea.gradle.jdk.GradleDefaultJdkPathStore
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JdkConstants.JDK_21_PATH
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListener
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class GradleProjectImporterJavaHomeTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private lateinit var jdksBeforeTest: Set<Sdk>

  @Before
  fun setUp() {
    jdksBeforeTest = ProjectJdkTable.getInstance().allJdks.toSet()
    GradleDefaultJdkPathStore.jdkPath = JDK_21_PATH
  }

  @After
  fun tearDown() {
    GradleDefaultJdkPathStore.jdkPath = null
    runWriteActionAndWait {
      (ProjectJdkTable.getInstance().allJdks.toSet() - jdksBeforeTest).forEach { SdkConfigurationUtil.removeSdk(it) }
    }
  }

  @Test
  fun `java home is written before the new project is linked`() {
    val project = projectRule.project
    val javaHomeAtLink = mutableListOf<File?>()
    GradleSettings.getInstance(project).subscribe(object : ExternalSystemSettingsListener<GradleProjectSettings> {
      override fun onProjectsLinked(settings: Collection<GradleProjectSettings>) {
        settings.mapTo(javaHomeAtLink) { GradleConfigProperties(File(it.externalProjectPath)).javaHome }
      }
    }, projectRule.testRootDisposable)

    GradleProjectImporter.configureNewProject(project)

    assertThat(javaHomeAtLink).hasSize(1)
    assertThat(javaHomeAtLink.single()?.path).isEqualTo(JDK_21_PATH)
  }
}
