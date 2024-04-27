/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.skincombobox;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.avdmanager.SkinUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class NoSkinTest {
  @Test
  public void merge() {
    // Arrange
    var skin = new DefaultSkin(SkinUtils.noSkin());

    // Act
    var actualSkin = NoSkin.INSTANCE.merge(skin);

    // Assert
    assertEquals(NoSkin.INSTANCE, actualSkin);
  }
}
