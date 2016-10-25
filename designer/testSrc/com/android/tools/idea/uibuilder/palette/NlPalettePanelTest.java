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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.model.NlLayoutType;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.ArgumentCaptor;

import java.awt.datatransfer.Transferable;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class NlPalettePanelTest extends AndroidTestCase {
  private CopyPasteManager myCopyPasteManager;
  private NlPalettePanel myPanel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Configuration configuration = mock(Configuration.class);
    when(configuration.getModule()).thenReturn(myModule);
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getLayoutType()).thenReturn(NlLayoutType.LAYOUT);
    when(surface.getConfiguration()).thenReturn(configuration);
    myCopyPasteManager = mock(CopyPasteManager.class);
    myPanel = new NlPalettePanel(getProject(), surface, myCopyPasteManager);
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
    assertThat(component.getRepresentation()).startsWith(("<TextView"));
  }
}
