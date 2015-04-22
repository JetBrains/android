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
import com.android.tools.perflib.heap.RootObj;
import com.intellij.util.ui.ColumnInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public abstract class HprofColumnInfo<Item, Aspect> extends ColumnInfo<Item, Aspect> {
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

  @Nullable
  public static Instance getUserInstance(@NotNull Object row) {
    return (Instance)((DefaultMutableTreeNode)row).getUserObject();
  }

  @NotNull
  public static HprofColumnInfo<HprofInstanceNode, Instance> getInstanceIdInfo() {
    return new HprofColumnInfo<HprofInstanceNode, Instance>("Instance ID", Instance.class, SwingConstants.LEFT, 400, true) {
      @Override
      @Nullable
      public Instance valueOf(@NotNull HprofInstanceNode node) {
        return node.getInstance();
      }
    };
  }

  @NotNull
  public static HprofColumnInfo<HprofInstanceNode, Integer> getInstanceSizeInfo() {
    return new HprofColumnInfo<HprofInstanceNode, Integer>("Sizeof", Integer.class, SwingConstants.RIGHT, 80, true) {
      @Override
      @Nullable
      public Integer valueOf(@NotNull HprofInstanceNode node) {
        if (node.isPrimitive()) {
          return null;
        }
        Instance instance = node.getInstance();
        return instance == null ? null : instance.getSize();
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return Integer.class;
      }
    };
  }

  @NotNull
  public static HprofColumnInfo<HprofInstanceNode, ClassObj> getInstanceDominatorInfo() {
    return new HprofColumnInfo<HprofInstanceNode, ClassObj>("Dominator Class", ClassObj.class, SwingConstants.LEFT, 400, false) {
      @Override
      @Nullable
      public ClassObj valueOf(@NotNull HprofInstanceNode node) {
        if (node.isPrimitive()) {
          return null;
        }
        Instance instance = node.getInstance();
        if (instance == null) {
          return null;
        }
        Instance dominator = instance.getImmediateDominator();
        return dominator == null || dominator instanceof RootObj ? null : dominator.getClassObj();
      }

      @Override
      @NotNull
      public Class<?> getColumnClass() {
        return ClassObj.class;
      }
    };
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
