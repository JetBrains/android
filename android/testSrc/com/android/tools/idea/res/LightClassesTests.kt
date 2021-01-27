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
import com.android.tools.idea.model.MergedManifestModificationListener
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findClass
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.updatePrimaryManifest
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.ElementPresentationUtil
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.usageView.UsageInfo
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidNonTransitiveRClassKotlinCompletionContributor
import org.jetbrains.android.AndroidResolveScopeEnlarger
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.augment.StyleableAttrLightField
import org.jetbrains.android.dom.manifest.Manifest
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

/**
 * Tests for the whole setup of light, in-memory R classes.
 *
 * @see ProjectSystemPsiElementFinder
 * @see ProjectLightResourceClassService
 */
sealed class LightClassesTestBase : AndroidTestCase() {

  protected fun resolveReferenceUnderCaret(): PsiElement? {
    // We cannot use myFixture.elementAtCaret or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED because JavaTargetElementEvaluator doesn't
    // consider synthetic PSI elements as "acceptable" and just returns null instead, so it wouldn't test much.
    return TargetElementUtil.findReference(myFixture.editor)!!.resolve()
  }

  open class SingleModule : LightClassesTestBase() {
    override fun setUp() {
      super.setUp()
      MergedManifestModificationListener.ensureSubscribed(project)
      myFixture.addFileToProject(
        "/res/values/values.xml",
        // language=xml
        """
        <resources>
          <string name="appString">Hello from app</string>
        </resources>
        """.trimIndent()
      )
    }

    fun testHighlighting_java() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
    }

