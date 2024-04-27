package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.IdeInfo
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class AndroidGradleDisableAutoImportTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun testAutoImportDisabled() {
    if (IdeInfo.getInstance().isAndroidStudio) {
      assertThat(Registry.`is`("external.system.auto.import.disabled")).isTrue()
      val projectTracker = ExternalSystemProjectTracker.getInstance(ProjectManager.getInstance().defaultProject)
      assertThat(projectTracker).isInstanceOf(RefreshOnlyAutoImportProjectTracker::class.java)
    }
  }
}
