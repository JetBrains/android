/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import javax.swing.AbstractButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DetailsPanelTest {
  private static final class TransferableArgumentMatcher implements ArgumentMatcher<Transferable> {
    private final @NotNull Object myExpectedData;

    private TransferableArgumentMatcher(@NotNull String expectedData) {
      myExpectedData = expectedData;
    }

    @Override
    public boolean matches(@NotNull Transferable actualTransferable) {
      try {
        return myExpectedData.equals(actualTransferable.getTransferData(DataFlavor.stringFlavor));
      }
      catch (UnsupportedFlavorException | IOException exception) {
        throw new AssertionError(exception);
      }
    }
  }

  @Test
  public void copyPropertiesToClipboardButtonDoClick() {
    // Arrange
    Clipboard clipboard = Mockito.mock(Clipboard.class);

    Toolkit toolkit = Mockito.mock(Toolkit.class);
    Mockito.when(toolkit.getSystemClipboard()).thenReturn(clipboard);

    InfoSection infoSection1 = new InfoSection("Info Section 1");
    InfoSection.setText(infoSection1.addNameAndValueLabels("Name 1"), "Value 1");
    InfoSection.setText(infoSection1.addNameAndValueLabels("Name 2"), "Value 2");

    InfoSection infoSection2 = new InfoSection("Info Section 2");
    InfoSection.setText(infoSection2.addNameAndValueLabels("Name 1"), "Value 1");
    InfoSection.setText(infoSection2.addNameAndValueLabels("Name 2"), "Value 2");

    var panel = new DetailsPanel("Device", null, () -> toolkit);
    panel.mySummarySection = new InfoSection("Summary Section");
    panel.myInfoSections.add(infoSection1);
    panel.myInfoSections.add(infoSection2);
    panel.init();

    AbstractButton button = panel.getCopyPropertiesToClipboardButton();

    // Act
    button.doClick();

    // Assert
    String data = String.format("Info Section 1%n" +
                                "Name 1 Value 1%n" +
                                "Name 2 Value 2%n" +
                                "%n" +
                                "Info Section 2%n" +
                                "Name 1 Value 1%n" +
                                "Name 2 Value 2%n");

    Mockito.verify(clipboard).setContents(ArgumentMatchers.argThat(new TransferableArgumentMatcher(data)), ArgumentMatchers.isNull());
  }
}
