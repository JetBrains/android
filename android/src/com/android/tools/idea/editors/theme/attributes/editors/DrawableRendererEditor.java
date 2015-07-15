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

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.preview.AndroidThemePreviewPanel;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.swing.ui.SwatchComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.JBMenuItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for drawable attributes.
 */
public class DrawableRendererEditor extends GraphicalResourceRendererEditor {
  static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"></font>";

  @Nullable
  private RenderTask myRenderTask;
  private final AndroidThemePreviewPanel myPreviewPanel;
  private EditedStyleItem myItem;

  public DrawableRendererEditor(@NotNull ThemeEditorContext context, @NotNull AndroidThemePreviewPanel previewPanel, boolean isEditor) {
    super(context, isEditor);

    myRenderTask = configureRenderTask(context.getCurrentContextModule(), context.getConfiguration());

    final EditorClickListener editorClickListener = new EditorClickListener();
    if (isEditor) {
      myComponent.addActionListener(editorClickListener);
    }
    myComponent.addVariantComboAction(new AbstractAction("Add variation") {
      @Override
      public void actionPerformed(ActionEvent e) {
        editorClickListener.actionPerformed(e);
      }
    });
    myPreviewPanel = previewPanel;
  }

  @Nullable
  public static RenderTask configureRenderTask(@NotNull final Module module, @NotNull final Configuration configuration) {
    RenderTask result = null;
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      final RenderService service = RenderService.get(facet);
      result =
        service.createTask(null, configuration, new RenderLogger("ThemeEditorLogger", module), null);
    }

    return result;
  }

  @Override
  protected void updateComponent(@NotNull ThemeEditorContext context, @NotNull ResourceComponent component, @NotNull EditedStyleItem item) {
    assert context.getResourceResolver() != null;

    myItem = item;

    if (myRenderTask != null) {
      component.setSwatchIcons(SwatchComponent.imageListOf(myRenderTask.renderDrawableAllStates(item.getSelectedValue())));
    }

    String nameText =
      String.format(LABEL_TEMPLATE, ThemeEditorConstants.RESOURCE_ITEM_COLOR.toString(), ThemeEditorUtils.getDisplayHtml(item));
    component.setNameText(nameText);
    component.setValueText(item.getValue());
  }

  private class EditorClickListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      ChooseResourceDialog dialog = ThemeEditorUtils.getResourceDialog(myItem, myContext, ColorRendererEditor.DRAWABLES_ONLY);

      final String oldValue = myItem.getSelectedValue().getValue();

      dialog.setResourcePickerListener(new ChooseResourceDialog.ResourcePickerListener() {
        @Override
        public void resourceChanged(final @Nullable String resource) {
          myItem.getSelectedValue().setValue(resource == null ? oldValue : resource);
          myPreviewPanel.invalidateGraphicsRenderer();
        }
      });

      if (e.getSource() instanceof JBMenuItem) {
        // This has been triggered from the "Add variations" menu option so display location settings
        dialog.openLocationSettings();
      }

      dialog.show();

      // Restore the old value in the properties model
      myItem.getSelectedValue().setValue(oldValue);

      myEditorValue = null;
      if (dialog.isOK()) {
        String value = dialog.getResourceName();
        if (value != null) {
          myEditorValue = dialog.getResourceName();
        }
      }
      else {
        // User cancelled, clean up the preview
        myPreviewPanel.invalidateGraphicsRenderer();
      }

      if (myEditorValue == null) {
        DrawableRendererEditor.this.cancelCellEditing();
      }
      else {
        DrawableRendererEditor.this.stopCellEditing();
      }
    }
  }
}
