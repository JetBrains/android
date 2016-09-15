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
import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.forms.ShaderEditorPanel;
import com.android.tools.idea.editors.gfxtrace.gapi.GapisConnection;
import com.android.tools.idea.editors.gfxtrace.lang.glsl.highlighting.GlslSyntaxHighlighter;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.models.ResourceCollection;
import com.android.tools.idea.editors.gfxtrace.renderers.CellRenderer;
import com.android.tools.idea.editors.gfxtrace.service.Context;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.ResourceBundle;
import com.android.tools.idea.editors.gfxtrace.service.ResourceInfo;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Program;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Shader;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Uniform;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.CellList;
import com.android.tools.idea.editors.gfxtrace.widgets.CellWidget;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.android.tools.rpclib.binary.BinaryID;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.border.IdeaTitledBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StatusText;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.UniformFormat.*;

/**
 * Controller for displaying shaders/programs.
 */
public class ShadersController extends Controller implements ResourceCollection.Listener, AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new ShadersController(editor).myPanel;
  }

  private static abstract class CharMemoryModel {
    private int rows;
    private int cols;

    public CharMemoryModel(int rows, int cols) {
      this.rows = rows;
      this.cols = cols;
    }

    public String formatMatrix() {
      StringBuilder sb = new StringBuilder(50);
      for (int row = 0; row < rows - 1; row++) {
        sb.append(formatLine(row, cols) + "\n");
      }
      sb.append(formatLine(rows - 1, cols));
      return sb.toString();
    }

    private String formatLine(int row, int cols) {
      StringBuilder sb = new StringBuilder(50);
      int CHARS_PER_FLOAT = 10;
      int FLOAT_SEPARATOR = 1;
      int myCharsPerRow = (CHARS_PER_FLOAT + FLOAT_SEPARATOR) * cols;

      char[] buffer = new char[myCharsPerRow];
      Arrays.fill(buffer, ' ');

      for (int i = row * cols, j = 0; i < row * cols + cols; i++, j += CHARS_PER_FLOAT + FLOAT_SEPARATOR) {
        sb.setLength(0);
        if (validIndex(i)) {
          charsFromInput(i, sb);
        }
        else {
          charsFromUnknown(sb);
        }
        int count = Math.min(CHARS_PER_FLOAT, sb.length());
        int dstBegin = j + CHARS_PER_FLOAT - count + 1;
        sb.getChars(0, count, buffer, dstBegin);
      }
      return new String(buffer);
    }

    public abstract boolean validIndex(int index);

    public abstract void charsFromInput(int index, StringBuilder sb);

    public abstract void charsFromUnknown(StringBuilder sb);
  }

  private static class UniformDimensions {
    public Map<GfxAPIProtos.UniformFormat, ArrayDimensions> matrixFormat;

    public UniformDimensions() {
      matrixFormat = new HashMap<>();
      matrixFormat.put(Scalar, new ArrayDimensions(1, 1));
      matrixFormat.put(Vec2, new ArrayDimensions(1, 2));
      matrixFormat.put(Vec3, new ArrayDimensions(1, 3));
      matrixFormat.put(Vec4, new ArrayDimensions(1, 4));
      matrixFormat.put(Mat2, new ArrayDimensions(2, 2));
      matrixFormat.put(Mat3, new ArrayDimensions(3, 3));
      matrixFormat.put(Mat4, new ArrayDimensions(4, 4));
      matrixFormat.put(Mat2x3, new ArrayDimensions(2, 3));
      matrixFormat.put(Mat2x4, new ArrayDimensions(2, 4));
      matrixFormat.put(Mat3x2, new ArrayDimensions(3, 2));
      matrixFormat.put(Mat3x4, new ArrayDimensions(3, 4));
      matrixFormat.put(Mat4x2, new ArrayDimensions(4, 2));
      matrixFormat.put(Mat4x3, new ArrayDimensions(4, 3));
      matrixFormat.put(Sampler, new ArrayDimensions(1, 1));
    }

    public ArrayDimensions getDimensions(GfxAPIProtos.UniformFormat uniformFormat) {
      return matrixFormat.get(uniformFormat);
    }
  }

  private static class ArrayDimensions {
    private int rows = -1;
    private int cols = -1;

    public ArrayDimensions(int rows, int cols) {
      this.rows = rows;
      this.cols = cols;
    }

    public int getRows() {
      return rows;
    }

    public int getCols() {
      return cols;
    }
  }

  @NotNull private static final Logger LOG = Logger.getInstance(ShadersController.class);

  private static final String CARD_EMPTY = "empty";
  private static final String CARD_SHADERS = "shaders";

  private static class ShaderData extends CellList.Data implements TextCellController.PathResource {
    public static final ShaderData EMPTY = new ShaderData(null, null);

    public final ResourceInfo resource;
    public final ResourcePath path;
    @NotNull public String source = "";
    public Map<GfxAPIProtos.ShaderType, ResourceID> shaderResources = Collections.emptyMap();
    public Uniform[] uniforms;

    private Shader shader;

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

    public ResourceInfo getResourceInfo() {
      return resource;
    }

    public Shader getShader() {
      return shader;
    }

    public void setShader(Shader shader) {
      this.shader = shader;
      this.source = Strings.nullToEmpty(shader.getSource());
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
      if (data == null) {
        return "";
      }
      else {
        return data.getType() + " " + data.getFormat() + " " + data.getName();
      }
    }

    private String formatData(int[] data, GfxAPIProtos.UniformFormat format) {
      ArrayDimensions dims = new UniformDimensions().getDimensions(format);
      CharMemoryModel intModel = new CharMemoryModel(dims.getRows(), dims.getCols()) {
        @Override
        public boolean validIndex(int index) {
          return index < data.length;
        }

        @Override
        public void charsFromInput(int index, StringBuilder sb) {
          sb.append(data[index]);
        }

        @Override
        public void charsFromUnknown(StringBuilder sb) {
          sb.append("0");
        }
      };
      return intModel.formatMatrix();
    }

    private String formatData(float[] data, GfxAPIProtos.UniformFormat format) {
      ArrayDimensions dims = new UniformDimensions().getDimensions(format);
      CharMemoryModel floatModel = new CharMemoryModel(dims.getRows(), dims.getCols()) {
        @Override
        public boolean validIndex(int index) {
          return index < data.length;
        }

        @Override
        public void charsFromInput(int index, StringBuilder sb) {
          sb.append(data[index]);
        }

        @Override
        public void charsFromUnknown(StringBuilder sb) {
          sb.append("0.0");
        }
      };
      return floatModel.formatMatrix();
    }

    public String getFormattedValue() {
      return data.getType() == GfxAPIProtos.UniformType.Float ?
             formatData((float[])data.getValue(), data.getFormat()) :
             formatData((int[])data.getValue(), data.getFormat());

    }
  }

  @NotNull private final JPanel myPanel = new JPanel(new CardLayout());
  @NotNull private final ThreeComponentsSplitter mySplitter = new ThreeComponentsSplitter(false);
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
  @NotNull private final SourcePanel mySourcePanel;
  @NotNull private final JTextArea myUniformValuePanel = new JTextArea();

  public ShadersController(@NotNull GfxTraceEditor editor) {
    super(editor);
    editor.getResourceCollection().addListener(this);
    editor.getAtomStream().addListener(this);

    // Init shaders and programs lists.
    myProgramsList = new ShaderCellController(editor, CellList.Orientation.VERTICAL, GfxTraceEditor.SELECT_ATOM);
    myShadersList = new ShaderCellController(editor, CellList.Orientation.VERTICAL, GfxTraceEditor.SELECT_ATOM);

    // Set up search fields.
    RegexFilterComponent myShaderSearchField = new RegexFilterComponent(ShadersController.class.getName(), 10);
    RegexFilterComponent myProgramSearchField = new RegexFilterComponent(ShadersController.class.getName(), 10);
    JPanel programsTab = new JPanel();
    programsTab.setLayout(new BorderLayout());
    JPanel shadersTab = new JPanel();
    shadersTab.setLayout(new BorderLayout());

    myShaderSearchField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
          searchList(myShadersList.getList(), myShaderSearchField, false);
        }
      }
    });

    myProgramSearchField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
          searchList(myProgramsList.getList(), myProgramSearchField, true);
        }
      }
    });
    shadersTab.add(myShaderSearchField, BorderLayout.NORTH);
    shadersTab.add(myShadersList.getList(), BorderLayout.CENTER);
    programsTab.add(myProgramSearchField, BorderLayout.NORTH);
    programsTab.add(myProgramsList.getList(), BorderLayout.CENTER);

    // Add shader and programs lists to tabs.
    JBRunnerTabs tabs = new JBRunnerTabs(editor.getProject(), ActionManager.getInstance(), IdeFocusManager.findInstance(), this);
    tabs.addTab(new TabInfo(shadersTab).setText("Shaders"));
    tabs.addTab(new TabInfo(programsTab).setText("Programs"));

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

    // Define it here so listener below can use it safely.
    mySourcePanel = new SourcePanel(myEditor.getProject());
    // Release editor on disposal.
    Disposer.register(this, mySourcePanel);

    final ShaderEditorPanel shaderEditorPanel = new ShaderEditorPanel();

    final JButton pushButton = shaderEditorPanel.getPushChangesButton();
    pushButton.addActionListener(event -> {
      final ShaderData data = mySourcePanel.getUpdatedShaderData();
      if (data == null) {
        return;
      }
      Rpc.listen(myEditor.getClient().set(data.getPath(), data.getShader()), new UiCallback<Path, ResourcePath>(myEditor, LOG) {
        @Override
        protected ResourcePath onRpcThread(Rpc.Result<Path> result)
          throws RpcException, ExecutionException, Channel.NotConnectedException {
          return (ResourcePath)result.get();
        }

        @Override
        protected void onUiThread(ResourcePath result) {
          // Activate atom path
          myEditor.activatePath(result.getParent(), this);
          update(false);
        }
      });
    });

    pushButton.setEnabled(false);
    mySourcePanel.setPushButton(pushButton);
    myEditor.addConnectionListener(connection -> {
      if (!myEditor.getFeatures().hasShaderSourceSet()) {
        shaderEditorPanel.getPanel().remove(pushButton);
      }
    });

    mySourcePanel.addTopPanel(shaderEditorPanel.getPanel());

    // Set listeners for selection actions.
    myProgramsList.getList().addSelectionListener((CellWidget.SelectionListener<ShaderData>)item -> {
      myShadersList.getList().selectItem(-1, false);
      myUniformsList.clearData();
      mySourcePanel.setData(item);
      loadProgramSource(item);
      loadUniforms(item);
    });
    myShadersList.getList().addSelectionListener((CellWidget.SelectionListener<ShaderData>)item -> {
      myProgramsList.getList().selectItem(-1, false);
      mySourcePanel.setData(item);
      myUniformsList.clearData();
    });
    myUniformsList.addSelectionListener(new CellWidget.SelectionListener<UniformData>() {
      @Override
      public void selected(UniformData item) {
        myUniformValuePanel.setText(item.getFormattedValue());
        myUniformsList.selectItem(myUniformsList.getSelectedIndex(), false);
      }
    });


    // Set up splitters.
    ThreeComponentsSplitter programDataPanel = new ThreeComponentsSplitter(true);
    ThreeComponentsSplitter uniformDataPanel = new ThreeComponentsSplitter(false);

    // Register for future garbage collection.
    Disposer.register(this, programDataPanel);
    Disposer.register(this, uniformDataPanel);
    Disposer.register(this, mySplitter);

    // Set up borders.
    JPanel uniformCompositePanel = new JPanel();
    uniformCompositePanel.setLayout(new BoxLayout(uniformCompositePanel, BoxLayout.Y_AXIS));
    TitledBorder uniformBorder = new IdeaTitledBorder("Uniforms", 0, new Insets(0, 0, 0, 0));
    uniformBorder.setTitleJustification(IdeaTitledBorder.CENTER);
    uniformCompositePanel.setBorder(uniformBorder);
    uniformCompositePanel.add(uniformDataPanel);

    JPanel shaderCompositePanel = new JPanel();
    shaderCompositePanel.setLayout(new BoxLayout(shaderCompositePanel, BoxLayout.Y_AXIS));
    IdeaTitledBorder shaderBorder = new IdeaTitledBorder("Shader source", 0, new Insets(5, 5, 5, 5));
    shaderBorder.setTitleJustification(IdeaTitledBorder.CENTER);
    shaderCompositePanel.setBorder(shaderBorder);
    shaderCompositePanel.add(programDataPanel);

    // Set up full UI.
    programDataPanel.setDividerWidth(5);
    programDataPanel.setFirstSize(JBUI.scale(600));
    uniformDataPanel.setDividerWidth(5);
    uniformDataPanel.setFirstSize(JBUI.scale(200));
    mySplitter.setDividerWidth(5);
    mySplitter.setFirstSize(JBUI.scale(200));

    myUniformValuePanel.setEditable(false);
    uniformDataPanel.setFirstComponent(myUniformsList);
    uniformDataPanel.setLastComponent(new JBScrollPane(myUniformValuePanel));
    programDataPanel.setFirstComponent(mySourcePanel);
    programDataPanel.setLastComponent(uniformCompositePanel);
    mySplitter.setFirstComponent(tabs);
    mySplitter.setLastComponent(shaderCompositePanel);
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
        cell.setShader((Shader)result);
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
    if (cell.uniforms != null) {
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
      if (BinaryID.INVALID.equals(resourceID)) {
        continue;
      }
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

  // Search for next occurrence of pattern in given CellList.
  public void searchList(CellList<ShaderData> cellList, RegexFilterComponent searchField, boolean isProgram) {
    Pattern pattern = searchField.getPattern();

    if (pattern == null) {
      return;
    }

    int currentIndex = cellList.getSelectedIndex() + 1;
    ShaderData currentItem = cellList.getItemAtIndex(currentIndex);
    while (currentItem != null && !pattern.matcher(currentItem.resource.getName()).find()) {
      currentIndex++;
      currentItem = cellList.getItemAtIndex(currentIndex);
    }

    if (currentItem != null) {
      cellList.selectItem(currentIndex, false);
      mySourcePanel.setData(cellList.getSelectedItem());
      if(isProgram){
        loadProgramSource(cellList.getSelectedItem());
        loadUniforms(cellList.getSelectedItem());
      }
    }
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
        if (bundle.getType() == null) {
          continue;
        }

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

  @Override
  public void onContextChanged(@NotNull Context context) {
  }

  // A LoadablePanel class that populates the Editor component with shader source code when it has been fetched.
  private static class SourcePanel extends LoadablePanel implements Disposable {

    private static Editor createEditor(@NotNull EditorFactory factory, @NotNull Document document,
                                       @NotNull Project project) {
      final EditorImpl e = (EditorImpl)factory.createEditor(document, project);
      final SyntaxHighlighter h = new GlslSyntaxHighlighter();
      e.setHighlighter(new LexerEditorHighlighter(h, e.getColorsScheme()));
      return e;
    }

    @NotNull private final Document myDocument;
    @NotNull private final Editor myEditor;

    private ShaderData myData;
    private JButton myPushButton;

    public SourcePanel(@NotNull Project project) {
      super(new BorderLayout());
      final EditorFactory factory = EditorFactory.getInstance();
      myDocument = factory.createDocument("");
      myEditor = createEditor(factory, myDocument, project);
      getContentLayer().add(myEditor.getComponent(), BorderLayout.CENTER);

      myDocument.addDocumentListener(new DocumentListener() {
        @Override
        public void beforeDocumentChange(DocumentEvent event) {
        }

        @Override
        public void documentChanged(DocumentEvent event) {
          if (myPushButton != null && myData != null) {
            myPushButton.setEnabled(!myData.source.equals(myDocument.getText()));
          }
        }
      });
    }

    public void setData(ShaderData data) {
      myData = data;
      update();
    }

    public void setPushButton(JButton pushButton) {
      myPushButton = pushButton;
    }

    public void update() {
      if (myData == null) {
        ApplicationManager.getApplication().runWriteAction(() -> myDocument.setText(""));
      }
      else if (!myData.isLoaded() || myData.source == null) {
        ApplicationManager.getApplication().runWriteAction(() -> myDocument.setText(""));
        startLoading();
      }
      else {
        ApplicationManager.getApplication().runWriteAction(() -> myDocument.setText(myData.source));
        stopLoading();
      }
    }

    @Override
    public void dispose() {
      if (myEditor != null) {
        EditorFactory.getInstance().releaseEditor(myEditor);
      }
    }

    public ShaderData getUpdatedShaderData() {
      if (myData != null) {
        final Shader shader = myData.getShader();
        shader.setSource(myDocument.getText());
        myData.setShader(shader);
      }
      return myData;
    }

    public void addTopPanel(@NotNull final JPanel panel) {
      getContentLayer().add(panel, BorderLayout.NORTH);
    }
  }
}
