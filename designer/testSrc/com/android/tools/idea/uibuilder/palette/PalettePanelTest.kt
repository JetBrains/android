/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.ide.common.gradle.Dependency
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeJBPopupFactory
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.idea.common.LayoutTestUtilities
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.fixtures.ModelBuilder
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.common.util.NlTreeDumper
import com.android.tools.idea.concurrency.pumpEventsAndWaitForFuture
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.CopyProvider
import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.StatusBarEx
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import java.awt.Point
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.KeyStroke
import javax.swing.TransferHandler
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.isNull
import org.mockito.Mockito.any
import org.mockito.Mockito.anyCollection
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val BUTTON_CATEGORY_INDEX = 2
private const val CHECKBOX_ITEM_INDEX = 2

@RunsInEdt
class PalettePanelTest {
  private val projectRule = AndroidProjectRule.withSdk()

  @get:Rule val chain = RuleChain.outerRule(projectRule).around(JBPopupRule()).around(EdtRule())!!

  private val myTreeDumper = NlTreeDumper(true, false)
  private var myDependencyManager: DependencyManager? = null
  private var myTrackingDesignSurface: DesignSurface<*>? = null
  private var myPanel: PalettePanel? = null
  private var myModel: SyncNlModel? = null

  @Before
  fun setUp() {
    myDependencyManager = mock()
    projectRule.replaceService(BrowserLauncher::class.java, mock())
    projectRule.replaceService(CopyPasteManager::class.java, mock())
    projectRule.replaceService(PropertiesComponent::class.java, PropertiesComponentMock())
    projectRule.replaceProjectService(GradleDependencyManager::class.java, mock())
    myPanel =
      PalettePanel(projectRule.project, myDependencyManager!!, projectRule.testRootDisposable)
  }

  @After
  fun after() {
    if (myModel != null) {
      Disposer.dispose(myModel!!)
    }
    if (myTrackingDesignSurface != null) {
      LayoutTestUtilities.cleanUsageTrackerAfterTesting(myTrackingDesignSurface!!)
    }
    myDependencyManager = null
    myTrackingDesignSurface = null
    myPanel = null
    myModel = null
  }

  @Test
  fun testCopyIsUnavailableWhenNothingIsSelected() {
    val context: DataContext = mock()
    val provider = myPanel!!.getData(PlatformDataKeys.COPY_PROVIDER.name) as CopyProvider?
    assertThat(provider).isNotNull()
    assertThat(provider!!.isCopyVisible(context)).isTrue()
    assertThat(provider.isCopyEnabled(context)).isFalse()
  }

  @Test
  fun testCopy() {
    myPanel!!.setToolContext(createDesignSurface(LayoutFileType))
    val context: DataContext = mock()
    val provider = myPanel!!.getData(PlatformDataKeys.COPY_PROVIDER.name) as CopyProvider?
    assertThat(provider).isNotNull()
    assertThat(provider!!.isCopyVisible(context)).isTrue()
    assertThat(provider.isCopyEnabled(context)).isTrue()
    provider.performCopy(context)
    val captor = ArgumentCaptor.forClass(Transferable::class.java)
    val copyPasteManager = CopyPasteManager.getInstance()
    verify(copyPasteManager).setContents(captor.capture())
    val transferable = captor.value
    assertThat(transferable).isNotNull()
    assertThat(transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue()
    val item = transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR)
    assertThat(item).isInstanceOf(DnDTransferItem::class.java)
    val dndItem = item as DnDTransferItem
    assertThat(dndItem.components.size).isEqualTo(1)
    val component = dndItem.components[0]
    assertThat(component.representation).startsWith("<TextView")
  }

