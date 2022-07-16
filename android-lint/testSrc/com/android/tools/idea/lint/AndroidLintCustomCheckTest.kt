/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import org.hamcrest.CoreMatchers.hasItem
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AndroidLintCustomCheckTest {

  @get:Rule
  val myProjectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    myProjectRule.fixture.testDataPath = TestDataPaths.TEST_DATA_ROOT
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(true)

    ApplicationManager.getApplication().invokeAndWait {

      // Load project (runs Gradle sync).
      myProjectRule.load("projects/lintCustomChecks")

      // Publish the library-remote module to a local maven repository so we can get custom Lint checks from its AAR.
      myProjectRule.invokeTasks(":library-remote:publish")

      // We are required (above) to run Gradle sync prior to running :library-remote:uploadArchives, but that means that the app
      // module cannot yet have had a dependency on the remote library (otherwise Gradle sync would fail!). So,
      // we have to add the dependency dynamically afterwards and re-run Gradle sync.
      WriteCommandAction.runWriteCommandAction(myProjectRule.project) {
        val buildFile = myProjectRule.project.baseDir.findFileByRelativePath("app/build.gradle")!!
        val doc = FileDocumentManager.getInstance().getDocument(buildFile)!!
        val lineNumber = doc.getLineNumber(doc.text.indexOf("dependencies {"))
        doc.insertString(doc.getLineEndOffset(lineNumber), "implementation 'com.example.google:library-remote:1.0'")
        FileDocumentManager.getInstance().saveDocument(doc)
      }

      myProjectRule.requestSyncAndWait()
      GradleBuildInvoker.getInstance(myProjectRule.project).generateSources(ModuleManager.getInstance(myProjectRule.project).modules)
    }
  }

  @After
  fun tearDown() {
    AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
  }

  private fun doTest(filePath: String, expectedWarning: String) {
    ApplicationManager.getApplication().invokeAndWait {
      val file = myProjectRule.project.baseDir.findFileByRelativePath(filePath)!!
      myProjectRule.fixture.openFileInEditor(file)
      val warnings = myProjectRule.fixture.doHighlighting(HighlightSeverity.WARNING).map { it.description }
      assertThat(warnings, hasItem(expectedWarning))
    }
  }

  @Test
  fun dependencyOnLocalModuleWithLintChecks() {
    doTest(
      "app/src/main/java/com/example/app/MyList.java",
      "Do not implement java.util.List directly")
  }

  @Test
  fun dependencyOnLocalLibraryExportingLintChecks() {
    doTest(
      "app/src/main/AndroidManifest.xml",
      "Should not specify <activity>."
    )
  }

  @Test
  fun dependencyOnRemoteLibraryExportingLintChecks() {
    doTest(
      "app/src/main/java/com/example/app/MySet.java",
      "Do not implement java.util.Set directly")
  }
}