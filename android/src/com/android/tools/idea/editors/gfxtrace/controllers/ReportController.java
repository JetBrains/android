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
import com.android.tools.idea.editors.gfxtrace.models.ReportStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.ReportItem;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.util.Arrays;

// TODO: Check if there's a need of TreeController (probably some kind of ListController will satisfy this entity).
public class ReportController extends TreeController implements ReportStream.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new ReportController(editor).myPanel;
  }

  private interface Renderable {
    void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);
  }

  public static class Node extends DefaultMutableTreeNode implements Renderable {
    private static final int PREVIEW_LENGTH = 80;

    public static Node createInstance(ReportItem item) {
      Node node = new Node(item);
      node.add(new Node(item.getMessage()));
      return node;
    }

    private Node(ReportItem item) {
      super(item);
    }

    private Node(String message) {
      super(message, false);
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    public LogProtos.Severity getSeverity() {
      return ((ReportItem) userObject).getSeverity();
    }

    public long getAtomId() {
      return ((ReportItem) userObject).getAtom();
    }

    public String getMessage() {
      return ((ReportItem) userObject).getMessage();
    }

    public String getMessagePreview() {
      return getMessage().substring(0, Math.min(getMessage().length(), PREVIEW_LENGTH));
    }
  }

  private ReportController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getReportStream().addListener(this);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
  }

  @Override
  public void notifyPath(PathEvent event) {

  }

  @NotNull
  @Override
  protected TreeCellRenderer createRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (value instanceof Renderable) {
          Renderable renderable = (Renderable)value;
          renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else if (value instanceof DefaultMutableTreeNode) {
          // Root of the report, no need to render.
        }
        else {
          assert false : value;
        }
      }
    };
  }

  @NotNull
  @Override
  protected TreeModel createEmptyModel() {
    return new DefaultTreeModel(new DefaultMutableTreeNode());
  }

  @NotNull
  @Override
  public String[] getColumns(TreePath path) {
    Object object = path.getLastPathComponent();
    SimpleColoredComponent component = new SimpleColoredComponent();
    if (object instanceof ReportController.Node) {
      Node node = (ReportController.Node)object;
      node.render(component, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      return new String[]{node.getAtomId() + ":", node.getMessage()};
    }
    return new String[]{object.toString()};
  }

  @Override
  public void onReportLoadingStart(ReportStream reportStream) {
    myTree.getEmptyText().setText("");
    myLoadingPanel.startLoading();
  }

  @Override
  public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
    // TODO: Display detailed empty view and/or error message
    myLoadingPanel.showLoadingError("Failed to load report");
  }

  @Override
  public void onReportLoadingSuccess(ReportStream reportStream) {
    if (reportStream.isLoaded()) {
      myLoadingPanel.stopLoading();
      updateTree(reportStream);
    }
    else {
      myLoadingPanel.showLoadingError("Failed to load report");
    }
  }

  private void updateTree(ReportStream reportStream) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Report", true);
    Arrays.stream(reportStream.getReport().getItems()).map(Node::createInstance).forEach(root::add);
    setRoot(root);
  }

  private void setRoot(DefaultMutableTreeNode root) {
    setModel(new DefaultTreeModel(root));
  }
}