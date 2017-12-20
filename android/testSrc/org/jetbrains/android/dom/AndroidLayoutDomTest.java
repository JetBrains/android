package org.jetbrains.android.dom;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.HashSet;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.inspections.AndroidMissingOnClickHandlerInspection;
import org.jetbrains.android.inspections.CreateFileResourceQuickFix;
import org.jetbrains.android.inspections.CreateValueResourceQuickFix;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

/**
 * Tests semantic highlighting and completion in layout XML files.
 */
public class AndroidLayoutDomTest extends AndroidDomTestCase {
  public AndroidLayoutDomTest() {
    super("dom/layout");
  }

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "res/layout/" + testFileName;
  }

  public void testAttributeNameCompletion1() throws Throwable {
    doTestCompletionVariants("an1.xml", "layout_weight", "layout_width");
  }

  public void testAttributeNameCompletion2() throws Throwable {
    toTestCompletion("an2.xml", "an2_after.xml");
  }

  public void testAttributeNameCompletion3() throws Throwable {
    toTestCompletion("an3.xml", "an3_after.xml");
  }

  public void testAttributeNameCompletion4() throws Throwable {
    toTestCompletion("an4.xml", "an4_after.xml");
  }

  public void testAttributeNameCompletion5() throws Throwable {
    toTestCompletion("an5.xml", "an5_after.xml");
  }

  public void testAttributeNameCompletion6() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("an6.xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\n");
    myFixture.checkResultByFile(myTestFolder + "/an6_after.xml");
  }

  public void testAttributeNameCompletion7() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("an7.xml"));
    myFixture.complete(CompletionType.BASIC);
    List<String> lookupElementStrings = myFixture.getLookupElementStrings();
    lookupElementStrings = lookupElementStrings.subList(0, 5);
    UsefulTestCase.assertSameElements(
      lookupElementStrings, "android:layout_above", "android:layout_alignBaseline",
      "android:layout_alignBottom", "android:layout_alignEnd", "android:layout_alignLeft");
  }

  public void testOpenDrawerAttributeNameCompletion() throws Throwable {
    // For unit tests there are no support libraries, copy dummy DrawerLayout class that imitates the support library one
    myFixture.copyFileToProject(myTestFolder + "/DrawerLayout.java", "src/android/support/v4/widget/DrawerLayout.java");
    toTestCompletion("drawer_layout.xml", "drawer_layout_after.xml");
  }

  // Deprecated attributes should be crossed out in the completion
  // This test specifically checks for "android:editable" attribute on TextView
  public void testDeprecatedAttributeNamesCompletion() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("text_view_editable.xml"));
    myFixture.complete(CompletionType.BASIC);

    // LookupElement that corresponds to "android:editable" attribute
    LookupElement editableElement = null;
    for (LookupElement element : myFixture.getLookupElements()) {
      if ("android:editable".equals(element.getLookupString())) {
        editableElement = element;
      }
    }

    assertEquals("android:editable", editableElement.getLookupString());
    LookupElementPresentation presentation = new LookupElementPresentation();
    editableElement.renderElement(presentation);
    assertTrue(presentation.isStrikeout());
  }

  // "conDes" is completed to "android:contentDescription", "xmlns:android" with right value is inserted
  public void testAutoAddNamespaceCompletion() throws Throwable {
    toTestCompletion("android_content.xml", "android_content_after.xml");
  }

  // "tools:" inside tag should autocomplete to available tools attributes, only "tools:targetApi" in this case
  public void testToolsPrefixedAttributeCompletion() throws Throwable {
    toTestCompletion("tools_namespace_attrs.xml", "tools_namespace_attrs_after.xml");
  }

  // ListView has some specific autocompletion attributes, like "listfooter", they should be autocompleted as well
  public void testToolsListViewAttributes() throws Throwable {
    doTestCompletionVariantsContains("tools_listview_attrs.xml", "tools:targetApi", "tools:listfooter", "tools:listheader", "tools:listitem");
  }

  // tools:targetApi values are autocompleted
  public void testTargetApiValueCompletion() throws Throwable {
    doTestCompletionVariants("tools_targetapi.xml", "HONEYCOMB", "HONEYCOMB_MR1", "HONEYCOMB_MR2");
  }

  // test @tools:sample datasources completion
  public void testToolsSampleCompletion() throws Throwable {
    doTestCompletionVariantsContains("tools_sample_completion.xml", "@tools:sample/full_names", "@tools:sample/lorem");
  }

  // "-1" is not a valid tools:targetApi value
  public void testTargetApiErrorMessage1() throws Throwable {
    doTestHighlighting("tools_targetapi_error1.xml");
  }

  // "apple_pie" is not a valid tools:targetApi value as well
  public void testTargetApiErrorMessage2() throws Throwable {
    doTestHighlighting("tools_targetapi_error2.xml");
  }

  // Designtime attributes completion is showing completion variants
  public void testDesigntimeAttributesCompletion() throws Throwable {
    doTestCompletionVariants("tools_designtime_completion.xml", "src", "nextFocusRight");
  }

  // Designtime attributes completion is completing attribute names correctly
  public void testDesigntimeAttributesCompletion2() throws Throwable {
    toTestFirstCompletion("tools_designtime_completion_background.xml",
                          "tools_designtime_completion_background_after.xml");
  }

  public void testToolsUseHandlerAttribute() throws Throwable {
    doTestCompletionVariants("tools_use_handler_completion.xml", "android.view.TextureView",
                             "android.widget.AutoCompleteTextView",
                             "android.widget.CheckedTextView",
                             "android.widget.MultiAutoCompleteTextView",
                             "android.widget.TextView");
  }

  // Code completion in views inside a <layout> tag need to pick up default layout params
  public void testDataBindingLayoutParamCompletion() throws Throwable {
    // Regression test for https://code.google.com/p/android/issues/detail?id=212690
    toTestFirstCompletion("data_binding_completion.xml",
                          "data_binding_completion_after.xml");
  }

  // fontFamily attribute values are autocompleted
  public void testFontFamilyCompletion() throws Throwable {
    doTestCompletionVariants("text_view_font_family.xml", "monospace", "serif-monospace");
  }

  public void testCommonPrefixIdea63531() throws Throwable {
    VirtualFile file = copyFileToProject("commonPrefixIdea63531.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + '/' + "commonPrefixIdea63531_after.xml");
  }

  public void testHighlighting() throws Throwable {
    doTestHighlighting("hl.xml");
  }

  public void testHighlighting2() throws Throwable {
    copyFileToProject("integers.xml", "res/values/integers.xml");
    doTestHighlighting("hl2.xml");
  }

  public void testWrongEnumValuesHighlighting() throws Throwable {
    doTestHighlighting("wrong_enum_value.xml");
  }

  public void testTableRowRootTag() throws Throwable {
    doTestHighlighting();
  }

  public void testCheckLayoutAttrs() throws Throwable {
    doTestHighlighting("layoutAttrs.xml");
  }

  public void testCheckLayoutAttrs1() throws Throwable {
    doTestHighlighting("layoutAttrs1.xml");
  }

  public void testCheckLayoutAttrs2() throws Throwable {
    doTestHighlighting("layoutAttrs2.xml");
  }

  public void testCheckLayoutAttrs3() throws Throwable {
    doTestHighlighting("layoutAttrs3.xml");
  }

  public void testUnknownAttribute() throws Throwable {
    doTestHighlighting("hl1.xml");
  }

  public void testMissingRequired() throws Throwable {
    doTestHighlighting("missing_attrs.xml");
  }

  @Language("JAVA")
  String recyclerView =
    "package android.support.v7.widget;\n" +
    "\n" +
    "import android.widget.ViewGroup;\n" +
    "\n" +
    "public class RecyclerView extends ViewGroup {\n" +
    "  public abstract static class LayoutManager {\n" +
    "  }\n" +
    "}\n" +
    "\n" +
    "public class GridLayoutManager extends RecyclerView.LayoutManager {\n" +
    "}\n" +
    "\n" +
    "public class LinearLayoutManager extends RecyclerView.LayoutManager {\n" +
    "}";

  @Language("XML")
  String recyclerViewAttrs =
    "<resources>\n" +
    "    <declare-styleable name=\"RecyclerView\">\n" +
    "        <attr name=\"layoutManager\" format=\"string\" />\n" +
    "    </declare-styleable>\n" +
    "</resources>";

  public void testLayoutManagerAttribute() throws Throwable {
    // RecyclerView has a "layoutManager" attribute that should give completions that extend
    // the RecyclerView.LayoutManager class.
    myFixture.addClass(recyclerView);
    myFixture.addFileToProject("res/values/recyclerView_attrs.xml", recyclerViewAttrs);
    doTestCompletionVariants("recycler_view.xml",
                             "android.support.v7.widget.GridLayoutManager",
                             "android.support.v7.widget.LinearLayoutManager");
  }

  public void testDataBindingHighlighting1() throws Throwable {
    ModuleDataBinding.enable(myFacet);
    copyFileToProject("User.java", "src/com/android/example/bindingdemo/vo/User.java");
    doTestHighlighting("binding1.xml");
  }

  public void testDataBindingHighlighting2() throws Throwable {
    ModuleDataBinding.enable(myFacet);
    doTestHighlighting("binding5.xml");
  }

  public void testDataBindingHighlighting3() throws Throwable {
    ModuleDataBinding.enable(myFacet);
    copyFileToProject("DataBindingHighlighting3.java", "src/p1/p2/DataBindingHighlighting3.java");
    doTestHighlighting("databinding_highlighting3.xml");
  }

  public void testDataBindingCompletion1() throws Throwable {
    doTestCompletionVariants("binding2.xml", "name", "type");
  }

  public void testDataBindingCompletion2() throws Throwable {
    toTestCompletion("binding3.xml", "binding3_after.xml");
  }

  public void testDataBindingCompletion3() throws Throwable {
    toTestCompletion("binding4.xml", "binding4_after.xml");
    //doTestCompletionVariants("binding4.xml", "safeUnbox", "superCool");
  }

  public void testCustomTagCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn.xml", "ctn_after.xml");
  }

  @SuppressWarnings("ConstantConditions")
  public void testCustomTagCompletion0() throws Throwable {
    VirtualFile labelViewJava = copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");

    VirtualFile lf1 = myFixture.copyFileToProject(myTestFolder + '/' + "ctn0.xml", "res/layout/layout1.xml");
    myFixture.configureFromExistingVirtualFile(lf1);
    myFixture.complete(CompletionType.BASIC);
    List<String> variants = myFixture.getLookupElementStrings();
    assertTrue(variants.contains("p1.p2.LabelView"));

    PsiFile psiLabelViewFile = PsiManager.getInstance(getProject()).findFile(labelViewJava);
    assertInstanceOf(psiLabelViewFile, PsiJavaFile.class);
    myFixture.renameElement(((PsiJavaFile)psiLabelViewFile).getClasses()[0], "LabelView1");

    VirtualFile lf2 = myFixture.copyFileToProject(myTestFolder + '/' + "ctn0.xml", "res/layout/layout2.xml");
    myFixture.configureFromExistingVirtualFile(lf2);
    myFixture.complete(CompletionType.BASIC);
    variants = myFixture.getLookupElementStrings();
    assertFalse(variants.contains("p1.p2.LabelView"));
    assertTrue(variants.contains("p1.p2.LabelView1"));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        labelViewJava.delete(null);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    VirtualFile lf3 = myFixture.copyFileToProject(myTestFolder + '/' + "ctn0.xml", "res/layout/layout3.xml");
    myFixture.configureFromExistingVirtualFile(lf3);
    myFixture.complete(CompletionType.BASIC);
    variants = myFixture.getLookupElementStrings();
    assertFalse(variants.contains("p1.p2.LabelView"));
    assertFalse(variants.contains("p1.p2.LabelView1"));
  }

  public void testCustomTagCompletion1() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    copyFileToProject("IncorrectView.java", "src/p1/p2/IncorrectView.java");
    doTestCompletionVariants("ctn1.xml", "p2.LabelView", "p2.LabelView1");
  }

  public void testCustomTagCompletion2() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("ctn2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("p1\n");
    myFixture.checkResultByFile(myTestFolder + '/' + "ctn2_after.xml");
  }

  public void testCustomTagCompletion3() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn3.xml", "ctn3_after.xml");
  }

  public void testCustomTagCompletion4() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("ctn4.xml", "LabelView");
  }

  public void testCustomTagCompletion5() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("ctn5.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("p1\n");
    myFixture.checkResultByFile(myTestFolder + '/' + "ctn5_after.xml");
  }

  public void testCustomTagCompletion6() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn6.xml", "ctn6_after.xml");
  }

  public void testCustomTagCompletion7() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn7.xml", "ctn6_after.xml");
  }

  public void testCustomTagCompletion8() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    doTestCompletionVariants("ctn8.xml", "LabelView");
  }

  public void testCustomTagCompletion9() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("ctn9.xml", "ctn9_after.xml");
  }

  public void testCustomTagCompletion10() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    copyFileToProject("LabelView1.java", "src/p1/p2/LabelView1.java");
    doTestCompletionVariants("ctn10.xml", "LabelView");
  }

  public void testCustomAttributeNameCompletion() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("can.xml", "text", "textColor", "textSize");
  }

  public void testCustomAttributeNameCompletion1() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestCompletionVariants("can1.xml",
                             "context", "contextClickable", "text", "textAlignment", "textColor", "textDirection", "textSize",
                             "tooltipText");
  }

  public void testCustomAttributeNameCompletion2() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    VirtualFile file = copyFileToProject("can2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("text");

    UsefulTestCase.assertSameElements(myFixture.getLookupElementStrings(),
                                      "android:contextClickable", "android:textAlignment", "android:textDirection",
                                      "android:tooltipText", "text", "textColor", "textSize");
  }

  public void testCustomAttributeNameCompletion3() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("can3.xml", "can3_after.xml");
  }

  public void testCustomAttributeNameCompletion4() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("can4.xml", "can4_after.xml");
  }

  public void testCustomAttributeNameCompletion5() throws Throwable {
    myFacet.setProjectType(PROJECT_TYPE_LIBRARY);
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    toTestCompletion("can5.xml", "can5_after.xml");
  }

  public void testToolsAttributesCompletion() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    // Create layout that we will use to test the layout completion
    myFixture.copyFileToProject(myTestFolder + "/tools_context_completion_after.xml", "res/layout/other_layout.xml");
    toTestFirstCompletion("tools_context_completion.xml", "tools_context_completion_after.xml");
    toTestCompletion("tools_showIn_completion.xml", "tools_showIn_completion_after.xml");
    toTestCompletion("tools_parentTag_completion.xml", "tools_parentTag_completion_after.xml");
  }

  public void testCustomAttributeValueCompletion() throws Throwable {
    doTestCompletionVariants("cav.xml", "@color/color0", "@color/color1", "@color/color2");
  }

  public void testIdea64993() throws Throwable {
    copyFileToProject("LabelView.java", "src/p1/p2/LabelView.java");
    doTestHighlighting();
  }

  public void testTagNameCompletion1() throws Throwable {
    VirtualFile file = copyFileToProject("tn1.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + "tn1_after.xml");
  }

  public void testFlagCompletion() throws Throwable {
    doTestCompletionVariants("av1.xml", "center", "center_horizontal", "center_vertical");
    doTestCompletionVariants("av2.xml", "fill_horizontal", "fill_vertical");
  }

  public void testFlagCompletion1() throws Throwable {
    doTestCompletionVariants("flagCompletion1.xml", "center", "center_horizontal", "center_vertical", "center|bottom",
                             "center|center_horizontal", "center|center_vertical", "center|clip_horizontal", "center|clip_vertical",
                             "center|end", "center|fill", "center|fill_horizontal", "center|fill_vertical", "center|left",
                             "center|right", "center|start", "center|top");
  }

  public void testFlagCompletion2() throws Throwable {
    doTestCompletionVariants("flagCompletion2.xml", "center", "center_horizontal", "center_vertical", "center|center_horizontal",
                             "center|center_vertical", "center|clip_horizontal", "center|clip_vertical", "center|end", "center|fill",
                             "center|fill_horizontal", "center|fill_vertical", "center|left", "center|right", "center|start",
                             "center|top");
    myFixture.type("|fill");

    UsefulTestCase.assertSameElements(myFixture.getLookupElementStrings(), "center|fill", "center|fill_horizontal", "center|fill_vertical");
  }

  public void testResourceCompletion() throws Throwable {
    doTestCompletionVariantsContains("av3.xml", "@color/color0", "@color/color1", "@android:", "@drawable/picture2", "@drawable/picture1");
    doTestCompletionVariantsContains("av8.xml", "@android:", "@anim/anim1", "@color/color0", "@color/color1", "@dimen/myDimen",
                                     "@drawable/picture1", "@layout/av3", "@layout/av8", "@string/itStr", "@string/hello", "@style/style1");
  }

  public void testLocalResourceCompletion1() throws Throwable {
    doTestCompletionVariants("av4.xml", "@color/color0", "@color/color1", "@color/color2");
  }

  public void testLocalResourceCompletion2() throws Throwable {
    doTestCompletionVariants("av5.xml", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3", "@drawable/cdrawable");
  }

  public void testLocalResourceCompletion3() throws Throwable {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr");
  }

  public void testLocalResourceCompletion4() throws Throwable {
    doTestCompletionVariants("av7.xml", "@android:", "@string/hello", "@string/hello1", "@string/welcome", "@string/welcome1",
                             "@string/itStr");
  }

  public void testLocalResourceCompletion5() throws Throwable {
    doTestCompletionVariants("av12.xml", "@android:", "@anim/anim1", "@anim/anim2");
  }

  public void testLocalResourceCompletion6() throws Throwable {
    doTestCompletionVariants("av14.xml", "@android:", "@color/color0", "@color/color1", "@color/color2", "@drawable/cdrawable", "@drawable/picture1", "@drawable/picture2", "@drawable/picture3");
  }

  public void testForceLocalResourceCompletion() throws Throwable {
    // No system colors are suggested as completion.
    doTestCompletionVariants("av13.xml", "@color/color0", "@color/color1", "@color/color2");
  }

  public void testSystemResourceCompletion() throws Throwable {
    doTestCompletionVariantsContains("av6.xml", "@android:color/primary_text_dark", "@android:drawable/menuitem_background");
  }

  public void testCompletionSpecialCases() throws Throwable {
    doTestCompletionVariants("av9.xml", "@string/hello", "@string/hello1");
  }

  public void testLayoutAttributeValuesCompletion() throws Throwable {
    doTestCompletionVariants("av10.xml", "fill_parent", "match_parent", "wrap_content", "@android:", "@dimen/myDimen");
    doTestCompletionVariants("av11.xml", "center", "center_horizontal", "center_vertical");
    doTestCompletionVariants("av15.xml", "horizontal", "vertical");
  }

  public void testFloatAttributeValuesCompletion() throws Throwable {
    copyFileToProject("myIntResource.xml", "res/values/myIntResource.xml");
    doTestCompletionVariants("floatAttributeValues.xml", "@android:", "@integer/my_integer");
  }

  public void testDrawerLayoutOpenDrawerCompletion() throws Throwable {
    // For unit tests there are no support libraries, copy dummy DrawerLayout class that imitates the support library one
    myFixture.copyFileToProject(myTestFolder + "/DrawerLayout.java", "src/android/support/v4/widget/DrawerLayout.java");
    doTestCompletionVariants("drawer_layout_attr_completion.xml", "start", "end", "left", "right");
  }

  public void testTagNameCompletion2() throws Throwable {
    VirtualFile file = copyFileToProject("tn2.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.assertPreferredCompletionItems(0, "EditText", "ExpandableListView", "android.inputmethodservice.ExtractEditText");
  }

  public void testTagNameCompletion3() throws Throwable {
    doTestCompletionVariants("tn3.xml", "ActionMenuView", "AdapterViewFlipper", "AutoCompleteTextView", "CalendarView", "CheckedTextView",
                             "ExpandableListView", "GridView", "HorizontalScrollView", "ImageView", "ListView", "MultiAutoCompleteTextView",
                             "ScrollView", "SearchView", "StackView", "SurfaceView", "TextView", "TextureView", "VideoView", "View",
                             "ViewAnimator", "ViewFlipper", "ViewStub", "ViewSwitcher", "WebView", "android.appwidget.AppWidgetHostView",
                             "android.gesture.GestureOverlayView", "android.inputmethodservice.KeyboardView", "android.media.tv.TvView",
                             "android.opengl.GLSurfaceView");
  }

  /*public void testTagNameCompletion4() throws Throwable {
    toTestCompletion("tn4.xml", "tn4_after.xml");
  }*/

  public void testTagNameCompletion5() throws Throwable {
    toTestFirstCompletion("tn5.xml", "tn5_after.xml");
  }

  public void testTagNameCompletion6() throws Throwable {
    VirtualFile file = copyFileToProject("tn6.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);

    assertFalse(myFixture.getLookupElementStrings().contains("android.widget.Button"));
  }

  public void testTagNameCompletion7() throws Throwable {
    toTestCompletion("tn7.xml", "tn7_after.xml");
  }

  public void testTagNameCompletion8() throws Throwable {
    VirtualFile file = copyFileToProject("tn8.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);

    assertTrue(myFixture.getLookupElementStrings().contains("widget.Button"));
  }

  public void testTagNameCompletion9() throws Throwable {
    toTestCompletion("tn9.xml", "tn9_after.xml");
  }

  public void testTagNameCompletion10() throws Throwable {
    VirtualFile file = copyFileToProject("tn10.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);

    assertFalse(myFixture.getLookupElementStrings().contains("android.widget.Button"));
  }

  public void testTagNameCompletion11() throws Throwable {
    toTestCompletion("tn11.xml", "tn11_after.xml");
  }

  public void testDeprecatedTagsAreLastInCompletion() throws Throwable {
    VirtualFile file = copyFileToProject("tagName_letter_G.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);

    // Gallery is deprecated and thus should be the last in completion list
    myFixture.assertPreferredCompletionItems(0, "GridLayout", "GridView", "android.gesture.GestureOverlayView", "android.opengl.GLSurfaceView", "Gallery");
  }

  // Completion by simple class name in layouts should work, inserting fully-qualified names
  // http://b.android.com/179380
  public void testTagNameCompletionBySimpleName() throws Throwable {
    toTestCompletion("tn13.xml", "tn13_after.xml");
  }

  // Test that support library component alternatives are pushed higher in completion
  public void testSupportLibraryCompletion() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/GridLayout.java", "src/android/support/v7/widget/GridLayout.java");
    VirtualFile file = copyFileToProject("tn14.xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> completionResult = myFixture.getLookupElementStrings();

    // Check the elements are in the right order
    assertEquals("android.support.v7.widget.GridLayout", completionResult.get(0));
    assertEquals("GridLayout", completionResult.get(1));
  }

  // Test android:layout_width and android:layout_height highlighting for framework and library layouts
  public void testWidthHeightHighlighting() throws Throwable {
    // For unit tests there are no support libraries, copy dummy classes that imitate support library ones
    myFixture.copyFileToProject(myTestFolder + "/PercentRelativeLayout.java", "src/android/support/percent/PercentRelativeLayout.java");
    myFixture.copyFileToProject(myTestFolder + "/PercentFrameLayout.java", "src/android/support/percent/PercentFrameLayout.java");

    doTestHighlighting("dimensions_layout.xml");
  }

  public void testTagNameIcons1() throws Throwable {
    doTestTagNameIcons("tn10.xml");
  }

  public void testTagNameIcons2() throws Throwable {
    doTestTagNameIcons("tn12.xml");
  }

  private void doTestTagNameIcons(String fileName) throws IOException {
    VirtualFile file = copyFileToProject(fileName);
    myFixture.configureFromExistingVirtualFile(file);
    LookupElement[] elements = myFixture.complete(CompletionType.BASIC);
    Set<String> elementsToCheck = new HashSet<>(Arrays.asList(
      "view", "include", "requestFocus", "fragment", "Button"));

    for (LookupElement element : elements) {
      String s = element.getLookupString();
      Object obj = element.getObject();

      if (elementsToCheck.contains(s)) {
        LookupElementPresentation presentation = new LookupElementPresentation();
        element.renderElement(presentation);
        assertNotNull("no icon for element: " + element, presentation.getIcon());

        if ("Button".equals(s)) {
          assertInstanceOf(obj, PsiClass.class);
        }
      }
    }
  }

  public void testIdCompletion1() throws Throwable {
    doTestCompletionVariants("idcompl1.xml", "@android:", "@+id/");
  }

  public void testIdCompletion2() throws Throwable {
    doTestCompletionVariantsContains("idcompl2.xml",
                                     "@android:id/text1", "@android:id/text2", "@android:id/inputExtractEditText",
                                     "@android:id/selectTextMode", "@android:id/startSelectingText", "@android:id/stopSelectingText");
  }

  public void testNestedScrollView() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/NestedScrollView.java", "src/android/support/v4/widget/NestedScrollView.java");
    toTestCompletion("nestedScrollView.xml", "nestedScrollView_after.xml");
  }

  public void testExtendedNestedScrollView() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/NestedScrollView.java", "src/android/support/v4/widget/NestedScrollView.java");
    myFixture.copyFileToProject(myTestFolder + "/ExtendedNestedScrollView.java", "src/p1/p2/ExtendedNestedScrollView.java");
    toTestCompletion("extendedNestedScrollView.xml", "extendedNestedScrollView_after.xml");
  }

  public void testNewIdCompletion1() throws Throwable {
    toTestCompletion("newIdCompl1.xml", "newIdCompl1_after.xml");
  }

  public void testNewIdCompletion2() throws Throwable {
    toTestCompletion("newIdCompl2.xml", "newIdCompl2_after.xml");
  }

  public void testIdHighlighting() throws Throwable {
    doTestHighlighting("idh.xml");
  }

  public void testIdHighlighting1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("idh.xml", "res/layout-large/idh.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testStyleNamespaceHighlighting() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("stylesNamespaceHighlight.xml", "res/values/stylesNamespaceHighlight.xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  // Regression test for http://b.android.com/175619
  public void testStyleShortNameCompletion() throws Throwable {
    myFixture.configureFromExistingVirtualFile(copyFileToProject("StyleNameCompletion_layout.xml", "res/layout/layout.xml"));
    copyFileToProject("StyleNameCompletion_style.xml", "res/values/styles.xml");
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResultByFile(myTestFolder + "/StyleNameCompletion_layout_after.xml");
  }

  public void testIdReferenceCompletion() throws Throwable {
    toTestCompletion("idref1.xml", "idref1_after.xml");
  }

  public void testSystemIdReferenceCompletion() throws Throwable {
    toTestCompletion("idref2.xml", "idref2_after.xml");
  }

  public void testSystemResourcesHighlighting() throws Throwable {
    doTestHighlighting("systemRes.xml");
  }

  public void testViewClassCompletion() throws Throwable {
    toTestCompletion("viewclass.xml", "viewclass_after.xml");
  }

  public void testViewElementHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testPrimitiveValues() throws Throwable {
    doTestHighlighting("primValues.xml");
  }

  public void testTableCellAttributes() throws Throwable {
    toTestCompletion("tableCell.xml", "tableCell_after.xml");
  }

  public void testTextViewRootTag_IDEA_62889() throws Throwable {
    doTestCompletionVariants("textViewRootTag.xml", "AutoCompleteTextView", "CheckedTextView", "MultiAutoCompleteTextView", "TextView",
                             "TextureView");
  }

  public void testRequestFocus() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testMerge() throws Throwable {
    doTestHighlighting("merge.xml");
  }

  public void testMerge1() throws Throwable {
    doTestCompletion();
  }

  public void testMerge2() throws Throwable {
    doTestCompletion();
  }

  public void testFragmentHighlighting() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    doTestHighlighting(getTestName(true) + ".xml");
  }

  public void testFragmentHighlighting1() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    doTestHighlighting(getTestName(true) + ".xml");
  }

  public void testFragmentCompletion1() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion2() throws Throwable {
    toTestFirstCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion3() throws Throwable {
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion4() throws Throwable {
    copyFileToProject("MyFragmentActivity.java", "src/p1/p2/MyFragmentActivity.java");
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion5() throws Throwable {
    toTestFirstCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion6() throws Throwable {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(true) + "_after.xml");
  }

  public void testFragmentCompletion7() throws Throwable {
    doTestCompletionVariants("fragmentCompletion7.xml",
                             "tools:layout",
                             "tools:targetApi");
  }

  public void testCustomAttrsPerformance() throws Throwable {
    myFixture.copyFileToProject("dom/resources/bigfile.xml", "res/values/bigfile.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs1.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs2.xml");
    myFixture.copyFileToProject("dom/resources/bigattrs.xml", "res/values/bigattrs3.xml");
    VirtualFile f = copyFileToProject("bigfile.xml");
    myFixture.configureFromExistingVirtualFile(f);

    PlatformTestUtil.startPerformanceTest("android custom attrs highlighting", 800, () -> myFixture.doHighlighting()).attempts(2).usesAllCPUCores().assertTiming();
  }

  public void testSupportGridLayoutCompletion() throws Throwable {
    myFixture.copyFileToProject("dom/layout/GridLayout.java", "src/android/support/v7/widget/GridLayout.java");
    myFixture.copyFileToProject("dom/resources/attrs_gridlayout.xml", "res/values/attrs_gridlayout.xml");
    doTestCompletionVariants(getTestName(true) + ".xml", "rowCount", "rowOrderPreserved");
  }

  public void testSupportGridLayoutCompletion2() throws Throwable {
    myFixture.copyFileToProject("dom/layout/GridLayout.java", "src/android/support/v7/widget/GridLayout.java");
    myFixture.copyFileToProject("dom/resources/attrs_gridlayout.xml", "res/values/attrs_gridlayout.xml");
    toTestCompletion(getTestName(true) + ".xml", getTestName(true) + "_after.xml");
  }

  public void testViewClassReference() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(myTestFolder + "/vcr.xml", getPathToCopy("vcr.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("ScrollView");
    PsiElement rootViewClass = psiFile.findReferenceAt(rootOffset).resolve();
    assertTrue("Must be PsiClass reference", rootViewClass instanceof PsiClass);
    int childOffset = text.indexOf("LinearLayout");
    PsiElement childViewClass = psiFile.findReferenceAt(childOffset).resolve();
    assertTrue("Must be PsiClass reference", childViewClass instanceof PsiClass);
  }

  public void testViewClassReference1() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(myTestFolder + "/vcr1.xml", getPathToCopy("vcr1.xml"));
    myFixture.testHighlighting(true, false, true, file);
  }

  public void testViewClassReference2() throws Throwable {
    VirtualFile file = myFixture.copyFileToProject(myTestFolder + "/vcr2.xml", getPathToCopy("vcr2.xml"));
    myFixture.configureFromExistingVirtualFile(file);
    PsiFile psiFile = myFixture.getFile();
    String text = psiFile.getText();
    int rootOffset = text.indexOf("ScrollView");

    PsiElement rootViewClass = psiFile.findReferenceAt(rootOffset).resolve();
    assertTrue("Must be PsiClass reference", rootViewClass instanceof PsiClass);
  }

  public void testOnClickCompletion() throws Throwable {
    copyOnClickClasses();
    doTestCompletionVariants(getTestName(true) + ".xml", "clickHandler1", "clickHandler7");
  }

  public void testOnClickHighlighting() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    copyOnClickClasses();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    doTestHighlighting();
  }

  public void testOnClickHighlighting1() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity3.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting2() throws Throwable {
    copyOnClickClasses();
    doTestHighlighting();
  }

  public void testOnClickHighlighting3() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity5.java", "src/p1/p2/Activity1.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting4() throws Throwable {
    myFixture.allowTreeAccessForAllFiles();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity6.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlighting5() throws Throwable {
    // Regression test for https://code.google.com/p/android/issues/detail?id=76262
    myFixture.allowTreeAccessForAllFiles();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity7.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity8.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  // See http://b.android.com/230153
  public void ignore_testOnClickHighlighting6() throws Throwable {
    // Like testOnClickHighlighting5, but instead of having the activity be found
    // due to a setContentView call, it's declared explicitly with a tools:context
    // attribute instead
    myFixture.allowTreeAccessForAllFiles();
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity7.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity9.java", "src/p1/p2/Activity2.java");
    doTestHighlighting();
  }

  public void testOnClickHighlightingJava() throws Throwable {
    myFixture.enableInspections(new UnusedDeclarationInspection());
    VirtualFile f = myFixture.copyFileToProject(myTestFolder + "/" + getTestName(true) + ".java", "src/p1/p2/MyActivity1.java");
    myFixture.configureFromExistingVirtualFile(f);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testMinHeightCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "@android:", "@dimen/myDimen");
  }

  public void testOnClickNavigation() throws Throwable {
    copyOnClickClasses();
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);

    PsiReference reference = TargetElementUtil.findReference(myFixture.getEditor(), myFixture.getCaretOffset());
    assertInstanceOf(reference, PsiPolyVariantReference.class);
    ResolveResult[] results = ((PsiPolyVariantReference)reference).multiResolve(false);
    assertEquals(2, results.length);
    for (ResolveResult result : results) {
      assertInstanceOf(result.getElement(), PsiMethod.class);
    }
  }

  public void testRelativeIdsCompletion() throws Throwable {
    doTestCompletionVariants(getTestName(false) + ".xml", "@+id/", "@android:", "@id/btn1", "@id/btn2");
  }

  public void testCreateResourceFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<HighlightInfo> infos = myFixture.doHighlighting();
    List<IntentionAction> actions = new ArrayList<>();

    for (HighlightInfo info : infos) {
      List<Pair<HighlightInfo.IntentionActionDescriptor, TextRange>> ranges = info.quickFixActionRanges;

      if (ranges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : ranges) {
          IntentionAction action = pair.getFirst().getAction();
          if (action instanceof CreateValueResourceQuickFix) {
            actions.add(action);
          }
        }
      }
    }
    assertEquals(1, actions.size());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
    myFixture.checkResultByFile("res/values/drawables.xml", myTestFolder + '/' + getTestName(true) + "_drawable_after.xml", true);
  }

  public void testXsdFile1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("XsdFile.xsd", "res/raw/XsdFile.xsd");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  public void testXsdFile2() throws Throwable {
    VirtualFile virtualFile = copyFileToProject("XsdFile.xsd", "res/assets/XsdFile.xsd");
    myFixture.configureFromExistingVirtualFile(virtualFile);
    myFixture.checkHighlighting(true, false, false);
  }

  private void copyOnClickClasses() throws IOException {
    copyFileToProject("OnClick_Class1.java", "src/p1/p2/OnClick_Class1.java");
    copyFileToProject("OnClick_Class2.java", "src/p1/p2/OnClick_Class2.java");
    copyFileToProject("OnClick_Class3.java", "src/p1/p2/OnClick_Class3.java");
    copyFileToProject("OnClick_Class4.java", "src/p1/p2/OnClick_Class4.java");
  }

  public void testJavaCompletion1() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion2() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaCompletion3() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaIdCompletion() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaCompletion("p1.p2");
  }

  public void testJavaHighlighting1() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting2() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1");
  }

  public void testJavaHighlighting3() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting4() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1.p2");
  }

  public void testJavaHighlighting5() throws Throwable {
    copyFileToProject("main.xml", "res/layout/main.xml");
    doTestJavaHighlighting("p1");
  }

  public void testJavaCreateResourceFromUsage() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(false) + ".java", "src/p1/p2/" + getTestName(true) + ".java");
    doCreateFileResourceFromUsage(virtualFile);
    myFixture.checkResultByFile("res/layout/unknown.xml", myTestFolder + '/' + getTestName(true) + "_layout_after.xml", true);
  }

  public void testAndroidPrefixCompletion1() throws Throwable {
    doTestAndroidPrefixCompletion("android:");
  }

  public void testAndroidPrefixCompletion2() throws Throwable {
    doTestAndroidPrefixCompletion("android:");
  }

  public void testAndroidPrefixCompletion3() throws Throwable {
    doTestAndroidPrefixCompletion(null);
  }

  public void testAndroidPrefixCompletion4() throws Throwable {
    doTestAndroidPrefixCompletion("andr:");
  }

  public void testAndroidPrefixCompletion5() throws Throwable {
    doTestAndroidPrefixCompletion(null);
  }

  public void testCreateResourceFromUsage1() throws Throwable {
    VirtualFile virtualFile = copyFileToProject(getTestName(true) + ".xml");
    doCreateFileResourceFromUsage(virtualFile);
    myFixture.type("selector");
    myFixture.checkResultByFile("res/drawable/unknown.xml", myTestFolder + '/' + getTestName(true) + "_drawable_after.xml", true);
  }

  public void testPrivateAndPublicResources() throws Throwable {
    doTestHighlighting();
  }

  public void testPrivateAttributesCompletion() throws Throwable {
    doTestCompletion();
  }

  public void testPrivateAttributesHighlighting() throws Throwable {
    doTestHighlighting();
  }

  public void testResourceValidationErrors() throws Throwable {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml");
    doTestHighlighting();
  }

  public void testAttrReferences1() throws Throwable {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml");
    doTestHighlighting();
  }

  public void testAttrReferences2() throws Throwable {
    doTestAttrReferenceCompletionVariants("?");
  }

  public void testAttrReferences3() throws Throwable {
    doTestAttrReferenceCompletionVariants("attr");
  }

  private void doTestAttrReferenceCompletionVariants(String prefix) throws IOException {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml");
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    List<String> variants = myFixture.getLookupElementStrings();

    assertTrue(!variants.isEmpty());
    assertFalse(containElementStartingWith(variants, prefix));
  }

  public void testAttrReferences4() throws Throwable {
    doTestAttrReferenceCompletion("myA\n");
  }

  public void testAttrReferences5() throws Throwable {
    doTestAttrReferenceCompletion("textAppear\n");
  }

  public void testAttrReferences6() throws Throwable {
    doTestAttrReferenceCompletion("myA\n");
  }

  public void testAttrReferences7() throws Throwable {
    doTestAttrReferenceCompletion("android:textAppear\n");
  }

  public void testAttrReferences8() throws Throwable {
    doTestAttrReferenceCompletion("attr\n");
  }

  public void testAttrReferences9() throws Throwable {
    doTestAttrReferenceCompletion("android:attr\n");
  }

  public void testNamespaceCompletion() throws Throwable {
    doTestNamespaceCompletion(true, true, true, false);
  }

  public void testDimenUnitsCompletion1() throws Exception {
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);

    UsefulTestCase.assertSameElements(myFixture.getLookupElementStrings(), "3dp", "3px", "3sp", "3pt", "3mm", "3in");

    PsiElement originalElement = myFixture.getFile().findElementAt(
      myFixture.getEditor().getCaretModel().getOffset());

    LookupEx lookup = myFixture.getLookup();
    LookupElement dpElement = null;
    LookupElement pxElement = null;

    for (LookupElement element : lookup.getItems()) {
      if (element.getLookupString().endsWith("dp")) {
        dpElement = element;
      }
      else if (element.getLookupString().endsWith("px")) {
        pxElement = element;
      }
    }
    DocumentationProvider provider;
    PsiElement docTargetElement;

    lookup.setCurrentItem(dpElement);
    docTargetElement = DocumentationManager.getInstance(getProject()).
      findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    provider = DocumentationManager.getProviderFromElement(docTargetElement);
    assertEquals("<html><body><b>Density-independent Pixels</b> - an abstract unit that is based on the physical " +
                 "density of the screen.</body></html>", provider.generateDoc(docTargetElement, originalElement));

    lookup.setCurrentItem(pxElement);
    docTargetElement = DocumentationManager.getInstance(getProject()).
      findTargetElement(myFixture.getEditor(), myFixture.getFile(), originalElement);
    provider = DocumentationManager.getProviderFromElement(docTargetElement);
    assertEquals("<html><body><b>Pixels</b> - corresponds to actual pixels on the screen. Not recommended.</body></html>",
                 provider.generateDoc(docTargetElement, originalElement));
  }

  public void testDimenUnitsCompletion2() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "@android:", "@dimen/myDimen");
  }

  public void testDimenUnitsCompletion3() throws Throwable {
    doTestCompletionVariants(getTestName(true) + ".xml", "3pt", "3px");
  }

  public void testOnClickIntention() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    AndroidCreateOnClickHandlerAction action = new AndroidCreateOnClickHandlerAction();
    assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    WriteCommandAction.runWriteCommandAction(null, () -> action.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity_after.java", false);
  }

  public void testOnClickIntentionIncorrectName() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivityIncorrectName.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntentionIncorrectName.xml");
    myFixture.configureFromExistingVirtualFile(file);
    AndroidCreateOnClickHandlerAction action = new AndroidCreateOnClickHandlerAction();
    assertFalse(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
  }

  public void testOnClickQuickFix1() throws Throwable {
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    List<IntentionAction> fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEmpty(fixes);
  }

  public void testOnClickQuickFix2() throws Throwable {
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntention.xml");
    myFixture.configureFromExistingVirtualFile(file);
    List<IntentionAction> actions = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEquals(1, actions.size());
    WriteCommandAction.runWriteCommandAction(null, () -> actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile()));

    myFixture.checkResultByFile(myTestFolder + "/onClickIntention.xml");
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity1_after.java", false);
  }

  public void testOnClickQuickFix3() throws Throwable {
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity2_after.java", false);
  }

  public void testOnClickQuickFix4() throws Throwable {
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity1.java", "src/p1/p2/Activity1.java");
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivity4.java", "src/p1/p2/Activity2.java");
    VirtualFile file = copyFileToProject("onClickIntention.xml");
    doTestOnClickQuickfix(file);
    myFixture.checkResultByFile("src/p1/p2/Activity1.java", myTestFolder + "/OnClickActivity1_after.java", false);
  }

  public void testOnClickQuickFixIncorrectName() throws Throwable {
    enableInspection(AndroidMissingOnClickHandlerInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/OnClickActivityIncorrectName.java", "src/p1/p2/Activity1.java");
    VirtualFile file = copyFileToProject("onClickIntentionIncorrectName.xml");
    myFixture.configureFromExistingVirtualFile(file);
    List<IntentionAction> fixes = highlightAndFindQuickFixes(AndroidMissingOnClickHandlerInspection.MyQuickFix.class);
    assertEmpty(fixes);
  }

  public void testSpellchecker() throws Throwable {
    enableInspection(SpellCheckingInspection.class);
    myFixture.copyFileToProject(myTestFolder + "/spellchecker_resources.xml", "res/values/sr.xml");
    doTestHighlighting();
  }

  public void testSpellcheckerQuickfix() throws Throwable {
    myFixture.copyFileToProject(myTestFolder + "/spellchecker_resources.xml", "res/values/sr.xml");
    doTestSpellcheckerQuickFixes();
  }

  public void testAarDependency() throws Throwable {
    PsiTestUtil.addLibrary(myModule, "maven_aar_dependency", getTestDataPath() + "/" + myTestFolder + "/myaar", "classes.jar", "res");
    doTestCompletion();
  }

  public void testUnknownDataBindingAttribute() throws Throwable {
    // Regression test for issue http://b.android.com/195485
    // Don't highlight data binding attributes as unknown
    doTestHighlighting();
  }

  // Regression test for http://b/37128688
  public void testToolsCompletion() throws Throwable {
    // Don't offer tools: completion for the mockup editor yet.
    // Also tests that the current expected set of tools attributes are offered.
    doTestCompletionVariants("toolsCompletion.xml",
                             "tools:listfooter",
                             "tools:listheader",
                             "tools:listitem",
                             "tools:targetApi");
  }

  // Regression test for http://b/66240917
  public void testToolsCompletion2() throws Throwable {
    doTestPresentableCompletionVariants("toolsCompletion2.xml",
                                        "listfooter",
                                        "listheader",
                                        "listitem",
                                        "listSelector",
                                        "stateListAnimator");
  }

  public void testIncludeCompletion() throws Throwable {
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
                             "android:visibility");

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
                                     "android:layout_toStartOf");

    doTestCompletionVariantsContains("include_in_absolute_layout.xml",
                                     "android:layout_x",
                                     "android:layout_y");

    doTestCompletionVariantsContains("include_in_frame_layout.xml",
                                     "android:layout_gravity");

    // <include> tag should also support auto-completion of layout_XXX attributes with cusomized domain name.
    // For example, app:layout_constraintXXX attributes should be supported when it is in the ConstraintLayout.

    // TODO: Improve the test framework and test the cusomized domain case.
  }

  @Language("JAVA")
  String restrictText =
    "package android.support.annotation;\n" +
    "\n" +
    "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
    "import static java.lang.annotation.ElementType.CONSTRUCTOR;\n" +
    "import static java.lang.annotation.ElementType.FIELD;\n" +
    "import static java.lang.annotation.ElementType.METHOD;\n" +
    "import static java.lang.annotation.ElementType.PACKAGE;\n" +
    "import static java.lang.annotation.ElementType.TYPE;\n" +
    "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
    "\n" +
    "import java.lang.annotation.Retention;\n" +
    "import java.lang.annotation.Target;\n" +
    "\n" +
    "@Retention(CLASS)\n" +
    "@Target({ANNOTATION_TYPE,TYPE,METHOD,CONSTRUCTOR,FIELD,PACKAGE})\n" +
    "public @interface RestrictTo {\n" +
    "\n" +
    "    Scope[] value();\n" +
    "\n" +
    "    enum Scope {\n" +
    "        LIBRARY,\n" +
    "        LIBRARY_GROUP,\n" +
    "        @Deprecated\n" +
    "        GROUP_ID,\n" +
    "        TESTS,\n" +
    "        SUBCLASSES,\n" +
    "    }\n" +
    "}";

  @Language("JAVA")
  String protectedView =
    "package p1.p2;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.widget.ImageView;\n" +
    "\n" +
    "class MyAddedProtectedImageView extends ImageView {\n" +
    "    public MyAddedProtectedImageView(Context context) {\n" +
    "        super(context);\n" +
    "    }\n" +
    "}";

  @Language("JAVA")
  String restrictedView =
    "package p1.p2;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.support.annotation.RestrictTo;\n" +
    "import android.widget.ImageView;\n" +
    "\n" +
    "@RestrictTo(RestrictTo.Scope.SUBCLASSES)\n" +
    "public class MyAddedHiddenImageView extends ImageView {\n" +
    "    public MyAddedHiddenImageView(Context context) {\n" +
    "        super(context);\n" +
    "    }\n" +
    "}";

  @Language("JAVA")
  String view =
    "package p1.p2;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.widget.ImageView;\n" +
    "\n" +
    "public class MyAddedImageView extends ImageView {\n" +
    "    public MyAddedImageView(Context context) {\n" +
    "        super(context);\n" +
    "    }\n" +
    "}";

  public void testRestricted() throws Throwable {
    myFixture.addClass(restrictText);
    myFixture.addClass(protectedView);
    myFixture.addClass(restrictedView);
    myFixture.addClass(view);

    toTestCompletion("restricted.xml", "restricted_after.xml");
  }

  @Language("JAVA")
  String innerClass =
    "package p1.p2;\n" +
    "\n" +
    "import android.content.Context;\n" +
    "import android.widget.ImageView;\n" +
    "import android.widget.LinearLayout;\n" +
    "import android.widget.TextView;\n" +
    "\n" +
    "public class MyImageView extends ImageView {\n" +
    "    public MyImageView(Context context) {\n" +
    "        super(context);\n" +
    "    }\n" +
    "    public static class MyTextView extends TextView {\n" +
    "        public MyTextView(Context context) {\n" +
    "            super(context);\n" +
    "        }\n" +
    "    }\n" +
    "    public static class MyLinearLayout extends LinearLayout {\n" +
    "        public MyLinearLayout(Context context) {\n" +
    "            super(context);\n" +
    "        }\n" +
    "    }\n" +
    "}";

  public void testTagCompletionUsingInnerClass() throws Throwable {
    myFixture.addClass(innerClass);

    toTestCompletion("innerClass1.xml", "innerClass1_after.xml");
  }

  public void testTagReplacementUsingInnerClass() throws Throwable {
    myFixture.addClass(innerClass);

    myFixture.configureFromExistingVirtualFile(copyFileToProject("innerClass2.xml"));
    myFixture.complete(CompletionType.BASIC);
    myFixture.type("\t");
    myFixture.checkResultByFile(myTestFolder + "/innerClass2_after.xml");
  }

  public void testTagLayoutCompletionUsingInnerClass() throws Throwable {
    myFixture.addClass(innerClass);

    toTestCompletion("innerClass3.xml", "innerClass3_after.xml");
  }

  private void doTestAttrReferenceCompletion(String textToType) throws IOException {
    copyFileToProject("attrReferences_attrs.xml", "res/values/attrReferences_attrs.xml");
    VirtualFile file = copyFileToProject(getTestName(true) + ".xml");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type(textToType);
    myFixture.checkResultByFile(myTestFolder + '/' + getTestName(true) + "_after.xml");
  }

  private static boolean containElementStartingWith(List<String> elements, String prefix) {
    for (String element : elements) {
      if (element.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private void doCreateFileResourceFromUsage(VirtualFile virtualFile) {
    myFixture.configureFromExistingVirtualFile(virtualFile);
    List<IntentionAction> actions = highlightAndFindQuickFixes(CreateFileResourceQuickFix.class);
    assertEquals(1, actions.size());

    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        actions.get(0).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
    }.execute();
  }

  private void enableInspection(@NotNull Class<? extends LocalInspectionTool> inspectionClass) {
    myFixture.enableInspections(Collections.singleton(inspectionClass));
  }
}

