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

import com.android.AndroidProjectTypes.PROJECT_TYPE_LIBRARY
import com.android.SdkConstants
import com.android.SdkConstants.ATTR_MODULE_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.testutils.MockitoKt
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.ChangeType
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.naveditor.NavEditorRule
import com.android.tools.idea.naveditor.NavModelBuilderUtil
import com.android.tools.idea.naveditor.NavModelBuilderUtil.navigation
import com.android.tools.idea.naveditor.NavTestCase
import com.android.tools.idea.naveditor.NavTestCase.Companion.findVirtualProjectFile
import com.android.tools.idea.naveditor.TestNavEditor
import com.android.tools.idea.naveditor.addDynamicFeatureModule
import com.android.tools.idea.naveditor.analytics.TestNavUsageTracker
import com.android.tools.idea.naveditor.model.className
import com.android.tools.idea.naveditor.model.layout
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.NavDestinationInfo
import com.google.wireless.android.sdk.stats.NavDestinationInfo.DestinationType.FRAGMENT
import com.google.wireless.android.sdk.stats.NavEditorEvent
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ADD_DESTINATION
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.ADD_INCLUDE
import com.google.wireless.android.sdk.stats.NavEditorEvent.NavEditorEventType.CREATE_FRAGMENT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.rootManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertContainsElements
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory
import com.intellij.testFramework.fixtures.TestFixtureBuilder
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import java.awt.event.MouseEvent
import java.io.File
import java.util.stream.Collectors

// TODO: testing with custom navigators
@RunsInEdt
class AddDestinationMenuTest {
  @get:Rule
  val edtRule = EdtRule()

  private val disposableRule = DisposableRule()
  private val projectRule = AndroidProjectRule.withSdk()
  private val navRule = NavEditorRule(projectRule)
  @get:Rule
  val chain = RuleChain.outerRule(projectRule).around(navRule).around(disposableRule)!!

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

  private var _root: NavModelBuilderUtil.NavigationComponentDescriptor? = null
  private val root
    get() = _root!!

  @Before
  fun setUp() {
      val fixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectRule.fixture,
                                                                                        projectRule.fixture.tempDirFixture)
      addDynamicFeatureModule("dynamicfeaturemodule", projectRule.module, fixture)
      _modelBuilder = navRule.modelBuilder("nav.xml") {
        navigation("navigation") {
          fragment("fragment")
          navigation("subnav") {
            navigation("subnav2") {
              fragment("fragment2")
            }
          }
        }.also { _root = it }
      }
      _model = modelBuilder.build()

