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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.actions.AtomComboAction;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.renderers.ImageCellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Cubemap;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Texture2D;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.image.Format;
import com.android.tools.idea.editors.gfxtrace.service.image.ImageInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.ImageCellList;
import com.android.tools.rpclib.futures.SingleInFlight;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.api.client.util.Lists;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventCategory;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class TexturesController extends ImagePanelController {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new TexturesController(editor).myPanel;
  }

  @NotNull private AtomComboAction myJumpToAtomComboAction;
  private Object myCurrentResourceId;

  public TexturesController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myPanel.add(new DropDownController(editor) {
      @Override
      public void selected(Data item) {
        setEmptyText(myList.isEmpty() ? GfxTraceEditor.NO_TEXTURES : GfxTraceEditor.SELECT_TEXTURE);
        setImage((item == null) ? null : FetchedImage.load(myEditor.getClient(), item.path));
        myJumpToAtomComboAction.setAtomIds(item == null ? Collections.emptyList() : Arrays.stream(item.info.getAccesses()).boxed().collect(Collectors.toList()));

        if (item != null && myCurrentResourceId != item.info.getID()) {
          myCurrentResourceId = item.info.getID();
          String format = item.typeLabel;
          int height = 0;
          int width = 0;
          if (item.imageInfo != null) {
            width = item.imageInfo.getWidth();
            height = item.imageInfo.getHeight();
            format = format + "/" + item.imageInfo.getFormat().toString();
          }

          UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                         .setCategory(EventCategory.GPU_PROFILER)
                                         .setKind(AndroidStudioEvent.EventKind.GFX_TRACE_TEXTURE_VIEWED)
                                         .setGfxTracingDetails(AndroidStudioStats.GfxTracingDetails.newBuilder()
                                                               .setImageFormat(format)
                                                               .setImageWidth(width)
                                                               .setImageHeight(height)));
        }
      }
    }.myList, BorderLayout.NORTH);

    DefaultActionGroup toolbar = new DefaultActionGroup();
    myJumpToAtomComboAction = new AtomComboAction(editor);
    initToolbar(toolbar, true);
    toolbar.add(myJumpToAtomComboAction);
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  private abstract static class DropDownController extends ImageCellController<DropDownController.Data> implements AtomStream.Listener {
    private static final Dimension CONTROL_SIZE = JBUI.size(100, 50);
    private static final Dimension REQUEST_SIZE = JBUI.size(100, 100);

    public static class Data extends ImageCellList.Data {
      @NotNull public final SingleInFlight extraController = new SingleInFlight();
      @NotNull public final ResourceInfo info;
      @NotNull public final ResourcePath path;
      @NotNull public final String typeLabel;

      @Nullable public ImageInfo imageInfo;
      @Nullable public String extraLabel;

      public Data(@NotNull ResourceInfo info, @NotNull String typeLabel, @NotNull ResourcePath path) {
        super(info.getName());
        this.typeLabel = typeLabel;
        this.info = info;
        this.path = path;
      }

      @Override
      public String getLabel() {
        return typeLabel + " " + super.getLabel() +
               (imageInfo == null ? "" : " - " + imageInfo.getFormat() + " - " + imageInfo.getWidth() + "x" + imageInfo.getHeight()) +
               (extraLabel == null ? "" : " " + extraLabel);
      }
    }

    @NotNull private static final Logger LOG = Logger.getInstance(TexturesController.class);
    @NotNull private final PathStore<ResourceBundlesPath> myResourcesPath = new PathStore<ResourceBundlesPath>();
    private ResourceBundles myResources;

    private DropDownController(@NotNull final GfxTraceEditor editor) {
      super(editor);
      editor.getAtomStream().addListener(this);

      usingComboBoxWidget(CONTROL_SIZE);
      ImageCellRenderer<?> renderer = (ImageCellRenderer<?>)myList.getRenderer();
      renderer.setLayout(ImageCellRenderer.Layout.LEFT_TO_RIGHT);
      renderer.setFlipImage(true);
      renderer.setNoItemText("<Click to select texture>");
    }

    @Override
    public void loadCell(Data cell, Runnable onLoad) {
      final ServiceClient client = myEditor.getClient();
      final ThumbnailPath path = cell.path.thumbnail(REQUEST_SIZE, Format.RGBA);
      loadCellImage(cell, client, path, onLoad);
      loadCellMetadata(cell);
    }

    private void loadCellMetadata(final Data cell) {
      Rpc.listen(myEditor.getClient().get(cell.path), LOG, cell.extraController, new UiErrorCallback<Object, Object, String>() {
        @Override
        protected ResultOrError<Object, String> onRpcThread(Rpc.Result<Object> result) throws RpcException, ExecutionException {
          try {
            return success(result.get());
          } catch (ErrDataUnavailable e) {
            return error(e.getMessage());
          }
        }

        @Override
        protected void onUiThreadSuccess(Object resource) {
          ImageInfo base = null;
          int mipmapLevels = -1;

          if (resource instanceof Texture2D) {
            Texture2D texture = (Texture2D)resource;
            base = texture.getLevels()[0];
            mipmapLevels = texture.getLevels().length;
          }
          else if (resource instanceof Cubemap) {
            Cubemap texture = (Cubemap)resource;
            base = texture.getLevels()[0].getNegativeZ();
            mipmapLevels = texture.getLevels().length;
          }

          if (base != null) {
            cell.imageInfo = base;
            cell.extraLabel = ((mipmapLevels > 1) ? " - " + mipmapLevels + " mip levels" : "") + " - Modified " + cell.info.getAccesses().length + " times";
          }
          else {
            cell.extraLabel = "Unknown texture type: " + resource.getClass().getName();
          }

          myList.repaint();
        }

        @Override
        protected void onUiThreadError(String error) {
          cell.extraLabel = error;
          myList.repaint();
        }
      });
    }

    protected void update(boolean resourcesChanged) {
      if (myEditor.getAtomStream().getSelectedAtomsPath() != null && myResources != null) {
        List<Data> cells = new ArrayList<Data>();
        for (ResourceBundle bundle : myResources.getBundles()) {
          addTextures(cells, bundle);
        }

        int selectedIndex = myList.getSelectedIndex();
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

    private static String getTypeLabel(GfxAPIProtos.ResourceType type) {
      switch (type) {
        case Texture1D: return "1D";
        case Texture2D: return "2D";
        case Texture3D: return "3D";
        case Cubemap: return "Cubemap";
        default: return null;
      }
    }

    private void addTextures(List<Data> cells, ResourceBundle bundle) {
      if (bundle == null || bundle.getResources().length == 0) {
        return;
      }

      String typeLabel = getTypeLabel(bundle.getType());
      if (typeLabel == null) {
        // Ignore non-texture resources (and unknown texture types).
        return;
      }

      AtomPath atomPath = myEditor.getAtomStream().getSelectedAtomsPath().getPathToLast();
      for (ResourceInfo info : bundle.getResources()) {
        if (info.getFirstAccess() <= atomPath.getIndex()) {
          cells.add(new Data(info, typeLabel, atomPath.resourceAfter(info.getID())));
        }
      }
    }

    @Override
    public void notifyPath(PathEvent event) {
      CapturePath capturePath = event.findCapturePath();
      if (capturePath == null) {
        return;
      }
      if (myResourcesPath.updateIfNotNull(CapturePath.resourceBundles(capturePath))) {
        if (myEditor.getFeatures().hasResourceBundles()) {
          Rpc.listen(myEditor.getClient().get(myResourcesPath.getPath()), LOG, new UiErrorCallback<ResourceBundles, ResourceBundles, String>() {
            @Override
            protected ResultOrError<ResourceBundles, String> onRpcThread(Rpc.Result<ResourceBundles> result)
                throws RpcException, ExecutionException {
              try {
                return success(result.get());
              }
              catch (ErrDataUnavailable e) {
                return error(e.getMessage());
              }
            }

            @Override
            protected void onUiThreadSuccess(ResourceBundles result) {
              myResources = result;
              update(true);
            }

            @Override
            protected void onUiThreadError(String error) {
              myResources = null;
              update(true);
            }
          });
        }
        else {
          // Use deprecated ResourcesPath and build the bundles from the result.
          ResourcesPath path = myResourcesPath.getPath().asResourcesPath();
          Rpc.listen(myEditor.getClient().get(path), LOG, new UiErrorCallback<Resources, ResourceBundles, String>() {
            @Override
            protected ResultOrError<ResourceBundles, String> onRpcThread(Rpc.Result<Resources> result) throws RpcException, ExecutionException {
              try {
                Resources res = result.get();
                List<ResourceBundle> bundles = Lists.newArrayList();
                if (res.getTextures1D().length != 0) {
                  bundles.add(new ResourceBundle().setType(GfxAPIProtos.ResourceType.Texture1D).setResources(res.getTextures1D()));
                }
                if (res.getTextures2D().length != 0) {
                  bundles.add(new ResourceBundle().setType(GfxAPIProtos.ResourceType.Texture2D).setResources(res.getTextures2D()));
                }
                if (res.getTextures3D().length != 0) {
                  bundles.add(new ResourceBundle().setType(GfxAPIProtos.ResourceType.Texture3D).setResources(res.getTextures3D()));
                }
                if (res.getCubemaps().length != 0) {
                  bundles.add(new ResourceBundle().setType(GfxAPIProtos.ResourceType.Cubemap).setResources(res.getCubemaps()));
                }
                return success(new ResourceBundles().setBundles(bundles.toArray(new ResourceBundle[bundles.size()])));
              }
              catch (ErrDataUnavailable e) {
                return error(e.getMessage());
              }
            }

            @Override
            protected void onUiThreadSuccess(ResourceBundles result) {
              myResources = result;
              update(true);
            }

            @Override
            protected void onUiThreadError(String error) {
              myResources = null;
              update(true);
            }
          });
        }
      }
    }

    @Override
    public void onAtomLoadingStart(AtomStream atoms) {
    }

    @Override
    public void onAtomLoadingComplete(AtomStream atoms) {
    }

    @Override
    public void onAtomsSelected(AtomRangePath path, Object source) {
      update(false);
    }
  }
}
