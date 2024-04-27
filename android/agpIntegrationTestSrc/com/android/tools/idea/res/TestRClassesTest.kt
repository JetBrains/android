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
package com.android.tools.idea.res;

import com.android.tools.idea.projectsystem.getAndroidTestModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import java.io.File

/**
 * Legacy projects (without the model) have no concept of test resources, so for now this needs to be tested using Gradle.
 *
 * We use the [TestProjectPaths.PROJECT_WITH_APPAND_LIB] project and make `app` have an `androidTestImplementation` dependency on `lib`.
 */
sealed class TestRClassesTest : AndroidGradleTestCase() {

  protected open val disableNonTransitiveRClass = false

  protected val projectRootDirectory by lazy { project.guessProjectDir()!! }

  override fun setUp() {
    super.setUp()

    val projectRoot = prepareProjectForImport(TestProjectPaths.PROJECT_WITH_APPAND_LIB)

    createFile(
      projectRootDirectory,
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

    createFile(
      projectRootDirectory,
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

    createFile(
      projectRootDirectory,
      "lib/src/main/res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name='libResource'>lib resource</string>
        </resources>
      """.trimIndent()
    )

    if (disableNonTransitiveRClass) {
      File(projectRootDirectory.toIoFile(), "gradle.properties").appendText(
        "android.nonTransitiveRClass=false"
      )
    }

    modifyGradleFiles(projectRoot)
    importProject()
    prepareProjectForTest(project, null)
    myFixture.allowTreeAccessForAllFiles()
  }

  open fun modifyGradleFiles(projectRoot: File) {
    File(projectRoot, "app/build.gradle").appendText("""
        dependencies {
          androidTestImplementation project(':lib')
          androidTestImplementation 'com.android.support:design:+'
        }
      """)

    File(projectRoot, "lib/build.gradle").appendText("""
        dependencies {
          androidTestImplementation 'com.android.support:design:+'
        }
      """)
  }

  protected fun doTestNavigateToDefinitionJavaToAppTestResource() {
    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int id = com.example.projectwithappandlib.app.test.R.string.${caret}appTestResource;
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.java")

    CodeInsightTestUtil.gotoImplementation(myFixture.editor, null)

    // Verify that the correct file opened up, and that the caret is moved to the correct definition in the file.
    assertThat(fileEditorManager.openFiles).hasLength(2)
    assertThat(fileEditorManager.currentFile?.path).endsWith("app/src/androidTest/res/values/strings.xml")

    val selectedEditor = fileEditorManager.selectedTextEditor!!
    val textAfterCaret =
      selectedEditor.document.text.substring(selectedEditor.caretModel.offset)
    assertThat(textAfterCaret).startsWith("appTestResource'>app test resource</string>")
  }

  protected fun doTestNavigateToDefinitionKotlinToAppTestResource() {
    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.kt",
      // language=kotlin
      """
      package com.example.projectwithappandlib.app

      class RClassAndroidTest {
          fun useResources() {
             val id = com.example.projectwithappandlib.app.test.R.string.${caret}appTestResource
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.kt")

    CodeInsightTestUtil.gotoImplementation(myFixture.editor, null)

    // Verify that the correct file opened up, and that the caret is moved to the correct definition in the file.
    assertThat(fileEditorManager.openFiles).hasLength(2)
    assertThat(fileEditorManager.currentFile?.path).endsWith("app/src/androidTest/res/values/strings.xml")

    val selectedEditor = fileEditorManager.selectedTextEditor!!
    val textAfterCaret =
      selectedEditor.document.text.substring(selectedEditor.caretModel.offset)
    assertThat(textAfterCaret).startsWith("appTestResource'>app test resource</string>")
  }

  protected fun doTestNavigateToDefinitionJavaToAppResource() {
    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int id = com.example.projectwithappandlib.app.R.string.${caret}app_name;
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.java")

    CodeInsightTestUtil.gotoImplementation(myFixture.editor, null)

    // Verify that the correct file opened up, and that the caret is moved to the correct definition in the file.
    assertThat(fileEditorManager.openFiles).hasLength(2)
    assertThat(fileEditorManager.currentFile?.path).endsWith("app/src/main/res/values/strings.xml")

    val selectedEditor = fileEditorManager.selectedTextEditor!!
    val textAfterCaret =
      selectedEditor.document.text.substring(selectedEditor.caretModel.offset)
    assertThat(textAfterCaret).startsWith("app_name\">projectWithAppandLib</string>")
  }

  protected fun doTestNavigateToDefinitionKotlinToAppResource() {
    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.kt",
      // language=kotlin
      """
      package com.example.projectwithappandlib.app

      class RClassAndroidTest {
          fun useResources() {
             val id = com.example.projectwithappandlib.app.R.string.${caret}app_name
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.kt")

    CodeInsightTestUtil.gotoImplementation(myFixture.editor, null)

    // Verify that the correct file opened up, and that the caret is moved to the correct definition in the file.
    assertThat(fileEditorManager.openFiles).hasLength(2)
    assertThat(fileEditorManager.currentFile?.path).endsWith("app/src/main/res/values/strings.xml")

    val selectedEditor = fileEditorManager.selectedTextEditor!!
    val textAfterCaret =
      selectedEditor.document.text.substring(selectedEditor.caretModel.offset)
    assertThat(textAfterCaret).startsWith("app_name\">projectWithAppandLib</string>")
  }
}

/**
 * Tests to verify that the AndroidResolveScopeEnlarger cache is invalidated when gradle sync is triggered to use using
 * android.nonTransitiveRClass=true gradle property.
 *
 * @see AndroidResolveScopeEnlarger
 */
class EnableNonTransitiveRClassTest: TestRClassesTest() {
  fun testNonTransitive_withoutRestart() {
    val normalClass = createFile(
      projectRootDirectory,
      "app/src/main/java/com/example/projectwithappandlib/app/NormalClass.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class NormalClass {
          void useResources() {
             int layout = R.layout.${caret};
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(normalClass)

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("activity_main", "fragment_foo", "fragment_main",
                                                               "fragment_navigation_drawer", "support_simple_spinner_dropdown_item",
                                                               "class")

    val projectRoot = File(FileUtilRt.toSystemDependentName(project.basePath!!))
    File(projectRoot, "gradle.properties").appendText("android.nonTransitiveRClass=true")
    requestSyncAndWait()

    // Verifies that the AndroidResolveScopeEnlarger cache has been updated, support_simple_spinner_dropdown_item is present but only as
    // part of a NonTransitiveResourceFieldLookupElement, with a package name.
    myFixture.completeBasic()
    assertThat(myFixture.lookupElements!!.firstOrNull {
      it.toPresentableText() == "support_simple_spinner_dropdown_item  (android.support.v7.appcompat) Int"
    }).isNotNull()
    assertThat(myFixture.lookupElementStrings).containsAllOf("activity_main", "fragment_foo",
                                                             "fragment_main", "fragment_navigation_drawer", "class")
  }
}

class TransitiveTestRClassesTest : TestRClassesTest() {

  override val disableNonTransitiveRClass = true

  fun testAppTestResources() {
    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.app.test.R.string.${caret}appTestResource,
              com.example.projectwithappandlib.app.test.R.string.libResource,
              com.example.projectwithappandlib.app.test.R.color.primary_material_dark,

              // Main resources are not in the test R class:
              com.example.projectwithappandlib.app.test.R.string.${"app_name" highlightedAs ERROR},

              // Main resources from dependencies are not in R class:
              com.example.projectwithappandlib.app.test.R.string.${"libTestResource" highlightedAs ERROR},

              R.string.app_name // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf(
      "appTestResource", // app test resources
      "libResource", // lib main resources
      "password_toggle_content_description" // androidTestImplementation AAR
    )

    // Private resources are filtered out.
    assertThat(myFixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
  }

  fun testLibTestResources() {
    val androidTest = createFile(
      projectRootDirectory,
      "lib/src/androidTest/java/com/example/projectwithappandlib/lib/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.lib.test.R.string.${caret}libTestResource,
              com.example.projectwithappandlib.lib.test.R.color.primary_material_dark,

              // Main resources are in the test R class:
              com.example.projectwithappandlib.lib.test.R.string.libResource,

              R.string.libResource // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf(
      "libTestResource", // lib test resources
      "libResource", // lib main resources
      "password_toggle_content_description" // androidTestImplementation AAR
    )

    // Private resources are filtered out.
    assertThat(myFixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
  }

  fun testResolveScope() {
    val unitTest = createFile(
      projectRootDirectory,
      "app/src/test/java/com/example/projectwithappandlib/app/RClassUnitTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassUnitTest {
          void useResources() {
             // Test R class is not in scope.
             int id = com.example.projectwithappandlib.app.test.${"R" highlightedAs ERROR}.string.appTestResource;
             // The test resource does not leak to the main R class.
             int id2 = com.example.projectwithappandlib.app.test.${"appTestResource" highlightedAs ERROR};
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(unitTest)
    myFixture.checkHighlighting()

    val normalClass = createFile(
      projectRootDirectory,
      "app/src/main/java/com/example/projectwithappandlib/app/NormalClass.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class NormalClass {
          void useResources() {
             // Test R class is not in scope.
             int id = com.example.projectwithappandlib.app.test.${"R" highlightedAs ERROR}.string.appTestResource;
             // The test resource does not leak to the main R class.
             int id2 = com.example.projectwithappandlib.app.test.${"appTestResource" highlightedAs ERROR};
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(normalClass)
    myFixture.checkHighlighting()
  }

  fun testClassesDefinedByModule() {
    val appModule = getModule("app")
    val libModule = getModule("lib")
    val service = ProjectLightResourceClassService.getInstance(project)

    assertThat(service.getLightRClassesDefinedByModule(appModule).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(appModule.getAndroidTestModule()!!).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.test.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(libModule).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.lib.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(libModule.getAndroidTestModule()!!).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.lib.test.R",
    )
  }

  fun testUseScope() {
    val appTest = myFixture.loadNewFile(
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.app.test.R.string.appTestResource,
              com.example.projectwithappandlib.app.test.R.string.libResource,
              com.example.projectwithappandlib.app.test.R.color.primary_material_dark,

              // Main resources are not in the test R class:
              com.example.projectwithappandlib.app.test.R.string.app_name,

              // Main resources from dependencies are not in R class:
              com.example.projectwithappandlib.app.test.R.string.libTestResource,

              R.string.app_name // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )

    val libTest = myFixture.loadNewFile(
      "lib/src/androidTest/java/com/example/projectwithappandlib/lib/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.lib.test.R.string.libTestResource,
              com.example.projectwithappandlib.lib.test.R.color.primary_material_dark,

              // Main resources are in the test R class:
              com.example.projectwithappandlib.lib.test.R.string.libResource,

              R.string.libResource // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val appTestRClass = myFixture.findClass("com.example.projectwithappandlib.app.test.R", appTest)
    assertThat(appTestRClass).isNotNull()
    val appTestScope = appTestRClass!!.useScope as GlobalSearchScope
    assertFalse(appTestScope.isSearchInLibraries)
    assertTrue(
      appTestScope.contains(myFixture.findClass("com.example.projectwithappandlib.app.RClassAndroidTest").containingFile.virtualFile))

    val libTestRClass = myFixture.findClass("com.example.projectwithappandlib.lib.test.R", libTest)
    assertThat(libTestRClass).isNotNull()
    val libTestScope = libTestRClass!!.useScope as GlobalSearchScope
    assertFalse(libTestScope.isSearchInLibraries)
    assertTrue(
      libTestScope.contains(myFixture.findClass("com.example.projectwithappandlib.lib.RClassAndroidTest").containingFile.virtualFile))
  }

  fun testNavigateToDefinitionJavaToAppTestResource() {
    doTestNavigateToDefinitionJavaToAppTestResource()
  }

  fun testNavigateToDefinitionKotlinToAppTestResource() {
    doTestNavigateToDefinitionKotlinToAppTestResource()
  }

  fun testNavigateToDefinitionJavaToAppResource() {
    doTestNavigateToDefinitionJavaToAppResource()
  }

  fun testNavigateToDefinitionKotlinToAppResource() {
    doTestNavigateToDefinitionKotlinToAppResource()
  }
}

class NonTransitiveTestRClassesTest : TestRClassesTest() {
  override fun modifyGradleFiles(projectRoot: File) {
    super.modifyGradleFiles(projectRoot)
    File(projectRoot, "gradle.properties").appendText("android.nonTransitiveRClass=true")
  }

  fun testAppTestResources() {
    // Sanity check.
    assertThat(project.findAppModule().getModuleSystem().isRClassTransitive).named("transitive flag").isFalse()

    val androidTest = createFile(
      projectRootDirectory,
      "app/src/androidTest/java/com/example/projectwithappandlib/app/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.app;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.app.test.R.string.${caret}appTestResource,

              // Resources from test deps are not in the non-transitive test R class:
              com.example.projectwithappandlib.app.test.R.string.${"libResource" highlightedAs ERROR},
              com.example.projectwithappandlib.app.test.R.color.${"primary_material_dark" highlightedAs ERROR},

              // Main resources are not in the test R class:
              com.example.projectwithappandlib.app.test.R.string.${"app_name" highlightedAs ERROR},

              // Main resources from dependencies are not in R class:
              com.example.projectwithappandlib.app.test.R.string.${"libTestResource" highlightedAs ERROR},

              R.string.app_name // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(androidTest)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("appTestResource", "anotherAppTestResource", "class")
  }

  fun testLibTestResources() {
    val androidTest = createFile(
      projectRootDirectory,
      "lib/src/androidTest/java/com/example/projectwithappandlib/lib/RClassAndroidTest.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassAndroidTest {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.lib.test.R.string.${caret}libTestResource,

              // Resources from test deps are not in the non-transitive test R class:
              com.example.projectwithappandlib.lib.test.R.color.${"primary_material_dark" highlightedAs ERROR},

              // Main resources are not in the test R class:
              com.example.projectwithappandlib.lib.test.R.string.${"libResource" highlightedAs ERROR},

              R.string.libResource // Main R class is still accessible.
             };
          }
      }
      """.trimIndent()
    )
    myFixture.configureFromExistingVirtualFile(androidTest)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("libTestResource", "anotherLibTestResource", "class")
  }

  fun testNavigateToDefinitionJavaToAppTestResource() {
    doTestNavigateToDefinitionJavaToAppTestResource()
  }

  fun testNavigateToDefinitionKotlinToAppTestResource() {
    doTestNavigateToDefinitionKotlinToAppTestResource()
  }

  fun testNavigateToDefinitionJavaToAppResource() {
    doTestNavigateToDefinitionJavaToAppResource()
  }

  fun testNavigateToDefinitionKotlinToAppResource() {
    doTestNavigateToDefinitionKotlinToAppResource()
  }
}

/**
 * The default lookup string from CodeInsightTestFixture, only includes the item text, and not other aspects of the lookup element such
 * as tail text and type text. We want to verify certain aspects are present in the lookup elements.
 *
 * Be careful using this on Java elements such as resource fields, as the int constant value in the tail text is not always the same on
 * repeated test runs.
 */
private fun LookupElement.toPresentableText(): String {
  val presentation = LookupElementPresentation()
  renderElement(presentation)
  return "${presentation.itemText} ${presentation.tailText} ${presentation.typeText}"
}
