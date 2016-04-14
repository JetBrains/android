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
import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.service.RenderSettings;
import com.android.tools.idea.editors.gfxtrace.service.ServiceClient;
import com.android.tools.idea.editors.gfxtrace.service.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.CellList;
import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ScrubberController extends ImageCellController<ScrubberController.Data> {
  private static final Dimension PREVIEW_SIZE = JBUI.size(192, 192);

  public static JComponent createUI(GfxTraceEditor editor) {
    return new ScrubberController(editor).myList;
  }

  public static class Data extends ImageCellList.Data {
    @NotNull public final AtomPath atomPath;
    @NotNull public final Range range;

    public Data(AtomPath atomPath, @NotNull Range range, @NotNull String label) {
      super(label);
      this.atomPath = atomPath;
      this.range = range;
    }
  }

  @NotNull private static final Logger LOG = Logger.getInstance(ScrubberController.class);
  @NotNull private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();
  @NotNull private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  @NotNull private final RenderSettings myRenderSettings = new RenderSettings();

  private ScrubberController(@NotNull final GfxTraceEditor editor) {
    super(editor);
    usingListWidget(CellList.Orientation.HORIZONTAL, GfxTraceEditor.LOADING_CAPTURE, PREVIEW_SIZE);
    myRenderSettings.setMaxWidth(PREVIEW_SIZE.width);
    myRenderSettings.setMaxHeight(PREVIEW_SIZE.height);
    myRenderSettings.setWireframeMode(WireframeMode.NoWireframe);
  }

  @Override
  public void loadCell(final Data cell, final Runnable onLoad) {
    final DevicePath devicePath = myRenderDevice.getPath();
    if (devicePath == null) {
      return;
    }
    final ServiceClient client = myEditor.getClient();
    Rpc.listen(client.getFramebufferColor(devicePath, cell.atomPath, myRenderSettings), LOG, new Rpc.Callback<ImageInfoPath>() {
      @Override
      public void onFinish(Rpc.Result<ImageInfoPath> result) throws RpcException, ExecutionException {
        // TODO: try{ result.get() } catch{ ErrDataUnavailable e }...
        ImageInfoPath imagePath = result.get();
        loadCellImage(cell, client, imagePath, onLoad);
      }
    });
  }

  @Override
  public void selected(@NotNull Data cell) {
    if (myAtomsPath.getPath() == null) {
      return;
    }
    AtomPath atomPath = myAtomsPath.getPath().index(cell.range.getLast());
    myEditor.activatePath(atomPath, this);
  }


  @Nullable
  public List<Data> prepareData(@NotNull AtomsPath path, @NotNull AtomList atoms) {
    List<Data> generatedList = new ArrayList<Data>();
    int frameCount = 0;
    Range range = new Range();
    range.setStart(0);
    for (int index = 0; index < atoms.getAtoms().length; ++index) {
      Atom atom = atoms.get(index);
      if (atom.isEndOfFrame()) {
        range.setEnd(index + 1);
        Data frameData = new Data(path.index(index), range, Integer.toString(frameCount++));
        generatedList.add(frameData);
        range = new Range();
        range.setStart(index + 1);
      }
    }
    return generatedList;
  }

  private void selectFrame(AtomPath atomPath) {
    if (atomPath == null) {
      return;
    }

    int index = 0;
    for (Data data : myList.items()) {
      if (data.range.contains(atomPath.getIndex())) {
        myList.selectItem(index, false);
        break;
      }
      index++;
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateIcons = myRenderDevice.updateIfNotNull(event.findDevicePath());
    updateIcons = myAtomsPath.updateIfNotNull(CapturePath.atoms(event.findCapturePath())) | updateIcons;

    selectFrame(event.findAtomPath());

    final AtomsPath atomsPath = myAtomsPath.getPath();
    if (updateIcons && atomsPath != null) {
      Rpc.listen(myEditor.getClient().get(atomsPath), LOG, new UiCallback<AtomList, List<Data>>() {
        @Override
        protected List<Data> onRpcThread(Rpc.Result<AtomList> result) throws RpcException, ExecutionException {
          return prepareData(atomsPath, result.get());
        }

        @Override
        protected void onUiThread(List<Data> result) {
          myList.setData(result);
        }
      });
    }
  }
}
