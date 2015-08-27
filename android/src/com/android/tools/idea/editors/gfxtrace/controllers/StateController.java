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
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.StatePath;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Map;

public class StateController extends TreeController {
  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);

  private final PathStore<StatePath> myStatePath = new PathStore<StatePath>();

  public StateController(@NotNull GfxTraceEditor editor, @NotNull JBScrollPane scrollPane) {
    super(editor, scrollPane, GfxTraceEditor.SELECT_ATOM);
  }

  public static class Node {
    public final Object key;
    public final Object value;

    public Node(Object key, Object value) {
      this.key = key;
      this.value = value;
    }
  }

  @Nullable
  private static DefaultMutableTreeNode createNode(@Nullable Object key, @Nullable Object value) {
    DefaultMutableTreeNode child = new DefaultMutableTreeNode();
    fillNode(child, key, value);
    if (child.getChildCount() == 0) {
      child.setUserObject(new Node(key, value));
    } else {
      child.setUserObject(new Node(key, null));
    }
    return child;
  }

  private static void fillNode(@NotNull DefaultMutableTreeNode parent, @Nullable Object key, @Nullable Object value) {
    if (value instanceof Dynamic) {
      Dynamic dynamic = (Dynamic)value;
      for (int index = 0; index < dynamic.getFieldCount(); ++index) {
        Field field = dynamic.getFieldInfo(index);
        if (field.getDeclared().length() == 0) {
          // embed anonymous fields directly into the parent
          fillNode(parent, field, dynamic.getFieldValue(index));
        } else {
          parent.add(createNode(field, dynamic.getFieldValue(index)));
        }
      }
    } else if (value instanceof Map) {
      Map<?,?> map = (Map)value;
      for (java.util.Map.Entry entry : map.entrySet()) {
        parent.add(createNode(entry.getKey(), entry.getValue()));
      }
    }
  }

  @Override
  public void notifyPath(Path path) {
    boolean updateState = false;
    if (path instanceof AtomPath) {
      updateState |= myStatePath.update(((AtomPath)path).stateAfter());
    }
    if (updateState && myStatePath.isValid()) {
      Futures.addCallback(myEditor.getClient().get(myStatePath.getPath()), new LoadingCallback<Object>(LOG, myLoadingPanel) {
        @Override
        public void onSuccess(@Nullable final Object state) {
          final DefaultMutableTreeNode stateNode = createNode("state", state);
          EdtExecutor.INSTANCE.execute(new Runnable() {
            @Override
            public void run() {
              setRoot(stateNode);
            }
          });
        }
      });
    }
  }
}
