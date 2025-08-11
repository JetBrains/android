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
package com.android.tools.idea.res

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.replaceText
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiClass
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.VfsTestUtil
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests focussing on users setting library module package name via the new build.gradle "namespace" and "testNamespace" DSL.
 */
@RunsInEdt
class GradleBuildFileNamespaceRClassesTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }
  
  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.PROJECT_WITH_APP_AND_LIB_DEPENDENCY) { projectRoot ->

      VfsTestUtil.createFile(
        project.guessProjectDir()!!,
        "app/src/androidTest/res/values/strings.xml",
        // language=xml
        """
          <resources>
            <string name='appTestResource'>app test resource</string>
            <string name='anotherAppTestResource'>another app test resource</string>
            <color name='appTestColor'>#000000</color>
          </resources>
        """.trimIndent()
      )

      VfsTestUtil.createFile(
        project.guessProjectDir()!!,
        "lib/src/androidTest/res/values/strings.xml",
        // language=xml
        """
          <resources>
            <string name='libTestResource'>lib test resource</string>
            <string name='anotherLibTestResource'>another lib test resource</string>
            <color name='libTestColor'>#000000</color>
          </resources>
        """.trimIndent()
      )

      VfsTestUtil.createFile(
        project.guessProjectDir()!!,
        "lib/src/main/res/values/strings.xml",
        // language=xml
        """
          <resources>
            <string name='libResource'>lib resource</string>
          </resources>
        """.trimIndent()
      )

      VfsTestUtil.createFile(
        project.guessProjectDir()!!,
        "lib/src/main/java/com/example/foo/libmodule/BasicActivity.java",
        // language=Java
        """
          package com.example.foo.libmodule;

          import android.app.Activity;

          public class BasicActivity extends Activity {
          }
        """.trimIndent()
      )

      // Setting the library module namespace via the build.gradle, which should take priority over the package name in AndroidManifest.xml
      File(projectRoot, "lib/build.gradle").appendText("""
        android {
          namespace "com.example.foo.libmodule"
          testNamespace "com.example.foo.libmodule.test"
        }
        """.trimIndent())
    }
    fixture.allowTreeAccessForAllFiles()
    waitForResourceRepositoryUpdates(project.findAppModule())

    fixture.enableInspections(AndroidDomInspection())
  }

  // Regression test for b/202006729
  @Test
  fun testManifestActivityXml() {
    val virtualFile = project.guessProjectDir()!!.findFileByRelativePath("lib/src/main/AndroidManifest.xml")
    fixture.openFileInEditor(virtualFile!!)

    // Checking that the basic AndroidManifest.xml file resolves correctly.
    fixture.checkHighlighting()


    // Add activity to the Manifest tag.
    fixture.moveCaret("""xmlns:android="http://schemas.android.com/apk/res/android">|""")
    fixture.editor.executeAndSave {
      insertText("""

        <application>
          <activity
              android:name="com.example.foo.libmodule.BasicActivity" />
        </application>
      """.trimIndent())
    }
    fixture.checkHighlighting()

    fixture.checkHighlighting()

    // Remove the package prefix to the class name
    fixture.editor.executeAndSave {
      replaceText(
        """com.example.foo.libmodule.BasicActivity""",
        """.BasicActivity""")
    }

    fixture.checkHighlighting()
    fixture.moveCaret("Basic|Activity")
    val elementAtCaret = fixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(PsiClass::class.java)
    assertThat((elementAtCaret as PsiClass).name).isEqualTo("BasicActivity")
  }

  @Test
  fun testAppResources() {
    val rClassUsageFile = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/src/main/java/com/example/projectwithappandlib/app/RClassAndroid.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroid {
          void useResources() {
             int[] id = new int[] {
              // Module R class resources work as normal
              //    resource from app module:
              R.string.app_name,
              //    resource from lib module not recognized because of non-transitive R classes
              R.string.${"libResource" highlightedAs HighlightSeverity.ERROR},

              // Fully qualified reference to lib module with package name from manifest:
              com.example.projectwithappandlib.lib.${"R" highlightedAs HighlightSeverity.ERROR}.string.libResource,
              // Fully qualified reference to lib module with package name from build.gradle DSL:
              com.example.foo.libmodule.R.string.libResource
             };
          }
      }
      """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(rClassUsageFile)
    fixture.checkHighlighting()
  }

  @Test
  fun testAppTestResources() {
    val androidTest = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              // Accessing resources transitively via the app module R Class should fail with non-transitive R classes enabled
              com.example.projectwithappandlib.app.test.R.string.${caret}appTestResource,
              com.example.projectwithappandlib.app.test.R.string.${"libResource" highlightedAs HighlightSeverity.ERROR},
              com.example.projectwithappandlib.app.test.R.color.${"primary_material_dark" highlightedAs HighlightSeverity.ERROR},

              // Main resources are not in the test R class:
              com.example.projectwithappandlib.app.test.R.string.${"app_name" highlightedAs HighlightSeverity.ERROR},

              // Main resources from dependencies are not in R class:
              com.example.projectwithappandlib.app.test.R.string.${"libTestResource" highlightedAs HighlightSeverity.ERROR},

              // Fully qualified reference to lib module with package name from build.gradle DSL:
              com.example.foo.libmodule.R.string.libResource,

              androidx.appcompat.R.color.primary_material_dark,

              R.string.app_name // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(androidTest)
    fixture.checkHighlighting()

    fixture.completeBasic()
    Truth.assertThat(fixture.lookupElementStrings).contains(
      "appTestResource" // app test resources
    )

    // Private resources are filtered out.
    Truth.assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
    Truth.assertThat(fixture.lookupElementStrings).doesNotContain("libResource")
  }

  @Test
  fun testLibTestResources() {
    val androidTest = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "lib/src/androidTest/java/com/example/projectwithappandlib/lib/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.foo.libmodule.test.R.string.${caret}libTestResource,
              
              androidx.appcompat.R.color.primary_material_dark,

              // Main resources are accessible:
              com.example.foo.libmodule.R.string.libResource,

              ${"R" highlightedAs HighlightSeverity.ERROR}.string.libResource // Main R class is no longer accessible as the package name in build file is
                                                            // different to the current file.
             };
          }
      }
      """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(androidTest)
    fixture.checkHighlighting()

    fixture.completeBasic()
    Truth.assertThat(fixture.lookupElementStrings).contains(
      "libTestResource" // lib test resources
    )

    // Private resources are filtered out.
    Truth.assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
    Truth.assertThat(fixture.lookupElementStrings).doesNotContain("libResource")
  }
}
