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
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.models.ResourceCollection;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.ResourceBundle;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.*;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.*;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Controller for displaying shaders/programs.
 */
public class ShadersController extends Controller implements ResourceCollection.Listener, AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new ShadersController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(ShadersController.class);

  private static final String CARD_EMPTY = "empty";
  private static final String CARD_SHADERS = "shaders";

  private static class ShaderData extends CellList.Data implements TextCellController.PathResource {
    public static final ShaderData EMPTY = new ShaderData(null, null);

    public final ResourceInfo resource;
    public final ResourcePath path;
    public String source;
    public Map<GfxAPIProtos.ShaderType, ResourceID> shaderResources = Collections.emptyMap();
    public Uniform[] uniforms;

    public ShaderData(ResourceInfo resource, ResourcePath path) {
      this.resource = resource;
      this.path = path;
    }

    @Override
    public String toString() {
      return ((resource == null) ? "<Click to select shader>" : resource.getName());
    }

    @Override
    public Path getPath() {
      return path;
    }
  }

  private static class UniformData extends CellList.Data {
    public static final UniformData EMPTY = new UniformData(null);

    public final Uniform data;

    public UniformData(Uniform data) {
      this.data = data;
    }

    @Override
    public String toString() {
      return data.getType() + " " + data.getFormat() + " " + data.getName();
    }

  }

  @NotNull private final JPanel myPanel = new JPanel(new CardLayout());
  @NotNull private final JBSplitter mySplitter = new JBSplitter(false, 0.3f);
  @NotNull private final CellList<UniformData> myUniformsList;
  private final EmptyPanel myEmptyPanel = new EmptyPanel();

  private static class EmptyPanel extends JComponent {
    private final StatusText myEmptyText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return true;
      }
    };

    public EmptyPanel() {
      myEmptyText.setText(GfxTraceEditor.LOADING_CAPTURE);
      myEmptyText.attachTo(this);
    }

    public void setEmptyText(String text) {
      myEmptyText.setText(text);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      myEmptyText.paint(this, graphics);
    }
  }

  @NotNull private final ShaderCellController myProgramsList;
  @NotNull private final ShaderCellController myShadersList;
  @NotNull private final SourcePanel mySourcePanel = new SourcePanel();

  public ShadersController(@NotNull GfxTraceEditor editor) {
    super(editor);
    editor.getResourceCollection().addListener(this);
    editor.getAtomStream().addListener(this);

    // Init shaders and programs lists.
    myProgramsList = new ShaderCellController(editor, CellList.Orientation.VERTICAL, GfxTraceEditor.SELECT_ATOM);
    myShadersList = new ShaderCellController(editor, CellList.Orientation.VERTICAL, GfxTraceEditor.SELECT_ATOM);

    // Add shader and programs lists to tabs.
    JBRunnerTabs tabs = new JBRunnerTabs(editor.getProject(), ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    tabs.addTab(new TabInfo(myShadersList.getList()).setText("Shaders"));
    tabs.addTab(new TabInfo(myProgramsList.getList()).setText("Programs"));

    // Init uniform panel.
    final CellRenderer.CellLoader<UniformData> uniformsLoader = (cell, onLoad) -> onLoad.run();

    final CellRenderer<UniformData> uniformsRenderer = new CellRenderer<UniformData>(uniformsLoader) {
      private final JBLabel label = new JBLabel() {{
        setOpaque(true);
      }};

      @Override
      protected UniformData createNullCell() {
        return UniformData.EMPTY;
      }

      @Override
      protected Component getRendererComponent(@NotNull JList list, @NotNull UniformData cell) {
        label.setText(cell.toString());
        label.setBackground(UIUtil.getListBackground(cell.isSelected));
        return label;
      }

      @Nullable
      @Override
      public Dimension getInitialCellSize() {
        return null;
      }
    };

    // Init uniform list.
    myUniformsList = new CellList<UniformData>(CellList.Orientation.VERTICAL, "Select a program", uniformsLoader) {
      @Override
      protected CellRenderer<UniformData> createCellRenderer(CellRenderer.CellLoader<UniformData> loader) {
        return uniformsRenderer;
      }
    };

    // Set listeners for selection actions.
    myProgramsList.getList().addSelectionListener((CellWidget.SelectionListener<ShaderData>)item -> {
      myShadersList.getList().selectItem(-1, false);
      mySourcePanel.setData(item);
      loadProgramSource(item);
      loadUniforms(item);
    });
    myShadersList.getList().addSelectionListener((CellWidget.SelectionListener<ShaderData>)item -> {
      myProgramsList.getList().selectItem(-1, false);
      mySourcePanel.setData(item);
      myUniformsList.clearData();
    });

    // Set up full UI.
    JBSplitter programDataPanel = new JBSplitter(true, 0.8f);
    programDataPanel.setFirstComponent(mySourcePanel);
    programDataPanel.setSecondComponent(myUniformsList);
    mySplitter.setFirstComponent(tabs);
    mySplitter.setSecondComponent(programDataPanel);
    myPanel.add(mySplitter, CARD_SHADERS);
    myPanel.add(myEmptyPanel, CARD_EMPTY);
    ((CardLayout)myPanel.getLayout()).show(myPanel, CARD_EMPTY);

  }

  public class ShaderCellController extends TextCellController<ShaderData> {
    public ShaderCellController(GfxTraceEditor editor, CellList.Orientation orientation, String initText) {
      super(editor, orientation, initText);
    }

    @Override
    public ShaderData EmptyCell() {
      return ShaderData.EMPTY;
    }

    @Override
    public void onTextLoadSuccess(Object result, ShaderData cell) {
      if (result instanceof Program) {
        cell.shaderResources = ((Program)result).getShaders();
        cell.uniforms = ((Program)result).getUniforms();
        if (cell == myProgramsList.getList().getSelectedItem()) {
          loadProgramSource(cell);
          loadUniforms(cell);
        }
      }
      else if (result instanceof Shader) {
        cell.source = Strings.nullToEmpty(((Shader)result).getSource());
      }

      myProgramsList.getList().repaint();
      myShadersList.getList().repaint();
      mySourcePanel.update();
    }

    @Override
    public void onTextLoadFailure(String error) {
    }

    @Override
    public void notifyPath(PathEvent event) {
    }
  }

  // Load uniform labels for selected program.
  private void loadUniforms(ShaderData cell) {
    if (cell.uniforms != null){
      List<UniformData> uniforms = new LinkedList<>();
      for (Uniform uniform : cell.uniforms) {
        uniforms.add(new UniformData(uniform));
      }
      myUniformsList.setData(uniforms);
      myUniformsList.repaint();
    }
  }

  // Fetch associated shader source for a program.
  private void loadProgramSource(ShaderData cell) {
    if (cell.source != null || cell.shaderResources.isEmpty()) {
      return;
    }

    final List<ListenableFuture<Object>> shaderFutures = new ArrayList<>(cell.shaderResources.size());
    for (ResourceID resourceID : cell.shaderResources.values()) {
      ResourcePath shaderPath = myEditor.getAtomStream().getSelectedAtomsPath().getPathToLast().resourceAfter(resourceID);
      ListenableFuture<Object> myFuturePath = myEditor.getClient().get(shaderPath);
      shaderFutures.add(myFuturePath);
    }
    ListenableFuture<List<Object>> futureOfShaders = Futures.allAsList(shaderFutures);
    Rpc.listen(futureOfShaders, cell.controller, new UiErrorCallback<List<Object>, List<Object>, String>(myEditor, LOG) {
      @Override
      protected ResultOrError<List<Object>, String> onRpcThread(Rpc.Result<List<Object>> result)
        throws RpcException, ExecutionException, Channel.NotConnectedException {
        try {
          return success(result.get());
        }
        catch (ErrDataUnavailable e) {
          return error(e.getMessage());
        }
      }

      @Override
      protected void onUiThreadSuccess(List<Object> result) {
        StringBuilder sb = new StringBuilder();
        for (Object returnValue : result) {
          if (returnValue instanceof Shader) {
            sb.append("//" + ((Shader)returnValue).getShaderType() + " Shader\n");
            sb.append(((Shader)returnValue).getSource());
            sb.append("\n\n");
          }
        }
        cell.source = sb.toString();
        mySourcePanel.update();
      }

      @Override
      protected void onUiThreadError(String error) {
      }
    });
  }

  private static final ResourceInfo[] NO_RESOURCES = new ResourceInfo[0];

  // Filter ResourceInfo by type and validity based on current Atom.
  private void update(boolean resourcesChanged) {
    if (myEditor.getResourceCollection() != null) {
      ((CardLayout)myPanel.getLayout()).show(myPanel, CARD_SHADERS);
      //TODO Add error message to empty panel
      ResourceInfo[] shadersBundle = NO_RESOURCES;
      ResourceInfo[] programsBundle = NO_RESOURCES;
      for (ResourceBundle bundle : myEditor.getResourceCollection().getResourceBundles().getBundles()) {
        switch (bundle.getType()) {
          case Shader:
            shadersBundle = bundle.getResources();
            break;
          case Program:
            programsBundle = bundle.getResources();
            break;
        }
      }

      AtomRangePath atomRangePath = myEditor.getAtomStream().getSelectedAtomsPath();
      if (atomRangePath != null) {
        updateResources(myProgramsList.getList(), programsBundle, atomRangePath.getPathToLast(), resourcesChanged);
        updateResources(myShadersList.getList(), shadersBundle, atomRangePath.getPathToLast(), resourcesChanged);
      }
    }
  }

  // Populate programs and shaders lists with fetched ResourceInfo.
  private static void updateResources(CellList<ShaderData> list, ResourceInfo[] resources, AtomPath atomPath, boolean resourcesChanged) {
    List<ShaderData> cells = Lists.newArrayList();
    for (ResourceInfo program : resources) {
      if (program.getFirstAccess() <= atomPath.getIndex()) {
        cells.add(new ShaderData(program, atomPath.resourceAfter(program.getID())));
      }
    }

    int selectedIndex = list.getSelectedIndex();
    list.setData(cells);
    if (!resourcesChanged && selectedIndex >= 0 && selectedIndex < cells.size()) {
      list.selectItem(selectedIndex, true);
    }
    else {
      list.selectItem(-1, true);
    }
  }


  // Fetch ResourceInfo.
  @Override
  public void notifyPath(PathEvent event) {
  }

  @Override
  public void onResourceLoadingStart(ResourceCollection resources) {
  }

  @Override
  public void onResourceLoadingComplete(ResourceCollection resources) {
    if (myEditor.getFeatures().hasResourceBundles()) {
      update(true);
    }
    else {
      myEmptyPanel.setEmptyText("Not supported in this version.");
      ((CardLayout)myPanel.getLayout()).show(myPanel, CARD_EMPTY);
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

  // A LoadablePanel class that populates the JTextArea with shader source code when it has been fetched.
  private static class SourcePanel extends LoadablePanel {
    private final JTextArea myCodeArea = new JTextArea();

    private ShaderData myData;

    public SourcePanel() {
      super(new BorderLayout());
      getContentLayer().add(new JBScrollPane(myCodeArea), BorderLayout.CENTER);
    }

    public void setData(ShaderData data) {
      myData = data;
      update();
    }

    public void update() {
      if (myData == null) {
        myCodeArea.setText(null);
      }
      else if (!myData.isLoaded() || myData.source == null) {
        startLoading();
      }
      else {
        myCodeArea.setText(myData.source);
        stopLoading();
      }
    }
  }
}