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
import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.Objects;

/**
 * Controller for the currently selected context.
 */
public class ContextController extends Controller implements AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new ContextController(editor).myComboBox;
  }

  private final JComboBox myComboBox = new ComboBox();

  private ContextList myContexts;
  private CapturePath myCapturePath;
  private Context mySelectedContext;

  private ContextController(@NotNull GfxTraceEditor editor) {
    super(editor);
    editor.getAtomStream().addListener(this);
    myComboBox.addItemListener(event -> {
      if (event.getStateChange() == ItemEvent.SELECTED) {
        selectContext((Context)(event.getItem()));
      }
    });
  }

  private void selectContext(Context context) {
    if (Objects.equals(context, mySelectedContext)) {
      return;
    }
    mySelectedContext = context;
    if (context != null) {
      myEditor.activatePath(myCapturePath.contexts().context(context.getID()), this);
    } else {
      myEditor.activatePath(myCapturePath.contexts(), this);
    }
  }

  @Override
  public void notifyPath(PathEvent event) {}

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {}

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
    myCapturePath = atoms.getPath().getCapture();
    myContexts = atoms.getContexts();
    myComboBox.removeAllItems();
    myComboBox.addItem(Context.ALL);
    for (Context context : myContexts) {
      myComboBox.addItem(context);
    }
    myComboBox.setSelectedItem(mySelectedContext);
    myComboBox.setVisible(myContexts.count() > 0);
  }

  @Override
  public void onAtomsSelected(AtomRangePath path) {}
}
