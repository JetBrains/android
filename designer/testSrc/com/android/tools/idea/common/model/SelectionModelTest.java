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
package com.android.tools.idea.common.model;

import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.openapi.util.Ref;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class SelectionModelTest {
  @Test
  public void testBasic() {
    SelectionModel model = new SelectionModel();
    assertTrue(model.isEmpty());
    final Ref<Boolean> called = new Ref<Boolean>(false);

    SelectionListener listener = (model1, selection) -> {
      assertFalse(called.get());
      called.set(true);
    };
    model.addListener(listener);
    assertFalse(model.getSelection().iterator().hasNext());
    model.setSelection(Collections.emptyList());
    assertFalse(called.get()); // no change; shouldn't notify

    NlComponent component1 = mock(NlComponent.class);
    NlComponent component2 = mock(NlComponent.class);

    model.toggle(component1);
    assertTrue(called.get());
    called.set(false);
    assertEquals(Collections.singletonList(component1), model.getSelection());
    assertEquals(component1, model.getPrimary());

    // Check multi-selection
    model.setSelection(Arrays.asList(component1, component2), component2);
    assertTrue(called.get());
    called.set(false);
    assertEquals(Arrays.asList(component1, component2), model.getSelection());
    assertSame(component2, model.getPrimary());

    model.toggle(component2);
    assertTrue(called.get());
    called.set(false);
    assertEquals(Collections.singletonList(component1), model.getSelection());
    assertNull(model.getPrimary());

    model.toggle(component1);
    assertTrue(called.get());
    called.set(false);
    assertNull(model.getPrimary());
    assertTrue(model.isEmpty());
    assertTrue(model.getSelection().isEmpty());

    model.toggle(component1);
    assertTrue(called.get());
    called.set(false);

    model.clear();
    assertTrue(called.get());
    called.set(false);
    assertNull(model.getPrimary());

    // Make sure we don't get notified once listener is unregistered
    model.removeListener(listener);
    model.toggle(component1);
    assertFalse(called.get());
  }
}