      _surface = NavDesignSurface(projectRule.project, disposableRule.disposable)
      surface.setSize(1000, 1000)
      PlatformTestUtil.waitForFuture(surface.setModel(model))
      _menu = AddDestinationMenu(surface)
      setupMainMenuPanel()
  }

  @After
  fun tearDown() {
    _model = null
    _menu = null
    _surface = null
  }

  @Test
  fun testContent() {
    val virtualFile = findVirtualProjectFile(projectRule.project, "res/layout/activity_main.xml")
    val xmlFile = PsiManager.getInstance(projectRule.project).findFile(virtualFile!!) as XmlFile

    addFragment("fragment1")
    addFragment("fragment3")
    addFragment("fragment2")
    addFragment("DynamicFragment", "dynamicfeaturemodule")

    addActivity("activity2")
    addActivityWithLayout("activity3")
    val activity3VirtualFile = findVirtualProjectFile(projectRule.project, "res/layout/activity3.xml")
    val activity3XmlFile = PsiManager.getInstance(projectRule.project).findFile(activity3VirtualFile!!) as XmlFile

    addActivityWithNavHost("activity1")

    addIncludeFile("include3")
    addIncludeFile("include2")
    addIncludeFile("include1")

    addDestination("NavHostFragmentChild", "androidx.navigation.fragment.NavHostFragment")

    val parent = model.components[0]

    val placeHolder = Destination.PlaceholderDestination(parent)

    val blankFragment =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.BlankFragment"))
    val dynamicFragment =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.DynamicFragment"),
                                     dynamicModuleName = "dynamicfeaturemodule")
    val fragment1 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment1"))
    val fragment2 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment2"))
    val fragment3 =
      Destination.RegularDestination(parent, "fragment", null, findClass("mytest.navtest.fragment3"))

    val include1 = Destination.IncludeDestination("include1.xml", parent)
    val include2 = Destination.IncludeDestination("include2.xml", parent)
    val include3 = Destination.IncludeDestination("include3.xml", parent)
    val includeNav = Destination.IncludeDestination("navigation.xml", parent)

    val activity2 =
      Destination.RegularDestination(parent, "activity", null, findClass("mytest.navtest.activity2"))
    val activity3 = Destination.RegularDestination(
      parent, "activity", null, findClass("mytest.navtest.activity3"), layoutFile = activity3XmlFile)
    val mainActivity = Destination.RegularDestination(
      parent, "activity", null, findClass("mytest.navtest.MainActivity"), layoutFile = xmlFile)
    waitForResourceRepositoryUpdates(projectRule.module.androidFacet!!)

    val expected = mutableListOf(placeHolder, blankFragment, dynamicFragment, fragment1, fragment2, fragment3, include1, include2, include3,
                                 includeNav, activity2, activity3, mainActivity)

    var destinations = AddDestinationMenu(surface).destinations

    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()

    root.include("include1")
    modelBuilder.updateModel(model)
    model.notifyModified(ChangeType.EDIT)

    expected.remove(include1)
    destinations = AddDestinationMenu(surface).destinations
    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()

    root.fragment("fragment1", name = "mytest.navtest.fragment1")
    modelBuilder.updateModel(model)
    model.notifyModified(ChangeType.EDIT)

    expected.remove(fragment1)
    destinations = AddDestinationMenu(surface).destinations
    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()

    root.activity("activity2", name = "mytest.navtest.activity2")
    modelBuilder.updateModel(model)
    model.notifyModified(ChangeType.EDIT)

    expected.remove(activity2)
    destinations = AddDestinationMenu(surface).destinations
    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()

    root.activity("activity3", name = "mytest.navtest.activity3")
    modelBuilder.updateModel(model)
    model.notifyModified(ChangeType.EDIT)

    expected.remove(activity3)
    destinations = AddDestinationMenu(surface).destinations
    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()

    (root.findById("@+id/subnav2") as NavModelBuilderUtil.NavigationComponentDescriptor)
      .fragment("fragment2", name = "mytest.navtest.fragment2")
    modelBuilder.updateModel(model)
    model.notifyModified(ChangeType.EDIT)

    expected.remove(fragment2)
    destinations = AddDestinationMenu(surface).destinations
    Truth.assertThat(destinations).containsExactlyElementsIn(expected).inOrder()
  }

  private fun findClass(className: String) =
    JavaPsiFacade.getInstance(projectRule.project).findClass(className, GlobalSearchScope.allScope(projectRule.project))!!

  @Test
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

  @Test
  fun testUndoNewComponent() {
    val gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(0, 0)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    gallery.dispatchEvent(MouseEvent(
      gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
      cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))

    // ordinarily this would be done by the resource change listener
    model.notifyModified(ChangeType.EDIT)

    assertNotNull(destination.component)
    assertEquals(3, surface.currentNavigation.children.size)

    UndoManager.getInstance(projectRule.project).undo(TestNavEditor(model.virtualFile, projectRule.project))

    PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
    model.notifyModified(ChangeType.EDIT)

    assertEquals(2, surface.currentNavigation.children.size)
  }

  @Test
  fun testFiltering() {
    val gallery = menu.destinationsList
    val searchField = menu.searchField

    assertEquals(4, gallery.itemsCount)
    assertEquals("placeholder", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("BlankFragment", (gallery.model.getElementAt(1) as Destination).label)
    assertEquals("navigation.xml", (gallery.model.getElementAt(2) as Destination).label)
    assertEquals("activity_main", (gallery.model.getElementAt(3) as Destination).label)

    searchField.text = "v"
    assertEquals(2, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
    assertEquals("activity_main", (gallery.model.getElementAt(1) as Destination).label)

    searchField.text = "vig"
    assertEquals(1, gallery.itemsCount)
    assertEquals("navigation.xml", (gallery.model.getElementAt(0) as Destination).label)
  }

  @Test
  fun testCreateBlank() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val createdFiles = mutableListOf<File>()
    val createFragmentFileTask = {
      val root = projectRule.module.rootManager.contentRoots[0].path
      projectRule.fixture.addFileToProject("src/mytest/navtest/Frag.java",
                                           "package mytest.navtest\n" +
                                           "public class Frag extends android.support.v4.app.Fragment {}")
      projectRule.fixture.addFileToProject("res/layout/frag_layout.xml", "")
      createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
      createdFiles.add(File(root, "res/layout/frag_layout.xml"))
      menu.postNewDestinationFileCreated(model, createdFiles)
    }

    TestNavUsageTracker.create(model).use { tracker ->
      createFragmentFileTask()

      val added = model.find("frag")!!
      assertEquals("fragment", added.tagName)
      assertEquals("@layout/frag_layout", added.layout)
      assertEquals("mytest.navtest.Frag", added.className)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(CREATE_FRAGMENT).build())
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(ADD_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                       .setHasClass(true)
                                                       .setHasLayout(true)
                                                       .setType(FRAGMENT)).build())
    }
  }

  @Test
  fun testCreateBlankNoLayout() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val createdFiles = mutableListOf<File>()
    val createFragmentFileTask = {
      val root = projectRule.module.rootManager.contentRoots[0].path
      projectRule.fixture.addFileToProject("src/mytest/navtest/Frag.java",
                                 "package mytest.navtest\n" +
                                 "public class Frag extends android.support.v4.app.Fragment {}")
      createdFiles.add(File(root, "src/mytest/navtest/Frag.java"))
      menu.postNewDestinationFileCreated(model, createdFiles)
    }

    TestNavUsageTracker.create(model).use { tracker ->
      createFragmentFileTask.invoke()

      val added = model.find("frag")!!
      assertEquals("fragment", added.tagName)
      assertNull(added.layout)
      assertEquals("mytest.navtest.Frag", added.className)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(CREATE_FRAGMENT).build())
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(ADD_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                       .setHasClass(true)
                                                       .setType(FRAGMENT)).build())
    }
  }

  @Test
  @RunsInEdt
  fun testCreateSettingsFragment() {
    model.pendingIds.addAll(model.flattenComponents().map { it.id }.collect(Collectors.toList()))
    val createdFiles = mutableListOf<File>()
    val createFragmentFileTask = {
      val root = projectRule.module.rootManager.contentRoots[0].path
      projectRule.fixture.addFileToProject("src/mytest/navtest/SettingsFragment.kt",
                                 """
package mytest.navtest
import androidx.preference.PreferenceFragmentCompat
class SettingsFragment : PreferenceFragmentCompat()
                                 """.trimIndent())
      createdFiles.add(File(root, "src/mytest/navtest/SettingsFragment.kt"))
      menu.postNewDestinationFileCreated(model, createdFiles)
    }
    TestNavUsageTracker.create(model).use { tracker ->
      createFragmentFileTask()

      val added = model.find("settingsFragment")!!
      assertEquals("fragment", added.tagName)
      assertEquals("mytest.navtest.SettingsFragment", added.className)
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(CREATE_FRAGMENT).build())
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(ADD_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder()
                                                       .setHasClass(true)
                                                       .setType(FRAGMENT)).build())
    }
  }

  @Test
  fun testCreatePlaceholder() {
    var gallery = menu.destinationsList
    val cell0Bounds = gallery.getCellBounds(1, 1)
    val destination = gallery.model.getElementAt(0) as Destination
    gallery.setSelectedValue(destination, false)
    TestNavUsageTracker.create(model).use { tracker ->
      gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
      val component = destination.component
      assertNotNull(component)
      assertEquals(listOf(component!!), surface.selectionModel.selection)
      assertEquals("placeholder", component.id)
      assertNull(component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT))
      assertNull(component.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME))

      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(ADD_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder().setType(FRAGMENT)).build())

      setupMainMenuPanel()
      gallery = menu.destinationsList
      val destination2 = gallery.model.getElementAt(0) as Destination
      gallery.setSelectedValue(destination2, false)
      gallery.dispatchEvent(MouseEvent(
        gallery, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
        cell0Bounds.centerX.toInt(), cell0Bounds.centerX.toInt(), 1, false))
      val component2 = destination2.component
      assertNotNull(component2)
      assertEquals(listOf(component2!!), surface.selectionModel.selection)
      assertEquals("placeholder2", component2.id)
      assertContainsElements(surface.model?.components?.get(0)?.children?.map { it.id }!!, "placeholder", "placeholder2")
    }
  }

  private fun setupMainMenuPanel() {
    // Init the lateinit variable AddDestinationMenu.destinationsList
    menu.mainPanel
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

  @Test
  fun testAddDestination() {
    val destination = MockitoKt.mock<Destination.IncludeDestination>()
    val component = model.find("fragment")!!
    MockitoKt.whenever(destination.component).thenReturn(component)
    TestNavUsageTracker.create(model).use { tracker ->
      menu.addDestination(destination)
      verify(destination).addToGraph()
      verify(tracker).logEvent(NavEditorEvent.newBuilder()
                                 .setType(ADD_DESTINATION)
                                 .setDestinationInfo(NavDestinationInfo.newBuilder().setType(FRAGMENT)).build())
    }
  }

  @Test
  fun testAddInclude() {
    val destination = MockitoKt.mock<Destination.IncludeDestination>()
    val component = spy(model.find("fragment")!!)
    MockitoKt.whenever(component.tagName).thenReturn(TAG_INCLUDE)
    MockitoKt.whenever(destination.component).thenReturn(component)
    TestNavUsageTracker.create(model).use { tracker ->
      menu.addDestination(destination)
      verify(destination).addToGraph()
      verify(tracker).logEvent(NavEditorEvent.newBuilder().setType(ADD_INCLUDE).build())
    }
  }

  @Test
  fun testAddDynamicFragment() {
    addFragment("DynamicFragment", "dynamicfeaturemodule")
    val dynamicFragment =
      Destination.RegularDestination(model.components[0], "fragment", null,
                                     findClass("mytest.navtest.DynamicFragment"), dynamicModuleName = "dynamicfeaturemodule")

    WriteCommandAction.runWriteCommandAction(surface.project) {
      dynamicFragment.addToGraph()
    }

    val fragment = model.find("dynamicFragment")!!
    assertEquals("dynamicfeaturemodule", fragment.getAttribute(AUTO_URI, ATTR_MODULE_NAME))
  }

  // Disabling test for now due to sporadic failures
  // b/130692291
  /*
  fun testDumbMode() {
    DumbServiceImpl.getInstance(project).isDumb = true
    try {
      AddDestinationMenu(surface).mainPanel
    }
    finally {
      DumbServiceImpl.getInstance(project).isDumb = false
    }
  }
  */

  private fun addFragment(name: String, folder: String = "src/mytest/navtest") {
    addDestination(name, "android.support.v4.app.Fragment", folder)
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
        <androidx.fragment.app.FragmentContainerView
            android:id="@+id/navhost"
            android:name="androidx.navigation.fragment.NavHostFragment"
            app:defaultNavHost="true"
            app:navGraph="@navigation/nav"/>

      </android.support.constraint.ConstraintLayout>
    """.trimIndent()

    projectRule.fixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivityWithLayout(name: String) {
    addActivity(name)
    val relativePath = "res/layout/$name.xml"
    val fileText = """
      <?xml version="1.0" encoding="utf-8"?>
      <android.support.constraint.ConstraintLayout xmlns:tools="http://schemas.android.com/tools"
                                                   tools:context=".$name"/>
    """.trimIndent()

    projectRule.fixture.addFileToProject(relativePath, fileText)
  }

  private fun addActivity(name: String) {
    addDestination(name, "android.app.Activity")
  }

  private fun addDestination(name: String, parentClass: String, folder: String = "src/mytest/navtest") {
    val relativePath = "$folder/$name.java"
    val fileText = """
      .package mytest.navtest;
      .import $parentClass;
      .
      .public class $name extends ${parentClass.substringAfterLast('.')} {
      .}
      """.trimMargin(".")

    projectRule.fixture.addFileToProject(relativePath, fileText)
  }

  private fun addIncludeFile(name: String) {
    val relativePath = "res/navigation/$name.xml"
    val fileText = """
      .<?xml version="1.0" encoding="utf-8"?>
      .<navigation xmlns:android="http://schemas.android.com/apk/res/android"
          .xmlns:app="http://schemas.android.com/apk/res-auto" android:id="@+id/testnav">

      .</navigation>
      """.trimMargin(".")

    projectRule.fixture.addFileToProject(relativePath, fileText)
  }
}

class AddDestinationMenuDependencyTest : NavTestCase() {
  override fun configureAdditionalModules(projectBuilder: TestFixtureBuilder<IdeaProjectTestFixture>,
                                          modules: MutableList<MyAdditionalModuleData>) {
    super.configureAdditionalModules(projectBuilder, modules)
    addModuleWithAndroidFacet(projectBuilder, modules, "myLibrary", PROJECT_TYPE_LIBRARY, true)
  }

  fun testLayoutFileInDependency() {
    val modulePath = getAdditionalModulePath("myLibrary")

    val psiClass = (myFixture.addFileToProject("$modulePath/src/main/java/com/example/mylibrary/BlankFragment.java", """
                                                 package com.example.mylibrary;
                                                 import android.support.v4.app.Fragment;
                                                 public class BlankFragment extends Fragment {}
                                                 """.trimIndent()) as PsiJavaFileImpl).classes[0]

    val xmlFile = myFixture.addFileToProject("$modulePath/res/layout/fragment_blank.xml", """
                                               <?xml version="1.0" encoding="utf-8"?>
                                               <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                                                   xmlns:tools="http://schemas.android.com/tools"
                                                   tools:context="com.example.mylibrary.BlankFragment"
                                               </FrameLayout>"
                                               """.trimIndent()) as XmlFile

    val module = getAdditionalModuleByName("myLibrary")!!
    val facet = module.androidFacet!!
    val model = NavModelBuilderUtil.model("nav.xml", facet, myFixture, { navigation("root") }).build()

    val surface = NavDesignSurface(project, testRootDisposable)
    PlatformTestUtil.waitForFuture(surface.setModel(model))

    val blankFragment = Destination.RegularDestination(
      model.components[0], "fragment", null, psiClass, layoutFile = xmlFile)
    waitForResourceRepositoryUpdates()

    val menu = AddDestinationMenu(surface)
    assertEquals(blankFragment, menu.destinations[1])
  }
}

