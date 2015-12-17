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
import com.android.tools.idea.editors.gfxtrace.service.image.Format;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
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
        setImage((item == null) ? null : FetchedImage.load(myEditor.getClient(), item.path.as(Format.RGBA)));
      }
    }.myList, BorderLayout.NORTH);
    initToolbar(new DefaultActionGroup(), true);
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  private abstract static class DropDownController extends ImageCellController<DropDownController.Data> {
    private static final Dimension CONTROL_SIZE = JBUI.size(100, 50);
    private static final Dimension REQUEST_SIZE = JBUI.size(100, 100);

    public static class Data extends ImageCellList.Data {
      @NotNull public final ResourceInfo info;
      @NotNull public final ResourcePath path;

      public Data(@NotNull ResourceInfo info, @NotNull String typeLabel, @NotNull ResourcePath path) {
        super(typeLabel + " " + info.getName());
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
      usingComboBoxWidget(CONTROL_SIZE);
      ((ImageCellRenderer<?>)myList.getRenderer()).setLayout(ImageCellRenderer.Layout.LEFT_TO_RIGHT);
    }

    @Override
    public void loadCell(Data cell, Runnable onLoad) {
      final ServiceClient client = myEditor.getClient();
      final ThumbnailPath path = cell.path.thumbnail(REQUEST_SIZE, Format.RGBA);
      loadCellImage(cell, client, path, onLoad);
    }

    protected void update(boolean resourcesChanged) {
      if (myAtomPath.getPath() != null && myResources != null) {
        List<Data> cells = new ArrayList<Data>();
        addTextures(cells, myResources.getTextures1D(), "1D");
        addTextures(cells, myResources.getTextures2D(), "2D");
        addTextures(cells, myResources.getTextures3D(), "3D");
        addTextures(cells, myResources.getCubemaps(), "Cubemap");

        int selectedIndex = myList.getSelectedItem();
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

    private void addTextures(List<Data> cells, ResourceInfo[] textures, String typeLabel) {
      AtomPath atomPath = myAtomPath.getPath();
      for (ResourceInfo info : textures) {
        if (info.getFirstAccess() <= atomPath.getIndex()) {
          cells.add(new Data(info, typeLabel, atomPath.resourceAfter(info.getID())));
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
        update(false);
      }
    }
  }
}
