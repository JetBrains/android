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
import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.swing.ui.SwatchComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Class that implements a {@link javax.swing.JTable} renderer and editor for drawable attributes.
 */
public class DrawableRendererEditor extends GraphicalResourceRendererEditor {
  static final String LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"></font>";

  private static final ResourceType[] DRAWABLE_TYPE = new ResourceType[]{ResourceType.DRAWABLE};

  @Nullable
  private RenderTask myRenderTask;
  private EditedStyleItem myItem;

  public DrawableRendererEditor(@NotNull ThemeEditorContext context, boolean isEditor) {
    super(context, isEditor);

    myRenderTask = configureRenderTask(context);

    if (isEditor) {
      myComponent.addActionListener(new EditorClickListener());
    }
  }

  @Nullable
  public static RenderTask configureRenderTask(@NotNull final ThemeEditorContext context) {
    RenderTask result = null;
    AndroidFacet facet = AndroidFacet.getInstance(context.getCurrentThemeModule());
    if (facet != null) {
      final RenderService service = RenderService.get(facet);
      result =
        service.createTask(null, context.getConfiguration(), new RenderLogger("ThemeEditorLogger", context.getCurrentThemeModule()), null);
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
      final ChooseResourceDialog dialog =
        new ChooseResourceDialog(myContext.getCurrentThemeModule(), DRAWABLE_TYPE, myItem.getValue(), null);

      dialog.show();

      if (dialog.isOK()) {
        myEditorValue = dialog.getResourceName();
        stopCellEditing();
      }
      else {
        myEditorValue = null;
        cancelCellEditing();
      }
    }
  }
}
