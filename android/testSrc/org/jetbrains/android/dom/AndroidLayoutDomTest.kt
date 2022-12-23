package org.jetbrains.android.dom

import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.resources.ResourceType
import com.android.tools.idea.res.addAarDependency
import com.android.tools.idea.res.addBinaryAarDependency
import com.android.tools.idea.res.psi.ResourceReferencePsiElement
import com.android.tools.idea.testing.caret
import com.android.tools.idea.testing.loadNewFile
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.parentOfType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.xml.DomManager
import org.intellij.lang.annotations.Language
import org.jetbrains.android.dom.converters.ResourceReferenceConverter
import org.jetbrains.android.dom.resources.ResourceValue
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection
import org.jetbrains.android.inspections.CreateFileResourceQuickFix
import org.jetbrains.android.inspections.CreateValueResourceQuickFix
import org.jetbrains.android.intentions.AndroidCreateOnClickHandlerAction
import org.jetbrains.android.refactoring.isAndroidx
import org.jetbrains.android.refactoring.setAndroidxProperties
import org.junit.Test
import java.io.IOException
import java.util.Arrays

/**
 * Tests semantic highlighting and completion in layout XML files.
 */
class AndroidLayoutDomTest : AndroidDomTestCase("dom/layout") {
  @Language("JAVA")
  private val recyclerViewOld =
      """
      package android.support.v7.widget;

      import android.view.ViewGroup;

      public class RecyclerView extends ViewGroup {
        public abstract static class LayoutManager {
        }
      }

      public class GridLayoutManager extends RecyclerView.LayoutManager {
      }

      public class LinearLayoutManager extends RecyclerView.LayoutManager {
      }
      """.trimIndent()

  @Language("JAVA")
  private val recyclerViewNew =
    """
      package androidx.recyclerview.widget;

      import android.view.ViewGroup;

      public class RecyclerView extends ViewGroup {
        public abstract static class LayoutManager {
        }
      }

      public class GridLayoutManager extends RecyclerView.LayoutManager {
      }

      public class LinearLayoutManager extends RecyclerView.LayoutManager {
      }
      """.trimIndent()

  @Language("XML")
  private val recyclerViewAttrs =
      """
      <resources>
        <declare-styleable name="RecyclerView">
          <attr name="layoutManager" format="string" />
        </declare-styleable>
        <string name='my_layout_manager'>com.example.MyLayoutManager</string>
      </resources>
      """.trimIndent()

  @Language("JAVA")
  private val myLayoutManager =
      """
      package p1.p2;

      import android.support.v7.widget.LinearLayoutManager;

      class MyLayoutManager extends LinearLayoutManager {
      }
      """.trimIndent()

  @Language("JAVA")
  private val restrictText =
      """
      package android.support.annotation;

      import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
      import static java.lang.annotation.ElementType.CONSTRUCTOR;
      import static java.lang.annotation.ElementType.FIELD;
      import static java.lang.annotation.ElementType.METHOD;
      import static java.lang.annotation.ElementType.PACKAGE;
      import static java.lang.annotation.ElementType.TYPE;
      import static java.lang.annotation.RetentionPolicy.CLASS;

      import java.lang.annotation.Retention;
      import java.lang.annotation.Target;

      @Retention(CLASS)
      @Target({ANNOTATION_TYPE,TYPE,METHOD,CONSTRUCTOR,FIELD,PACKAGE})
      public @interface RestrictTo {
        Scope[] value();

        enum Scope {
          LIBRARY,
          LIBRARY_GROUP,
          @Deprecated
          GROUP_ID,
          TESTS,
          SUBCLASSES,
        }
      }
      """.trimIndent()

  @Language("JAVA")
  private val protectedView =
      """
      package p1.p2;

      import android.content.Context;
      import android.widget.ImageView;

      class MyAddedProtectedImageView extends ImageView {
        MyAddedProtectedImageView(Context context) {
          super(context);
        }
      }
      """.trimIndent()

  @Language("JAVA")
  private val restrictedView =
      """
      package p1.p2;

      import android.content.Context;
      import android.support.annotation.RestrictTo;
      import android.widget.ImageView;

      @RestrictTo(RestrictTo.Scope.SUBCLASSES)
      public class MyAddedHiddenImageView extends ImageView {
        public MyAddedHiddenImageView(Context context) {
          super(context);
        }
      }
      """.trimIndent()

  @Language("JAVA")
  private val view =
      """
      package p1.p2;

      import android.content.Context;
      import android.widget.ImageView;

      public class MyAddedImageView extends ImageView {
        public MyAddedImageView(Context context) {
          super(context);
        }
      }
      """.trimIndent()

  @Language("JAVA")
  private val innerClass =
      """
      package p1.p2;

      import android.content.Context;
      import android.widget.ImageView;
      import android.widget.LinearLayout;
      import android.widget.TextView;

      public class MyImageView extends ImageView {
        public MyImageView(Context context) {
          super(context);
        }
        public static class MyTextView extends TextView {
          public MyTextView(Context context) {
            super(context);
          }
        }
        public static class MyLinearLayout extends LinearLayout {
          public MyLinearLayout(Context context) {
            super(context);
          }
        }
      }
      """.trimIndent()

  @Language("JAVA")
  private val coordinatorLayout =
    """
      package androidx.coordinatorlayout.widget;

      public class CoordinatorLayout extends android.view.ViewGroup {
        public static abstract class Behavior {}
      }
      """.trimIndent()

  @Language("XML")
  private val coordinatorLayoutResources =
    """
      <resources>
        <declare-styleable name="CoordinatorLayout_Layout">
          <attr name="layout_behavior" format="string" />
        </declare-styleable>
        <string name='appbar_scrolling_view_behavior'>foo.Bar</string>
      </resources>
      """.trimIndent()

  @Language("JAVA")
  private val constraintLayout =
    """
      package androidx.constraintlayout.widget;

      public class ConstraintLayout extends android.view.ViewGroup {
      }
      """.trimIndent()


  @Language("JAVA")
  private val barrier =
    """
      package androidx.constraintlayout.widget;

      public class Barrier extends androidx.constraintlayout.widget.ConstraintLayout {
      }
      """.trimIndent()

  @Language("JAVA")
  private val composeView =
    """
      package androidx.compose.ui.platform;
      public class ComposeView extends android.view.View {}
      """.trimIndent()

  @Language("JAVA")
  private val fragmentContainerView =
    """
      package androidx.fragment.app;

      import android.view.ViewGroup;
      
      public class FragmentContainerView extends ViewGroup {}
      """.trimIndent()

  @Language("XML")
  private val constraintLayoutResources =
    """
      <resources>
        <declare-styleable name="ConstraintLayout_Layout">
          <attr name="constraint_referenced_ids" format="string" />
        </declare-styleable>
      </resources>
      """.trimIndent()

  override fun providesCustomManifest(): Boolean {
    return true
  }

