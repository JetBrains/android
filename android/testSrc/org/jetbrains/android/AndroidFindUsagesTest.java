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

import static com.google.common.truth.Truth.assertThat;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.PsiElementUsageTarget;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageTargetUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidFindUsagesTest extends AndroidTestCase {
  private static final String BASE_PATH = "/findUsages/";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(BASE_PATH + "picture3.gif", "res/drawable/picture3.gif");
  }

  public List<UsageInfo> findCodeUsages(String path, String pathInProject) {
    Collection<UsageInfo> usages = findUsages(path, myFixture, pathInProject);
    List<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      if (!usage.isNonCodeUsage) {
        result.add(usage);
      }
    }
    return result;
  }

  public void testFileResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "styles.xml", "res/values/styles.xml");
    List<UsageInfo> references = findCodeUsages("fu1_layout.xml", "res/layout/fu1_layout.xml");
    assertEquals(3, references.size());
    assertEquals("Usage (3 usages)\n" +
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
                 "      3<item name=\"android:windowBackground\">@drawable/picture3</item>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu2_layout.xml", "res/layout/fu2_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      3<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource1() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu1_values.xml", "res/values/fu1_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource2() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu2_values.xml", "res/values/fu2_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource3() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu3_values.xml", "res/values/fu3_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource4() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu4_values.xml", "res/values/fu4_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource5() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu5_values.xml", "res/values/fu5_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource6() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu6_values.xml", "res/values/fu6_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource7() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu7_values.xml", "res/values/fu7_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResource8() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu8_values.xml", "res/values/f8_values.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  /**
   * Test that usages are found if searching for a reference value in an resource file.
   */
  public void testValueResource9() {
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("fu13_values.xml", "res/values/f13_values.xml");
    assertEquals(3, references.size());
    assertEquals("Usage (3 usages)\n" +
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
                 "      9<item>@string/hello</item>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }


  public void testStyleInheritance() {
    Collection<UsageInfo> references = findCodeUsages("fu10_values.xml", "res/values/f10_values.xml");
    assertEquals(3, references.size());
    assertEquals("Usage (3 usages)\n" +
                 " Found usages (3 usages)\n" +
                 "  Usage in Android resources XML (3 usages)\n" +
                 "   app (3 usages)\n" +
                 "    res/values (3 usages)\n" +
                 "     f10_values.xml (3 usages)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance1() {
    Collection<UsageInfo> references = findCodeUsages("fu11_values.xml", "res/values/f11_values.xml");
    assertEquals(3, references.size());
    assertEquals("Usage (3 usages)\n" +
                 " Found usages (3 usages)\n" +
                 "  Usage in Android resources XML (3 usages)\n" +
                 "   app (3 usages)\n" +
                 "    res/values (3 usages)\n" +
                 "     f11_values.xml (3 usages)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleInheritance2() {
    Collection<UsageInfo> references = findCodeUsages("fu14_values.xml", "res/values/f14_values.xml");
    assertEquals(3, references.size());
    assertEquals("Usage (3 usages)\n" +
                 " Found usages (3 usages)\n" +
                 "  Usage in Android resources XML (3 usages)\n" +
                 "   app (3 usages)\n" +
                 "    res/values (3 usages)\n" +
                 "     f14_values.xml (3 usages)\n" +
                 "      6<style name=\"myStyle.s\">\n" +
                 "      10<style name=\"style1\" parent=\"myStyle\">\n" +
                 "      14<style name=\"myStyle.s.a\">\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu5_layout.xml", "res/layout/fu5_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      3<TextView android:text=\"@string/hi\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testFileResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu3.java", "src/p1/p2/Fu3.java");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      3android:background=\"@drawable/picture3\">\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu4.java", "src/p1/p2/Fu4.java");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:text=\"@string/hello\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testValueItemResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu6.java", "src/p1/p2/Fu6.java");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      6<TextView android:text=\"@string/hi\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResource() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu7_layout.xml", "res/layout/fu7_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceField() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "layout.xml", "res/layout/layout.xml");
    Collection<UsageInfo> references = findCodeUsages("Fu8.java", "src/p1/p2/Fu8.java");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      7<TextView android:layout_alignRight=\"@id/anchor\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testIdResourceDeclaration() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("fu9_layout.xml", "res/layout/fu9_layout.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      5<TextView android:layout_alignRight=\"@id/anchor\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testStringArray() {
    myFixture.copyFileToProject(BASE_PATH + "strings.xml", "res/values/strings.xml");
    myFixture.copyFileToProject(BASE_PATH + "Class.java", "src/p1/p2/Class.java");
    Collection<UsageInfo> references = findCodeUsages("stringArray.xml", "res/layout/stringArray.xml");
    assertEquals(2, references.size());
    assertEquals("Usage (2 usages)\n" +
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
                 "      3<ListView android:entries=\"@array/str_arr\"/>\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleable() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");

    Collection<UsageInfo> references = findCodeUsages("MyView1.java", "src/p1/p2/MyView.java");
    String expected = "Usage (2 usages)\n" +
                      " Found usages (2 usages)\n" +
                      "  Resource reference in code (2 usages)\n" +
                      "   app (2 usages)\n" +
                      "    p1.p2 (2 usages)\n" +
                      "     MyView (2 usages)\n" +
                      "      MyView(Context, AttributeSet, int) (2 usages)\n" +
                      "       13TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MyView);\n" +
                      "       14int answer = a.getInt(R.styleable.MyView_answer, 0);\n";
    assertEquals(expected,
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

                 myFixture.getUsageViewTreeTextRepresentation(references));
  }

  public void testStyleableAttr() throws Throwable {
    createManifest();
    myFixture.copyFileToProject(BASE_PATH + "attrs.xml", "res/values/attrs.xml");
    Collection<UsageInfo> references = findCodeUsages("MyView2.java", "src/p1/p2/MyView.java");
    String expected = "Usage (3 usages)\n" +
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
                      "      4<attr name=\"answer\">\n";
    assertEquals(expected, myFixture.getUsageViewTreeTextRepresentation(references));
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
                      "      3<item name=\"newAttr\">true</item>\n";
    assertThat(myFixture.getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
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
    String expected = "Usage (3 usages)\n" +
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
                      "      3<item name=\"newAttr\">true</item>\n";
    assertThat(myFixture.getUsageViewTreeTextRepresentation(references)).isEqualTo(expected);
  }

  public void testIdDeclarations() {
    Collection<UsageInfo> references = findCodeUsages("fu12_layout.xml", "res/layout/f12_layout.xml");
    assertEquals("Usage (2 usages)\n" +
                 " Found usages (2 usages)\n" +
                 "  Usage in Android resources XML (2 usages)\n" +
                 "   app (2 usages)\n" +
                 "    res/layout (2 usages)\n" +
                 "     f12_layout.xml (2 usages)\n" +
                 "      26android:layout_below=\"@+id/second\"\n" +
                 "      27android:labelFor=\"@id/second\"\n",
                 myFixture.getUsageViewTreeTextRepresentation(references));
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
    fixture.configureFromExistingVirtualFile(file);

    final UsageTarget[] targets = UsageTargetUtil.findUsageTargets(
      dataId -> ((EditorEx)fixture.getEditor()).getDataContext().getData(dataId));

    assert targets != null && targets.length > 0 && targets[0] instanceof PsiElementUsageTarget;
    return fixture.findUsages(((PsiElementUsageTarget)targets[0]).getElement());
  }

  public static Collection<UsageInfo> findUsages(VirtualFile file, JavaCodeInsightTestFixture fixture) {
    fixture.configureFromExistingVirtualFile(file);
    final PsiElement targetElement =
      TargetElementUtil.findTargetElement(fixture.getEditor(),
                                          TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null;
    Collection<UsageInfo> usages = fixture.findUsages(targetElement);
    assertThat(usages).named("Usages of " + targetElement).doesNotContain(null);
    return usages;
  }
}