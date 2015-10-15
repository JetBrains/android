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
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ScrubberController extends CellController<ScrubberController.Data> {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new ScrubberController(editor).myPanel;
  }

  public static class Data extends CellController.Data {
    @NotNull public final AtomPath atomPath;
    @NotNull public final Range range;

    public Data(AtomPath atomPath, @NotNull Range range, @NotNull String label, @NotNull ImageIcon icon) {
      super(label, icon);
      this.atomPath = atomPath;
      this.range = range;
    }
  }

  @NotNull private static final Logger LOG = Logger.getInstance(ScrubberController.class);
  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  @NotNull private final RenderSettings myRenderSettings = new RenderSettings();
  private boolean mDisableActivation = false;

  private ScrubberController(@NotNull final GfxTraceEditor editor) {
    super(editor);
    myRenderSettings.setMaxWidth(CellRenderer.MAX_WIDTH);
    myRenderSettings.setMaxHeight(CellRenderer.MAX_HEIGHT);
    myRenderSettings.setWireframeMode(WireframeMode.noWireframe());
  }

  @Override
  public boolean loadCell(final Data cell) {
    final DevicePath devicePath = myRenderDevice.getPath();
    if (devicePath == null) {
      return false;
    }
    final ServiceClient client = myEditor.getClient();
    ListenableFuture<ImageInfoPath> imagePathF = client.getFramebufferColor(devicePath, cell.atomPath, myRenderSettings);
    Futures.addCallback(imagePathF, new LoadingCallback<ImageInfoPath>(LOG, cell) {
      @Override
      public void onSuccess(@Nullable final ImageInfoPath imagePath) {
        loadCellImage(cell, client, imagePath);
      }
    });
    return true;
  }

  @Override
  void selected(@NotNull Data cell) {
    if (mDisableActivation || myAtomsPath.getPath() == null) {
      return;
    }
    AtomPath atomPath = myAtomsPath.getPath().index(cell.range.getLast());
    myEditor.activatePath(atomPath);
  }


  @Nullable
  public List<Data> prepareData(@NotNull AtomsPath path, @NotNull AtomList atoms) {
    List<Data> generatedList = new ArrayList<Data>();
    int frameCount = 0;
    Range range = new Range();
    range.setStart(0);
    for (int index = 0; index < atoms.getAtoms().length; ++index) {
      Atom atom = atoms.get(index);
      if (atom.getIsEndOfFrame()) {
        range.setEnd(index + 1);
        Data frameData = new Data(path.index(index), range, Integer.toString(frameCount++), myRenderer.getDefaultIcon());
        generatedList.add(frameData);
        range = new Range();
        range.setStart(index + 1);
      }
    }
    return generatedList;
  }

  public void selectFrame(long atomIndex) {
    if (myData == null) return;
    for (int i = 0; i < myData.size(); ++i) {
      if (myData.get(i).range.contains(atomIndex)) {
        try {
          mDisableActivation = true;
          selectItem(i);
        }
        finally {
          mDisableActivation = false;
        }
      }
    }
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateIcons = false;
    if (path instanceof DevicePath) {
      updateIcons |= myRenderDevice.update((DevicePath)path);
    }
    if (path instanceof CapturePath) {
      updateIcons |= myAtomsPath.update(((CapturePath)path).atoms());
    }
    if (path instanceof AtomPath) {
      selectFrame(((AtomPath)path).getIndex());
    }
    if (updateIcons && myAtomsPath.getPath() != null) {
      Futures.addCallback(myEditor.getClient().get(myAtomsPath.getPath()), new LoadingCallback<AtomList>(LOG) {
        @Override
        public void onSuccess(@Nullable final AtomList atoms) {
          final List<Data> cells = prepareData(myAtomsPath.getPath(), atoms);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              // Back in the UI thread here
              populateUi(cells);
            }
          });
        }
      });
    }
  }
}
