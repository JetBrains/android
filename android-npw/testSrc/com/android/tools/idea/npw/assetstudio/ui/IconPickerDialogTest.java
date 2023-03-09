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
package com.android.tools.idea.npw.assetstudio.ui;

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.vectordrawable.VdIcon;
import com.android.tools.idea.material.icons.common.MaterialIconsMetadataUrlProvider;
import com.android.tools.idea.material.icons.common.MaterialIconsUrlProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IconPickerDialogTest extends LightPlatformTestCase {

  private static final String ICONS_PATH = "images/material/icons/";

  public void testDialogWithInitialIcon() throws IOException {
    URL iconUrl = IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "style1/my_icon_2/style1_my_icon_2_24.xml");
    assertThat(iconUrl).isNotNull();

    VdIcon initialIcon = new VdIcon(iconUrl);
    IconPickerDialog pickerDialog = getInitializedIconPickerDialog(initialIcon);

    assertThat(pickerDialog.getSelectedIcon().getName()).isEqualTo("style1_my_icon_2_24.xml");
    pickerDialog.close(DialogWrapper.CLOSE_EXIT_CODE);
  }

  public void testSelectedIconAfterStyleChange() throws IOException {
    URL style1IconUrl = IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "style1/my_icon_2/style1_my_icon_2_24.xml");
    URL style2IconUrl = IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "style2/my_icon_1/style2_my_icon_1_24.xml");

    VdIcon initialIcon = new VdIcon(style1IconUrl);
    IconPickerDialog pickerDialog = getInitializedIconPickerDialog(initialIcon);

    UIUtil.findComponentsOfType(pickerDialog.createCenterPanel(), JComboBox.class).forEach(box -> {
      Object item = box.getSelectedItem();
      if (item instanceof String && item.equals("Style 1")) {
        // Select the "Style 2" style.
        box.setSelectedIndex(1);
      }
    });
    // Selection changes to icon 1 since the style is part of the name, so it technically cannot find an icon with the same name as before
    assertThat(pickerDialog.getSelectedIcon().getURL()).isEqualTo(style2IconUrl);
    pickerDialog.close(DialogWrapper.CLOSE_EXIT_CODE);
  }

  public void testSelectedIconAfterCategoryChange() throws IOException {
    URL category1IconUrl = IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "style1/my_icon_1/style1_my_icon_1_24.xml");
    URL category3IconUrl = IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "style1/my_icon_2/style1_my_icon_2_24.xml");

    VdIcon initialIcon = new VdIcon(category1IconUrl);
    IconPickerDialog pickerDialog = getInitializedIconPickerDialog(initialIcon);

    UIUtil.findComponentsOfType(pickerDialog.createCenterPanel(), JComboBox.class).forEach(box -> {
      Object item = box.getSelectedItem();
      if (item instanceof String && item.equals("All")) {
        // Select category: "Category3" which should only have 1 icon: 'my_icon_2.xml'.
        box.setSelectedIndex(3);
      }
    });
    assertThat(pickerDialog.getSelectedIcon().getURL()).isEqualTo(category3IconUrl);
    pickerDialog.close(DialogWrapper.CLOSE_EXIT_CODE);
  }

  public void testFiltering() {
    IconPickerDialog dialog = getInitializedIconPickerDialog(null);

    dialog.setFilter("icon 1");
    assertThat(tableToString(dialog.getTable())).isEqualTo(
      "style1 my icon 1                                                                                                        \n"
    );

    dialog.setFilter("icon 2");
    assertThat(tableToString(dialog.getTable())).isEqualTo(
      "style1 my icon 2                                                                                                        \n"
    );
    dialog.close(DialogWrapper.CLOSE_EXIT_CODE);
  }

  @NotNull
  public static String tableToString(@NotNull JTable table) {
    return tableToString(table, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 20);
  }

  @NotNull
  public static String tableToString(@NotNull JTable table, int startRow, int endRow, int startColumn, int endColumn,
                                     int cellWidth) {
    StringBuilder sb = new StringBuilder();

    String formatString = "%-" + Integer.toString(cellWidth) + "s";
    for (int row = Math.max(0, startRow); row < Math.min(endRow, table.getRowCount()); row++) {
      for (int column = Math.max(0, startColumn); column < Math.min(table.getColumnCount(), endColumn); column++) {
        Object value = table.getValueAt(row, column);
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component component = renderer.getTableCellRendererComponent(table, value, false, false, row, column);

        JLabel label = (JLabel)component;
        assertThat(label.getText()).isEmpty();
        String cell = label.getAccessibleContext().getAccessibleName();
        if (cell.length() > cellWidth) {
          cell = cell.substring(0, cellWidth - 3) + "...";
        }
        sb.append(String.format(formatString, cell));
      }
      sb.append('\n');
    }

    return sb.toString();
  }

  private static IconPickerDialog getInitializedIconPickerDialog(@Nullable VdIcon initialIcon) {
    IconPickerDialog dialog = new IconPickerDialog(initialIcon, new TestUrlMetadataProvider(), new TestUrlLoaderProvider());
    JComponent pickerPanel = dialog.createCenterPanel();
    pickerPanel.setVisible(true);

    // The icons table is initialized asynchronously, so before doing any tests, lets wait for the table to get populated.
    WaitFor wait = new WaitFor(3000) {
      @Override
      protected boolean condition() {
        // Dispatch pending EDT tasks, do not block the thread while waiting.
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        JTable table = UIUtil.findComponentOfType(pickerPanel, JTable.class);
        JComboBox box = UIUtil.findComponentOfType(pickerPanel, JComboBox.class);
        boolean populatedTable = table != null && table.getValueAt(0, 0) != null;
        boolean populatedComboBox = box != null && box.isEnabled() && box.getItemCount() >= 1;
        return populatedComboBox && populatedTable;
      }
    };
    assertTrue(wait.isConditionRealized());
    return dialog;
  }

  private static class TestUrlLoaderProvider implements MaterialIconsUrlProvider {
    @Nullable
    @Override
    public URL getStyleUrl(@NotNull String style) {
      return IconPickerDialogTest.class.getClassLoader().getResource(getStylePath(style));
    }

    @Nullable
    @Override
    public URL getIconUrl(@NotNull String style, @NotNull String iconName, @NotNull String iconFileName) {
      return IconPickerDialogTest.class.getClassLoader().getResource(getStylePath(style) + iconName + "/" + iconFileName);
    }
  }

  private static class TestUrlMetadataProvider implements MaterialIconsMetadataUrlProvider {
    @Nullable
    @Override
    public URL getMetadataUrl() {
      return IconPickerDialogTest.class.getClassLoader().getResource(ICONS_PATH + "icons_metadata_test.txt");
    }
  }

  private static String getStylePath(@NotNull String style) {
    return ICONS_PATH + style.toLowerCase(Locale.US).replace(" ", "") + "/";
  }
}