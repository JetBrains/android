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

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.idea.common.analytics.NlUsageTracker;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.util.JavaDocViewer;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.util.ui.JBUI;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.lang.reflect.Method;

import static com.android.SdkConstants.CONSTRAINT_LAYOUT;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.cleanUsageTrackerAfterTesting;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.findActionForKey;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.mockNlUsageTracker;
import static com.google.common.truth.Truth.assertThat;
import static java.awt.dnd.DnDConstants.ACTION_MOVE;
import static org.mockito.Mockito.*;

public class PalettePanelTest extends AndroidTestCase {
  private CopyPasteManager myCopyPasteManager;
  private DependencyManager myDependencyManager;
  private JavaDocViewer myJavaDocViewer;
  private NlDesignSurface myTrackingDesignSurface;
  private PalettePanel myPanel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCopyPasteManager = mock(CopyPasteManager.class);
    myDependencyManager = mock(DependencyManager.class);
    myJavaDocViewer = mock(JavaDocViewer.class);
    registerApplicationComponent(JavaDocViewer.class, myJavaDocViewer);
    registerApplicationComponent(CopyPasteManager.class, myCopyPasteManager);
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    myPanel = new PalettePanel(getProject(), myDependencyManager);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myPanel);
      if (myTrackingDesignSurface != null) {
        cleanUsageTrackerAfterTesting(myTrackingDesignSurface);
      }
      myCopyPasteManager = null;
      myDependencyManager = null;
      myJavaDocViewer = null;
      myTrackingDesignSurface = null;
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
    assertThat(component.getRepresentation()).startsWith(("<Button"));
  }

  public void testDownloadClick() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    myPanel.setToolContext(createDesignSurface(NlLayoutType.LAYOUT));
    myPanel.setFilter("floating");

    ItemList itemList = myPanel.getItemList();
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, 0, 690, 10, 1, false));
    verify(myDependencyManager).ensureLibraryIsIncluded(eq(itemList.getSelectedValue()));
  }

  public void testClickOutsideDownloadIconDoesNotCauseNewDependency() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    myPanel.setToolContext(createDesignSurface(NlLayoutType.LAYOUT));
    myPanel.setFilter("floating");

    ItemList itemList = myPanel.getItemList();
    itemList.dispatchEvent(new MouseEvent(itemList, MouseEvent.MOUSE_RELEASED, 0, 0, 400, 10, 1, false));
    verify(myDependencyManager, never()).ensureLibraryIsIncluded(any(Palette.Item.class));
  }

  public void testInitialCategoryWidthIsReadFromOptions() {
    PropertiesComponent.getInstance().setValue(PalettePanel.PALETTE_CATEGORY_WIDTH, "217");
    Disposer.dispose(myPanel);
    myPanel = new PalettePanel(getProject(), myDependencyManager);
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    assertThat(getCategoryWidth()).isEqualTo(JBUI.scale(217));
  }

  public void testCategoryResize() {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    setCategoryWidth(388);
    fireComponentResize(myPanel.getCategoryList());
    assertThat(PropertiesComponent.getInstance().getValue(PalettePanel.PALETTE_CATEGORY_WIDTH)).isEqualTo("388");
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

  public void testTypingInTreeStartsFilteringInCategoryList() {
    checkTypingStartsFiltering(myPanel.getCategoryList());
  }

  public void testTypingInTreeStartsFilteringInItemList() {
    checkTypingStartsFiltering(myPanel.getItemList());
  }

  public void testShiftHelpOnPaletteItem() throws Exception {
    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    myPanel.setToolContext(createDesignSurface(NlLayoutType.LAYOUT));

    AnAction action = findActionForKey(myPanel.getItemList(), KeyEvent.VK_F1, InputEvent.SHIFT_MASK);
    assertThat(action).isNotNull();

    DataContext context = mock(DataContext.class);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getDataContext()).thenReturn(context);
    ArgumentCaptor<PsiClass> psiClassCaptor = ArgumentCaptor.forClass(PsiClass.class);

    action.actionPerformed(event);
    verify(myJavaDocViewer).showExternalJavaDoc(psiClassCaptor.capture(), eq(context));
    assertThat(psiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.widget.Button");
  }

  public void testDragAndDropAreLoggedForAnalytics() throws Exception {
    @Language("XML")
    String representation = "<android.support.constraint.ConstraintLayout\n" +
                            "    android:layout_width=\"match_parent\"\n" +
                            "    android:layout_height=\"match_parent\">\n\n" +
                            "</android.support.constraint.ConstraintLayout>\n";

    myPanel.setSize(800, 1000);
    doLayout(myPanel);
    myTrackingDesignSurface = createDesignSurface(NlLayoutType.LAYOUT);
    myPanel.setToolContext(myTrackingDesignSurface);
    myPanel.getItemList().setSelectedIndex(10); // Select ConstraintLayout (to avoid preview)
    NlUsageTracker usageTracker = mockNlUsageTracker(myTrackingDesignSurface);

    MouseEvent event = mock(MouseEvent.class);
    when(event.getPoint()).thenReturn(new Point(50, 50));
    JList<Palette.Item> list = myPanel.getItemList();
    TransferHandler handler = list.getTransferHandler();
    imitateDrop(handler, list);

    verify(usageTracker).logDropFromPalette(CONSTRAINT_LAYOUT, representation, PaletteMode.ICON_AND_NAME, "Common", -1);
  }

  private static void imitateDrop(@NotNull TransferHandler handler, @NotNull JComponent component) throws Exception {
    Method createTransferable = handler.getClass().getDeclaredMethod("createTransferable", JComponent.class);
    createTransferable.setAccessible(true);
    Transferable transferable = (Transferable)createTransferable.invoke(handler, component);

    Method exportDone = handler.getClass().getDeclaredMethod("exportDone", JComponent.class, Transferable.class, int.class);
    exportDone.setAccessible(true);
    exportDone.invoke(handler, component, transferable, ACTION_MOVE);
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
  private NlDesignSurface createDesignSurface(@NotNull NlLayoutType layoutType) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getModule()).thenReturn(myModule);
    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getLayoutType()).thenReturn(layoutType);
    when(surface.getConfiguration()).thenReturn(configuration);
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
    return myPanel.getSplitter().getFirstComponent().isVisible();
  }

  private static void fireComponentResize(@NotNull JComponent component) {
    ComponentEvent event = mock(ComponentEvent.class);
    for (ComponentListener listener : component.getComponentListeners()) {
      listener.componentResized(event);
    }
  }

  private int getCategoryWidth() {
    return myPanel.getSplitter().getFirstSize();
  }

  private void setCategoryWidth(@SuppressWarnings("SameParameterValue") int width) {
    myPanel.getSplitter().setFirstSize(width);
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
}