  override fun setUp() {
    super.setUp()
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML)
  }

  override fun getPathToCopy(testFileName: String): String {
    return "res/layout/$testFileName"
  }

  fun testLayoutParamsDeclareStyleable() {
    myFixture.addClass(
      """
      package p1.p2;
      public class CustomViewGroup extends android.view.ViewGroup {}
      """.trimIndent())

    myFixture.addFileToProject(
      "res/values/values.xml",
      // language=XML
      """
      <resources>
        <declare-styleable name="CustomViewGroup_LayoutParams">
          <attr format="integer" name="customLayoutParam"/>
          <attr format="integer" name="customLayoutParam2"/>
        </declare-styleable>

        <declare-styleable name="CustomViewGroup">
          <attr format="integer" name="notLayoutParam"/>
        </declare-styleable>
      </resources>
      """.trimIndent()
    )

    val layoutFile = myFixture.addFileToProject(
      "res/layout/activity_main.xml",
      //language=XML
      """<p1.p2.CustomViewGroup
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <Button
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              app:${caret}/>
        </p1.p2.CustomViewGroup>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(layoutFile.virtualFile)

    myFixture.completeBasic()
    val lookupElementStrings = myFixture.lookupElementStrings
    assertThat(lookupElementStrings).containsAllOf("app:customLayoutParam", "app:customLayoutParam2")
    assertThat(lookupElementStrings).doesNotContain("app:notLayoutParam")
  }

  fun testColorLiteralResourceCompletion() {
    myFixture.addFileToProject(
      "res/values/other_colors.xml",
      //language=XML
      """
      <resources>
        <color name="foocolor">#150</integer>
      </resources>
      """.trimIndent())

    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:orientation="vertical"
              android:layout_width="match_parent"
              android:layout_height="match_parent">

              <Button
                  android:layout_width="match_parent"
                  android:layout_height="match_parent"
                  android:textColor="$caret"/>
          </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(layoutFile)

    // Expect color related resources
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf("@android:","@color/foocolor")
  }

  fun testColorLiteralResourceHighlighting() {
    val highlightedFile = myFixture.addFileToProject(
      "res/layout/incorrect_layout.xml",
      """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:orientation="vertical"
              android:layout_width="match_parent"
              android:layout_height="match_parent">

              <Button
                  android:layout_width="match_parent"
                  android:layout_height="match_parent"
                  android:textColor="#F12"
                  android:shadowColor="#F123"
                  android:textColorHighlight="#FF1234"
                  android:textColorHint="#FF432343k"
                  android:textColorLink="@android:color/black"
                  android:outlineSpotShadowColor="<error descr="Cannot resolve color 'This is not a color'">This is not a color</error>"
                  android:outlineAmbientShadowColor="<error descr="Cannot resolve color '#FA342'">#FA342</error>"/>
          </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(highlightedFile)
    myFixture.checkHighlighting()
  }

  fun testFloatLiteralResourceCompletion() {
    myFixture.addFileToProject(
      "res/values/other_integers.xml",
      //language=XML
      """
      <resources>
        <integer name="foo">150</integer>
      </resources>
      """.trimIndent())

    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:rotationX="$caret">
        </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(layoutFile)

    // Expect integer related resources
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf("@android:","@integer/foo")
  }

  fun testFloatLiteralResourceHighlighting() {
    val highlightedFile = myFixture.addFileToProject(
      "res/layout/incorrect_layout.xml",
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:rotationX="<error descr="Cannot resolve float 'bad float'">bad float</error>">
        </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(highlightedFile)
    myFixture.checkHighlighting()
  }

  fun testAutoFillHints() {
    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints="$caret">
        </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(layoutFile)

    // Expect auto fill hints from the framework only
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf(
      "creditCardExpirationDate", "emailAddress", "name", "password", "phone", "postalAddress",
      "postalCode", "username")
  }

  fun testAutoFillHintsAndroidX() {
    myFixture.addClass(
      // Language=Java
      """
      package androidx.autofill;

      public class HintConstants {
        public static final String AUTOFILL_HINT_PHONE_NATIONAL = "phoneNational";
      }
      """.trimIndent()
    )

    val layoutFile = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                android:orientation="vertical"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:autofillHints="$caret">
        </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(layoutFile)

    // Expect auto fill hints from the framework only
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf(
      "phoneNational", "creditCardExpirationDate", "emailAddress", "name", "password", "phone",
      "postalAddress", "postalCode", "username")
  }

  fun testFragmentContainerViewNameAttribute() {
    myFixture.addClass(fragmentContainerView)
    myFixture.addClass("package androidx.fragment.app; public class Fragment {}")

    myFixture.addClass(
      // language=JAVA
      """
      package p1.p2;
      public class FirstFragmentActivity extends androidx.fragment.app.Fragment{
      }
      """.trimIndent()
    )

    myFixture.addClass(
      // language=JAVA
      """
      package p1.p2;
      public class SecondFragmentActivity extends androidx.fragment.app.Fragment{
      }
      """.trimIndent()
    )

    val layout = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <androidx.fragment.app.FragmentContainerView
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/fragment_container_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:name="p1.p2.${caret}FirstFragmentActivity"
            android:tag="my_tag">
        </androidx.fragment.app.FragmentContainerView>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(layout)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf("FirstFragmentActivity", "SecondFragmentActivity")

    myFixture.moveCaret("p1.p2.FirstFrag|mentActivity")

    val elementAtCaret = myFixture.elementAtCaret
    assertThat(elementAtCaret).isInstanceOf(PsiClass::class.java)
    assertThat((elementAtCaret as PsiClass).name).isEqualTo("FirstFragmentActivity")
  }

  fun testComposableNameToolsAttributeCompletion() {
    myFixture.addClass(composeView)
    @Suppress("RequiredAttributes")
    val composeLayout = myFixture.addFileToProject(
      "res/layout/layout.xml",
      //language=XML
      """
        <LinearLayout
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          xmlns:tools="http://schemas.android.com/tools"
          xmlns:android="http://schemas.android.com/apk/res/android">

          <androidx.compose.ui.platform.ComposeView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            tools:${caret}=""/>
        </LinearLayout>
      """.trimIndent()).virtualFile
    myFixture.configureFromExistingVirtualFile(composeLayout)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("tools:composableName")
  }

  /**
   * Regression test for http://b/136596952
   * This test checks that an attribute eg. <attr name="defaultValue" format="color|string|boolean"\>, which has Boolean in the formats, but
   * also has other [ResourceType] options, accepts literals which are not "true" or "false". See [testResourceLiteralWithBooleanFormat]
   * where this validation is enforced.
   */
  fun testResourceLiteralWithMultipleFormats() {
    //
    val file = myFixture.addFileToProject(
      "res/xml/preferences.xml",
      //language=XML
      """
      <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        <ListPreference android:defaultValue="he<caret>llo"/>
      </PreferenceScreen>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val xmlAttribute = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<XmlAttribute>()
    val domElement = DomManager.getDomManager(myFixture.project).getDomElement(xmlAttribute)
    assertThat(domElement!!.converter).isInstanceOf(ResourceReferenceConverter::class.java)
    val value = domElement.value as ResourceValue
    assertThat(value.isReference).isEqualTo(false)
    assertThat(value.value).isEqualTo("hello")
    assertThat(value.type).isNull()
  }

  /**
   * This test checks that an attribute eg. <attr name="shouldDisableView" format="boolean"\>, which only has Boolean in the formats,
   * accepts literals which are only "true" or "false".
   */
  fun testResourceLiteralWithBooleanFormat() {
    val file = myFixture.addFileToProject(
      "res/xml/preferences.xml",
      //language=XML
      """
      <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        <ListPreference android:shouldDisableView="t<caret>e"/>
      </PreferenceScreen>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val xmlAttribute = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<XmlAttribute>()
    val domElement = DomManager.getDomManager(myFixture.project).getDomElement(xmlAttribute)
    assertThat(domElement!!.converter).isInstanceOf(ResourceReferenceConverter::class.java)
    assertThat(domElement.value).isNull()

    // With a valid literal for a boolean only attribute
    val validFile = myFixture.addFileToProject(
      "res/xml/preferences_valid.xml",
      //language=XML
      """
      <PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android">
        <ListPreference android:shouldDisableView="tr<caret>ue"/>
      </PreferenceScreen>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(validFile.virtualFile)
    val newXmlAttribute = myFixture.file.findElementAt(myFixture.caretOffset)!!.parentOfType<XmlAttribute>()
    val newDomElement = DomManager.getDomManager(myFixture.project).getDomElement(newXmlAttribute)
    assertThat(newDomElement!!.converter).isInstanceOf(ResourceReferenceConverter::class.java)
    val value = newDomElement.value as ResourceValue
    assertThat(value.isReference).isEqualTo(false)
    assertThat(value.value).isEqualTo("true")
    assertThat(value.type).isNull()
  }

  fun testStylesItemReferenceAndroid() {
    val psiFile = myFixture.addFileToProject("res/values/styles.xml",
      //language=XML
                                             """
      <resources>
        <style name="TextAppearance.Theme.PlainText">
          <item name="android:textStyle"/>
        </style>
      </resources>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("android:textS|tyle")
    val fakePsiElement = myFixture.elementAtCaret
    assertThat(fakePsiElement).isInstanceOf(ResourceReferencePsiElement::class.java)
    assertThat((fakePsiElement as ResourceReferencePsiElement).resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.ANDROID, ResourceType.ATTR, "textStyle"))
  }

  fun testStylesItemReferenceResAuto() {
    myFixture.addFileToProject("res/values/coordinatorlayout_attrs.xml", coordinatorLayoutResources)
    val psiFile = myFixture.addFileToProject("res/values/styles.xml",
      //language=XML
                                             """
      <resources>
        <style name="TextAppearance.Theme.PlainText">
          <item name="layout_behavior"/>
        </style>
      </resources>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("la|yout_behavior")
    val fakePsiElement = myFixture.elementAtCaret
    assertThat(fakePsiElement).isInstanceOf(ResourceReferencePsiElement::class.java)
    assertThat((fakePsiElement as ResourceReferencePsiElement).resourceReference)
      .isEqualTo(ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.ATTR, "layout_behavior"))
  }

  fun testStylesItemCompletionAndroid() {
    val psiFile = myFixture.addFileToProject("res/values/styles.xml",
      //language=XML
                                             """
      <resources>
        <style name="TextAppearance.Theme.PlainText">
          <item name="layout_wid"/>
        </style>
      </resources>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("layout_wid|")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("android:layout_width")
  }

  fun testStylesItemCompletionResAuto() {
    myFixture.addFileToProject("res/values/coordinatorlayout_attrs.xml", coordinatorLayoutResources)
    val psiFile = myFixture.addFileToProject("res/values/styles.xml",
      //language=xml
                                             """
      <resources>
        <style name="TextAppearance.Theme.PlainText">
          <item name="layout_be"/>
        </style>
      </resources>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)
    myFixture.moveCaret("layout_be|")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).contains("layout_behavior")
  }

  fun testAttributeNameCompletion1() {
    doTestCompletionVariants("an1.xml", "layout_weight", "layout_width")
  }

  fun testAttributeNameCompletion2() {
    toTestCompletion("an2.xml", "an2_after.xml")
  }

  fun testAttributeNameCompletion3() {
    toTestCompletion("an3.xml", "an3_after.xml")
  }

  fun testAttributeNameCompletion4() {
    toTestCompletion("an4.xml", "an4_after.xml")
  }

  fun testAttributeNameCompletion5() {
    toTestCompletion("an5.xml", "an5_after.xml")
  }

  fun testAttributeNameCompletion6() {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("an6.xml"))
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("\n")
    myFixture.checkResultByFile("$myTestFolder/an6_after.xml")
  }

  fun testAttributeNameCompletion7() {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("an7.xml"))
    myFixture.complete(CompletionType.BASIC)
    val lookupElementStrings = myFixture.lookupElementStrings!!.subList(0, 5)
    assertThat(lookupElementStrings).containsExactly(
      "android:layout_above", "android:layout_alignBaseline",
      "android:layout_alignBottom", "android:layout_alignEnd", "android:layout_alignLeft")
  }

  fun testAttributeNameInheritedAttributesForViewTag() {
    // TextClock inherits "bufferType" attr from TextView.
    toTestCompletion("inheritedAttributesForViewTag.xml", "inheritedAttributesForViewTag_after.xml")
  }

  fun testOpenDrawerAttributeNameCompletion() {
    // For unit tests there are no support libraries, copy dummy DrawerLayout class that imitates the support library one
    myFixture.copyFileToProject("$myTestFolder/DrawerLayout.java", "src/android/support/v4/widget/DrawerLayout.java")
    toTestCompletion("drawer_layout.xml", "drawer_layout_after.xml")
  }

  // Deprecated attributes should be crossed out in the completion
  // This test specifically checks for "android:editable" attribute on TextView
  fun testDeprecatedAttributeNamesCompletion() {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("text_view_editable.xml"))
    myFixture.complete(CompletionType.BASIC)

    // LookupElement that corresponds to "android:editable" attribute
    var editableElement: LookupElement? = null
    for (element in myFixture.lookupElements!!) {
      if ("android:editable" == element.lookupString) {
        editableElement = element
      }
    }

    assertThat(editableElement!!.lookupString).isEqualTo("android:editable")
    val presentation = LookupElementPresentation()
    editableElement.renderElement(presentation)
    assertThat(presentation.isStrikeout).isTrue()
  }

  // "conDes" is completed to "android:contentDescription", "xmlns:android" with right value is inserted
  fun testAutoAddNamespaceCompletion() {
    toTestCompletion("android_content.xml", "android_content_after.xml")
  }

  // "tools:" inside tag should autocomplete to available tools attributes, only "tools:targetApi" in this case
  fun testToolsPrefixedAttributeCompletion() {
    toTestCompletion("tools_namespace_attrs.xml", "tools_namespace_attrs_after.xml")
  }

  // ListView has some specific autocompletion attributes, like "listfooter", they should be autocompleted as well
  fun testToolsListViewAttributes() {
    doTestCompletionVariantsContains("tools_listview_attrs.xml", "tools:targetApi", "tools:listfooter", "tools:listheader",
                                     "tools:listitem")
  }

  // tools:targetApi values are autocompleted
  fun testTargetApiValueCompletion() {
    doTestCompletionVariants("tools_targetapi.xml", "HONEYCOMB", "HONEYCOMB_MR1", "HONEYCOMB_MR2")
  }

  // test @tools:sample datasources completion
  fun testToolsSampleCompletion() {
    doTestCompletionVariantsContains("tools_sample_completion.xml", "@tools:sample/full_names", "@tools:sample/lorem")
  }

  fun testToolsSampleHighlighting() {
    doTestHighlighting(getTestName(true) + ".xml")
  }

  // "-1" is not a valid tools:targetApi value
  fun testTargetApiErrorMessage1() {
    doTestHighlighting("tools_targetapi_error1.xml")
  }

  // "apple_pie" is not a valid tools:targetApi value as well
  fun testTargetApiErrorMessage2() {
    doTestHighlighting("tools_targetapi_error2.xml")
  }

  // Designtime attributes completion is showing completion variants
  fun testDesigntimeAttributesCompletion() {
    doTestCompletionVariants("tools_designtime_completion.xml", "src", "nextFocusRight", "screenReaderFocusable")
  }

  // Designtime attributes completion is completing attribute names correctly
  fun testDesigntimeAttributesCompletion2() {
    toTestFirstCompletion("tools_designtime_completion_background.xml",
                          "tools_designtime_completion_background_after.xml")
  }

  // Designtime attributes completion after having typed tools:
  fun testDesigntimeAttributesCompletion3() {
    doTestCompletionVariantsContains("tools_designtime_prefix_only.xml", "tools:background")
  }

  fun testToolsUseHandlerAttribute() {
    doTestCompletionVariants("tools_use_handler_completion.xml", "android.view.TextureView",
                             "android.widget.AutoCompleteTextView",
                             "android.widget.CheckedTextView",
                             "android.widget.MultiAutoCompleteTextView",
                             "android.widget.TextView")
  }

  // fontFamily attribute values are autocompleted
  fun testFontFamilyCompletion() {
    doTestCompletionVariants("text_view_font_family.xml", "monospace", "serif-monospace")
  }

  fun testCommonPrefixIdea63531() {
    val file = copyFileToProject("commonPrefixIdea63531.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + "commonPrefixIdea63531_after.xml")
  }

  fun testHighlighting() {
    doTestHighlighting("hl.xml")
  }

  fun testHighlighting2() {
    copyFileToProject("integers.xml", "res/values/integers.xml")
    doTestHighlighting("hl2.xml")
  }

  fun testWrongEnumValuesHighlighting() {
    doTestHighlighting("wrong_enum_value.xml")
  }

  fun testTableRowRootTag() {
    doTestHighlighting()
  }

  fun testCheckLayoutAttrs() {
    doTestHighlighting("layoutAttrs.xml")
  }

  fun testCheckLayoutAttrs1() {
    doTestHighlighting("layoutAttrs1.xml")
  }

  fun testCheckLayoutAttrs1_appStyleable() {
    // Having a styleable in res-auto that references a framework attr may cause AttributeDefinitions to be wrong. See b/111547198.
    copyFileToProject("layoutAttrs1_styleable.xml", "res/values/styleable.xml")
    doTestHighlighting("layoutAttrs1.xml")
  }

  fun testCheckLayoutAttrs2() {
    doTestHighlighting("layoutAttrs2.xml")
  }

  fun testCheckLayoutAttrs3() {
    doTestHighlighting("layoutAttrs3.xml")
  }

  fun testUnknownAttribute() {
    doTestHighlighting("hl1.xml")
  }

  fun testMissingRequired() {
    doTestHighlighting("missing_attrs.xml")
  }

  fun testViewHighlighting() {
    doTestHighlighting("view_highlighting.xml")
  }

  fun testLayoutManagerAttributeForOldRecyclerView() {
    // RecyclerView has a "layoutManager" attribute that should give completions that extend
    // the RecyclerView.LayoutManager class.
    myFixture.addClass(recyclerViewOld)
    myFixture.addFileToProject("res/values/recyclerView_attrs.xml", recyclerViewAttrs)
    doTestCompletionVariants("recycler_view.xml",
                             "android.support.v7.widget.GridLayoutManager",
                             "android.support.v7.widget.LinearLayoutManager")
  }

  fun testLayoutManagerAttributeForNewRecyclerView() {
    // RecyclerView has a "layoutManager" attribute that should give completions that extend
    // the RecyclerView.LayoutManager class.
    setAndroidx()
    myFixture.addClass(recyclerViewNew)
    myFixture.addFileToProject("res/values/recyclerView_attrs.xml", recyclerViewAttrs)
    doTestCompletionVariants("recycler_view_0.xml",
                             "androidx.recyclerview.widget.GridLayoutManager",
                             "androidx.recyclerview.widget.LinearLayoutManager")
  }

  fun testLayoutManagerAttributeHighlighting() {
    // Check the highlighting of the "layoutManager" attribute values for a RecyclerView.
    myFixture.addClass(recyclerViewOld)
    myFixture.addClass(myLayoutManager)
    myFixture.addFileToProject("res/values/recyclerView_attrs.xml", recyclerViewAttrs)
    doTestHighlighting("recycler_view_1.xml")
  }

  fun testToolsAttributesForOldRecyclerView() {
    myFixture.addClass(recyclerViewOld)
    doTestCompletionVariantsContains("recycler_view_2.xml",
                                     "tools:targetApi",
                                     "tools:itemCount",
                                     "tools:listitem",
                                     "tools:viewBindingType")
  }

  fun testToolsAttributesForNewRecyclerView() {
    myFixture.addClass(recyclerViewNew)
    doTestCompletionVariantsContains("recycler_view_3.xml",
                                     "tools:targetApi",
                                     "tools:itemCount",
                                     "tools:listitem",
                                     "tools:viewBindingType")
  }

  fun testCustomTagCompletion() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("ctn.xml", "ctn_after.xml")
  }

  fun testCustomTagCompletion0() {
    val labelViewJava = copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")

    val lf1 = myFixture.copyFileToProject(myTestFolder + '/'.toString() + "ctn0.xml", "res/layout/layout1.xml")
    myFixture.configureFromExistingVirtualFile(lf1)
    myFixture.complete(CompletionType.BASIC)
    var variants = myFixture.lookupElementStrings
    assertThat(variants).contains("p1.p2.LabelView")

    val psiLabelViewFile = PsiManager.getInstance(project).findFile(labelViewJava)
    assertThat(psiLabelViewFile).isInstanceOf(PsiJavaFile::class.java)
    myFixture.renameElement((psiLabelViewFile as PsiJavaFile).classes[0], "LabelView1")

    val lf2 = myFixture.copyFileToProject(myTestFolder + '/'.toString() + "ctn0.xml", "res/layout/layout2.xml")
    myFixture.configureFromExistingVirtualFile(lf2)
    myFixture.complete(CompletionType.BASIC)
    variants = myFixture.lookupElementStrings
    assertThat(variants).doesNotContain("p1.p2.LabelView")
    assertThat(variants).contains("p1.p2.LabelView1")

    runWriteCommandAction(project) {
      try {
        labelViewJava.delete(null)
      }
      catch (e: IOException) {
        throw RuntimeException(e)
      }
    }

    val lf3 = myFixture.copyFileToProject(myTestFolder + '/'.toString() + "ctn0.xml", "res/layout/layout3.xml")
    myFixture.configureFromExistingVirtualFile(lf3)
    myFixture.complete(CompletionType.BASIC)
    variants = myFixture.lookupElementStrings
    assertThat(variants).doesNotContain("p1.p2.LabelView")
    assertThat(variants).doesNotContain("p1.p2.LabelView1")
  }

  fun testCustomTagCompletion1() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java")
    copyFileToProject("IncorrectView.java", "src/p1/p2/IncorrectView.java")
    doTestCompletionVariants("ctn1.xml", "p2.LabelView", "p2.LabelView1")
  }

  fun testCustomTagCompletion2() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    val file = copyFileToProject("ctn2.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("p1\n")
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + "ctn2_after.xml")
  }

  fun testCustomTagCompletion3() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("ctn3.xml", "ctn3_after.xml")
  }

  fun testCustomTagCompletion4() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    doTestCompletionVariants("ctn4.xml", "LabelView")
  }

  fun testCustomTagCompletion5() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    val file = copyFileToProject("ctn5.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("p1\n")
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + "ctn5_after.xml")
  }

  fun testCustomTagCompletion6() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("ctn6.xml", "ctn6_after.xml")
  }

  fun testCustomTagCompletion7() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("ctn7.xml", "ctn6_after.xml")
  }

  fun testCustomTagCompletion8() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java")
    toTestCompletion("ctn8.xml", "ctn8_after.xml")
  }

  fun testCustomTagCompletion9() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("ctn9.xml", "ctn9_after.xml")
  }

  fun testCustomTagCompletion10() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java")
    toTestCompletion("ctn10.xml", "ctn10_after.xml")
  }

  fun testCustomAttributeNameCompletion() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    doTestCompletionVariants("can.xml", "text", "textColor", "textSize")
  }

  fun testCustomAttributeNameCompletion1() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    doTestCompletionVariants("can1.xml",
                             "context", "contextClickable", "text", "textAlignment", "textColor", "textDirection", "textSize",
                             "tooltipText")
  }

  fun testCustomAttributeNameCompletion2() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    val file = copyFileToProject("can2.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("text")

    assertThat(myFixture.lookupElementStrings).containsExactly(
        "android:contextClickable", "android:textAlignment", "android:textDirection", "android:tooltipText", "text", "textColor",
        "textSize")
  }

  fun testCustomAttributeNameCompletion3() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("can3.xml", "can3_after.xml")
  }

  fun testCustomAttributeNameCompletion4() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("can4.xml", "can4_after.xml")
  }

  fun testCustomAttributeNameCompletion5() {
    myFacet.configuration.projectType = PROJECT_TYPE_LIBRARY
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    toTestCompletion("can5.xml", "can5_after.xml")
  }

  fun testToolsAttributesCompletion() {
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity.java", "src/p1/p2/Activity1.java")
    // Create layout that we will use to test the layout completion
    myFixture.copyFileToProject("$myTestFolder/tools_context_completion_after.xml", "res/layout/other_layout.xml")
    toTestFirstCompletion("tools_context_completion.xml", "tools_context_completion_after.xml")
    toTestCompletion("tools_showIn_completion.xml", "tools_showIn_completion_after.xml")
    toTestCompletion("tools_parentTag_completion.xml", "tools_parentTag_completion_after.xml")
  }

  fun testCustomAttributeValueCompletion() {
    doTestCompletionVariants("cav.xml", "@color/color0", "@color/color1", "@color/color2")
  }

  fun testIdea64993() {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java")
    doTestHighlighting()
  }

  fun testTagNameCompletion1() {
    val file = copyFileToProject("tn1.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type('\n')
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + "tn1_after.xml")
  }

  fun testFlagCompletion() {
    doTestCompletionVariants("av1.xml", "center", "center_horizontal", "center_vertical")
    doTestCompletionVariants("av2.xml", "fill_horizontal", "fill_vertical")
  }

  fun testFlagCompletion1() {
    doTestCompletionVariants("flagCompletion1.xml", "center", "center_horizontal", "center_vertical", "center|bottom",
                             "center|center_horizontal", "center|center_vertical", "center|clip_horizontal", "center|clip_vertical",
                             "center|end", "center|fill", "center|fill_horizontal", "center|fill_vertical", "center|left",
                             "center|right", "center|start", "center|top")
  }

  fun testFlagCompletion2() {
    doTestCompletionVariants("flagCompletion2.xml", "center", "center_horizontal", "center_vertical", "center|center_horizontal",
                             "center|center_vertical", "center|clip_horizontal", "center|clip_vertical", "center|end", "center|fill",
                             "center|fill_horizontal", "center|fill_vertical", "center|left", "center|right", "center|start",
                             "center|top")
    myFixture.type("|fill")

    assertThat(myFixture.lookupElementStrings).containsExactly("center|fill", "center|fill_horizontal", "center|fill_vertical")
  }

  fun testResourceCompletion() {
    doTestCompletionVariantsContains("av3.xml", "@color/color0", "@color/color1", "@android:", "@drawable/picture2", "@drawable/picture1")
    doTestCompletionVariantsContains("av8.xml", "@android:", "@anim/anim1", "@color/color0", "@color/color1", "@dimen/myDimen",
                                     "@drawable/picture1", "@layout/av3", "@layout/av8", "@string/itStr", "@string/hello", "@style/style1")
  }

  fun testLocalResourceCompletion1() {
    doTestCompletionVariants("av4.xml", "@color/color0", "@color/color1", "@color/color2")
  }

  fun testLocalResourceCompletion2() {
    doTestCompletionVariants("av5.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3", "@drawable/cdrawable")
  }

  fun testLocalResourceCompletion3() {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr")
  }

  fun testLocalResourceCompletion4() {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr")
  }

  fun testLocalResourceCompletion5() {
    doTestCompletionVariants("av12.xml", "@android:", "@anim/anim1", "@anim/anim2")
  }

  fun testLocalResourceCompletion6() {
    doTestCompletionVariants("av14.xml", "@android:", "@color/color0", "@color/color1", "@color/color2", "@drawable/cdrawable",
                             "@drawable/picture1", "@drawable/picture2", "@drawable/picture3")
  }

  fun testForceLocalResourceCompletion() {
    // No system colors are suggested as completion.
    doTestCompletionVariants("av13.xml", "@color/color0", "@color/color1", "@color/color2")
  }

  fun testSystemResourceCompletion() {
    doTestCompletionVariantsContains("av6.xml", "@android:color/primary_text_dark", "@android:drawable/menuitem_background")
  }

  fun testCompletionSpecialCases() {
    doTestCompletionVariants("av9.xml", "@string/hello", "@string/hello1")
  }

  fun testLayoutAttributeValuesCompletion() {
    doTestCompletionVariants("av10.xml", "fill_parent", "match_parent", "wrap_content", "@android:", "@dimen/myDimen")
    doTestCompletionVariants("av11.xml", "center", "center_horizontal", "center_vertical")
    doTestCompletionVariants("av15.xml", "horizontal", "vertical")
  }

  fun testFloatAttributeValuesCompletion() {
    copyFileToProject("myIntResource.xml", "res/values/myIntResource.xml")
    doTestCompletionVariants("floatAttributeValues.xml", "@android:", "@integer/my_integer")
  }

  fun testDrawerLayoutOpenDrawerCompletion() {
    // For unit tests there are no support libraries, copy dummy DrawerLayout class that imitates the support library one
    myFixture.copyFileToProject("$myTestFolder/DrawerLayout.java", "src/android/support/v4/widget/DrawerLayout.java")
    doTestCompletionVariants("drawer_layout_attr_completion.xml", "start", "end", "left", "right")
  }

  fun testTagNameCompletion2() {
    val file = copyFileToProject("tn2.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.assertPreferredCompletionItems(0, "EditText", "ExpandableListView", "android.inputmethodservice.ExtractEditText")
  }

  fun testTagNameCompletion3() {
    doTestCompletionVariants("tn3.xml", "ActionMenuView", "AdapterViewFlipper", "AutoCompleteTextView", "CalendarView", "CheckedTextView",
                             "ExpandableListView", "GridView", "HorizontalScrollView", "ImageView", "ListView", "MultiAutoCompleteTextView",
                             "ScrollView", "SearchView", "StackView", "SurfaceView", "TextView", "TextureView", "VideoView", "View",
                             "ViewAnimator", "ViewFlipper", "ViewStub", "ViewSwitcher", "WebView", "android.appwidget.AppWidgetHostView",
                             "android.gesture.GestureOverlayView", "android.inputmethodservice.KeyboardView",
                             "android.media.tv.interactive.TvInteractiveAppView", "android.media.tv.TvView",
                             "android.opengl.GLSurfaceView", "android.widget.inline.InlineContentView", "android.window.SplashScreenView")
  }

  /*public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }*/

  fun testTagNameCompletion5() {
    toTestFirstCompletion("tn5.xml", "tn5_after.xml")
  }

  fun testTagNameCompletion6() {
    val file = copyFileToProject("tn6.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings).doesNotContain("android.widget.Button")
  }

  fun testTagNameCompletion7() {
    toTestCompletion("tn7.xml", "tn7_after.xml")
  }

  fun testTagNameCompletion8() {
    val file = copyFileToProject("tn8.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings).contains("widget.Button")
  }

  fun testTagNameCompletion9() {
    toTestCompletion("tn9.xml", "tn9_after.xml")
  }

  fun testTagNameCompletion10() {
    val file = copyFileToProject("tn10.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings).doesNotContain("android.widget.Button")
  }

  fun testTagNameCompletion11() {
    toTestCompletion("tn11.xml", "tn11_after.xml")
  }

  fun testDeprecatedTagsAreLastInCompletion() {
    val file = copyFileToProject("tagName_letter_G.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)

    // Gallery is deprecated and thus should be the last in completion list
    myFixture.assertPreferredCompletionItems(0, "GridLayout", "GridView", "android.gesture.GestureOverlayView",
                                             "android.opengl.GLSurfaceView", "Gallery")
  }

  // Completion by simple class name in layouts should work, inserting fully-qualified names
  // http://b.android.com/179380
  fun testTagNameCompletionBySimpleName() {
    toTestCompletion("tn13.xml", "tn13_after.xml")
  }

  // Test that support library component alternatives are pushed higher in completion
  fun testSupportLibraryCompletion() {
    myFixture.copyFileToProject("$myTestFolder/GridLayout.java", "src/android/support/v7/widget/GridLayout.java")
    val file = copyFileToProject("tn14.xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val completionResult = myFixture.lookupElementStrings

    assertThat(completionResult).containsExactly("android.support.v7.widget.GridLayout", "GridLayout").inOrder()
  }

  // Test android:layout_width and android:layout_height highlighting for framework and library layouts
  fun testWidthHeightHighlighting() {
    // For unit tests there are no support libraries, copy dummy classes that imitate support library ones
    myFixture.copyFileToProject("$myTestFolder/PercentRelativeLayout.java", "src/android/support/percent/PercentRelativeLayout.java")
    myFixture.copyFileToProject("$myTestFolder/PercentFrameLayout.java", "src/android/support/percent/PercentFrameLayout.java")

    doTestHighlighting("dimensions_layout.xml")
  }

  fun testTagNameIcons1() {
    doTestTagNameIcons("tn10.xml")
  }

  fun testTagNameIcons2() {
    doTestTagNameIcons("tn12.xml")
  }

  private fun doTestTagNameIcons(fileName: String) {
    val file = copyFileToProject(fileName)
    myFixture.configureFromExistingVirtualFile(file)
    val elements = myFixture.complete(CompletionType.BASIC)
    val elementsToCheck = HashSet(Arrays.asList(
      "view", "include", "requestFocus", "fragment", "Button"))

    for (element in elements) {
      val s = element.lookupString
      val obj = element.getObject()

      if (elementsToCheck.contains(s)) {
        val presentation = LookupElementPresentation()
        element.renderElement(presentation)
        assertWithMessage("no icon for element: $element").that(presentation.icon).isNotNull()

        if ("Button" == s) {
          assertThat(obj).isInstanceOf(PsiClass::class.java)
        }
      }
    }
  }

  fun testIdCompletion1() {
    doTestCompletionVariants("idcompl1.xml", "@android:", "@+id/")
  }

  fun testIdCompletion2() {
    doTestCompletionVariantsContains("idcompl2.xml",
                                     "@android:id/text1", "@android:id/text2", "@android:id/inputExtractEditText",
                                     "@android:id/selectTextMode", "@android:id/startSelectingText", "@android:id/stopSelectingText")
  }

  fun testIdCompletion3() {
    doTestCompletionVariantsContains("idcompl3.xml", "@android:id/title")
  }

  fun testNestedScrollView() {
    myFixture.copyFileToProject("$myTestFolder/NestedScrollView.java", "src/android/support/v4/widget/NestedScrollView.java")
    toTestCompletion("nestedScrollView.xml", "nestedScrollView_after.xml")
  }

  fun testExtendedNestedScrollView() {
    myFixture.copyFileToProject("$myTestFolder/NestedScrollView.java", "src/android/support/v4/widget/NestedScrollView.java")
    myFixture.copyFileToProject("$myTestFolder/ExtendedNestedScrollView.java", "src/p1/p2/ExtendedNestedScrollView.java")
    toTestCompletion("extendedNestedScrollView.xml", "extendedNestedScrollView_after.xml")
  }

  fun testNewIdCompletion1() {
    toTestCompletion("newIdCompl1.xml", "newIdCompl1_after.xml")
  }

  fun testNewIdCompletion2() {
    toTestCompletion("newIdCompl2.xml", "newIdCompl2_after.xml")
  }

  fun testIdHighlighting() {
    doTestHighlighting("idh.xml")
  }

  fun testIdHighlighting1() {
    val virtualFile = copyFileToProject("idh.xml", "res/layout-large/idh.xml")
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testStyleNamespaceHighlighting() {
    val virtualFile = copyFileToProject("stylesNamespaceHighlight.xml", "res/values/stylesNamespaceHighlight.xml")
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  // Regression test for http://b.android.com/175619
  fun testStyleShortNameCompletion() {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("StyleNameCompletion_layout.xml", "res/layout/layout.xml"))
    copyFileToProject("StyleNameCompletion_style.xml", "res/values/styles.xml")
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResultByFile("$myTestFolder/StyleNameCompletion_layout_after.xml")
  }

  fun testIdReferenceCompletion() {
    toTestCompletion("idref1.xml", "idref1_after.xml")
  }

  fun testSystemIdReferenceCompletion() {
    toTestCompletion("idref2.xml", "idref2_after.xml")
  }

  fun testSystemResourcesHighlighting() {
    doTestHighlighting("systemRes.xml")
  }

  fun testViewClassCompletion() {
    toTestCompletion("viewclass.xml", "viewclass_after.xml")
  }

  fun testViewElementHighlighting() {
    doTestHighlighting()
  }

  fun testPrimitiveValues() {
    doTestHighlighting("primValues.xml")
  }

  fun testTableCellAttributes() {
    toTestCompletion("tableCell.xml", "tableCell_after.xml")
  }

  fun testTextViewRootTag_IDEA_62889() {
    doTestCompletionVariants("textViewRootTag.xml", "AutoCompleteTextView", "CheckedTextView", "MultiAutoCompleteTextView", "TextView",
                             "TextureView")
  }

  fun testRequestFocus() {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testMerge() {
    doTestHighlighting("merge.xml")
  }

  fun testMerge1() {
    doTestCompletion()
  }

  fun testMerge2() {
    doTestCompletion()
  }

  fun testMerge3() {
    doTestHighlighting()
  }

  fun testFragmentHighlighting() {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java")
    doTestHighlighting(getTestName(true) + ".xml")
  }

  fun testFragmentHighlighting1() {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java")
    doTestHighlighting(getTestName(true) + ".xml")
  }

  fun testFragmentCompletion1() {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java")
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion1_androidX() {
    myFixture.addClass("package androidx.fragment.app; public class Fragment {}")

    myFixture.addClass(
      // language=JAVA
      """
      package p1.p2;

      public class MyFragmentActivity {
        public static class MyFragment extends androidx.fragment.app.Fragment {}
      }
      """.trimIndent()
    )

    toTestCompletion("fragmentCompletion1.xml", "fragmentCompletion1_after.xml")
  }

  fun testFragmentCompletion2() {
    toTestFirstCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion3() {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion4() {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java")
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion5() {
    toTestFirstCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion6() {
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type('\n')
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + getTestName(true) + "_after.xml")
  }

  fun testFragmentCompletion7() {
    doTestCompletionVariantsContains("fragmentCompletion7.xml",
                                     "tools:layout",
                                     "tools:targetApi",
                                     "tools:ignore")
  }

  fun testCustomAttrsPerformance() {
    myFixture.copyFileToProject("dom/resources/bigfile.xml", "res/values/bigfile.xml")
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs.xml")
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs1.xml")
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs2.xml")
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs3.xml")
    val f = copyFileToProject("bigfile.xml")
    myFixture.configureFromExistingVirtualFile(f)

    PlatformTestUtil.startPerformanceTest("android custom attrs highlighting", 800) { myFixture.doHighlighting() }.attempts(
      2).usesAllCPUCores().assertTiming()
  }

  fun testSupportGridLayoutCompletion() {
    myFixture.copyFileToProject("dom/layout/GridLayout.java", "src/android/support/v7/widget/GridLayout.java")
    myFixture.copyFileToProject("dom/resources/attrs_gridlayout.xml", "res/values/attrs_gridlayout.xml")
    doTestCompletionVariants(getTestName(true) + ".xml", "rowCount", "rowOrderPreserved")
  }

  fun testSupportGridLayoutCompletion2() {
    myFixture.copyFileToProject("dom/layout/GridLayout.java", "src/android/support/v7/widget/GridLayout.java")
    myFixture.copyFileToProject("dom/resources/attrs_gridlayout.xml", "res/values/attrs_gridlayout.xml")
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml")
  }

  fun testViewClassReference() {
    val file = myFixture.copyFileToProject("$myTestFolder/vcr.xml", getPathToCopy("vcr.xml"))
    myFixture.configureFromExistingVirtualFile(file)
    val psiFile = myFixture.file
    val text = psiFile.text
    val rootOffset = text.indexOf("ScrollView")
    val rootViewClass = psiFile.findReferenceAt(rootOffset)!!.resolve()
    assertThat(rootViewClass).isInstanceOf(PsiClass::class.java)
    val childOffset = text.indexOf("LinearLayout")
    val childViewClass = psiFile.findReferenceAt(childOffset)!!.resolve()
    assertThat(childViewClass).isInstanceOf(PsiClass::class.java)
  }

  fun testViewClassReference1() {
    val file = myFixture.copyFileToProject("$myTestFolder/vcr1.xml", getPathToCopy("vcr1.xml"))
    myFixture.testHighlighting(true, false, true, file)
  }

  fun testViewClassReference2() {
    val file = myFixture.copyFileToProject("$myTestFolder/vcr2.xml", getPathToCopy("vcr2.xml"))
    myFixture.configureFromExistingVirtualFile(file)
    val psiFile = myFixture.file
    val text = psiFile.text
    val rootOffset = text.indexOf("ScrollView")

    val rootViewClass = psiFile.findReferenceAt(rootOffset)!!.resolve()
    assertThat(rootViewClass).isInstanceOf(PsiClass::class.java)
  }

  fun testOnClickCompletion() {
    copyOnClickClasses()
    doTestCompletionVariants(getTestName(true) + ".xml", "clickHandler1", "clickHandler7")
  }

  fun testOnClickHighlighting() {
    myFixture.allowTreeAccessForAllFiles()
    copyOnClickClasses()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    doTestHighlighting()
  }

  fun testOnClickHighlighting1() {
    myFixture.allowTreeAccessForAllFiles()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity3.java", "src/p1/p2/Activity1.java")
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity4.java", "src/p1/p2/Activity2.java")
    doTestHighlighting()
  }

  fun testOnClickHighlighting2() {
    copyOnClickClasses()
    doTestHighlighting()
  }

  fun testOnClickHighlighting3() {
    myFixture.allowTreeAccessForAllFiles()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity5.java", "src/p1/p2/Activity1.java")
    doTestHighlighting()
  }

  fun testOnClickHighlighting4() {
    myFixture.allowTreeAccessForAllFiles()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity6.java", "src/p1/p2/Activity1.java")
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity4.java", "src/p1/p2/Activity2.java")
    doTestHighlighting()
  }

  fun testOnClickHighlighting5() {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76262
    myFixture.allowTreeAccessForAllFiles()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity7.java", "src/p1/p2/Activity1.java")
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity8.java", "src/p1/p2/Activity2.java")
    doTestHighlighting()
  }

  // b/78423832
  fun ignore_testOnClickHighlighting6() {
    // Like testOnClickHighlighting5, but instead of having the activity be found
    // due to a setContentView call, it's declared explicitly with a tools:context
    // attribute instead
    myFixture.allowTreeAccessForAllFiles()
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity7.java", "src/p1/p2/Activity1.java")
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity9.java", "src/p1/p2/Activity2.java")
    doTestHighlighting()
  }

  fun testOnClickHighlightingJava() {
    myFixture.enableInspections(UnusedDeclarationInspection())
    val f = myFixture.copyFileToProject(myTestFolder + "/" + getTestName(true) + ".java", "src/p1/p2/MyActivity1.java")
    myFixture.configureFromExistingVirtualFile(f)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testMinHeightCompletion() {
    doTestCompletionVariants(getTestName(true) + ".xml", "@android:", "@dimen/myDimen")
  }

  fun testOnClickNavigation() {
    copyOnClickClasses()
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)

    val reference = TargetElementUtil.findReference(myFixture.editor, myFixture.caretOffset)
    assertThat(reference).isInstanceOf(PsiPolyVariantReference::class.java)
    val results = (reference as PsiPolyVariantReference).multiResolve(false)
    assertThat(results.size).isEqualTo(2)
    for (result in results) {
      assertThat(result.element).isInstanceOf(PsiMethod::class.java)
    }
  }

  fun testRelativeIdsCompletion() {
    doTestCompletionVariants(getTestName(false) + ".xml", "@+id/", "@android:", "@id/btn1", "@id/btn2")
  }

  fun testCreateResourceFromUsage() {
    val virtualFile = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val infos = myFixture.doHighlighting()
    val actions = ArrayList<IntentionAction>()

    for (info in infos) {
      val ranges = info.quickFixActionRanges

      if (ranges != null) {
        for (pair in ranges) {
          val action = pair.getFirst().action
          if (action is CreateValueResourceQuickFix) {
            actions.add(action)
          }
        }
      }
    }
    assertThat(actions).hasSize(1)

    runWriteCommandAction(project) { actions[0].invoke(project, myFixture.editor, myFixture.file) }
    myFixture.checkResultByFile("res/values/drawables.xml", myTestFolder + '/'.toString() + getTestName(true) + "_drawable_after.xml", true)
  }

  fun testXsdFile1() {
    val virtualFile = copyFileToProject("XsdFile.xsd", "res/raw/XsdFile.xsd")
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  fun testXsdFile2() {
    val virtualFile = copyFileToProject("XsdFile.xsd", "res/assets/XsdFile.xsd")
    myFixture.configureFromExistingVirtualFile(virtualFile)
    myFixture.checkHighlighting(true, false, false)
  }

  @Throws(IOException::class)
  private fun copyOnClickClasses() {
    copyFileToProject("OnClick_Class1.java", "src/p1/p2/OnClick_Class1.java")
    copyFileToProject("OnClick_Class2.java", "src/p1/p2/OnClick_Class2.java")
    copyFileToProject("OnClick_Class3.java", "src/p1/p2/OnClick_Class3.java")
    copyFileToProject("OnClick_Class4.java", "src/p1/p2/OnClick_Class4.java")
  }

  fun testJavaCompletion1() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaCompletion("p1.p2")
  }

  fun testJavaCompletion2() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaCompletion("p1.p2")
  }

  fun testJavaCompletion3() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaCompletion("p1.p2")
  }

  fun testJavaIdCompletion() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaCompletion("p1.p2")
  }

  fun testJavaHighlighting1() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaHighlighting("p1.p2")
  }

  fun testJavaHighlighting2() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaHighlighting("p1")
  }

  fun testJavaHighlighting3() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaHighlighting("p1.p2")
  }

  fun testJavaHighlighting4() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaHighlighting("p1.p2")
  }

  fun testJavaHighlighting5() {
    copyFileToProject("main.xml", "res/layout/main.xml")
    doTestJavaHighlighting("p1")
  }

  fun testJavaCreateResourceFromUsage() {
    val virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(true) + ".java")
/* b/263898646
    doCreateFileResourceFromUsage(virtualFile)
    myFixture.checkResultByFile("res/layout/unknown.xml", myTestFolder + '/'.toString() + getTestName(true) + "_layout_after.xml", true)
b/263898646 */
  }

  fun testCreateResourceFromUsage1() {
    val virtualFile = copyFileToProject(getTestName(true) + ".xml")
    doCreateFileResourceFromUsage(virtualFile)
    myFixture.type("selector")
    myFixture.checkResultByFile("res/drawable/unknown.xml", myTestFolder + '/'.toString() + getTestName(true) + "_drawable_after.xml", true)
  }

  fun testPrivateAndPublicResources() {
    doTestHighlighting()
  }

  fun testPrivateAttributesCompletion() {
    doTestCompletion()
  }

  fun testPrivateAttributesHighlighting() {
    doTestHighlighting()
  }

  fun testResourceValidationErrors() {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml")
    doTestHighlighting()
  }

  fun testAttrReferences1() {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml")
    doTestHighlighting("attrReferences1.xml", "res/layout/attr_references1.xml")
  }

  fun testAttrReferences2() {
    doTestAttrReferenceCompletionVariants("?")
  }

  fun testAttrReferences3() {
    doTestAttrReferenceCompletionVariants("attr")
  }

  private fun doTestAttrReferenceCompletionVariants(prefix: String) {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml")
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    val variants = myFixture.lookupElementStrings

    assertThat(variants).isNotEmpty()
    assertThat(containElementStartingWith(variants!!, prefix)).isFalse()
  }

  fun testAttrReferences4() {
    doTestAttrReferenceCompletion("myA\n")
  }

  fun testAttrReferences5() {
    doTestAttrReferenceCompletion("textAppear\n")
  }

  fun testAttrReferences6() {
    doTestAttrReferenceCompletion("myA\n")
  }

  fun testAttrReferences7() {
    doTestAttrReferenceCompletion("android:textAppear\n")
  }

  fun testAttrReferences8() {
    doTestAttrReferenceCompletion("attr\n")
  }

  fun testAttrReferences9() {
    doTestAttrReferenceCompletion("android:attr\n")
  }

  fun testNamespaceCompletion() {
    doTestNamespaceCompletion()
  }

  fun testDimenUnitsCompletion1() {
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings).containsExactly("3dp", "3px", "3sp", "3pt", "3mm", "3in")

    val originalElement = myFixture.file.findElementAt(
      myFixture.editor.caretModel.offset)

    val lookup = myFixture.lookup
    var dpElement: LookupElement? = null
    var pxElement: LookupElement? = null

    for (element in lookup.items) {
      if (element.lookupString.endsWith("dp")) {
        dpElement = element
      }
      else if (element.lookupString.endsWith("px")) {
        pxElement = element
      }
    }
    var provider: DocumentationProvider
    var docTargetElement: PsiElement?

    lookup.setCurrentItem(dpElement)
    docTargetElement = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file, originalElement)
    provider = DocumentationManager.getProviderFromElement(docTargetElement)
    assertThat(provider.generateDoc(docTargetElement, originalElement)).isEqualTo(
      "<html><body><b>Density-independent Pixels</b> - an abstract unit that is based on the physical " + "density of the screen.</body></html>")

    lookup.setCurrentItem(pxElement)
    docTargetElement = DocumentationManager.getInstance(project).findTargetElement(myFixture.editor, myFixture.file, originalElement)
    provider = DocumentationManager.getProviderFromElement(docTargetElement)
    assertThat(provider.generateDoc(docTargetElement, originalElement)).isEqualTo(
      "<html><body><b>Pixels</b> - corresponds to actual pixels on the screen. Not recommended.</body></html>")
  }

  fun testMipMapCompletionInDrawableXML() {
    myFixture.addFileToProject(
      "res/mipmap/mipmap.xml",
      //language=XML
      """
      <adaptive-icon></adaptive-icon>
      """.trimIndent())
    myFixture.addFileToProject(
        "res/mipmap/launcher.xml",
      //language=XML
      """
      <adaptive-icon></adaptive-icon>
      """.trimIndent())
    val valuesFile = myFixture.addFileToProject(
      "res/drawable/testDrawable.xml",
      //language=XML
      """
      <selector xmlns:android="http://schemas.android.com/apk/res/android">
           <item android:drawable="@mipmap/${caret}" />
      </selector>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(valuesFile.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsAllOf("@mipmap/launcher", "@mipmap/mipmap")
  }

  fun testMipMapCompletionNotInValuesXML() {
    myFixture.addFileToProject(
      "res/mipmap/launcher.xml",
      //language=XML
      """
      <adaptive-icon></adaptive-icon>
      """.trimIndent())
    val valuesFile = myFixture.addFileToProject(
      "res/values/styles.xml",
      //language=XML
      """
      <resources>
        <drawable name="alias_for_mipmap">@mipmap/${caret}</drawable>
      </resources>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(valuesFile.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).doesNotContain("@mipmap/launcher")
  }

  fun testAttributeValueAttrCompletionDocumentation() {
    val file = myFixture.addFileToProject(
      "res/layout/activity_main.xml",
      //language=XML
      """<LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical"
            android:layout_width="${caret}"
            android:layout_height="match_parent">
        </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings)
      .containsExactlyElementsIn(listOf("match_parent", "wrap_content", "@android:", "@dimen/myDimen", "fill_parent"))

    val lookup = myFixture.lookup
    var matchParentElement: LookupElement? = null
    var fillParentElement: LookupElement? = null

    for (element in lookup.items) {
      when (element.lookupString) {
        "match_parent" -> matchParentElement = element
        "fill_parent" -> fillParentElement = element
      }
    }

    lookup.currentItem = matchParentElement
    var ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)!!
    var docTargetElement = DocumentationManager.getInstance(project).findTargetElement(
      myFixture.editor, myFixture.file, ref.element)
    var documentationProvider = DocumentationManager.getProviderFromElement(docTargetElement)
    assertThat(documentationProvider.generateDoc(docTargetElement, ref.element)).isEqualTo(
      """The view should be as big as its parent (minus padding).
                 Introduced in API Level 8.""".trimIndent())

    lookup.currentItem = fillParentElement
    ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)!!
    docTargetElement = DocumentationManager.getInstance(project).findTargetElement(
      myFixture.editor, myFixture.file, ref.element)
    documentationProvider = DocumentationManager.getProviderFromElement(docTargetElement)
    assertThat(documentationProvider.generateDoc(docTargetElement, ref.element)).isEqualTo(
      """The view should be as big as its parent (minus padding).
                 This constant is deprecated starting from API Level 8 and
                 is replaced by {@code match_parent}.""".trimIndent())
  }

  fun testAttributeValueColorCompletionDocumentation() {
    myFixture.addFileToProject(
      "res/values/colors.xml",
      """<resources>
        <color name="colorPrimary">#008577</color>
      </resources>
      """.trimIndent())
    val file = myFixture.addFileToProject(
      "res/layout/activity_main.xml",
      //language=XML
      """<LinearLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:orientation="vertical"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <Button
              android:layout_width="match_parent"
              android:layout_height="match_parent"
              android:shadowColor="${caret}">
        </LinearLayout>""".trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.complete(CompletionType.BASIC)

    assertThat(myFixture.lookupElementStrings).contains("@color/colorPrimary")
    var colorElement: LookupElement? = null
    val lookup = myFixture.lookup
    for (element in lookup.items) {
      when (element.lookupString) {
        "@color/colorPrimary" -> colorElement = element
      }
    }

    lookup.currentItem = colorElement
    val ref = myFixture.file.findReferenceAt(myFixture.editor.caretModel.offset)!!
    val docTargetElement = DocumentationManager.getInstance(project).findTargetElement(
      myFixture.editor, myFixture.file, ref.element)
    val documentationProvider = DocumentationManager.getProviderFromElement(docTargetElement)
    assertThat(documentationProvider.generateDoc(docTargetElement, ref.element)).isEqualTo(
      """<html><body><table style="background-color:rgb(0,133,119);width:200px;text-align:center;vertical-align:middle;" border="0">""" +
        """<tr height="100"><td align="center" valign="middle" height="100" style="color:black">#008577</td></tr></table><BR/>""" +
        """@color/colorPrimary => #008577<BR/></body></html>""")
  }


  fun testDimenUnitsCompletion2() {
    doTestCompletionVariants(getTestName(true) + ".xml", "@android:", "@dimen/myDimen")
  }

  fun testDimenUnitsCompletion3() {
    doTestCompletionVariants(getTestName(true) + ".xml", "3pt", "3px")
  }

  fun testOnClickIntention() {
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntention.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val action = AndroidCreateOnClickHandlerAction()
    assertThat(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file)).isTrue()
    myFixture.launchAction(action)
    myFixture.checkResultByFile("$myTestFolder/onClickIntention.xml")
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", "$myTestFolder/OnClickActivity_after.java", false)
  }

  fun testOnClickIntentionIncorrectName() {
    myFixture.copyFileToProject("$myTestFolder/OnClickActivityIncorrectName.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntentionIncorrectName.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val action = AndroidCreateOnClickHandlerAction()
    assertThat(action.isAvailable(myFixture.project, myFixture.editor, myFixture.file)).isFalse()
  }

  fun testOnClickQuickFixEmptyKotlin() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity.kt", "src/p1/p2/Activity1.kt")
    val file = copyFileToProject("onClickIntentionWithContext.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(actions).hasSize(1)
    myFixture.launchAction(actions[0])
    myFixture.checkResultByFile("$myTestFolder/onClickIntentionWithContext.xml")
    myFixture.checkResultByFile("src/p1/p2/Activity1.kt", "$myTestFolder/OnClickActivity_after.kt", false)
  }

  fun testOnClickQuickFixKotlin() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivityWithLayout.kt", "src/p1/p2/Activity1.kt")
    val file = copyFileToProject("onClickIntentionWithContext.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(actions).hasSize(1)
    myFixture.launchAction(actions[0])

    myFixture.checkResultByFile("$myTestFolder/onClickIntentionWithContext.xml")
    myFixture.checkResultByFile("src/p1/p2/Activity1.kt", "$myTestFolder/OnClickActivityWithLayout_after.kt", false)
  }

  fun testOnClickQuickFixKotlinNoContext() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivityWithLayout.kt", "src/p1/p2/Activity1.kt")
    val file = copyFileToProject("onClickIntention.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(actions).hasSize(1)
    myFixture.launchAction(actions[0])

    myFixture.checkResultByFile("$myTestFolder/onClickIntention.xml")
    myFixture.checkResultByFile("src/p1/p2/Activity1.kt", "$myTestFolder/OnClickActivityWithLayout_after.kt", false)
  }

  fun testOnClickQuickFix1() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntention.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(fixes).isEmpty()
  }

  fun testOnClickQuickFix2() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity1.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntention.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(actions).hasSize(1)
    myFixture.launchAction(actions[0])

    myFixture.checkResultByFile("$myTestFolder/onClickIntention.xml")
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", "$myTestFolder/OnClickActivity1_after.java", false)
  }

  fun testOnClickQuickFix3() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity1.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntention.xml")
    doTestOnClickQuickfix(file)
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", "$myTestFolder/OnClickActivity2_after.java", false)
  }

  fun testOnClickQuickFix4() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity1.java", "src/p1/p2/Activity1.java")
    myFixture.copyFileToProject("$myTestFolder/OnClickActivity4.java", "src/p1/p2/Activity2.java")
    val file = copyFileToProject("onClickIntention.xml")
    doTestOnClickQuickfix(file)
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", "$myTestFolder/OnClickActivity1_after.java", false)
  }

  fun testOnClickQuickFixIncorrectName() {
    enableInspection(AndroidMissingOnClickHandlerInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/OnClickActivityIncorrectName.java", "src/p1/p2/Activity1.java")
    val file = copyFileToProject("onClickIntentionIncorrectName.xml")
    myFixture.configureFromExistingVirtualFile(file)
    val fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix::class.java)
    assertThat(fixes).isEmpty()
  }

  fun testSpellchecker() {
    enableInspection(SpellCheckingInspection::class.java)
    myFixture.copyFileToProject("$myTestFolder/spellchecker_resources.xml", "res/values/sr.xml")
    doTestHighlighting()
  }

  fun testSpellcheckerQuickfix() {
    myFixture.copyFileToProject("$myTestFolder/spellchecker_resources.xml", "res/values/sr.xml")
    doTestSpellcheckerQuickFixes()
  }

  fun testAarDependencyCompletion() {
    // See org.jetbrains.android.facet.ResourceFolderManager#isAarDependency
    PsiTestUtil.addLibrary(myModule, "myapklib.aar", getTestDataPath() + "/" + myTestFolder + "/myaar", "classes.jar",
                           "res")
    doTestCompletion()
  }

  fun testAarVisibilitySensitiveCompletion() {
    addAarDependency(myModule, "myaar", "com.example.myaar") { resDir ->
      @Language("XML")
      val stringsXml =
          """
          <resources>
            <string name="my_aar_private_string">private</string>
            <string name="my_aar_public_string">private</string>
          </resources>
          """.trimIndent()
      resDir.resolve("values/strings.xml").writeText(stringsXml)
      resDir.resolveSibling("public.txt").writeText("string my_aar_public_string")
    }
    doTestCompletion()
  }

  fun testAarDependencyHighlightingNamespaced() {
    enableNamespacing("myapp")
    addBinaryAarDependency(myModule)
    doTestHighlighting()
  }

  // Regression test for http://b/37128688
  fun testToolsCompletion() {
    // Don't offer tools: completion for the mockup editor yet.
    // Also tests that the current expected set of tools attributes are offered.
    doTestCompletionVariantsContains("toolsCompletion.xml",
                                     "tools:listfooter",
                                     "tools:listheader",
                                     "tools:listitem",
                                     "tools:targetApi",
                                     "tools:viewBindingType",
                                     "tools:ignore")
  }

  // Regression test for http://b/66240917
  fun testToolsCompletion2() {
    doTestPresentableCompletionVariants("toolsCompletion2.xml",
                                        "listfooter",
                                        "listheader",
                                        "listitem",
                                        "listSelector",
                                        "stateListAnimator")
  }

  fun testIncludeCompletion() {
    // <include> tag should support auto-completion of android:layout_XXX attributes.
    // The actual supported attributes depend on the type of parent.
    // (e.g. <include> tag in RelativeLayout support android:layout_alignXXX attributes
    //  and <include> tag in AbsoluteLayout support android:layout_x/y attributes.

    // Check all attributes here
    doTestCompletionVariants("include_in_linear_layout.xml",
                             "android:id",
                             "android:layout_gravity",
                             "android:layout_height",
                             "android:layout_margin",
                             "android:layout_marginBottom",
                             "android:layout_marginEnd",
                             "android:layout_marginHorizontal",
                             "android:layout_marginLeft",
                             "android:layout_marginRight",
                             "android:layout_marginStart",
                             "android:layout_marginTop",
                             "android:layout_marginVertical",
                             "android:layout_weight",
                             "android:layout_width",
                             "android:visibility")

    // The duplicated attributes have been tested, only test the specified attributes for the remaining test cases.

    doTestCompletionVariantsContains("include_in_relative_layout.xml",
                                     "android:layout_above",
                                     "android:layout_alignBaseline",
                                     "android:layout_alignBottom",
                                     "android:layout_alignEnd",
                                     "android:layout_alignLeft",
                                     "android:layout_alignParentBottom",
                                     "android:layout_alignParentEnd",
                                     "android:layout_alignParentLeft",
                                     "android:layout_alignParentRight",
                                     "android:layout_alignParentStart",
                                     "android:layout_alignParentTop",
                                     "android:layout_alignRight",
                                     "android:layout_alignStart",
                                     "android:layout_alignTop",
                                     "android:layout_alignWithParentIfMissing",
                                     "android:layout_centerHorizontal",
                                     "android:layout_centerInParent",
                                     "android:layout_centerVertical",
                                     "android:layout_toEndOf",
                                     "android:layout_toLeftOf",
                                     "android:layout_toRightOf",
                                     "android:layout_toStartOf")

    doTestCompletionVariantsContains("include_in_absolute_layout.xml",
                                     "android:layout_x",
                                     "android:layout_y")

    doTestCompletionVariantsContains("include_in_frame_layout.xml",
                                     "android:layout_gravity")

    // <include> tag should also support auto-completion of layout_XXX attributes with cusomized domain name.
    // For example, app:layout_constraintXXX attributes should be supported when it is in the ConstraintLayout.

    // TODO: Improve the test framework and test the cusomized domain case.
  }

  fun testRestricted() {
    myFixture.addClass(restrictText)
    myFixture.addClass(protectedView)
    myFixture.addClass(restrictedView)
    myFixture.addClass(view)

    toTestCompletion("restricted.xml", "restricted_after.xml")
  }

  fun testProtected() {
    myFixture.addClass(protectedView)
    myFixture.addClass(view)

    doTestCompletionVariants("protected.xml", "p1.p2.MyAddedImageView")
  }

  fun testTagCompletionUsingInnerClass() {
    myFixture.addClass(innerClass)

    toTestCompletion("innerClass1.xml", "innerClass1_after.xml")
  }

  fun testTagReplacementUsingInnerClass() {
    myFixture.addClass(innerClass)

    myFixture.configureFromExistingVirtualFile(copyFileToProject("innerClass2.xml"))
    myFixture.complete(CompletionType.BASIC)
    myFixture.type("\t")
    myFixture.checkResultByFile("$myTestFolder/innerClass2_after.xml")
  }

  fun testTagLayoutCompletionUsingInnerClass() {
    myFixture.addClass(innerClass)

    toTestCompletion("innerClass3.xml", "innerClass3_after.xml")
  }

  fun testConstraintReferencedIdsGoToAction() {
    setAndroidx()
    myFixture.addClass(constraintLayout)
    myFixture.addClass(barrier)
    myFixture.addFileToProject("res/values/values.xml", constraintLayoutResources)
    val file = myFixture.addFileToProject("res/layout/activity_main.xml",
      // language=xml
      """
      <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
          android:id="@+id/textView"
          android:layout_height="40dp"
          android:layout_width="match_parent"
          app:layout_constraintTop_toTopOf="parent" />

        <EditText
          android:id="@+id/editText"
          app:layout_constraintStart_toStartOf="parent"
          app:layout_constraintTop_toBottomOf="parent"/>

        <androidx.constraintlayout.widget.Barrier
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          app:constraint_referenced_ids="text${caret}View"/>
      </androidx.constraintlayout.widget.ConstraintLayout>
      """.trimIndent())
    // Checking the textView goto action
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val textViewReference = TargetElementUtil.findReference(myFixture.editor, myFixture.editor.caretModel.offset)
    assertThat(textViewReference).isNotNull()
    assertThat(textViewReference!!.canonicalText).isEqualTo("textView")

    // Add a second id and check its' goto action
    myFixture.moveCaret("app:constraint_referenced_ids=\"textView|\"")
    myFixture.type(",editText")
    myFixture.moveCaret("app:constraint_referenced_ids=\"textView,edit|Text\"")
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    val editTextReference = TargetElementUtil.findReference(myFixture.editor, myFixture.editor.caretModel.offset)
    assertThat(editTextReference).isNotNull()
    assertThat(editTextReference!!.canonicalText).isEqualTo("editText")

    // Check that the first id click action is not changed by the addition of the second id
    myFixture.moveCaret("app:constraint_referenced_ids=\"text|View")
    val textAgain = TargetElementUtil.findReference(myFixture.editor, myFixture.editor.caretModel.offset)
    assertThat(textAgain).isNotNull()
    assertThat(textAgain!!.canonicalText).isEqualTo("textView")

    //Add leading whitespace to an id in the constraint_referenced_ids
    myFixture.moveCaret("app:constraint_referenced_ids=\"textView,|")
    myFixture.type("  ")
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    myFixture.moveCaret("app:constraint_referenced_ids=\"textView,  edit|Text\"")
    val secondId = TargetElementUtil.findReference(myFixture.editor, myFixture.editor.caretModel.offset)
    assertThat(secondId).isNotNull()
    assertThat(secondId?.rangeInElement).isEqualTo(TextRange(12, 20))
    assertThat(secondId!!.canonicalText).isEqualTo("editText")
  }

  fun testConstraintReferencedCompletion() {
    setAndroidx()
    myFixture.addClass(constraintLayout)
    myFixture.addClass(barrier)
    myFixture.addFileToProject("res/values/values.xml", constraintLayoutResources)
    val file = myFixture.addFileToProject("res/layout/activity_main.xml",
      // language=xml
      """
      <androidx.constraintlayout.widget.ConstraintLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:app="http://schemas.android.com/apk/res-auto"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <TextView
          android:id="@+id/textView"
          android:layout_height="40dp"
          android:layout_width="match_parent"
          app:layout_constraintTop_toTopOf="parent" />

        <EditText
          android:id="@+id/editText"
          app:layout_constraintStart_toStartOf="parent"
          app:layout_constraintTop_toBottomOf="parent"/>

        <androidx.constraintlayout.widget.Barrier
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          app:constraint_referenced_ids="${caret}"/>
      </androidx.constraintlayout.widget.ConstraintLayout>
      """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactlyElementsIn(arrayOf("editText", "textView"))
    myFixture.type("\n,")
    myFixture.moveCaret("app:constraint_referenced_ids=\"edit|Text,\"")
    PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
    val editTextReference = TargetElementUtil.findReference(myFixture.editor, myFixture.editor.caretModel.offset)
    assertThat(editTextReference).isNotNull()
    assertThat(editTextReference!!.canonicalText).isEqualTo("editText")

    // Adding a second id
    myFixture.moveCaret("app:constraint_referenced_ids=\"editText,|\"")
    myFixture.completeBasic()
    assertThat(myFixture.lookupElementStrings).containsExactlyElementsIn(arrayOf("editText", "textView"))
  }

  fun testViewBindingTypeCompletion() {
    run { // test autocompleting the tools:viewBindingType label
      val file = myFixture.addFileToProject(
        "res/layout/activity_view_binding_type_label.xml",
        // language=XML
        """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout>
            <EditText
                xmlns:tools="http://schemas.android.com/tools"
                tools:${caret} />
          </LinearLayout>
        """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(file.virtualFile)
      myFixture.completeBasic()
      assertThat(myFixture.lookupElementStrings).contains("tools:viewBindingType")
    }

    run { // Test that Android views are suggested for the value of the tools:viewBindingType attribute
      val file = myFixture.addFileToProject(
        "res/layout/activity_view_binding_type_value.xml",
        // language=XML
        """
          <?xml version="1.0" encoding="utf-8"?>
          <LinearLayout>
            <EditText
                xmlns:tools="http://schemas.android.com/tools"
                tools:viewBindingType="Text${caret}" />
          </LinearLayout>
      """.trimIndent()
      )

      myFixture.configureFromExistingVirtualFile(file.virtualFile)
      myFixture.completeBasic()
      // Just choose a random sampling of autocompleted values, enough to show a pattern
      assertThat(myFixture.lookupElementStrings).containsAllOf(
        "android.widget.EditText",
        "android.widget.TextView",
        "android.view.TextureView")
      // Make sure random non-view classes aren't showing up in the list
      assertThat(myFixture.lookupElementStrings!!.all { suggestion -> suggestion.startsWith("android.") }).isTrue()
    }
  }

  fun testCoordinatorLayoutBehavior_classes() {
    setAndroidx()
    myFixture.addClass(coordinatorLayout)
    myFixture.addFileToProject("res/values/coordinatorlayout_attrs.xml", coordinatorLayoutResources)

    myFixture.addClass(
      // language=java
      """
        package com.example.behaviors;

        import androidx.coordinatorlayout.widget.CoordinatorLayout;

        public class MyBehavior extends CoordinatorLayout.Behavior {}
      """.trimIndent()
    )

    myFixture.addClass(
      // language=java
      """
        package com.example.behaviors;

        import androidx.coordinatorlayout.widget.CoordinatorLayout;

        public class SomeView {
          public static class SomeBehavior extends CoordinatorLayout.Behavior {}
        }
      """.trimIndent()
    )

    val layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=xml
      """
      <androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Hello World!"
              app:layout_behavior="$caret" />

      </androidx.coordinatorlayout.widget.CoordinatorLayout>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(layout.virtualFile)
    myFixture.completeBasic()

    assertThat(myFixture.lookupElementStrings).containsExactly(
      "com.example.behaviors.MyBehavior",
      "com.example.behaviors.SomeView\$SomeBehavior"
    )

    myFixture.type('\n')
    myFixture.checkHighlighting()
  }

  fun testCoordinatorLayoutBehavior_strings() {
    setAndroidx()
    myFixture.addClass(coordinatorLayout)
    myFixture.addFileToProject("res/values/coordinatorlayout_attrs.xml", coordinatorLayoutResources)

    val layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=xml
      """
      <androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:text="Hello World!"
              app:layout_behavior="@string/appbar_scrolling_view_behavior" />

      </androidx.coordinatorlayout.widget.CoordinatorLayout>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(layout.virtualFile)
    myFixture.checkHighlighting()
  }

  /**
   * Previously, "< ", a tag without a name, would cause the inspection logic to throw an
   * exception with a message like:
   *
   * "Argument rangeInElement (39,40) endOffset must not exceed descriptor text range (39, 40) length (1)."
   *
   * This was caused because the inspection code that found XML tags without a name would
   * incorrectly receive an absolute offset instead of a relative one.
   *
   * For more context, see https://youtrack.jetbrains.com/issue/IDEA-205629
   */
  @Test
  fun testNamelessXmlTag_doesntThrowException() {
    val layout = myFixture.addFileToProject(
      "res/layout/my_layout.xml",
      // language=xml
      """
      <!-- Blank line intentionally added, which used to trigger an out of range exception -->
      <<EOLError descr="Tag name expected"></EOLError>
      """.trimIndent()
    )

    myFixture.configureFromExistingVirtualFile(layout.virtualFile)
    myFixture.checkHighlighting()
  }

  fun testFramework9Patch() {
    myFixture.loadNewFile(
      "res/layout/my_layout.xml",
      // language=xml
      """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
          android:layout_width="match_parent"
          android:layout_height="match_parent">
        <ImageView
          android:src="@android:drawable/dark_header"
          android:layout_width="match_parent"
          android:layout_height="match_parent" />
      </LinearLayout>
      """.trimIndent()
    )

    myFixture.checkHighlighting()
  }

  private fun doTestAttrReferenceCompletion(textToType: String) {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml")
    val file = copyFileToProject(getTestName(true) + ".xml")
    myFixture.configureFromExistingVirtualFile(file)
    myFixture.complete(CompletionType.BASIC)
    myFixture.type(textToType)
    myFixture.checkResultByFile(myTestFolder + '/'.toString() + getTestName(true) + "_after.xml")
  }

  private fun containElementStartingWith(elements: List<String>, prefix: String): Boolean {
    for (element in elements) {
      if (element.startsWith(prefix)) {
        return true
      }
    }
    return false
  }

  private fun doCreateFileResourceFromUsage(virtualFile: VirtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile)
    val actions = highlightAndFindQuickFixes(CreateFileResourceQuickFix::class.java)
    assertThat(actions).hasSize(1)

    runWriteCommandAction(project) { actions[0].invoke(project, myFixture.editor, myFixture.file) }
  }

  private fun enableInspection(inspectionClass: Class<out LocalInspectionTool>) {
    myFixture.enableInspections(setOf(inspectionClass))
  }

  private fun setAndroidx() = runWriteCommandAction(project) {
    project.setAndroidxProperties("true")
    assertThat(project.isAndroidx()).isTrue()  // Sanity check, regression test for b/145854589.
  }
}
