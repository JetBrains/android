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

import com.android.tools.idea.projectsystem.gradle.getAndroidTestModule
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.getMainModule
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.util.toIoFile
import com.android.tools.idea.util.toVirtualFile
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import java.io.File
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/**
 * Legacy projects (without the model) have no concept of test resources, so for now this needs to be tested using Gradle.
 *
 * We use the [TestProjectPaths.PROJECT_WITH_APPAND_LIB] project and make `app` have an `androidTestImplementation` dependency on `lib`.
 */
sealed class TestRClassesTest {
  val projectRule = AndroidGradleProjectRule()
  @get:Rule
  val rule = RuleChain.outerRule(projectRule).around(EdtRule())
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }

  protected open val disableNonTransitiveRClass = false

  lateinit var projectRootDirectory: VirtualFile

  @Before
  fun setup() {
    projectRule.loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB) { projectRoot ->
      projectRootDirectory = projectRoot.toVirtualFile()!!

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
    }
    fixture.allowTreeAccessForAllFiles()
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

    fixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.java")

    CodeInsightTestUtil.gotoImplementation(fixture.editor, null)

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

    fixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.kt")

    CodeInsightTestUtil.gotoImplementation(fixture.editor, null)

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

    fixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.java")

    CodeInsightTestUtil.gotoImplementation(fixture.editor, null)

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

    fixture.configureFromExistingVirtualFile(androidTest)

    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    assertThat(fileEditorManager.openFiles).hasLength(1)
    assertThat(fileEditorManager.currentFile?.name).isEqualTo("RClassAndroidTest.kt")

    CodeInsightTestUtil.gotoImplementation(fixture.editor, null)

    // Verify that the correct file opened up, and that the caret is moved to the correct definition in the file.
    assertThat(fileEditorManager.openFiles).hasLength(2)
    assertThat(fileEditorManager.currentFile?.path).endsWith("app/src/main/res/values/strings.xml")

    val selectedEditor = fileEditorManager.selectedTextEditor!!
    val textAfterCaret =
      selectedEditor.document.text.substring(selectedEditor.caretModel.offset)
    assertThat(textAfterCaret).startsWith("app_name\">projectWithAppandLib</string>")
  }

  protected fun setAndroidResourcesEnablementInLibAndSync(androidResourcesEnabled: Boolean) {
    val gradleFile = File(projectRootDirectory.toIoFile(), "lib/build.gradle")
    val gradleFileText = gradleFile.readText()

    val searchString = "buildFeatures {"
    val insertionIndex = gradleFileText.indexOf("buildFeatures {") + searchString.length

    val updatedGradleFileText =
      """${gradleFileText.substring(0, insertionIndex)}
          androidResources ${if (androidResourcesEnabled) "true" else "false"}
      ${gradleFileText.substring(insertionIndex)}
      """.trimIndent()

    gradleFile.writeText(updatedGradleFileText)

    projectRule.requestSyncAndWait()
  }

  fun doTestLibAndroidResourcesEnabled() {
    setAndroidResourcesEnablementInLibAndSync(androidResourcesEnabled = true)

    val file = createFile(
      projectRootDirectory,
      "lib/src/main/java/com/example/projectwithappandlib/lib/RClassInLib.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassInLib {
          void useResources() {
             int[] id = new int[] {
              com.example.projectwithappandlib.lib.R.string.libResource,
              // The string that isn't in strings.xml should be highlighted as an error.
              com.example.projectwithappandlib.lib.R.string.${"resource_that_does_not_exist" highlightedAs ERROR},
             };
          }
      }
      """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(file)
    fixture.checkHighlighting()
  }

  fun doTestLibAndroidResourcesDisabled() {
    setAndroidResourcesEnablementInLibAndSync(androidResourcesEnabled = false)

    val file = createFile(
      projectRootDirectory,
      "lib/src/main/java/com/example/projectwithappandlib/lib/RClassInLib.java",
      // language=java
      """
      package com.example.projectwithappandlib.lib;

      public class RClassInLib {
          void useResources() {
             int[] id = new int[] {
              // Since resources are disabled for the library, the `R` class should be highlighted as an error.
              com.example.projectwithappandlib.lib.${"R" highlightedAs ERROR}.string.libResource,
              com.example.projectwithappandlib.lib.${"R" highlightedAs ERROR}.string.resource_that_does_not_exist,
             };
          }
      }
      """.trimIndent()
    )

    fixture.configureFromExistingVirtualFile(file)
    fixture.checkHighlighting()
  }

  companion object {
    val ALL_R_CLASS_NAMES = listOf(
      "android.arch.core.R",
      "android.arch.lifecycle.livedata.core.R",
      "android.arch.lifecycle.livedata.R",
      "android.arch.lifecycle.R",
      "android.arch.lifecycle.viewmodel.R",
      "android.support.graphics.drawable.R",
      "android.support.v7.appcompat.R",
      "android.support.asynclayoutinflater.R",
      "android.support.coordinatorlayout.R",
      "android.support.cursoradapter.R",
      "android.support.customview.R",
      "android.support.documentfile.R",
      "android.support.drawerlayout.R",
      "android.support.interpolator.R",
      "android.support.loader.R",
      "android.support.localbroadcastmanager.R",
      "android.support.print.R",
      "android.support.slidingpanelayout.R",
      "android.support.compat.R",
      "android.support.coreui.R",
      "android.support.coreutils.R",
      "android.support.fragment.R",
      "android.support.graphics.drawable.R",
      "android.support.swiperefreshlayout.R",
      "androidx.versionedparcelable.R",
      "android.support.v7.viewpager.R",
      "com.example.projectwithappandlib.lib.test.R",
      "com.example.projectwithappandlib.lib.test.R",
      "com.example.projectwithappandlib.lib.R",
      "com.example.projectwithappandlib.lib.R",
      "com.example.projectwithappandlib.app.R",
      "com.example.projectwithappandlib.app.R",
      "com.example.projectwithappandlib.lib.R",
      "com.example.projectwithappandlib.lib.R",
      "com.example.projectwithappandlib.app.R",
      "com.example.projectwithappandlib.app.R",
      "com.example.projectwithappandlib.app.test.R",
      "com.example.projectwithappandlib.app.test.R",
    )

    val ADDITIONAL_R_CLASS_NAMES = listOf(
      "android.support.v7.cardview.R",
      "android.support.design.R",
      "android.support.v7.recyclerview.R",
      "android.support.transition.R",
    )
  }
}

/**
 * Tests to verify that the AndroidResolveScopeEnlarger cache is invalidated when gradle sync is triggered to use using
 * android.nonTransitiveRClass=true gradle property.
 *
 * @see org.jetbrains.android.AndroidResolveScopeEnlarger
 */
@RunsInEdt
class EnableNonTransitiveRClassTest: TestRClassesTest() {
  @Test
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

    fixture.configureFromExistingVirtualFile(normalClass)

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsExactly("activity_main", "fragment_foo", "fragment_main",
                                                               "fragment_navigation_drawer", "support_simple_spinner_dropdown_item",
                                                               "class")

    val projectRoot = File(FileUtil.toSystemDependentName(project.basePath!!))
    File(projectRoot, "gradle.properties").appendText("android.nonTransitiveRClass=true")
    projectRule.requestSyncAndWait()
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    // Verifies that the AndroidResolveScopeEnlarger cache has been updated, support_simple_spinner_dropdown_item is present but only as
    // part of a NonTransitiveResourceFieldLookupElement, with a package name.
    fixture.completeBasic()
    assertThat(fixture.lookupElements!!.firstOrNull {
      it.toPresentableText() == "support_simple_spinner_dropdown_item  (android.support.v7.appcompat) Int"
    }).isNotNull()
    assertThat(fixture.lookupElementStrings).containsAllOf("activity_main", "fragment_foo",
                                                             "fragment_main", "fragment_navigation_drawer", "class")
  }
}

@RunsInEdt
class TransitiveTestRClassesTest : TestRClassesTest() {

  override val disableNonTransitiveRClass = true

  @Test
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

    fixture.configureFromExistingVirtualFile(androidTest)
    fixture.checkHighlighting()

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsAllOf(
      "appTestResource", // app test resources
      "libResource", // lib main resources
      "password_toggle_content_description" // androidTestImplementation AAR
    )

    // Private resources are filtered out.
    assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
  }

  @Test
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

    fixture.configureFromExistingVirtualFile(androidTest)
    AndroidGradleTests.waitForSourceFolderManagerToProcessUpdates(project)
    fixture.checkHighlighting()

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsAllOf(
      "libTestResource", // lib test resources
      "libResource", // lib main resources
      "password_toggle_content_description" // androidTestImplementation AAR
    )

    // Private resources are filtered out.
    assertThat(fixture.lookupElementStrings).doesNotContain("abc_action_bar_home_description")
  }

  @Test
  fun testLibAndroidResourcesEnabled() {
    doTestLibAndroidResourcesEnabled()
  }

  @Test
  fun testLibAndroidResourcesDisabled() {
    doTestLibAndroidResourcesDisabled()
  }

  @Test
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

    fixture.configureFromExistingVirtualFile(unitTest)
    fixture.checkHighlighting()

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

    fixture.configureFromExistingVirtualFile(normalClass)
    fixture.checkHighlighting()
  }

  @Test
  fun testClassesDefinedByModule() {
    val appModule = project.findAppModule()
    val libModule = project.findModule("lib")
    val service = ProjectLightResourceClassService.getInstance(project)

    assertThat(service.getLightRClassesDefinedByModule(appModule.getMainModule()).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(appModule.getAndroidTestModule()!!).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.test.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(libModule.getMainModule()).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.lib.R",
    )
    assertThat(service.getLightRClassesDefinedByModule(libModule.getAndroidTestModule()!!).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.lib.test.R",
    )
  }

  @Test
  fun testAllClasses() {
    val service = ProjectLightResourceClassService.getInstance(project)
    val allClassNames = service.allLightRClasses.map { it.qualifiedName }
    assertThat(allClassNames).containsExactlyElementsIn(ALL_R_CLASS_NAMES)
  }

  @Test
  fun testUseScope() {
    val appTest = fixture.loadNewFile(
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

    val libTest = fixture.loadNewFile(
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

    val appTestRClass = fixture.findClass("com.example.projectwithappandlib.app.test.R", appTest)
    assertThat(appTestRClass).isNotNull()
    val appTestScope = appTestRClass!!.useScope as GlobalSearchScope
    assertThat(appTestScope.isSearchInLibraries).isFalse()
    assertThat(
      appTestScope.contains(fixture.findClass("com.example.projectwithappandlib.app.RClassAndroidTest")!!.containingFile.virtualFile))
      .isTrue()

    val libTestRClass = fixture.findClass("com.example.projectwithappandlib.lib.test.R", libTest)
    assertThat(libTestRClass).isNotNull()
    val libTestScope = libTestRClass!!.useScope as GlobalSearchScope
    assertThat(libTestScope.isSearchInLibraries).isFalse()
    assertThat(
      libTestScope.contains(fixture.findClass("com.example.projectwithappandlib.lib.RClassAndroidTest")!!.containingFile.virtualFile))
      .isTrue()
  }

  @Test
  fun testNavigateToDefinitionJavaToAppTestResource() {
    doTestNavigateToDefinitionJavaToAppTestResource()
  }

  @Test
  fun testNavigateToDefinitionKotlinToAppTestResource() {
    doTestNavigateToDefinitionKotlinToAppTestResource()
  }

  @Test
  fun testNavigateToDefinitionJavaToAppResource() {
    doTestNavigateToDefinitionJavaToAppResource()
  }

  @Test
  fun testNavigateToDefinitionKotlinToAppResource() {
    doTestNavigateToDefinitionKotlinToAppResource()
  }
}

@RunsInEdt
class NonTransitiveTestRClassesTest : TestRClassesTest() {
  override fun modifyGradleFiles(projectRoot: File) {
    super.modifyGradleFiles(projectRoot)
    File(projectRoot, "gradle.properties").appendText("android.nonTransitiveRClass=true")
  }

  @Test
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

    fixture.configureFromExistingVirtualFile(androidTest)
    fixture.checkHighlighting()

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsExactly("appTestResource", "anotherAppTestResource", "class")
  }

  @Test
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
    fixture.configureFromExistingVirtualFile(androidTest)
    fixture.checkHighlighting()

    fixture.completeBasic()
    assertThat(fixture.lookupElementStrings).containsExactly("libTestResource", "anotherLibTestResource", "class")
  }

  @Test
  fun testAllClasses() {
    val service = ProjectLightResourceClassService.getInstance(project)
    val allClassNames = service.allLightRClasses.map { it.qualifiedName }
    assertThat(allClassNames).containsExactlyElementsIn(ALL_R_CLASS_NAMES + ADDITIONAL_R_CLASS_NAMES)
  }

  @Test
  fun testLibAndroidResourcesEnabled() {
    doTestLibAndroidResourcesEnabled()
  }

  @Test
  fun testLibAndroidResourcesDisabled() {
    doTestLibAndroidResourcesDisabled()
  }

  @Test
  fun testNavigateToDefinitionJavaToAppTestResource() {
    doTestNavigateToDefinitionJavaToAppTestResource()
  }

  @Test
  fun testNavigateToDefinitionKotlinToAppTestResource() {
    doTestNavigateToDefinitionKotlinToAppTestResource()
  }

  @Test
  fun testNavigateToDefinitionJavaToAppResource() {
    doTestNavigateToDefinitionJavaToAppResource()
  }

  @Test
  fun testNavigateToDefinitionKotlinToAppResource() {
    doTestNavigateToDefinitionKotlinToAppResource()
  }
}

@RunsInEdt
class ConstantIdsRClassesTest : TestRClassesTest() {
  override fun modifyGradleFiles(projectRoot: File) {
    super.modifyGradleFiles(projectRoot)
    File(projectRoot, "gradle.properties").appendText("android.nonFinalResIds=false")
  }

  @Test
  fun testAllClasses() {
    val service = ProjectLightResourceClassService.getInstance(project)
    val allClassNames = service.allLightRClasses.map { it.qualifiedName }
    assertThat(allClassNames).containsExactlyElementsIn(ALL_R_CLASS_NAMES + ADDITIONAL_R_CLASS_NAMES)
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

private fun CodeInsightTestFixture.findClass(name: String) =
  (this as? JavaCodeInsightTestFixture)?.findClass(name)
private fun CodeInsightTestFixture.findClass(name: String, context: PsiElement) =
  (this as? JavaCodeInsightTestFixture)?.findClass(name, context)