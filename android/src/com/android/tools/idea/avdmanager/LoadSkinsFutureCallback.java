/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.google.common.util.concurrent.FutureCallback;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.CollectionComboBoxModel;
import java.io.File;
import java.util.List;
import javax.swing.JComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class LoadSkinsFutureCallback implements FutureCallback<List<File>> {
  private final @NotNull SkinChooser myChooser;
  private final @Nullable Object mySkin;

  LoadSkinsFutureCallback(@NotNull SkinChooser chooser, @Nullable Object skin) {
    myChooser = chooser;
    mySkin = skin;
  }

  @Override
  public void onSuccess(@NotNull List<@NotNull File> skins) {
    JComboBox<File> comboBox = myChooser.getComboBox();
    comboBox.setModel(new CollectionComboBoxModel<>(skins));

    if (mySkin != null) {
      comboBox.setSelectedItem(mySkin);
    }
  }

  @Override
  public final void onFailure(@NotNull Throwable throwable) {
    Logger.getInstance(LoadSkinsFutureCallback.class).warn(throwable);
    myChooser.getComboBox().setModel(new CollectionComboBoxModel<>(List.of(SkinChooser.FAILED_TO_LOAD_SKINS)));
  }
}
