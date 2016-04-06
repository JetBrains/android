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
package com.android.tools.idea.ui.properties.expressions.list;

import com.android.tools.idea.ui.properties.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Locale;

import static org.fest.assertions.Assertions.assertThat;

public final class ListExpressionsTest {

  @Test
  public void testMapExpression() {
    ObservableList<String> strings = new ObservableList<>();
    strings.add("First");
    strings.add("seconD");
    strings.add("thIrd");

    MapExpression<String, String> toUpper = new MapExpression<String, String>(strings) {
      @NotNull
      @Override
      protected String transform(@NotNull String srcElement) {
        return srcElement.toUpperCase(Locale.US);
      }
    };

    assertThat(toUpper.get()).containsExactly("FIRST", "SECOND", "THIRD");

    strings.add("fourth");

    assertThat(toUpper.get()).containsExactly("FIRST", "SECOND", "THIRD", "FOURTH");
  }

  @Test
  public void testSizeExpression() {
    ObservableList<Integer> numbers = new ObservableList<>();
    SizeExpression count = new SizeExpression(numbers);

    assertThat(count.get()).isEqualTo(0);

    numbers.add(1);
    numbers.add(2);

    assertThat(count.get()).isEqualTo(2);

    numbers.clear();

    assertThat(count.get()).isEqualTo(0);
  }
}
