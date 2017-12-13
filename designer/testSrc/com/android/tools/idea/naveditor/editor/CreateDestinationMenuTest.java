// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.naveditor.editor;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.psi.PsiClass;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.ATTR_LABEL;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;

// TODO: testing with custom navigators
public class CreateDestinationMenuTest extends NavTestCase {

  private SyncNlModel myModel;
  private NavDesignSurface mySurface;
  private CreateDestinationMenu myMenu;

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
    myMenu = new CreateDestinationMenu(mySurface);
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
    // TODO: implement create new included graph
    /*
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");
    // TODO: validate "source"

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
    */
  }

  public void testVisibleComponents() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.ACTIVITY));
    assertFalse(myMenu.mySourceField.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertFalse(myMenu.mySourceField.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());

    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.NAVIGATION));
    assertFalse(myMenu.mySourceField.isVisible());
    assertFalse(myMenu.mySourceLabel.isVisible());
    assertTrue(myMenu.myIdField.isVisible());
    assertTrue(myMenu.myLabelField.isVisible());
    assertTrue(myMenu.myLabelLabel.isVisible());
    assertTrue(myMenu.myIdLabel.isVisible());

    // TODO: implement create "include"
    /*
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    assertTrue(myMenu.mySourceField.isVisible());
    assertTrue(myMenu.mySourceLabel.isVisible());
    assertFalse(myMenu.myIdField.isVisible());
    assertFalse(myMenu.myLabelField.isVisible());
    assertFalse(myMenu.myLabelLabel.isVisible());
    assertFalse(myMenu.myIdLabel.isVisible());
    */
  }

  public void testCreateNested() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.NAVIGATION));
    myMenu.myIdField.setText("myId");
    myMenu.myLabelField.setText("myLabel");

    myMenu.createDestination();

    NlComponent added = myModel.find("myId");
    assertEquals(TAG_NAVIGATION, added.getTagName());
    assertEquals("myLabel", added.getAttribute(AUTO_URI, ATTR_LABEL));
  }

  public void testCreateFragment() {
    CreateDestinationMenu menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    NlComponent added = myModel.find("myId");
    assertEquals("fragment", added.getTagName());
    assertEquals("myLabel", added.getAttribute(AUTO_URI, ATTR_LABEL));
  }

  public void testCreateActivity() {
    CreateDestinationMenu menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.ACTIVITY));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");

    menu.createDestination();

    NlComponent added = myModel.find("myId");
    assertEquals("activity", added.getTagName());
    assertEquals("myLabel", added.getAttribute(AUTO_URI, ATTR_LABEL));
    assertEquals(ImmutableList.of(added), mySurface.getSelectionModel().getSelection());
  }

  public void testCreateInclude() {
    // TODO: implement create new included graph
    /*
    AddMenuWrapper menu = Mockito.spy(myMenu);

    menu.myKindPopup.setSelectedItem(myItemsByType.get(null));
    menu.myIdField.setText("myId");
    menu.myLabelField.setText("myLabel");
    menu.mySourceField.setSelectedItem("navigation.xml");

    menu.createDestination();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Consumer<NlComponent>> consumerArg = ArgumentCaptor.forClass(Consumer.class);
    Mockito.verify(menu)
      .addElement(eq(mySurface), eq("include"), eq("navigation"), isNull(), eq("myLabel"), consumerArg.capture());

    NlComponent component = Mockito.mock(NlComponent.class);
    consumerArg.getValue().accept(component);
    Mockito.verify(component).setAttribute(AUTO_URI, "graph", "@navigation/navigation");
    */
  }

  public void testUniqueId() {
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertEquals("fragment", myMenu.myIdField.getText());
    myMenu.createDestination();
    myMenu = new CreateDestinationMenu(mySurface);
    myMenu.createCustomComponentPopup();
    myMenu.myKindPopup.setSelectedItem(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT));
    assertEquals("fragment2", myMenu.myIdField.getText());
  }

  public void testImageLoading() {
    // TODO: implement thumbnails for destinations
    /*
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

    Destination dest = new Destination.RegularDestination(mySurface.getCurrentNavigation(), "fragment", null, "foo", "foo");

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
    */
  }

  public void testKindPopup() {
    ComboBox<Pair<String, PsiClass>> popup = myMenu.myKindPopup;
    ListCellRenderer<? super Pair<String, PsiClass>> renderer = popup.getRenderer();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < popup.getItemCount(); i++) {
      result.add(((JLabel)renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false)).getText());
    }
    assertEquals(ImmutableSet.of(/*"Include Graph",*/ "Nested Graph", "Fragment", "Activity"), result);
    assertEquals(myItemsByType.get(NavigationSchema.DestinationType.FRAGMENT), popup.getSelectedItem());
  }

  public void testSourcePopup() {
    // TODO: implement create new included graph
    /*
    @Language("XML")
    String source = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                    "  android:id=\"@+id/nav2\">\n" +
                    "</navigation>\n";
    myFixture.addFileToProject("res/navigation/nav2.xml", source);
    ComboBox<String> popup = myMenu.mySourceField;
    ListCellRenderer<? super String> renderer = popup.getRenderer();
    Set<String> result = new HashSet<>();
    for (int i = 0; i < popup.getItemCount(); i++) {
      result.add(((JLabel)renderer.getListCellRendererComponent(null, popup.getItemAt(i), i, false, false)).getText());
    }
    assertEquals(ImmutableSet.of("navigation.xml", "New..."), result);*/
  }
}
