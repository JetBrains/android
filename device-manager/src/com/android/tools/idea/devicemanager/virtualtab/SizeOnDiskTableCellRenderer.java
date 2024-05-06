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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.sdklib.devices.Storage.Unit;
import com.android.tools.adtui.table.Tables;
import com.google.common.annotations.VisibleForTesting;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.number.Precision;
import com.ibm.icu.util.MeasureUnit;
import com.intellij.ui.components.JBLabel;
import java.awt.Component;
import java.util.Locale;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class SizeOnDiskTableCellRenderer implements TableCellRenderer {
  private static final @NotNull LocalizedNumberFormatter GB_WHOLE_FORMATTER = NumberFormatter.withLocale(Locale.ROOT)
    .unit(MeasureUnit.GIGABYTE)
    .precision(Precision.fixedFraction(0));

  private static final @NotNull LocalizedNumberFormatter GB_FRACTION_FORMATTER = NumberFormatter.withLocale(Locale.ROOT)
    .unit(MeasureUnit.GIGABYTE)
    .precision(Precision.fixedFraction(1));

  private static final @NotNull LocalizedNumberFormatter MB_WHOLE_FORMATTER = NumberFormatter.withLocale(Locale.ROOT)
    .unit(MeasureUnit.MEGABYTE)
    .precision(Precision.fixedFraction(0));

  private static final @NotNull LocalizedNumberFormatter MB_FRACTION_FORMATTER = NumberFormatter.withLocale(Locale.ROOT)
    .unit(MeasureUnit.MEGABYTE)
    .precision(Precision.fixedFraction(1));

  private final JLabel myLabel = new JBLabel();

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    myLabel.setBackground(Tables.getBackground(table, selected));
    myLabel.setBorder(Tables.getBorder(selected, focused));
    myLabel.setForeground(Tables.getForeground(table, selected));
    myLabel.setText(toString((long)value));

    return myLabel;
  }

  @VisibleForTesting
  static @NotNull String toString(long sizeOnDisk) {
    LocalizedNumberFormatter formatter;
    double sizeOnDiskAsDouble;

    if (sizeOnDisk >= Unit.GiB.getNumberOfBytes()) {
      sizeOnDiskAsDouble = (double)sizeOnDisk / Unit.GiB.getNumberOfBytes();
      formatter = sizeOnDiskAsDouble > 9.94 ? GB_WHOLE_FORMATTER : GB_FRACTION_FORMATTER;
    }
    else {
      sizeOnDiskAsDouble = (double)sizeOnDisk / Unit.MiB.getNumberOfBytes();
      formatter = sizeOnDiskAsDouble > 9.94 ? MB_WHOLE_FORMATTER : MB_FRACTION_FORMATTER;
    }

    return formatter.format(sizeOnDiskAsDouble).toString();
  }
}
