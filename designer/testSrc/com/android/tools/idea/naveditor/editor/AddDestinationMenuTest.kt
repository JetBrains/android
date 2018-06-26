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

import com.android.tools.idea.actions.NewAndroidComponentAction.CREATED_FILES
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.layout
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.google.common.collect.ImmutableList
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.rootManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.awt.event.MouseEvent
import java.io.File
import java.util.stream.Collectors
import javax.swing.JPanel

// TODO: testing with custom navigators
class AddDestinationMenuTest : NavTestCase() {


  private var _model: SyncNlModel? = null
  private val model
    get() = _model!!

  private var _surface: NavDesignSurface? = null
  private val surface
    get() = _surface!!

  private var _menu: AddDestinationMenu? = null
  private val menu
    get() = _menu!!

  private var _panel: JPanel? = null
  private val panel
    get() = _panel!!

  override fun setUp() {
    super.setUp()
    _model = model("nav.xml") {
      navigation("navigation") {
        fragment("fragment")
        navigation("subnav") {
          fragment("fragment2")
        }
      }
    }

    _surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    _menu = AddDestinationMenu(surface)
    _panel = menu.mainPanel
    // We kick of a worker thread to load the destinations and then update the list in the ui thread, so we have to wait and dispatch
    // events until it's set.
    for (i in 0..10) {
      if (!_menu!!.destinationsList.isEmpty) {
        break
      }
      if (i == 10) {
        fail("Failed to create destination list")
      }
      Thread.sleep(10L)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  fun testContent() {
    val virtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity_main2.xml")
    val xmlFile = PsiManager.getInstance(project).findFile(virtualFile!!) as XmlFile

    val parent = model.components[0]
    val expected1 = Destination.RegularDestination(parent, "fragment", null, "BlankFragment", "mytest.navtest.BlankFragment")
    val expected2 = Destination.RegularDestination(parent, "activity", null, "MainActivity", "mytest.navtest.MainActivity",
        layoutFile = xmlFile)
    val expected3 = Destination.IncludeDestination("navigation.xml", parent)
    assertSameElements(AddDestinationMenu(surface).destinations, ImmutableList.of(expected1, expected2, expected3))
  }

  override fun tearDown() {
    _model = null
    _menu = null
    _surface = null
    super.tearDown()
  }

  fun testNewComponentSelected() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
    assertNotNull(destination.component)
    assertEquals(listOf(destination.component!!), surface.selectionModel.selection)
  }

  fun testFiltering() {
    val gallery = menu.destinationsList
    val searchField = menu.searchField

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

  fun testCaching() {
    val group = DefaultActionGroup()
    surface.actionManager.addActions(group, null, null, listOf<NlComponent>(), true)
    val addDestinationMenu = group.getChildren(null)[0] as AddDestinationMenu
    val panel = addDestinationMenu.mainPanel
    // get it again and check that it's the same instance
    assertSame(panel, addDestinationMenu.mainPanel)

    myFixture.addClass("class Foo extends android.app.Fragment {}")
    UIUtil.dispatchAllInvocationEvents()

    assertNotSame(panel, addDestinationMenu.mainPanel)
    addDestinationMenu.destinations.first { it.label == "Foo" }
  }


  fun testCreateBlank() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object: AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl.MyDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.app.Fragment {}")
        myFixture.addFileToProject("res/layout/frag_layout.xml", "")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
        createdFiles.add(File(root, "res/layout/frag_layout.xml"))
      }
    }
    menu.createBlankDestination(event, action)

    val added = model.find("frag")!!
    assertEquals("fragment", added.tagName)
    assertEquals("@layout/frag_layout", added.layout)
    assertEquals("mytest.navtest.Frag", added.className)
  }

  fun testCreateBlankNoLayout() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object: AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl.MyDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.app.Fragment {}")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
      }
    }
    menu.createBlankDestination(event, action)

    val added = model.find("frag")!!
    assertEquals("fragment", added.tagName)
    assertNull(added.layout)
    assertEquals("mytest.navtest.Frag", added.className)
  }

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
