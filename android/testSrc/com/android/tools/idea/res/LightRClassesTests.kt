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

import com.android.builder.model.AndroidProject
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.facet.AndroidFacet

/**
 * Tests for the whole setup of light, in-memory R classes.
 *
 * @see ProjectSystemPsiElementFinder
 * @see ProjectLightResourceClassService
 */
sealed class LightRClassesTestBase : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    StudioFlags.IN_MEMORY_R_CLASSES.override(true)
    // No need to copy R.java into gen!
  }

  /**
   * Sets the language level to avoid the module system visibility rules.
   */
  override fun getLanguageLevel() = LanguageLevel.JDK_1_8

  override fun tearDown() {
    try {
      StudioFlags.IN_MEMORY_R_CLASSES.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  protected fun resolveReferenceUnderCaret(): PsiElement? {
    // We cannot use myFixture.elementAtCaret or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED because JavaTargetElementEvaluator doesn't
    // consider synthetic PSI elements as "acceptable" and just returns null instead, so it wouldn't test much.
    val rReference = TargetElementUtil.findReference(myFixture.editor)!!
    val resolve = rReference.resolve()
    return resolve
  }

  class SingleModule : LightRClassesTestBase() {
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

    fun testFindClass() {
      val javaPsiFacade = JavaPsiFacade.getInstance(project)
      assertThat(javaPsiFacade.findClass("p1.p2.R", myModule.moduleScope)).named("module R class").isNotNull()
      assertThat(javaPsiFacade.findClass("p1.p2.R.string", myModule.moduleScope)).named("existing subclass").isNotNull()
      assertThat(javaPsiFacade.findClass("p1.p2.R.color", myModule.moduleScope)).named("non-existing subclass").isNull()
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
                getResources().getString(p1.p2.${caret});
            }
        }
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(activity.virtualFile)
      myFixture.completeBasic()

      assertThat(myFixture.lookupElementStrings).containsExactly("R", "MainActivity")
    }

    fun testInnerClassesCompletion() {
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

      // TODO(b/80425291): remove "sample"
      assertThat(myFixture.lookupElementStrings).containsExactly("class", "string", "sample")
    }

    fun testResourceNamesCompletion() {
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
  }

  /**
   * Tests with a module that should not see R class from another module.
   */
  class UnrelatedModules : LightRClassesTestBase() {

    override fun configureAdditionalModules(
      projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
      modules: MutableList<MyAdditionalModuleData>
    ) {
      addModuleWithAndroidFacet(projectBuilder, modules, "unrelatedLib", AndroidProject.PROJECT_TYPE_LIBRARY, false)
    }

    override fun setUp() {
      super.setUp()

      runWriteCommandAction(project) {
        getAdditionalModuleByName("unrelatedLib")!!
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

  class NamespacedModuleWithAar : LightRClassesTestBase() {

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
      assertThat(resolveReferenceUnderCaret()).isInstanceOf(AarPackageRClass::class.java)
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
}
