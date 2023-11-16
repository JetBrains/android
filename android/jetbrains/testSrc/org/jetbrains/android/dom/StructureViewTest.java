/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.structure.DomStructureViewBuilder;
import com.intellij.util.xml.structure.DomStructureViewBuilderProvider;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.structure.layout.LayoutStructureViewBuilder;
import org.jetbrains.android.dom.structure.resources.ResourceStructureViewBuilder;
import org.jetbrains.annotations.NotNull;

public class StructureViewTest extends AndroidDomTestCase {
  public StructureViewTest() {
    super("dom");
  }

  public void testResourceStructure() throws Exception {
    VirtualFile file = copyFileToProject("resources/resources_structure.xml", "/res/values/styles.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertInstanceOf(psiFile, XmlFile.class);
    DomFileElement<Resources> element = DomManager.getDomManager(getProject()).getFileElement(((XmlFile)psiFile), Resources.class);
    assertNotNull(element);

    StructureViewModel model = new ResourceStructureViewBuilder(element).createStructureViewModel(null);
    String expected = "Resources file 'styles.xml'\n" +
                      "  Style - AppTheme\n" +
                      "  String - test_string\n" +
                      "  Style - SecondStyle\n";
    assertEquals(expected, model.getRoot().toString());
  }

  public void testResourceStructureNestedAttr() {
    PsiFile file = myFixture.addFileToProject(
      "/res/values/styles.xml",
      "<resources>\n" +
      "    <attr name=\"testAttr\" format=\"boolean\" />  \n" +
      "    <style name=\"AppTheme\" parent=\"android:Theme\">\n" +
      "        <item name=\"testAttr\">false</item>\n" +
      "    </style>\n" +
      "    <string name=\"test_string\">Just a string</string>\n" +
      "    <declare-styleable name=\"testStyleable\">\n" +
      "        <attr name=\"testAttr\" format=\"boolean\" />\n" +
      "    </declare-styleable>\n" +
      "</resources>");
    assertInstanceOf(file, XmlFile.class);
    DomFileElement<Resources> element = DomManager.getDomManager(getProject()).getFileElement(((XmlFile)file), Resources.class);
    assertNotNull(element);

    StructureViewModel model = new ResourceStructureViewBuilder(element).createStructureViewModel(null);
    String expected = "Resources file 'styles.xml'\n" +
                      "  Attr - testAttr\n" +
                      "  Style - AppTheme\n" +
                      "    Style Item - testAttr\n" +
                      "  String - test_string\n" +
                      "  Styleable - testStyleable\n" +
                      "    Attr - testAttr\n";
    assertThat(expected).isEqualTo(model.getRoot().toString());
  }

  public void testLayoutStructure() throws Exception {
    VirtualFile file = copyFileToProject("layout/structure_view_test.xml", "/res/layout/layout.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertInstanceOf(psiFile, XmlFile.class);
    DomFileElement<LayoutViewElement> element =
      DomManager.getDomManager(getProject()).getFileElement(((XmlFile)psiFile), LayoutViewElement.class);
    assertNotNull(element);

    final StructureViewModel model = new LayoutStructureViewBuilder(element).createStructureViewModel(null);
    String expected = "LinearLayout\n" +
                      "  TextView\n" +
                      "  TextView (@+id/login)\n" +
                      "  TextView (@+id/password)\n" +
                      "  LinearLayout\n" +
                      "    Include @layout/some_other_layout\n" +
                      "    TextView\n";
    assertEquals(expected, model.getRoot().toString());
  }

  public void testLayoutStructureOrder1() throws Exception {
    VirtualFile file = copyFileToProject("layout/structure_view_test_order_1.xml", "/res/layout/layout.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertInstanceOf(psiFile, XmlFile.class);
    DomFileElement<LayoutViewElement> element =
      DomManager.getDomManager(getProject()).getFileElement(((XmlFile)psiFile), LayoutViewElement.class);
    assertNotNull(element);

    final StructureViewModel model = new LayoutStructureViewBuilder(element).createStructureViewModel(null);
    String expected = "LinearLayout\n" +
                      "  Fragment\n" +
                      "  Include\n";
    assertEquals(expected, model.getRoot().toString());
  }

  public void testLayoutStructureOrder2() throws Exception {
    VirtualFile file = copyFileToProject("layout/structure_view_test_order_2.xml", "/res/layout/layout.xml");
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertInstanceOf(psiFile, XmlFile.class);
    DomFileElement<LayoutViewElement> element =
      DomManager.getDomManager(getProject()).getFileElement(((XmlFile)psiFile), LayoutViewElement.class);
    assertNotNull(element);

    final StructureViewModel model = new LayoutStructureViewBuilder(element).createStructureViewModel(null);
    String expected = "LinearLayout\n" +
                      "  Include\n" +
                      "  Fragment\n";
    assertEquals(expected, model.getRoot().toString());
  }

  public void testManifestStructure() throws Exception {
    copyFileToProject("manifest/MyActivity.java", "src/p1/p2/MyActivity.java");
    deleteManifest();
    final VirtualFile file = copyFileToProject("manifest/structure_view_test.xml", "AndroidManifest.xml");
    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertInstanceOf(psiFile, XmlFile.class);
    final DomStructureViewBuilder builder =
      new DomStructureViewBuilder(((XmlFile)psiFile), DomStructureViewBuilderProvider.DESCRIPTOR);
    final StructureViewTreeElement root = builder.createStructureViewModel(null).getRoot();
    assertNotNull(root);

    final String expected = "Manifest\n" +
                            "  Application\n" +
                            "    Activity\n" +
                            "      Intent Filter\n" +
                            "        Action (android.intent.action.MAIN)\n" +
                            "        Category (android.intent.category.LAUNCHER)\n" +
                            "    Activity Alias\n" +
                            "      Intent Filter\n" +
                            "        Action (android.intent.action.CREATE_SHORTCUT)\n" +
                            "        Category (android.intent.category.DEFAULT)\n";
    assertEquals(expected, dumpTree(root));
  }

  /**
   * Pretty-print any TreeElement to a String
   */
  @NotNull
  public static String dumpTree(@NotNull TreeElement root) {
    final StringBuilder builder = new StringBuilder();
    dumpTreeToBuilder(builder, root, 0);
    return builder.toString();
  }

  /**
   * Helper function for pretty-printing a tree element
   *
   * @param builder     where results would be written to
   * @param element     tree node being pretty-printed
   * @param indentation level of indentation for the current node
   */
  public static void dumpTreeToBuilder(final @NotNull StringBuilder builder, final @NotNull TreeElement element, final int indentation) {
    for (int i = 0; i < indentation; i++) {
      builder.append("  ");
    }

    builder.append(element.getPresentation().getPresentableText()).append('\n');
    for (TreeElement treeElement : element.getChildren()) {
      dumpTreeToBuilder(builder, treeElement, indentation + 1);
    }
  }

  @Override
  protected String getPathToCopy(String testFileName) {
    return "/dom/resources";
  }
}
