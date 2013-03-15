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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedView;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.android.tools.idea.rendering.ScalableImage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends JPanel implements Disposable {
  public static final Gray DESIGNER_BACKGROUND_COLOR = Gray._150;
  public static final Color SELECTION_BORDER_COLOR = new Color(0x00, 0x99, 0xFF, 255);
  public static final Color SELECTION_FILL_COLOR = new Color(0x00, 0x99, 0xFF, 32);

  private FixableIssueMessage myErrorMessage;
  private List<FixableIssueMessage> myWarnMessages;

  @NotNull
  private RenderResult myRenderResult = RenderResult.NONE;

  private final JPanel myMessagesPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
  private final JPanel myTitlePanel;
  private boolean myZoomToFit = true;

  private final List<ProgressIndicator> myProgressIndicators = new ArrayList<ProgressIndicator>();
  private boolean myProgressVisible = false;
  private boolean myShowWarnings = false;

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
  private final JBLabel myFileNameLabel = new JBLabel();
  private TextEditor myEditor;
  private RenderedView mySelectedView;
  private CaretModel myCaretModel;
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

    myMessagesPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 5, 0, 5));
    myMessagesPanel.setOpaque(false);
    add(myMessagesPanel);

    add(new MyImagePanelWrapper());
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
      if (selected != null) {
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

// TODO: Ensure that I keep the most recent render on failure!

    if (myRenderResult.getImage() != null) {
      myRenderResult.getImage().setScale(prevScale);
    }

    mySelectedView = null;
    if (renderResult.getFile() != null) {
      myFileNameLabel.setText(renderResult.getFile().getName());
    }

    List<FixableIssueMessage> errorMessages = myRenderResult.getLogger().getErrorMessages();
    myErrorMessage = errorMessages != null && !errorMessages.isEmpty() ? errorMessages.get(0) : null;
    myWarnMessages = myRenderResult.getLogger().getWarningMessages();

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
    // TODO: Add a configuration listener which does auto zoom only when necessary
    updateImageSize();
    repaint();
  }

  public void update() {
    myImagePanel.setVisible(true);
    myMessagesPanel.removeAll();

    if (myErrorMessage != null) {
      showMessage(myErrorMessage, Messages.getErrorIcon(), myMessagesPanel);
    }
    if (myWarnMessages != null && myWarnMessages.size() > 0) {
      final HyperlinkLabel showHideWarnsLabel = new HyperlinkLabel();
      showHideWarnsLabel.setOpaque(false);
      String warningCount = myWarnMessages.size() + " warning" + (myWarnMessages.size() != 1 ? "s" : "");
      final String showMessage = "Show " + warningCount;
      final String hideMessage = "Hide " + warningCount;
      showHideWarnsLabel.setHyperlinkText("", myShowWarnings ? hideMessage : showMessage, "");

      final JPanel warningsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      warningsPanel.setOpaque(false);

      showHideWarnsLabel.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            myShowWarnings = !warningsPanel.isVisible();
            warningsPanel.setVisible(myShowWarnings);
            showHideWarnsLabel.setHyperlinkText("", myShowWarnings ? hideMessage : showMessage, "");
          }
        }
      });
      final JPanel wrapper = new JPanel(new BorderLayout());
      wrapper.setOpaque(false);
      wrapper.add(showHideWarnsLabel);
      wrapper.setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 5, 0));
      myMessagesPanel.add(wrapper);

      for (FixableIssueMessage warnMessage : myWarnMessages) {
        showMessage(warnMessage, Messages.getWarningIcon(), warningsPanel);
      }
      warningsPanel.setVisible(myShowWarnings);
      myMessagesPanel.add(warningsPanel);
    }
    revalidate();

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        doRevalidate();
      }
    });
  }

  private static void showMessage(final FixableIssueMessage message, Icon icon, JPanel panel) {
    if (message.myLinkText.length() > 0 || message.myAfterLinkText.length() > 0) {
      final HyperlinkLabel warnLabel = new HyperlinkLabel();
      warnLabel.setOpaque(false);
      warnLabel.setHyperlinkText(message.myBeforeLinkText,
                                 message.myLinkText,
                                 message.myAfterLinkText);
      warnLabel.setIcon(icon);

      warnLabel.addHyperlinkListener(new HyperlinkListener() {
        public void hyperlinkUpdate(final HyperlinkEvent e) {
          final Runnable quickFix = message.myQuickFix;
          if (quickFix != null && e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            quickFix.run();
          }
        }
      });
      panel.add(warnLabel);
    }
    else {
      final JBLabel warnLabel = new JBLabel();

      if (message.myAdditionalFixes.size() == 0 && message.myTips.size() == 0) {
        warnLabel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 10, 0));
      }
      warnLabel.setOpaque(false);
      warnLabel.setText("<html><body>" + message.myBeforeLinkText.replace("\n", "<br>") + "</body></html>");
      warnLabel.setIcon(icon);
      panel.add(warnLabel);
    }
    if (message.myAdditionalFixes.size() > 0 || message.myTips.size() > 0) {
      final JPanel fixesAndTipsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
      fixesAndTipsPanel.setBorder(IdeBorderFactory.createEmptyBorder(0, 0, 10, 0));
      fixesAndTipsPanel.setOpaque(false);
      fixesAndTipsPanel.add(Box.createHorizontalStrut(icon.getIconWidth()));

      final JPanel fixesAndTipsRight = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false));
      fixesAndTipsRight.setOpaque(false);

      final JPanel fixesPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
      fixesPanel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 0, 0));
      fixesPanel.setOpaque(false);

      for (Pair<String, Runnable> pair : message.myAdditionalFixes) {
        final HyperlinkLabel fixLabel = new HyperlinkLabel();
        fixLabel.setOpaque(false);
        fixLabel.setHyperlinkText(pair.getFirst());
        final Runnable fix = pair.getSecond();

        fixLabel.addHyperlinkListener(new HyperlinkListener() {
          @Override
          public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
              fix.run();
            }
          }
        });
        fixesPanel.add(fixLabel);
      }
      fixesAndTipsRight.add(fixesPanel);

      final JPanel tipsPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 5, 0, true, false));
      tipsPanel.setOpaque(false);
      tipsPanel.setBorder(IdeBorderFactory.createEmptyBorder(3, 0, 0, 0));

      for (String tip : message.myTips) {
        tipsPanel.add(new JBLabel(tip));
      }
      fixesAndTipsRight.add(tipsPanel);

      fixesAndTipsPanel.add(fixesAndTipsRight);
      panel.add(fixesAndTipsPanel);
    }
  }

  void updateImageSize() {
    ScalableImage image = myRenderResult.getImage();
    if (image == null) {
      myImagePanel.setSize(0, 0);
    }
    else {
      if (myZoomToFit) {
        double availableHeight = getPanelHeight() - myMessagesPanel.getSize().getHeight() - myTitlePanel.getSize().getHeight();
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
  }

  private class MyImagePanelWrapper extends JBLayeredPane {
    public MyImagePanelWrapper() {
      add(myImagePanel);
      setBackground(null);
    }

    private void centerComponents() {
      Rectangle bounds = getBounds();
      Point point = myImagePanel.getLocation();
      point.x = (bounds.width - myImagePanel.getWidth()) / 2;
      myImagePanel.setLocation(point);
    }

    public void invalidate() {
      centerComponents();
      super.invalidate();
    }

    public Dimension getPreferredSize() {
      return myImagePanel.getSize();
    }
  }
}
