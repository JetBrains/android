/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;

import com.intellij.ide.ui.laf.darcula.DarculaTableSelectedCellHighlightBorder;
import java.util.function.Function;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class TablesTest {
  @Test
  public void getBorderUnfocused() {
    // Arrange
    Border expectedBorder = new EmptyBorderUIResource(2, 3, 2, 3);
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", expectedBorder);

    // Act
    Object actualBorder = Tables.getBorder(false, false, getBorder);

    // Assert
    assertEquals(expectedBorder, actualBorder);
  }

  @Test
  public void getBorderSelected() {
    // Arrange
    Border expectedBorder = new DarculaTableSelectedCellHighlightBorder();
    Function<Object, Border> getBorder = mockGetBorder("Table.focusSelectedCellHighlightBorder", expectedBorder);

    // Act
    Object actualBorder = Tables.getBorder(true, true, getBorder);

    // Assert
    assertEquals(expectedBorder, actualBorder);
  }

  private static @NotNull Function<Object, Border> mockGetBorder(@NotNull Object key, @NotNull Border border) {
    @SuppressWarnings("unchecked")
    Function<Object, Border> getBorder = Mockito.mock(Function.class);

    Mockito.when(getBorder.apply(key)).thenReturn(border);

    return getBorder;
  }

  @Test
  public void getBorder() {
    // Act
    Object border = Tables.getBorder(false, true);

    // Assert
    assertEquals(UIManager.getBorder("Table.focusCellHighlightBorder"), border);
  }
}
