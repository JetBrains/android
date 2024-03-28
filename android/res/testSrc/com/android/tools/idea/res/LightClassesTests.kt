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
package com.android.tools.idea.res

import com.android.AndroidProjectTypes
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.testutils.TestUtils
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeAndroidGradlePluginProjectFlagsImpl
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.MergedManifestModificationListener
import com.android.tools.idea.model.TestAndroidModel.Companion.namespaced
import com.android.tools.idea.res.LightClassesTestBase.Companion.JAVA_RESOURCES_FILE
import com.android.tools.idea.res.LightClassesTestBase.Companion.KOTLIN_RESOURCE_FILE
import com.android.tools.idea.res.LightClassesTestBase.Companion.assertNoElementAtCaret
import com.android.tools.idea.res.LightClassesTestBase.Companion.resolveReferenceUnderCaret
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectStubBuilder
import com.android.tools.idea.testing.JavaModuleModelBuilder.Companion.rootModuleBuilder
import com.android.tools.idea.testing.buildAgpProjectFlagsStub
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.createAndroidProjectBuilderForDefaultTestProjectStructure
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.testing.gradleModule
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.testing.updatePrimaryManifest
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.util.androidFacet
import com.android.utils.executeWithRetries
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.usageView.UsageInfo
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.completion.AndroidNonTransitiveRClassJavaCompletionContributor
import org.jetbrains.android.completion.AndroidNonTransitiveRClassKotlinCompletionContributor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.resourceManagers.LocalResourceManager
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for the whole setup of light, in-memory R classes.
 *
 * @see ProjectSystemPsiElementFinder
 * @see ProjectLightResourceClassService
 */
sealed class LightClassesTestBase : AndroidTestCase() {

  fun resolveReferenceUnderCaret() = resolveReferenceUnderCaret(myFixture)

  fun assertNoElementAtCaret() {
    assertNoElementAtCaret(myFixture)
  }

  companion object {
    val JAVA_RESOURCES_FILE =
      // language=Java
      """
        package p1.p2;

        class RClassAndroidTest {
            public static void useResources() {
                // Both main R class references will contain all innner R class types, and all fields in completion
                int a = com.example.mylib.R.string.;
                int b = p1.p2.R.string.;
                int c = R.string.anotherLib;
                int d = Nothing.Inner.number;
            }
        }
        public class Nothing() {
            static class Inner() {
                public static int number = 0;
            }
        }"""
        .trimIndent()

    val KOTLIN_RESOURCE_FILE =
      // language=kotlin
      """
        package p1.p2

        class RClassAndroidTest {
            fun useResources() {
                listOf(
                    // Both main R class references will contain all innner R class types, and all fields in completion
                    com.example.mylib.R.string.,
                    p1.p2.R.string.,
                    R.string.anotherLib,
                    Nothing.Inner.number
                )
            }
        }
        class Nothing() {
            class Inner() {
                companion object {
                    val number = 0
                }
            }
        }"""
        .trimIndent()

    fun resolveReferenceUnderCaret(fixture: CodeInsightTestFixture): PsiElement {
      // This method is occasionally throwing, causing test flakiness. Nothing in the logs indicates
      // any errors; there's just no element under
      // the caret. It's unclear whether this is because something's gone wrong with reference
      // resolution, or whether we just needed to wait.
      // This is clearly a sub-optimal solution, but will help distinguish between the two cases. If
      // it does turn out that we need to wait,
      // we can investigate further if there's a better trigger to wait for than just looping and
      // retrying.
      return executeWithRetries<AssertionError, PsiElement>(
        duration = 2.seconds,
        sleepBetweenRetries = 100.milliseconds,
      ) {
        fixture.elementAtCaret
      }
    }

    fun assertNoElementAtCaret(fixture: CodeInsightTestFixture) {
      assertFailsWith<AssertionError> { fixture.elementAtCaret }
    }
  }
}

@RunWith(JUnit4::class)
@RunsInEdt
abstract class SingleModuleLightClassesTestBase {

