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
package com.android.tools.adtui.ptable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class PTableItem {
  private PTableItem myParent;

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getNamespace());
  }

  public boolean equals(@Nullable Object other) {
    if (!(other instanceof PTableItem)) {
      return false;
    }
    PTableItem item = (PTableItem)other;
    return Objects.equals(getName(), item.getName()) &&
           Objects.equals(getNamespace(), item.getNamespace());
  }

  public boolean hasChildren() {
    return false;
  }

  public List<PTableItem> getChildren() {
    return Collections.emptyList();
  }

  @NotNull
  public String getChildLabel(@NotNull PTableItem item) {
    return item.getName();
  }

  @Nullable
  public PTableItem getParent() {
    return myParent;
  }

  public void setParent(PTableItem parent) {
    myParent = parent;
  }

  public boolean isExpanded() {
    return false;
  }

  public void setExpanded(boolean expanded) {
  }

  @Nullable
  public TableCellRenderer getCellRenderer() {
    return null;
  }

  @NotNull
  public StarState getStarState() {
    return StarState.NOT_STAR_ABLE;
  }

  public void setStarState(@NotNull StarState starState) {
  }

  @NotNull
  public abstract String getName();

  @Nullable
  public String getNamespace() {
    return null;
  }

  @Nullable
  public abstract String getValue();

  @Nullable
  public String getResolvedValue() {
    return getValue();
  }

  public boolean isDefaultValue(@Nullable String value) {
    return false;
  }

  public abstract void setValue(@Nullable Object value);

  @Nullable
  public String getTooltipText() {
    return null;
  }

  public boolean isEditable(int col) {
    return false;
  }

  @SuppressWarnings("UnusedParameters")
  public void mousePressed(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
  }

  @SuppressWarnings("UnusedParameters")
  public void mouseMoved(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
  }

  public int getColumnToEdit() {
    return 1;
  }
}
