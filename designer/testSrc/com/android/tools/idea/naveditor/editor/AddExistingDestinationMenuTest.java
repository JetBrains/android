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

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.SearchTextField;

import java.awt.*;
import java.awt.event.MouseEvent;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;

// TODO: testing with custom navigators
public class AddExistingDestinationMenuTest extends NavTestCase {

  private SyncNlModel myModel;
  private NavDesignSurface mySurface;
  private AddExistingDestinationMenu myMenu;

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
    myMenu = new AddExistingDestinationMenu(mySurface, new NavActionManager(mySurface).getDestinations());
    myMenu.createCustomComponentPopup();
  }

  @Override
  protected void tearDown() throws Exception {
    myModel = null;
    myMenu = null;
    super.tearDown();
  }

  public void testNewComponentSelected() {
    ASGallery<Destination> gallery = myMenu.myDestinationsGallery;
    Rectangle cell0Bounds = gallery.getCellBounds(0, 0);
    Destination destination = (Destination)gallery.getModel().getElementAt(0);
    gallery.setSelectedElement(destination);
    gallery.dispatchEvent(new MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      (int)cell0Bounds.getCenterX(), (int)cell0Bounds.getCenterX(), 1, false));
    assertNotNull(destination.getComponent());
    assertEquals(ImmutableList.of(destination.getComponent()), mySurface.getSelectionModel().getSelection());
  }

  public void testFiltering() {
    ASGallery<Destination> gallery = myMenu.myDestinationsGallery;
    SearchTextField searchField = myMenu.mySearchField;

    assertEquals(3, gallery.getItemsCount());
    assertEquals("activity_main2", ((Destination)gallery.getModel().getElementAt(0)).getLabel());
    assertEquals("BlankFragment", ((Destination)gallery.getModel().getElementAt(1)).getLabel());
    assertEquals("navigation.xml", ((Destination)gallery.getModel().getElementAt(2)).getLabel());

    searchField.setText("v");
    assertEquals(2, gallery.getItemsCount());
    assertEquals("activity_main2", ((Destination)gallery.getModel().getElementAt(0)).getLabel());
    assertEquals("navigation.xml", ((Destination)gallery.getModel().getElementAt(1)).getLabel());

    searchField.setText("vig");
    assertEquals(1, gallery.getItemsCount());
    assertEquals("navigation.xml", ((Destination)gallery.getModel().getElementAt(0)).getLabel());
  }

  public void testImageLoading() throws Exception {
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
}
