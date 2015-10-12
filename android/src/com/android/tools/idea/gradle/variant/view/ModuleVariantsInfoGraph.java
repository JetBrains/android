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
package com.android.tools.idea.gradle.variant.view;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil.ComponentStyle;
import com.intellij.util.ui.UIUtil.FontSize;
import com.mxgraph.canvas.mxICanvas;
import com.mxgraph.canvas.mxImageCanvas;
import com.mxgraph.layout.mxCircleLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.model.mxGraphModel;
import com.mxgraph.model.mxIGraphModel;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.util.mxGraphTransferable;
import com.mxgraph.swing.view.mxInteractiveCanvas;
import com.mxgraph.view.mxCellState;
import com.mxgraph.view.mxGraph;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.Serializable;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.*;
import static com.intellij.openapi.util.text.StringUtil.notNullize;
import static com.intellij.util.ui.UIUtil.getFontSize;
import static javax.swing.SwingConstants.RIGHT;

class ModuleVariantsInfoGraph extends DialogWrapper {
  @NotNull private final Module myModule;
  @NotNull private final AndroidGradleModel myAndroidModel;

  @NotNull private Variant mySelectedVariant;

  private mxGraphComponent myGraphComponent;
  private VariantGraph myGraph;

  ModuleVariantsInfoGraph(@NotNull Module module) {
    super(module.getProject());
    myModule = module;

    AndroidGradleModel androidModel = AndroidGradleModel.get(myModule);
    assert androidModel != null;
    myAndroidModel = androidModel;
    mySelectedVariant = myAndroidModel.getSelectedVariant();

    setTitle(String.format("Dependency Details for Module '%1$s'", myModule.getName()));
    init();
  }

  @Override
  @NotNull
  protected JComponent createCenterPanel() {
    JPanel mainPanel = new JPanel(new BorderLayout());
    mainPanel.setPreferredSize(JBUI.size(600, 400));

    ActionGroup group =
      new DefaultActionGroup(new VariantsComboBoxAction(), Separator.getInstance(), new ResetLayoutAction(), new ShowGridAction(),
                             Separator.getInstance(), new ZoomInAction(), new ZoomOutAction(), new ZoomActualAction());

    ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("TOP", group, true);
    mainPanel.add(toolbar.getComponent(), BorderLayout.NORTH);

    myGraph = new VariantGraph();
    myGraphComponent = new mxGraphComponent(myGraph) {
      @Override
      public mxInteractiveCanvas createCanvas() {
        return new CustomCanvas(myModule.getProject(), this);
      }
    };
    myGraphComponent.setGridVisible(true);
    myGraphComponent.setToolTips(false);
    mainPanel.add(myGraphComponent, BorderLayout.CENTER);

    myGraph.render(myModule, myAndroidModel, mySelectedVariant);

    return mainPanel;
  }

  private static class VariantGraph extends mxGraph {
    private static final double VERTEX_WIDTH = 100d;
    private static final double VERTEX_HEIGHT = 30d;

    static {
      mxGraphTransferable.enableImageSupport = false;
    }

    VariantGraph() {
      setCellsCloneable(false);
      setCellsDeletable(false);
      setCellsDisconnectable(false);
      setCellsEditable(false);
      setCellsResizable(false);
    }

    void render(@NotNull Module module, @NotNull AndroidGradleModel androidModel, @NotNull Variant variant) {
      setModel(new mxGraphModel());

      mxIGraphModel model = getModel();
      model.beginUpdate();

      try {
        mxCell parent = (mxCell)getDefaultParent();

        mxCell moduleVertex = createVertex(module);
        moduleVertex.setConnectable(false);

        for (AndroidLibrary library : getDirectLibraryDependencies(variant, androidModel)) {
          String gradlePath = library.getProject();
          if (gradlePath == null) {
            continue;
          }
          Module dependency = findModuleByGradlePath(module.getProject(), gradlePath);
          if (dependency != null) {
            mxCell dependencyVertex = createVertex(dependency);
            dependencyVertex.setConnectable(false);
            String projectVariant = notNullize(library.getProjectVariant());
            insertEdge(parent, null, projectVariant, moduleVertex, dependencyVertex);
          }
        }
      }
      finally {
        model.endUpdate();
      }

      resetLayout();
    }

    @NotNull
    private mxCell createVertex(@NotNull Module module) {
      ModuleVertexModel model = new ModuleVertexModel(module.getName());
      return (mxCell)insertVertex(getDefaultParent(), null, model, 0, 0, VERTEX_WIDTH, VERTEX_HEIGHT);
    }

    void resetLayout() {
      mxCircleLayout layout = new mxCircleLayout(this);
      layout.setDisableEdgeStyle(false);
      layout.execute(getDefaultParent());
    }

    @Override
    public void drawState(mxICanvas canvas, mxCellState state, boolean drawLabel) {
      mxIGraphModel model = getModel();

      // Indirection for wrapped CustomCanvas inside image canvas (used for creating the preview image when cells are dragged)
      boolean vertex = model.isVertex(state.getCell());
      if (vertex && canvas instanceof mxImageCanvas && ((mxImageCanvas)canvas).getGraphicsCanvas() != null) {
        ((CustomCanvas)((mxImageCanvas)canvas).getGraphicsCanvas()).drawVertex(state, drawLabel);
        return;
      }
      // Redirection of drawing vertices in CustomCanvas
      if (vertex && canvas instanceof CustomCanvas) {
        ((CustomCanvas)canvas).drawVertex(state, drawLabel);
        return;
      }
      super.drawState(canvas, state, drawLabel);
    }
  }