    fun testHighlighting_kotlin() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
    }

    fun testTopLevelClassCompletion_java() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
    }

    fun testTopLevelClassCompletion_kotlin() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
    }

    fun testInnerClassesCompletion_java() {
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
                getResources().getString(R.${caret});
            }
        }
        """.trimIndent()
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("class", "string")
    }

    fun testInnerClassesCompletion_kotlin() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("string")
    }

    fun testResourceNamesCompletion_java() {
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
        """.trimIndent()
      )

      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("appString", "class")
    }

    fun testResourceNamesCompletion_kotlin() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("appString")
    }

    fun testStyleableAttrResourceNamesCompletion_java() {
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
        """.trimIndent()
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
        """.trimIndent()
      )

      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("LabelView_android_maxHeight", "LabelView_foo", "LabelView", "class")
    }

    fun testManifestClass_java() {
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
        """.trimIndent()
      )

      assertThat(resolveReferenceUnderCaret()).isNull()

      runWriteCommandAction(project) {
        Manifest.getMainManifest(myFacet)!!.addPermission()!!.apply { name.value = "com.example.SEND_MESSAGE" }
      }

      assertThat(resolveReferenceUnderCaret()).isInstanceOf(AndroidLightField::class.java)
      myFixture.checkHighlighting()
    }

    fun testManifestClass_kotlin() {
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
        """.trimIndent()
      )

      assertThat(resolveReferenceUnderCaret()).isNull()

      runWriteCommandAction(project) {
        Manifest.getMainManifest(myFacet)!!.addPermission()!!.apply { name.value = "com.example.SEND_MESSAGE" }
      }

      assertThat(resolveReferenceUnderCaret()).isInstanceOf(AndroidLightField::class.java)
      myFixture.checkHighlighting()
    }

    fun testAddingAar() {
      // Initialize the light classes code.
      assertThat(myFixture.javaFacade.findClass("p1.p2.R", GlobalSearchScope.everythingScope(project))).isNotNull()

      addAarDependency(myModule, "someLib", "com.example.someLib") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText("int string some_lib_string 0x7f010001")
      }

      assertThat(myFixture.javaFacade.findClass("com.example.someLib.R", GlobalSearchScope.everythingScope(project)))
        .isNotNull()
    }

    fun testResourceRename() {
      val strings = myFixture.addFileToProject(
        "/res/values/strings.xml",
        // language=xml
        """
        <resources>
          <string name="f${caret}oo">foo</string>
        </resources>
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(strings.virtualFile)
      assertThat(
        myFixture.javaFacade
          .findClass("p1.p2.R.string", GlobalSearchScope.everythingScope(project))!!
          .fields
          .map(PsiField::getName)
      ).containsExactly("appString", "foo")

      myFixture.renameElementAtCaretUsingHandler("bar")
      UIUtil.dispatchAllInvocationEvents()

      assertThat(
        myFixture.javaFacade
          .findClass("p1.p2.R.string", GlobalSearchScope.everythingScope(project))!!
          .fields
          .map(PsiField::getName)
      ).containsExactly("appString", "bar")
    }

    fun testModificationTracking() {
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
        """.trimIndent()
      )

      // Sanity check:
      myFixture.checkHighlighting()

      // Make sure light classes pick up changes to repositories:
      val barXml = myFixture.addFileToProject("res/drawable/bar.xml", "<vector-drawable />")
      UIUtil.dispatchAllInvocationEvents()
      assertThat(myFixture.doHighlighting(ERROR)).isEmpty()

      // Regression test for b/144585792. Caches in ResourceRepositoryManager can be dropped for various reasons, we need to make sure we
      // keep track of changes even after new repository instances are created.
      ResourceRepositoryManager.getInstance(myFacet).resetAllCaches()
      runWriteAction { barXml.delete() }
      UIUtil.dispatchAllInvocationEvents()
      assertThat(myFixture.doHighlighting(ERROR)).hasSize(1)
    }

    fun testContainingClass() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      assertThat((resolveReferenceUnderCaret() as? PsiField)?.containingClass?.name).isEqualTo("string")
    }

    fun testUsageInfos() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.moveCaret("|R.string.appString")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.|string.appString")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.string.|appString")
      UsageInfo(resolveReferenceUnderCaret()!!)
    }

    fun testInvalidManifest() {
      updatePrimaryManifest(myFacet) {
        `package`.value = "."
      }

      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )
      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      // The R class is not reachable from Java, but we should not crash trying to create an invalid package name.
      myFixture.checkHighlighting()

      updatePrimaryManifest(myFacet) {
        `package`.value = "p1.p2"
      }

      // The first call to checkHighlighting removes error markers from the Document, so this makes sure there are no errors.
      myFixture.checkHighlighting()
      val rClass = resolveReferenceUnderCaret()
      assertThat(rClass).isInstanceOf(ModuleRClass::class.java)
      assertThat((rClass as ModuleRClass).qualifiedName).isEqualTo("p1.p2.R")
    }
  }

  class SingleModuleNamespaced : SingleModule() {
    override fun setUp() {
      super.setUp()
      enableNamespacing("p1.p2")
    }
  }

  class AppAndLibModules : LightClassesTestBase() {
    override fun configureAdditionalModules(
      projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
      modules: MutableList<MyAdditionalModuleData>
    ) {
      addModuleWithAndroidFacet(
        projectBuilder,
        modules,
        "mylib",
        AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
        true
      )
    }

    override fun setUp() {
      super.setUp()

      myFixture.addFileToProject(
        "/res/values/values.xml",
        // language=xml
        """
        <resources>
          <string name="appString">Hello from app</string>
          <string name="anotherAppString">Hello from app</string>
          <id name="basicID"/>
        </resources>
        """.trimIndent()
      )

      val libModule = getAdditionalModuleByName("mylib")!!

      runWriteCommandAction(project) {
        libModule
          .let(AndroidFacet::getInstance)!!
          .let { Manifest.getMainManifest(it)!!}
          .`package`!!
          .value = "com.example.mylib"
      }

      myFixture.addFileToProject(
        "${getAdditionalModulePath("mylib")}/res/values/values.xml",
        // language=xml
        """
        <resources>
          <string name="libString">Hello from app</string>
          <string name="anotherLibString">Hello from app</string>
        </resources>
        """.trimIndent()
      )
    }

    /**
     * Testing completion elements provided via [AndroidNonTransitiveRClassKotlinCompletionContributor]
     */
    fun testNonTransitiveKotlinCompletion() {
      (myModule.getModuleSystem() as DefaultModuleSystem).isRClassTransitive = false

      val androidTest = createFile(
        project.guessProjectDir()!!,
        "/src/p1/p2/RClassAndroidTest.kt",
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
        }""".trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(androidTest)

      // Check that the lib class reference only contains library resources
      myFixture.moveCaret("com.example.mylib.R.string.|,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsAllIn(arrayOf("anotherLibString", "libString"))

      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings)
        .containsAllIn(arrayOf("anotherLibString", "libString"))

      // R class references with package prefix only get resources in original R class
      myFixture.moveCaret("p1.p2.R.string.|,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsAllIn(arrayOf("anotherAppString", "appString"))

      myFixture.moveCaret("p1.p2.R.|string.,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsAllIn(ResourceType.values().filter { it.hasInnerClass }.map { it.getName() })

      myFixture.moveCaret("R.string.|anotherLib,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElements.map { it.toPresentableText() })
        .containsAllIn(arrayOf(
          "anotherLibString  (com.example.mylib) Int",
          "libString  (com.example.mylib) Int",
          "anotherAppString null Int",
          "appString null Int"))

      // Check insert handler works correctly
      myFixture.moveCaret("R.string.anotherLib|,")
      myFixture.completeBasic()

      myFixture.moveCaret("p1.p2.R.string.|,")
      myFixture.type("anotherApp")
      myFixture.completeBasic()

      myFixture.checkResult(
        "/src/p1/p2/RClassAndroidTest.kt",
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
        """.trimIndent(), true)

      // Test for same module, different package
      val otherPackage = createFile(
        project.guessProjectDir()!!,
        "/src/p3/p4/RClassAndroidTest.kt",
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
        }""".trimIndent())
      myFixture.configureFromExistingVirtualFile(otherPackage)

      // R class references with package prefix only get resources in original R class
      myFixture.moveCaret("R.string.|,")
      myFixture.completeBasic()
      myFixture.type("anotherLib")
      myFixture.completeBasic()

      myFixture.moveCaret("p1.p2.R.string.|,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsAllIn(arrayOf("anotherAppString", "appString"))
      myFixture.type("anotherApp")
      myFixture.completeBasic()

      myFixture.moveCaret("R.string.|anotherApp,")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsAllIn(arrayOf("anotherLibString", "libString", "anotherAppString", "appString"))

      myFixture.moveCaret("R.string.anotherApp|,")
      myFixture.completeBasic()

      myFixture.checkResult(
        "/src/p3/p4/RClassAndroidTest.kt",
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
        }""".trimIndent(), true)
    }

    fun testNonTransitive() {
      (myModule.getModuleSystem() as DefaultModuleSystem).isRClassTransitive = false

      myFixture.loadNewFile(
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
              getResources().getString(R.string.${"libString" highlightedAs ERROR});
              getResources().getString(com.example.mylib.R.string.libString);
          }
      }
      """.trimIndent()
      )

      myFixture.checkHighlighting()

      myFixture.moveCaret("(R.string.|libString")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("appString", "anotherAppString", "class")

      myFixture.moveCaret("mylib.R.string.|libString")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("libString", "anotherLibString", "class")
    }

    fun testNonFinalResourceIds() {
      (myModule.getModuleSystem() as DefaultModuleSystem).applicationRClassConstantIds = false
      myFixture.addFileToProject("res/drawable/foo.xml", "<vector-drawable />")

      myFixture.loadNewFile(
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
              int id = R.id.basi${caret}cID;
              switch (id) {
                  case <error descr="Constant expression required">R.id.basicID</error>: break;
                  case <error descr="Constant expression required">R.string.appString</error>: break;
                  case <error descr="Constant expression required">R.drawable.foo</error>: break;
              }
          }
      }
      """.trimIndent()
      )

      myFixture.checkHighlighting()

      val elementAtCaret = myFixture.elementAtCaret
      assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
      assertThat((elementAtCaret as PsiModifierListOwner).modifierList!!.hasExplicitModifier(PsiModifier.FINAL)).isFalse()
    }

    fun testFinalResourceIds() {
      (myModule.getModuleSystem() as DefaultModuleSystem).applicationRClassConstantIds = true
      myFixture.addFileToProject("res/drawable/foo.xml", "<vector-drawable />")

      myFixture.loadNewFile(
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
              int id = R.id.basi${caret}cID;
              switch (id) {
                  case R.id.basicID: break;
                  case R.string.appString: break;
                  case R.drawable.foo: break;
              }
          }
      }
      """.trimIndent()
      )

      myFixture.checkHighlighting()

      with (myFixture.elementAtCaret) {
        assertThat(this).isInstanceOf(ResourceLightField::class.java)
        assertThat((this as ResourceLightField).resourceName).isEqualTo("basicID")
        assertThat((this as PsiModifierListOwner).modifierList!!.hasExplicitModifier(PsiModifier.FINAL)).isTrue()
      }

      myFixture.moveCaret("case R.string.app|String")
      with (myFixture.elementAtCaret) {
        assertThat(this).isInstanceOf(ResourceLightField::class.java)
        assertThat((this as ResourceLightField).resourceName).isEqualTo("appString")
        assertThat((this as PsiModifierListOwner).modifierList!!.hasExplicitModifier(PsiModifier.FINAL)).isTrue()
      }
    }

    fun testUseScope() {
      val activity = myFixture.loadNewFile(
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
              getResources().getString(R.string.${caret}app_string);
          }
      }
      """.trimIndent()
      )
      val appClassUseScope = myFixture.findClass("p1.p2.R", activity)!!.useScope as GlobalSearchScope
      assertFalse(appClassUseScope.isSearchInLibraries)
      assertFalse(appClassUseScope.isSearchInModuleContent(getAdditionalModuleByName("mylib")!!))
      assertTrue(appClassUseScope.isSearchInModuleContent(myFixture.module))
      assertTrue(appClassUseScope.isSearchInModuleContent(myFixture.module, true))

      val libClassUseScope = myFixture.findClass("com.example.mylib.R", activity)!!.useScope as GlobalSearchScope
      assertFalse(libClassUseScope.isSearchInLibraries)
      assertTrue(libClassUseScope.isSearchInModuleContent(getAdditionalModuleByName("mylib")!!))
      assertTrue(libClassUseScope.isSearchInModuleContent(myFixture.module))
      assertTrue(libClassUseScope.isSearchInModuleContent(myFixture.module, true))
    }
  }

  /**
   * Tests with a module that should not see R class from another module.
   */
  class UnrelatedModules : LightClassesTestBase() {

    override fun configureAdditionalModules(
      projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
      modules: MutableList<MyAdditionalModuleData>
    ) {
      addModuleWithAndroidFacet(
        projectBuilder,
        modules,
        "unrelatedLib",
        AndroidProjectTypes.PROJECT_TYPE_LIBRARY,
        false
      )
    }

    override fun setUp() {
      super.setUp()

      val libModule = getAdditionalModuleByName("unrelatedLib")!!

      runWriteCommandAction(project) {
        Manifest.getMainManifest(libModule.let(AndroidFacet::getInstance)!!
        )!!
          .`package`!!
          .value = "p1.p2.unrelatedLib"
      }

      myFixture.addFileToProject(
        "${getAdditionalModulePath("unrelatedLib")}/res/values/values.xml",
        // language=xml
        """
        <resources>
          <string name="libString">Hello from app</string>
        </resources>
        """.trimIndent()
      )
    }

    /**
     * Regression test for b/110776676. p1.p2 is potentially special, because it contains the R class, p1.p2.util is a regular package that
     * contains a regular class. We need to make sure the parent of p1.p2.util `equals` to p1.p2 from the facade, otherwise various tree
     * views get confused.
     */
    fun testPackageParent() {
      myFixture.addFileToProject(
        "/src/p1/p2/util/Util.java",
        // language=java
        """
        package p1.p2.util;

        public class Util {}
        """.trimIndent()
      )

      val utilPackage = myFixture.javaFacade.findPackage("p1.p2.util")!!
      assertThat(utilPackage.parentPackage).isEqualTo(myFixture.javaFacade.findPackage("p1.p2"))
    }

    fun testTopLevelClassCompletion() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)

      assertThat(resolveReferenceUnderCaret()).isNull()

      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).isEmpty()
    }

    fun testLibraryUseScope() {
      addAarDependency(myModule, "aarlib", "com.example.aarlib") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
          """
          int string aar_lib_string 0x7f010001
          """.trimIndent()
        )
      }

      val aarRClass = myFixture.javaFacade.findClass("com.example.aarlib.R", GlobalSearchScope.everythingScope(project))!!
      val useScope = aarRClass.useScope as GlobalSearchScope
      assertTrue(useScope.isSearchInModuleContent(myModule))
      assertFalse(useScope.isSearchInModuleContent(getAdditionalModuleByName("unrelatedLib")!!))
    }
  }

  class NamespacedModuleWithAar : LightClassesTestBase() {

    override fun setUp() {
      super.setUp()
      enableNamespacing("p1.p2")
      addBinaryAarDependency(myModule)
    }

    fun testTopLevelClass() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(SmallAarRClass::class.java)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("R", "BuildConfig")
    }

    fun testResourceNames() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(LightElement::class.java)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("my_aar_string", "class")
    }

    fun testContainingClass() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      assertThat((resolveReferenceUnderCaret() as? PsiField)?.containingClass?.name).isEqualTo("string")
    }

    fun testUsageInfos() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.moveCaret("|R.string.my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.|string.my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.string.|my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
    }
  }

  class NonNamespacedModuleWithAar : LightClassesTestBase() {

    override fun setUp() {
      super.setUp()
      addAarDependency(myModule, "aarLib", "com.example.mylibrary") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
          """
          int string my_aar_string 0x7f010001
          int string another_aar_string 0x7f010002
          int attr attrOne 0x7f040001
          int attr attrTwo 0x7f040002
          int[] styleable LibStyleable { 0x7f040001, 0x7f040002, 0x7f040003 }
          int styleable LibStyleable_attrOne 0
          int styleable LibStyleable_attrTwo 1
          int styleable LibStyleable_android_maxWidth 2
          """.trimIndent()
        )
      }
      addAarDependency(myModule, "anotherLib", "com.example.anotherLib") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
          """
          int string another_lib_string 0x7f010001
          """.trimIndent()
        )
      }
    }

    fun testTopLevelClass() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      val aarRClass = resolveReferenceUnderCaret()
      assertThat(aarRClass).isInstanceOf(TransitiveAarRClass::class.java)

      // Regression test for b/141392340, make sure getResolveScope() doesn't throw when called on AAR classes that don't have a module.
      assertThat(ElementPresentationUtil.getClassKind(aarRClass as PsiClass)).isEqualTo(ElementPresentationUtil.CLASS_KIND_CLASS)
    }

    fun testResourceNames_string() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(LightElement::class.java)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("my_aar_string", "another_aar_string", "class")
    }

    fun testUsageInfos() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.moveCaret("|R.string.my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.|string.my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
      myFixture.moveCaret("R.string.|my_aar_string")
      UsageInfo(resolveReferenceUnderCaret()!!)
    }

    fun testResourceNames_styleable() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      val elementUnderCaret = resolveReferenceUnderCaret()
      assertThat(elementUnderCaret).isInstanceOf(StyleableAttrLightField::class.java)
      val styleable = (elementUnderCaret as StyleableAttrLightField).styleableAttrFieldUrl.styleable
      assertThat(styleable).isEqualTo(ResourceReference(RES_AUTO, ResourceType.STYLEABLE, "LibStyleable"))
      val attr = elementUnderCaret.styleableAttrFieldUrl.attr
      assertThat(attr).isEqualTo(ResourceReference(RES_AUTO, ResourceType.ATTR, "attrOne"))

      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly(
        "LibStyleable",
        "LibStyleable_attrOne",
        "LibStyleable_attrTwo",
        "LibStyleable_android_maxWidth",
        "class"
      )
    }

    fun testResourceNames_styleableWithPackage() {
      val activityWithStyleablePackage = myFixture.addFileToProject(
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
        """.trimIndent()
      )
      myFixture.configureFromExistingVirtualFile(activityWithStyleablePackage.virtualFile)
      myFixture.checkHighlighting()
      val elementUnderCaret = resolveReferenceUnderCaret()
      assertThat(elementUnderCaret).isInstanceOf(StyleableAttrLightField::class.java)
      val styleable = (elementUnderCaret as StyleableAttrLightField).styleableAttrFieldUrl.styleable
      assertThat(styleable).isEqualTo(ResourceReference(RES_AUTO, ResourceType.STYLEABLE, "LibStyleable"))
      val attr = elementUnderCaret.styleableAttrFieldUrl.attr
      assertThat(attr).isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "maxWidth"))
    }

    fun testRClassCompletion_java() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      // MainActivity has an import for a class from com.example.anotherLib, which would put com.example.anotherLib.R at the top if not for
      // AndroidLightClassWeigher.
      val firstSuggestion = myFixture.lookupElements?.first()?.psiElement
      assertThat(firstSuggestion).isInstanceOf(ResourceRepositoryRClass::class.java)
      assertThat((firstSuggestion as PsiClass).qualifiedName).isEqualTo("p1.p2.R")

      assertThat(
        this.myFixture.lookupElements!!
          .mapNotNull { it.psiElement as? PsiClass }
          .filter { it.name == "R" }
          .map { it.qualifiedName }
      ).containsExactly(
        "p1.p2.R",
        "com.example.mylibrary.R",
        "com.example.anotherLib.R"
      )
    }

    fun testContainingClass() {
      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      assertThat((resolveReferenceUnderCaret() as? PsiField)?.containingClass?.name).isEqualTo("string")
    }

    /**
     *  Regression test for b/118485835.
     */
    fun testKotlinCompletion_b118485835() {
      val activity = myFixture.addFileToProject(
        "/src/p1/p2/sub/test.kt",
        // language=kotlin
        """
        package p1.p2.sub

        fun test() {
          R${caret}
        }
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(
        this.myFixture.lookupElements!!
          .mapNotNull { it.psiElement as? PsiClass }
          .filter { it.name == "R" }
          .map { it.qualifiedName }
      ).containsExactly(
        "p1.p2.R",
        "com.example.mylibrary.R",
        "com.example.anotherLib.R"
      )
    }

    fun testMalformedRTxt_styleables() {
      addAarDependency(myModule, "brokenLib", "com.example.brokenLib") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
          """
            int[] styleable FontFamilyFont { notANumber, 0x7f040008, 0x7f040009 }
            int styleable FontFamilyFont_android_font 0
            int styleable FontFamilyFont_android_fontStyle 1
            int styleable FontFamilyFont_android_fontWeight 2
            int styleable FontFamilyFont_font notANumber
            int styleable FontFamilyFont_fontStyle 4
            int styleable FontFamilyFont_fontWeight 5
          """.trimIndent()
        )
      }


      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      val elementUnderCaret = resolveReferenceUnderCaret()
      assertThat(elementUnderCaret).isNotInstanceOf(StyleableAttrLightField::class.java)
      assertThat(elementUnderCaret).isInstanceOf(AndroidLightField::class.java)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly(
        "FontFamilyFont",
        "FontFamilyFont_android_font",
        "FontFamilyFont_android_fontStyle",
        "FontFamilyFont_android_fontWeight",
        "FontFamilyFont_font",
        "FontFamilyFont_fontStyle",
        "FontFamilyFont_fontWeight",
        "class"
      )
    }

    fun testMalformedRTxt_completelyWrong() {
      addAarDependency(myModule, "brokenLib", "com.example.brokenLib") { resDir ->
        resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText("Hello from the internet")
      }


      val activity = myFixture.addFileToProject(
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
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()
      myFixture.completeBasic()
      // The R class itself exists, but has not inner classes because we don't know what to put in them.
      assertThat(myFixture.lookupElementStrings).containsExactly("class")
    }
  }
}

