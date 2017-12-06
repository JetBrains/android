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
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ResourceUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import sun.awt.image.ToolkitImage;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

// TODO: testing with custom navigators
public class AddMenuWrapperTest extends NavTestCase {

  private SyncNlModel myModel;
  private NavDesignSurface mySurface;
  private AddMenuWrapper myMenu;

  private final Map<NavigationSchema.DestinationType, Pair<String, PsiClass>> myItemsByType = new HashMap<>();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = model("nav.xml",
                    rootComponent("navigation").unboundedChildren(
                      fragmentComponent("fragment1"),
                      navigationComponent("subnav")
                        .unboundedChildren(fragmentComponent("fragment2"))))
      .build();
    mySurface = new NavDesignSurface(getProject(), myRootDisposable);
    mySurface.setSize(1000, 1000);
    mySurface.setModel(myModel);
    myMenu = new AddMenuWrapper(mySurface, ImmutableList.of());
    myMenu.createCustomComponentPopup();

    NavigationSchema schema = NavigationSchema.getOrCreateSchema(myFacet);
    ComboBoxModel<Pair<String, PsiClass>> model = myMenu.myKindPopup.getModel();
    for (int i = 0; i < model.getSize(); i++) {
      Pair<String, PsiClass> entry = model.getElementAt(i);
      PsiClass psiClass = entry.getSecond();
      myItemsByType.put(psiClass == null ? null : schema.getTypeForNavigatorClass(psiClass), entry);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    myMenu = null;
    super.tearDown();
  }

  public void testAddFromDestination() {
    PsiFile layout = LocalResourceManager.getInstance(myFacet.getModule()).findResourceFiles(
      ResourceFolderType.LAYOUT).stream().filter(file -> file.getName().equals("activity_main.xml")).findFirst().get();
    NavActionManager.Destination destination = new NavActionManager.Destination(
      (XmlFile)layout, "MainActivity", "mytest.navtest.MainActivity", "activity", null);
    myMenu.addElement(destination, mySurface, null, null);
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

  public void testAddDirectly() {
    myMenu.addElement(mySurface, "myTag", "myId", "myClassName", "myLabel", component -> component.setAttribute("ns", "attr", "value"));
    assertEquals("NlComponent{tag=<navigation>, instance=0}\n" +
                 "    NlComponent{tag=<fragment>, instance=1}\n" +
                 "    NlComponent{tag=<navigation>, instance=2}\n" +
                 "        NlComponent{tag=<fragment>, instance=3}\n" +
                 "    NlComponent{tag=<myTag>, instance=4}",
                 new NlTreeDumper().toTree(myModel.getComponents()));
    NlComponent newChild = myModel.find("myId");
    assertEquals(ImmutableList.of(newChild), mySurface.getSelectionModel().getSelection());
    assertEquals("myClassName", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME));
    assertEquals("@+id/myId", newChild.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ID));
    assertEquals("value", newChild.getAttribute("ns", "attr"));
  }

  public void testFragmentValidation() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
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

  public void testActivityValidation() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.ACTIVITY));
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

  public void testNestedValidation() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.NAVIGATION));
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

  public void testIncludeValidation() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(null));
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

  public void testVisibleComponents() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.NAVIGATION));
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    assertTrue(myMenu.mySourcePopup.isVisible());
    assertTrue(myMenu.mySourceLabel.isVisible());
    assertFalse(myMenu.myIdField.isVisible());
    assertFalse(myMenu.myLabelField.isVisible());
    assertFalse(myMenu.myLabelLabel.isVisible());
    assertFalse(myMenu.myIdLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.ACTIVITY));
    assertFalse(myMenu.mySourcePopup.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());
  }

  public void testCreateNested() {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.NAVIGATION));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    Mockito.verify(menu).addElement(mySurface, "navigation", "myId", null, "myLabel", null);
  }

  public void testCreateFragment() {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    Mockito.verify(menu)
      .addElement(new NavActionManager.Destination(null, "", "", "fragment", null), mySurface,
                  "myId", "myLabel");
  }

  public void testCreateActivity() {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.ACTIVITY));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    Mockito.verify(menu)
      .addElement(new NavActionManager.Destination(null, "", "", "activity", null), mySurface,
                  "myId", "myLabel");
  }

  public void testCreateInclude() {
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.mySourcePopup.setSelectedItem("navigation.xml");

    menu.createDestination();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<NlComponent>> consumerArg = ArgumentCaptor.forClass(Consumer.class);
    Mockito.verify(menu)
      .addElement(eq(mySurface), eq("include"), eq("navigation"), isNull(), eq("myLabel"), consumerArg.capture());

    NlComponent component = Mockito.mock(NlComponent.class);
    consumerArg.getValue().accept(component);
    Mockito.verify(component).setAttribute(AUTO_URI, "graph", "@navigation/navigation");
  }

  public void testUniqueId() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertEquals("fragment", myMenu.myIdField.getText());
    myMenu.createDestination();
    myMenu = new AddMenuWrapper(mySurface, ImmutableList.of());
    myMenu.createCustomComponentPopup();
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertEquals("fragment2", myMenu.myIdField.getText());
  }

  public void testImageLoading() throws Exception {
    Lock lock = new ReentrantLock();
    lock.lock();

    // use createImage so the instances are different
    ToolkitImage image = (ToolkitImage)Toolkit.getDefaultToolkit().createImage(
      ResourceUtil.getResource(AndroidIcons.class, "/icons/naveditor", "basic-activity.png"));
    image.preload((img, infoflags, x, y, width, height) -> {
      lock.lock();
      return false;
    });

    MediaTracker tracker = new MediaTracker(new JPanel());
    tracker.addImage(image, 0);

    NavActionManager.Destination dest = new NavActionManager.Destination(null, "foo", "foo", "fragment", image);

    AddMenuWrapper menu = new AddMenuWrapper(mySurface, ImmutableList.of(dest));
    menu.createCustomComponentPopup();
    assertTrue(menu.myLoadingPanel.isLoading());
    lock.unlock();
    tracker.waitForAll();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertFalse(menu.myLoadingPanel.isLoading());

    // Now images are loaded, make sure a new menu doesn't even have the loading panel
    menu = new AddMenuWrapper(mySurface, ImmutableList.of(dest));
    menu.createCustomComponentPopup();
    assertNull(menu.myLoadingPanel);
  }

  public void testKindPopup() {
    ComboBox<Pair<String, PsiClass>> popup = myMenu.myKindPopup;
    ListCellRenderer<? super Pair<String, PsiClass>> renderer = popup.getRenderer();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < popup.getItemCount(); i++) {
      result.add(((JLabel)renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false)).getText());
    }
    assertEquals(ImmutableSet.of("Include Graph", "Nested Graph", "Fragment", "Activity"), result);
    assertEquals(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT), popup.getSelectedItem());
  }

  public void testSourcePopup() {
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "  android:id=\"@+id/nav2\">\n" +
                    "</navigation>\n";
    myFixture.addFileToProject("res/navigation/nav2.xml", source);
    ComboBox<String> popup = myMenu.mySourcePopup;
    ListCellRenderer<? super String> renderer = popup.getRenderer();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < popup.getItemCount(); i++) {
      result.add(((JLabel)renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false)).getText());
    }
    assertEquals(ImmutableSet.of("navigation.xml", "New..."), result);
  }
}
