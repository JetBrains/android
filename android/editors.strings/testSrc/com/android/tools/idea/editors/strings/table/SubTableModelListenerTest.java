/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import org.junit.Test;
import org.mockito.Mockito;

public final class SubTableModelListenerTest {
  @Test
  public void tableChangedDelegateColumnIndexEqualsAllColumns() {
    TableModel delegateModel = new DefaultTableModel(1, 4);
    SubTableModel model = new SubTableModel(delegateModel, () -> 0, () -> delegateModel.getColumnCount());
    TableModelListener delegateListener = Mockito.mock(TableModelListener.class);

    new SubTableModelListener(model, delegateListener).tableChanged(new TableModelEvent(delegateModel));
    Mockito.verify(delegateListener).tableChanged(Mockito.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }
}
