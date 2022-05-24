/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio.wizard;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

public class DensityAwareFileComparatorTest {
  /**
   * Checks contract compliance (https://issuetracker.google.com/110312021).
   */
  @Test
  public void testContract() {
    Comparator<File> comparator = new DensityAwareFileComparator(Collections.emptySet());
    List<File> files = Arrays.asList(
        new File("/res/mipmap-mdpi"),
        new File("/res/drawable-hdpi"),
        new File("/res/mipmap")
    );
    files.sort(comparator);
    assertThat(comparator.compare(files.get(0), files.get(1))).isLessThan(0);
    assertThat(comparator.compare(files.get(1), files.get(2))).isLessThan(0);
    assertThat(comparator.compare(files.get(0), files.get(2))).isLessThan(0);
  }
}
