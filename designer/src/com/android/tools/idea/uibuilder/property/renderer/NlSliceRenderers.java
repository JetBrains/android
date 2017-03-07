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
package com.android.tools.idea.uibuilder.property.renderer;

import com.android.tools.idea.uibuilder.property.ptable.PNameRenderer;
import com.android.tools.idea.uibuilder.property.ptable.PTableCellRendererProvider;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import org.jetbrains.annotations.NotNull;

import javax.swing.table.TableCellRenderer;

public class NlSliceRenderers implements PTableCellRendererProvider {
  private static NlSliceRenderers ourInstance = new NlSliceRenderers();

  private final NlSliceNameRenderer mySliceNameRenderer;
  private final NlSliceValueRenderer mySliceValueRenderer;

  public static NlSliceRenderers getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlSliceRenderers();
    }
    return ourInstance;
  }

  private NlSliceRenderers() {
    mySliceNameRenderer = new NlSliceNameRenderer();
    mySliceValueRenderer = new NlSliceValueRenderer();
  }

  @NotNull
  @Override
  public PNameRenderer getNameCellRenderer(@NotNull PTableItem item) {
    return mySliceNameRenderer;
  }

  @NotNull
  @Override
  public TableCellRenderer getValueCellRenderer(@NotNull PTableItem item) {
    return mySliceValueRenderer;
  }
}
