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
package com.android.tools.idea.ui.properties.swing;

import com.android.tools.idea.ui.properties.CountListener;
import com.intellij.openapi.util.EmptyRunnable;
import org.junit.Test;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;

public final class VisiblePropertyTest {
  @Test
  public void testVisibleProperty() throws Exception {
    JLabel label = new JLabel();
    VisibleProperty visibleProperty = new VisibleProperty(label);
    CountListener listener = new CountListener();
    visibleProperty.addListener(listener);

    assertThat(visibleProperty.get()).isTrue();
    assertThat(listener.getCount()).isEqualTo(0);

    label.setVisible(false);
    // Swing enqueues the visibility changed event, so we need to wait for it
    SwingUtilities.invokeAndWait(EmptyRunnable.INSTANCE);
    assertThat(visibleProperty.get()).isFalse();
    assertThat(listener.getCount()).isEqualTo(1);

    visibleProperty.set(true);
    assertThat(label.isVisible()).isTrue();
    assertThat(listener.getCount()).isGreaterThan(1);
  }
}