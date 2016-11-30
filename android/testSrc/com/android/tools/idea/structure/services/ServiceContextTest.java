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
package com.android.tools.idea.structure.services;

import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.IntValueProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public final class ServiceContextTest {
  @Test
  public void dataModelCanRegisterActionsAndObservableValues() throws Exception {
    ServiceContext context = new ServiceContext("Gradle");

    {
      final IntValueProperty count = new IntValueProperty();
      Runnable incCount = new Runnable() {
        @Override
        public void run() {
          count.increment();
        }
      };

      final StringValueProperty title = new StringValueProperty("Title");
      final BoolValueProperty enabled = new BoolValueProperty(true);

      Runnable toggleEnabled = new Runnable() {
        @Override
        public void run() {
          enabled.invert();
        }
      };

      context.putValue("count", count);
      context.putValue("title", title);
      context.putValue("enabled", enabled);
      context.putAction("incCount", incCount);
      context.putAction("toggleEnabled", toggleEnabled);
    }

    IntValueProperty count = (IntValueProperty)context.getValue("count");
    StringValueProperty title = (StringValueProperty)context.getValue("title");
    BoolValueProperty enabled = (BoolValueProperty)context.getValue("enabled");

    assertThat(title.get()).isEqualTo("Title");

    assertThat(count.get()).isEqualTo(0);
    context.getAction("incCount").run();
    assertThat(count.get()).isEqualTo(1);

    assertThat(enabled.get()).isTrue();
    context.getAction("toggleEnabled").run();
    assertThat(enabled.get()).isFalse();
  }
}