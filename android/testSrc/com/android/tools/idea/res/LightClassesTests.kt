/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.builder.model.AndroidProject
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidResolveScopeEnlarger
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.facet.AndroidFacet

/**
 * Tests for the whole setup of light, in-memory R classes.
 *
 * @see ProjectSystemPsiElementFinder
 * @see ProjectLightResourceClassService
 */
sealed class LightClassesTestBase : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.IN_MEMORY_R_CLASSES.override(true)
    // No need to copy R.java into gen!

    myModule.createImlFile()
  }

  override fun tearDown() {
    try {
      StudioFlags.IN_MEMORY_R_CLASSES.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * Creates the iml file for a module on disk. This is necessary for correct Kotlin resolution of light classes.
   *
   * @see AndroidResolveScopeEnlarger
   */
  protected fun Module.createImlFile() {
    VfsTestUtil.createFile(LocalFileSystem.getInstance().findFileByPath("/")!!, moduleFilePath)
    assertNotNull(moduleFile)
  }

  protected fun resolveReferenceUnderCaret(): PsiElement? {
    // We cannot use myFixture.elementAtCaret or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED because JavaTargetElementEvaluator doesn't
    // consider synthetic PSI elements as "acceptable" and just returns null instead, so it wouldn't test much.
    return TargetElementUtil.findReference(myFixture.editor)!!.resolve()
  }

  open class SingleModule : LightClassesTestBase() {
    override fun setUp() {
      super.setUp()

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
            override fun onCreate(savedInstanceState: Bundle) {
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
            override fun onCreate(savedInstanceState: Bundle) {
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
            override fun onCreate(savedInstanceState: Bundle) {
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
            override fun onCreate(savedInstanceState: Bundle) {
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

    fun testManifestClass_java() {
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
                getResources().getString(Manifest.permission.${caret}SEND_MESSAGE);
            }
        }
        """.trimIndent()
      )

      assertThat(resolveReferenceUnderCaret()).isNull()

      runWriteCommandAction(project) {
        myFacet.manifest!!.addPermission()!!.apply { name.value = "com.example.SEND_MESSAGE" }
      }

      assertThat(resolveReferenceUnderCaret()).isInstanceOf(AndroidLightField::class.java)
      myFixture.checkHighlighting()
    }

    fun testManifestClass_kotlin() {
      val activity = myFixture.addFileToProject(
        "/src/p1/p2/MainActivity.kt",
        // language=kotlin
        """
        package p1.p2

        import android.app.Activity
        import android.os.Bundle
        import android.util.Log

        class MainActivity : Activity() {
            override fun onCreate(savedInstanceState: Bundle) {
                super.onCreate(savedInstanceState)
                Log.d("tag", Manifest.permission.${caret}SEND_MESSAGE)
            }
        }
        """.trimIndent()
      )
      myFixture.configureFromExistingVirtualFile(activity.virtualFile)

      assertThat(resolveReferenceUnderCaret()).isNull()

      runWriteCommandAction(project) {
        myFacet.manifest!!.addPermission()!!.apply { name.value = "com.example.SEND_MESSAGE" }
      }

      assertThat(resolveReferenceUnderCaret()).isInstanceOf(AndroidLightField::class.java)
      myFixture.checkHighlighting()
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
  }

  class SingleModuleNamespaced : SingleModule() {
    override fun setUp() {
      super.setUp()
      enableNamespacing("p1.p2")
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
      addModuleWithAndroidFacet(projectBuilder, modules, "unrelatedLib", AndroidProject.PROJECT_TYPE_LIBRARY, false)
    }

    override fun setUp() {
      super.setUp()

      val libModule = getAdditionalModuleByName("unrelatedLib")!!
      libModule.createImlFile()

      runWriteCommandAction(project) {
        libModule
          .let(AndroidFacet::getInstance)!!
          .manifest!!
          .`package`!!
          // TODO(b/109739056): Once we correctly create subpackages, we no longer need to use a common prefix.
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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(NamespacedAarPackageRClass::class.java)
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
          int[] styleable LibStyleable { 0x7f040001, 0x7f040002 }
          int styleable LibStyleable_attrOne 0
          int styleable LibStyleable_attrTwo 1
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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(NonNamespacedAarPackageRClass::class.java)
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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(LightElement::class.java)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly(
        "LibStyleable",
        "LibStyleable_attrOne",
        "LibStyleable_attrTwo",
        "class"
      )
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
      assertThat(firstSuggestion).isInstanceOf(ModulePackageRClass::class.java)
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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(LightElement::class.java)
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