  @Test
  fun testDownloadClick() {
    setUpLayoutDesignSurface()
    myPanel!!.setFilter("floating")
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    val itemList = myPanel!!.itemList
    val x = itemList.width - 10
    itemList.dispatchEvent(
      MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.BUTTON1_MASK, x, 10, 1, false)
    )
    verify(myDependencyManager!!).ensureLibraryIsIncluded(eq(itemList.selectedValue))
  }

  @Test
  fun testClickOutsideDownloadIconDoesNotCauseNewDependency() {
    setUpLayoutDesignSurface()
    myPanel!!.setFilter("floating")
    val itemList = myPanel!!.itemList
    val x = itemList.width - 30
    itemList.dispatchEvent(MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, 0, x, 10, 1, false))
    verify(myDependencyManager!!, never()).ensureLibraryIsIncluded(any(Palette.Item::class.java))
  }

  @Test
  fun testSearchPaletteWithCustomComponent() {
    // Regression test for b/65842975
    @Language("JAVA")
    val widget =
      """package a.b;

import android.content.Context;
import android.webkit.WebView;

public class MyWebView extends android.webkit.WebView {

    public WebView(Context context) {
        super(context);
    }
}
"""
    projectRule.fixture.addFileToProject("src/a/b/MyWebView.java", widget)
    setUpLayoutDesignSurface()
    myPanel!!.setFilter("%")
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    assertThat(myPanel!!.categoryList.itemsCount).isEqualTo(1)
    assertThat(myPanel!!.itemList.itemsCount).isEqualTo(0)
  }

  @Test
  fun testLayoutTypes() {
    myPanel!!.setToolContext(createDesignSurface(LayoutFileType))
    assertThat(isCategoryListVisible).isTrue()
    myPanel!!.setToolContext(createDesignSurface(MenuFileType))
    assertThat(isCategoryListVisible).isFalse()
    myPanel!!.setToolContext(createDesignSurface(PreferenceScreenFileType))
    assertThat(isCategoryListVisible).isTrue()
  }

  @Test
  fun testTypingInCategoryListStartsFiltering() {
    checkTypingStartsFiltering(myPanel!!.categoryList, 'u', true)
  }

  @Test
  fun testTypingInItemListStartsFiltering() {
    checkTypingStartsFiltering(myPanel!!.itemList, 'u', true)
  }

  @Test
  fun testTypingNonCharactersDoesNotStartFiltering() {
    val chars = charArrayOf('\b', KeyEvent.VK_DELETE.toChar(), '@', ' ')
    for (ch in chars) {
      checkTypingStartsFiltering(myPanel!!.categoryList, ch, false)
      checkTypingStartsFiltering(myPanel!!.itemList, ch, false)
    }
  }

  @Test
  fun testShiftHelpOnPaletteItem() {
    setUpLayoutDesignSurface()
    val listener =
      myPanel!!
        .itemList
        .getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_MASK))
    assertThat(listener).isNotNull()
    val event: ActionEvent = mock()
    listener.actionPerformed(event)
    verify(BrowserLauncher.instance)
      .browse(
        eq("https://developer.android.com/reference/android/widget/TextView.html"),
        isNull(),
        isNull(),
      )
  }

  @Test
  fun testDragAndDropAreLoggedForAnalytics() {
    @Language("XML")
    val representation =
      """<android.support.constraint.ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

</android.support.constraint.ConstraintLayout>
"""
    myTrackingDesignSurface = setUpLayoutDesignSurface()
    myPanel!!.setToolContext(myTrackingDesignSurface)
    myPanel!!.categoryList.selectedIndex = 4 // Layouts
    myPanel!!.itemList.selectedIndex = 0 // ConstraintLayout (to avoid preview)
    val usageTracker = LayoutTestUtilities.mockNlUsageTracker(myTrackingDesignSurface!!)
    val event: MouseEvent = mock()
    `when`(event.point).thenReturn(Point(50, 50))
    val list: JList<Palette.Item> = myPanel!!.itemList
    val handler = list.transferHandler
    imitateDragAndDrop(handler, list)
    verify(usageTracker)
      .logDropFromPalette(
        AndroidXConstants.CONSTRAINT_LAYOUT.defaultName(),
        representation,
        "Layouts",
        -1,
      )
  }

  @Test
  fun testDragAndDropInDumbMode() {
    val statusBar = registerMockStatusBar()
    myTrackingDesignSurface = setUpLayoutDesignSurface()
    myPanel!!.setToolContext(myTrackingDesignSurface)
    myPanel!!.categoryList.selectedIndex = 4 // Layouts
    myPanel!!.itemList.selectedIndex = 0 // ConstraintLayout (to avoid preview)
    val event: MouseEvent = mock()
    `when`(event.point).thenReturn(Point(50, 50))
    val list: JList<Palette.Item> = myPanel!!.itemList
    val handler = list.transferHandler
    val project = projectRule.project
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      assertFalse(imitateDragAndDrop(handler, list))
      verify(statusBar)
        .notifyProgressByBalloon(
          eq(MessageType.WARNING),
          eq("Dragging from the Palette is not available while indices are updating."),
        )
    }
  }

  @Test
  fun testAddToDesignFromEnterKey() {
    val surface = setUpLayoutDesignSurface()
    myPanel!!.categoryList.selectedIndex = BUTTON_CATEGORY_INDEX
    myPanel!!.itemList.selectedIndex = CHECKBOX_ITEM_INDEX
    val listener =
      myPanel!!.itemList.getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))
    assertThat(listener).isNotNull()
    val event: ActionEvent = mock()
    listener.actionPerformed(event)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(myTreeDumper.toTree(surface.model!!.components))
      .isEqualTo(
        """NlComponent{tag=<LinearLayout>, instance=0}
    NlComponent{tag=<TextView>, instance=1}
    NlComponent{tag=<CheckBox>, instance=2}"""
      )
    assertThat(myTreeDumper.toTree(surface.selectionModel.selection))
      .isEqualTo("NlComponent{tag=<CheckBox>, instance=2}")
  }

  @Test
  fun testOpenContextPopupOnMousePressed() {
    setUpLayoutDesignSurface()
    val itemList = myPanel!!.itemList
    val bounds = itemList.getCellBounds(3, 3)
    val x = bounds.x + bounds.width / 2
    val y = bounds.y + bounds.height / 2

    // On some OS we get context menus on mouse pressed events
    itemList.dispatchEvent(
      MouseEvent(itemList, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_MASK, x, y, 1, true)
    )
    val popupFactory = JBPopupFactory.getInstance() as FakeJBPopupFactory
    assertThat(popupFactory.getChildPopups(itemList)).hasSize(1)
    assertThat(itemList.selectedIndex).isEqualTo(3)
  }

  @Test
  fun testOpenContextPopupOnMouseReleased() {
    setUpLayoutDesignSurface()
    val itemList = myPanel!!.itemList
    val bounds = itemList.getCellBounds(3, 3)
    val x = bounds.x + bounds.width / 2
    val y = bounds.y + bounds.height / 2

    // On some OS we get context menus on mouse released events
    itemList.dispatchEvent(
      MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.BUTTON3_MASK, x, y, 1, true)
    )
    val popupFactory = JBPopupFactory.getInstance() as FakeJBPopupFactory
    assertThat(popupFactory.getChildPopups(itemList)).hasSize(1)
    assertThat(itemList.selectedIndex).isEqualTo(3)
  }

  @Test
  fun testAddToDesign() {
    val surface = setUpLayoutDesignSurface()
    myPanel!!.categoryList.selectedIndex = BUTTON_CATEGORY_INDEX
    myPanel!!.itemList.selectedIndex = CHECKBOX_ITEM_INDEX
    val event: AnActionEvent = mock()
    myPanel!!.addToDesignAction.actionPerformed(event)
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    assertThat(myTreeDumper.toTree(surface.model!!.components))
      .isEqualTo(
        """NlComponent{tag=<LinearLayout>, instance=0}
    NlComponent{tag=<TextView>, instance=1}
    NlComponent{tag=<CheckBox>, instance=2}"""
      )
    assertThat(myTreeDumper.toTree(surface.selectionModel.selection))
      .isEqualTo("NlComponent{tag=<CheckBox>, instance=2}")
  }

  @Test
  fun testAddToDesignUpdateDoesNotCauseDependencyDialog() {
    setUpLayoutDesignSurface()
    myPanel!!.categoryList.selectedIndex = 7 // Google
    myPanel!!.itemList.selectedIndex = 0 // AdView
    assertThat(myPanel!!.itemList.selectedValue.tagName).isEqualTo(SdkConstants.AD_VIEW)
    val event: AnActionEvent = mock()
    val presentation = myPanel!!.addToDesignAction.templatePresentation.clone()
    val gradleDependencyManager = GradleDependencyManager.getInstance(projectRule.project)
    `when`(event.presentation).thenReturn(presentation)
    `when`(
        gradleDependencyManager.findMissingDependencies(any(Module::class.java), anyCollection())
      )
      .thenReturn(listOf(Dependency.parse(SdkConstants.ADS_ARTIFACT)))

    // This statement would fail if the user is asked if they want to add a dependency on
    // play-services-ads:
    myPanel!!.addToDesignAction.update(event)
  }

  @Test
  fun testOpenAndroidDocumentation() {
    setUpLayoutDesignSurface()
    myPanel!!.categoryList.selectedIndex = BUTTON_CATEGORY_INDEX
    myPanel!!.itemList.selectedIndex = CHECKBOX_ITEM_INDEX
    val event =
      TestActionEvent.createTestEvent {
        if (CommonDataKeys.PROJECT.`is`(it)) projectRule.project else null
      }
    myPanel!!.androidDocAction.actionPerformed(event)
    verify(BrowserLauncher.instance)
      .browse(
        eq("https://developer.android.com/reference/android/widget/CheckBox.html"),
        isNull(),
        isNull(),
      )
  }

  @Test
  fun testOpenMaterialDesignDocumentation() {
    setUpLayoutDesignSurface()
    myPanel!!.categoryList.selectedIndex = BUTTON_CATEGORY_INDEX
    myPanel!!.itemList.selectedIndex = CHECKBOX_ITEM_INDEX
    val event: AnActionEvent = mock()
    myPanel!!.materialDocAction.actionPerformed(event)
    verify(BrowserLauncher.instance)
      .browse(
        eq("https://d.android.com/r/studio-ui/designer/material/checkbox"),
        isNull(),
        isNull(),
      )
  }

  @Test
  fun testPopupMenuWithPreferences() {
    setUpPreferenceDesignSurface()
    val itemList = myPanel!!.itemList
    itemList.dispatchEvent(
      MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.CTRL_DOWN_MASK, 10, 10, 1, true)
    )

    // Popup shown for first item in the item list:
    val popupFactory = JBPopupFactory.getInstance() as FakeJBPopupFactory
    assertThat(popupFactory.getChildPopups(itemList)).hasSize(1)
    assertThat(itemList.selectedIndex).isEqualTo(0)
  }

  @Test
  fun testEmptyText() {
    setUpLayoutDesignSurface()
    val itemList = myPanel!!.itemList
    assertThat(itemList.emptyText.text).isEqualTo("No favorites")
    assertThat(itemList.emptyText.secondaryComponent.getCharSequence(false))
      .isEqualTo("Right click to add")
    myPanel!!.categoryList.selectedIndex = BUTTON_CATEGORY_INDEX
    assertThat(itemList.emptyText.text).isEqualTo("Empty group")
    assertThat(itemList.emptyText.secondaryComponent.getCharSequence(false)).isEqualTo("")
    myPanel!!.setFilter("<NOT-FOUND>!!")
    assertThat(itemList.emptyText.text).isEqualTo("No matches found")
    assertThat(itemList.emptyText.secondaryComponent.getCharSequence(false)).isEqualTo("")
  }

  @Test
  fun testMenuCreationForLayouts() {
    checkPopupMenuCreation(LayoutFileType)
  }

  @Test
  fun testMenuCreationForPreferences() {
    checkPopupMenuCreation(PreferenceScreenFileType)
  }

  @Test
  fun testMenuCreationForMenus() {
    checkPopupMenuCreation(MenuFileType)
  }

  private fun checkPopupMenuCreation(layoutType: DesignerEditorFileType) {
    val ui = FakeUi(myPanel!!, createFakeWindow = true)
    myPanel!!.setSize(800, 1000)
    doLayout(myPanel!!)
    createDesignSurface(layoutType)
    val categoryList = myPanel!!.categoryList
    for (categoryIndex in 0 until categoryList.model.size) {
      categoryList.selectedIndex = categoryIndex
      val itemList = myPanel!!.itemList
      for (itemIndex in 0 until itemList.model.size) {
        val bounds = itemList.getCellBounds(itemIndex, itemIndex)
        val x = bounds.x + bounds.width / 2
        val y = bounds.y + bounds.height / 2
        val app = ApplicationManager.getApplication() as ApplicationEx
        // During the menu popup and MenuGroup.update, we are not allowed to write to PSI.
        // At runtime this is checked because an async DataContext is used in
        // ActionUpdater.expandActionGroupAsync.
        // Simulate that here by adding the no write check up front:
        @Suppress("UnstableApiUsage")
        ProgressIndicatorUtils.runActionAndCancelBeforeWrite(
          app,
          { error("No writes allowed") },
          { ui.mouse.rightClick(x, y) },
        )
      }
    }
  }

  private fun registerMockStatusBar(): StatusBarEx {
    val windowManager: WindowManagerEx = mock()
    val frame: IdeFrame = mock()
    val statusBar: StatusBarEx = mock()
    projectRule.replaceService(WindowManager::class.java, windowManager)
    `when`(windowManager.getIdeFrame(projectRule.project)).thenReturn(frame)
    `when`(frame.statusBar).thenReturn(statusBar)
    return statusBar
  }

  private fun checkTypingStartsFiltering(
    component: JComponent,
    character: Char,
    expectSearchStarted: Boolean,
  ) {
    val toolWindow = TestToolWindow()
    myPanel!!.registerCallbacks(toolWindow)
    for (listener in component.keyListeners) {
      listener.keyTyped(
        KeyEvent(
          component,
          KeyEvent.KEY_TYPED,
          System.currentTimeMillis(),
          0,
          KeyEvent.VK_UNDEFINED,
          character,
        )
      )
    }
    if (expectSearchStarted) {
      assertThat(toolWindow.initialSearchString).isEqualTo(character.toString())
    } else {
      assertThat(toolWindow.initialSearchString).isNull()
    }
  }

  private fun setUpLayoutDesignSurface(): DesignSurface<*> {
    myPanel!!.setSize(800, 1000)
    doLayout(myPanel!!)
    return createDesignSurface(LayoutFileType)
  }

  private fun setUpPreferenceDesignSurface(): DesignSurface<*> {
    myPanel!!.setSize(800, 1000)
    doLayout(myPanel!!)
    return createDesignSurface(PreferenceScreenFileType)
  }

  private fun doLayout(parent: JComponent) {
    parent.doLayout()
    for (component in parent.components) {
      if (component is JComponent) {
        doLayout(component)
      }
    }
  }

  private fun createDesignSurface(layoutType: DesignerEditorFileType): DesignSurface<*> {
    val (resourceFolder, name) =
      when (layoutType) {
        LayoutFileType -> SdkConstants.FD_RES_LAYOUT to "layout.xml"
        PreferenceScreenFileType -> SdkConstants.FD_RES_XML to "preference.xml"
        MenuFileType -> SdkConstants.FD_RES_MENU to "menu.xml"
        else -> error("unknown type")
      }
    myModel = createModel(resourceFolder, name).build()
    val surface = myModel!!.surface
    LayoutTestUtilities.createScreen(myModel)
    doReturn(layoutType).`when`(surface).layoutType
    // setToolContextAsyncImpl requires some operations to be executed on the UI thread so let the
    // events execute until it completes
    try {
      pumpEventsAndWaitForFuture(myPanel!!.setToolContextAsyncImpl(surface), 5, TimeUnit.SECONDS)
    } catch (e: Exception) {
      error(e.message!!)
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    return surface
  }

  private val isCategoryListVisible: Boolean
    get() = myPanel!!.categoryList.parent.parent.isVisible

  private class TestToolWindow : ToolWindowCallback {
    var initialSearchString: String? = null
      private set

    override fun startFiltering(initialSearchString: String) {
      this.initialSearchString = initialSearchString
    }
  }

  private fun createModel(resourceFolder: String, name: String): ModelBuilder =
    NlModelBuilderUtil.model(
      projectRule,
      resourceFolder,
      name,
      ComponentDescriptor(SdkConstants.LINEAR_LAYOUT)
        .withBounds(0, 0, 2000, 2000)
        .matchParentWidth()
        .matchParentHeight()
        .children(
          ComponentDescriptor(SdkConstants.TEXT_VIEW)
            .withBounds(200, 200, 200, 200)
            .id("@id/myText")
            .matchParentWidth()
            .height("100dp")
        ),
    )

  private fun imitateDragAndDrop(handler: TransferHandler, component: JComponent): Boolean {
    val createTransferable =
      handler.javaClass.getDeclaredMethod("createTransferable", JComponent::class.java)
    createTransferable.isAccessible = true
    val transferable =
      createTransferable.invoke(handler, component) as? Transferable ?: return false
    val exportDone =
      handler.javaClass.getDeclaredMethod(
        "exportDone",
        JComponent::class.java,
        Transferable::class.java,
        Int::class.javaPrimitiveType,
      )
    exportDone.isAccessible = true
    exportDone.invoke(handler, component, transferable, DnDConstants.ACTION_MOVE)
    return true
  }
}
