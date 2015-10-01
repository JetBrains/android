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
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.StatePath;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import com.android.tools.rpclib.schema.Map;
import com.android.tools.rpclib.schema.Type;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;

public class StateController extends TreeController {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new StateController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);

  private final PathStore<StatePath> myStatePath = new PathStore<StatePath>();

  private StateController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myPanel.setBorder(BorderFactory.createTitledBorder(myScrollPane.getBorder(), "GPU State"));
    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
  }

  public static class Typed {
    @NotNull public final Type type;
    @NotNull public final Object value;

    public Typed(@NotNull Type type, @NotNull Object value) {
      this.type = type;
      this.value = value;
    }
  }

  public static class Node {
    @Nullable public final Object key;
    @Nullable public final Object value;

    public Node(@Nullable Object key, @Nullable Object value) {
      this.key = key;
      this.value = value;
    }
  }

  @Nullable
  private static DefaultMutableTreeNode createNode(@Nullable Object key, @Nullable Type type, @Nullable Object value) {
    DefaultMutableTreeNode child = new DefaultMutableTreeNode();
    fillNode(child, key, value);
    if (child.getChildCount() != 0) {
      child.setUserObject(new Node(key, null));
    }
    else if ((type != null) && (value != null)) {
      child.setUserObject(new Node(key, new Typed(type, value)));
    }
    else {
      child.setUserObject(new Node(key, value));
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
        }
        else {
          parent.add(createNode(field, field.getType(), dynamic.getFieldValue(index)));
        }
      }
    }
    else if (key instanceof Field) {
      Field field = (Field)key;
      if (field.getType() instanceof Map) {
        assert (value instanceof java.util.Map);
        Map map = (Map)field.getType();
        for (java.util.Map.Entry entry : ((java.util.Map<?, ?>)value).entrySet()) {
          parent.add(createNode(new Typed(map.getKeyType(), entry.getKey()), map.getValueType(), entry.getValue()));
        }
      }
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateState = myStatePath.updateIfNotNull(AtomPath.stateAfter(event.findAtomPath()));

    if (updateState && myStatePath.getPath() != null) {
      Futures.addCallback(myEditor.getClient().get(myStatePath.getPath()), new LoadingCallback<Object>(LOG, myLoadingPanel) {
        @Override
        public void onSuccess(@Nullable final Object state) {
          setRoot(createNode("state", null, state));
        }
      }, EdtExecutor.INSTANCE);
    }
  }
}