  private static class ModuleVertexModel implements Serializable {
    @NotNull String name;

    ModuleVertexModel(@NotNull String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private static class CustomCanvas extends mxInteractiveCanvas {
    @NotNull private final Project myProject;
    @NotNull private final mxGraphComponent myGraphComponent;

    @NotNull private final JBLabel myVertexRenderer = new JBLabel();

    CustomCanvas(@NotNull Project project, @NotNull mxGraphComponent graphComponent) {
      myProject = project;
      myGraphComponent = graphComponent;

      myVertexRenderer.setOpaque(true);
      myVertexRenderer.setBackground(JBColor.background());
      myVertexRenderer.setForeground(JBColor.foreground());
      myVertexRenderer.setBorder(BorderFactory.createLineBorder(JBColor.border(), 1));
      myVertexRenderer.setHorizontalTextPosition(RIGHT);
      myVertexRenderer.setComponentStyle(ComponentStyle.SMALL);
    }

    void drawVertex(@NotNull mxCellState cellState, boolean drawLabel) {
      mxCell cell = (mxCell)cellState.getCell();
      Object value = cell.getValue();

      String text;
      Icon icon = null;
      if (value instanceof ModuleVertexModel) {
        ModuleVertexModel model = (ModuleVertexModel)value;
        text = (drawLabel) ? model.name : "";

        Module module = ModuleManager.getInstance(myProject).findModuleByName(model.name);
        assert module != null;
        icon = getModuleIcon(module);
      }
      else {
        text = (drawLabel) ? cellState.getLabel() : "";
      }

      int w = (int)cellState.getWidth();
      int h = (int)cellState.getHeight();

      float scale = JBUI.scale((float)getScale());
      // scale the font in the vertex
      float defaultFontSize = getFontSize(FontSize.SMALL);
      Font newFont = myVertexRenderer.getFont().deriveFont(defaultFontSize * scale);
      myVertexRenderer.setFont(newFont);

      // scale the icon in the vertex
      if (icon instanceof ScalableIcon) {
        ScalableIcon scalableIcon = (ScalableIcon)icon;
        icon = scalableIcon.scale(scale);
      }

      myVertexRenderer.setText(text);
      myVertexRenderer.setIcon(icon);
      myVertexRenderer.setSize(w, h);

      rendererPane.paintComponent(g, myVertexRenderer, myGraphComponent, (int)cellState.getX() + translate.x,
                                  (int)cellState.getY() + translate.y, w, h, true);
    }
  }

  private class VariantsComboBoxAction extends ComboBoxAction {
    @Override
    public JComponent createCustomComponent(Presentation presentation) {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(new JLabel("Variant: "), BorderLayout.WEST);
      panel.add(createComboBoxButton(presentation), BorderLayout.CENTER);
      panel.setBorder(IdeBorderFactory.createEmptyBorder(2, 6, 2, 0));
      return panel;
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setText(mySelectedVariant.getName());
    }

    @Override
    @NotNull
    protected DefaultActionGroup createPopupActionGroup(JComponent button) {
      List<VariantSelectionAction> actions = Lists.newArrayList();
      for (Variant variant : myAndroidModel.getAndroidProject().getVariants()) {
        actions.add(new VariantSelectionAction(variant));
      }
      return new DefaultActionGroup(actions);
    }
  }

  private class VariantSelectionAction extends DumbAwareAction {
    @NotNull private final Variant myVariant;

    VariantSelectionAction(@NotNull Variant variant) {
      super(variant.getName());
      myVariant = variant;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySelectedVariant = myVariant;
      myGraph.render(myModule, myAndroidModel, mySelectedVariant);
      myGraphComponent.refresh();
    }
  }

  private class ShowGridAction extends ToggleAction {
    ShowGridAction() {
      super("Show/Hide Grid", null, AllIcons.Graph.Grid);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myGraphComponent != null);
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myGraphComponent != null && myGraphComponent.isGridVisible();
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (myGraphComponent != null) {
        myGraphComponent.setGridVisible(state);
        myGraphComponent.refresh();
      }
    }
  }

  private class ResetLayoutAction extends DumbAwareAction {
    ResetLayoutAction() {
      super("Reset Layout", null, AllIcons.Graph.Layout);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myGraph != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myGraph != null) {
        myGraph.resetLayout();
      }
    }
  }

  private class ZoomInAction extends DumbAwareAction {
    ZoomInAction() {
      super("Zoom In", null, AllIcons.Graph.ZoomIn);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myGraphComponent != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myGraphComponent != null) {
        myGraphComponent.zoomIn();
      }
    }
  }

  private class ZoomOutAction extends DumbAwareAction {
    ZoomOutAction() {
      super("Zoom Out", null, AllIcons.Graph.ZoomOut);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myGraphComponent != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myGraphComponent != null) {
        myGraphComponent.zoomOut();
      }
    }
  }

  private class ZoomActualAction extends DumbAwareAction {
    ZoomActualAction() {
      super("Actual Size", null, AllIcons.Graph.ActualZoom);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myGraphComponent != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myGraphComponent != null) {
        myGraphComponent.zoomActual();
      }
    }
  }
}
