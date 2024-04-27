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

import com.android.sdklib.AndroidVersion;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class EditorTest {
  @Test
  public void choosePath() {
    // Arrange
    var home = System.getProperty("user.home");

    var platformSkinPath = Path.of(home, "Android", "Sdk", "platforms", "android-32", "skins", "HVGA");
    var platformSkin = new PlatformSkin(platformSkinPath, new AndroidVersion(32));

    var model = new SkinComboBoxModel(List.of(NoSkin.INSTANCE, platformSkin));
    var chosenPath = Path.of(home, "skin");

    var editor = new Editor(model, null, project -> Optional.of(chosenPath));

    // Act
    editor.choosePath();

    // Assert
    var chosenSkin = new DefaultSkin(chosenPath);

    assertEquals(List.of(NoSkin.INSTANCE, platformSkin, chosenSkin), model.getSkins());
    assertEquals(chosenSkin, model.getSelectedItem());
  }
}
