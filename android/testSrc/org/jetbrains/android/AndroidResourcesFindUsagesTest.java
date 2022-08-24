
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

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.TreeNodeTester;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.util.containers.ContainerUtil;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * FindUsages tests for Android resources.
 *
 * Usages are found via {@link AndroidResourcesFindUsagesHandlerFactory}, Tests that require Gradle projects are at
 * {@link AndroidGradleProjectFindUsagesTest}.
 */
public class AndroidResourcesFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";
  private static final String MODULE_WITHOUT_DEPENDENCY = "MODULE_WITHOUT_DEPENDENCY";

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

  // Only testing in the new Resources Pipeline, the old pipeline never supported FindUsages of framework resources.
  public void testFrameworkResourceFromUsage() {
    PsiFile file = myFixture.addFileToProject(
      "src/p1/p2/MyView.java",
      //language=JAVA
      "package p1.p2;\n" +
      "public class MyTest {\n" +
      "    public MyTest() {\n" +
      "        int attribute = android.R.color.background<caret>_dark;\n" +
      "    }\n" +
      "}\n");
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    Collection<UsageInfo> references = findUsages(file.getVirtualFile(), myFixture, GlobalSearchScope.allScope(myFixture.getProject()));
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @android:color/background_dark\n" +
                 " Usages in (4)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   values (1)\n" +
                 "    colors.xml (1)\n" +
                 "     44<color name=\"background_dark\">#ff000000</color>\n" +
                 "  Resource reference Android resources XML (2)\n" +
                 "   values (2)\n" +
                 "    colors.xml (1)\n" +
                 "     48<color name=\"bright_foreground_light\">@android:color/background_dark</color>\n" +
                 "    themes.xml (1)\n" +
                 "     48<item name=\"colorBackground\">@color/background_dark</item>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     MyTest (1)\n" +
                 "      MyTest() (1)\n" +
                 "       4int attribute = android.R.color.background_dark;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testDoNotFindResourceOutOfScope() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    VirtualFile file = myFixture.copyFileToProject(BASE_PATH + "fu2_layout.xml", "res/layout/fu2_layout.xml");
    Collection<UsageInfo> references = findUsages(file, myFixture);
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu2_layout.xml (1)\n" +
                 "      3<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testFontResource() {
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
    assertEquals("<root> (2)\n" +
                 " Targets\n" +
                 "  @font/new_font\n" +
                 " Usages in (2)\n" +
                 "  Android resource file (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "font (1)\n" +
                 "     new_font.ttf (1)\n" +
                 "      Android resource file font/new_font.ttf\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Example (1)\n" +
                 "      f() (1)\n" +
                 "       4int id1 = R.font.new_font;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    Collection<UsageInfo> references = findUsages("fu1_layout.xml", myFixture, "res/layout/fu1_layout.xml");
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @drawable/picture3\n" +
                 " Usages in (4)\n" +
                 "  Android resource file (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "drawable (1)\n" +
                 "     picture3.gif (1)\n" +
                 "      Android resource file drawable/picture3.gif\n" +
                 "  Resource reference Android resources XML (2)\n" +
                 "   app (2)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu1_layout.xml (1)\n" +
                 "      3<TextView android:background=\"@drawable/picture3\"/>\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     styles.xml (1)\n" +
                 "      3<item name=\"android:windowBackground\">@drawable/picture3</item>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.drawable.picture3;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResourceNoEditor() {
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
    assertEquals("<root> (2)\n" +
                 " Usages in (2)\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      1<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Foo (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.layout.layout;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu3.java", myFixture, "src/p1/p2/Fu3.java");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @drawable/picture3\n" +
                 " Usages in (3)\n" +
                 "  Android resource file (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "drawable (1)\n" +
                 "     picture3.gif (1)\n" +
                 "      Android resource file drawable/picture3.gif\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      3android:background=\"@drawable/picture3\">\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Fu3 (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.drawable.picture3;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testIdDeclarations() {
    Collection<UsageInfo> references = findUsages("fu12_layout.xml", myFixture, "res/layout/f12_layout.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @id/second\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (2)\n" +
                 "   app (2)\n" +
                 "    res" + File.separatorChar + "layout (2)\n" +
                 "     f12_layout.xml (2)\n" +
                 "      16android:id=\"@+id/second\"\n" +
                 "      26android:layout_below=\"@+id/second\"\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     f12_layout.xml (1)\n" +
                 "      27android:labelFor=\"@id/second\"\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu7_layout.xml", myFixture, "res/layout/fu7_layout.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @id/anchor\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu7_layout.xml (1)\n" +
                 "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu7_layout.xml (1)\n" +
                 "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       8int id3 = R.id.anchor;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceDeclaration() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu9_layout.xml", myFixture, "res/layout/fu9_layout.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @id/anchor\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu9_layout.xml (1)\n" +
                 "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu9_layout.xml (1)\n" +
                 "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       8int id3 = R.id.anchor;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu8.java", myFixture, "src/p1/p2/Fu8.java");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @id/anchor\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      4<EditText android:id=\"@+id/anchor\"/>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      7<TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Fu6 (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.id.anchor;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testStringArray() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("stringArray.xml", myFixture, "res/layout/stringArray.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @array/str_arr\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      4<string-array name=\"str_arr\"></string-array>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     stringArray.xml (1)\n" +
                 "      3<ListView android:entries=\"@array/str_arr\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       9int id4 = R.array.str_arr;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleable() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    Collection<UsageInfo> references = findUsages("MyView1.java", myFixture, "src/p1/p2/MyView.java");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @styleable/MyView\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     attrs.xml (1)\n" +
                 "      3<declare-styleable name=\"MyView\">\n" +
                 "  Resource reference in code (2)\n" +
                 "   app (2)\n" +
                 "    p1.p2 (2)\n" +
                 "     MyView (2)\n" +
                 "      MyView(Context, AttributeSet, int) (2)\n" +
                 "       13TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyView);\n" +
                 "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n",
                 getUsageViewTreeTextRepresentation(references));
  }

  // Styleable attr fields are not yet found in the new Find Usages pipeline
  public void testStyleableAttr() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    Collection<UsageInfo> references = findUsages("MyView2.java", myFixture, "src/p1/p2/MyView.java");
    assertEquals("<root> (1)\n" +
                 " Targets\n" +
                 "  @styleable/MyView_answer\n" +
                 " Usages in (1)\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     MyView (1)\n" +
                 "      MyView(Context, AttributeSet, int) (1)\n" +
                 "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n",
                 getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance() {
    Collection<UsageInfo> references = findUsages("fu10_values.xml", myFixture, "res/values/f10_values.xml");
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @style/myStyle\n" +
                 " Usages in (4)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f10_values.xml (1)\n" +
                 "      2<style name=\"myStyle\">\n" +
                 "  Resource reference Android resources XML (3)\n" +
                 "   app (3)\n" +
                 "    res" + File.separatorChar + "values (3)\n" +
                 "     f10_values.xml (3)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance1() {
    Collection<UsageInfo> references = findUsages("fu11_values.xml", myFixture, "res/values/f11_values.xml");
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @style/myStyle\n" +
                 " Usages in (4)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f11_values.xml (1)\n" +
                 "      2<style name=\"myStyle\">\n" +
                 "  Resource reference Android resources XML (3)\n" +
                 "   app (3)\n" +
                 "    res" + File.separatorChar + "values (3)\n" +
                 "     f11_values.xml (3)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance2() {
    Collection<UsageInfo> references = findUsages("fu14_values.xml", myFixture, "res/values/f14_values.xml");
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @style/myStyle\n" +
                 " Usages in (4)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f14_values.xml (1)\n" +
                 "      2<style name=\"myStyle\">\n" +
                 "  Resource reference Android resources XML (3)\n" +
                 "   app (3)\n" +
                 "    res" + File.separatorChar + "values (3)\n" +
                 "     f14_values.xml (3)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu5_layout.xml", myFixture, "res/layout/fu5_layout.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hi\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      3<item name=\"hi\" type=\"string\"/>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu5_layout.xml (1)\n" +
                 "      3<TextView android:text=\"@string/hi\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       7int id3 = R.string.hi;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu6.java", myFixture, "src/p1/p2/Fu6.java");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hi\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      3<item name=\"hi\" type=\"string\"/>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      6<TextView android:text=\"@string/hi\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Fu6 (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.string.hi;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findUsages("fu2_layout.xml", myFixture, "res/layout/fu2_layout.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     fu2_layout.xml (1)\n" +
                 "      3<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource1() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu1_values.xml", myFixture, "res/values/fu1_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu1_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource2() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu2_values.xml", myFixture, "res/values/fu2_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu2_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource3() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu3_values.xml", myFixture, "res/values/fu3_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu3_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource4() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu4_values.xml", myFixture, "res/values/fu4_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu4_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource5() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu5_values.xml", myFixture, "res/values/fu5_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu5_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource6() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu6_values.xml", myFixture, "res/values/fu6_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu6_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource7() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu7_values.xml", myFixture, "res/values/fu7_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     fu7_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource8() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu8_values.xml", myFixture, "res/values/f8_values.xml");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f8_values.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource9() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("fu13_values.xml", myFixture, "res/values/f13_values.xml");
    assertEquals("<root> (4)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (4)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f13_values.xml (1)\n" +
                 "      4<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (2)\n" +
                 "   app (2)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     f13_values.xml (1)\n" +
                 "      9<item>@string/hello</item>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Class1 (1)\n" +
                 "      f() (1)\n" +
                 "       6int id2 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findUsages("Fu4.java", myFixture, "src/p1/p2/Fu4.java");
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @string/hello\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     strings.xml (1)\n" +
                 "      2<string name=\"hello\">hello</string>\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "layout (1)\n" +
                 "     layout.xml (1)\n" +
                 "      5<TextView android:text=\"@string/hello\"/>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     Fu4 (1)\n" +
                 "      f() (1)\n" +
                 "       5int id1 = R.string.hello;\n", getUsageViewTreeTextRepresentation(references));
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
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @attr/newAttr\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     attrs.xml (1)\n" +
                 "      3<attr name=\"newAttr\" format=\"boolean\" />\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     style.xml (1)\n" +
                 "      3<item name=\"newAttr\">true</item>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     MyView (1)\n" +
                 "      MyView(Context, AttributeSet, int) (1)\n" +
                 "       12int attribute = R.attr.newAttr;\n", getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleItemAttrFromJava() throws Throwable {
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
    assertEquals("<root> (3)\n" +
                 " Targets\n" +
                 "  @attr/newAttr\n" +
                 " Usages in (3)\n" +
                 "  Resource declaration in Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     attrs.xml (1)\n" +
                 "      3<attr name=\"newAttr\" format=\"boolean\" />\n" +
                 "  Resource reference Android resources XML (1)\n" +
                 "   app (1)\n" +
                 "    res" + File.separatorChar + "values (1)\n" +
                 "     style.xml (1)\n" +
                 "      3<item name=\"newAttr\">true</item>\n" +
                 "  Resource reference in code (1)\n" +
                 "   app (1)\n" +
                 "    p1.p2 (1)\n" +
                 "     MyView (1)\n" +
                 "      MyView(Context, AttributeSet, int) (1)\n" +
                 "       12int attribute = R.attr.newAttr;\n", getUsageViewTreeTextRepresentation(references));
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
    String expected = "<root> (1)\n" +
                      " Targets\n" +
                      "  foo()\n" +
                      " Usages in (1)\n" +
                      "  Unclassified (1)\n" +
                      "   app (1)\n" +
                      "    p1.p2 (1)\n" +
                      "     Example (1)\n" +
                      "      bar() (1)\n" +
                      "       6foo();\n";
    assertThat(getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
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
    String expected = "<root> (1)\n" +
                      " Targets\n" +
                      "  foo()\n" +
                      " Usages in (1)\n" +
                      "  Function call (1)\n" +
                      "   app (1)\n" +
                      "    p1.p2 (1)\n" +
                      "     Example.kt (1)\n" +
                      "      Example (1)\n" +
                      "       bar (1)\n" +
                      "        6foo();\n";
    assertThat(getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
  }

  private static Collection<UsageInfo> findUsages(String fileName, final JavaCodeInsightTestFixture fixture, String newFilePath) {
    VirtualFile file = fixture.copyFileToProject(BASE_PATH + fileName, newFilePath);
    return findUsages(file,fixture);
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture) {
    return findUsages(file, fixture, null);
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture, GlobalSearchScope scope) {
    fixture.configureFromExistingVirtualFile(file);
    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(
      dataId -> ((EditorEx)fixture.getEditor()).getDataContext().getData(dataId));
    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return ((CodeInsightTestFixtureImpl)fixture).findUsages(((PsiElementUsageTarget)targets[0]).getElement(), scope);
  }

  public static Collection<UsageInfo> findUsagesNoEditor(String filePath, JavaCodeInsightTestFixture fixture) {
    PsiFile psiFile = fixture.configureByFile(filePath);
    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(psiFile);
    assert targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  /**
   * Generates the text representation of the UsageView. We previously used CodeInsideTestFixture.getUsageViewTreeTextRepresentation(),
   * except that wouldn't provide UsageTargets to the UsageTypeProviders.
   * @param usages
   */
  @NotNull
  public String getUsageViewTreeTextRepresentation(@NotNull final Collection<? extends UsageInfo> usages) {
    UsageTarget[] target = UsageTargetUtil.findUsageTargets(dataId -> ((EditorEx)myFixture.getEditor()).getDataContext().getData(dataId));
    target = target == null ? UsageTarget.EMPTY_ARRAY : Arrays.stream(target).limit(1).toArray(UsageTarget[]::new);
    UsageViewImpl usageView = (UsageViewImpl)UsageViewManager
      .getInstance(getProject())
      .createUsageView(
        target,
        ContainerUtil.map2Array(usages, Usage.EMPTY_ARRAY, usageInfo -> new UsageInfo2UsageAdapter(usageInfo)),
        new UsageViewPresentation(),
         null);
    Disposer.register(myFixture.getTestRootDisposable(), usageView);
    usageView.expandAll();
    return TreeNodeTester.forNode(usageView.getRoot()).withPresenter(usageView::getNodeText).constructTextRepresentation();
  }
}
