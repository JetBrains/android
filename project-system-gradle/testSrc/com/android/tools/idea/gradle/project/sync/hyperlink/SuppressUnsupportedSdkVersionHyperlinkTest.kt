package com.android.tools.idea.gradle.project.sync.hyperlink

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.dsl.utils.FN_GRADLE_PROPERTIES
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.mockito.Mockito.mock
import java.io.File
import com.intellij.openapi.util.io.FileUtil.loadFile

class SuppressUnsupportedSdkVersionHyperlinkTest: AndroidGradleTestCase() {

  fun `test when project is disposed`() {
    val gradlePropertiesPath = File(projectFolderPath, FN_GRADLE_PROPERTIES)
    deleteGradlePropertiesFile(gradlePropertiesPath)

    val mockProject = mock(Project::class.java)
    whenever(mockProject.isDisposed).thenReturn(true)
    SuppressUnsupportedSdkVersionHyperlink("abc=x").execute(mockProject)
    assertFalse(gradlePropertiesPath.exists())
  }

  fun `test gradle properties file is updated`() {
    prepareProjectForImport(TestProjectPaths.BASIC)

    val gradlePropertiesPath = File(projectFolderPath, FN_GRADLE_PROPERTIES)
    assertThat(gradlePropertiesPath.exists())
    gradlePropertiesPath.appendText("abc=y")
    assertFalse("Gradle properties must not contain abc=x", loadFile(gradlePropertiesPath)
      .contains("abc=x"))

    SuppressUnsupportedSdkVersionHyperlink("abc=x").execute(project)

    assertTrue(gradlePropertiesPath.exists())
    assertTrue("Gradle properties must contain abc=x", loadFile(gradlePropertiesPath)
      .contains("abc=x"))
  }

  fun `test gradle properties file is created and updated`() {
    prepareProjectForImport(TestProjectPaths.BASIC)

    val gradlePropertiesPath = File(projectFolderPath, FN_GRADLE_PROPERTIES)
    deleteGradlePropertiesFile(gradlePropertiesPath)

    val hyperlink = SuppressUnsupportedSdkVersionHyperlink("abc=x")
    hyperlink.execute(project)

    assertTrue(gradlePropertiesPath.exists())
    assertTrue("Gradle properties must contain abc=x", loadFile(gradlePropertiesPath)
      .contains("abc=x"))
  }

  private fun deleteGradlePropertiesFile(path: File) {
    if (path.exists()) {
      assertTrue(path.delete())
    }
  }
}