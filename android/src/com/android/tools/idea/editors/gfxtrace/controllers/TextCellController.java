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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Program;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Shader;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.widgets.CellList;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;

/**
 * Displays a list of String objects, all loaded at once
 */
public abstract class TextCellController <T extends CellList.Data & TextCellController.PathResource> extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(TextCellController.class);
  @NotNull protected CellList<T> myList;

  public TextCellController(@NotNull GfxTraceEditor editor) {
    this(editor, CellList.Orientation.VERTICAL, "");
  }

  public TextCellController(@NotNull GfxTraceEditor editor, CellList.Orientation orientation, String initialText) {
    super(editor);
    final CellRenderer.CellLoader<T> loader = new CellRenderer.CellLoader<T>() {
      @Override
      public void loadCell(T cell, Runnable onLoad) {
        Rpc.listen(myEditor.getClient().get(cell.getPath()), cell.controller, new UiErrorCallback<Object, Object, String>(myEditor, LOG) {
          @Override
          protected ResultOrError<Object, String> onRpcThread(Rpc.Result<Object> result)
            throws RpcException, ExecutionException, Channel.NotConnectedException {
            try {
              return success(result.get());
            }
            catch (ErrDataUnavailable e) {
              return error(e.getMessage());
            }
          }

          @Override
          protected void onUiThreadSuccess(Object result) {
            onTextLoadSuccess(result, cell);
            onLoad.run();
          }

          @Override
          protected void onUiThreadError(String error) {
            onTextLoadFailure(error);
          }
        });
      }
    };
    myList = new CellList<T>(orientation, initialText, loader) {
      @Override
      protected CellRenderer<T> createCellRenderer(CellRenderer.CellLoader<T> loader) {
        return new CellRenderer<T>(loader) {
          private final JBLabel label = new JBLabel() {{
            setOpaque(true);
          }};

          @Override
          protected T createNullCell() {
            return EmptyCell();
          }

          @Override
          protected Component getRendererComponent(@NotNull JList list, @NotNull T cell) {
            label.setText(cell.toString());
            label.setBackground(UIUtil.getListBackground(cell.isSelected));
            return label;
          }

          @Override
          public Dimension getInitialCellSize() {
            return null;
          }
        };
      }
    };
    myList.setBorder(JBUI.Borders.emptyLeft(JBUI.scale(4)));
    myList.setBackground(UIUtil.getListBackground());

  }

  public interface PathResource {
    public Path getPath();
  }

  public abstract T EmptyCell();

  public abstract void onTextLoadSuccess(Object result, T cell);

  public abstract void onTextLoadFailure(String error);


  public CellList<T> getList() {
    return myList;
  }
}
