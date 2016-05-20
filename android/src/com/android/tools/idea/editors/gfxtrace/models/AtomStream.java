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
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AtomStream implements PathListener {
  private static final Logger LOG = Logger.getInstance(AtomStream.class);

  private final GfxTraceEditor myEditor;
  private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  private final PathStore<AtomPath> myAtomPath = new PathStore<AtomPath>();
  private final Listeners myListeners = new Listeners();

  private AtomList myAtomList;
  private AtomGroup myAtomGroup;

  public AtomStream(GfxTraceEditor editor) {
    myEditor = editor;
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (myAtomsPath.updateIfNotNull(CapturePath.atoms(event.findCapturePath()))) {
      myListeners.onAtomLoadingStart(this);
      final ListenableFuture<AtomList> atomF = myEditor.getClient().get(myAtomsPath.getPath());
      final ListenableFuture<AtomGroup> hierarchyF = myEditor.getClient().get(myAtomsPath.getPath().getCapture().hierarchy());
      Rpc.listen(Futures.allAsList(atomF, hierarchyF), LOG, new UiErrorCallback<List<BinaryObject>, Pair<AtomList, AtomGroup>, Void>() {
        @Override
        protected ResultOrError<Pair<AtomList, AtomGroup>, Void> onRpcThread(Rpc.Result<List<BinaryObject>> result) {
          try {
            List<BinaryObject> list = result.get();
            return success(Pair.create((AtomList)list.get(0), (AtomGroup)list.get(1)));
          }
          catch (RpcException e) {
            LOG.error(e);
            return error(null);
          }
          catch (ExecutionException e) {
            LOG.error(e);
            return error(null);
          }
        }

        @Override
        protected void onUiThreadSuccess(Pair<AtomList, AtomGroup> result) {
          update(result.first, result.second);
        }

        @Override
        protected void onUiThreadError(Void error) {
          update(null, null);
        }
      });
    }

    if (myAtomPath.updateIfNotNull(event.findAtomPath())) {
      myListeners.onAtomSelected(myAtomPath.getPath(), event.source);
    }
  }

  private void update(AtomList atomList, AtomGroup atomGroup) {
    myAtomList = atomList;
    myAtomGroup = atomGroup;
    myListeners.onAtomLoadingComplete(this);
  }

  public boolean isLoaded() {
    return myAtomList != null && myAtomGroup != null;
  }

  public AtomsPath getPath() {
    return myAtomsPath.getPath();
  }

  public int getAtomCount() {
    return myAtomList.getAtoms().length;
  }

  public Atom getAtom(long index) {
    return myAtomList.get(index);
  }

  public int getStartOfFrame(long index) {
    Atom[] atoms = myAtomList.getAtoms();
    for (int i = (int)index; i > 0; i--) {
      if (atoms[i - 1].isEndOfFrame()) {
        return i;
      }
    }
    return 0;
  }

  public int getEndOfFrame(long index) {
    Atom[] atoms = myAtomList.getAtoms();
    for (int i = (int)index; i < atoms.length; i++) {
      if (atoms[i].isEndOfFrame()) {
        return i;
      }
    }
    return atoms.length - 1;
  }

  public AtomList getAtoms() {
    return myAtomList;
  }

  public AtomGroup getHierarchy() {
    return myAtomGroup;
  }

  public AtomPath getSelectedAtomPath() {
    return myAtomPath.getPath();
  }

  public int getSelectedAtomIndex() {
    AtomPath path = myAtomPath.getPath();
    return (path == null) ? -1 : (int)path.getIndex();
  }

  public Atom getSelectedAtom() {
    AtomPath path = myAtomPath.getPath();
    return (path == null || myAtomList == null) ? null : myAtomList.get(path.getIndex());
  }

  public void selectAtom(long index, Object source) {
    AtomsPath path = myAtomsPath.getPath();
    if (path != null) {
      myEditor.activatePath(path.index(index), source);
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  public interface Listener {
    void onAtomLoadingStart(AtomStream atoms);

    void onAtomLoadingComplete(AtomStream atoms);

    void onAtomSelected(AtomPath path, Object source);
  }

  private static class Listeners extends ArrayList<Listener> implements Listener {
    public Listeners() {
    }

    @Override
    public void onAtomLoadingStart(AtomStream atoms) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onAtomLoadingStart(atoms);
      }
    }

    @Override
    public void onAtomLoadingComplete(AtomStream atoms) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onAtomLoadingComplete(atoms);
      }
    }

    @Override
    public void onAtomSelected(AtomPath path, Object source) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onAtomSelected(path, source);
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
