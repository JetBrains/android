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

import com.google.common.collect.Lists;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "picture3.gif", "res/drawable/picture3.gif");
    myFixture.copyFileToProject("R.java", "gen/p1/p2/R.java");
  }

  public List<UsageInfo> findCodeUsages(String path, String pathInProject) throws Throwable {
    Collection<UsageInfo> usages = findUsages(path, myFixture, pathInProject);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  public void testFileResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    List<UsageInfo> references = findCodeUsages("fu1_layout.xml", "res/layout/fu1_layout.xml");
    assertEquals(3, references.size());
    assertEquals("Class.java:5:\n" +
                 "  int id1 = R.drawable.picture3;\n" +
                 "                       |~~~~~~~~\n" +
                 "layout/fu1_layout.xml:3:\n" +
                 "  <TextView android:background=\"@drawable/picture3\"/>\n" +
                 "                                |~~~~~~~~~~~~~~~~~~  \n" +
                 "values/styles.xml:3:\n" +
                 "  <item name=\"android:windowBackground\">@drawable/picture3</item>\n" +
                 "                                        |~~~~~~~~~~~~~~~~~~      \n",
                 describeUsages(references));
  }

  public void testValueResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu2_layout.xml", "res/layout/fu2_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/fu2_layout.xml:3:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource1() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu1_values.xml", "res/values/fu1_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource2() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu2_values.xml", "res/values/fu2_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource3() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu3_values.xml", "res/values/fu3_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource4() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu4_values.xml", "res/values/fu4_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource5() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu5_values.xml", "res/values/fu5_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource6() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu6_values.xml", "res/values/fu6_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource7() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu7_values.xml", "res/values/fu7_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueResource8() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu8_values.xml", "res/values/f8_values.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  /**
   * Test that usages are found if searching for a reference value in an resource file.
   */
  public void testValueResource9() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu13_values.xml", "res/values/f13_values.xml");
    assertEquals(3, references.size());
    assertEquals("Class.java:6:\n" +
                 "  int id2 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "values/f13_values.xml:9:\n" +
                 "  <item>@string/hello</item>\n" +
                 "        |~~~~~~~~~~~~~      \n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }


  public void testStyleInheritance() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu10_values.xml", "res/values/f10_values.xml");
    assertEquals(3, references.size());
    assertEquals("values/f10_values.xml:6:\n" +
                 "  <style name=\"myStyle.s\">\n" +
                 "               |~~~~~~~   \n" +
                 "values/f10_values.xml:10:\n" +
                 "  <style name=\"style1\" parent=\"myStyle\">\n" +
                 "                               |~~~~~~~ \n" +
                 "values/f10_values.xml:14:\n" +
                 "  <style name=\"myStyle.s.a\">\n" +
                 "               |~~~~~~~     \n",
                 describeUsages(references));
  }

  public void testStyleInheritance1() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu11_values.xml", "res/values/f11_values.xml");
    assertEquals(3, references.size());
    assertEquals("values/f11_values.xml:6:\n" +
                 "  <style name=\"myStyle.s\">\n" +
                 "               |~~~~~~~   \n" +
                 "values/f11_values.xml:10:\n" +
                 "  <style name=\"style1\" parent=\"myStyle\">\n" +
                 "                               |~~~~~~~ \n" +
                 "values/f11_values.xml:14:\n" +
                 "  <style name=\"myStyle.s.a\">\n" +
                 "               |~~~~~~~     \n",
                 describeUsages(references));
  }

  public void testStyleInheritance2() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu14_values.xml", "res/values/f14_values.xml");
    assertEquals(3, references.size());
    assertEquals("values/f14_values.xml:6:\n" +
                 "  <style name=\"myStyle.s\">\n" +
                 "               |~~~~~~~   \n" +
                 "values/f14_values.xml:10:\n" +
                 "  <style name=\"style1\" parent=\"myStyle\">\n" +
                 "                               |~~~~~~~ \n" +
                 "values/f14_values.xml:14:\n" +
                 "  <style name=\"myStyle.s.a\">\n" +
                 "               |~~~~~~~     \n",
                 describeUsages(references));
  }

  public void testValueItemResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu5_layout.xml", "res/layout/fu5_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:7:\n" +
                 "  int id3 = R.string.hi;\n" +
                 "                     |~~\n" +
                 "layout/fu5_layout.xml:3:\n" +
                 "  <TextView android:text=\"@string/hi\"/>\n" +
                 "                          |~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testFileResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu3.java", "src/p1/p2/Fu3.java");
    assertEquals(2, references.size());
    assertEquals("Fu3.java:5:\n" +
                 "  int id1 = R.drawable.picture3;\n" +
                 "                       |~~~~~~~~\n" +
                 "layout/layout.xml:3:\n" +
                 "  android:background=\"@drawable/picture3\">\n" +
                 "                      |~~~~~~~~~~~~~~~~~~ \n",
                 describeUsages(references));
  }

  public void testValueResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu4.java", "src/p1/p2/Fu4.java");
    assertEquals(2, references.size());
    assertEquals("Fu4.java:5:\n" +
                 "  int id1 = R.string.hello;\n" +
                 "                     |~~~~~\n" +
                 "layout/layout.xml:5:\n" +
                 "  <TextView android:text=\"@string/hello\"/>\n" +
                 "                          |~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testValueItemResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu6.java", "src/p1/p2/Fu6.java");
    assertEquals(2, references.size());
    assertEquals("Fu6.java:5:\n" +
                 "  int id1 = R.string.hi;\n" +
                 "                     |~~\n" +
                 "layout/layout.xml:6:\n" +
                 "  <TextView android:text=\"@string/hi\"/>\n" +
                 "                          |~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testIdResource() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu7_layout.xml", "res/layout/fu7_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:8:\n" +
                 "  int id3 = R.id.anchor;\n" +
                 "                 |~~~~~~\n" +
                 "layout/fu7_layout.xml:5:\n" +
                 "  <TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "                                       |~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testIdResourceField() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu8.java", "src/p1/p2/Fu8.java");
    assertEquals(2, references.size());
    assertEquals("Fu8.java:5:\n" +
                 "  int id1 = R.id.anchor;\n" +
                 "                 |~~~~~~\n" +
                 "layout/layout.xml:7:\n" +
                 "  <TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "                                       |~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testIdResourceDeclaration() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu9_layout.xml", "res/layout/fu9_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:8:\n" +
                 "  int id3 = R.id.anchor;\n" +
                 "                 |~~~~~~\n" +
                 "layout/fu9_layout.xml:5:\n" +
                 "  <TextView android:layout_alignRight=\"@id/anchor\"/>\n" +
                 "                                       |~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testStringArray() throws Throwable {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("stringArray.xml", "res/layout/stringArray.xml");
    assertEquals(2, references.size());
    assertEquals("Class.java:9:\n" +
                 "  int id4 = R.array.str_arr;\n" +
                 "                    |~~~~~~~\n" +
                 "layout/stringArray.xml:3:\n" +
                 "  <ListView android:entries=\"@array/str_arr\"/>\n" +
                 "                             |~~~~~~~~~~~~~~  \n",
                 describeUsages(references));
  }

  public void testStyleable() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");

    Collection<UsageInfo> references = findCodeUsages("MyView1.java", "src/p1/p2/MyView.java");
    //noinspection SpellCheckingInspection
    assertEquals("MyView.java:13:\n" +
                 "  TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyView);\n" +
                 "                                                                   |~~~~~~ \n" +
                 "MyView.java:14:\n" +
                 "  int answer = a.getInt(R.styleable.MyView_answer, 0);\n" +
                 "                                    |~~~~~~~~~~~~~    \n" +
                 "R.java:46:\n" +
                 "  <tr><td><code>{@link #MyView_answer p1.p2:answer}</code></td><td></td></tr>\n" +
                 "                        |~~~~~~~~~~~~~                                       \n" +
                 "R.java:48:\n" +
                 "  @see #MyView_answer\n" +
                 "        |~~~~~~~~~~~~\n" +
                 "R.java:55:\n" +
                 "  attribute's value can be found in the {@link #MyView} array.\n" +
                 "                                                |~~~~~~       \n",

                 // Note: the attrs.xml occurence of "MyView" is not a *reference* to the *field*,
                 // the field is a reference to the XML:
                 // I had earlier implemented this such the following showed up (by
                 // making the resolveInner method in DeclareStyleableNameConverter also
                 // look up AndroidResourceUtil.findResourceFields, but that isn't semantically correct
                 // since "go to declaration" for the XML reference will jump to the derived generated
                 // R class field (and some other highlighting tests will fail).
                 //"values/attrs.xml:3:\n" +
                 //"  <declare-styleable name=\"MyView\">\n" +
                 //"                           |~~~~~~ \n",

                 describeUsages(references));
  }

  public void testStyleableAttr() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    myFixture.copyFileToProject(BASE_PATH + "R_MyView.java", "src/p1/p2/R.java");
    Collection<UsageInfo> references = findCodeUsages("MyView2.java", "src/p1/p2/MyView.java");
    assertEquals("MyView.java:12:\n" +
                 "  int attribute = R.attr.answer;\n" +
                 "                         |~~~~~~\n" +
                 "MyView.java:14:\n" +
                 "  int answer = a.getInt(R.styleable.MyView_answer, 0);\n" +
                 "                                    |~~~~~~~~~~~~~    \n" +
                 "R.java:46:\n" +
                 "  <tr><td><code>{@link #MyView_answer p1.p2:answer}</code></td><td></td></tr>\n" +
                 "                        |~~~~~~~~~~~~~                                       \n" +
                 "R.java:48:\n" +
                 "  @see #MyView_answer\n" +
                 "        |~~~~~~~~~~~~\n" +
                 "R.java:54:\n" +
                 "  <p>This symbol is the offset where the {@link p1.p2.R.attr#answer}\n" +
                 "                                                             |~~~~~~\n",
                 describeUsages(references));
  }

  public void testIdDeclarations() throws Throwable {
    Collection<UsageInfo> references = findCodeUsages("fu12_layout.xml", "res/layout/f12_layout.xml");
    assertEquals("layout/f12_layout.xml:26:\n" +
                 "  android:layout_below=\"@+id/second\"\n" +
                 "                        |~~~~~~~~~~~\n" +
                 "layout/f12_layout.xml:27:\n" +
                 "  android:labelFor=\"@id/second\"\n" +
                 "                    |~~~~~~~~~~\n",
                 describeUsages(references));
  }

  private static Collection<UsageInfo> findUsages(String fileName, final JavaCodeInsightTestFixture fixture, String newFilePath)
    throws Throwable {
    VirtualFile file = fixture.copyFileToProject(BASE_PATH + fileName, newFilePath);
    fixture.configureFromExistingVirtualFile(file);

    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        return ((EditorEx)fixture.getEditor()).getDataContext().getData(dataId);
      }
    });

    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture) throws Exception {
    fixture.configureFromExistingVirtualFile(file);
    final PsiElement targetElement = TargetElementUtil
      .findTargetElement(fixture.getEditor(),
                         TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null;
    return fixture.findUsages(targetElement);
  }

  private static String describeUsages(Collection<UsageInfo> collection) {
    assertNotNull(collection);
    // Ensure stable output: sort in a predictable order
    List<UsageInfo> usages = new ArrayList<UsageInfo>(collection);
    Collections.sort(usages, new Comparator<UsageInfo>() {
      @Override
      public int compare(UsageInfo usageInfo1, UsageInfo usageInfo2) {
        PsiFile file1 = usageInfo1.getFile();
        PsiFile file2 = usageInfo2.getFile();
        assertNotNull(file1);
        assertNotNull(file2);
        int delta = file1.getName().compareTo(file2.getName());
        if (delta != 0) {
          return delta;
        }
        VirtualFile virtualFile1 = file1.getVirtualFile();
        VirtualFile virtualFile2 = file2.getVirtualFile();
        if (virtualFile1 != null && virtualFile2 != null) {
          delta = virtualFile1.getPath().compareTo(virtualFile2.getPath());
        } else if (virtualFile1 != null) {
          delta = -1;
        } else {
          delta = 1;
        }
        if (delta != 0) {
          return delta;
        }
        return usageInfo1.getNavigationOffset() - usageInfo2.getNavigationOffset();
      }
    });

    // Remove duplicates: For some of the tests we manually add an R class with values
    // which duplicates the dynamically augmented (light) fields
    List<UsageInfo> unique = Lists.newArrayListWithExpectedSize(usages.size());
    PsiElement prev = null;
    for (UsageInfo usage : usages) {
      if (prev == null || usage.getElement() == null || !prev.isEquivalentTo(usage.getElement())) {
        unique.add(usage);
      }
      prev = usage.getElement();
    }
    usages = unique;

    // Create golden file output
    StringBuilder sb = new StringBuilder();
    for (UsageInfo usage : usages) {
      PsiFile file = usage.getFile();
      int navigationOffset = usage.getNavigationOffset();
      Segment segment = usage.getSegment();
      appendSourceDescription(sb, file, navigationOffset, segment);
    }
    return sb.toString();
  }
}
