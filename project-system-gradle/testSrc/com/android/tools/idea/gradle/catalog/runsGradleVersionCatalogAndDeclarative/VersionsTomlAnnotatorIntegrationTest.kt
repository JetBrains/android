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
package com.android.tools.idea.gradle.catalog.runsGradleVersionCatalogAndDeclarative

import com.android.tools.idea.gradle.catalog.VersionsTomlAnnotator
import com.android.tools.idea.gradle.project.sync.snapshots.replaceContent
import com.android.tools.idea.gradle.service.VersionCatalogDocumentationProvider
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import java.io.File
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class VersionsTomlAnnotatorIntegrationTest {

  @get:Rule val projectRule = AndroidGradleProjectRule().onEdt()

  @Test
  fun testAnnotationInCustomNamedCatalog() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG) { root ->
      // Rename libs.versions.toml to custom.toml
      val catalogFile = File(root, "gradle/libs.versions.toml")
      val newCatalogFile = File(root, "gradle/custom.toml")
      catalogFile.renameTo(newCatalogFile)

      // Update settings.gradle to use the custom catalog
      val settingsFile = File(root, "settings.gradle")
      settingsFile.replaceContent { content ->
        content.replace(
          "dependencyResolutionManagement {",
          "dependencyResolutionManagement {\n" +
            "    versionCatalogs {\n" +
            "        libs {\n" +
            "            from(files(\"gradle/custom.toml\"))\n" +
            "        }\n" +
            "    }",
        )
      }
    }

    val virtualFile = VfsUtil.findFileByIoFile(File(projectRule.project.basePath, "gradle/custom.toml"), true)!!
    projectRule.fixture.configureFromExistingVirtualFile(virtualFile)

    // Add an error to the custom catalog
    projectRule.fixture.type("\n[plugins]\na = \"some:plugin\"\n")

    val annotator = VersionsTomlAnnotator()
    val psiFile = projectRule.fixture.file

    val element = runReadAction {
      // check for segment key
      psiFile.findElementAt(psiFile.text.indexOf("a ="))!!.parent.parent
    }

    val annotations = CodeInsightTestUtil.testAnnotator(annotator, element)
    Truth.assertThat(annotations).hasSize(1)
  }

  @Test
  fun testDocumentationInCustomNamedCatalog() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG) { root ->
      val catalogFile = File(root, "gradle/libs.versions.toml")
      val newCatalogFile = File(root, "gradle/custom.toml")
      catalogFile.renameTo(newCatalogFile)

      val settingsFile = File(root, "settings.gradle")
      settingsFile.replaceContent { content ->
        content.replace(
          "dependencyResolutionManagement {",
          "dependencyResolutionManagement {\n" +
            "    versionCatalogs {\n" +
            "        libs {\n" +
            "            from(files(\"gradle/custom.toml\"))\n" +
            "        }\n" +
            "    }",
        )
      }
    }

    val virtualFile = VfsUtil.findFileByIoFile(File(projectRule.project.basePath, "gradle/custom.toml"), true)!!
    projectRule.fixture.configureFromExistingVirtualFile(virtualFile)

    val docProvider = VersionCatalogDocumentationProvider()
    val psiFile = projectRule.fixture.file

    // Find the 'module' key in [libraries]
    val element = runReadAction {
      val offset = psiFile.text.indexOf("module")
      psiFile.findElementAt(offset)!!
    }

    val doc = runReadAction { docProvider.generateDoc(element, null) }
    Assert.assertNotNull("Documentation should not be null for registered custom catalog file", doc)
  }
}
