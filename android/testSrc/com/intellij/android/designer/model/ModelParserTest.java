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
import com.android.ide.common.rendering.api.RenderSession;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * Functional tests for ModelParser
 */
public class ModelParserTest extends AndroidTestCase {

  private MetaManager myMetaManager;
  private XmlElementFactory myXmlElementFactory;
  private ConfigurationManager myConfigurationManager;
  private AndroidFacet myFacet;
  private ModelParser myModelParser;
  private TestRenderContext myRenderContext;
  private TestRootView myRootView;
  private JPanel myBasePanel = new JPanel();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myMetaManager = ViewsMetaManager.getInstance(getProject());
    myXmlElementFactory = XmlElementFactory.getInstance(getProject());
    myFacet = AndroidFacet.getInstance(myModule);
    assertNotNull(myFacet);
    myConfigurationManager = myFacet.getConfigurationManager();
    assertNotNull(myConfigurationManager);
  }

  @Override
  protected boolean requireRecentSdk() {
    // Need valid layoutlib install
    return true;
  }

  @NotNull
  private VirtualFile getTestFile(String filename) {
    File sourceFile = new File(FileUtil.toSystemDependentName(getTestDataPath()), FileUtil.join("designer", filename));
    return myFixture.copyFileToProject(sourceFile.getPath(), "res/layout/" + filename);
  }

  private ModelParser getParserForFile(VirtualFile virtualFile) throws Exception {
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(virtualFile);
    assertNotNull(psiFile);
    IAndroidTarget target = getTestTarget(myFacet.getConfigurationManager());
    assertNotNull(target);

    // Create our parser
    ModelParser parser = new ModelParser(getProject(), psiFile);

    // Get an ID manager
    parser.getRootComponent().setClientProperty(IdManager.KEY, new IdManager());

    // Get a Property Parser
    PropertyParser propertyParser = new PropertyParser(myModule, target);
    parser.getRootComponent().setClientProperty(PropertyParser.KEY, propertyParser);

    return parser;
  }

  @Nullable
  private IAndroidTarget getTestTarget(@NotNull ConfigurationManager configurationManager) {
    String platformDir = getPlatformDir();
    for (IAndroidTarget target : configurationManager.getTargets()) {
      if (!ConfigurationManager.isLayoutLibTarget(target)) {
        continue;
      }
      String path = target.getPath(IAndroidTarget.ANDROID_JAR);
      if (path == null) {
        continue;
      }
      File f = new File(path);
      if (f.getParentFile() != null && f.getParentFile().getName().equals(platformDir)) {
        return target;
      }
    }

    return null;
  }

  public void testSimpleConstruction() throws Exception {
    ModelParser parser = getParserForFile(getTestFile("simple.xml"));
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
    ModelParser parser = getParserForFile(getTestFile("layout.xml"));
    RadViewComponent rootComponent = parser.getRootComponent();
    String layoutText = parser.getLayoutXmlText();

    String expectedContents = TemplateUtils.readTextFile(new File(getTestDataPath(), FileUtil.join("designer", "layout.xml")));
    assertEquals(expectedContents, layoutText);

    assertEquals("LinearLayout(" +
                           "LinearLayout(ImageView, View, ImageView, ImageButton, ProgressBar, ImageView, ImageButton, ), " +
                           "LinearLayout(EditText, EditText(requestFocus, ), ), " +
                           "LinearLayout(Button, Button, ), " +
                           "), ",
                           toString(rootComponent));

    assertEquals("vertical", rootComponent.getAttribute("android:orientation", null));

    RadComponent lastLayoutChild = rootComponent.getChildren().get(2);
    RadComponent shouldBeAButton = lastLayoutChild.getChildren().get(0);

    RadViewComponent buttonView = (RadViewComponent)shouldBeAButton;
    assertEquals("Button", buttonView.getTag().getName());
    assertEquals("0dip", buttonView.getAttribute("android:layout_width", null));
    assertEquals(0, buttonView.getPaddedBounds().width);
    assertEquals("wrap_content", buttonView.getAttribute("android:layout_height", null));
  }

  /**
   * Get a string representation of the given RadComponent tree
   */
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
    VirtualFile file = getTestFile("simple.xml");
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(file);
    ModelParser parser = getParserForFile(file);
    final RadViewComponent rootComponent = parser.getRootComponent();

    MetaModel spaceModel = myMetaManager.getModelByTag(SdkConstants.SPACE);
    assertNotNull(spaceModel);

    XmlTag spaceTag = myXmlElementFactory.createTagFromText(spaceModel.getCreation());
    assertNotNull(spaceTag);

    final RadViewComponent spaceComponent = ModelParser.createComponent(spaceTag, spaceModel);
    assertNotNull(spaceComponent);

    assertNotNull(spaceComponent.getTag());
    assertEquals("Space", spaceComponent.getTag().getName());

    (new WriteCommandAction<Void>(getProject(), "Add Tag", psiFile) {

      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        ModelParser.addComponent(rootComponent, spaceComponent, null /* add before */);
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
    VirtualFile layoutFile = getTestFile("simple.xml");

    final XmlFile psiFile = parseFile(layoutFile);

    RadViewComponent rootComponent = myModelParser.getRootComponent();
    assertEquals("LinearLayout(LinearLayout, ), ", toString(rootComponent));

    assertEquals("LinearLayout[x=0,y=0,width=768,height=1280](LinearLayout[x=0,y=100,width=768,height=1084], ), ",
                 toBoundsString(rootComponent, myRootView));

    assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                 "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "    android:layout_width=\"fill_parent\"\n" +
                 "    android:layout_height=\"fill_parent\">\n" +
                 "</LinearLayout>\n", psiFile.getText());

    // Add a button
    final RadViewComponent layoutRoot = (RadViewComponent)rootComponent.getChildren().get(0);
    rootComponent.setClientProperty(PropertyParser.KEY, layoutRoot.getClientProperty(PropertyParser.KEY));
    MetaModel buttonModel = myMetaManager.getModelByTag("Button");
    assertNotNull(buttonModel);

    final RadViewComponent buttonComponent = ModelParser.createComponent(null, buttonModel);
    assertNotNull(buttonComponent);

    (new WriteCommandAction<Void>(getProject(), "Add Tag", psiFile) {

      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        ModelParser.addComponent(layoutRoot, buttonComponent, null /* add before */);
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

    assertEquals("LinearLayout(LinearLayout(Button, ), ), ", toString(rootComponent));

    RenderResult result = getRenderForFile(psiFile, myRenderContext);
    assertTrue(result.getSession().getResult().isSuccess());

    ModelParser.updateRootComponent(myRenderContext.getConfiguration().getFullConfig(), myModelParser.getRootComponent(),
                                    result.getSession(), myRootView);

    assertEquals("LinearLayout[x=0,y=0,width=768,height=1280](" +
                    "LinearLayout[x=0,y=100,width=768,height=1084](Button[x=0,y=100,width=191,height=96], ), ), ",
                 toBoundsString(rootComponent, myRootView));

  }


  /**
   * Parses the given XML layout file and populates the following fields:
   *  * myModelParser - the model parser which holds the tree representation of the given layout file
   *  * myRenderContext - the render context passed to the renderer. Holds the resultant image.
   *  * myRootView - the Swing root component which serves as a container for the result.
   *
   * @throws Exception
   */
  private XmlFile parseFile(VirtualFile xmlFile) throws Exception {
    XmlFile psiFile = (XmlFile)PsiManager.getInstance(getProject()).findFile(xmlFile);
    assertNotNull(psiFile);
    myModelParser = new ModelParser(getProject(), psiFile);
    Configuration configuration = myConfigurationManager.getConfiguration(xmlFile);
    assertNotNull(configuration);

    myRenderContext = new TestRenderContext(getProject(), myModule, psiFile);
    myRenderContext.setConfiguration(configuration);

    RenderResult result = getRenderForFile(xmlFile, myRenderContext);
    RenderSession session = result.getSession();
    assertNotNull(session);
    assertTrue(session.getResult().isSuccess());
    myRootView = TestRootView.getTestRootView(getProject(), myModule, xmlFile, result);

    myRootView.setRenderedImage(result.getImage());
    myRootView.updateBounds(true);

    RadViewComponent newRootComponent = myModelParser.getRootComponent();

    newRootComponent.setClientProperty(ModelParser.XML_FILE_KEY, xmlFile);
    newRootComponent.setClientProperty(ModelParser.MODULE_KEY, myRenderContext);

    IAndroidTarget target = configuration.getTarget();
    assert target != null; // otherwise, rendering would not have succeeded
    PropertyParser propertyParser = new PropertyParser(myModule, target);
    newRootComponent.setClientProperty(PropertyParser.KEY, propertyParser);
    propertyParser.loadRecursive(newRootComponent);

    myModelParser.updateRootComponent(configuration.getFullConfig(), session, myRootView);

    myBasePanel.add(myRootView);

    return psiFile;
  }

  /**
   * Creates the render service and builds a RenderResult for the given file.
   */
  @NotNull
  private RenderResult getRenderForFile(@NotNull VirtualFile file, RenderContext renderContext) {
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    return getRenderForFile(psiFile, renderContext);
  }

  /**
   * Creates the render service and builds a RenderResult for the given file.
   */
  @NotNull
  private RenderResult getRenderForFile(@NotNull PsiFile psiFile, RenderContext renderContext) {
    IAndroidTarget target = getTestTarget(myConfigurationManager);
    assertNotNull(target);
    myConfigurationManager.setTarget(target);
    Configuration configuration = myConfigurationManager.getConfiguration(psiFile.getVirtualFile());
    assertSame(target, configuration.getTarget());
    RenderLogger logger = new RenderLogger("myLogger", myModule);
    RenderService service = RenderService.create(myFacet, myModule, psiFile, configuration, logger, renderContext);
    assertNotNull(service);
    RenderResult render = RenderTestBase.renderOnSeparateThread(service);
    assertNotNull(render);

    assertFalse(getMessageString(logger), logger.hasProblems());
    return render;
  }

  /**
   * Converts the RenderLogger's list of RenderProblems into a single string.
   */
  @NotNull
  private static String getMessageString(RenderLogger logger) {
    StringBuilder sb = new StringBuilder();
    if (logger.getMessages() == null) {
      return "";
    }
    for (RenderProblem problem : logger.getMessages()) {
      sb.append(problem.getHtml());
      sb.append('\n');
    }
    return sb.toString();
  }
}