  @get:Rule val androidProjectRule = AndroidProjectRule.withSdk().withKotlin().onEdt()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    } as JavaCodeInsightTestFixture
  }
  private val project by lazy { myFixture.project }
  private val myModule by lazy { myFixture.module }
  private val myFacet by lazy { myModule.androidFacet!! }

  abstract val packageNameForNamespacing: String?

  @Before
  fun setUp() {
    MergedManifestModificationListener.ensureSubscribed(project)
    myFixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      SdkConstants.FN_ANDROID_MANIFEST_XML,
    )
    myFixture.addFileToProject(
      "/res/values/values.xml",
      // language=xml
      """
      <resources>
        <string name="appString">Hello from app</string>
      </resources>
      """
        .trimIndent(),
    )

    if (packageNameForNamespacing != null) {
      AndroidModel.set(myFacet, namespaced(myFacet))
      updatePrimaryManifest(myFacet) { `package`.value = packageNameForNamespacing }
      LocalResourceManager.getInstance(myFacet.module)!!.invalidateAttributeDefinitions()
    }
  }

  @Test
  fun highlighting_java() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R.string.appString);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
  }

  @Test
  fun highlighting_kotlin() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              resources.getString(R.string.appString)
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
  }

  @Test
  fun topLevelClassCompletion_java() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(p1.p2.${caret});
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
  }

  @Test
  fun topLevelClassCompletion_kotlin() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              resources.getString(p1.p2.${caret})
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
  }

  @Test
  fun innerClassesCompletion_java() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R.${caret});
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("class", "string")
  }

  @Test
  fun innerClassesCompletion_kotlin() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              resources.getString(R.${caret})
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("string")
  }

  @Test
  fun resourceNamesCompletion_java() {
    myFixture.configureByText(
      "/src/p1/p2/MainActivity.java",
      // language=java
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R.string.${caret});
          }
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("appString", "class")
  }

  @Test
  fun resourceNamesCompletion_kotlin() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              resources.getString(R.string.${caret})
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("appString")
  }

  @Test
  fun styleableAttrResourceNamesCompletion_java() {
    myFixture.addFileToProject(
      "/res/values/styles.xml",
      // language=xml
      """
      <resources>
        <attr name="foo" format="boolean" />
        <declare-styleable name="LabelView">
            <attr name="foo"/>
            <attr name="android:maxHeight"/>
        </declare-styleable>
      </resources>
      """
        .trimIndent(),
    )
    myFixture.configureByText(
      "/src/p1/p2/MainActivity.java",
      // language=java
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              int styleableattr = R.styleable.${caret};
          }
      }
      """
        .trimIndent(),
    )

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsExactly("LabelView_android_maxHeight", "LabelView_foo", "LabelView", "class")
  }

  @Test
  fun manifestClass_java() {
    myFixture.loadNewFile(
      "/src/p1/p2/MainActivity.java",
      // language=java
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;
      import android.util.Log;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              Log.d("tag", Manifest.permission.${caret}SEND_MESSAGE);
          }
      }
      """
        .trimIndent(),
    )

    assertNoElementAtCaret(myFixture)

    updatePrimaryManifest(myFacet) { addPermission().name.value = "com.example.SEND_MESSAGE" }

    assertThat(resolveReferenceUnderCaret(myFixture)).isInstanceOf(AndroidLightField::class.java)
    myFixture.checkHighlighting()
  }

  @Test
  fun manifestClass_kotlin() {
    myFixture.loadNewFile(
      "/src/p1/p2/MainActivity.kt",
      // language=kotlin
      """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle
      import android.util.Log

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              Log.d("tag", Manifest.permission.${caret}SEND_MESSAGE)
          }
      }
      """
        .trimIndent(),
    )

    assertNoElementAtCaret(myFixture)

    updatePrimaryManifest(myFacet) { addPermission().name.value = "com.example.SEND_MESSAGE" }

    assertThat(resolveReferenceUnderCaret(myFixture)).isInstanceOf(AndroidLightField::class.java)
    myFixture.checkHighlighting()
  }

  @Test
  fun addingAar() {
    // Initialize the light classes code.
    assertThat(
        myFixture.javaFacade.findClass("p1.p2.R", GlobalSearchScope.everythingScope(project))
      )
      .isNotNull()

    addAarDependency(myFixture, myModule, "someLib", "com.example.someLib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText("int string some_lib_string 0x7f010001")
    }

    assertThat(
        myFixture.javaFacade.findClass(
          "com.example.someLib.R",
          GlobalSearchScope.everythingScope(project),
        )
      )
      .isNotNull()
  }

  @Test
  fun resourceRename() {
    val strings =
      myFixture.addFileToProject(
        "/res/values/strings.xml",
        // language=xml
        """
      <resources>
        <string name="f${caret}oo">foo</string>
      </resources>
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(strings.virtualFile)
    assertThat(
        myFixture.javaFacade
          .findClass("p1.p2.R.string", GlobalSearchScope.everythingScope(project))!!
          .fields
          .map(PsiField::getName)
      )
      .containsExactly("appString", "foo")

    myFixture.renameElementAtCaretUsingHandler("bar")
    waitForResourceRepositoryUpdates(myFacet, 2)

    assertThat(
        myFixture.javaFacade
          .findClass("p1.p2.R.string", GlobalSearchScope.everythingScope(project))!!
          .fields
          .map(PsiField::getName)
      )
      .containsExactly("appString", "bar")
  }

  @Test
  fun modificationTracking() {
    myFixture.addFileToProject("res/drawable/foo.xml", "<vector-drawable />")

    myFixture.loadNewFile(
      "src/p1/p2/MainActivity.java",
      // language=java
      """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              int id1 = R.drawable.foo;
              int id2 = R.drawable.${"bar" highlightedAs ERROR};
          }
      }
      """
        .trimIndent(),
    )

    // Sanity check:
    myFixture.checkHighlighting()

    // Make sure light classes pick up changes to repositories:
    val barXml = myFixture.addFileToProject("res/drawable/bar.xml", "<vector-drawable />")
    waitForResourceRepositoryUpdates(myFacet)
    assertThat(myFixture.doHighlighting(ERROR)).isEmpty()

    // Regression test for b/144585792. Caches in ResourceRepositoryManager can be dropped for
    // various reasons, we need to make sure we
    // keep track of changes even after new repository instances are created.
    StudioResourceRepositoryManager.getInstance(myFacet).resetAllCaches()
    runWriteAction { barXml.delete() }
    waitForResourceRepositoryUpdates(myFacet)
    assertThat(myFixture.doHighlighting(ERROR)).hasSize(1)
  }

  @Test
  fun containingClass() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R.string.${caret}appString);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    assertThat((resolveReferenceUnderCaret(myFixture) as? PsiField)?.containingClass?.name)
      .isEqualTo("string")
  }

  @Test
  fun usageInfos() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R.string.appString);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.moveCaret("|R.string.appString")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
    myFixture.moveCaret("R.|string.appString")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
    myFixture.moveCaret("R.string.|appString")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
  }

  @Test
  fun invalidManifest() {
    updatePrimaryManifest(myFacet) { `package`.value = "." }

    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(${"R" highlightedAs ERROR}${caret}.string.appString);
          }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    // The R class is not reachable from Java, but we should not crash trying to create an invalid
    // package name.
    myFixture.checkHighlighting()

    updatePrimaryManifest(myFacet) { `package`.value = "p1.p2" }

    // The first call to checkHighlighting removes error markers from the Document, so this makes
    // sure there are no errors.
    myFixture.checkHighlighting()
    val rClass = resolveReferenceUnderCaret(myFixture)
    assertThat(rClass).isInstanceOf(ModuleRClass::class.java)
    assertThat((rClass as ModuleRClass).qualifiedName).isEqualTo("p1.p2.R")
  }
}

class SingleModuleLightClassesTest : SingleModuleLightClassesTestBase() {
  override val packageNameForNamespacing = null
}

class SingleModuleNamespacedLightClassesTest : SingleModuleLightClassesTestBase() {
  override val packageNameForNamespacing = "p1.p2"
}

@RunWith(JUnit4::class)
@RunsInEdt
abstract class AppAndLibModulesLightClassesTestBase {

  abstract fun getAgpProjectFlags(
    builder: AndroidProjectStubBuilder
  ): IdeAndroidGradlePluginProjectFlagsImpl

  private val appModuleBuilder =
    AndroidModuleModelBuilder(
      gradlePath = ":app",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(
          IdeAndroidProjectType.PROJECT_TYPE_APP,
          "p1.p2",
        )
        .withAndroidModuleDependencyList { _ -> listOf(AndroidModuleDependency(":mylib", "debug")) }
        .withAgpProjectFlags { getAgpProjectFlags(this) },
    )

  private val libModuleBuilder =
    AndroidModuleModelBuilder(
      gradlePath = ":mylib",
      selectedBuildVariant = "debug",
      createAndroidProjectBuilderForDefaultTestProjectStructure(
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY,
          "com.example.mylib",
        )
        .withAgpProjectFlags { getAgpProjectFlags(this) },
    )

  @get:Rule
  val androidProjectRule =
    AndroidProjectRule.withAndroidModels(
        { dir ->
          assertThat(dir.resolve("app/src").mkdirs()).isTrue()
          assertThat(dir.resolve("app/res").mkdirs()).isTrue()
          assertThat(dir.resolve("mylib/src").mkdirs()).isTrue()
          assertThat(dir.resolve("mylib/res").mkdirs()).isTrue()
        },
        rootModuleBuilder,
        appModuleBuilder,
        libModuleBuilder,
      )
      .initAndroid(true)
      .onEdt()

  protected val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    } as JavaCodeInsightTestFixture
  }
  protected val project by lazy { myFixture.project }

  protected val appModule by lazy {
    project.gradleModule(":app", IdeModuleWellKnownSourceSet.MAIN)!!
  }
  protected val libModule by lazy {
    project.gradleModule(":mylib", IdeModuleWellKnownSourceSet.MAIN)!!
  }

  @Before
  fun setUp() {
    myFixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      SdkConstants.FN_ANDROID_MANIFEST_XML,
    )
    myFixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      "app/${SdkConstants.FN_ANDROID_MANIFEST_XML}",
    )
    myFixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      "mylib/${SdkConstants.FN_ANDROID_MANIFEST_XML}",
    )
    myFixture.addFileToProject(
      "/app/res/values/values.xml",
      // language=xml
      """
      <resources>
        <string name="appString">Hello from app</string>
        <string name="app.String">Hello from app</string>
        <string name="anotherAppString">Hello from app</string>
        <id name="basicID"/>
      </resources>
      """
        .trimIndent(),
    )

    updatePrimaryManifest(AndroidFacet.getInstance(libModule)!!) {
      `package`.value = "com.example.mylib"
    }

    myFixture.addFileToProject(
      "mylib/res/values/values.xml",
      // language=xml
      """
      <resources>
        <string name="libString">Hello from app</string>
        <string name="lib.String">Hello from app</string>
        <string name="lib.String_Foo">Hello from app</string>
        <string name="lib.String.Bar">Hello from app</string>
        <string name="anotherLibString">Hello from app</string>
      </resources>
      """
        .trimIndent(),
    )
  }
}

class AppAndLibModulesTransitiveLightClassesTest : AppAndLibModulesLightClassesTestBase() {

  override fun getAgpProjectFlags(builder: AndroidProjectStubBuilder) =
    builder.buildAgpProjectFlagsStub()

  /** Regression test for b/191952219. */
  @Test
  fun kotlinFlattenedResources() {
    val activity =
      myFixture.addFileToProject(
        "/app/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
      package p1.p2

      import android.app.Activity
      import android.os.Bundle

      class MainActivity : Activity() {
          override fun onCreate(savedInstanceState: Bundle?) {
              super.onCreate(savedInstanceState)
              R.string.appString
              R.string.app_String
              R.string.libString
              com.example.mylib.R.string.libString
              R.string.lib_String
              com.example.mylib.R.string.lib_String
              R.string.lib_String_Bar
              com.example.mylib.R.string.<caret>lib_String_Bar
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    val lookupStrings = myFixture.lookupElementStrings
    assertThat(lookupStrings).containsAllOf("libString", "lib_String", "lib_String_Bar")
  }

  /**
   * Regression test for b/191952219. In Java, fully qualified resources did not resolve from
   * library modules.
   */
  @Test
  fun javaFlattenedResources() {
    val activity =
      myFixture.addFileToProject(
        "/app/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              int a = R.string.appString;
              a = R.string.app_String;
              a = R.string.libString;
              a = com.example.mylib.R.string.libString;
              a = R.string.lib_String;
              a = com.example.mylib.R.string.lib_String;
              a = R.string.lib_String_Bar;
              a = com.example.mylib.R.string.<caret>lib_String_Bar;
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()

    myFixture.completeBasic()
    val lookupStrings = myFixture.lookupElementStrings
    assertThat(lookupStrings).containsAllOf("libString", "lib_String", "lib_String_Bar")
  }

  @Test
  fun useScope() {
    val activity =
      myFixture.loadNewFile(
        "/app/src/p1/p2/MainActivity.java",
        // language=java
        """
    package p1.p2;

    import android.app.Activity;
    import android.os.Bundle;

    public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getResources().getString(R.string.${caret}app_string);
        }
    }
    """
          .trimIndent(),
      )
    val appClassUseScope = myFixture.findClass("p1.p2.R", activity)!!.useScope as GlobalSearchScope
    assertThat(appClassUseScope.isSearchInLibraries).isFalse()
    assertThat(appClassUseScope.isSearchInModuleContent(libModule)).isFalse()
    assertThat(appClassUseScope.isSearchInModuleContent(appModule)).isTrue()
    assertThat(appClassUseScope.isSearchInModuleContent(appModule, true)).isTrue()

    val libClassUseScope =
      myFixture.findClass("com.example.mylib.R", activity)!!.useScope as GlobalSearchScope
    assertThat(libClassUseScope.isSearchInLibraries).isFalse()
    assertThat(libClassUseScope.isSearchInModuleContent(libModule)).isTrue()
    assertThat(libClassUseScope.isSearchInModuleContent(appModule)).isTrue()
    assertThat(libClassUseScope.isSearchInModuleContent(appModule, true)).isTrue()
  }
}

class AppAndLibModulesNonTransitiveLightClassesTest : AppAndLibModulesLightClassesTestBase() {

  override fun getAgpProjectFlags(builder: AndroidProjectStubBuilder) =
    builder.buildAgpProjectFlagsStub().copy(transitiveRClasses = false)

  /**
   * Testing completion elements provided via
   * [AndroidNonTransitiveRClassKotlinCompletionContributor]
   */
  @Test
  fun nonTransitiveKotlinCompletion() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.kt",
        KOTLIN_RESOURCE_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    // Check that the lib class reference only contains library resources
    myFixture.moveCaret("com.example.mylib.R.string.|,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString"))

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString"))

    // R class references with package prefix only get resources in original R class
    myFixture.moveCaret("p1.p2.R.string.|,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherAppString", "appString"))

    myFixture.moveCaret("p1.p2.R.|string.,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(ResourceType.values().filter { it.hasInnerClass }.map { it.getName() })

    myFixture.moveCaret("R.string.|anotherLib,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElements!!.map { it.toPresentableText() })
      .containsAllIn(
        arrayOf(
          "anotherLibString  (com.example.mylib) Int",
          "libString  (com.example.mylib) Int",
          "anotherAppString null Int",
          "appString null Int",
        )
      )

    // Check insert handler works correctly
    myFixture.moveCaret("R.string.anotherLib|,")
    myFixture.completeBasic()

    myFixture.moveCaret("p1.p2.R.string.|,")
    myFixture.type("anotherApp")
    myFixture.completeBasic()

    myFixture.checkResult(
      "/app/src/p1/p2/RClassAndroidTest.kt",
      // language=kotlin
      """
        package p1.p2

        class RClassAndroidTest {
            fun useResources() {
                listOf(
                    // Both main R class references will contain all innner R class types, and all fields in completion
                    com.example.mylib.R.string.,
                    p1.p2.R.string.anotherAppString,
                    com.example.mylib.R.string.anotherLibString,
                    Nothing.Inner.number
                )
            }
        }
        class Nothing() {
            class Inner() {
                companion object {
                    val number = 0
                }
            }
        }
      """
        .trimIndent(),
      true,
    )

    // Test for same module, different package
    val otherPackage =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p3/p4/RClassAndroidTest.kt",
        // language=kotlin
        """
      package p3.p4

      import com.example.mylib.R

      class RClassAndroidTest {
          fun useResources() {
              listOf(
                  // Both main R class references will contain all innner R class types, and all fields in completion
                  R.string.,
                  p1.p2.R.string.,
                  R.string.anotherApp,
                  0
              )
          }
      }"""
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(otherPackage)

    // R class references with package prefix only get resources in original R class
    myFixture.moveCaret("R.string.|,")
    myFixture.completeBasic()
    myFixture.type("anotherLib")
    myFixture.completeBasic()

    myFixture.moveCaret("p1.p2.R.string.|,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherAppString", "appString"))
    myFixture.type("anotherApp")
    myFixture.completeBasic()

    myFixture.moveCaret("R.string.|anotherApp,")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString", "anotherAppString", "appString"))

    myFixture.moveCaret("R.string.anotherApp|,")
    myFixture.completeBasic()

    myFixture.checkResult(
      "/app/src/p3/p4/RClassAndroidTest.kt",
      // language=kotlin
      """
      package p3.p4

      import com.example.mylib.R

      class RClassAndroidTest {
          fun useResources() {
              listOf(
                  // Both main R class references will contain all innner R class types, and all fields in completion
                  R.string.anotherLibString,
                  p1.p2.R.string.anotherAppString,
                  p1.p2.R.string.anotherAppString,
                  0
              )
          }
      }"""
        .trimIndent(),
      true,
    )
  }

  /**
   * Testing completion elements provided via [AndroidNonTransitiveRClassJavaCompletionContributor]
   */
  @Test
  fun nonTransitiveJavaCompletionWithPrefix() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.java",
        JAVA_RESOURCES_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    // R class references with package prefix only get resources in original R class
    myFixture.moveCaret("p1.p2.R.string.|;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherAppString", "appString"))

    myFixture.moveCaret("p1.p2.R.|string.;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(ResourceType.values().filter { it.hasInnerClass }.map { it.getName() })
  }

  @Test
  fun nonTransitiveJavaCompletionLibraryResources() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.java",
        JAVA_RESOURCES_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    // Check that the lib class reference only contains library resources
    myFixture.moveCaret("com.example.mylib.R.string.|;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString"))
  }

  @Test
  fun nonTransitiveJavaCompletionExtraElements() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.java",
        JAVA_RESOURCES_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    myFixture.moveCaret("R.string.|anotherLib;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElements!!.map { it.toPresentableText() })
      .containsAllIn(
        arrayOf("anotherLibString  (com.example.mylib) Int", "libString  (com.example.mylib) Int")
      )
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString", "anotherAppString", "appString"))
  }

  @Test
  fun nonTransitiveJavaCompletionInsertHandler() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.java",
        JAVA_RESOURCES_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    // Check insert handler works correctly
    myFixture.moveCaret("R.string.anotherLib|;")
    myFixture.completeBasic()

    myFixture.moveCaret("p1.p2.R.string.|;")
    myFixture.type("anotherApp")
    myFixture.completeBasic()

    myFixture.checkResult(
      "/app/src/p1/p2/RClassAndroidTest.java",
      // language=Java
      """
        package p1.p2;

        class RClassAndroidTest {
            public static void useResources() {
                // Both main R class references will contain all innner R class types, and all fields in completion
                int a = com.example.mylib.R.string.;
                int b = R.string.anotherAppString;
                int c = com.example.mylib.R.string.anotherLibString;
                int d = Nothing.Inner.number;
            }
        }
        public class Nothing() {
            static class Inner() {
                public static int number = 0;
            }
        }
      """
        .trimIndent(),
      true,
    )
  }

  @Test
  fun nonTransitiveJavaCompletionDifferentPackage() {
    val androidTest =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p1/p2/RClassAndroidTest.java",
        JAVA_RESOURCES_FILE,
      )

    myFixture.configureFromExistingVirtualFile(androidTest)

    // Test for same module, different package
    val otherPackage =
      createFile(
        project.guessProjectDir()!!,
        "/app/src/p3/p4/RClassAndroidTest.java",
        // language=Java
        """
      package p3.p4;

      import com.example.mylib.R;

      class RClassAndroidTest {
          public static void foo() {
              int a = R.string.;
              int b = p1.p2.R.string.;
              int c = R.string.anotherApp;
              int d = 0;
          }
      }"""
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(otherPackage)

    // R class references with package prefix only get resources in original R class
    myFixture.moveCaret("R.string.|;")
    myFixture.completeBasic()
    myFixture.type("anotherLib")
    myFixture.completeBasic()

    myFixture.moveCaret("p1.p2.R.string.|;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherAppString", "appString"))
    myFixture.type("anotherApp")
    myFixture.completeBasic()

    myFixture.moveCaret("R.string.|anotherApp;")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllIn(arrayOf("anotherLibString", "libString", "anotherAppString", "appString"))

    myFixture.moveCaret("R.string.anotherApp|;")
    myFixture.completeBasic()

    myFixture.checkResult(
      "/app/src/p3/p4/RClassAndroidTest.java",
      // language=Java
      """
      package p3.p4;

      import com.example.mylib.R;

      class RClassAndroidTest {
          public static void foo() {
              int a = R.string.anotherLibString;
              int b = p1.p2.R.string.anotherAppString;
              int c = p1.p2.R.string.anotherAppString;
              int d = 0;
          }
      }"""
        .trimIndent(),
      true,
    )
  }

  @Test
  fun nonTransitive() {
    myFixture.loadNewFile(
      "/app/src/p1/p2/MainActivity.java",
      // language=java
      """
    package p1.p2;

    import android.app.Activity;
    import android.os.Bundle;

    public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getResources().getString(R.string.${"libString" highlightedAs ERROR});
            getResources().getString(com.example.mylib.R.string.libString);
        }
    }
    """
        .trimIndent(),
    )

    myFixture.checkHighlighting()

    myFixture.moveCaret("(R.string.|libString")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllOf("appString", "anotherAppString", "class")

    myFixture.moveCaret("mylib.R.string.|libString")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsAllOf("libString", "anotherLibString", "class")
  }
}

class AppAndLibModulesNonFinalResourceIdsLightClassesTest : AppAndLibModulesLightClassesTestBase() {

  override fun getAgpProjectFlags(builder: AndroidProjectStubBuilder) =
    builder.buildAgpProjectFlagsStub().copy(applicationRClassConstantIds = false)

  @Test
  fun nonFinalResourceIds() {
    myFixture.addFileToProject("app/res/drawable/foo.xml", "<vector-drawable />")

    myFixture.loadNewFile(
      "/app/src/p1/p2/MainActivity.java",
      // language=java
      """
    package p1.p2;

    import android.app.Activity;
    import android.os.Bundle;

    public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int id = R.id.basi${caret}cID;
            switch (id) {
                case <error descr="Constant expression required">R.id.basicID</error>: break;
                case <error descr="Constant expression required">R.string.appString</error>: break;
                case <error descr="Constant expression required">R.drawable.foo</error>: break;
            }
        }
    }
    """
        .trimIndent(),
    )

    myFixture.checkHighlighting()

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
    assertThat(
        (elementAtCaret as PsiModifierListOwner)
          .modifierList!!
          .hasExplicitModifier(PsiModifier.FINAL)
      )
      .isFalse()
  }
}

class AppAndLibModulesFinalResourceIdsLightClassesTest : AppAndLibModulesLightClassesTestBase() {

  override fun getAgpProjectFlags(builder: AndroidProjectStubBuilder) =
    builder.buildAgpProjectFlagsStub().copy(applicationRClassConstantIds = true)

  @Test
  fun finalResourceIds() {
    myFixture.addFileToProject("app/res/drawable/foo.xml", "<vector-drawable />")

    myFixture.loadNewFile(
      "/app/src/p1/p2/MainActivity.java",
      // language=java
      """
    package p1.p2;

    import android.app.Activity;
    import android.os.Bundle;

    public class MainActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            int id = R.id.basi${caret}cID;
            switch (id) {
                case R.id.basicID: break;
                case R.string.appString: break;
                case R.drawable.foo: break;
            }
        }
    }
    """
        .trimIndent(),
    )

    myFixture.checkHighlighting()

    with(myFixture.elementAtCaret) {
      assertThat(this).isInstanceOf(ResourceLightField::class.java)
      assertThat((this as ResourceLightField).resourceName).isEqualTo("basicID")
      assertThat(
          (this as PsiModifierListOwner).modifierList!!.hasExplicitModifier(PsiModifier.FINAL)
        )
        .isTrue()
    }

    myFixture.moveCaret("case R.string.app|String")
    with(myFixture.elementAtCaret) {
      assertThat(this).isInstanceOf(ResourceLightField::class.java)
      assertThat((this as ResourceLightField).resourceName).isEqualTo("appString")
      assertThat(
          (this as PsiModifierListOwner).modifierList!!.hasExplicitModifier(PsiModifier.FINAL)
        )
        .isTrue()
    }
  }
}

/** Tests with a module that should not see R class from another module. */
class UnrelatedModules : LightClassesTestBase() {

  override fun configureAdditionalModules(
    projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
    modules: MutableList<MyAdditionalModuleData>,
  ) {
    addModuleWithAndroidFacet(
      projectBuilder,
      modules,
      "unrelatedLib",
      AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
      false,
    )
  }

  override fun setUp() {
    super.setUp()

    val libModule = getAdditionalModuleByName("unrelatedLib")!!

    updatePrimaryManifest(AndroidFacet.getInstance(libModule)!!) {
      `package`.value = "p1.p2.unrelatedLib"
    }

    myFixture.addFileToProject(
      "${getAdditionalModulePath("unrelatedLib")}/res/values/values.xml",
      // language=xml
      """
      <resources>
        <string name="libString">Hello from app</string>
      </resources>
      """
        .trimIndent(),
    )
  }

  /**
   * Regression test for b/110776676. p1.p2 is potentially special, because it contains the R class,
   * p1.p2.util is a regular package that contains a regular class. We need to make sure the parent
   * of p1.p2.util `equals` to p1.p2 from the facade, otherwise various tree views get confused.
   */
  fun testPackageParent() {
    myFixture.addFileToProject(
      "/src/p1/p2/util/Util.java",
      // language=java
      """
      package p1.p2.util;

      public class Util {}
      """
        .trimIndent(),
    )

    val utilPackage = myFixture.javaFacade.findPackage("p1.p2.util")!!
    assertThat(utilPackage.parentPackage).isEqualTo(myFixture.javaFacade.findPackage("p1.p2"))
  }

  fun testTopLevelClassCompletion() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(p1.p2.unrelatedLib.${caret}R.string.libString);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)

    assertNoElementAtCaret()

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).isEmpty()
  }

  fun testLibraryUseScope() {
    addAarDependency(myFixture, myModule, "aarlib", "com.example.aarlib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText(
          """
        int string aar_lib_string 0x7f010001
        """
            .trimIndent()
        )
    }

    val aarRClass =
      myFixture.javaFacade.findClass(
        "com.example.aarlib.R",
        GlobalSearchScope.everythingScope(project),
      )!!
    val useScope = aarRClass.useScope as GlobalSearchScope
    assertTrue(useScope.isSearchInModuleContent(myModule))
    assertFalse(useScope.isSearchInModuleContent(getAdditionalModuleByName("unrelatedLib")!!))
  }
}

@RunWith(JUnit4::class)
@RunsInEdt
class NamespacedModuleWithAarLightClassesTest {

  @get:Rule val androidProjectRule = AndroidProjectRule.withSdk().onEdt()

  private val myFixture by lazy {
    androidProjectRule.fixture.apply {
      testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/android/testData").toString()
    }
  }
  private val project by lazy { myFixture.project }
  private val myModule by lazy { myFixture.module }
  private val myFacet by lazy { myModule.androidFacet!! }

  @Before
  fun setUp() {
    myFixture.copyFileToProject(
      SdkConstants.FN_ANDROID_MANIFEST_XML,
      SdkConstants.FN_ANDROID_MANIFEST_XML,
    )

    AndroidModel.set(myFacet, namespaced(myFacet))
    updatePrimaryManifest(myFacet) { `package`.value = "p1.p2" }
    LocalResourceManager.getInstance(myFacet.module)!!.invalidateAttributeDefinitions()
    addBinaryAarDependency(myModule)
  }

  @Test
  fun topLevelClass() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.${caret}R.string.my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    assertThat(resolveReferenceUnderCaret(myFixture)).isInstanceOf(SmallAarRClass::class.java)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("R", "BuildConfig")
  }

  @Test
  fun resourceNames() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.${caret}my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    assertThat(resolveReferenceUnderCaret(myFixture)).isInstanceOf(LightElement::class.java)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("my_aar_string", "class")
  }

  @Test
  fun containingClass() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.${caret}my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    assertThat((resolveReferenceUnderCaret(myFixture) as? PsiField)?.containingClass?.name)
      .isEqualTo("string")
  }

  @Test
  fun usageInfos() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.moveCaret("|R.string.my_aar_string")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
    myFixture.moveCaret("R.|string.my_aar_string")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
    myFixture.moveCaret("R.string.|my_aar_string")
    UsageInfo(resolveReferenceUnderCaret(myFixture))
  }
}

class NonNamespacedModuleWithAar : LightClassesTestBase() {

  override fun setUp() {
    super.setUp()
    addAarDependency(myFixture, myModule, "aarLib", "com.example.mylibrary") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText(
          """
        int string my_aar_string 0x7f010001
        int string another_aar_string 0x7f010002
        int attr attrOne 0x7f040001
        int attr attrTwo 0x7f040002
        int[] styleable LibStyleable { 0x7f040001, 0x7f040002, 0x7f040003 }
        int styleable LibStyleable_attrOne 0
        int styleable LibStyleable_attrTwo 1
        int styleable LibStyleable_android_maxWidth 2
        """
            .trimIndent()
        )
    }
    addAarDependency(myFixture, myModule, "anotherLib", "com.example.anotherLib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText(
          """
        int string another_lib_string 0x7f010001
        """
            .trimIndent()
        )
    }
  }

  fun testTopLevelClass() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.${caret}R.string.my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    val aarRClass = resolveReferenceUnderCaret()
    assertThat(aarRClass).isInstanceOf(TransitiveAarRClass::class.java)

    // Regression test for b/141392340, make sure getResolveScope() doesn't throw when called on AAR
    // classes that don't have a module.
    assertThat(ElementPresentationUtil.getClassKind(aarRClass as PsiClass))
      .isEqualTo(ElementPresentationUtil.CLASS_KIND_CLASS)
  }

  fun testResourceNames_string() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.${caret}my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    assertThat(resolveReferenceUnderCaret()).isInstanceOf(LightElement::class.java)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsExactly("my_aar_string", "another_aar_string", "class")
  }

  fun testUsageInfos() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.moveCaret("|R.string.my_aar_string")
    UsageInfo(resolveReferenceUnderCaret())
    myFixture.moveCaret("R.|string.my_aar_string")
    UsageInfo(resolveReferenceUnderCaret())
    myFixture.moveCaret("R.string.|my_aar_string")
    UsageInfo(resolveReferenceUnderCaret())
  }

  fun testResourceNames_styleable() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.styleable.${caret}LibStyleable_attrOne);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    val elementUnderCaret = resolveReferenceUnderCaret()
    assertThat(elementUnderCaret).isInstanceOf(StyleableAttrLightField::class.java)
    val styleable = (elementUnderCaret as StyleableAttrLightField).styleableAttrFieldUrl.styleable
    assertThat(styleable)
      .isEqualTo(ResourceReference(RES_AUTO, ResourceType.STYLEABLE, "LibStyleable"))
    val attr = elementUnderCaret.styleableAttrFieldUrl.attr
    assertThat(attr).isEqualTo(ResourceReference(RES_AUTO, ResourceType.ATTR, "attrOne"))

    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsExactly(
        "LibStyleable",
        "LibStyleable_attrOne",
        "LibStyleable_attrTwo",
        "LibStyleable_android_maxWidth",
        "class",
      )
  }

  fun testResourceNames_styleableWithPackage() {
    val activityWithStyleablePackage =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.styleable.${caret}LibStyleable_android_maxWidth);
          }
      }
      """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(activityWithStyleablePackage.virtualFile)
    myFixture.checkHighlighting()
    val elementUnderCaret = resolveReferenceUnderCaret()
    assertThat(elementUnderCaret).isInstanceOf(StyleableAttrLightField::class.java)
    val styleable = (elementUnderCaret as StyleableAttrLightField).styleableAttrFieldUrl.styleable
    assertThat(styleable)
      .isEqualTo(ResourceReference(RES_AUTO, ResourceType.STYLEABLE, "LibStyleable"))
    val attr = elementUnderCaret.styleableAttrFieldUrl.attr
    assertThat(attr)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "maxWidth"))
  }

  fun testRClassCompletion_java() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;
      import com.example.anotherLib.Foo; // See assertion below.

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(R${caret});
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    // MainActivity has an import for a class from com.example.anotherLib, which would put
    // com.example.anotherLib.R at the top if not for
    // AndroidLightClassWeigher.
    val firstSuggestion = myFixture.lookupElements?.first()?.psiElement
    assertThat(firstSuggestion).isInstanceOf(ResourceRepositoryRClass::class.java)
    assertThat((firstSuggestion as PsiClass).qualifiedName).isEqualTo("p1.p2.R")

    assertThat(
        this.myFixture.lookupElements!!
          .mapNotNull { it.psiElement as? PsiClass }
          .filter { it.name == "R" }
          .map { it.qualifiedName }
      )
      .containsExactly("p1.p2.R", "com.example.mylibrary.R", "com.example.anotherLib.R")
  }

  fun testContainingClass() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              getResources().getString(com.example.mylibrary.R.string.${caret}my_aar_string);
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    assertThat((resolveReferenceUnderCaret() as? PsiField)?.containingClass?.name)
      .isEqualTo("string")
  }

  /** Regression test for b/118485835. */
  fun testKotlinCompletion_b118485835() {
    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/sub/test.kt",
        // language=kotlin
        """
      package p1.p2.sub

      fun test() {
        R${caret}
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.completeBasic()

    assertThat(
        this.myFixture.lookupElements!!
          .mapNotNull { it.psiElement as? PsiClass }
          .filter { it.name == "R" }
          .map { it.qualifiedName }
      )
      .containsExactly("p1.p2.R", "com.example.mylibrary.R", "com.example.anotherLib.R")
  }

  fun testMalformedRTxt_styleables() {
    addAarDependency(myFixture, myModule, "brokenLib", "com.example.brokenLib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText(
          """
          int[] styleable FontFamilyFont { notANumber, 0x7f040008, 0x7f040009 }
          int styleable FontFamilyFont_android_font 0
          int styleable FontFamilyFont_android_fontStyle 1
          int styleable FontFamilyFont_android_fontWeight 2
          int styleable FontFamilyFont_font notANumber
          int styleable FontFamilyFont_fontStyle 4
          int styleable FontFamilyFont_fontWeight 5
        """
            .trimIndent()
        )
    }

    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              int[] ids = com.example.brokenLib.R.styleable.${caret}FontFamilyFont;
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    val elementUnderCaret = resolveReferenceUnderCaret()
    assertThat(elementUnderCaret).isNotInstanceOf(StyleableAttrLightField::class.java)
    assertThat(elementUnderCaret).isInstanceOf(AndroidLightField::class.java)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings)
      .containsExactly(
        "FontFamilyFont",
        "FontFamilyFont_android_font",
        "FontFamilyFont_android_fontStyle",
        "FontFamilyFont_android_fontWeight",
        "FontFamilyFont_font",
        "FontFamilyFont_fontStyle",
        "FontFamilyFont_fontWeight",
        "class",
      )
  }

  fun testMalformedRTxt_completelyWrong() {
    addAarDependency(myFixture, myModule, "brokenLib", "com.example.brokenLib") { resDir ->
      resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText("Hello from the internet")
    }

    val activity =
      myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.java",
        // language=java
        """
      package p1.p2;

      import android.app.Activity;
      import android.os.Bundle;

      public class MainActivity extends Activity {
          @Override
          protected void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              Object o = com.example.brokenLib.R.${caret}class;
          }
      }
      """
          .trimIndent(),
      )

    myFixture.configureFromExistingVirtualFile(activity.virtualFile)
    myFixture.checkHighlighting()
    myFixture.completeBasic()
    // The R class itself exists, but has not inner classes because we don't know what to put in
    // them.
    assertThat(myFixture.lookupElementStrings).containsExactly("class")
  }
}

/**
 * The default lookup string from CodeInsightTestFixture, only includes the item text, and not other
 * aspects of the lookup element such as tail text and type text. We want to verify certain aspects
 * are present in the lookup elements.
 *
 * Be careful using this on Java elements such as resource fields, as the int constant value in the
 * tail text is not always the same on repeated test runs.
 */
private fun LookupElement.toPresentableText(): String {
  val presentation = LookupElementPresentation()
  renderElement(presentation)
  return "${presentation.itemText} ${presentation.tailText} ${presentation.typeText}"
}
