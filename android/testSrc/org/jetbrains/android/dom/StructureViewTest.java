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

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.dom.structure.layout.LayoutStructureViewBuilder;
import org.jetbrains.android.dom.structure.resources.ResourceStructureViewBuilder;

public class StructureViewTest extends AndroidDomTest {
  public StructureViewTest() {
    super(false, "dom");
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

  @Override
  protected String getPathToCopy(String testFileName) {
    return "/dom/resources";
  }
}
