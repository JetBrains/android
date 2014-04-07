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
package com.intellij.android.designer.model;

import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.collect.Lists;
import com.intellij.designer.model.RadComponent;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.text.StringTokenizer;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Functional tests for ModelParser
 */
public class ModelParserTest extends AndroidTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();

  }

  private ModelParser getParserForFile(String filename) {
    File targetFile = new File(getTestDataPath(), FileUtil.join("xmlpull", filename));
    String fileContents = TemplateUtils.readTextFile(targetFile);
    assertNotNull(fileContents);
    XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(getProject()).createFileFromText("simple.xml", XmlFileType.INSTANCE, fileContents);
    return new ModelParser(getProject(), xmlFile);
  }

  public void testSimpleConstruction() throws Exception {
    ModelParser parser = getParserForFile("simple.xml");
    RadViewComponent rootComponent = parser.getRootComponent();
    String layoutText = parser.getLayoutXmlText();

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"fill_parent\"\n" +
                 "    android:layout_height=\"fill_parent\">\n" +
                 "</LinearLayout>\n", layoutText);

    assertEquals(0, rootComponent.getChildren().size());
    assertEquals("LinearLayout", rootComponent.getTag().getName());
    assertEquals("fill_parent", rootComponent.getAttribute("android:layout_width", null));
    assertEquals("fill_parent", rootComponent.getAttribute("android:layout_height", null));
  }

  public void testMediumConstruction() throws Exception {
    ModelParser parser = getParserForFile("layout.xml");
    RadViewComponent rootComponent = parser.getRootComponent();
    String layoutText = parser.getLayoutXmlText();

    String expectedContents = TemplateUtils.readTextFile(new File(getTestDataPath(), FileUtil.join("xmlpull", "layout.xml")));
    assertEquals(expectedContents, layoutText);

    assertEquals("LinearLayout(include, " +
                           "LinearLayout(ImageView, View, ImageView, ImageButton, ProgressBar, ImageView, ImageButton, ), " +
                           "LinearLayout(EditText, EditText(requestFocus, ), ), " +
                           "LinearLayout(Button, Button, ), " +
                           "), ",
                           toString(rootComponent));

    assertEquals("vertical", rootComponent.getAttribute("android:orientation", null));

    RadComponent lastLayoutChild = rootComponent.getChildren().get(3);
    RadComponent shouldBeAButton = lastLayoutChild.getChildren().get(0);

    RadViewComponent buttonView = (RadViewComponent)shouldBeAButton;
    assertEquals("Button", buttonView.getTag().getName());
    assertEquals("0dip", buttonView.getAttribute("android:layout_width", null));
    assertEquals(0, buttonView.getPaddedBounds().width);
    assertEquals("wrap_content", buttonView.getAttribute("android:layout_height", null));
  }

  private static String toString(RadViewComponent root) {
    StringBuilder sb = new StringBuilder();
    sb.append(root.getTag().getName());
    if (!root.getChildren().isEmpty()) {
      sb.append('(');
      for (RadComponent child : root.getChildren()) {
        sb.append(toString((RadViewComponent)child));
      }
      sb.append(')');
    }
    sb.append(", ");
    return sb.toString();
  }

  public void testCreateComponent() throws Exception {

  }

  public void testMoveComponent() throws Exception {

  }

  public void testAddComponent() throws Exception {

  }

  public void testPasteComponent() throws Exception {

  }

  public void testAddComponentTag() throws Exception {

  }

  public void testCheckTag() throws Exception {

  }

  public void testDeleteAttribute() throws Exception {

  }

  public void testGetRootComponent() throws Exception {

  }

  public void testGetLayoutXmlText() throws Exception {

  }

  public void testUpdateRootComponent() throws Exception {

  }

  public void testPrintTree() throws Exception {

  }
}
