/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.uipreview;

import com.android.tools.idea.rendering.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.rendering.RenderErrorPanel.SIZE_ERROR_PANEL_DYNAMICALLY;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel implements Disposable {
  public static final Gray DESIGNER_BACKGROUND_COLOR = Gray._150;
  public static final Color SELECTION_BORDER_COLOR = new Color(0x00, 0x99, 0xFF, 255);
  public static final Color SELECTION_FILL_COLOR = new Color(0x00, 0x99, 0xFF, 32);

  @NotNull
  private RenderResult myRenderResult = RenderResult.NONE;

  private final JPanel myTitlePanel;
  private boolean myZoomToFit = true;

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();
  private boolean myProgressVisible = false;

  private final JComponent myImagePanel = new JPanel() {
    @Override
    public void paintComponent(Graphics g) {
      paintRenderedImage(g);
    }
  };

  private AsyncProcessIcon myProgressIcon;
  @NonNls private static final String PROGRESS_ICON_CARD_NAME = "Progress";
  @NonNls private static final String EMPTY_CARD_NAME = "Empty";
  private JPanel myProgressIconWrapper = new JPanel();
  private final JLabel myFileNameLabel = new JLabel();
  private TextEditor myEditor;
  private RenderedView mySelectedView;
  private CaretModel myCaretModel;
  private RenderErrorPanel myErrorPanel;
  private int myErrorPanelHeight = -1;
  private CaretListener myCaretListener = new CaretListener() {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      updateCaret();
    }
  };

  public AndroidLayoutPreviewPanel() {
    super(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true));
    setBackground(DESIGNER_BACKGROUND_COLOR);
    myImagePanel.setBackground(null);

    myImagePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        selectViewAt(mouseEvent.getX(), mouseEvent.getY());
      }

      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2) {
          // Double click: open in the UI editor
          switchtoLayoutEditor();
        }
      }
    });

    myFileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myFileNameLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
    // We're using a hardcoded color here rather than say a JBLabel, since this
    // label is sitting on top of the preview gray background, which is the same
    // in all themes
    myFileNameLabel.setForeground(Color.BLACK);

    final JPanel progressPanel = new JPanel();
    progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.X_AXIS));
    myProgressIcon = new AsyncProcessIcon("Android layout rendering");
    myProgressIconWrapper.setLayout(new CardLayout());
    myProgressIconWrapper.add(PROGRESS_ICON_CARD_NAME, myProgressIcon);
    myProgressIconWrapper.add(EMPTY_CARD_NAME, new JBLabel(" "));
    myProgressIconWrapper.setOpaque(false);

    Disposer.register(this, myProgressIcon);
    progressPanel.add(myProgressIconWrapper);
    progressPanel.add(new JBLabel(" "));
    progressPanel.setOpaque(false);

    myTitlePanel = new JPanel(new BorderLayout());
    myTitlePanel.setOpaque(false);
    myTitlePanel.add(myFileNameLabel, BorderLayout.CENTER);
    myTitlePanel.add(progressPanel, BorderLayout.EAST);

    ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);

    add(myTitlePanel);

    MyImagePanelWrapper previewPanel = new MyImagePanelWrapper();
    add(previewPanel);

    myErrorPanel = new RenderErrorPanel();
    myErrorPanel.setVisible(false);
    previewPanel.add(myErrorPanel, JLayeredPane.POPUP_LAYER);
  }

  private void switchtoLayoutEditor() {
    /* TODO: Find out how to implement this correctly
    if (myEditor != null && myRenderResult.getFile() != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          VirtualFile file = myRenderResult.getFile().getVirtualFile();
          if (file != null) {
            Project project = myEditor.getEditor().getProject();
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
            List<FileEditor> editors = FileEditorManager.getInstance(project).openEditor(descriptor, true);

            for (FileEditor editor : editors) {
              // TODO: When we merge the layout editor code into this plugin we
              // can avoid stringly typed expressions like this and reference
              // the android designer classes directly:
              if (editor.getClass().getSimpleName().contains("Designer")) {
                editor.getComponent().getParent().getParent();
                IdeFocusManager.getInstance(project).requestFocus(editor.getComponent(), true);
              }
            }
          }
        }
      }, ModalityState.NON_MODAL);
    }
    */
  }

  private void selectViewAt(int x1, int y1) {
    if (myEditor != null && myRenderResult.getImage() != null) {
      double zoomFactor = myRenderResult.getImage().getScale();
      int x = (int)(x1 / zoomFactor);
      int y = (int)(y1 / zoomFactor);
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      assert hierarchy != null; // because image != null
      RenderedView leaf = hierarchy.findLeafAt(x, y);
      if (leaf != null && leaf.tag != null) {
        int offset = leaf.tag.getTextOffset();
        if (offset != -1) {
          myEditor.getEditor().getCaretModel().moveToOffset(offset);
        }
      }
    }
  }

  private void paintRenderedImage(Graphics g) {
    ScalableImage image = myRenderResult.getImage();
    if (image != null) {
      image.paint(g);

      // TODO: Use layout editor's static feedback rendering
      RenderedView selected = mySelectedView;
      if (selected != null && !myErrorPanel.isVisible()) {
        double zoomFactor = image.getScale();
        int x = (int)(selected.x * zoomFactor);
        int y = (int)(selected.y * zoomFactor);
        int w = (int)(selected.w * zoomFactor);
        int h = (int)(selected.h * zoomFactor);

        g.setColor(SELECTION_FILL_COLOR);
        g.fillRect(x, y, w, h);

        g.setColor(SELECTION_BORDER_COLOR);
        x -= 1;
        y -= 1;
        w += 1; // +1 rather than +2: drawRect already includes end point whereas fillRect does not
        h += 1;
        if (x < 0) {
          w -= x;
          x = 0;
        }
        if (y < 0) {
          h -= y;
          h = 0;
        }
        g.drawRect(x, y, w, h);
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null) {
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      if (hierarchy != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          RenderedView view = hierarchy.findByOffset(offset);
          if (view != null && view.isRoot()) {
            view = null;
          }
          if (view != mySelectedView) {
            mySelectedView = view;
            repaint();
          }
        }
      }
    }
  }

  public void setRenderResult(@NotNull final RenderResult renderResult, @Nullable final TextEditor editor) {
    double prevScale = myRenderResult.getImage() != null ? myRenderResult.getImage().getScale() : 1;
    myRenderResult = renderResult;
    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().setScale(prevScale);
    }

    mySelectedView = null;
    if (renderResult.getFile() != null) {
      myFileNameLabel.setText(renderResult.getFile().getName());
    }

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

    setEditor(editor);
    updateCaret();
    doRevalidate();
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(myCaretListener);
        myCaretModel = null;
      }
      if (editor != null)
      myCaretModel = myEditor.getEditor().getCaretModel();
      if (myCaretModel != null) {
        myCaretModel.addCaretListener(myCaretListener);
      }
    }
  }

  public synchronized void registerIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.add(indicator);

      if (!myProgressVisible) {
        myProgressVisible = true;
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, PROGRESS_ICON_CARD_NAME);
        myProgressIcon.setVisible(true);
        myProgressIcon.resume();
      }
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.size() == 0 && myProgressVisible) {
        myProgressVisible = false;
        myProgressIcon.suspend();
        ((CardLayout)myProgressIconWrapper.getLayout()).show(myProgressIconWrapper, EMPTY_CARD_NAME);
        myProgressIcon.setVisible(false);
      }
    }
  }

  private void doRevalidate() {
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

  void updateImageSize() {
    ScalableImage image = myRenderResult.getImage();
    if (image == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      if (myZoomToFit) {
        double availableHeight = getPanelHeight() - myTitlePanel.getSize().getHeight();
        double availableWidth = getPanelWidth();
        image.zoomToFit((int)availableWidth, (int)availableHeight, false, 0, 0);
      }

      myImagePanel.setSize(image.getRequiredSize());
    }
  }

  private double getPanelHeight() {
    return getParent().getParent().getSize().getHeight() - 5;
  }

  private double getPanelWidth() {
    return getParent().getParent().getSize().getWidth() - 5;
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
    if (myRenderResult.getImage() != null) {
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
    myErrorPanel.dispose();
    myErrorPanel = null;
  }

  /**
   * Layered pane which shows the rendered image, as well as (if applicable) an error message panel on top of the rendering
   * near the bottom
   */
  private class MyImagePanelWrapper extends JBLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
      setBackground(null);
      setOpaque(false);
    }

    @Override
    public void revalidate() {
      super.revalidate();
    }

    @Override
    public void doLayout() {
      super.doLayout();
      positionErrorPanel();
      centerComponents();
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;

      // If we're squeezing the image to fit, and there's a drop shadow showing
      // shift *some* space away from the tail portion of the drop shadow over to
      // the left to make the image look more balanced
      if (point.x <= 2) {
        ScalableImage image = myRenderResult.getImage();
        // If there's a drop shadow
        if (image != null) {
          if (image.getShowDropShadow()) {
            point.x += ShadowPainter.SHADOW_SIZE / 3;
          }
        }
      }
      if (point.y <= 2) {
        ScalableImage image = myRenderResult.getImage();
        // If there's a drop shadow
        if (image != null) {
          if (image.getShowDropShadow()) {
            point.y += ShadowPainter.SHADOW_SIZE / 3;
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
    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }
}
