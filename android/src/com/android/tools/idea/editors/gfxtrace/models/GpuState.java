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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.futures.SingleInFlight;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.tools.rpclib.schema.Dynamic;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;

public class GpuState implements PathListener {
  private static final Logger LOG = Logger.getInstance(GpuState.class);
  private static final Object[] NO_SELECTION = new Object[0];

  private final GfxTraceEditor myEditor;
  private final PathStore<StatePath> myStatePath = new PathStore<StatePath>();
  private final SingleInFlight myReqController = new SingleInFlight(new SingleInFlight.Listener() {
    @Override
    public void onIdleToWorking() {
      myListeners.onStateLoadingStart(GpuState.this);
    }

    @Override
    public void onWorkingToIdle() {
    }
  });
  private final Listeners myListeners = new Listeners();

  private Dynamic myState;
  private Object[] mySelection = NO_SELECTION;
  private Object[] myCachedSelection = NO_SELECTION;

  public GpuState(GfxTraceEditor editor) {
    myEditor = editor;
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (myStatePath.updateIfNotNull(AtomPath.stateAfter(event.findAtomPath()))) {
      Rpc.listen(myEditor.getClient().get(myStatePath.getPath()), EdtExecutor.INSTANCE, LOG, myReqController, new Rpc.Callback<Object>() {
        @Override
        public void onFinish(Rpc.Result<Object> result) throws RpcException, ExecutionException {
          try {
            update((Dynamic)result.get(), null);
          }
          catch (ErrDataUnavailable e) {
            update(null, e);
          }
        }
      });
    }
    if (event.findStatePath() != null) {
      if (myState == null) {
        myCachedSelection = getStatePath(event.path);
      }
      else {
        mySelection = getStatePath(event.path);
        myCachedSelection = NO_SELECTION;
        myListeners.onStateSelection(this, mySelection);
      }
    }
  }

  protected void update(Dynamic state, ErrDataUnavailable error) {
    myState = state;
    if (error != null) {
      myListeners.onStateLoadingFailure(this, error);
    }
    else {
      myListeners.onStateLoadingSuccess(this);
      if (myCachedSelection.length != 0) {
        mySelection = myCachedSelection;
        myCachedSelection = NO_SELECTION;
        myListeners.onStateSelection(this, mySelection);
      }
    }
  }

  private static Object[] getStatePath(Path path) {
    LinkedList<Object> result = Lists.newLinkedList();
    while (path != null) {
      if (path instanceof FieldPath) {
        result.add(0, ((FieldPath)path).getName());
      }
      else if (path instanceof MapIndexPath) {
        result.add(0, ((MapIndexPath)path).getKey());
      }
      else {
        break;
      }
      path = path.getParent();
    }
    return result.toArray(new Object[result.size()]);
  }

  public StatePath getPath() {
    return myStatePath.getPath();
  }

  public Dynamic getState() {
    return myState;
  }

  public Object[] getSelection() {
    return (myCachedSelection.length == 0) ? mySelection : myCachedSelection;
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  public interface Listener {
    void onStateLoadingStart(GpuState state);

    void onStateLoadingFailure(GpuState state, ErrDataUnavailable error);

    void onStateLoadingSuccess(GpuState state);

    void onStateSelection(GpuState state, Object[] selection);
  }

  private static class Listeners extends ArrayList<Listener> implements Listener {
    @Override
    public void onStateLoadingStart(GpuState state) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onStateLoadingStart(state);
      }
    }

    @Override
    public void onStateLoadingFailure(GpuState state, ErrDataUnavailable error) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onStateLoadingFailure(state, error);
      }
    }

    @Override
    public void onStateLoadingSuccess(GpuState state) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onStateLoadingSuccess(state);
      }
    }

    @Override
    public void onStateSelection(GpuState state, Object[] selection) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onStateSelection(state, selection);
      }
    }

    @Override
    public synchronized <T> T[] toArray(T[] a) {
      return super.toArray(a);
    }

    @Override
    public synchronized boolean add(Listener listener) {
      return super.add(listener);
    }

    @Override
    public synchronized boolean remove(Object o) {
      return super.remove(o);
    }
  }
}
