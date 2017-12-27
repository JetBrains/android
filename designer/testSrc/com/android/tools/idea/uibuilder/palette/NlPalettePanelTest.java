/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.ArgumentCaptor;

import java.awt.datatransfer.Transferable;

import static com.android.tools.idea.uibuilder.palette.NlPalettePanel.PALETTE_MODE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlPalettePanelTest extends AndroidTestCase {
  private CopyPasteManager myCopyPasteManager;
  private NlDesignSurface mySurface;
  private NlPalettePanel myPanel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    registerApplicationComponent(PropertiesComponent.class, new PropertiesComponentMock());
    Configuration configuration = mock(Configuration.class);
    when(configuration.getModule()).thenReturn(myModule);
    mySurface = mock(NlDesignSurface.class);
    when(mySurface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(mySurface.getConfiguration()).thenReturn(configuration);
    myCopyPasteManager = mock(CopyPasteManager.class);
    myPanel = new NlPalettePanel(getProject(), mySurface, myCopyPasteManager);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myPanel);
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
    myPanel.requestFocus();
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

  public void testSupportsFiltering() {
    assertThat(myPanel.supportsFiltering()).isTrue();
  }

  public void testSetFilter() {
    assertThat(myPanel.getTreeGrid().getFilter()).isEmpty();
    myPanel.setFilter("button");
    assertThat(myPanel.getTreeGrid().getFilter()).isEqualTo("button");
  }

  public void testDefaultMode() {
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.ICON_AND_NAME);
  }

  public void testInitialModeIsReadFromOptions() {
    PropertiesComponent.getInstance().setValue(PALETTE_MODE, PaletteMode.LARGE_ICONS.toString());
    Disposer.dispose(myPanel);
    myPanel = new NlPalettePanel(getProject(), mySurface, myCopyPasteManager);
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.LARGE_ICONS);
  }

  public void testInitialModeFromMalformedOptionValueIsIgnored() {
    PropertiesComponent.getInstance().setValue(PALETTE_MODE, "malformed");
    Disposer.dispose(myPanel);
    myPanel = new NlPalettePanel(getProject(), mySurface, myCopyPasteManager);
    assertThat(myPanel.getMode()).isEqualTo(PaletteMode.ICON_AND_NAME);
  }

  public void testInitialModeIsSavedToOptions() {
    assertThat(PropertiesComponent.getInstance().getValue(PALETTE_MODE)).isNull();
    myPanel.setMode(PaletteMode.SMALL_ICONS);
    assertThat(PropertiesComponent.getInstance().getValue(PALETTE_MODE)).isEqualTo(PaletteMode.SMALL_ICONS.toString());
  }
}
