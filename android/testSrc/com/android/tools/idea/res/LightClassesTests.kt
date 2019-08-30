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

import com.android.SdkConstants
import com.android.builder.model.AndroidProject
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.highlightedAs
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.updatePrimaryManifest
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.VfsTestUtil.createFile
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.usageView.UsageInfo
import com.intellij.util.ui.UIUtil
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.AndroidLightField
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

private fun withNonTransitiveClasses(code: () -> Unit) {
  StudioFlags.TRANSITIVE_R_CLASSES.override(false)
  try {
    code()
  }
  finally {
    StudioFlags.TRANSITIVE_R_CLASSES.clearOverride()
  }
}

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
        AndroidProject.PROJECT_TYPE_LIBRARY,
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
        </resources>
        """.trimIndent()
      )

      val libModule = getAdditionalModuleByName("mylib")!!

      runWriteCommandAction(project) {
        libModule
          .let(AndroidFacet::getInstance)!!
          .manifest!!
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

    fun testNonTransitive() = withNonTransitiveClasses {
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
              getResources().getString(R.string.${"libString" highlightedAs ERROR});
              getResources().getString(com.example.mylib.R.string.libString);
          }
      }
      """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.checkHighlighting()

      myFixture.moveCaret("(R.string.|libString")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("appString", "anotherAppString", "class")

      myFixture.moveCaret("mylib.R.string.|libString")
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).containsExactly("libString", "anotherLibString", "class")
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
        AndroidProject.PROJECT_TYPE_LIBRARY,
        false
      )
    }

    override fun setUp() {
      super.setUp()

      val libModule = getAdditionalModuleByName("unrelatedLib")!!

      runWriteCommandAction(project) {
        libModule
          .let(AndroidFacet::getInstance)!!
          .manifest!!
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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(TransitiveAarRClass::class.java)
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

/**
 * Legacy projects (without the model) have no concept of test resources, so for now this needs to be tested using Gradle.
 *
 * We use the [TestProjectPaths.PROJECT_WITH_APPAND_LIB] project and make `app` have an `androidTestImplementation` dependency on `lib`.
 */
class TestRClassesTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()

    val projectRoot = prepareProjectForImport(TestProjectPaths.PROJECT_WITH_APPAND_LIB)

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

    requestSyncAndWait()

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
  }

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

  fun testAppTestResources_nonTransitive() = withNonTransitiveClasses {

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

  fun testLibTestResources_nonTransitive() = withNonTransitiveClasses {
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

  fun testScoping() {
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
    assertThat(ResourceRepositoryManager.getAppResources(myModules.appModule)!!
                 .getResources(ResourceNamespace.RES_AUTO, ResourceType.RAW, "sample_raw_resource")).isEmpty()

    generateSources()

    assertThat(ResourceRepositoryManager.getAppResources(myModules.appModule)!!
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
