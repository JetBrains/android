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
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class PalettePanelTest extends AndroidTestCase {
  private CopyPasteManager myCopyPasteManager;
  private DependencyManager myDependencyManager;
  private PalettePanel myPanel;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myCopyPasteManager = mock(CopyPasteManager.class);
    myDependencyManager = mock(DependencyManager.class);
    registerApplicationComponent(CopyPasteManager.class, myCopyPasteManager);
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    myPanel = new PalettePanel(myDependencyManager);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myPanel);
      myCopyPasteManager = null;
      myDependencyManager = null;
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
    myPanel = new PalettePanel(myDependencyManager);
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
}
