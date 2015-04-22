/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.tables.instancestable;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InstanceDetailTreeRenderer extends ColoredTreeCellRenderer {
  @Override
  public void customizeCellRenderer(@NotNull JTree tree,
                                    @NotNull Object value,
                                    boolean selected,
                                    boolean expanded,
                                    boolean leaf,
                                    int row,
                                    boolean hasFocus) {
    final InstanceDetailTreeNode node = (InstanceDetailTreeNode)value;

    append(node.getField().getName(), XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);

    setIcon(PlatformIcons.FIELD_ICON);
  }
}
