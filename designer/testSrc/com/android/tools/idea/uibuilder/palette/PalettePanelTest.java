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
package com.android.tools.idea.uibuilder.palette;

import static com.android.SdkConstants.ADS_ARTIFACT;
import static com.android.SdkConstants.AD_VIEW;
import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.cleanUsageTrackerAfterTesting;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockNlUsageTracker;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.dnd.DnDConstants.ACTION_MOVE;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.adtui.workbench.ToolWindowCallback;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.concurrency.FutureUtils;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.type.PreferenceScreenFileType;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.testFramework.PlatformTestUtil;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.swing.*;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;

public class PalettePanelTest extends LayoutTestCase {
  private NlTreeDumper myTreeDumper;
  private CopyPasteManager myCopyPasteManager;
  private GradleDependencyManager myGradleDependencyManager;
  private DependencyManager myDependencyManager;
  private BrowserLauncher myBrowserLauncher;
  private ActionManager myActionManager;
  private DesignSurface myTrackingDesignSurface;
  private ActionPopupMenu myPopupMenu;
  private JPopupMenu myPopupMenuComponent;
  private PalettePanel myPanel;
  private SyncNlModel myModel;

  private static final int BUTTON_CATEGORY_INDEX = 2;
  private static final int CHECKBOX_ITEM_INDEX = 2;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTreeDumper = new NlTreeDumper(true, false);
    myCopyPasteManager = mock(CopyPasteManager.class);
    myDependencyManager = mock(DependencyManager.class);
    myBrowserLauncher = mock(BrowserLauncher.class);
    myPopupMenu = mock(ActionPopupMenu.class);
    myPopupMenuComponent = mock(JPopupMenu.class);
    myActionManager = mock(ActionManager.class);
    myGradleDependencyManager = mock(GradleDependencyManager.class);
    registerApplicationService(BrowserLauncher.class, myBrowserLauncher);
    registerApplicationService(CopyPasteManager.class, myCopyPasteManager);
    registerApplicationService(PropertiesComponent.class, new PropertiesComponentMock());
    //registerApplicationService(ActionManager.class, myActionManager);  // ActionManager is too complex to be mocked in a heavy test
    registerProjectService(GradleDependencyManager.class, myGradleDependencyManager);
    // Some IntelliJ platform code might ask for actions during the initialization. Mock a generic one.
    when(myActionManager.getAction(anyString())).thenReturn(new AnAction("Empty") {
      @Override
      public boolean isDumbAware() {
        return true;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) { }
    });
    when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class))).thenReturn(myPopupMenu);
    when(myPopupMenu.getComponent()).thenReturn(myPopupMenuComponent);
    myPanel = new PalettePanel(getProject(), myDependencyManager, getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myModel != null) {
        Disposer.dispose(myModel);
      }
      if (myTrackingDesignSurface != null) {
        cleanUsageTrackerAfterTesting(myTrackingDesignSurface);
      }
      myTreeDumper = null;
      myCopyPasteManager = null;
      myDependencyManager = null;
      myBrowserLauncher = null;
      myPopupMenu = null;
      myPopupMenuComponent = null;
      myActionManager = null;
      myTrackingDesignSurface = null;
      myPanel = null;
      myModel = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testCopyIsUnavailableWhenNothingIsSelected() {
    DataContext context = mock(DataContext.class);
    CopyProvider provider = (CopyProvider)myPanel.getData(PlatformDataKeys.COPY_PROVIDER.getName());
    assertThat(provider).isNotNull();
    assertThat(provider.isCopyVisible(context)).isTrue();
    assertThat(provider.isCopyEnabled(context)).isFalse();
  }

  public void testCopy() throws Exception {
    myPanel.setToolContext(createDesignSurface(LayoutFileType.INSTANCE));

    DataContext context = mock(DataContext.class);
    CopyProvider provider = (CopyProvider)myPanel.getData(PlatformDataKeys.COPY_PROVIDER.getName());
    assertThat(provider).isNotNull();
    assertThat(provider.isCopyVisible(context)).isTrue();
    assertThat(provider.isCopyEnabled(context)).isTrue();
    provider.performCopy(context);

    ArgumentCaptor<Transferable> captor = ArgumentCaptor.forClass(Transferable.class);
    verify(myCopyPasteManager).setContents(captor.capture());
    Transferable transferable = captor.getValue();
    assertThat(transferable).isNotNull();
    assertThat(transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)).isTrue();
    Object item = transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
    assertThat(item).isInstanceOf(DnDTransferItem.class);
    DnDTransferItem dndItem = (DnDTransferItem)item;
    assertThat(dndItem.getComponents().size()).isEqualTo(1);
    DnDTransferComponent component = dndItem.getComponents().get(0);
    assertThat(component.getRepresentation()).startsWith(("<TextView"));
  }

  public void testDownloadClick() {
    setUpLayoutDesignSurface();
    myPanel.setFilter("floating");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    ItemList itemList = myPanel.getItemList();
    int x = itemList.getWidth() - 10;
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.BUTTON1_MASK, x, 10, 1, false));
    verify(myDependencyManager).ensureLibraryIsIncluded(eq(itemList.getSelectedValue()));
  }

  public void testClickOutsideDownloadIconDoesNotCauseNewDependency() {
    setUpLayoutDesignSurface();
    myPanel.setFilter("floating");

    ItemList itemList = myPanel.getItemList();
    int x = itemList.getWidth() - 30;
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, 0, x, 10, 1, false));
    verify(myDependencyManager, never()).ensureLibraryIsIncluded(any(Palette.Item.class));
  }

  public void testSearchPaletteWithCustomComponent() {
    // Regression test for b/65842975
    @Language("JAVA")
    String widget = "package a.b;\n" +
                    "\n" +
                    "import android.content.Context;\n" +
                    "import android.webkit.WebView;\n" +
                    "\n" +
                    "public class MyWebView extends android.webkit.WebView {\n" +
                    "\n" +
                    "    public WebView(Context context) {\n" +
                    "        super(context);\n" +
                    "    }\n" +
                    "}\n";

    myFixture.addFileToProject("src/a/b/MyWebView.java", widget);
    setUpLayoutDesignSurface();
    myPanel.setFilter("%");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();

    assertThat(myPanel.getCategoryList().getItemsCount()).isEqualTo(1);
    assertThat(myPanel.getItemList().getItemsCount()).isEqualTo(0);
  }

  public void testLayoutTypes() {
    myPanel.setToolContext(createDesignSurface(LayoutFileType.INSTANCE));
    assertThat(isCategoryListVisible()).isTrue();

    myPanel.setToolContext(createDesignSurface(MenuFileType.INSTANCE));
    assertThat(isCategoryListVisible()).isFalse();

    myPanel.setToolContext(createDesignSurface(PreferenceScreenFileType.INSTANCE));
    assertThat(isCategoryListVisible()).isTrue();
  }

  public void testTypingInCategoryListStartsFiltering() {
    checkTypingStartsFiltering(myPanel.getCategoryList(), 'u', true);
  }

  public void testTypingInItemListStartsFiltering() {
    checkTypingStartsFiltering(myPanel.getItemList(), 'u', true);
  }

  public void testTypingNonCharactersDoesNotStartFiltering() {
    char[] chars = {'\b', KeyEvent.VK_DELETE, '@', ' '};
    for (char ch : chars) {
      checkTypingStartsFiltering(myPanel.getCategoryList(), ch, false);
      checkTypingStartsFiltering(myPanel.getItemList(), ch, false);
    }
  }

  public void testShiftHelpOnPaletteItem() {
    setUpLayoutDesignSurface();

    ActionListener listener = myPanel.getItemList().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.SHIFT_MASK));
    assertThat(listener).isNotNull();

    ActionEvent event = mock(ActionEvent.class);

    listener.actionPerformed(event);
    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/TextView.html"), isNull(), isNull());
  }

  public void testDragAndDropAreLoggedForAnalytics() throws Exception {
    @Language("XML")
    String representation = "<android.support.constraint.ConstraintLayout\n" +
                            "    android:layout_width=\"match_parent\"\n" +
                            "    android:layout_height=\"match_parent\">\n\n" +
                            "</android.support.constraint.ConstraintLayout>\n";

    myTrackingDesignSurface = setUpLayoutDesignSurface();
    myPanel.setToolContext(myTrackingDesignSurface);
    myPanel.getCategoryList().setSelectedIndex(4); // Layouts
    myPanel.getItemList().setSelectedIndex(0);     // ConstraintLayout (to avoid preview)
    NlUsageTracker usageTracker = mockNlUsageTracker(myTrackingDesignSurface);

    MouseEvent event = mock(MouseEvent.class);
    when(event.getPoint()).thenReturn(new Point(50, 50));
    JList<Palette.Item> list = myPanel.getItemList();
    TransferHandler handler = list.getTransferHandler();
    imitateDragAndDrop(handler, list);

    verify(usageTracker).logDropFromPalette(CONSTRAINT_LAYOUT.defaultName(), representation, "Layouts", -1);
  }

  public void testDragAndDropInDumbMode() throws Exception {
    StatusBarEx statusBar = registerMockStatusBar();
    myTrackingDesignSurface = setUpLayoutDesignSurface();
    myPanel.setToolContext(myTrackingDesignSurface);
    myPanel.getCategoryList().setSelectedIndex(4); // Layouts
    myPanel.getItemList().setSelectedIndex(0);     // ConstraintLayout (to avoid preview)
    MouseEvent event = mock(MouseEvent.class);
    when(event.getPoint()).thenReturn(new Point(50, 50));
    JList<Palette.Item> list = myPanel.getItemList();
    TransferHandler handler = list.getTransferHandler();
    try {
      DumbServiceImpl.getInstance(getProject()).setDumb(true);
      assertFalse(imitateDragAndDrop(handler, list));
      verify(statusBar)
        .notifyProgressByBalloon(eq(MessageType.WARNING), eq("Dragging from the Palette is not available while indices are updating."));
    }
    finally {
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
    }
  }

  public void testAddToDesignFromEnterKey() {
    DesignSurface surface = setUpLayoutDesignSurface();

    myPanel.getCategoryList().setSelectedIndex(BUTTON_CATEGORY_INDEX);
    myPanel.getItemList().setSelectedIndex(CHECKBOX_ITEM_INDEX);

    ActionListener listener = myPanel.getItemList().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    assertThat(listener).isNotNull();

    ActionEvent event = mock(ActionEvent.class);
    listener.actionPerformed(event);

    assertThat(myTreeDumper.toTree(surface.getModel().getComponents())).isEqualTo(
      "NlComponent{tag=<LinearLayout>, instance=0}\n" +
      "    NlComponent{tag=<TextView>, instance=1}\n" +
      "    NlComponent{tag=<CheckBox>, instance=2}");

    assertThat(myTreeDumper.toTree(surface.getSelectionModel().getSelection())).isEqualTo(
      "NlComponent{tag=<CheckBox>, instance=2}");
  }

  public void testOpenContextPopupOnMousePressed() {
    setUpLayoutDesignSurface();

    ItemList itemList = myPanel.getItemList();
    Rectangle bounds = itemList.getCellBounds(3, 3);
    int x = bounds.x + bounds.width / 2;
    int y = bounds.y + bounds.height / 2;

    // On some OS we get context menus on mouse pressed events
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON3_MASK, x, y, 1, true));

    verify(myPopupMenuComponent).show(eq(itemList), eq(x), eq(y));
    assertThat(itemList.getSelectedIndex()).isEqualTo(3);
  }

  public void testOpenContextPopupOnMouseReleased() {
    setUpLayoutDesignSurface();

    ItemList itemList = myPanel.getItemList();
    Rectangle bounds = itemList.getCellBounds(3, 3);
    int x = bounds.x + bounds.width / 2;
    int y = bounds.y + bounds.height / 2;

    // On some OS we get context menus on mouse released events
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.BUTTON3_MASK, x, y, 1, true));

    verify(myPopupMenuComponent).show(eq(itemList), eq(x), eq(y));
    assertThat(itemList.getSelectedIndex()).isEqualTo(3);
  }

  public void testAddToDesign() {
    DesignSurface surface = setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(BUTTON_CATEGORY_INDEX);
    myPanel.getItemList().setSelectedIndex(CHECKBOX_ITEM_INDEX);

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getAddToDesignAction().actionPerformed(event);

    assertThat(myTreeDumper.toTree(surface.getModel().getComponents())).isEqualTo(
      "NlComponent{tag=<LinearLayout>, instance=0}\n" +
      "    NlComponent{tag=<TextView>, instance=1}\n" +
      "    NlComponent{tag=<CheckBox>, instance=2}");

    assertThat(myTreeDumper.toTree(surface.getSelectionModel().getSelection())).isEqualTo(
      "NlComponent{tag=<CheckBox>, instance=2}");
  }

  public void testAddToDesignUpdateDoesNotCauseDependencyDialog() {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(7); // Google
    myPanel.getItemList().setSelectedIndex(0);     // AdView
    assertThat(myPanel.getItemList().getSelectedValue().getTagName()).isEqualTo(AD_VIEW);

    AnActionEvent event = mock(AnActionEvent.class);
    Presentation presentation = myPanel.getAddToDesignAction().getTemplatePresentation().clone();
    when(event.getPresentation()).thenReturn(presentation);
    when(myGradleDependencyManager.findMissingDependencies(any(Module.class), anyCollection())).thenReturn(Collections.singletonList(
      GradleCoordinate.parseCoordinateString(ADS_ARTIFACT)));

    // This statement would fail if the user is asked if they want to add a dependency on play-services-ads:
    myPanel.getAddToDesignAction().update(event);
  }

  public void testOpenAndroidDocumentation() {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(BUTTON_CATEGORY_INDEX);
    myPanel.getItemList().setSelectedIndex(CHECKBOX_ITEM_INDEX);

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getAndroidDocAction().actionPerformed(event);

    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/CheckBox.html"), isNull(), isNull());
  }

  public void testOpenMaterialDesignDocumentation() {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(BUTTON_CATEGORY_INDEX);
    myPanel.getItemList().setSelectedIndex(CHECKBOX_ITEM_INDEX);

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getMaterialDocAction().actionPerformed(event);

    verify(myBrowserLauncher).browse(eq("https://material.io/guidelines/components/selection-controls.html"), isNull(), isNull());
  }

  public void testPopupMenuWithPreferences() {
    setUpPreferenceDesignSurface();
    ItemList itemList = myPanel.getItemList();
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, InputEvent.CTRL_DOWN_MASK, 10, 10, 1, true));

    // Popup shown for first item in the item list:
    verify(myPopupMenuComponent).show(eq(itemList), eq(10), eq(10));
    assertThat(itemList.getSelectedIndex()).isEqualTo(0);
  }

  public void testEmptyText() {
    setUpLayoutDesignSurface();
    ItemList itemList = myPanel.getItemList();
    assertThat(itemList.getEmptyText().getText()).isEqualTo("No favorites");
    assertThat(itemList.getEmptyText().getSecondaryComponent().getCharSequence(false)).isEqualTo("Right click to add");

    myPanel.getCategoryList().setSelectedIndex(BUTTON_CATEGORY_INDEX);
    assertThat(itemList.getEmptyText().getText()).isEqualTo("Empty group");
    assertThat(itemList.getEmptyText().getSecondaryComponent().getCharSequence(false)).isEqualTo("");

    myPanel.setFilter("<NOT-FOUND>!!");
    assertThat(itemList.getEmptyText().getText()).isEqualTo("No matches found");
    assertThat(itemList.getEmptyText().getSecondaryComponent().getCharSequence(false)).isEqualTo("");
  }

  @NotNull
  private StatusBarEx registerMockStatusBar() {
    WindowManager windowManager = mock(WindowManagerEx.class);
    IdeFrame frame = mock(IdeFrame.class);
    StatusBarEx statusBar = mock(StatusBarEx.class);
    registerApplicationService(WindowManager.class, windowManager);
    when(windowManager.getIdeFrame(getProject())).thenReturn(frame);
    when(frame.getStatusBar()).thenReturn(statusBar);
    return statusBar;
  }

  // Imitate a Drag & Drop operation. Return true if a transferable object was created.
  private static boolean imitateDragAndDrop(@NotNull TransferHandler handler, @NotNull JComponent component) throws Exception {
    Method createTransferable = handler.getClass().getDeclaredMethod("createTransferable", JComponent.class);
    createTransferable.setAccessible(true);
    Transferable transferable = (Transferable)createTransferable.invoke(handler, component);
    if (transferable == null) {
      return false;
    }

    Method exportDone = handler.getClass().getDeclaredMethod("exportDone", JComponent.class, Transferable.class, int.class);
    exportDone.setAccessible(true);
    exportDone.invoke(handler, component, transferable, ACTION_MOVE);
    return true;
  }

  private void checkTypingStartsFiltering(@NotNull JComponent component, char character, boolean expectSearchStarted) {
    TestToolWindow toolWindow = new TestToolWindow();
    myPanel.registerCallbacks(toolWindow);
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyTyped(new KeyEvent(component, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, character));
    }
    if (expectSearchStarted) {
      assertThat(toolWindow.getInitialSearchString()).isEqualTo(String.valueOf(character));
    }
    else {
      assertThat(toolWindow.getInitialSearchString()).isNull();
    }
  }

  @NotNull
  private DesignSurface setUpLayoutDesignSurface() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    return createDesignSurface(LayoutFileType.INSTANCE);
  }

  @NotNull
  private DesignSurface setUpPreferenceDesignSurface() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    return createDesignSurface(PreferenceScreenFileType.INSTANCE);
  }

  @NotNull
  private DesignSurface createDesignSurface(@NotNull DesignerEditorFileType layoutType) {
    myModel = createModel().build();
    DesignSurface surface = myModel.getSurface();
    LayoutTestUtilities.createScreen(myModel);
    doReturn(layoutType).when(surface).getLayoutType();
    // setToolContextAsyncImpl requires some operations to be executed on the UI thread so let the events execute until it completes
    try {
      FutureUtils.pumpEventsAndWaitForFuture(myPanel.setToolContextAsyncImpl(surface), 5, TimeUnit.SECONDS);
    }
    catch (Exception e) {
      fail(e.getMessage());
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    return surface;
  }

  private static void doLayout(@NotNull JComponent parent) {
    parent.doLayout();
    for (Component component : parent.getComponents()) {
      if (component instanceof JComponent) {
        doLayout((JComponent)component);
      }
    }
  }

  private boolean isCategoryListVisible() {
    return myPanel.getCategoryList().getParent().getParent().isVisible();
  }

  private static class TestToolWindow implements ToolWindowCallback {
    private String myInitialSearchString;

    @Override
    public void startFiltering(@NotNull String initialSearchString) {
      myInitialSearchString = initialSearchString;
    }

    @Nullable
    public String getInitialSearchString() {
      return myInitialSearchString;
    }
  }

  @NotNull
  private ModelBuilder createModel() {
    return model("linear.xml",
                 component(LINEAR_LAYOUT)
                   .withBounds(0, 0, 2000, 2000)
                   .matchParentWidth()
                   .matchParentHeight()
                   .children(
                     component(TEXT_VIEW)
                       .withBounds(200, 200, 200, 200)
                       .id("@id/myText")
                       .matchParentWidth()
                       .height("100dp")
                   ));
  }
}
