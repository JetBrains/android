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
package com.android.tools.idea.editors.gfxtrace.models;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;

import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.ResourceBundle;
import com.android.tools.idea.editors.gfxtrace.service.ResourceBundles;
import com.android.tools.idea.editors.gfxtrace.service.Resources;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * A class for retrieving a collection of resources, specifically ResourceBundles.
 */
public class ResourceCollection implements PathListener {
  private static final Logger LOG = Logger.getInstance(ResourceCollection.class);

  @NotNull private final GfxTraceEditor myEditor;
  @NotNull private final PathStore<AtomRangePath> myAtomPath = new PathStore<AtomRangePath>();
  @NotNull private final PathStore<ResourceBundlesPath> myResourcesPath = new PathStore<ResourceBundlesPath>();
  private ResourceBundles myResources;
  @NotNull private final Listeners myListeners = new ResourceCollection.Listeners();


  public ResourceCollection(GfxTraceEditor editor) {
    myEditor = editor;
  }


  @Override
  public void notifyPath(PathEvent event) {
    if (myResourcesPath.updateIfNotNull(CapturePath.resourceBundles(event.findCapturePath()))) {
      if (myEditor.getFeatures().hasResourceBundles()) {
        myListeners.onResourceLoadingStart(ResourceCollection.this);
        Rpc.listen(myEditor.getClient().get(myResourcesPath.getPath()),
                   new UiErrorCallback<ResourceBundles, ResourceBundles, String>(myEditor, LOG) {
                     @Override
                     protected ResultOrError<ResourceBundles, String> onRpcThread(Rpc.Result<ResourceBundles> result)
                       throws RpcException, ExecutionException, Channel.NotConnectedException {
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
                       myListeners.onResourceLoadingComplete(ResourceCollection.this);
                     }

                     @Override
                     protected void onUiThreadError(String error) {
                       myResources = null;
                       myListeners.onResourceLoadingComplete(ResourceCollection.this);
                     }
                   });
      }
      else {
        // Use deprecated ResourcesPath and build the bundles from the result.
        ResourcesPath path = myResourcesPath.getPath().asResourcesPath();
        myListeners.onResourceLoadingStart(ResourceCollection.this);
        Rpc.listen(myEditor.getClient().get(path), new UiErrorCallback<Resources, ResourceBundles, String>(myEditor, LOG) {
          @Override
          protected ResultOrError<ResourceBundles, String> onRpcThread(Rpc.Result<Resources> result)
            throws RpcException, ExecutionException, Channel.NotConnectedException {
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
            myListeners.onResourceLoadingComplete(ResourceCollection.this);
          }

          @Override
          protected void onUiThreadError(String error) {
            myResources = null;
            myListeners.onResourceLoadingComplete(null);
          }
        });
      }
    }
  }

  public ResourceBundles getResourceBundles() {
    return myResources;
  }

  public void addListener(ResourceCollection.Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(AtomStream.Listener listener) {
    myListeners.remove(listener);
  }

  public interface Listener {
    void onResourceLoadingStart(ResourceCollection resources);

    void onResourceLoadingComplete(ResourceCollection resources);
  }

  private static class Listeners extends ArrayList<ResourceCollection.Listener> implements ResourceCollection.Listener {
    public Listeners() {
    }

    @Override
    public void onResourceLoadingStart(ResourceCollection resources) {
      for (ResourceCollection.Listener listener : toArray(new ResourceCollection.Listener[size()])) {
        listener.onResourceLoadingStart(resources);
      }
    }

    @Override
    public void onResourceLoadingComplete(ResourceCollection resources) {
      for (ResourceCollection.Listener listener : toArray(new ResourceCollection.Listener[size()])) {
        listener.onResourceLoadingComplete(resources);
      }
    }

    @Override
    public synchronized <T> T[] toArray(T[] a) {
      return super.toArray(a);
    }

    @Override
    public synchronized boolean add(ResourceCollection.Listener listener) {
      return super.add(listener);
    }

    @Override
    public synchronized boolean remove(Object o) {
      return super.remove(o);
    }
  }
}
