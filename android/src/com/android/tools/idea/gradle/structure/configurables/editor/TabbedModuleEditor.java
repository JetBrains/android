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
package com.android.tools.idea.gradle.structure.configurables.editor;

import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class TabbedModuleEditor extends ModuleEditor {
  @NonNls private static final String SELECTED_EDITOR_KEY = TabbedModuleEditor.class.getName() + ".selectedEditor";

  private TabbedPaneWrapper myTabbedPane;

  public TabbedModuleEditor(@NotNull ModuleMergedModel model) {
    super(model);
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    myTabbedPane = new TabbedPaneWrapper(this);

    for (ModuleConfigurationEditor editor : getEditors()) {
      myTabbedPane.addTab(editor.getDisplayName(), editor.createComponent());
      editor.reset();
    }
    restoreSelectedEditor();

    myTabbedPane.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        saveSelectedEditor();
        History history = getHistory();
        if (history != null) {
          history.pushQueryPlace();
        }
      }
    });
    return myTabbedPane.getComponent();
  }

  @Override
  protected void restoreSelectedEditor() {
    selectEditor(getSavedSelectedEditor());
  }

  @Override
  @Nullable
  public ModuleConfigurationEditor getSelectedEditor() {
    if (myTabbedPane == null) {
      return null;
    }
    String title = myTabbedPane.getSelectedTitle();
    if (title == null) {
      return null;
    }
    return getEditor(title);
  }

  private int getEditorTabIndex(@NotNull String editorName) {
    if (myTabbedPane != null) {
      final int tabCount = myTabbedPane.getTabCount();
      for (int index = 0; index < tabCount; index++) {
        if (editorName.equals(myTabbedPane.getTitleAt(index))) {
          return index;
        }
      }
    }
    return -1;
  }

  @Override
  @Nullable
  public ModuleConfigurationEditor getEditor(@NotNull String displayName) {
    return null;
  }

  @Override
  protected void disposeCenterPanel() {
    if (myTabbedPane != null) {
      saveSelectedEditor();
      myTabbedPane = null;
    }
  }

  @Nullable
  private String getSelectedTabName() {
    if (myTabbedPane != null) {
      int index = myTabbedPane.getSelectedIndex();
      return index != -1 ? myTabbedPane.getTitleAt(index) : null;
    }
    return null;
  }

  @Override
  public ActionCallback navigateTo(@Nullable Place place, boolean requestFocus) {
    if (place != null) {
      selectEditor((String)place.getPath(SELECTED_EDITOR_NAME));
    }
    return ActionCallback.DONE;
  }

  @Override
  public void selectEditor(@Nullable String displayName) {
    if (displayName != null) {
      getPanel();
      int editorTabIndex = getEditorTabIndex(displayName);
      if (editorTabIndex >= 0 && editorTabIndex < myTabbedPane.getTabCount()) {
        myTabbedPane.setSelectedIndex(editorTabIndex);
        saveSelectedEditor();
      }
    }
  }

  private void saveSelectedEditor() {
    String selectedTabName = getSelectedTabName();
    if (selectedTabName != null) {
      // already disposed
      PropertiesComponent.getInstance().setValue(SELECTED_EDITOR_KEY, selectedTabName);
    }
  }

  @Override
  public void queryPlace(@NotNull Place place) {
    place.putPath(SELECTED_EDITOR_NAME, getSavedSelectedEditor());
  }

  @Nullable
  private static String getSavedSelectedEditor() {
    return PropertiesComponent.getInstance().getValue(SELECTED_EDITOR_KEY);
  }
}
