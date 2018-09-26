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

import com.android.SdkConstants
import com.android.tools.idea.actions.NewAndroidComponentAction.CREATED_FILES
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.TestNlEditor
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.layout
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.ide.impl.DataManagerImpl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.rootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
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

  private var _modelBuilder: ModelBuilder? = null
  private val modelBuilder
    get() = _modelBuilder!!

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

  private var _root: NavModelBuilderUtil.NavigationComponentDescriptor? = null
  private val root
    get() = _root!!

  override fun setUp() {
    super.setUp()
    _modelBuilder = modelBuilder("nav.xml") {
      navigation("navigation") {
        fragment("fragment")
        navigation("subnav") {
          fragment("fragment2")
        }
      }.also { _root = it }
    }
    _model = modelBuilder.build()

    _surface = NavDesignSurface(project, myRootDisposable)
    surface.setSize(1000, 1000)
    surface.model = model
    _menu = AddDestinationMenu(surface)
    _panel = menu.mainPanel
    // We kick off a worker thread to load the destinations and then update the list in the ui thread, so we have to wait and dispatch
    // events until it's set.
    while (true) {
      if (!_menu!!.destinationsList.isEmpty) {
        break
      }
      Thread.sleep(10L)
      PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
  }

  fun testContent() {
    val virtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity_main2.xml")
    val xmlFile = PsiManager.getInstance(project).findFile(virtualFile!!) as XmlFile

    addFragment("fragment1")
    addFragment("fragment3")
    addFragment("fragment2")

    addActivity("activity2")
    addActivityWithLayout("activity3")
    val activity3VirtualFile = project.baseDir.findFileByRelativePath("../unitTest/res/layout/activity3.xml")
    val activity3XmlFile = PsiManager.getInstance(project).findFile(activity3VirtualFile!!) as XmlFile

    addActivityWithNavHost("activity1")

    addIncludeFile("include3")
    addIncludeFile("include2")
    addIncludeFile("include1")

    val parent = model.components[0]
    val expected = mutableListOf(
      Destination.PlaceholderDestination(parent),
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.BlankFragment")),
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment1")),
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment2")),
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment3")),
      Destination.IncludeDestination("include1.xml", parent),
      Destination.IncludeDestination("include2.xml", parent),
      Destination.IncludeDestination("include3.xml", parent),
      Destination.IncludeDestination("navigation.xml", parent),
      Destination.RegularDestination(parent, "activity", null, findClass("mytest.navtest.activity2")),
      Destination.RegularDestination(parent, "activity", null, findClass("mytest.navtest.activity3"), layoutFile = activity3XmlFile),
      Destination.RegularDestination(parent, "activity", null, findClass("mytest.navtest.MainActivity"), layoutFile = xmlFile))

    var destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)

    root.include("include1")
    modelBuilder.updateModel(model)
    model.notifyModified(NlModel.ChangeType.EDIT)

    expected.removeAt(5)
    destinations = AddDestinationMenu(surface).destinations
    assertEquals(destinations, expected)
  }

  fun findClass(className: String) = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))!!

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

  fun testUndoNewComponent() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))

    // ordinarily this would be done by the resource change listener
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertNotNull(destination.component)
    assertEquals(3, surface.currentNavigation.children.size)

    UndoManager.getInstance(project).undo(TestNlEditor(model.virtualFile, project))

    PsiDocumentManager.getInstance(project).commitAllDocuments()
    model.notifyModified(NlModel.ChangeType.EDIT)

    assertEquals(2, surface.currentNavigation.children.size)
  }

  fun testFiltering() {
    val gallery = menu.destinationsList
    val searchField = menu.searchField

    assertEquals(4, gallery.itemsCount)
    assertEquals("placeholder", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("BlankFragment", (gallery.model.getElementAt(1) as Destination).label)
    assertEquals("navigation.xml", (gallery.model.getElementAt(2) as Destination).label)
    assertEquals("activity_main2", (gallery.model.getElementAt(3) as Destination).label)

    searchField.text = "v"
    assertEquals(2, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("activity_main2", (gallery.model.getElementAt(1) as Destination).label)

    searchField.text = "vig"
    assertEquals(1, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
  }

  fun testCaching() {
    val group = DefaultActionGroup()
    surface.actionManager.addActions(group, null, listOf<NlComponent>(), true)
    val addDestinationMenu = group.getChildren(null)[0] as AddDestinationMenu
    val panel = addDestinationMenu.mainPanel
    // get it again and check that it's the same instance
    assertSame(panel, addDestinationMenu.mainPanel)

    myFixture.addClass("class Foo extends android.support.v4.app.Fragment {}")
    UIUtil.dispatchAllInvocationEvents()

    assertNotSame(panel, addDestinationMenu.mainPanel)
    addDestinationMenu.destinations.first { it.label == "Foo" }
  }


  fun testCreateBlank() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl.MyDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.support.v4.app.Fragment {}")
        myFixture.addFileToProject("res/layout/frag_layout.xml", "")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
        createdFiles.add(File(root, "res/layout/frag_layout.xml"))
      }
    }
    menu.createNewDestination(event, action)

    val added = model.find("frag")!!
    assertEquals("fragment", added.tagName)
    assertEquals("@layout/frag_layout", added.layout)
    assertEquals("mytest.navtest.Frag", added.className)
  }

  fun testCreateBlankNoLayout() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val event = mock(AnActionEvent::class.java)
    `when`(event.project).thenReturn(project)
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        TestCase.assertEquals(event, e)
        val createdFiles = DataManagerImpl.MyDataContext(panel).getData(CREATED_FILES)!!
        val root = myModule.rootManager.contentRoots[0].path
        myFixture.addFileToProject("src/mytest/navtest/Frag.java",
                                   "package mytest.navtest\n" +
                                   "public class Frag extends android.support.v4.app.Fragment {}")
        createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
      }
    }
    menu.createNewDestination(event, action)

    val added = model.find("frag")!!
    assertEquals("fragment", added.tagName)
    assertNull(added.layout)
    assertEquals("mytest.navtest.Frag", added.className)
  }

  fun testCreatePlaceholder() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(1, 1)
    val destination = gallery.model.getElementAt(1) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
    val component = destination.component
    assertNotNull(component)
    assertEquals(listOf(component!!), surface.selectionModel.selection)
    assertNull(component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
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

  private fun addFragment(name: String) {
    addDestination(name, "android.support.v4.app.Fragment")
  }

  private fun addActivityWithNavHost(name: String) {
    addActivity(name)
    val relativePath = "res/layout/$name.xml"
    val fileText = """
      <?xml version="1.0" encoding="utf-8"?>
      <android.support.constraint.ConstraintLayout xmlns:tools="http://schemas.android.com/tools"
                                                   xmlns:app="http://schemas.android.com/apk/res-auto"
                                                   xmlns:android="http://schemas.android.com/apk/res/android"
                                                   tools:context=".$name">
        <fragment
            android:id="@+id/navhost"
            android:name="androidx.navigation.fragment.NavHostFragment"
            app:defaultNavHost="true"
            app:navGraph="@navigation/nav"/>

      </android.support.constraint.ConstraintLayout>
    """.trimIndent()

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivityWithLayout(name: String) {
    addActivity(name)
    val relativePath = "res/layout/$name.xml"
    val fileText = """
      <?xml version="1.0" encoding="utf-8"?>
      <android.support.constraint.ConstraintLayout xmlns:tools="http://schemas.android.com/tools"
                                                   tools:context=".$name"/>
    """.trimIndent()

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivity(name: String) {
    addDestination(name, "android.app.Activity")
  }

  private fun addDestination(name: String, parentClass: String) {
    val relativePath = "src/mytest/navtest/$name.java"
    val fileText = """
      .package mytest.navtest;
      .import $parentClass;
      .
      .public class $name extends ${parentClass.substringAfterLast('.')} {
      .}
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }

  private fun addIncludeFile(name: String) {
    val relativePath = "res/navigation/$name.xml"
    val fileText = """
      .<?xml version="1.0" encoding="utf-8"?>
      .<navigation xmlns:android="http://schemas.android.com/apk/res/android"
          .xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/testnav">

      .</navigation>
      """.trimMargin(".")

    myFixture.addFileToProject(relativePath, fileText)
  }
}
