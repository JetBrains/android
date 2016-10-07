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
package com.android.tools.idea.npw;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AddAndroidActivityPath}
 */
public class AddAndroidActivityPathTest {
  @Test
  public void testGetRelativePackageName() {
    assertThat(AddAndroidActivityPath.removeCommonPackagePrefix("com.google", "com.google.android"))
        .isEqualTo("android");
    assertThat(AddAndroidActivityPath.removeCommonPackagePrefix("com.google.android",
        "com.google.android"))
        .isEqualTo("");
    assertThat(AddAndroidActivityPath.removeCommonPackagePrefix("com.google.android",
        "not.google.android"))
        .isEqualTo("not.google.android");
  }
}
