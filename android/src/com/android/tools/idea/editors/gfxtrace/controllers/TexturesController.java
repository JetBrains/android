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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.LoadingCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ResourceInfo;
import com.android.tools.idea.editors.gfxtrace.service.Resources;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TexturesController extends ImagePanelController {
  private static final Dimension DISPLAY_SIZE = new Dimension(8192, 8192);

  public static JComponent createUI(GfxTraceEditor editor) {
    return new TexturesController(editor).myPanel;
  }

  public TexturesController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myPanel.add(new DropDownController(editor) {
      @Override
      public void selected(Data item) {
        setEmptyText(myList.isEmpty() ? GfxTraceEditor.NO_TEXTURES : GfxTraceEditor.SELECT_TEXTURE);
        setImage((item == null) ? null : FetchedImage.load(myEditor.getClient(), item.path.thumbnail(DISPLAY_SIZE)));
      }
    }.myList, BorderLayout.NORTH);
    initToolbar(new DefaultActionGroup());
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  private abstract static class DropDownController extends ImageCellController<DropDownController.Data> {
    private static final Dimension PREVIEW_SIZE = JBUI.size(100, 50);

    public static class Data extends ImageCellList.Data {
      @NotNull public final ResourceInfo info;
      @NotNull public final ResourcePath path;

      public Data(@NotNull ResourceInfo info, @NotNull ResourcePath path) {
        super(info.getName());
        this.info = info;
        this.path = path;
      }
    }

    @NotNull private static final Logger LOG = Logger.getInstance(TexturesController.class);
    @NotNull private final PathStore<ResourcesPath> myResourcesPath = new PathStore<ResourcesPath>();
    @NotNull private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
    @NotNull private Resources myResources;

    private DropDownController(@NotNull final GfxTraceEditor editor) {
      super(editor);
      usingComboBoxWidget(PREVIEW_SIZE);
      ((ImageCellRenderer<?>)myList.getRenderer()).setLayout(ImageCellRenderer.Layout.LEFT_TO_RIGHT);
    }

    @Override
    public void loadCell(Data cell, Runnable onLoad) {
      final ServiceClient client = myEditor.getClient();
      final ThumbnailPath path = cell.path.thumbnail(PREVIEW_SIZE);
      loadCellImage(cell, client, path, onLoad);
    }

    protected void update(boolean resourcesChanged) {
      if (myAtomPath.getPath() != null && myResources != null) {
        AtomPath atomPath = myAtomPath.getPath();
        List<Data> cells = new ArrayList<Data>();
        int selectedIndex = myList.getSelectedItem();
        for (ResourceInfo info : myResources.getTextures2D()) {
          if (info.getFirstAccess() <= atomPath.getIndex()) {
            cells.add(new Data(info, atomPath.resourceAfter(info.getID())));
          }
        }
        myList.setData(cells);
        if (!resourcesChanged && selectedIndex >= 0 && selectedIndex < cells.size()) {
          myList.selectItem(selectedIndex, false);
          selected(cells.get(selectedIndex));
        } else {
          myList.selectItem(-1, false);
          selected(null);
        }
      }
    }

    @Override
    public void notifyPath(PathEvent event) {
      if (myResourcesPath.updateIfNotNull(CapturePath.resources(event.findCapturePath()))) {
        Futures.addCallback(myEditor.getClient().get(myResourcesPath.getPath()), new LoadingCallback<Resources>(LOG) {
          @Override
          public void onSuccess(@Nullable final Resources resources) {
            // Back in the UI thread here
            myResources = resources;
            update(true);
          }
        }, EdtExecutor.INSTANCE);
      }

      if (myAtomPath.updateIfNotNull(event.findAtomPath())) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            update(false);
          }
        });
      }
    }
  }
}
