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

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.GfxAPIProtos.DrawPrimitive;
import com.android.tools.idea.editors.gfxtrace.service.gfxapi.Mesh;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.MeshPath;
import com.android.tools.idea.editors.gfxtrace.service.path.MeshPathOptions;
import com.android.tools.idea.editors.gfxtrace.service.vertex.*;
import com.android.tools.idea.editors.gfxtrace.service.vertex.VertexProtos.*;
import com.android.tools.idea.editors.gfxtrace.viewer.Geometry;
import com.android.tools.idea.editors.gfxtrace.viewer.Viewer;
import com.android.tools.idea.editors.gfxtrace.viewer.camera.CylindricalCameraModel;
import com.android.tools.idea.editors.gfxtrace.viewer.camera.IsoSurfaceCameraModel;
import com.android.tools.idea.editors.gfxtrace.viewer.geo.Model;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.StatusText;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLJPanel;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class GeometryController extends Controller implements AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new GeometryController(editor).myPanel;
  }

  private static final Logger LOG = Logger.getInstance(GeometryController.class);

  private static final String CARD_EMPTY = "empty";
  private static final String CARD_GEOMETRY = "geometry";
  private static final FmtFloat32 FMT_XYZ_F32 = new FmtFloat32().setOrder(
    new VectorElement[]{VectorElement.X, VectorElement.Y, VectorElement.Z}
  );

  private final JPanel myPanel = new JPanel(new CardLayout());
  private final JBLoadingPanel myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject(), 50);
  private final EmptyPanel myEmptyPanel = new EmptyPanel();
  private final GLJPanel myCanvas;
  private final IsoSurfaceCameraModel myCamera = new IsoSurfaceCameraModel(new CylindricalCameraModel());
  private final Viewer myViewer = new Viewer(myCamera);

  private Geometry myGeometry = new Geometry();
  private Geometry.DisplayMode myDisplayMode = Geometry.DisplayMode.TRIANGLES;
  private boolean myZUp = false;
  private Model myOriginalModel = null;
  private Model myFacetedModel = null;

  public GeometryController(@NotNull GfxTraceEditor editor) {
    super(editor);
    editor.getAtomStream().addListener(this);

    GLProfile profile = GLProfile.getDefault();
    GLCapabilities caps = new GLCapabilities(profile);
    caps.setDoubleBuffered(true);
    caps.setHardwareAccelerated(true);
    caps.setSampleBuffers(true);
    myCanvas = new GLJPanel(caps);
    myCanvas.addGLEventListener(myViewer);
    myViewer.addMouseListeners(myCanvas);

    JPanel geoPanel = new JPanel(new BorderLayout());
    geoPanel.add(myCanvas, BorderLayout.CENTER);
    geoPanel.add(
      ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, getToolbarActions(), false).getComponent(), BorderLayout.WEST);
    myLoading.add(geoPanel, BorderLayout.CENTER);
    myPanel.add(myEmptyPanel, CARD_EMPTY);
    myPanel.add(myLoading, CARD_GEOMETRY);
  }

  private DefaultActionGroup getToolbarActions() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new ToggleAction("Y-Up", "Toggle Y-Up/Z-Up", AndroidIcons.GfxTrace.YUp) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myZUp;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myZUp = state;
        Presentation presentation = e.getPresentation();
        presentation.setIcon(myZUp ? AndroidIcons.GfxTrace.ZUp : AndroidIcons.GfxTrace.YUp);
        presentation.setText(myZUp ? "Z-Up" : "Y-Up");
        if (myGeometry != null) {
          myGeometry.setZUp(myZUp);
          updateViewer();
        }
      }
    });
    group.add(new ToggleAction("Triangle Winding", "Toggle triangle winding", AndroidIcons.GfxTrace.WindingCCW) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myViewer.getWinding() == Viewer.Winding.CW;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myViewer.setWinding(state ? Viewer.Winding.CW : Viewer.Winding.CCW);
        Presentation presentation = e.getPresentation();
        presentation.setIcon(state ? AndroidIcons.GfxTrace.WindingCW : AndroidIcons.GfxTrace.WindingCCW);
        if (myGeometry != null) {
          updateViewer();
        }
      }
    });

    group.add(new Separator());
    group.add(new DisplayModeAction("Shaded", "Display the goemetry with shading", AndroidIcons.GfxTrace.WireframeNone,
                                    Geometry.DisplayMode.TRIANGLES));
    group.add(new DisplayModeAction("Wireframe", "Display the geometry with wireframes", AndroidIcons.GfxTrace.WireframeAll,
                                    Geometry.DisplayMode.LINES));
    group.add(new DisplayModeAction("Point Cloud", "Display the geometry as a point cloud", AndroidIcons.GfxTrace.PointCloud,
                                    Geometry.DisplayMode.POINTS));

    group.add(new Separator());
    group.add(new NormalsAction("Original", "Original vertex normals", AndroidIcons.GfxTrace.Smooth, NormalsAction.ORIGINAL));
    group.add(new NormalsAction("Faceted", "Per-face normals", AndroidIcons.GfxTrace.Faceted, NormalsAction.FACETED));

    group.add(new Separator());
    group.add(new ToggleAction("Backface Culling", "Toggle culling of backfaces", AndroidIcons.GfxTrace.CullingDisabled) {
      @Override
      public boolean isSelected(AnActionEvent e) {
        return myViewer.getCulling() != Viewer.Culling.OFF;
      }

      @Override
      public void setSelected(AnActionEvent e, boolean state) {
        myViewer.setCulling(state ? Viewer.Culling.ON : Viewer.Culling.OFF);
        Presentation presentation = e.getPresentation();
        presentation.setIcon(state ? AndroidIcons.GfxTrace.CullingEnabled : AndroidIcons.GfxTrace.CullingDisabled);
        if (myGeometry != null) {
          updateViewer();
        }
      }
    });

    group.add(new Separator());
    group.add(new ShadingAction("Lit", "Lit shading", AndroidIcons.GfxTrace.Lit, Viewer.Shading.LIT));
    group.add(new ShadingAction("Flat", "Flat shading", AndroidIcons.GfxTrace.Flat, Viewer.Shading.FLAT));
    group.add(new ShadingAction("Normals", "Show face normals", AndroidIcons.GfxTrace.Normals, Viewer.Shading.NORMALS));

    return group;
  }

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {
  }

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
  }

  @Override
  public void onAtomSelected(AtomPath path) {
    CardLayout layout = (CardLayout)myPanel.getLayout();
    if (myEditor.getAtomStream().getSelectedAtom().isDrawCall()) {
      layout.show(myPanel, CARD_GEOMETRY);
      fetchMeshes(path);
    }
    else {
      layout.show(myPanel, CARD_EMPTY);
    }
  }

  private void fetchMeshes(AtomPath path) {
    myLoading.startLoading();

    ListenableFuture<Model> originalFuture = fetchModel(path.mesh(null));
    ListenableFuture<Model> facetedFuture = fetchModel(path.mesh(new MeshPathOptions().setFaceted(true)));

    Rpc.listen(Futures.allAsList(originalFuture, facetedFuture), LOG, new Rpc.Callback<List<Model>>() {
      @Override
      public void onFinish(Rpc.Result<List<Model>> results) throws RpcException, ExecutionException {
        try {
          List<Model> models = results.get();
          myOriginalModel = models.get(0);
          myFacetedModel = models.get(1);

          setModel((myOriginalModel != null) ? myOriginalModel : myFacetedModel);
        }
        finally {
          myLoading.stopLoading();
        }
      }
    });
  }

  private ListenableFuture<Model> fetchModel(MeshPath path) {
    ListenableFuture<Mesh> meshFuture = myEditor.getClient().get(path);
    return Futures.transform(meshFuture, new AsyncFunction<Mesh, Model>() {
      @Override
      public ListenableFuture<Model> apply(Mesh mesh) throws Exception {
        VertexBuffer vb = mesh.getVertexBuffer();

        ListenableFuture<VertexStreamData> positionsFuture = null;
        ListenableFuture<VertexStreamData> normalsFuture = null;

        for (VertexStream stream : vb.getStreams()) {
          SemanticType semantic = stream.getSemantic().getType();
          if (positionsFuture == null && semantic == SemanticType.Position) {
            positionsFuture = myEditor.getClient().get(stream.getData(), FMT_XYZ_F32);
          }
          else if (normalsFuture == null && semantic == SemanticType.Normal) {
            normalsFuture = myEditor.getClient().get(stream.getData(), FMT_XYZ_F32);
          }
        }

        if (positionsFuture == null || normalsFuture == null) {
          return Futures.immediateFuture(null); // Model doesn't have the data we need.
        }

        final int[] indices = mesh.getIndexBuffer().getIndices();
        final DrawPrimitive primitive = mesh.getDrawPrimitive();

        return Futures.transform(Futures.allAsList(positionsFuture, normalsFuture), new Function<List<VertexStreamData>, Model>() {
          @Override
          public Model apply(List<VertexStreamData> inputs) {
            float[] positions = inputs.get(0).getDataAndCast();
            float[] normals = inputs.get(1).getDataAndCast();
            return new Model(primitive, positions, normals, indices);
          }
        });
      }
    });
  }

  private void setModel(Model model) {
    myGeometry.setModel(model);
    updateRenderable();
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  private void updateRenderable() {
    // Repaint will happen below.
    myViewer.setRenderable(myGeometry.asRenderable(myDisplayMode));
    updateViewer();
  }

  private void updateViewer() {
    myCamera.setEmitter(myGeometry.getEmitter());
    myPanel.repaint();
    myCanvas.display();
  }

  private static class EmptyPanel extends JComponent {
    private final StatusText myEmptyText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return true;
      }
    };

    public EmptyPanel() {
      myEmptyText.setText(GfxTraceEditor.SELECT_DRAW_CALL);
      myEmptyText.attachTo(this);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      myEmptyText.paint(this, graphics);
    }
  }

  private class NormalsAction extends ToggleAction {
    private static final int ORIGINAL = 0;
    private static final int FACETED = 1;

    private int myTargetMode;

    public NormalsAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, int targetMode) {
      super(text, description, icon);
      myTargetMode = targetMode;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      Model model = myGeometry.getModel();
      return model != null && model == getModel();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(getModel() != null);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      setModel(getModel());
    }

    private Model getModel() {
      switch (myTargetMode) {
        case ORIGINAL:
          return myOriginalModel;
        case FACETED:
          return myFacetedModel;
        default:
          return null;
      }
    }
  }

  private class DisplayModeAction extends ToggleAction {
    private Geometry.DisplayMode myDisplayMode;

    public DisplayModeAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, Geometry.DisplayMode displayMode) {
      super(text, description, icon);
      myDisplayMode = displayMode;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return GeometryController.this.myDisplayMode == myDisplayMode;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      GeometryController.this.myDisplayMode = myDisplayMode;
      updateRenderable();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled((myGeometry != null)); // TODO:  && myGeometry.canRenderAs(myDisplayMode));
    }
  }

  private class ShadingAction extends ToggleAction {
    private Viewer.Shading myTargetShading;

    public ShadingAction(@Nullable String text, @Nullable String description, @Nullable Icon icon, Viewer.Shading targetShading) {
      super(text, description, icon);
      myTargetShading = targetShading;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myViewer.getShading() == myTargetShading;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myViewer.setShading(myTargetShading);
      if (myGeometry != null) {
        updateViewer();
      }
    }
  }
}
