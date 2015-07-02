/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.rendering.api.ViewType;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.android.tools.idea.rendering.multi.RenderPreviewManager;
import com.android.tools.idea.rendering.multi.RenderPreviewMode;
import com.google.common.base.Objects;
import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.TAG_ITEM;
import static com.android.tools.idea.rendering.RenderErrorPanel.SIZE_ERROR_PANEL_DYNAMICALLY;

/** A panel displaying a layoutlib render result as well as errors */
public class RenderedPanel extends JPanel implements Disposable {
  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 100;
  private static final boolean DEBUG_SHOW_VIEWS = false;

  protected RenderResult myRenderResult;
  protected RenderPreviewManager myPreviewManager;
  protected List<RenderedView> mySelectedViews;
  protected RenderContext myContext;

  private boolean myZoomToFit = true;
  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();
  private final JComponent myImagePanel = new JComponent() {
  };
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private MyProgressPanel myProgressPanel;
  private RenderErrorPanel myErrorPanel;
  private int myErrorPanelHeight = -1;

  public RenderedPanel(boolean installSelectionListeners) {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    updateBackgroundColor();
    myImagePanel.setBackground(null);

    MyImagePanelWrapper previewPanel = new MyImagePanelWrapper();
    if (installSelectionListeners) {
      previewPanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent mouseEvent) {
          if (myRenderResult == null) {
            return;
          }

          if (myRenderResult.getImage() != null) {
            // Convert to model coordinates
            int x1 = mouseEvent.getX();
            int y1 = mouseEvent.getY();
            x1 -= myImagePanel.getX();
            y1 -= myImagePanel.getY();

            Point p = fromScreenToModel(x1, y1);
            if (p == null) {
              return;
            }

            selectViewAt(p.x, p.y);
          }
        }

        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
          if (myRenderResult == null) {
            return;
          }

          if (mouseEvent.getClickCount() == 2) {
            // Double click: open in the UI editor
            switchToLayoutEditor();
          }
        }
      });
    }

    add(previewPanel);

    myErrorPanel = new RenderErrorPanel();
    myErrorPanel.setVisible(false);
    previewPanel.add(myErrorPanel, JLayeredPane.POPUP_LAYER);
    myProgressPanel = new MyProgressPanel();
    previewPanel.add(myProgressPanel, LAYER_PROGRESS);
  }

  public void setRenderContext(@Nullable RenderContext context) {
    myContext = context;
  }

  public Component getPaintComponent() {
    return myImagePanel;
  }

  private void switchToLayoutEditor() {
    if (myRenderResult != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          VirtualFile file = myRenderResult.getFile().getVirtualFile();
          if (file != null) {
            Project project = myRenderResult.getModule().getProject();
            FileEditorManager.getInstance(project).setSelectedEditor(file, AndroidDesignerEditorProvider.ANDROID_DESIGNER_ID);
          }
        }
      }, ModalityState.NON_MODAL);
    }
  }

  protected void selectViewAt(int x, int y) {
    RenderedView leaf = findLeaf(x, y, false);

    if (handleMenu(leaf)) {
      return;
    }

    while (leaf != null && leaf.tag == null) {
      leaf = leaf.getParent();
    }

    selectView(leaf);
  }

  private boolean handleMenu(@Nullable RenderedView leaf) {
    boolean showMenu = false;
    if (leaf != null) {
      ViewInfo view = leaf.view;
      if (view != null) {
        ViewType viewType = view.getViewType();
        if (viewType != ViewType.USER) {
          XmlFile xmlFile = myContext.getXmlFile();
          if (ResourceHelper.getFolderType(xmlFile) == ResourceFolderType.MENU) {
            // When rendering a menu file, don't hide menu when clicking outside of it
            showMenu = true;
          }
          if (viewType == ViewType.ACTION_BAR_OVERFLOW) {
            showMenu = !ActionBarHandler.isShowingMenu(myContext);
          } else if (ActionBarHandler.isShowingMenu(myContext)) {
            RenderedView v = leaf.getParent();
            while (v != null) {
              if (v.tag != null) {
                // A view *containing* a system view is the menu
                showMenu = true;
                if (TAG_ITEM.equals(v.tag.getName())) {
                  PsiFile file = v.tag.getContainingFile();
                  if (file != null && file != xmlFile) {
                    VirtualFile virtualFile = file.getVirtualFile();
                    if (virtualFile != null) {
                      Project project = file.getProject();
                      int offset = v.tag.getTextOffset();
                      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, offset);
                      FileEditorManager.getInstance(project).openEditor(descriptor, true);
                      return true;
                    }
                  }
                }
                break;
              }
              v = v.getParent();
            }
          }
        }
      }
    }

    ActionBarHandler.showMenu(showMenu, myContext, true);

    return false;
  }

  @Nullable
  protected RenderedView findLeaf(int x, int y, boolean requireTag) {
    RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
    assert hierarchy != null; // because image != null
    RenderedView leaf = hierarchy.findLeafAt(x, y);

    // If you've clicked on for example a list item, the view you clicked
    // on may not correspond to a tag, it could be a designtime preview item,
    // so search upwards for the nearest surrounding tag
    if (requireTag) {
      while (leaf != null && leaf.tag == null) {
        leaf = leaf.getParent();
      }
    }

    return leaf;
  }

  protected void selectView(@Nullable RenderedView leaf) {
  }

  /**
   * Computes the corresponding layoutlib point from a screen point (relative to the top left corner of the rendered image)
   */
  @Nullable
  public Point fromScreenToModel(int x, int y) {
    if (myRenderResult == null) {
      return null;
    }
    RenderedImage image = myRenderResult.getImage();
    if (image != null) {
      double zoomFactor = image.getScale();
      Rectangle imageBounds = image.getImageBounds();
      if (imageBounds != null) {
        x -= imageBounds.x;
        y -= imageBounds.y;
        double deviceFrameFactor = imageBounds.getWidth() / (double) image.getScaledWidth();
        zoomFactor *= deviceFrameFactor;
      }

      x /= zoomFactor;
      y /= zoomFactor;

      return new Point(x, y);
    }

    return null;
  }

  /**
   * Computes the corresponding layoutlib point from a screen point (relative to the top left corner of the rendered image)
   */
  @Nullable
  public Rectangle fromScreenToModel(int x, int y, int width, int height) {
    if (myRenderResult == null) {
      return null;
    }
    RenderedImage image = myRenderResult.getImage();
    if (image != null) {
      double zoomFactor = image.getScale();
      Rectangle imageBounds = image.getImageBounds();
      if (imageBounds != null) {
        x -= imageBounds.x;
        y -= imageBounds.y;
        double deviceFrameFactor = imageBounds.getWidth() / (double) image.getScaledWidth();
        zoomFactor *= deviceFrameFactor;
      }

      x /= zoomFactor;
      y /= zoomFactor;
      width /= zoomFactor;
      height /= zoomFactor;

      return new Rectangle(x, y, width, height);
    }

    return null;
  }

  /**
   * Computes the corresponding screen coordinates (relative to the top left corner of the rendered image)
   * for a layoutlib rectangle.
   */
  @Nullable
  public Rectangle fromModelToScreen(int x, int y, int width, int height) {
    if (myRenderResult == null) {
      return null;
    }
    RenderedImage image = myRenderResult.getImage();
    if (image != null) {
      double zoomFactor = image.getScale();
      Rectangle imageBounds = image.getImageBounds();
      if (imageBounds != null) {
        double deviceFrameFactor = imageBounds.getWidth() / (double) image.getScaledWidth();
        zoomFactor *= deviceFrameFactor;
      }

      x *= zoomFactor;
      y *= zoomFactor;
      width *= zoomFactor;
      height *= zoomFactor;

      if (imageBounds != null) {
        x += imageBounds.x;
        y += imageBounds.y;
      }

      return new Rectangle(x, y, width, height);
    }

    return null;
  }

  protected boolean paintRenderedImage(Component component, Graphics g, int px, int py) {
    if (myRenderResult == null) {
      return false;
    }
    RenderedImage image = myRenderResult.getImage();
    if (image != null) {
      image.paint(g, px, py);

      // Paint hierarchy
      if (DEBUG_SHOW_VIEWS) {
        paintViews(g, px, py);
      }

      List<RenderedView> selectedViews = mySelectedViews;
      if (selectedViews != null && !selectedViews.isEmpty() && !myErrorPanel.isVisible()) {
        Shape prevClip = g.getClip();
        Shape clip = null;
        Configuration configuration = myContext.getConfiguration();
        if (configuration != null) {
          Device device = configuration.getDevice();
          if (device != null && device.isScreenRound()) {
            Screen screen = device.getDefaultHardware().getScreen();
            int width = screen.getXDimension();
            int height = screen.getYDimension();
            Rectangle m = fromModelToScreen(0, 0, width, height);
            if (m != null) {
              clip = RenderedImage.getClip(device, m.x + px, m.y + py, m.width, m.height);
              if (clip != null) {
                g.setClip(clip);
              }
            }
          }
        }

        for (RenderedView selected : selectedViews) {
          Rectangle r = fromModelToScreen(selected.x, selected.y, selected.w, selected.h);
          if (r == null) {
            continue;
          }
          int x = r.x + px;
          int y = r.y + py;
          int w = r.width;
          int h = r.height;

          DesignerGraphics.drawFilledRect(DrawingStyle.SELECTION, g, x, y, w, h);
        }

        if (clip != null) {
          g.setClip(prevClip);
        }
      }

      return true;
    }
    return false;
  }

  private void paintViews(Graphics g, int px, int py) {
    if (DEBUG_SHOW_VIEWS) {
      Graphics2D g2d = (Graphics2D)g;
      Composite prev = g2d.getComposite();
      g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      if (hierarchy != null) {
        for (RenderedView view : hierarchy.getRoots()) {
          paintView(g, px, py, view);
        }
      }
      g2d.setComposite(prev);
    }
  }

  private void paintView(Graphics g, int px, int py, RenderedView view) {
    if (DEBUG_SHOW_VIEWS) {
      if (view.view == null || view.view.getViewType() == ViewType.USER) {
        return;
      }
      Rectangle bounds = view.getBounds();
      Rectangle r = fromModelToScreen(bounds.x, bounds.y, bounds.width, bounds.height);
      if (r == null) {
        return;
      }
      int x = r.x + px;
      int y = r.y + py;
      int w = Math.max(0, r.width - 1);
      int h = Math.max(0, r.height - 1);

      if (view.h <= 300) {
        //noinspection UseJBColor
        g.setColor(Color.RED);
        g.fillRect(x, y, w, h);
      }
      //noinspection UseJBColor
      g.setColor(Color.WHITE);
      g.drawRect(x, y, w, h);
      String className = view.view.getClassName();
      if (className != null) {
        className = className.substring(className.lastIndexOf('.') + 1);
        Shape clip = g.getClip();
        g.setClip(x, y, w, h);
        g.drawString(className, x, y + h);
        g.setClip(clip);
      }

      for (RenderedView child : view.getChildren()) {
        paintView(g, px, py, child);
      }
    }
  }

  public void setRenderResult(@NotNull final RenderResult renderResult) {
    double prevScale = myRenderResult != null && myRenderResult.getImage() != null ? myRenderResult.getImage().getScale() : 1;
    myRenderResult = renderResult;
    RenderedImage image = myRenderResult.getImage();
    if (image != null) {
      image.setDeviceFrameEnabled(myShowDeviceFrames && myRenderResult.getRenderTask() != null &&
                                  myRenderResult.getRenderTask().getRenderingMode() == SessionParams.RenderingMode.NORMAL &&
                                  myRenderResult.getRenderTask().getShowDecorations());
      if (myPreviewManager != null && RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
        Dimension fixedRenderSize = myPreviewManager.getFixedRenderSize();
        if (fixedRenderSize != null) {
          image.setMaxSize(fixedRenderSize.width, fixedRenderSize.height);
          image.setUseLargeShadows(false);
        }
      }
      image.setScale(prevScale);
    }
    mySelectedViews = null;

    RenderLogger logger = myRenderResult.getLogger();
    if (logger.hasProblems()) {
      if (!myErrorPanel.isVisible()) {
        myErrorPanelHeight = -1;
      }
      myErrorPanel.showErrors(myRenderResult);
      myErrorPanel.setVisible(true);
    } else {
      myErrorPanel.setVisible(false);
    }

    repaint();

    // Ensure that if we have a a preview mode enabled, it's shown
    if (myPreviewManager != null && myPreviewManager.hasPreviews()) {
      myPreviewManager.renderPreviews();
    }
  }

  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  public void setSelectedViews(@Nullable List<RenderedView> views) {
    if (!Objects.equal(views, mySelectedViews)) {
      mySelectedViews = views;
      repaint();
    }
  }

  public synchronized void registerIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);
      myProgressPanel.showProgressIcon();
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  protected void doRevalidate() {
    revalidate();
    updateImageSize();
    repaint();
  }

  public void update() {
    revalidate();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        doRevalidate();
      }
    });
  }

  public void updateImageSize() {
    if (myRenderResult == null) {
      return;
    }
    updateBackgroundColor();


    RenderedImage image = myRenderResult.getImage();
    if (image == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      if (myZoomToFit) {
        double availableHeight = getPanelHeight();
        double availableWidth = getPanelWidth();
        final int MIN_SIZE = 200;
        if (myPreviewManager != null && availableWidth > MIN_SIZE) {
          int previewWidth  = myPreviewManager.computePreviewWidth();
          availableWidth = Math.max(MIN_SIZE, availableWidth - previewWidth);
        }
        image.zoomToFit((int)availableWidth, (int)availableHeight, false, 0, 0);
      }

      myImagePanel.setSize(getScaledImageSize());
      repaint();
    }
  }

  private void updateBackgroundColor() {
    // Ensure the background color is right: light/dark when showing device chrome, gray when not
    boolean useGray = false;
    if (!myShowDeviceFrames) {
      useGray = true;
    } else if (myPreviewManager != null && RenderPreviewMode.getCurrent() != RenderPreviewMode.NONE) {
      // TODO: Don't do this if showing device frames or if we're not in Darcula!
      useGray = !RenderPreviewMode.getCurrent().showsDeviceFrames();
    } else {
      if (myRenderResult != null) {
        RenderedImage image = myRenderResult.getImage();
        if (image != null) {
          Boolean framed = image.isFramed();
          if (framed == null) {
            return;
          }
          useGray = !framed;
        }
      }
    }
    Color background = useGray ? DrawingStyle.DESIGNER_BACKGROUND_COLOR : JBColor.WHITE;
    if (getBackground() != background) {
      setBackground(background);
    }
  }

  protected double getPanelHeight() {
    return getSize().getHeight() - 5;
  }

  protected double getPanelWidth() {
    return getSize().getWidth() - 5;
  }

  public void zoomOut() {
    myZoomToFit = false;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomOut();
    }
    doRevalidate();
  }

  public void zoomIn() {
    myZoomToFit = false;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomIn();
    }
    doRevalidate();
  }

  public void zoomActual() {
    myZoomToFit = false;
    if (myRenderResult != null && myRenderResult.getImage() != null) {
      myRenderResult.getImage().zoomActual();
    }
    doRevalidate();
  }

  public void setZoomToFit(boolean zoomToFit) {
    myZoomToFit = zoomToFit;
    doRevalidate();
  }

  public boolean isZoomToFit() {
    return myZoomToFit;
  }

  @Override
  public void dispose() {
    if (myPreviewManager != null) {
      myPreviewManager.dispose();
      myPreviewManager = null;
    }
    myErrorPanel.dispose();
    myErrorPanel = null;
  }

  // RenderContext helpers

  @Nullable
  public Module getModule() {
    return myRenderResult != null ? myRenderResult.getModule() : null;
  }

  @Nullable
  public XmlFile getXmlFile() {
    return myRenderResult != null ? (XmlFile)myRenderResult.getFile() : null;
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return myRenderResult != null ? myRenderResult.getFile().getVirtualFile() : null;
  }

  public boolean hasAlphaChannel() {
    return myRenderResult.getImage() != null && !myRenderResult.getImage().hasAlphaChannel();
  }

  @NotNull
  public Component getComponent() {
    return this;
  }

  @NotNull
  public Dimension getFullImageSize() {
    if (myRenderResult != null) {
      RenderedImage scaledImage = myRenderResult.getImage();
      if (scaledImage != null) {
        return new Dimension(scaledImage.getOriginalWidth(), scaledImage.getOriginalHeight());
      }
    }

    return RenderContext.NO_SIZE;
  }

  @NotNull
  public Dimension getScaledImageSize() {
    if (myRenderResult != null) {
      RenderedImage scaledImage = myRenderResult.getImage();
      if (scaledImage != null) {
        return new Dimension(scaledImage.getScaledWidth(), scaledImage.getScaledHeight());
      }
    }

    return RenderContext.NO_SIZE;
  }

  public Component getRenderComponent() {
    return myImagePanel.getParent();
  }

  public void setPreviewManager(@Nullable RenderPreviewManager manager) {
    if (manager == myPreviewManager) {
      return;
    }
    Component renderComponent = getRenderComponent();
    if (myPreviewManager != null) {
      myPreviewManager.unregisterMouseListener(renderComponent);
      myPreviewManager.dispose();
    }
    myPreviewManager = manager;
    if (myPreviewManager != null) {
      myPreviewManager.registerMouseListener(renderComponent);
    }
  }

  @Nullable
  public RenderPreviewManager getPreviewManager(@Nullable RenderContext context, boolean createIfNecessary) {
    if (myPreviewManager == null && createIfNecessary && context != null) {
      setPreviewManager(new RenderPreviewManager(context));
    }

    return myPreviewManager;
  }

  public void setMaxSize(int width, int height) {
    RenderedImage scaledImage = myRenderResult.getImage();
    if (scaledImage != null) {
      scaledImage.setMaxSize(width, height);
      scaledImage.setUseLargeShadows(width <= 0);
      myImagePanel.revalidate();
    }
  }

  private boolean myShowDeviceFrames = true;

  public void setDeviceFramesEnabled(boolean on) {
    myShowDeviceFrames = on;
    if (myRenderResult != null) {
      RenderedImage image = myRenderResult.getImage();
      if (image != null) {
        image.setDeviceFrameEnabled(on);
      }
    }
  }

  /**
   * Layered pane which shows the rendered image, as well as (if applicable) an error message panel on top of the rendering
   * near the bottom
   */
  private class MyImagePanelWrapper extends JBLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
      setBackground(null);
      setOpaque(true);
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
      myProgressPanel.setBounds(0, 0, getWidth(), getHeight());

      if (myPreviewManager == null || !myPreviewManager.hasPreviews()) {
        centerComponents();
      } else {
        if (myRenderResult != null) {
          RenderedImage image = myRenderResult.getImage();
          if (image != null) {
            int fixedWidth = image.getMaxWidth();
            int fixedHeight = image.getMaxHeight();
            if (fixedWidth > 0) {
              myImagePanel.setLocation(Math.max(0, (fixedWidth - image.getScaledWidth()) / 2),
                                       2 + Math.max(0, (fixedHeight - image.getScaledHeight()) / 2));
              return;
            }
          }
        }

        myImagePanel.setLocation(0, 0);
      }
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;
      point.y = (bounds.height - myImagePanel.getHeight()) / 2;

      // If we're squeezing the image to fit, and there's a drop shadow showing
      // shift *some* space away from the tail portion of the drop shadow over to
      // the left to make the image look more balanced
      if (myRenderResult != null) {
        if (point.x <= 2) {
          RenderedImage image = myRenderResult.getImage();
          // If there's a drop shadow
          if (image != null) {
            if (image.hasDropShadow()) {
              point.x += ShadowPainter.SHADOW_SIZE / 3;
            }
          }
        }
        if (point.y <= 2) {
          RenderedImage image = myRenderResult.getImage();
          // If there's a drop shadow
          if (image != null) {
            if (image.hasDropShadow()) {
              point.y += ShadowPainter.SHADOW_SIZE / 3;
            }
          }
        }
      }
      myImagePanel.setLocation(point);
    }

    private void positionErrorPanel() {
      int height = getHeight();
      int width = getWidth();
      int size;
      if (SIZE_ERROR_PANEL_DYNAMICALLY) {
        if (myErrorPanelHeight == -1) {
          // Make the layout take up to 3/4ths of the height, and at least 1/4th, but
          // anywhere in between based on what the actual text requires
          size = height * 3 / 4;
          int preferredHeight = myErrorPanel.getPreferredHeight(width) + 8;
          if (preferredHeight < size) {
            size = Math.max(preferredHeight, Math.min(height / 4, size));
            myErrorPanelHeight = size;
          }
        } else {
          size = myErrorPanelHeight;
        }
      } else {
        size = height / 2;
      }

      myErrorPanel.setSize(width, size);
      myErrorPanel.setLocation(0, height - size);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      paintRenderedImage(this, graphics, myImagePanel.getX(), myImagePanel.getY());

      if (myPreviewManager != null) {
        myPreviewManager.paint((Graphics2D)graphics);
      }

      super.paintComponent(graphics);
    }

    @Override
    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }

  /**
   * Panel which displays the progress icon. The progress icon can either be a large icon in the
   * center, when there is no rendering showing, or a small icon in the upper right corner when there
   * is a rendering. This is necessary because even though the progress icon looks good on some
   * renderings, depending on the layout theme colors it is invisible in other cases.
   */
  private class MyProgressPanel extends JPanel {
    private AsyncProcessIcon mySmallProgressIcon;
    private AsyncProcessIcon myLargeProgressIcon;
    private boolean mySmall;
    private boolean myProgressVisible;

    private MyProgressPanel() {
      super(new BorderLayout());
      setOpaque(false);
    }

    /** The "small" icon mode isn't just for the icon size; it's for the layout position too; see {@link #doLayout} */
    private void setSmallIcon(boolean small) {
      if (small != mySmall) {
        if (myProgressVisible && getComponentCount() != 0) {
          AsyncProcessIcon oldIcon = getProgressIcon();
          oldIcon.suspend();
        }
        mySmall = true;
        removeAll();
        AsyncProcessIcon icon = getProgressIcon();
        add(icon, BorderLayout.CENTER);
        if (myProgressVisible) {
          icon.setVisible(true);
          icon.resume();
        }
      }
    }

    public void showProgressIcon() {
      if (!myProgressVisible) {
        setSmallIcon(myRenderResult != null && myRenderResult.getImage() != null);
        myProgressVisible = true;
        setVisible(true);
        AsyncProcessIcon icon = getProgressIcon();
        if (getComponentCount() == 0) { // First time: haven't added icon yet?
          add(getProgressIcon(), BorderLayout.CENTER);
        } else {
          icon.setVisible(true);
        }
        icon.resume();
      }
    }

    public void hideProgressIcon() {
      if (myProgressVisible) {
        myProgressVisible = false;
        setVisible(false);
        AsyncProcessIcon icon = getProgressIcon();
        icon.setVisible(false);
        icon.suspend();
      }
    }

    @Override
    public void doLayout() {
      super.doLayout();

      if (!myProgressVisible) {
        return;
      }

      // Place the progress icon in the center if there's no rendering, and in the
      // upper right corner if there's a rendering. The reason for this is that the icon color
      // will depend on whether we're in a light or dark IDE theme, and depending on the rendering
      // in the layout it will be invisible. For example, in Darcula the icon is white, and if the
      // layout is rendering a white screen, the progress is invisible.
      AsyncProcessIcon icon = getProgressIcon();
      Dimension size = icon.getPreferredSize();
      if (mySmall) {
        icon.setBounds(getWidth() - size.width - 1, 1, size.width, size.height);
      } else {
        icon.setBounds(getWidth() / 2 - size.width / 2, getHeight() / 2 - size.height / 2, size.width, size.height);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return getProgressIcon().getPreferredSize();
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon() {
      return getProgressIcon(mySmall);
    }

    @NotNull
    private AsyncProcessIcon getProgressIcon(boolean small) {
      if (small) {
        if (mySmallProgressIcon == null) {
          mySmallProgressIcon = new AsyncProcessIcon("Android layout rendering");
          Disposer.register(RenderedPanel.this, mySmallProgressIcon);
        }
        return mySmallProgressIcon;
      }
      else {
        if (myLargeProgressIcon == null) {
          myLargeProgressIcon = new AsyncProcessIcon.Big("Android layout rendering");
          Disposer.register(RenderedPanel.this, myLargeProgressIcon);
        }
        return myLargeProgressIcon;
      }
    }
  }
}
