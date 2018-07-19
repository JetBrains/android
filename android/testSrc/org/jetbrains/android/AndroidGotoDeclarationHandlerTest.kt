/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.jetbrains.android

import com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.ResourceRepositoryManager
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.goToElementAtCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import org.jetbrains.android.dom.AndroidValueResourcesTest
import org.jetbrains.android.facet.AndroidFacet

/**
 * Note: There are some additional tests for goto declaration in [AndroidValueResourcesTest] such
 * as [AndroidValueResourcesTest.testDeclareStyleableNameNavigation1], as well as in
 * [AndroidResourcesLineMarkerTest]
 *
 * TODO: Test the manifest-oriented logic in [AndroidGotoDeclarationHandler]
 * TODO: Test jumping from a layout to an XML declare styleable attribute!
 */
abstract class AndroidGotoDeclarationHandlerTestBase : AndroidTestCase() {
  val basePath = "/gotoDeclaration/"

  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: List<MyAdditionalModuleData>) {
    addModuleWithAndroidFacet(projectBuilder, modules, "lib", PROJECT_TYPE_LIBRARY)
  }

  override fun setUp() {
    super.setUp()
    val libModule = myAdditionalModules[0]
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")
  }

  fun testGotoString() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.copyFileToProject(basePath + "GotoString.java", "src/p1/p2/GotoString.java")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoDynamicId() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.copyFileToProject(basePath + "GotoId.java", "src/p1/p2/GotoId.java")
    assertEquals("layout/layout.xml:4:\n" +
                 "  <EditText android:id=\"@+id/anchor\"/>\n" +
                 "                       ~|~~~~~~~~~~~~ \n" +
                 "values/ids.xml:2:\n" +
                 "  <item name=\"anchor\" type=\"id\"/>\n" +
                 "             ~|~~~~~~~           \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testLanguageFolders() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-no/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-en/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-en-rUS/strings.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.copyFileToProject(basePath + "GotoString.java", "src/p1/p2/GotoString.java")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en-rUS/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-no/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testLanguageFoldersFromXml() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-no/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-en/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values-en-rUS/strings.xml")
    val file = myFixture.copyFileToProject(basePath + "layout2.xml", "res/layout/layout2.xml")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-en-rUS/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n" +
                 "values-no/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoStringFromXml() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    val file = myFixture.copyFileToProject(basePath + "layout2.xml", "res/layout/layout2.xml")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoStyleableAttr() {
    myFixture.copyFileToProject(basePath + "attrs.xml", "res/values/attrs.xml")
    val file = myFixture.copyFileToProject(basePath + "MyView2.java", "src/p1/p2/MyView.java")
    assertEquals("values/attrs.xml:4:\n" +
                 "  <attr name=\"answer\">\n" +
                 "             ~|~~~~~~~\n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoActivityFromToolsContext() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "MyActivity.java", "src/p1/p2/MyActivity.java")
    val file = myFixture.copyFileToProject(basePath + "tools_layout1.xml", "res/layout/layout2.xml")
    assertEquals("MyActivity.java:6:\n" +
                 "  public class MyActivity extends Activity {\n" +
                 "  ~~~~~~~~~~~~~|~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoResourceFromToolsAttribute() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    val file = myFixture.copyFileToProject(basePath + "tools_layout2.xml", "res/layout/layout2.xml")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoStringFromLib_importedRClass() {
    // Add some lib string resources.
    myFixture.copyFileToProject(basePath + "strings.xml", "additionalModules/lib/res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "additionalModules/lib/res/layout/layout.xml")

    // Remove the manifest from the main module, this is a regression test for b/26160081.
    deleteManifest(myModule)

    val file = myFixture.copyFileToProject(basePath + "ImportedGotoString.java", "src/p1/p2/ImportedGotoString.java")
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  open fun testGotoAarResourceFromCode_libRClass() {
    addAarDependencyToMyModule()

    assertEquals("values/styles.xml:2:\n" +
                 "  <style name=\"LibStyle\"></style>\n" +
                 "              ~|~~~~~~~~~        \n",
                 describeElements(
                   getDeclarationsFrom(
                     myFixture.addFileToProject(
                       "src/p1/p2/GotoAarStyle.java",
                       // language=java
                       """
                       package p1.p2;

                       public class GotoAarStyle {
                           public void f() {
                               int id1 = p1.p2.aarLib.R.style.Lib${caret}Style;
                           }
                       }
                       """.trimIndent()
                     ).virtualFile
                   )
                 )
    )

    assertEquals("values/styles.xml:4:\n" +
                 "  <attr name=\"libAttr\" format=\"string\" />\n" +
                 "             ~|~~~~~~~~                  \n",
                 describeElements(
                   getDeclarationsFrom(
                     myFixture.addFileToProject(
                       "src/p1/p2/GotoAarStyleableAttr.java",
                       // language=java
                       """
                       package p1.p2;

                       public class GotoAarStyleableAttr {
                           public void f() {
                               int id1 = p1.p2.aarLib.R.style.lib${caret}Attr;
                           }
                       }
                       """.trimIndent()
                     ).virtualFile
                   )
                 )
    )
  }

  open fun testGotoPermission() {
    WriteCommandAction.runWriteCommandAction(project) {
      myFacet.manifest!!.addPermission()?.apply { name.value = "com.example.SEND_MESSAGE" }
      myFacet.manifest!!.addPermission()?.apply { name.value = "com.example.SEND-MESSAGE" }
    }

    val file = myFixture.addFileToProject(
      "src/p1/p2/GotoPermission.java",
      // language=java
      """
      package p1.p2;

      public class GotoPermission {
          public void f() {
              String permissionName = Manifest.permission.SEND${caret}_MESSAGE;
          }
      }
      """.trimIndent()
    )

    assertEquals(
      """
      AndroidManifest.xml:7:
        <permission android:name="com.example.SEND_MESSAGE" />
                                 ~|~~~~~~~~~~~~~~~~~~~~~~~~~
      AndroidManifest.xml:8:
        <permission android:name="com.example.SEND-MESSAGE" />
                                 ~|~~~~~~~~~~~~~~~~~~~~~~~~~
      """.trimIndent(),
      describeElements(getDeclarationsFrom(file.virtualFile))
        .lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotEmpty() }
        .joinToString(separator = "\n")
    )
  }

  protected fun getDeclarationsFrom(file: VirtualFile): Array<PsiElement> {
    myFixture.configureFromExistingVirtualFile(file)

    // AndroidGotoDeclarationHandler only handles .java files. We also want to check .xml files, so
    // we use GotoDeclarationAction instead of creating AndroidGotoDeclarationHandler and invoking getGotoDeclarationTargets
    // on it directly.
    return GotoDeclarationAction.findAllTargetElements(myFixture.project, myFixture.editor, myFixture.caretOffset)
             .takeUnless { it.isEmpty() }
           ?: TargetElementUtil.findReference(myFixture.editor, myFixture.caretOffset)
             ?.let { TargetElementUtil.getInstance().getTargetCandidates(it) }
             ?.let { PsiUtilCore.toPsiElementArray(it) }
           ?: PsiElement.EMPTY_ARRAY
  }

  protected abstract fun addAarDependencyToMyModule()
}

class AndroidGotoDeclarationHandlerTestNonNamespaced : AndroidGotoDeclarationHandlerTestBase() {

  override fun setUp() {
    super.setUp()
    copyRJavaToGeneratedSources()
    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      myFixture.copyFileToProject("Manifest.java", "gen/p1/p2/Manifest.java")
      myFixture.copyFileToProject("util/lib/R.java", "additionalModules/lib/gen/p1/p2/lib/R.java")
    }
  }

  override fun addAarDependencyToMyModule() {
    addAarDependency(myModule, "aarLib", "com.example.aarLib") { resDir ->
      resDir.resolve("values/styles.xml").writeText(
        """
        <resources>
          <style name="ParentStyle"></style>
          <style name="LibStyle" parent="ParentStyle"></style>
          <declare-styleable name="LibStyleable">
            <attr name="libAttr" format="string" />
          </declare-styleable>
        </resources>
        """.trimIndent()
      )
    }

    // Sanity check.
    val appResources = ResourceRepositoryManager.getAppResources(myFacet)
    assertSize(1, appResources.getResourceItems(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "LibStyle"))
    assertSize(1, appResources.getResourceItems(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "libAttr"))

    if (!StudioFlags.IN_MEMORY_R_CLASSES.get()) {
      myFixture.addFileToProject(
        "gen/p1/p2/aarLib/R.java",
        // language=java
        """
      package p1.p2.aarLib;

      public final class R {
        public static final class attr {
          public static final int libAttr = 0x7f010001;
        }
        public static final class style {
          public static final int LibStyle = 0x7f020001;
        }
      }
      """.trimIndent()
      )
    }
  }

  fun testGotoStringFromLib_ownRClass() {
    // Add some lib string resources.
    myFixture.copyFileToProject(basePath + "strings.xml", "additionalModules/lib/res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "additionalModules/lib/res/layout/layout.xml")

    val file = myFixture.addFileToProject(
      "src/p1/p2/LibString.java",
      // language=java
      """
      package p1.p2;

      public class LibString {
          public void f() {
              int id1 = R.string.hel${caret}lo;
          }
      }
      """.trimIndent()
    )

    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file.virtualFile))
    )
  }

  fun testGotoAarResourceFromCode_ownRClass() {
    addAarDependencyToMyModule()

    assertEquals("values/styles.xml:3:\n" +
                 "  <style name=\"LibStyle\" parent=\"ParentStyle\"></style>\n" +
                 "              ~|~~~~~~~~~                             \n",
                 describeElements(
                   getDeclarationsFrom(
                     myFixture.addFileToProject(
                       "src/p1/p2/GotoAarStyle.java",
                       // language=java
                       """
                       package p1.p2;

                       public class GotoAarStyle {
                           public void f() {
                               int id1 = R.style.Lib${caret}Style;
                           }
                       }
                       """.trimIndent()
                     ).virtualFile
                   )
                 )
    )

    assertEquals("values/styles.xml:5:\n" +
                 "  <attr name=\"libAttr\" format=\"string\" />\n" +
                 "             ~|~~~~~~~~                  \n",
                 describeElements(
                   getDeclarationsFrom(
                     myFixture.addFileToProject(
                       "src/p1/p2/GotoAarStyleableAttr.java",
                       // language=java
                       """
                       package p1.p2;

                       public class GotoAarStyleableAttr {
                           public void f() {
                               int id1 = R.attr.lib${caret}Attr;
                           }
                       }
                       """.trimIndent()
                     ).virtualFile
                   )
                 )
    )
  }

  override fun testGotoAarResourceFromCode_libRClass() {
    // TODO(b/109652745): fix handling of AAR R classes.
  }

  fun testGotoAarResourceFromAarXml() = with(myFixture) {
    addAarDependencyToMyModule()

    configureFromExistingVirtualFile(
      addFileToProject(
        "res/values/styles.xml",
        // language=xml
        """
      <!--suppress CheckTagEmptyBody -->
      <resources>
        <style name="AppStyle" parent="Lib${caret}Style"></style>
      </resources>
    """.trimIndent()
      ).virtualFile
    )

    navigateToElementAtCaretFromDifferentFile()
    assertThat(elementAtCurrentOffset.text).isEqualTo("LibStyle")
    assertThat(elementAtCaret.parentOfType<XmlTag>()!!.text).isEqualTo("<style name=\"LibStyle\" parent=\"ParentStyle\"></style>")
    editor.caretModel.moveToOffset(editor.caretModel.offset + 20)
    assertThat(elementAtCurrentOffset.text).isEqualTo("ParentStyle")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.text).isEqualTo("ParentStyle")
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo("<style name=\"ParentStyle\"></style>")
  }

  fun testGotoFrameworkResourceFromFrameworkXml() = with(myFixture) {
    addAarDependencyToMyModule()

    configureFromExistingVirtualFile(
      addFileToProject(
        "res/values/styles.xml",
        // language=xml
        """
      <!--suppress CheckTagEmptyBody -->
      <resources>
        <style name="AppStyle" parent="android:Theme.${caret}InputMethod"></style>
      </resources>
    """.trimIndent()
      ).virtualFile
    )

    navigateToElementAtCaretFromDifferentFile()
    assertThat(elementAtCurrentOffset.text).isEqualTo("Theme.InputMethod")
    editor.caretModel.moveToOffset(editor.caretModel.offset + 35)
    assertThat(elementAtCurrentOffset.text).isEqualTo("Theme.Panel")
    assertThat(elementAtCurrentOffset.parentOfType<XmlAttribute>()!!.text).isEqualTo("parent=\"Theme.Panel\"")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.parentOfType<XmlAttribute>()!!.text).isEqualTo("name=\"Theme.Panel\"")
  }

  private fun navigateToElementAtCaretFromDifferentFile() = with(myFixture) {
    val element =
      GotoDeclarationAction.findTargetElement(project, editor, editor.caretModel.offset) as NavigatablePsiElement
    val destinationFile = element.navigationElement.containingFile.virtualFile
    assertThat(destinationFile).isNotEqualTo(myFixture.file.virtualFile)

    openFileInEditor(destinationFile)
    element.navigate(true)
  }

  private val JavaCodeInsightTestFixture.elementAtCurrentOffset: PsiElement get() = file.findElementAt(editor.caretModel.offset)!!
}

