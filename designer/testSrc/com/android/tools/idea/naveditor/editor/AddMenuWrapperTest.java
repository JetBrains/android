/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.editor;

import com.android.SdkConstants;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

public class AddMenuWrapperTest extends NavigationTestCase {

  private SyncNlModel myModel;
  private NavDesignSurface mySurface;
  private AddMenuWrapper myMenu;


  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = model("nav.xml",
                    component(TAG_NAVIGATION).unboundedChildren(
                      component(TAG_FRAGMENT).id("@id/fragment1"),
                      component(TAG_NAVIGATION).id("@id/subnav")
                        .unboundedChildren(component(TAG_FRAGMENT).id("@id/fragment2"))))
      .build();
    mySurface = new NavDesignSurface(getProject(), getTestRootDisposable());
    mySurface.setSize(1000, 1000);
    mySurface.setModel(myModel);
    myMenu = new AddMenuWrapper(mySurface, ImmutableList.of());
    myMenu.createCustomComponentPopup();
  }

  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    myMenu = null;
    super.tearDown();
  }

  public void testAddFromDestination() throws Exception {
    PsiFile layout = LocalResourceManager.getInstance(myAndroidFacet.getModule()).findResourceFiles(
      ResourceFolderType.LAYOUT).stream().filter(file -> file.getName().equals("activity_main.xml")).findFirst().get();
    NavActionManager.Destination destination = new NavActionManager.Destination(
      (XmlFile)layout, "MainActivity", "mytest.navtest.MainActivity", "activity", null);
    myMenu.addElement(destination, mySurface);
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<navigation>, instance=2}\n" +
                 "        NlComponent{tag=<fragment>, instance=3}\n" +
                 "    NlComponent{tag=<activity>, instance=4}",
                 new NlTreeDumper().toTree(myModel.getComponents()));
    NlComponent newChild = myModel.getComponents().get(0).getChild(2);
    assertEquals(SdkConstants.LAYOUT_RESOURCE_PREFIX + "activity_main",
                 newChild.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT));
    assertEquals("mytest.navtest.MainActivity",
                 newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME));
    assertEquals("@+id/mainActivity",
                 newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID));
  }

  public void testAdd() throws Exception {
    myMenu.addElement(mySurface, "myTag", "myId", "myName", component -> component.setAttribute("ns", "attr", "value"));
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<navigation>, instance=2}\n" +
                 "        NlComponent{tag=<fragment>, instance=3}\n" +
                 "    NlComponent{tag=<myTag>, instance=4}",
                 new NlTreeDumper().toTree(myModel.getComponents()));
    NlComponent newChild = myModel.getComponents().get(0).getChild(2);
    assertEquals("myName", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME));
    assertEquals("@+id/myId", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID));
    assertEquals("value", newChild.getAttribute("ns", "attr"));
  }

  public void testFragmentValidation() throws Exception {
    myMenu.myKindPopup.setSelectedItem("fragment");
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");
    myMenu.myClassPopup.setSelectedItem("mytest.navtest.BlankFragment");
    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myIdField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myIdField.setText("myId");

    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myLabelField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myLabelField.setText("myLabel");

    myMenu.myClassPopup.setSelectedItem(null);
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
  }

  public void testActivityValidation() throws Exception {
    myMenu.myKindPopup.setSelectedItem("activity");
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");
    myMenu.myClassPopup.setSelectedItem("mytest.navtest.MainActivity");
    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myIdField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myIdField.setText("myId");

    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myLabelField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myLabelField.setText("myLabel");

    myMenu.myClassPopup.setSelectedItem(null);
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
  }

  public void testNestedValidation() throws Exception {
    myMenu.myKindPopup.setSelectedItem("navigation");
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");
    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myIdField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myIdField.setText("myId");

    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myLabelField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
  }

  public void testIncludeValidation() throws Exception {
    myMenu.myKindPopup.setSelectedItem("include");
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");
    // Not possible to have an invalid value in "source"

    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myIdField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
    myMenu.myIdField.setText("myId");

    assertTrue(myMenu.validate());
    assertFalse(myMenu.myValidationLabel.isVisible());

    myMenu.myLabelField.setText("");
    assertFalse(myMenu.validate());
    assertTrue(myMenu.myValidationLabel.isVisible());
  }

  public void testVisibleComponents() throws Exception {
    myMenu.myKindPopup.setSelectedItem("fragment");
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myClassPopup.isVisible());
    assertTrue(myMenu.myClassLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem("navigation");
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertFalse(myMenu.myClassPopup.isVisible());
    assertFalse(myMenu.myClassLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem("include");
    assertTrue(myMenu.mySourcePopup.isVisible());
    assertTrue(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertFalse(myMenu.myClassPopup.isVisible());
    assertFalse(myMenu.myClassLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem("activity");
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myClassPopup.isVisible());
    assertTrue(myMenu.myClassLabel.isVisible());
  }

  public void testCreateNested() throws Exception {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem("navigation");
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    Mockito.verify(menu).addElement(mySurface, "navigation", "myId", "myLabel", null);
  }

  public void testCreateFragment() throws Exception {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem("fragment");
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.myClassPopup.setSelectedItem("mytest.navtest.BlankFragment");

    menu.createDestination();

    Mockito.verify(menu)
      .addElement(new NavActionManager.Destination(null, "BlankFragment", "mytest.navtest.BlankFragment", "fragment", null), mySurface);
  }

  public void testCreateActivity() throws Exception {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem("activity");
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.myClassPopup.setSelectedItem("mytest.navtest.MainActivity");

    menu.createDestination();

    Mockito.verify(menu)
      .addElement(new NavActionManager.Destination(null, "MainActivity", "mytest.navtest.MainActivity", "activity", null), mySurface);
  }

  public void testCreateInclude() throws Exception {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem("include");
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.mySourcePopup.setSelectedItem("othernav.xml");

    menu.createDestination();

    Mockito.verify(menu)
      .addElement(eq(mySurface), eq("include"), eq("myId"), eq("myLabel"), any(Consumer.class));
  }
}
