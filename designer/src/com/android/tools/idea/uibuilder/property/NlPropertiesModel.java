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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.Iterables;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.AbstractTableModel;
import java.util.Collections;
import java.util.List;

public class NlPropertiesModel extends AbstractTableModel {
  @Nullable private XmlTag myTag;
  @NotNull private List<NlProperty> myProperties = Collections.emptyList();

  public void update(@NotNull Iterable<NlComponent> selection) {
    // TODO: handle multiple selections: show properties common to all selections
    NlComponent first = Iterables.getFirst(selection, null);
    myTag = first == null ? null : first.tag;
    if (myTag == null) {
      myProperties = Collections.emptyList();
      fireTableDataChanged();
      return;
    }

    // Obtaining the properties, especially the first time around on a big project
    // can take close to a second, so we do it on a separate thread..
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<NlProperty> properties = NlProperties.getInstance().getProperties(myTag);
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myProperties = properties;
            fireTableDataChanged();
          }
        });
      }
    });
  }

  @Override
  public int getRowCount() {
    return myProperties.size();
  }

  @Override
  public int getColumnCount() {
    return 2;
  }

  @Override
  public Object getValueAt(int row, int column) {
    if (column == 1) {
      return "";
    }
    else {
      return row < myProperties.size() ? myProperties.get(row) : null;
    }
  }
}