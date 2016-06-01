/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.service.Context;
import com.android.tools.idea.editors.gfxtrace.service.ContextList;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomRangePath;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;

/**
 * Controller for the currently selected context.
 */
public class ContextController extends Controller implements AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new ContextController(editor).myComboBox;
  }

  private final JComboBox myComboBox = new ComboBox();

  @NotNull private Context mySelectedContext = Context.ALL;

  private boolean mySuspendUiUpdates;


  private ContextController(@NotNull GfxTraceEditor editor) {
    super(editor);
    editor.getAtomStream().addListener(this);
    myComboBox.setName("ContextCombo"); // for ui tests
    myComboBox.addItemListener(event -> {
      if (event.getStateChange() == ItemEvent.SELECTED) {
        selectContext((Context)(event.getItem()));
      }
    });
  }

  private void selectContext(@NotNull Context context) {
    if (mySuspendUiUpdates) {
      return;
    }
    myEditor.getAtomStream().selectContext(context);
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {}

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
    if (!atoms.isLoaded()) {
      return;
    }

    Context contextToSelect;
    mySuspendUiUpdates = true;
    try {
      ContextList contexts = atoms.getContexts();
      myComboBox.removeAllItems();
      if (contexts.count() == 1) {
        contextToSelect = contexts.getContexts()[0];
      }
      else {
        myComboBox.addItem(Context.ALL);
        contextToSelect = contexts.find(mySelectedContext.getID(), Context.ALL);
      }

      for (Context context : contexts) {
        myComboBox.addItem(context);
      }
      myComboBox.setSelectedItem(contextToSelect);
      myComboBox.setVisible(contexts.count() > 0);
    } finally {
      mySuspendUiUpdates = false;
    }

    selectContext(contextToSelect);
  }

  @Override
  public void onAtomsSelected(AtomRangePath path, Object source) {}

  @Override
  public void onContextChanged(@NotNull Context context) {
    mySelectedContext = context;
    myComboBox.setSelectedItem(context);
  }
}
