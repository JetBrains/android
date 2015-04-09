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
package com.android.tools.idea.editors.hprof.tables;

import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Instance;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public abstract class HprofColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
  public static final HprofColumnInfo<DefaultMutableTreeNode, String> INSTANCE_ID_INFO =
    new HprofColumnInfo<DefaultMutableTreeNode, String>("Instance ID", String.class, SwingConstants.LEFT, 400, true) {
      @Override
      @NotNull
      public String valueOf(@NotNull DefaultMutableTreeNode node) {
        Instance instance = getUserInstance(node);
        return String.format("%s @ 0x%x16", instance.getClassObj().getClassName(), instance.getId());
      }
    };

  public static final HprofColumnInfo<DefaultMutableTreeNode, Integer> INSTANCE_SIZE_INFO =
    new HprofColumnInfo<DefaultMutableTreeNode, Integer>("Sizeof", Integer.class, SwingConstants.RIGHT, 80, true) {
      @Override
      @NotNull
      public Integer valueOf(@NotNull DefaultMutableTreeNode node) {
        return getUserInstance(node).getCompositeSize();
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return Integer.class;
      }
    };

  public static final HprofColumnInfo<DefaultMutableTreeNode, ClassObj> INSTANCE_DOMINATOR_INFO =
    new HprofColumnInfo<DefaultMutableTreeNode, ClassObj>("Dominator Class", ClassObj.class, SwingConstants.LEFT, 400, false) {
      @Override
      @Nullable
      public ClassObj valueOf(@NotNull DefaultMutableTreeNode node) {
        Instance dominator = getUserInstance(node).getImmediateDominator();
        return dominator == null ? null : dominator.getClassObj();
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return ClassObj.class;
      }
    };

  @NotNull private Class<Aspect> myClass;
  private int myHeaderJustification;
  private int myWidth;
  private boolean myEnabled;

  public HprofColumnInfo(@NotNull String name, @NotNull Class<Aspect> clazz, int headerJustification, int width, boolean enabled) {
    super(name);
    myHeaderJustification = headerJustification;
    myClass = clazz;
    myWidth = width;
    myEnabled = enabled;
  }

  @NotNull
  public static Instance getUserInstance(@NotNull Object row) {
    return (Instance)((DefaultMutableTreeNode)row).getUserObject();
  }

  @Override
  @NotNull
  public Class<?> getColumnClass() {
    return myClass;
  }

  public boolean getEnabled() {
    return myEnabled;
  }

  public void setEnabled(boolean enabled) {
    myEnabled = enabled;
  }

  public int getHeaderJustification() {
    return myHeaderJustification;
  }

  public int getColumnWidth() {
    return myWidth;
  }
}