/**
 * Legacy projects (without the model) have no concept of test resources, so for now this needs to be tested using Gradle.
 *
 * We use the [TestProjectPaths.PROJECT_WITH_APPAND_LIB] project and make `app` have an `androidTestImplementation` dependency on `lib`.
 */
sealed class TestRClassesTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()

    val projectRoot = prepareProjectForImport(TestProjectPaths.PROJECT_WITH_APPAND_LIB)

    createFile(
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

    createFile(
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

    createFile(
      project.guessProjectDir()!!,
      "lib/src/main/res/values/strings.xml",
      // language=xml
      """
        <resources>
          <string name='libResource'>lib resource</string>
        </resources>
      """.trimIndent()
    )

    modifyGradleFiles(projectRoot)
    importProject(null)
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
      project.guessProjectDir()!!,
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

    val projectRoot = File(FileUtil.toSystemDependentName(project.basePath!!))
    File(projectRoot, "gradle.properties").appendText("android.nonTransitiveRClass=true")
    requestSyncAndWait()

    // Verifies that the AndroidResolveScopeEnlarger cache has been updated, support_simple_spinner_dropdown_item is not present.
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactly("activity_main", "fragment_foo", "fragment_main",
                                                               "fragment_navigation_drawer", "class")
  }
}

class TransitiveTestRClassesTest : TestRClassesTest() {

