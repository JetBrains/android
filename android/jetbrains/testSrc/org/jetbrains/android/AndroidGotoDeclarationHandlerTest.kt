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

import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.res.TransitiveAarRClass
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.res.addBinaryAarDependency
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.goToElementAtCaret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.android.augment.ResourceLightField
import org.jetbrains.android.dom.AndroidValueResourcesTest
import org.jetbrains.android.dom.manifest.Manifest
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

  companion object {
    @JvmStatic
    fun navigateToElementAtCaretFromDifferentFile(myFixture: JavaCodeInsightTestFixture) = with(myFixture) {
      val element = GotoDeclarationAction.findTargetElement(project, editor, editor.caretModel.offset)
      val destinationFile = element!!.navigationElement.containingFile.virtualFile
      assertThat(destinationFile).isNotEqualTo(myFixture.file.virtualFile)

      openFileInEditor(destinationFile)
      (element as Navigatable).navigate(true)
    }
  }

  protected val JavaCodeInsightTestFixture.elementAtCurrentOffset: PsiElement get() = file.findElementAt(editor.caretModel.offset)!!

  override fun setUp() {
    super.setUp()
    val libModule = myAdditionalModules[0]
    // Remove the current lib manifest (has wrong package name) and copy a manifest with proper package into the lib module.
    deleteManifest(libModule)
    myFixture.copyFileToProject("util/lib/AndroidManifest.xml", "additionalModules/lib/AndroidManifest.xml")
  }

  data class DeclarationDescription(val directoryName: String, val surroundingTagText: String) {
    companion object {
      fun createDeclarationDescription(element: PsiElement): DeclarationDescription {
        return DeclarationDescription(
          element.containingFile.parent!!.name + "/" + element.containingFile.name,
          element.parentOfType<XmlTag>()?.text?.trim() ?: "")
      }
    }
  }

  fun testFrameworkAttrFromXml() {
    val file = myFixture.addFileToProject(
      "res/values/attrs.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <resources>
          <attr name="android:text${caret}Style" />
      </resources>
      """).virtualFile
    val declarations = getDeclarationsFrom(file)
    val listOfDescriptions = declarations.map {
      DeclarationDescription.createDeclarationDescription(it.navigationElement)
    }
    assertThat(listOfDescriptions).containsExactly(
      DeclarationDescription(
        "values/attrs.xml",
        //language=XML
        """<attr name="textStyle">
        <flag name="normal" value="0" />
        <flag name="bold" value="1" />
        <flag name="italic" value="2" />
    </attr>""".trim()))
  }

  fun testGotoResourceFromStartOfReferenceXml() {
    val file = myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <resources>
        <color name="colorPrimary">#3f51b5</color>
        <color name="colorPrimaryDark">${caret}@color/colorPrimary</color>
      </resources>
      """).virtualFile
    val declaration = getDeclarationsFrom(file)
    assertThat(declaration).hasLength(1)
    assertThat(describeElements(declaration))
      .isEqualTo("""
        values/colors.xml:3:
          <color name="colorPrimary">#3f51b5</color>
                      ~|~~~~~~~~~~~~~               

      """.trimIndent())
  }

  fun testGotoResourceFromStartOfReferenceLayoutXml() {
    myFixture.addFileToProject(
      "res/values/colors.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <resources>
        <color name="colorPrimary">#3f51b5</color>
      </resources>
      """)
    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout_main.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <TextView android:textColor="${caret}@color/colorPrimary"/>
        </LinearLayout>
      """).virtualFile
    val declaration = getDeclarationsFrom(layoutFile)
    assertThat(declaration).hasLength(1)
    assertThat(describeElements(declaration))
      .isEqualTo("""
        values/colors.xml:3:
          <color name="colorPrimary">#3f51b5</color>
                      ~|~~~~~~~~~~~~~               

      """.trimIndent())
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

  fun testGotoFlattenableResource() {
    myFixture.addFileToProject(
      "res/values/flattencolors.xml",
      //language=XML
      """<?xml version="1.0" encoding="utf-8"?>
      <resources>
          <color name="foo.bar" />
      </resources>
      """)
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2
        import android.app.Activity
        class ExampleActivity: Activity() {
            fun resource() {
                R.color.foo_${caret}bar
            }
        }
      """.trimIndent()).virtualFile
    assertEquals("values/flattencolors.xml:3:\n" +
                 "  <color name=\"foo.bar\" />\n" +
                 "              ~|~~~~~~~~  \n",
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoStringFromKotlin() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2

        import android.app.Activity

        class ExampleActivity: Activity() {
            fun resource() {
                R.string.he${caret}llo
            }
        }
      """.trimIndent())
    assertEquals("values/strings.xml:2:\n" +
                 "  <string name=\"hello\">hello</string>\n" +
                 "               ~|~~~~~~              \n",
                 describeElements(getDeclarationsFrom(file.virtualFile))
    )
  }

  fun testGotoDynamicId() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.copyFileToProject(basePath + "GotoId.java", "src/p1/p2/GotoId.java")
    assertEquals("""
                  layout/layout.xml:4:
                    <EditText android:id="@+id/anchor"/>
                                         ~|~~~~~~~~~~~~ 
                  values/ids.xml:2:
                    <item name="anchor" type="id"/>
                               ~|~~~~~~~           

                    """.trimIndent(),
                 describeElements(getDeclarationsFrom(file))
    )
  }

  fun testGotoDynamicIdFromKotlin() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2

        import android.app.Activity

        class ExampleActivity: Activity() {
            fun resource() {
                R.id.a${caret}nchor
            }
        }
      """.trimIndent())
    assertEquals("""
                  layout/layout.xml:4:
                    <EditText android:id="@+id/anchor"/>
                                         ~|~~~~~~~~~~~~ 
                  values/ids.xml:2:
                    <item name="anchor" type="id"/>
                               ~|~~~~~~~           

                 """.trimIndent(),
                 describeElements(getDeclarationsFrom(file.virtualFile))
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

  fun testGotoStyleableAttr_frameworkAttr() {
    myFixture.copyFileToProject(basePath + "attrs.xml", "res/values/attrs.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/ExampleActivity.java",
      //language=JAVA
      """
      package p1.p2;

      import com.android.internal.R;
      public class SomeClass {
          void f() {
            int attrId = R.styleable.MyView_android_${caret}maxHeight;
          }

      }
      """.trimIndent()
    )
    assertEquals(
      "values/attrs.xml:8:\n" +
      "  <attr name=\"android:maxHeight\" />\n" +
      "             ~|~~~~~~~~~~~~~~~~~~  \n",
      describeElements(getDeclarationsFrom(file.virtualFile))
    )
  }

  fun testGotoStyleableAttr_frameworkAttrKotlin() {
    myFixture.copyFileToProject(basePath + "attrs.xml", "res/values/attrs.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/ExampleActivity.kt",
      """
      package p1.p2
      import android.app.Activity

      class ExampleActivity: Activity() {
          fun resource() {
              R.styleable.MyView_android_m${caret}axHeight
          }
      }
      """.trimIndent()
    )
    assertEquals(
      "values/attrs.xml:8:\n" +
      "  <attr name=\"android:maxHeight\" />\n" +
      "             ~|~~~~~~~~~~~~~~~~~~  \n",
      describeElements(getDeclarationsFrom(file.virtualFile))
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

  fun testGotoDeclareStyleableFromXML() {
    myFixture.addFileToProject(
      "src/p1/p2/MyView.java",
      //language=Java
      """
      package p1.p2;

      import android.content.Context;
      import android.util.AttributeSet;
      import android.widget.Button;

      @SuppressWarnings("UnusedDeclaration")
      public class MyView extends Button {
          public MyView(Context context, AttributeSet attrs, int defStyle) {
              super(context, attrs, defStyle);
          }
      }
    """.trimIndent())
    val file = myFixture.addFileToProject(
      "res/values/styles.xml",
      //language=XML
      """
        <resources>
          <declare-styleable name="My<caret>View">
            <attr name="libAttr" format="string" />
          </declare-styleable>
        </resources>
        """.trimIndent()
    ).virtualFile
    val expectedResult =
        """
          values/styles.xml:2:
            <declare-styleable name="MyView">
                                    ~|~~~~~~~

        """.trimIndent()
    assertEquals(expectedResult,
                 describeElements(getDeclarationsFrom(file)))

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

  fun testGotoAliasResourceKotlin() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2

        import android.app.Activity
        import p1.p2.R as LibR

        class ExampleActivity: Activity() {
            fun resource() {
                LibR.id.a${caret}nchor
            }
        }
      """.trimIndent())
    assertEquals("""
                  layout/layout.xml:4:
                    <EditText android:id="@+id/anchor"/>
                                         ~|~~~~~~~~~~~~ 
                  values/ids.xml:2:
                    <item name="anchor" type="id"/>
                               ~|~~~~~~~           

                 """.trimIndent(),
                 describeElements(getDeclarationsFrom(file.virtualFile)))
  }

  fun testGotoStaticallyImportedResourceJava() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/ExampleActivity.java",
      //language=Java
      """
        package p1.p2;

        import android.app.Activity;
        import static p1.p2.R.id.anchor;

        public class ExampleActivity extends Activity {
            public static void fun() {
                anch<caret>or;
            }
        }
      """.trimIndent())
    assertEquals("""
                 layout/layout.xml:4:
                   <EditText android:id="@+id/anchor"/>
                                        ~|~~~~~~~~~~~~ 
                 values/ids.xml:2:
                   <item name="anchor" type="id"/>
                              ~|~~~~~~~           

                  """.trimIndent(),
                 describeElements(getDeclarationsFrom(file.virtualFile)))
  }

  fun testGotoFrameworkResourceKotlin() {
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2

        import android.app.Activity

        class ExampleActivity: Activity() {
            fun resource() {
                android.R.color.backg${caret}round_dark
            }
        }
      """.trimIndent())
    assertEquals("""
                  values/colors.xml:44:
                    <color name="background_dark">#ff000000</color>
                                ~|~~~~~~~~~~~~~~~~                 

                 """.trimIndent(),
                 describeElements(getDeclarationsFrom(file.virtualFile)))
  }

  fun testGotoStaticallyImportedResourceKotlin() {
    myFixture.copyFileToProject(basePath + "strings.xml", "res/values/strings.xml")
    myFixture.copyFileToProject(basePath + "ids.xml", "res/values/ids.xml")
    myFixture.copyFileToProject(basePath + "layout.xml", "res/layout/layout.xml")
    val file = myFixture.addFileToProject(
      "src/p1/p2/Activity.kt",
      """
        package p1.p2

        import android.app.Activity
        import p1.p2.R.id.anchor

        class ExampleActivity: Activity() {
            fun resource() {
                a${caret}nchor
            }
        }
      """.trimIndent())
    assertEquals("""
                  layout/layout.xml:4:
                    <EditText android:id="@+id/anchor"/>
                                         ~|~~~~~~~~~~~~ 
                  values/ids.xml:2:
                    <item name="anchor" type="id"/>
                               ~|~~~~~~~           

                 """.trimIndent(),
                 describeElements(getDeclarationsFrom(file.virtualFile)))
  }

  open fun testGotoAarResourceFromCode_libRClass() {
    addAarDependencyToMyModule()

    val virtualFile = myFixture.addFileToProject(
      "src/p1/p2/GotoAarStyle.java",
      // language=java
      """
       package p1.p2;

       public class GotoAarStyle {
           public void f() {
               int id1 = com.example.aarLib.R.style.Lib${caret}Style;
           }
       }
       """.trimIndent()
    ).virtualFile

    myFixture.configureFromExistingVirtualFile(virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
    assertThat(elementAtCaret.parent.parent).isInstanceOf(TransitiveAarRClass::class.java)

    assertEquals("values/styles.xml:5:\n" +
                 "  <style name=\"LibStyle\" parent=\"ParentStyle\">\n" +
                 "              ~|~~~~~~~~~                     \n",
                 describeElements(getDeclarationsFrom(virtualFile))
    )

    assertEquals("values/styles.xml:9:\n" +
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
                               int id1 = com.example.aarLib.R.attr.lib${caret}Attr;
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
      Manifest.getMainManifest(myFacet)!!.addPermission()?.apply { name.value = "com.example.SEND_MESSAGE" }
      Manifest.getMainManifest(myFacet)!!.addPermission()?.apply { name.value = "com.example.SEND-MESSAGE" }
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

    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)

    assertEquals(
      """
      AndroidManifest.xml:8:
        <permission android:name="com.example.SEND-MESSAGE" />
                                 ~|~~~~~~~~~~~~~~~~~~~~~~~~~
      AndroidManifest.xml:7:
        <permission android:name="com.example.SEND_MESSAGE" />
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

  override fun addAarDependencyToMyModule() {
    addAarDependency(myModule, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile.resolve(SdkConstants.FN_RESOURCE_TEXT).writeText(
        """
          int attr libAttr 0x7f010000
          int drawable libLogo 0x7f020000
          int style ParentStyle 0x7f040001
          int style LibStyle 0x7f040002
          int dimen smallText 0x7f080001
          int dimen libDimen 0x7f080002
          int[] styleable LibStyleable { 0x7f040001}
          int styleable LibStyleable_libAttr 0
          """.trimIndent()
      )
      resDir.resolve("values/styles.xml").writeText(
        // language=XML
        """
        <resources>
          <dimen name="smallText">10dp</dimen>
          <dimen name="libDimen">@dimen/smallText</dimen>
          <style name="ParentStyle"></style>
          <style name="LibStyle" parent="ParentStyle">
            <item name="android:textSize">@dimen/libDimen</item>
          </style>
          <declare-styleable name="LibStyleable">
            <attr name="libAttr" format="string" />
          </declare-styleable>
        </resources>
        """.trimIndent()
      )
      resDir.resolve("drawable").mkdirs()
      resDir.resolve("drawable/libLogo.xml").writeText(
        // language=XML
        """
        <vector xmlns:android="http://schemas.android.com/apk/res/android">
          <group android:name="g">
            <vector android:pathData="xxx" />
          </group>
        </vector>
        """.trimIndent()
      )
    }

    // Sanity check.
    val appResources = StudioResourceRepositoryManager.getAppResources(myFacet)
    assertSize(1, appResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.STYLE, "LibStyle"))
    assertSize(1, appResources.getResources(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "libAttr"))
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

    assertEquals("values/styles.xml:5:\n" +
                 "  <style name=\"LibStyle\" parent=\"ParentStyle\">\n" +
                 "              ~|~~~~~~~~~                     \n",
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

    assertEquals("values/styles.xml:9:\n" +
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

  fun testGotoAarFileResourceFromCode_ownRClass() {
    addAarDependencyToMyModule()

    val javaFile = myFixture.addFileToProject(
      "src/p1/p2/GotoAarDrawable.java",
      // language=java
      """
      package p1.p2;

      public class GotoAarDrawable {
          public void f() {
              int id1 = R.drawable.lib${caret}Logo;
          }
      }
      """.trimIndent()
    )

    val targets = getDeclarationsFrom(javaFile.virtualFile)
    assertThat(targets).hasLength(1)
    val target = targets.single()
    assertThat(target).isInstanceOf(PsiFile::class.java)
    assertThat((target as PsiFile).virtualFile.name).isEqualTo("libLogo.xml")
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

    navigateToElementAtCaretFromDifferentFile(myFixture)
    assertThat(elementAtCurrentOffset.text).isEqualTo("LibStyle")
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text)
      .isEqualTo("""
        <style name="LibStyle" parent="ParentStyle">
            <item name="android:textSize">@dimen/libDimen</item>
          </style>""".trimIndent())

    // ParentStyleConverter:
    moveCaret("""parent="Parent|Style""")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.text).isEqualTo("ParentStyle")
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo("""<style name="ParentStyle"></style>""")

    // StyleItemConverter:
    moveCaret("""<item name="android:textSize">@dimen/|libDimen""")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo("""<dimen name="libDimen">@dimen/smallText</dimen>""")

    // ResourceReferenceConverter:
    moveCaret("""@dimen/|smallText""")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.parentOfType<XmlTag>()!!.text).isEqualTo("""<dimen name="smallText">10dp</dimen>""")
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

    navigateToElementAtCaretFromDifferentFile(myFixture)
    assertThat(elementAtCurrentOffset.text).isEqualTo("Theme.InputMethod")
    editor.caretModel.moveToOffset(editor.caretModel.offset + 35)
    assertThat(elementAtCurrentOffset.text).isEqualTo("Theme.Panel")
    assertThat(elementAtCurrentOffset.parentOfType<XmlAttribute>()!!.text).isEqualTo("parent=\"Theme.Panel\"")
    goToElementAtCaret()
    assertThat(elementAtCurrentOffset.parentOfType<XmlAttribute>()!!.text).isEqualTo("name=\"Theme.Panel\"")
  }
}

class AndroidGotoDeclarationHandlerTestNamespaced : AndroidGotoDeclarationHandlerTestBase() {
  override fun setUp() {
    super.setUp()
    enableNamespacing(myFacet, "p1.p2")
    enableNamespacing(
      AndroidFacet.getInstance(getAdditionalModuleByName("lib")!!)!!,
      "p1.p2.lib"
    )
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
    addBinaryAarDependency(myModule)
  }

  override fun testGotoAarResourceFromCode_libRClass() {
    addAarDependencyToMyModule()

    val file = myFixture.addFileToProject(
      "src/p1/p2/AarString.java",
      // language=java
      """
      package p1.p2;

      public class AarString {
          public void f() {
              int id1 = com.example.mylibrary.R.string.my_aar_str${caret}ing;
          }
      }
      """.trimIndent()
    )

    assertThat(describeElements(getDeclarationsFrom(file.virtualFile)).trimEnd()).isEqualTo("""
        values/strings.xml:3:
          <string name="my_aar_string">This string came from an AARv2</string>
                       ~|~~~~~~~~~~~~~~
        """.trimIndent())
  }
}
