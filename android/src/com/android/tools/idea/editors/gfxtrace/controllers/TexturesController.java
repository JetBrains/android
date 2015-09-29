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
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ResourceInfo;
import com.android.tools.idea.editors.gfxtrace.service.Resources;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.CellList;
import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class TexturesController extends ImageCellController<TexturesController.Data> {
  private static final Dimension PREVIEW_SIZE = JBUI.size(100, 50);

  public static JComponent createUI(GfxTraceEditor editor) {
    return new TexturesController(editor).myPanel;
  }

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
  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final PathStore<ResourcesPath> myResourcesPath = new PathStore<ResourcesPath>();
  @NotNull private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
  @NotNull private Resources myResources;

  private TexturesController(@NotNull final GfxTraceEditor editor) {
    super(editor);
    usingComboBoxWidget(PREVIEW_SIZE);
    ((ImageCellRenderer<?>)myList.getRenderer()).setLayout(ImageCellRenderer.Layout.LEFT_TO_RIGHT);
    myPanel.add(myList, BorderLayout.NORTH);
  }

  @Override
  public void selected(@NotNull Data cell) {
  }

  @Override
  public void loadCell(Data cell, Runnable onLoad) {
    final ServiceClient client = myEditor.getClient();
    final ThumbnailPath path = cell.path.thumbnail(PREVIEW_SIZE);
    loadCellImage(cell, client, path, onLoad);
  }

  void update() {
    if (myAtomPath.getPath() != null && myResources != null) {
      final AtomPath atomPath = myAtomPath.getPath();
      final Resources resources = myResources;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          final List<Data> cells = new ArrayList<Data>();
          for (ResourceInfo info : resources.getTextures()) {
            cells.add(new Data(info, atomPath.resourceAfter(info.getID())));
          }
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              myList.setData(cells);
            }
          });
        }
      });
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateIcons = false;
    if (event.path instanceof CapturePath) {
      if (myResourcesPath.update(((CapturePath)event.path).resources())) {
        Futures.addCallback(myEditor.getClient().get(myResourcesPath.getPath()), new LoadingCallback<Resources>(LOG) {
          @Override
          public void onSuccess(@Nullable final Resources resources) {
            EdtExecutor.INSTANCE.execute(new Runnable() {
              @Override
              public void run() {
                // Back in the UI thread here
                myResources = resources;
                update();
              }
            });
          }
        });
      }
    }
    if ((event.path instanceof AtomPath) && myAtomPath.update((AtomPath)event.path)) {
      update();
    }
  }
}
