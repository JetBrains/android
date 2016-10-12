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
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class AtomStream implements PathListener {
  private static final Logger LOG = Logger.getInstance(AtomStream.class);

  private final GfxTraceEditor myEditor;
  private final PathStore<AtomsPath> myAtomsPath = new PathStore<AtomsPath>();
  private final PathStore<AtomRangePath> myAtomPath = new PathStore<AtomRangePath>();
  private final Listeners myListeners = new Listeners();
  private Context mySelectedContext = Context.ALL;

  private AtomList myAtomList;
  private HierarchyList myHierarchies; // TODO: this probably doesn't belong here.
  private ContextList myContexts; // TODO: this probably doesn't belong here.

  public AtomStream(GfxTraceEditor editor) {
    myEditor = editor;
  }

  @Override
  public void notifyPath(PathEvent event) {
    if (myAtomsPath.updateIfNotNull(CapturePath.atoms(event.findCapturePath()))) {
      myListeners.onAtomLoadingStart(this);
      CapturePath capturePath = myAtomsPath.getPath().getCapture();
      ListenableFuture<AtomList> atomF = myEditor.getClient().get(myAtomsPath.getPath());
      final ListenableFuture allF = Futures.allAsList(
          /* 0 */ atomF,
          /* 1 */ loadContexts(capturePath),
          /* 2 */ loadHierarchies(capturePath));
      Rpc.listen(allF, new UiErrorCallback<List<BinaryObject>, LoadData, Void>(myEditor, LOG) {
        @Override
        protected ResultOrError<LoadData, Void> onRpcThread(Rpc.Result<List<BinaryObject>> result) throws Channel.NotConnectedException {
          try {
            List<BinaryObject> list = result.get();
            return success(new LoadData((AtomList)list.get(0), (ContextList)list.get(1), (HierarchyList)list.get(2)));
          }
          catch (RpcException | ExecutionException e) {
            if (!myEditor.isDisposed()) {
              LOG.error("LoadData error", e);
            }
            return error(null);
          }
        }

        @Override
        protected void onUiThreadSuccess(LoadData data) {
          update(data.myAtoms, data.myContexts, data.myHierarchies);
        }

        @Override
        protected void onUiThreadError(Void error) {
          update(null, null, null);
        }
      });
    }

    if (myAtomPath.updateIfNotNull(event.findAtomPath())) {
      myListeners.onAtomsSelected(myAtomPath.getPath(), event.source);
    }
  }

  public void selectContext(@NotNull Context context) {
    if (!Objects.equals(context, mySelectedContext)) {
      mySelectedContext = context;
      myListeners.onContextChanged(context);
    }
  }

  @NotNull
  public Context getSelectedContext() {
    return mySelectedContext;
  }

  private ListenableFuture<ContextList> loadContexts(CapturePath capturePath) {
    if (myEditor.getFeatures().hasContextsAndHierachies()) {
      return myEditor.getClient().get(capturePath.contexts());
    }
    // Server doesn't support the contexts path, assume no contexts.
    return Futures.immediateFuture(new ContextList());
  }

  private ListenableFuture<HierarchyList> loadHierarchies(CapturePath capturePath) {
    if (myEditor.getFeatures().hasContextsAndHierachies()) {
      return myEditor.getClient().get(capturePath.hierarchies());
    }
    // Server doesn't support the hierarchies path, instead build a list from the deprecated
    // Hierarchy path.
    return Futures.transform(myEditor.getClient().get(capturePath.hierarchy()),
        new Function<AtomGroup, HierarchyList>() {
          @Nullable
          @Override
          public HierarchyList apply(@Nullable AtomGroup root) {
            Hierarchy hierarchy = new Hierarchy().setRoot(root).setContext(ContextID.INVALID);
            return new HierarchyList().setHierarchies(new Hierarchy[]{hierarchy});
          }
        }
    );
  }

  /** The structure to hold the results of the RPC loads */
  private class LoadData {
    public final AtomList myAtoms;
    public final ContextList myContexts;
    public final HierarchyList myHierarchies;

    public LoadData(AtomList atoms, ContextList contexts, HierarchyList hierarchies) {
      myAtoms = atoms;
      myContexts = contexts;
      myHierarchies = hierarchies;
    }
  }

  private void update(AtomList atomList, ContextList contexts, HierarchyList hierarchies) {
    myAtomList = atomList;
    myContexts = contexts;
    myHierarchies = hierarchies;
    myListeners.onAtomLoadingComplete(this);
  }

  public boolean isLoaded() {
    return myAtomList != null && myHierarchies != null && myContexts != null;
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

  public HierarchyList getHierarchies() {
    return myHierarchies;
  }

  public ContextList getContexts() {
    return myContexts;
  }

  public AtomRangePath getSelectedAtomsPath() {
    return myAtomPath.getPath();
  }

  public void selectAtoms(long from, long count, Object source) {
    AtomsPath path = getPath();
    if (path != null) {

      if (count == 1) {
        // check to see if this atom index is in the current context
        if (!getSelectedContext().contains(from)) {
          Context found = null;
          for (Context c : getContexts()) {
            if (c.contains(from)) {
              // and if not, switch to the context that does have this atom index
              found = c;
              break;
            }
          }
          myEditor.activatePath(path.getCapture().contexts().context(found == null ? Context.ALL.getID() : found.getID()), source);
        }
      }

      myEditor.activatePath(path.range(from, count), source);
    }
  }

  public void selectAtoms(Range range, Object source) {
    selectAtoms(range.getStart(), range.getCount(), source);
  }

  public Atom getFirstSelectedAtom() {
    AtomRangePath path = myAtomPath.getPath();
    return (path == null || myAtomList == null) ? null : myAtomList.get(path.getFirst());
  }

  public Atom getLastSelectedAtom() {
    AtomRangePath path = myAtomPath.getPath();
    return (path == null || myAtomList == null) ? null : myAtomList.get(path.getLast());
  }

  @Nullable("if not loaded or the selection doesn't contain a draw call")
  public AtomPath getLastSelectedDrawCall() {
    AtomRangePath selection = myAtomPath.getPath();
    if (selection == null || myAtomList == null) {
      return null;
    }

    for (long index = selection.getLast(); index >= selection.getFirst(); index--) {
      if (mySelectedContext.contains(index) && myAtomList.get(index).isDrawCall()) {
        return selection.getAtoms().index(index);
      }
    }
    return null;
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

    void onAtomsSelected(AtomRangePath path, Object source);

    void onContextChanged(@NotNull Context context);
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
    public void onAtomsSelected(AtomRangePath path, Object source) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onAtomsSelected(path, source);
      }
    }

    @Override
    public void onContextChanged(@NotNull Context context) {
      for (Listener listener : toArray(new Listener[size()])) {
        listener.onContextChanged(context);
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
