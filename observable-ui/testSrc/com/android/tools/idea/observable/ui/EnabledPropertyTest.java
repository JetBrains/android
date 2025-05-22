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
package com.android.tools.idea.observable.ui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.observable.CountListener;
import javax.swing.JButton;
import org.junit.Test;

public final class EnabledPropertyTest {
  @Test
  public void testEnabledProperty() {
    JButton button = new JButton();
    EnabledProperty enabledProperty = new EnabledProperty(button);
    CountListener listener = new CountListener();
    enabledProperty.addListener(listener);

    assertThat(enabledProperty.get()).isTrue();
    assertThat(listener.getCount()).isEqualTo(0);

    button.setEnabled(false);
    assertThat(enabledProperty.get()).isFalse();
    assertThat(listener.getCount()).isEqualTo(1);

    enabledProperty.set(true);
    assertThat(button.isEnabled()).isTrue();
    assertThat(listener.getCount()).isEqualTo(2);
  }
}