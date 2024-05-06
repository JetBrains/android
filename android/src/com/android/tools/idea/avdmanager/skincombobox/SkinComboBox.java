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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import java.nio.file.Path;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SkinComboBox extends ComboBox<Skin> {
  public SkinComboBox(@Nullable Project project, @NotNull UnaryOperator<Path> map) {
    this(project, new SkinComboBoxModel(new Collector(map)::collect));
  }

  @VisibleForTesting
  public SkinComboBox(@Nullable Project project, @NotNull SkinComboBoxModel model) {
    super(model);

    setEditable(true);
    setEditor(new Editor(model, project));
  }

  public void load() {
    ((SkinComboBoxModel)dataModel).load();
  }

  @NotNull
  public Skin getSkin(@NotNull Path path) {
    return ((SkinComboBoxModel)dataModel).getSkin(path);
  }

  @NotNull
  @Override
  public Skin getSelectedItem() {
    var item = (Skin)super.getSelectedItem();
    assert item != null;

    return item;
  }
}
