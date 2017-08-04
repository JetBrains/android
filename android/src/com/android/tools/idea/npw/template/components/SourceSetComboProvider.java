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
package com.android.tools.idea.npw.template.components;

import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.intellij.ui.ListCellRendererWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Provides a combobox which presents the user with a list of source sets.
 *
 * @see AndroidSourceSet
 */
// Disable JComboBox warnings. We have no choice but to use it as we're stuck with JDK6
@SuppressWarnings({"UndesirableClassUsage", "unchecked"})
public final class SourceSetComboProvider extends ComponentProvider<JComboBox> {
  @NotNull private final List<AndroidSourceSet> mySourceSets;

  public SourceSetComboProvider(@NotNull List<AndroidSourceSet> sourceSets) {
    mySourceSets = sourceSets;
  }

  @NotNull
  @Override
  public JComboBox createComponent() {
    DefaultComboBoxModel comboBoxModel = new DefaultComboBoxModel();
    for (AndroidSourceSet sourceSet : mySourceSets) {
      comboBoxModel.addElement(sourceSet);
    }

    JComboBox sourceSetCombo = new JComboBox(comboBoxModel);
    sourceSetCombo.setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        setText(((AndroidSourceSet)value).getName());
      }
    });
    sourceSetCombo.setToolTipText("<html>The source set within which to generate new project files.<br>" +
                                  "If you specify a source set that does not yet exist on disk, a folder will be created for it.</html>");
    return sourceSetCombo;
  }

  @Nullable
  @Override
  public AbstractProperty<?> createProperty(@NotNull JComboBox sourceSetCombo) {
    return new SelectedItemProperty<AndroidSourceSet>(sourceSetCombo);
  }
}