  fun testAppTestResources() {
    val androidTest = createFile(
      project.guessProjectDir()!!,
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
      project.guessProjectDir()!!,
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
      project.guessProjectDir()!!,
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
      project.guessProjectDir()!!,
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

    assertThat(service.getLightRClassesDefinedByModule(appModule, true).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.R",
      "com.example.projectwithappandlib.app.test.R"
    )
    assertThat(service.getLightRClassesDefinedByModule(appModule, false).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.app.R"
    )
    assertThat(service.getLightRClassesDefinedByModule(libModule, true).map { it.qualifiedName }).containsExactly(
      "com.example.projectwithappandlib.lib.R",
      "com.example.projectwithappandlib.lib.test.R"
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

    val libTest = myFixture.loadNewFile(
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
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    val appTestScope = myFixture.findClass("com.example.projectwithappandlib.app.test.R", appTest)!!.useScope as GlobalSearchScope
    assertFalse(appTestScope.isSearchInLibraries)
    assertEquals(appTestScope, myFixture.findClass("com.example.projectwithappandlib.app.RClassAndroidTest").useScope)

    val libTestScope = myFixture.findClass("com.example.projectwithappandlib.lib.test.R", libTest)!!.useScope as GlobalSearchScope
    assertFalse(libTestScope.isSearchInLibraries)
    assertEquals(libTestScope, myFixture.findClass("com.example.projectwithappandlib.lib.RClassAndroidTest").useScope)
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
      project.guessProjectDir()!!,
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
      project.guessProjectDir()!!,
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
}


/**
 * Tests for resources registered as generated with Gradle.
 */
class GeneratedResourcesTest : AndroidGradleTestCase() {

  /**
   * Regression test for b/120750247.
   */
  fun testGeneratedRawResource() {
    val projectRoot = prepareProjectForImport(TestProjectPaths.PROJECT_WITH_APPAND_LIB)

    File(projectRoot, "app/build.gradle").appendText(
      """
      android {
        String resGeneratePath = "${"$"}{buildDir}/generated/my_generated_resources/res"
        def generateResTask = tasks.create(name: 'generateMyResources').doLast {
            def rawDir = "${"$"}{resGeneratePath}/raw"
            mkdir(rawDir)
            file("${"$"}{rawDir}/sample_raw_resource").write("sample text")
        }

        def resDir = files(resGeneratePath).builtBy(generateResTask)

        applicationVariants.all { variant ->
            variant.registerGeneratedResFolders(resDir)
        }
      }
      """.trimIndent())

    requestSyncAndWait()

    AndroidProjectRootListener.ensureSubscribed(project)
    assertThat(ResourceRepositoryManager.getAppResources(project.findAppModule())!!
                 .getResources(ResourceNamespace.RES_AUTO, ResourceType.RAW, "sample_raw_resource")).isEmpty()

    generateSources()

    assertThat(ResourceRepositoryManager.getAppResources(project.findAppModule())!!
                 .getResources(ResourceNamespace.RES_AUTO, ResourceType.RAW, "sample_raw_resource")).isNotEmpty()

    myFixture.openFileInEditor(
      project.guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/com/example/projectwithappandlib/app/MainActivity.java")!!)

    myFixture.moveCaret("int id = |item.getItemId();")
    myFixture.type("R.raw.")
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly("sample_raw_resource", "class")
  }
}

/**
 * The default lookup string from CodeInsightTestFixture, only includes the item text, and not other aspects of the lookup element such
 * as tail text and type text. We want to verify certain aspects are present in the lookup elements.
 */
private fun LookupElement.toPresentableText(): String {
  val presentation = LookupElementPresentation()
  renderElement(presentation)
  return "${presentation.itemText} ${presentation.tailText} ${presentation.typeText}"
}
