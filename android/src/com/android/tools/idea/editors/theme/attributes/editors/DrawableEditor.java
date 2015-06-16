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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.resources.ResourceType;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.RenderTask;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DrawableEditor extends TypedCellEditor<EditedStyleItem, String> implements ThemeEditorContext.ChangeListener {
  private static final ResourceType[] DRAWABLE_TYPE = new ResourceType[] { ResourceType.DRAWABLE };

  private final DrawableComponent myComponent;

  private EditedStyleItem myEditedItem;
  private @Nullable String myResultValue;
  private RenderTask myRenderTask;
  private final ThemeEditorContext myContext;

  public DrawableEditor(final @NotNull ThemeEditorContext context) {
    myRenderTask = DrawableRenderer.configureRenderTask(context);
    myContext = context;
    context.addChangeListener(this);

    myComponent = new DrawableComponent();
    myComponent.addActionListener(new EditorClickListener());
  }

  @Override
  public Component getEditorComponent(JTable table, EditedStyleItem value, boolean isSelected, int row, int column) {
    myEditedItem = value;
    myComponent.configure(myEditedItem, myRenderTask);
    return myComponent;
  }

  @Override
  public String getEditorValue() {
    return myResultValue;
  }

  @Override
  public void onNewConfiguration(ThemeEditorContext context) {
    myRenderTask = DrawableRenderer.configureRenderTask(context);
  }

  private class EditorClickListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myContext.getCurrentThemeModule(), DRAWABLE_TYPE, myEditedItem.getValue(), null);

      dialog.show();

      if (dialog.isOK()) {
        myResultValue = dialog.getResourceName();
        stopCellEditing();
      } else {
        myResultValue = null;
        cancelCellEditing();
      }
    }
  }
}
