
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.android;

import static com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.flags.StudioFlags;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TreeTester;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewImpl;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

/**
 * FindUsages tests for Android resources.
 */
public abstract class AndroidFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";
  private static String MODULE_WITHOUT_DEPENDENCY = "MODULE_WITHOUT_DEPENDENCY";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "picture3.gif", "res/drawable/picture3.gif");
    myFixture.addFileToProject(getAdditionalModulePath(MODULE_WITHOUT_DEPENDENCY) + "/res/values/colors.xml",
      "<resources>\n" +
      "        <string name=\"hello\">Hello</string>\n" +
      "    </resources>"
    );
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, MODULE_WITHOUT_DEPENDENCY, PROJECT_TYPE_LIBRARY, false);
  }

  /**
   * Test for old FindUsages pipeline, where [StudioFlags.RESOLVE_USING_REPOS] is set to false.
   */
  public static class OldAndroidFindUsagesTest extends AndroidFindUsagesTest {
    @Override
    public void setUp() throws Exception {
      super.setUp();
      StudioFlags.RESOLVE_USING_REPOS.override(false);
    }

    @Override
    public void tearDown() throws Exception {
      try {
        StudioFlags.RESOLVE_USING_REPOS.clearOverride();
      }
      finally {
        super.tearDown();
      }
    }

    public void testFontResource() {
      super.testFontResource("Usage (1 usage)\n" +
                             " Found usages (1 usage)\n" +
                             "  Resource reference in code (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    p1.p2 (1 usage)\n" +
                             "     Example (1 usage)\n" +
                             "      f() (1 usage)\n" +
                             "       4int id1 = R.font.new_font;\n");
    }

    public void testFileResource() {
      super.testFileResource("Usage (3 usages)\n" +
                             " Found usages (3 usages)\n" +
                             "  Resource reference in code (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    p1.p2 (1 usage)\n" +
                             "     Class1 (1 usage)\n" +
                             "      f() (1 usage)\n" +
                             "       5int id1 = R.drawable.picture3;\n" +
                             "  Usage in Android resources XML (2 usages)\n" +
                             "   app (2 usages)\n" +
                             "    res/layout (1 usage)\n" +
                             "     fu1_layout.xml (1 usage)\n" +
                             "      3<TextView android:background=\"@drawable/picture3\"/>\n" +
                             "    res/values (1 usage)\n" +
                             "     styles.xml (1 usage)\n" +
                             "      3<item name=\"android:windowBackground\">@drawable/picture3</item>\n");
    }

    public void testFileResourceNoEditor() {
      super.testFileResourceNoEditor("Usage (1 usage)\n" +
                                     " Found usages (1 usage)\n" +
                                     "  Resource reference in code (1 usage)\n" +
                                     "   app (1 usage)\n" +
                                     "    p1.p2 (1 usage)\n" +
                                     "     Foo (1 usage)\n" +
                                     "      f() (1 usage)\n" +
                                     "       5int id1 = R.layout.layout;\n");
    }

    public void testFileResourceField() {
      super.testFileResourceField("Usage (2 usages)\n" +
                                  " Found usages (2 usages)\n" +
                                  "  Resource reference in code (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    p1.p2 (1 usage)\n" +
                                  "     Fu3 (1 usage)\n" +
                                  "      f() (1 usage)\n" +
                                  "       5int id1 = R.drawable.picture3;\n" +
                                  "  Usage in Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/layout (1 usage)\n" +
                                  "     layout.xml (1 usage)\n" +
                                  "      3android:background=\"@drawable/picture3\">\n");
    }

    public void testIdDeclarations() {
      super.testIdDeclarations("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Usage in Android resources XML (2 usages)\n" +
                               "   app (2 usages)\n" +
                               "    res/layout (2 usages)\n" +
                               "     f12_layout.xml (2 usages)\n" +
                               "      26android:layout_below=\"@+id/second\"\n" +
                               "      27android:labelFor=\"@id/second\"\n");
    }

    public void testIdResource() {
      super.testIdResource("Usage (2 usages)\n" +
                           " Found usages (2 usages)\n" +
                           "  Resource reference in code (1 usage)\n" +
                           "   app (1 usage)\n" +
                           "    p1.p2 (1 usage)\n" +
                           "     Class1 (1 usage)\n" +
                           "      f() (1 usage)\n" +
                           "       8int id3 = R.id.anchor;\n" +
                           "  Usage in Android resources XML (1 usage)\n" +
                           "   app (1 usage)\n" +
                           "    res/layout (1 usage)\n" +
                           "     fu7_layout.xml (1 usage)\n" +
                           "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n");
    }

    public void testIdResourceDeclaration() {
      super.testIdResourceDeclaration("Usage (2 usages)\n" +
                                      " Found usages (2 usages)\n" +
                                      "  Resource reference in code (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    p1.p2 (1 usage)\n" +
                                      "     Class1 (1 usage)\n" +
                                      "      f() (1 usage)\n" +
                                      "       8int id3 = R.id.anchor;\n" +
                                      "  Usage in Android resources XML (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    res/layout (1 usage)\n" +
                                      "     fu9_layout.xml (1 usage)\n" +
                                      "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n");
    }

    public void testIdResourceField() {
      super.testIdResourceField("Usage (2 usages)\n" +
                                " Found usages (2 usages)\n" +
                                "  Resource reference in code (1 usage)\n" +
                                "   app (1 usage)\n" +
                                "    p1.p2 (1 usage)\n" +
                                "     Fu6 (1 usage)\n" +
                                "      f() (1 usage)\n" +
                                "       5int id1 = R.id.anchor;\n" +
                                "  Usage in Android resources XML (1 usage)\n" +
                                "   app (1 usage)\n" +
                                "    res/layout (1 usage)\n" +
                                "     layout.xml (1 usage)\n" +
                                "      7<TextView android:layout_alignRight=\"@id/anchor\"/>\n");
    }

    public void testStringArray() {
      super.testStringArray("Usage (2 usages)\n" +
                            " Found usages (2 usages)\n" +
                            "  Resource reference in code (1 usage)\n" +
                            "   app (1 usage)\n" +
                            "    p1.p2 (1 usage)\n" +
                            "     Class1 (1 usage)\n" +
                            "      f() (1 usage)\n" +
                            "       9int id4 = R.array.str_arr;\n" +
                            "  Usage in Android resources XML (1 usage)\n" +
                            "   app (1 usage)\n" +
                            "    res/layout (1 usage)\n" +
                            "     stringArray.xml (1 usage)\n" +
                            "      3<ListView android:entries=\"@array/str_arr\"/>\n");
    }

    public void testStyleable() throws Throwable {
      super.testStyleable("Usage (2 usages)\n" +
                          " Found usages (2 usages)\n" +
                          "  Resource reference in code (2 usages)\n" +
                          "   app (2 usages)\n" +
                          "    p1.p2 (2 usages)\n" +
                          "     MyView (2 usages)\n" +
                          "      MyView(Context, AttributeSet, int) (2 usages)\n" +
                          "       13TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyView);\n" +
                          "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n");
    }

    public void testStyleableAttr() throws Throwable {
      super.testStyleableAttr("Usage (3 usages)\n" +
                              " Found usages (3 usages)\n" +
                              "  Resource reference in code (2 usages)\n" +
                              "   app (2 usages)\n" +
                              "    p1.p2 (2 usages)\n" +
                              "     MyView (2 usages)\n" +
                              "      MyView(Context, AttributeSet, int) (2 usages)\n" +
                              "       12int attribute = R.attr.answer;\n" +
                              "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n" +
                              "  Usage in Android resources XML (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    res/values (1 usage)\n" +
                              "     attrs.xml (1 usage)\n" +
                              "      4<attr name=\"answer\">\n");
    }

    public void testStyleInheritance() {
      super.testStyleInheritance("Usage (3 usages)\n" +
                                 " Found usages (3 usages)\n" +
                                 "  Usage in Android resources XML (3 usages)\n" +
                                 "   app (3 usages)\n" +
                                 "    res/values (3 usages)\n" +
                                 "     f10_values.xml (3 usages)\n" +
                                 "      6<style name=\"myStyle.s\">\n" +
                                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                 "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testStyleInheritance1() {
      super.testStyleInheritance1("Usage (3 usages)\n" +
                                  " Found usages (3 usages)\n" +
                                  "  Usage in Android resources XML (3 usages)\n" +
                                  "   app (3 usages)\n" +
                                  "    res/values (3 usages)\n" +
                                  "     f11_values.xml (3 usages)\n" +
                                  "      6<style name=\"myStyle.s\">\n" +
                                  "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                  "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testStyleInheritance2() {
      super.testStyleInheritance2("Usage (3 usages)\n" +
                                  " Found usages (3 usages)\n" +
                                  "  Usage in Android resources XML (3 usages)\n" +
                                  "   app (3 usages)\n" +
                                  "    res/values (3 usages)\n" +
                                  "     f14_values.xml (3 usages)\n" +
                                  "      6<style name=\"myStyle.s\">\n" +
                                  "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                  "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testValueItemResource() {
      super.testValueItemResource("Usage (2 usages)\n" +
                                  " Found usages (2 usages)\n" +
                                  "  Resource reference in code (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    p1.p2 (1 usage)\n" +
                                  "     Class1 (1 usage)\n" +
                                  "      f() (1 usage)\n" +
                                  "       7int id3 = R.string.hi;\n" +
                                  "  Usage in Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/layout (1 usage)\n" +
                                  "     fu5_layout.xml (1 usage)\n" +
                                  "      3<TextView android:text=\"@string/hi\"/>\n");
    }

    public void testValueResource() {
      super.testValueResource("Usage (2 usages)\n" +
                              " Found usages (2 usages)\n" +
                              "  Resource reference in code (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    p1.p2 (1 usage)\n" +
                              "     Class1 (1 usage)\n" +
                              "      f() (1 usage)\n" +
                              "       6int id2 = R.string.hello;\n" +
                              "  Usage in Android resources XML (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    res/layout (1 usage)\n" +
                              "     fu2_layout.xml (1 usage)\n" +
                              "      3<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource1() {
      super.testValueResource1("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource2() {
      super.testValueResource2("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource3() {
      super.testValueResource3("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource4() {
      super.testValueResource4("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource5() {
      super.testValueResource5("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource6() {
      super.testValueResource6("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource7() {
      super.testValueResource7("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource8() {
      super.testValueResource8("Usage (2 usages)\n" +
                               " Found usages (2 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueResource9() {
      super.testValueResource9("Usage (3 usages)\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n" +
                               "  Usage in Android resources XML (2 usages)\n" +
                               "   app (2 usages)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "    res/values (1 usage)\n" +
                               "     f13_values.xml (1 usage)\n" +
                               "      9<item>@string/hello</item>\n");
    }

    public void testValueResourceField() {
      super.testValueResourceField("Usage (2 usages)\n" +
                                   " Found usages (2 usages)\n" +
                                   "  Resource reference in code (1 usage)\n" +
                                   "   app (1 usage)\n" +
                                   "    p1.p2 (1 usage)\n" +
                                   "     Fu4 (1 usage)\n" +
                                   "      f() (1 usage)\n" +
                                   "       5int id1 = R.string.hello;\n" +
                                   "  Usage in Android resources XML (1 usage)\n" +
                                   "   app (1 usage)\n" +
                                   "    res/layout (1 usage)\n" +
                                   "     layout.xml (1 usage)\n" +
                                   "      5<TextView android:text=\"@string/hello\"/>\n");
    }

    public void testValueItemResourceField() {
      super.testValueItemResourceField("Usage (2 usages)\n" +
                                       " Found usages (2 usages)\n" +
                                       "  Resource reference in code (1 usage)\n" +
                                       "   app (1 usage)\n" +
                                       "    p1.p2 (1 usage)\n" +
                                       "     Fu6 (1 usage)\n" +
                                       "      f() (1 usage)\n" +
                                       "       5int id1 = R.string.hi;\n" +
                                       "  Usage in Android resources XML (1 usage)\n" +
                                       "   app (1 usage)\n" +
                                       "    res/layout (1 usage)\n" +
                                       "     layout.xml (1 usage)\n" +
                                       "      6<TextView android:text=\"@string/hi\"/>\n");
    }

    public void testStyleItemAttrFromJava() throws Throwable {
      super.testStyleItemAttrFromJava("Usage (3 usages)\n" +
                                      " Found usages (3 usages)\n" +
                                      "  Resource reference in code (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    p1.p2 (1 usage)\n" +
                                      "     MyView (1 usage)\n" +
                                      "      MyView(Context, AttributeSet, int) (1 usage)\n" +
                                      "       12int attribute = R.attr.newAttr;\n" +
                                      "  Usage in Android resources XML (2 usages)\n" +
                                      "   app (2 usages)\n" +
                                      "    res/values (2 usages)\n" +
                                      "     attrs.xml (1 usage)\n" +
                                      "      3<attr name=\"newAttr\" format=\"boolean\" />\n" +
                                      "     style.xml (1 usage)\n" +
                                      "      3<item name=\"newAttr\">true</item>\n");
    }
  }

  /**
   * Test for new FindUsages pipeline, where [StudioFlags.RESOLVE_USING_REPOS] is set to true.
   */
  public static class NewAndroidFindUsagesTest extends AndroidFindUsagesTest {
    @Override
    public void setUp() throws Exception {
      super.setUp();
      StudioFlags.RESOLVE_USING_REPOS.override(true);
    }

    @Override
    public void tearDown() throws Exception {
      try {
        StudioFlags.RESOLVE_USING_REPOS.clearOverride();
      }
      finally {
        super.tearDown();
      }
    }

    public void testDoNotFindResourceOutOfScope() {
      myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
      myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
      VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "fu2_layout.xml", "res/layout/fu2_layout.xml");
      Collection<UsageInfo> references = findUsages(file, myFixture);
      assertEquals("Usage (3 usages)\n" +
                   " Targets\n" +
                   "  @string/hello\n" +
                   " Found usages (3 usages)\n" +
                   "  Resource declaration in Android resources XML (1 usage)\n" +
                   "   app (1 usage)\n" +
                   "    res/values (1 usage)\n" +
                   "     strings.xml (1 usage)\n" +
                   "      2<string name=\"hello\">hello</string>\n" +
                   "  Resource reference Android resources XML (1 usage)\n" +
                   "   app (1 usage)\n" +
                   "    res/layout (1 usage)\n" +
                   "     fu2_layout.xml (1 usage)\n" +
                   "      3<TextView android:text=\"@string/hello\"/>\n" +
                   "  Resource reference in code (1 usage)\n" +
                   "   app (1 usage)\n" +
                   "    p1.p2 (1 usage)\n" +
                   "     Class1 (1 usage)\n" +
                   "      f() (1 usage)\n" +
                   "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
    }

    public void testFontResource() {
      super.testFontResource("Usage (2 usages)\n" +
                             " Targets\n" +
                             "  @font/new_font\n" +
                             " Found usages (2 usages)\n" +
                             "  Android resource file (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    res/font (1 usage)\n" +
                             "     new_font.ttf (1 usage)\n" +
                             "      Android resource file font/new_font.ttf\n" +
                             "  Resource reference in code (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    p1.p2 (1 usage)\n" +
                             "     Example (1 usage)\n" +
                             "      f() (1 usage)\n" +
                             "       4int id1 = R.font.new_font;\n");
    }

    public void testFileResource() {
      super.testFileResource("Usage (4 usages)\n" +
                             " Targets\n" +
                             "  @drawable/picture3\n" +
                             " Found usages (4 usages)\n" +
                             "  Android resource file (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    res/drawable (1 usage)\n" +
                             "     picture3.gif (1 usage)\n" +
                             "      Android resource file drawable/picture3.gif\n" +
                             "  Resource reference Android resources XML (2 usages)\n" +
                             "   app (2 usages)\n" +
                             "    res/layout (1 usage)\n" +
                             "     fu1_layout.xml (1 usage)\n" +
                             "      3<TextView android:background=\"@drawable/picture3\"/>\n" +
                             "    res/values (1 usage)\n" +
                             "     styles.xml (1 usage)\n" +
                             "      3<item name=\"android:windowBackground\">@drawable/picture3</item>\n" +
                             "  Resource reference in code (1 usage)\n" +
                             "   app (1 usage)\n" +
                             "    p1.p2 (1 usage)\n" +
                             "     Class1 (1 usage)\n" +
                             "      f() (1 usage)\n" +
                             "       5int id1 = R.drawable.picture3;\n");
    }

    public void testFileResourceNoEditor() {
      super.testFileResourceNoEditor("Usage (2 usages)\n" +
                                     " Found usages (2 usages)\n" +
                                     "  Resource reference Android resources XML (1 usage)\n" +
                                     "   app (1 usage)\n" +
                                     "    res/layout (1 usage)\n" +
                                     "     layout.xml (1 usage)\n" +
                                     "      1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                     "  Resource reference in code (1 usage)\n" +
                                     "   app (1 usage)\n" +
                                     "    p1.p2 (1 usage)\n" +
                                     "     Foo (1 usage)\n" +
                                     "      f() (1 usage)\n" +
                                     "       5int id1 = R.layout.layout;\n");
    }

    public void testFileResourceField() {
      super.testFileResourceField("Usage (3 usages)\n" +
                                  " Targets\n" +
                                  "  @drawable/picture3\n" +
                                  " Found usages (3 usages)\n" +
                                  "  Android resource file (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/drawable (1 usage)\n" +
                                  "     picture3.gif (1 usage)\n" +
                                  "      Android resource file drawable/picture3.gif\n" +
                                  "  Resource reference Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/layout (1 usage)\n" +
                                  "     layout.xml (1 usage)\n" +
                                  "      3android:background=\"@drawable/picture3\">\n" +
                                  "  Resource reference in code (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    p1.p2 (1 usage)\n" +
                                  "     Fu3 (1 usage)\n" +
                                  "      f() (1 usage)\n" +
                                  "       5int id1 = R.drawable.picture3;\n");
    }

    public void testIdDeclarations() {
      super.testIdDeclarations("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @id/second\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (2 usages)\n" +
                               "   app (2 usages)\n" +
                               "    res/layout (2 usages)\n" +
                               "     f12_layout.xml (2 usages)\n" +
                               "      16android:id=\"@+id/second\"\n" +
                               "      26android:layout_below=\"@+id/second\"\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     f12_layout.xml (1 usage)\n" +
                               "      27android:labelFor=\"@id/second\"\n");
    }

    public void testIdResource() {
      super.testIdResource("Usage (3 usages)\n" +
                           " Targets\n" +
                           "  @id/anchor\n" +
                           " Found usages (3 usages)\n" +
                           "  Resource declaration in Android resources XML (1 usage)\n" +
                           "   app (1 usage)\n" +
                           "    res/layout (1 usage)\n" +
                           "     fu7_layout.xml (1 usage)\n" +
                           "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                           "  Resource reference Android resources XML (1 usage)\n" +
                           "   app (1 usage)\n" +
                           "    res/layout (1 usage)\n" +
                           "     fu7_layout.xml (1 usage)\n" +
                           "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                           "  Resource reference in code (1 usage)\n" +
                           "   app (1 usage)\n" +
                           "    p1.p2 (1 usage)\n" +
                           "     Class1 (1 usage)\n" +
                           "      f() (1 usage)\n" +
                           "       8int id3 = R.id.anchor;\n");
    }

    public void testIdResourceDeclaration() {
      super.testIdResourceDeclaration("Usage (3 usages)\n" +
                                      " Targets\n" +
                                      "  @id/anchor\n" +
                                      " Found usages (3 usages)\n" +
                                      "  Resource declaration in Android resources XML (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    res/layout (1 usage)\n" +
                                      "     fu9_layout.xml (1 usage)\n" +
                                      "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                                      "  Resource reference Android resources XML (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    res/layout (1 usage)\n" +
                                      "     fu9_layout.xml (1 usage)\n" +
                                      "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                                      "  Resource reference in code (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    p1.p2 (1 usage)\n" +
                                      "     Class1 (1 usage)\n" +
                                      "      f() (1 usage)\n" +
                                      "       8int id3 = R.id.anchor;\n");
    }

    public void testIdResourceField() {
      super.testIdResourceField("Usage (3 usages)\n" +
                                " Targets\n" +
                                "  @id/anchor\n" +
                                " Found usages (3 usages)\n" +
                                "  Resource declaration in Android resources XML (1 usage)\n" +
                                "   app (1 usage)\n" +
                                "    res/layout (1 usage)\n" +
                                "     layout.xml (1 usage)\n" +
                                "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                                "  Resource reference Android resources XML (1 usage)\n" +
                                "   app (1 usage)\n" +
                                "    res/layout (1 usage)\n" +
                                "     layout.xml (1 usage)\n" +
                                "      7<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                                "  Resource reference in code (1 usage)\n" +
                                "   app (1 usage)\n" +
                                "    p1.p2 (1 usage)\n" +
                                "     Fu6 (1 usage)\n" +
                                "      f() (1 usage)\n" +
                                "       5int id1 = R.id.anchor;\n");
    }

    public void testStringArray() {
      super.testStringArray("Usage (3 usages)\n" +
                            " Targets\n" +
                            "  @array/str_arr\n" +
                            " Found usages (3 usages)\n" +
                            "  Resource declaration in Android resources XML (1 usage)\n" +
                            "   app (1 usage)\n" +
                            "    res/values (1 usage)\n" +
                            "     strings.xml (1 usage)\n" +
                            "      4<string-array name=\"str_arr\"></string-array>\n" +
                            "  Resource reference Android resources XML (1 usage)\n" +
                            "   app (1 usage)\n" +
                            "    res/layout (1 usage)\n" +
                            "     stringArray.xml (1 usage)\n" +
                            "      3<ListView android:entries=\"@array/str_arr\"/>\n" +
                            "  Resource reference in code (1 usage)\n" +
                            "   app (1 usage)\n" +
                            "    p1.p2 (1 usage)\n" +
                            "     Class1 (1 usage)\n" +
                            "      f() (1 usage)\n" +
                            "       9int id4 = R.array.str_arr;\n");
    }

    public void testStyleable() throws Throwable {
      super.testStyleable("Usage (3 usages)\n" +
                          " Targets\n" +
                          "  @styleable/MyView\n" +
                          " Found usages (3 usages)\n" +
                          "  Resource declaration in Android resources XML (1 usage)\n" +
                          "   app (1 usage)\n" +
                          "    res/values (1 usage)\n" +
                          "     attrs.xml (1 usage)\n" +
                          "      3<declare-styleable name=\"MyView\">\n" +
                          "  Resource reference in code (2 usages)\n" +
                          "   app (2 usages)\n" +
                          "    p1.p2 (2 usages)\n" +
                          "     MyView (2 usages)\n" +
                          "      MyView(Context, AttributeSet, int) (2 usages)\n" +
                          "       13TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyView);\n" +
                          "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n");
    }

    // Styleable attr fields are not yet found in the new Find Usages pipeline
    public void testStyleableAttr_TODO() throws Throwable {
      super.testStyleableAttr("Usage (1 usage)\n" +
                              " Targets\n" +
                              "  @styleable/MyView_answer\n" +
                              " Found usages (1 usage)\n" +
                              "  Resource reference in code (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    p1.p2 (1 usage)\n" +
                              "     MyView (1 usage)\n" +
                              "      MyView(Context, AttributeSet, int) (1 usage)\n" +
                              "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n");
    }

    public void testStyleInheritance() {
      super.testStyleInheritance("Usage (4 usages)\n" +
                                 " Targets\n" +
                                 "  @style/myStyle\n" +
                                 " Found usages (4 usages)\n" +
                                 "  Resource declaration in Android resources XML (1 usage)\n" +
                                 "   app (1 usage)\n" +
                                 "    res/values (1 usage)\n" +
                                 "     f10_values.xml (1 usage)\n" +
                                 "      2<style name=\"myStyle\">\n" +
                                 "  Resource reference Android resources XML (3 usages)\n" +
                                 "   app (3 usages)\n" +
                                 "    res/values (3 usages)\n" +
                                 "     f10_values.xml (3 usages)\n" +
                                 "      6<style name=\"myStyle.s\">\n" +
                                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                 "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testStyleInheritance1() {
      super.testStyleInheritance1("Usage (4 usages)\n" +
                                  " Targets\n" +
                                  "  @style/myStyle\n" +
                                  " Found usages (4 usages)\n" +
                                  "  Resource declaration in Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/values (1 usage)\n" +
                                  "     f11_values.xml (1 usage)\n" +
                                  "      2<style name=\"myStyle\">\n" +
                                  "  Resource reference Android resources XML (3 usages)\n" +
                                  "   app (3 usages)\n" +
                                  "    res/values (3 usages)\n" +
                                  "     f11_values.xml (3 usages)\n" +
                                  "      6<style name=\"myStyle.s\">\n" +
                                  "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                  "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testStyleInheritance2() {
      super.testStyleInheritance2("Usage (4 usages)\n" +
                                  " Targets\n" +
                                  "  @style/myStyle\n" +
                                  " Found usages (4 usages)\n" +
                                  "  Resource declaration in Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/values (1 usage)\n" +
                                  "     f14_values.xml (1 usage)\n" +
                                  "      2<style name=\"myStyle\">\n" +
                                  "  Resource reference Android resources XML (3 usages)\n" +
                                  "   app (3 usages)\n" +
                                  "    res/values (3 usages)\n" +
                                  "     f14_values.xml (3 usages)\n" +
                                  "      6<style name=\"myStyle.s\">\n" +
                                  "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                                  "      14<style name=\"myStyle.s.a\">\n");
    }

    public void testValueItemResource() {
      super.testValueItemResource("Usage (3 usages)\n" +
                                  " Targets\n" +
                                  "  @string/hi\n" +
                                  " Found usages (3 usages)\n" +
                                  "  Resource declaration in Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/values (1 usage)\n" +
                                  "     strings.xml (1 usage)\n" +
                                  "      3<item name=\"hi\" type=\"string\"/>\n" +
                                  "  Resource reference Android resources XML (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    res/layout (1 usage)\n" +
                                  "     fu5_layout.xml (1 usage)\n" +
                                  "      3<TextView android:text=\"@string/hi\"/>\n" +
                                  "  Resource reference in code (1 usage)\n" +
                                  "   app (1 usage)\n" +
                                  "    p1.p2 (1 usage)\n" +
                                  "     Class1 (1 usage)\n" +
                                  "      f() (1 usage)\n" +
                                  "       7int id3 = R.string.hi;\n");
    }

    public void testValueItemResourceField() {
      super.testValueItemResourceField("Usage (3 usages)\n" +
                                       " Targets\n" +
                                       "  @string/hi\n" +
                                       " Found usages (3 usages)\n" +
                                       "  Resource declaration in Android resources XML (1 usage)\n" +
                                       "   app (1 usage)\n" +
                                       "    res/values (1 usage)\n" +
                                       "     strings.xml (1 usage)\n" +
                                       "      3<item name=\"hi\" type=\"string\"/>\n" +
                                       "  Resource reference Android resources XML (1 usage)\n" +
                                       "   app (1 usage)\n" +
                                       "    res/layout (1 usage)\n" +
                                       "     layout.xml (1 usage)\n" +
                                       "      6<TextView android:text=\"@string/hi\"/>\n" +
                                       "  Resource reference in code (1 usage)\n" +
                                       "   app (1 usage)\n" +
                                       "    p1.p2 (1 usage)\n" +
                                       "     Fu6 (1 usage)\n" +
                                       "      f() (1 usage)\n" +
                                       "       5int id1 = R.string.hi;\n");
    }

    public void testValueResource() {
      super.testValueResource("Usage (3 usages)\n" +
                              " Targets\n" +
                              "  @string/hello\n" +
                              " Found usages (3 usages)\n" +
                              "  Resource declaration in Android resources XML (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    res/values (1 usage)\n" +
                              "     strings.xml (1 usage)\n" +
                              "      2<string name=\"hello\">hello</string>\n" +
                              "  Resource reference Android resources XML (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    res/layout (1 usage)\n" +
                              "     fu2_layout.xml (1 usage)\n" +
                              "      3<TextView android:text=\"@string/hello\"/>\n" +
                              "  Resource reference in code (1 usage)\n" +
                              "   app (1 usage)\n" +
                              "    p1.p2 (1 usage)\n" +
                              "     Class1 (1 usage)\n" +
                              "      f() (1 usage)\n" +
                              "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource1() {
      super.testValueResource1("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu1_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource2() {
      super.testValueResource2("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu2_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource3() {
      super.testValueResource3("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu3_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource4() {
      super.testValueResource4("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu4_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource5() {
      super.testValueResource5("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu5_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource6() {
      super.testValueResource6("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu6_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource7() {
      super.testValueResource7("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     fu7_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource8() {
      super.testValueResource8("Usage (3 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (3 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     f8_values.xml (1 usage)\n" +
                               "      2<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResource9() {
      super.testValueResource9("Usage (4 usages)\n" +
                               " Targets\n" +
                               "  @string/hello\n" +
                               " Found usages (4 usages)\n" +
                               "  Resource declaration in Android resources XML (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    res/values (1 usage)\n" +
                               "     f13_values.xml (1 usage)\n" +
                               "      4<string name=\"hello\">hello</string>\n" +
                               "  Resource reference Android resources XML (2 usages)\n" +
                               "   app (2 usages)\n" +
                               "    res/layout (1 usage)\n" +
                               "     layout.xml (1 usage)\n" +
                               "      5<TextView android:text=\"@string/hello\"/>\n" +
                               "    res/values (1 usage)\n" +
                               "     f13_values.xml (1 usage)\n" +
                               "      9<item>@string/hello</item>\n" +
                               "  Resource reference in code (1 usage)\n" +
                               "   app (1 usage)\n" +
                               "    p1.p2 (1 usage)\n" +
                               "     Class1 (1 usage)\n" +
                               "      f() (1 usage)\n" +
                               "       6int id2 = R.string.hello;\n");
    }

    public void testValueResourceField() {
      super.testValueResourceField("Usage (3 usages)\n" +
                                   " Targets\n" +
                                   "  @string/hello\n" +
                                   " Found usages (3 usages)\n" +
                                   "  Resource declaration in Android resources XML (1 usage)\n" +
                                   "   app (1 usage)\n" +
                                   "    res/values (1 usage)\n" +
                                   "     strings.xml (1 usage)\n" +
                                   "      2<string name=\"hello\">hello</string>\n" +
                                   "  Resource reference Android resources XML (1 usage)\n" +
                                   "   app (1 usage)\n" +
                                   "    res/layout (1 usage)\n" +
                                   "     layout.xml (1 usage)\n" +
                                   "      5<TextView android:text=\"@string/hello\"/>\n" +
                                   "  Resource reference in code (1 usage)\n" +
                                   "   app (1 usage)\n" +
                                   "    p1.p2 (1 usage)\n" +
                                   "     Fu4 (1 usage)\n" +
                                   "      f() (1 usage)\n" +
                                   "       5int id1 = R.string.hello;\n");
    }

    public void testStyleItemAttr() throws Throwable {
      createManifest();
      PsiFile file = myFixture.addFileToProject(
        "res/values/style.xml",
        //language=XML
        "<resources>\n" +
        "    <style name=\"Example\">\n" +
        "        <item name=\"newAtt<caret>r\">true</item>\n" +
        "    </style>\n" +
        "</resources>");
      myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
      myFixture.addFileToProject(
        "res/values/attrs.xml",
        //language=XML
        "<resources>\n" +
        "  <declare-styleable name=\"LabelView\">\n" +
        "    <attr name=\"newAttr\" format=\"boolean\" />\n" +
        "  </declare-styleable>\n" +
        "</resources>");
      myFixture.addFileToProject(
        "src/p1/p2/MyView.java",
        //language=JAVA
        "package p1.p2;\n" +
        "\n" +
        "import android.content.Context;\n" +
        "import android.content.res.TypedArray;\n" +
        "import android.util.AttributeSet;\n" +
        "import android.widget.Button;\n" +
        "\n" +
        "@SuppressWarnings(\"UnusedDeclaration\")\n" +
        "public class MyView extends Button {\n" +
        "    public MyView(Context context, AttributeSet attrs, int defStyle) {\n" +
        "        super(context, attrs, defStyle);\n" +
        "        int attribute = R.attr.newAttr;\n" +
        "    }\n" +
        "}\n");
      Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture);
      String expected = "Usage (3 usages)\n" +
                        " Targets\n" +
                        "  @attr/newAttr\n" +
                        " Found usages (3 usages)\n" +
                        "  Resource declaration in Android resources XML (1 usage)\n" +
                        "   app (1 usage)\n" +
                        "    res/values (1 usage)\n" +
                        "     attrs.xml (1 usage)\n" +
                        "      3<attr name=\"newAttr\" format=\"boolean\" />\n" +
                        "  Resource reference Android resources XML (1 usage)\n" +
                        "   app (1 usage)\n" +
                        "    res/values (1 usage)\n" +
                        "     style.xml (1 usage)\n" +
                        "      3<item name=\"newAttr\">true</item>\n" +
                        "  Resource reference in code (1 usage)\n" +
                        "   app (1 usage)\n" +
                        "    p1.p2 (1 usage)\n" +
                        "     MyView (1 usage)\n" +
                        "      MyView(Context, AttributeSet, int) (1 usage)\n" +
                        "       12int attribute = R.attr.newAttr;\n";
      assertThat(getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
    }

    public void testStyleItemAttrFromJava() throws Throwable {
      super.testStyleItemAttrFromJava("Usage (3 usages)\n" +
                                      " Targets\n" +
                                      "  @attr/newAttr\n" +
                                      " Found usages (3 usages)\n" +
                                      "  Resource declaration in Android resources XML (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    res/values (1 usage)\n" +
                                      "     attrs.xml (1 usage)\n" +
                                      "      3<attr name=\"newAttr\" format=\"boolean\" />\n" +
                                      "  Resource reference Android resources XML (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    res/values (1 usage)\n" +
                                      "     style.xml (1 usage)\n" +
                                      "      3<item name=\"newAttr\">true</item>\n" +
                                      "  Resource reference in code (1 usage)\n" +
                                      "   app (1 usage)\n" +
                                      "    p1.p2 (1 usage)\n" +
                                      "     MyView (1 usage)\n" +
                                      "      MyView(Context, AttributeSet, int) (1 usage)\n" +
                                      "       12int attribute = R.attr.newAttr;\n");
    }
  }

  public void testFontResource(String expectedTreeRepresentation) {
    myFixture.copyFileToProject("fonts/Roboto-Black.ttf", "res/font/new_font.ttf");
    PsiFile file = myFixture.addFileToProject(
      "src/p1/p2/Example.java",
      //language=JAVA
      "package p1.p2;\n" +
      "public class Example {\n" +
      "  public void f() {\n" +
      "    int id1 = R.font.new_<caret>font;\n" +
      "  }\n" +
      "}");
    Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture);
    assertThat(getUsageViewTreeTextRepresentation(references)).isEqualTo(expectedTreeRepresentation);
  }


  public void testFileResource(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    Collection<UsageInfo> references = findUsages("fu1_layout.xml", myFixture, "res/layout/fu1_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResourceNoEditor(String expectedTreeRepresentation) {
    myFixture.addFileToProject("src/p1/p2/Foo.java",
                               "package p1.p2;\n" +
                               "\n" +
                               "public class Foo {\n" +
                               "    public void f() {\n" +
                               "        int id1 = R.layout.layout;\n" +
                               "    }\n" +
                               "}");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsagesNoEditor("res/layout/layout.xml", myFixture);
    // Fixture Usage View tree is sufficient for file resources with no editor.
    assertEquals(expectedTreeRepresentation, myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu2_layout.xml", myFixture, "res/layout/fu2_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource1(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu1_values.xml", myFixture, "res/values/fu1_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource2(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu2_values.xml", myFixture, "res/values/fu2_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource3(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu3_values.xml", myFixture, "res/values/fu3_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource4(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu4_values.xml", myFixture, "res/values/fu4_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource5(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu5_values.xml", myFixture, "res/values/fu5_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource6(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu6_values.xml", myFixture, "res/values/fu6_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource7(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu7_values.xml", myFixture, "res/values/fu7_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource8(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu8_values.xml", myFixture, "res/values/f8_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  /**
   * Test that usages are found if searching for a reference value in an resource file.
   */
  public void testValueResource9(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu13_values.xml", myFixture, "res/values/f13_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance(String expectedTreeRepresentation) {
    Collection<UsageInfo> references = findUsages("fu10_values.xml", myFixture, "res/values/f10_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance1(String expectedTreeRepresentation) {
    Collection<UsageInfo> references = findUsages("fu11_values.xml", myFixture, "res/values/f11_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance2(String expectedTreeRepresentation) {
    Collection<UsageInfo> references = findUsages("fu14_values.xml", myFixture, "res/values/f14_values.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResource(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu5_layout.xml", myFixture, "res/layout/fu5_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResourceField(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu3.java", myFixture, "src/p1/p2/Fu3.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResourceField(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu4.java", myFixture, "src/p1/p2/Fu4.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResourceField(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu6.java", myFixture, "src/p1/p2/Fu6.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResource(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu7_layout.xml", myFixture, "res/layout/fu7_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceField(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu8.java", myFixture, "src/p1/p2/Fu8.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceDeclaration(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu9_layout.xml", myFixture, "res/layout/fu9_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStringArray(String expectedTreeRepresentation) {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("stringArray.xml", myFixture, "res/layout/stringArray.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleable(String expectedTreeRepresentation) throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    Collection<UsageInfo> references = findUsages("MyView1.java", myFixture, "src/p1/p2/MyView.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleableAttr(String expectedTreeRepresentation) throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    Collection<UsageInfo> references = findUsages("MyView2.java", myFixture, "src/p1/p2/MyView.java");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleItemAttrFromJava(String expectedTreeRepresentation) throws Throwable {
    createManifest();
    myFixture.addFileToProject(
      "res/values/style.xml",
      //language=XML
      "<resources>\n" +
      "    <style name=\"Example\">\n" +
      "        <item name=\"newAttr\">true</item>\n" +
      "    </style>\n" +
      "</resources>");
    myFixture.addFileToProject(
      "res/values/attrs.xml",
      //language=XML
      "<resources>\n" +
      "  <declare-styleable name=\"LabelView\">\n" +
      "    <attr name=\"newAttr\" format=\"boolean\" />\n" +
      "  </declare-styleable>\n" +
      "</resources>");
    PsiFile file = myFixture.addFileToProject(
      "src/p1/p2/MyView.java",
      //language=JAVA
      "package p1.p2;\n" +
      "\n" +
      "import android.content.Context;\n" +
      "import android.content.res.TypedArray;\n" +
      "import android.util.AttributeSet;\n" +
      "import android.widget.Button;\n" +
      "\n" +
      "@SuppressWarnings(\"UnusedDeclaration\")\n" +
      "public class MyView extends Button {\n" +
      "    public MyView(Context context, AttributeSet attrs, int defStyle) {\n" +
      "        super(context, attrs, defStyle);\n" +
      "        int attribute = R.attr.newA<caret>ttr;\n" +
      "    }\n" +
      "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture);
    assertThat(getUsageViewTreeTextRepresentation(references)).isEqualTo(expectedTreeRepresentation);
  }

  public void testIdDeclarations(String expectedTreeRepresentation) {
    Collection<UsageInfo> references = findUsages("fu12_layout.xml", myFixture, "res/layout/f12_layout.xml");
    assertEquals(expectedTreeRepresentation, getUsageViewTreeTextRepresentation(references));
  }

  // Regression test for https://issuetracker.google.com/140199141
  public void testJavaPsiExpression() {
    PsiFile file = myFixture.addFileToProject(
      "src/p1/p2/Example.java",
      //language=JAVA
      "package p1.p2;\n" +
      "\n" +
      "public class Example  {\n" +
      "  void foo(){}\n" +
      "  void bar() {\n" +
      "    fo<caret>o();\n" +
      "  }\n" +
      "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture);
    String expected = "Usage (1 usage)\n" +
                      " Found usages (1 usage)\n" +
                      "  Unclassified usage (1 usage)\n" +
                      "   app (1 usage)\n" +
                      "    p1.p2 (1 usage)\n" +
                      "     Example (1 usage)\n" +
                      "      bar() (1 usage)\n" +
                      "       6foo();\n";
    assertThat(myFixture.getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
  }

  // Regression test for https://issuetracker.google.com/140199141
  public void testKotlinSimpleNameExpression() {
    PsiFile file = myFixture.addFileToProject(
      "src/p1/p2/Example.kt",
      //language=kotlin
      "package p1.p2\n" +
      "\n" +
      "class Example {\n" +
      "  fun foo(){}\n" +
      "  fun bar() {\n" +
      "    fo<caret>o();\n" +
      "  }\n" +
      "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture);
    String expected = "Usage (1 usage)\n" +
                      " Found usages (1 usage)\n" +
                      "  Function call (1 usage)\n" +
                      "   app (1 usage)\n" +
                      "    p1.p2 (1 usage)\n" +
                      "     Example.kt (1 usage)\n" +
                      "      Example (1 usage)\n" +
                      "       bar (1 usage)\n" +
                      "        6foo();\n";
    assertThat(myFixture.getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
  }

  private static Collection<UsageInfo> findUsages(String fileName, final JavaCodeInsightTestFixture fixture, String newFilePath) {
    VirtualFile file = fixture.copyFileToProject(BASE_PATH + fileName, newFilePath);
    return findUsages(file,fixture);
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture) {
    fixture.configureFromExistingVirtualFile(file);
    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(
      dataId -> ((EditorEx)fixture.getEditor()).getDataContext().getData(dataId));
    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  public static Collection<UsageInfo> findUsagesNoEditor(String filePath, JavaCodeInsightTestFixture fixture) {
    PsiFile psiFile = fixture.configureByFile(filePath);
    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(psiFile);
    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  /**
   * Generates the text representation of the UsageView. We previously used CodeInsideTestFixture.getUsageViewTreeTextRepresentation(),
   * except that wouldn't provide UsageTargets to the UsageTypeProviders.
   * @param usages
   */
  @NotNull
  public String getUsageViewTreeTextRepresentation(@NotNull final Collection<? extends UsageInfo> usages) {
    if (!StudioFlags.RESOLVE_USING_REPOS.get()) {
      // Old tests aren't expecting to include Usage Target, for this the CodeInsideTestFixture implementation is sufficient.
      return myFixture.getUsageViewTreeTextRepresentation(usages);
    }
    final UsageTarget[] target = Arrays.stream(UsageTargetUtil.findUsageTargets(
      dataId -> ((EditorEx)myFixture.getEditor()).getDataContext().getData(dataId))).limit(1).toArray(UsageTarget[]::new);
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager
      .getInstance(getProject()).createUsageView(target,
                                                 StreamEx.of(usages)
                                                   .map(usageInfo -> new UsageInfo2UsageAdapter(usageInfo)).toArray(Usage.EMPTY_ARRAY),
                                                 new UsageViewPresentation(),
                                                 null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);
    usageView.expandAll();
    return TreeTester.forNode(usageView.getRoot()).withPresenter(usageView::getNodeText).constructTextRepresentation();
  }
}
