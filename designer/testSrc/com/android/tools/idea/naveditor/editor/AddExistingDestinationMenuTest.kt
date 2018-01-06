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
package com.android.tools.idea.naveditor.editor

import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import java.awt.event.MouseEvent

// TODO: testing with custom navigators
class AddExistingDestinationMenuTest : NavTestCase() {


  private var _model: SyncNlModel? = null
  private val model
    get() = _model!!

  private var _surface: NavDesignSurface? = null
  private val surface
    get() = _surface!!

  private var _menu: AddExistingDestinationMenu? = null
  private val menu
    get() = _menu!!

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    _model = model("nav.xml") {
      navigation("navigation") {
        fragment("fragment1")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }

    _surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    _menu = AddExistingDestinationMenu(surface, NavActionManager(surface).destinations)
    menu.createCustomComponentPopup()
  }

  @Throws(Exception::class)
  override fun tearDown() {
    _model = null
    _menu = null
    _surface = null
    super.tearDown()
  }

  fun testNewComponentSelected() {
    val gallery = menu.myDestinationsGallery
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.selectedElement = destination
    gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
    assertNotNull(destination.component)
    assertEquals(listOf(destination.component!!), surface.selectionModel.selection)
  }

  fun testFiltering() {
    val gallery = menu.myDestinationsGallery
    val searchField = menu.mySearchField

    assertEquals(3, gallery.itemsCount)
    assertEquals("activity_main2", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("BlankFragment", (gallery.model.getElementAt(1) as Destination).label)
    assertEquals("navigation.xml", (gallery.model.getElementAt(2) as Destination).label)

    searchField.text = "v"
    assertEquals(2, gallery.itemsCount)
    assertEquals("activity_main2", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("navigation.xml", (gallery.model.getElementAt(1) as Destination).label)

    searchField.text = "vig"
    assertEquals(1, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
  }

  @Throws(Exception::class)
  fun testImageLoading() {
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

    Destination dest = new Destination.RegularDestination(surface.getCurrentNavigation(), "fragment", null, "foo", "foo");

    AddMenuWrapper menu = new AddMenuWrapper(surface, ImmutableList.of(dest));
    menu.createCustomComponentPopup();
    assertTrue(menu.myLoadingPanel.isLoading());
    lock.unlock();
    tracker.waitForAll();
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
    assertFalse(menu.myLoadingPanel.isLoading());

    // Now images are loaded, make sure a new menu doesn't even have the loading panel
    menu = new AddMenuWrapper(surface, ImmutableList.of(dest));
    menu.createCustomComponentPopup();
    assertNull(menu.myLoadingPanel);
    */
  }
}
