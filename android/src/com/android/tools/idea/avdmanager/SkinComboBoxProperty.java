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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.avdmanager.skincombobox.NoSkin;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBox;
import com.android.tools.idea.observable.core.OptionalProperty;
import java.io.File;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

final class SkinComboBoxProperty extends OptionalProperty<File> {
  @NotNull
  private final SkinComboBox myComboBox;

  SkinComboBoxProperty(@NotNull SkinComboBox comboBox) {
    comboBox.addActionListener(event -> notifyInvalidated());
    myComboBox = comboBox;
  }

  @Override
  protected void setDirectly(@NotNull Optional<File> file) {
    var skin = file
      .map(File::toPath)
      .map(myComboBox::getSkin)
      .orElse(NoSkin.INSTANCE);

    myComboBox.addItem(skin);
    myComboBox.setSelectedItem(skin);
  }

  @NotNull
  @Override
  public Optional<File> get() {
    return Optional.of(myComboBox.getSelectedItem().path().toFile());
  }
}