class AndroidGotoDeclarationHandlerTestNamespaced : AndroidGotoDeclarationHandlerTestBase() {
  override fun setUp() {
    super.setUp()
    StudioFlags.IN_MEMORY_R_CLASSES.override(true)
    enableNamespacing(myFacet, "p1.p2")
    enableNamespacing(
      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!,
      "p1.p2.lib"
    )
  }

  override fun tearDown() {
    try {
      StudioFlags.IN_MEMORY_R_CLASSES.clearOverride()
    }
    finally {
      super.tearDown()
    }
  }

  fun testGotoStringFromLib_ownRClass() {
    // Add some lib string resources.
    myFixture.copyFileToProject(basePath + "strings.xml", "additionalModules/lib/res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "additionalModules/lib/res/layout/layout.xml")

    val file = myFixture.addFileToProject(
      "src/p1/p2/LibString.java",
      // language=java
      """
      package p1.p2;

      public class LibString {
          public void f() {
              int id1 = R.string.hel${caret}lo;
          }
      }
      """.trimIndent()
    )

    assertEmpty(getDeclarationsFrom(file.virtualFile))
  }

  override fun addAarDependencyToMyModule() {
    TODO("not implemented") // TODO(b/110082720): implement support for source attachments.
  }

  fun testGotoAarResourceFromCode_ownRClass() {
    // TODO(b/110082720): implement support for source attachments.
  }

  override fun testGotoAarResourceFromCode_libRClass() {
    // TODO(b/110082720): implement support for source attachments.
  }
}
