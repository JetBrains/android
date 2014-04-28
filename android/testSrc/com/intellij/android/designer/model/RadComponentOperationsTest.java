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

import com.android.SdkConstants;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.LayoutEditorTestBase;
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;

/**
 * Functional tests for {@linkplain com.intellij.android.designer.model.RadComponentOperations}
 */
public class RadComponentOperationsTest extends LayoutEditorTestBase {

  private MetaManager myMetaManager;
  private XmlElementFactory myXmlElementFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myMetaManager = ViewsMetaManager.getInstance(getProject());
    myXmlElementFactory = XmlElementFactory.getInstance(getProject());
  }

  private static RadViewComponent getLayoutRoot(RadViewComponent modelRoot) {
    return (RadViewComponent)modelRoot.getChildren().get(0);
  }

  public void testSimpleConstruction() throws Exception {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple.xml"));
    @SuppressWarnings("ConstantConditions")
    RadViewComponent rootComponent = getLayoutRoot(editor.getRootViewComponent());
    assertEquals(0, rootComponent.getChildren().size());
    assertEquals("LinearLayout", rootComponent.getTag().getName());
    assertEquals("fill_parent", rootComponent.getAttribute("android:layout_width", null));
    assertEquals("fill_parent", rootComponent.getAttribute("android:layout_height", null));
  }

  public void testMediumConstruction() throws Exception {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("layout.xml"));
    @SuppressWarnings("ConstantConditions")
    RadViewComponent rootComponent = getLayoutRoot(editor.getRootViewComponent());

    assertEquals("LinearLayout(" +
                           "LinearLayout(ImageView, View, ImageView, ImageButton, ProgressBar, ImageView, ImageButton, ), " +
                           // RequestFocus' not in the model
                           //"LinearLayout(EditText, EditText(requestFocus, ), ), " +
                           "LinearLayout(EditText, EditText, ), " +
                           "LinearLayout(Button, Button, ), " +
                           "), ",
                           toString(rootComponent));

    assertEquals("vertical", rootComponent.getAttribute("android:orientation", null));

    RadComponent lastLayoutChild = rootComponent.getChildren().get(2);
    RadComponent shouldBeAButton = lastLayoutChild.getChildren().get(0);

    RadViewComponent buttonView = (RadViewComponent)shouldBeAButton;
    assertEquals("Button", buttonView.getTag().getName());
    assertEquals("0dip", buttonView.getAttribute("android:layout_width", null));
    // Depends on render:
    //assertEquals(0, buttonView.getPaddedBounds().width);
    assertEquals("wrap_content", buttonView.getAttribute("android:layout_height", null));

    // These actual coordinates may be brittle (depend on bounds of widgets?) so we may need to just assert list.size() == 2 instead
    // if this breaks on some platforms
    RootView rootView = editor.getCurrentRootView();
    assertEquals(Arrays.asList(new Rectangle(754, 100, 14, 14), new Rectangle(754, 100, 14, 14)), rootView.getEmptyRegions());
  }

  /**
   * Get a string representation of the given RadComponent tree
   */
  private static String toString(@NotNull RadViewComponent root) {
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

  /**
   * Get a string representation of the given RadComponent tree with bounds.
   * Each node is of the form NodeName[x, y, width, height](Children...),
   */
  private static String toBoundsString(RadViewComponent root, Component relativeto) {
    StringBuilder sb = new StringBuilder();
    sb.append(root.getTag().getName());
    sb.append(root.getBounds(relativeto).toString().substring(Rectangle.class.getName().length()));
    if (!root.getChildren().isEmpty()) {
      sb.append('(');
      for (RadComponent child : root.getChildren()) {
        sb.append(toBoundsString((RadViewComponent)child, relativeto));
      }
      sb.append(')');
    }
    sb.append(", ");
    return sb.toString();
  }

  public void testCreateAndAddSpace() throws Exception {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple.xml"));
    @SuppressWarnings("ConstantConditions")
    final RadViewComponent rootComponent = getLayoutRoot(editor.getRootViewComponent());
    XmlFile psiFile = editor.getXmlFile();

    MetaModel spaceModel = myMetaManager.getModelByTag(SdkConstants.SPACE);
    assertNotNull(spaceModel);

    XmlTag spaceTag = myXmlElementFactory.createTagFromText(spaceModel.getCreation());
    assertNotNull(spaceTag);

    final RadViewComponent spaceComponent = RadComponentOperations.createComponent(spaceTag, spaceModel);
    assertNotNull(spaceComponent);

    assertNotNull(spaceComponent.getTag());
    assertEquals("Space", spaceComponent.getTag().getName());

    (new WriteCommandAction<Void>(getProject(), "Add Tag", psiFile) {

      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        RadComponentOperations.addComponent(rootComponent, spaceComponent, null /* add before */);
      }
    }).execute();

    assertEquals("LinearLayout(Space, ), ", toString(rootComponent));
    assertEquals("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"fill_parent\"\n" +
                 "    android:layout_height=\"fill_parent\">\n" +
                 "    <Space\n" +
                 "            android:layout_width=\"20px\"\n" +
                 "            android:layout_height=\"20px\"\n" +
                 "            />\n" +
                 "</LinearLayout>",
                 rootComponent.getTag().getText());
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

  public void testUpdateRootComponent() throws Exception {
    AndroidDesignerEditorPanel editor = createLayoutEditor(getTestFile("simple.xml"));
    @SuppressWarnings("ConstantConditions")
    final RadViewComponent rootComponent = editor.getRootViewComponent();
    XmlFile psiFile = editor.getXmlFile();
    assertNotNull(psiFile);

    assertNotNull(rootComponent);
    assertEquals("LinearLayout(LinearLayout, ), ", toString(rootComponent));

    RootView rootView = editor.getCurrentRootView();
    assertEquals("LinearLayout[x=0,y=0,width=768,height=1280](LinearLayout[x=0,y=100,width=768,height=1084], ), ",
                 toBoundsString(rootComponent, rootView));

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"fill_parent\"\n" +
                 "    android:layout_height=\"fill_parent\">\n" +
                 "</LinearLayout>\n", psiFile.getText());

    // Add a button
    MetaModel buttonModel = myMetaManager.getModelByTag("Button");
    assertNotNull(buttonModel);

    editor.requestImmediateRender();
    final RadViewComponent newRoot = editor.getRootViewComponent();
    final RadViewComponent layoutRoot = getLayoutRoot(newRoot);

    final RadViewComponent buttonComponent = RadComponentOperations.createComponent(null, buttonModel);
    assertNotNull(buttonComponent);

    (new WriteCommandAction<Void>(getProject(), "Add Tag", psiFile) {

      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        RadComponentOperations.addComponent(layoutRoot, buttonComponent, null /* add before */);
      }
    }).execute();

    assertNotNull(buttonComponent.getTag());
    assertEquals("Button", buttonComponent.getTag().getName());

    // Ensure tag was properly added
    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"fill_parent\"\n" +
                 "    android:layout_height=\"fill_parent\">\n" +
                 "    <Button\n" +
                 "            android:layout_width=\"wrap_content\"\n" +
                 "            android:layout_height=\"wrap_content\"\n" +
                 "            android:text=\"New Button\"\n" +
                 "            android:id=\"@+id/button\"/>\n" +
                 "</LinearLayout>\n", psiFile.getText());

    assertEquals("LinearLayout(LinearLayout(Button, ), ), ", toString(newRoot));

    editor.requestImmediateRender();
    final RadViewComponent root = editor.getRootViewComponent();
    assertNotNull(root);

    assertEquals("LinearLayout[x=0,y=0,width=768,height=1280](" +
                    "LinearLayout[x=0,y=100,width=768,height=1084](Button[x=0,y=100,width=191,height=96], ), ), ",
                 toBoundsString(root, rootView));
  }
}
