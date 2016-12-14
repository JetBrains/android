/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.StringResource;
import com.android.tools.idea.rendering.Locale;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import javax.swing.RowFilter.Entry;

import static org.junit.Assert.assertFalse;

public final class NeedsTranslationForLocaleRowFilterTest {
  @Test
  public void include() {
    StringResource resource = new StringResource("key");
    resource.setTranslatable(false);

    StringResourceTableModel model = Mockito.mock(StringResourceTableModel.class);
    Mockito.when(model.getStringResourceAt(0)).thenReturn(resource);

    @SuppressWarnings("unchecked")
    Entry<StringResourceTableModel, Integer> entry = (Entry<StringResourceTableModel, Integer>)Mockito.mock(Entry.class);

    Mockito.when(entry.getModel()).thenReturn(model);
    Mockito.when(entry.getIdentifier()).thenReturn(0);

    RowFilter<StringResourceTableModel, Integer> filter = new NeedsTranslationForLocaleRowFilter(Locale.create("ar"));
    assertFalse(filter.include(entry));
  }
}
