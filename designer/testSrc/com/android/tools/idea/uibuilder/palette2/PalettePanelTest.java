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
package com.android.tools.idea.uibuilder.palette2;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.lang.reflect.Method;
import java.util.Collections;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.cleanUsageTrackerAfterTesting;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockNlUsageTracker;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.dnd.DnDConstants.ACTION_MOVE;
import static org.mockito.Mockito.*;

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

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myTreeDumper = new NlTreeDumper();
    myCopyPasteManager = mock(CopyPasteManager.class);
    myDependencyManager = mock(DependencyManager.class);
    myBrowserLauncher = mock(BrowserLauncher.class);
    myPopupMenu = mock(ActionPopupMenu.class);
    myPopupMenuComponent = mock(JPopupMenu.class);
    myActionManager = mock(ActionManager.class);
    myGradleDependencyManager = mock(GradleDependencyManager.class);
    registerApplicationComponent(BrowserLauncher.class, myBrowserLauncher);
    registerApplicationComponent(CopyPasteManager.class, myCopyPasteManager);
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    registerApplicationComponentImplementation(ActionManager.class, myActionManager);
    registerProjectComponent(GradleDependencyManager.class, myGradleDependencyManager);
    when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class))).thenReturn(myPopupMenu);
    when(myPopupMenu.getComponent()).thenReturn(myPopupMenuComponent);
    myPanel = new PalettePanel(getProject(), myDependencyManager);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myPanel);
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
      myPopupMenu = null;
      myPanel = null;
    }
    finally {
      super.tearDown();
    }
  }

  public void testCopyIsUnavailableWhenNothingIsSelected() throws Exception {
    DataContext context = mock(DataContext.class);
    CopyProvider provider = (CopyProvider)myPanel.getData(PlatformDataKeys.COPY_PROVIDER.getName());
    assertThat(provider).isNotNull();
    assertThat(provider.isCopyVisible(context)).isTrue();
    assertThat(provider.isCopyEnabled(context)).isFalse();
  }

  public void testCopy() throws Exception {
    myPanel.setToolContext(createDesignSurface(NlLayoutType.LAYOUT));

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
    assertThat(myPanel.getCategoryList().getItemsCount()).isEqualTo(1);
    assertThat(myPanel.getItemList().getItemsCount()).isEqualTo(0);
  }

  public void testLayoutTypes() {
    myPanel.setToolContext(createDesignSurface(NlLayoutType.LAYOUT));
    assertThat(isCategoryListVisible()).isTrue();

    myPanel.setToolContext(createDesignSurface(NlLayoutType.MENU));
    assertThat(isCategoryListVisible()).isFalse();

    myPanel.setToolContext(createDesignSurface(NlLayoutType.PREFERENCE_SCREEN));
    assertThat(isCategoryListVisible()).isTrue();

    myPanel.setToolContext(createDesignSurface(NlLayoutType.STATE_LIST));
    assertThat(isCategoryListVisible()).isFalse();
  }

  public void testTypingInCategoryListStartsFiltering() {
    checkTypingStartsFiltering(myPanel.getCategoryList());
  }

  public void testTypingInItemListStartsFiltering() {
    checkTypingStartsFiltering(myPanel.getItemList());
  }

  public void testShiftHelpOnPaletteItem() throws Exception {
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

    verify(usageTracker).logDropFromPalette(CONSTRAINT_LAYOUT, representation, PaletteMode.ICON_AND_NAME, "Layouts", -1);
  }

  public void testDragAndDropInDumbMode() throws Exception {
    try {
      DumbServiceImpl.getInstance(getProject()).setDumb(true);
      StatusBarEx statusBar = registerMockStatusBar();
      myTrackingDesignSurface = setUpLayoutDesignSurface();
      myPanel.setToolContext(myTrackingDesignSurface);
      myPanel.getCategoryList().setSelectedIndex(4); // Layouts
      myPanel.getItemList().setSelectedIndex(0);     // ConstraintLayout (to avoid preview)
      MouseEvent event = mock(MouseEvent.class);
      when(event.getPoint()).thenReturn(new Point(50, 50));
      JList<Palette.Item> list = myPanel.getItemList();
      TransferHandler handler = list.getTransferHandler();
      assertFalse(imitateDragAndDrop(handler, list));
      verify(statusBar).notifyProgressByBalloon(eq(MessageType.WARNING), eq("Dragging from the Palette is not available while indices are updating."), isNull(), isNull());
    }
    finally {
      DumbServiceImpl.getInstance(getProject()).setDumb(false);
    }
  }

  public void testAddToDesignFromEnterKey() throws Exception {
    DesignSurface surface = setUpLayoutDesignSurface();

    myPanel.getCategoryList().setSelectedIndex(2); // Buttons
    myPanel.getItemList().setSelectedIndex(2);     // CheckBox

    ActionListener listener = myPanel.getItemList().getActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
    assertThat(listener).isNotNull();

    ActionEvent event = mock(ActionEvent.class);
    listener.actionPerformed(event);

    assertThat(myTreeDumper.toTree(surface.getModel().getComponents())).isEqualTo(
      "NlComponent{tag=<LinearLayout>, bounds=[0,100:768x1084, instance=0}\n" +
      "    NlComponent{tag=<TextView>, bounds=[0,106:768x200, instance=1}\n" +
      "    NlComponent{tag=<CheckBox>, bounds=[768,100:2x310, instance=2}");
  }

  public void testOpenContextPopupOnMousePressed() throws Exception {
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

  public void testOpenContextPopupOnMouseReleased() throws Exception {
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

  public void testAddToDesign() throws Exception {
    DesignSurface surface = setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(2); // Buttons
    myPanel.getItemList().setSelectedIndex(2);     // CheckBox

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getAddToDesignAction().actionPerformed(event);

    assertThat(myTreeDumper.toTree(surface.getModel().getComponents())).isEqualTo(
      "NlComponent{tag=<LinearLayout>, bounds=[0,100:768x1084, instance=0}\n" +
      "    NlComponent{tag=<TextView>, bounds=[0,106:768x200, instance=1}\n" +
      "    NlComponent{tag=<CheckBox>, bounds=[768,100:2x310, instance=2}");
  }

  public void testAddToDesignUpdateDoesNotCauseDependencyDialog() throws Exception {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(6); // Google
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

  public void testOpenAndroidDocumentation() throws Exception {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(2); // Buttons
    myPanel.getItemList().setSelectedIndex(2);     // CheckBox

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getAndroidDocAction().actionPerformed(event);

    verify(myBrowserLauncher).browse(eq("https://developer.android.com/reference/android/widget/CheckBox.html"), isNull(), isNull());
  }

  public void testOpenMaterialDesignDocumentation() throws Exception {
    setUpLayoutDesignSurface();
    myPanel.getCategoryList().setSelectedIndex(2); // Buttons
    myPanel.getItemList().setSelectedIndex(2);     // CheckBox

    AnActionEvent event = mock(AnActionEvent.class);
    myPanel.getMaterialDocAction().actionPerformed(event);

    verify(myBrowserLauncher).browse(eq("https://material.io/guidelines/components/selection-controls.html"), isNull(), isNull());
  }

  @NotNull
  private StatusBarEx registerMockStatusBar() {
    WindowManager windowManager = mock(WindowManagerEx.class);
    IdeFrame frame = mock(IdeFrame.class);
    StatusBarEx statusBar = mock(StatusBarEx.class);
    registerApplicationComponentImplementation(WindowManager.class, windowManager);
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

  private void checkTypingStartsFiltering(@NotNull JComponent component) {
    StartFiltering filtering = new StartFiltering();
    myPanel.setStartFiltering(filtering);
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyTyped(new KeyEvent(component, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, 'u'));
    }
    assertThat(filtering.getChar()).isEqualTo('u');
  }

  @NotNull
  private DesignSurface setUpLayoutDesignSurface() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    DesignSurface surface = createDesignSurface(NlLayoutType.LAYOUT);
    myPanel.setToolContext(surface);
    return surface;
  }

  @NotNull
  private DesignSurface createDesignSurface(@NotNull NlLayoutType layoutType) {
    SyncNlModel model = createModel().build();
    DesignSurface surface = model.getSurface();
    LayoutTestUtilities.createScreen(model);
    when(surface.getLayoutType()).thenReturn(layoutType);
    myPanel.setToolContext(model.getSurface());
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

  private static void fireComponentResize(@NotNull JComponent component) {
    ComponentEvent event = mock(ComponentEvent.class);
    for (ComponentListener listener : component.getComponentListeners()) {
      listener.componentResized(event);
    }
  }

  private static class StartFiltering implements StartFilteringListener {
    private char myChar;

    @Override
    public void startFiltering(char character) {
      myChar = character;
    }

    public char getChar() {
      return myChar;
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
