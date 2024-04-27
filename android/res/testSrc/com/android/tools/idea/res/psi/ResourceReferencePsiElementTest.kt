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
package com.android.tools.idea.res.psi

import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.res.ModuleRClass
import com.android.tools.idea.res.TransitiveAarRClass
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.compiled.ClsFieldImpl
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.usageView.UsageViewLongNameLocation
import com.intellij.usageView.UsageViewShortNameLocation
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.android.augment.ResourceLightField

class ResourceReferencePsiElementTest : AndroidTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      "res/values/colors.xml",
      // language=XML
      """<resources><color name="colorPrimary">#008577</color></resources>"""
    )
  }

  fun testReferencesToAAR_equivalent() {
    addAarDependency(myFixture, myModule, "aarLib", "com.example.aarLib") { resDir ->
      resDir.parentFile
        .resolve(SdkConstants.FN_RESOURCE_TEXT)
        .writeText("""int color colorPrimary 0x7f010001""")
      resDir
        .resolve("values/colors.xml")
        .writeText(
          // language=XML
          """
        <resources>
          <color name="colorPrimary">#008577</color>
        </resources>
        """
            .trimIndent()
        )
    }

    // All three references are to the same resource in a non-namespaced project
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.kt",
        // language=kotlin
        """
       package p1.p2
       class Foo {
         fun example() {
           R.color.colorPrimary${caret}
           p1.p2.R.color.colorPrimary
           com.example.aarLib.R.color.colorPrimary
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    // Resource in ModuleRClass
    val moduleRClassResource = myFixture.elementAtCaret
    assertThat(moduleRClassResource.parent.parent).isInstanceOf(ModuleRClass::class.java)

    // Resource in ModuleRClass
    myFixture.moveCaret("p1.p2.R.color.color|Primary")
    val qualifiedModuleRClassResource = myFixture.elementAtCaret
    assertThat(qualifiedModuleRClassResource.parent.parent).isInstanceOf(ModuleRClass::class.java)

    // Resource in TransitiveAaaRClass
    myFixture.moveCaret("com.example.aarLib.R.color.color|Primary")
    val transitiveAarRClassResource = myFixture.elementAtCaret
    assertThat(transitiveAarRClassResource.parent.parent)
      .isInstanceOf(TransitiveAarRClass::class.java)

    assertThat(
      ResourceReferencePsiElement.create(moduleRClassResource)!!.isEquivalentTo(
        ResourceReferencePsiElement.create(qualifiedModuleRClassResource)!!
      )
    )
    assertThat(
      ResourceReferencePsiElement.create(moduleRClassResource)!!.isEquivalentTo(
        ResourceReferencePsiElement.create(transitiveAarRClassResource)!!
      )
    )
  }

  fun testClsFieldImplKotlin() {
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.kt",
        // language=kotlin
        """
       package p1.p2
       class Foo {
         fun example() {
           android.R.color.b${caret}lack
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ClsFieldImpl::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "black"))
    assertThat(fakePsiElement.getIcon(false)).isNotNull()
  }

  fun testClsFieldImplJava() {
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.java",
        // language=java
        """
       package p1.p2;
       public class Foo {
         public static void example() {
           int black = android.R.color.bla${caret}ck;
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ClsFieldImpl::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "black"))
    assertThat(fakePsiElement.getIcon(false)).isNotNull()
  }

  fun testResourceLightFieldKotlin() {
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.kt",
        // language=kotlin
        """
       package p1.p2
       class Foo {
         fun example() {
           R.color.color${caret}Primary
           android.R.color.black
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
    assertThat(fakePsiElement.getIcon(false)).isNotNull()
  }

  fun testResourceLightFieldJava() {
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.java",
        // language=java
        """
       package p1.p2;
       public class Foo {
         public static void example() {
           int colorPrimary = R.color.color${caret}Primary;
           int black = android.R.color.black;
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testResourceLightFieldFlattenableResourceName() {
    myFixture.addFileToProject(
      "res/values/flattenColors.xml",
      // language=XML
      """
       <resources>
         <color name="foo.bar">#3700B3</color>
       </resources>"""
        .trimIndent()
    )
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.java",
        // language=java
        """
       package p1.p2;
       public class Foo {
         public static void example() {
           int colorPrimary = R.color.foo${caret}_bar;
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceLightField::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "foo.bar"))
  }

  fun testResourceReferencePsiElementDeclaration() {
    myFixture.configureByFile("res/values/colors.xml")
    myFixture.moveCaret("colorPri|mary")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testResourceReferenceInLayoutResAuto() {
    val file =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
         <LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:textColor="@color/col${caret}orPrimary"/>
        </LinearLayout>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val fakePsiElement = myFixture.elementAtCaret as ResourceReferencePsiElement
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.COLOR, "colorPrimary"))
  }

  fun testFileResourceReferenceInLayoutAndroid() {
    val file =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@android:color/secondary${caret}_text_dark"/>
      </LinearLayout>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val fakePsiElement = myFixture.elementAtCaret as ResourceReferencePsiElement
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(
        ResourceReference(ResourceNamespace.ANDROID, ResourceType.COLOR, "secondary_text_dark")
      )
  }

  fun testIdDeclarationInLayout() {
    val file =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:id="@+id/text${caret}view"
              android:layout_width="match_parent"
              android:layout_height="match_parent"/>
      </LinearLayout>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val fakePsiElement = myFixture.elementAtCaret as ResourceReferencePsiElement
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ID, "textview"))
  }

  fun testAttrValueResourceAndroid() {
    val file =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
       <resources>
         <attr name="android:text${caret}Style"/>
       </resources>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val elementAtCaret =
      myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<XmlAttributeValue>()!!
    val fakePsiElement =
      ResourceReferencePsiElement.create(elementAtCaret) as ResourceReferencePsiElement
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "textStyle"))
  }

  /**
   * Regression test for http://b.android.com/170867656 Bug: res/values files should not be
   * considered resources, only the tags within.
   */
  fun testStringsFile() {
    val file =
      myFixture.addFileToProject(
        "res/values/strings.xml",
        // language=XML
        """
        <resources>
          <string name="Foo">Bar</string>
        </resources>"""
          .trimMargin()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat(file).isInstanceOf(PsiFile::class.java)
    assertThat(ResourceReferencePsiElement.create(file)).isNull()
    myFixture.moveCaret("name=\"Fo|o\"")
    val psiElement = myFixture.elementAtCaret
    assertThat(ResourceReferencePsiElement.create(psiElement)!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.STRING, "Foo"))
  }

  fun testDrawableFile() {
    val file =
      myFixture.addFileToProject(
        "res/drawable/test.xml",
        // language=XML
        """<shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="rectangle"
        android:tint="#FF0000">
       </shape>"""
          .trimMargin()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat(file).isInstanceOf(PsiFile::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(file) as ResourceReferencePsiElement
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.DRAWABLE, "test"))
  }

  fun testDrawableFileInvalidName() {
    // A file with an invalid resource name is not a resource, even though it might be in the
    // res/drawable folder. It is also not picked up
    // by the Resource Repository.
    val file =
      myFixture.addFileToProject(
        "res/drawable/test file.xml",
        // language=XML
        """<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"/>"""
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    assertThat(file).isInstanceOf(PsiFile::class.java)
    val invalidFileElement = ResourceReferencePsiElement.create(file)
    assertThat(invalidFileElement).isNull()
  }

  fun testStyleItemAndroid() {
    val psiFile =
      myFixture.addFileToProject(
        "res/values/styles.xml",
        // language=XML
        """
       <resources>
         <style name="TextAppearance.Theme.PlainText">
           <item name="android:textStyle"/>
         </style>
       </resources>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("android:textS|tyle")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement = ResourceReferencePsiElement.create(elementAtCaret)
    assertThat(fakePsiElement).isNotNull()
    assertThat(fakePsiElement!!.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "textStyle"))
  }

  fun testStyleItemResAuto() {
    myFixture.addFileToProject(
      "res/values/coordinatorlayout_attrs.xml",
      // language=XML
      """
        <resources>
          <declare-styleable name="CoordinatorLayout_Layout">
            <attr name="layout_behavior" format="string" />
          </declare-styleable>
        </resources>
        """
        .trimIndent()
    )
    val psiFile =
      myFixture.addFileToProject(
        "res/values/styles.xml",
        // language=XML
        """
       <resources>
         <style name="TextAppearance.Theme.PlainText">
           <item name="layout_behavior"/>
         </style>
       </resources>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("la|yout_behavior")
    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(ResourceReferencePsiElement::class.java)
    val fakePsiElement =
      ResourceReferencePsiElement.create(elementAtCaret) as ResourceReferencePsiElement
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(
        ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_behavior")
      )
  }

  fun testReferenceStyleAttribute() {
    myFixture.addFileToProject(
      "res/values/attrs.xml",
      // language=XML
      """
        <resources>
          <attr name="button_text" format="string" />
         <style name="TextAppearance.Theme.PlainText">
           <item name="button_text">Example Text</item>
         </style>
        </resources>
        """
        .trimIndent()
    )
    val file =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:text="?attr/button_text"/>
      </LinearLayout>
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.moveCaret("attr/butto|n_text")
    val fakePsiElement = myFixture.elementAtCaret as ResourceReferencePsiElement
    assertThat(fakePsiElement.resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "button_text"))
  }

  fun testElementRepresentation_equivalent() {
    val file =
      myFixture.addFileToProject(
        "/src/p1/p2/Foo.java",
        // language=java
        """
       package p1.p2;
       public class Foo {
         public static void example() {
           int colorPrimary = R.color.color${caret}Primary;
           int black = android.R.color.black;
         }
       }
       """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val resourceLightField = myFixture.elementAtCaret
    assertThat(resourceLightField).isInstanceOf(ResourceLightField::class.java)

    myFixture.configureByFile("res/values/colors.xml")
    myFixture.moveCaret("colorPri|mary")
    val resourceNameElement = myFixture.elementAtCaret
    assertThat(resourceNameElement).isInstanceOf(ResourceReferencePsiElement::class.java)

    val layoutFile =
      myFixture.addFileToProject(
        "res/layout/layout.xml",
        // language=XML
        """
       <LinearLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
          <TextView
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:textColor="@color/col${caret}orPrimary"/>
      </LinearLayout>"""
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(layoutFile.virtualFile)

    val resourceReferenceElement = myFixture.elementAtCaret
    assertThat(resourceReferenceElement).isInstanceOf(ResourceReferencePsiElement::class.java)

    val listOfElements = listOf(resourceLightField, resourceNameElement, resourceReferenceElement)
    for (element in listOfElements) {
      val referencePsiElement = ResourceReferencePsiElement.create(element)
      for (compareElement in listOfElements) {
        assertThat(referencePsiElement?.isEquivalentTo(compareElement)).isTrue()
      }
    }
  }

  fun testResourceReferencePsiElementDescription() {
    myFixture.configureByFile("res/values/colors.xml")
    myFixture.moveCaret("colorPri|mary")
    val elementAtCaret = myFixture.elementAtCaret as ResourceReferencePsiElement
    checkElementDescriptions(
      elementAtCaret,
      "colorPrimary",
      "@color/colorPrimary",
      "Color Resource"
    )
    val frameworkElement =
      ResourceReferencePsiElement(
        elementAtCaret,
        ResourceReference(ResourceNamespace.ANDROID, ResourceType.STRING, "example")
      )
    checkElementDescriptions(
      frameworkElement,
      "example",
      "@android:string/example",
      "String Resource"
    )
  }

  private fun checkElementDescriptions(
    element: ResourceReferencePsiElement,
    expectedShortName: String,
    expectedLongName: String,
    expectedTypeDescription: String
  ) {
    assertThat(
        ElementDescriptionUtil.getElementDescription(element, UsageViewShortNameLocation.INSTANCE)
      )
      .isEqualTo(expectedShortName)
    assertThat(
        ElementDescriptionUtil.getElementDescription(element, UsageViewLongNameLocation.INSTANCE)
      )
      .isEqualTo(expectedLongName)
    assertThat(
        ElementDescriptionUtil.getElementDescription(element, UsageViewTypeLocation.INSTANCE)
      )
      .isEqualTo(expectedTypeDescription)
  }
}
