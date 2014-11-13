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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.configurations.OverlayContainer;
import com.android.tools.idea.rendering.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.AndroidPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLayoutPreviewPanel extends RenderedPanel {
  private TextEditor myEditor;
  private CaretModel myCaretModel;
  private CaretListener myCaretListener = new CaretAdapter() {
    @Override
    public void caretPositionChanged(CaretEvent e) {
      updateCaret();
      if (!myIgnoreListener) {
        ActionBarHandler.showMenu(false, myContext, true);
      }
    }
  };
  private OverlayContainer myOverlayContainer;
  private boolean myIgnoreListener;
  private boolean myUseInteractiveSelector = true;

  public AndroidLayoutPreviewPanel() {
    super(true);
  }

  public void installHover(final HoverOverlay overlay) {
    Container parent = getPaintComponent().getParent();
    parent.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent mouseEvent) {
        int x1 = mouseEvent.getX();
        int y1 = mouseEvent.getY();

        Component paintComponent = getPaintComponent();
        x1 -= paintComponent.getX();
        y1 -= paintComponent.getY();

        RenderedView leaf = null;
        Point p = fromScreenToModel(x1, y1);
        if (p != null) {
          leaf = findLeaf(p.x, p.y, true);
        }
        if (overlay.setHoveredView(leaf)) {
          repaint();
        }
      }
    });
    parent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseExited(MouseEvent mouseEvent) {
        if (overlay.setHoveredView(null)) {
          repaint();
        }
      }
    });
  }

  @Override
  protected double getPanelHeight() {
    return getParent().getParent().getSize().getHeight() - 5;
  }

  @Override
  protected double getPanelWidth() {
    return getParent().getParent().getSize().getWidth() - 5;
  }

  @Override
  protected boolean paintRenderedImage(Component component, Graphics g, int px, int py) {
    boolean paintedImage = super.paintRenderedImage(component, g, px, py);
    if (paintedImage) {
      Overlay.paintOverlays(myOverlayContainer, component, g, px, py);
    }
    return paintedImage;
  }

  @VisibleForTesting
  public void paintOverlays(Graphics g) {
    assert AndroidPlugin.isGuiTestingMode() || ApplicationManager.getApplication().isUnitTestMode();
    Overlay.paintOverlays(myOverlayContainer, getPaintComponent().getParent(), g, 0, 0);
  }

  @Override
  protected void selectView(@Nullable RenderedView leaf) {
    if (myEditor != null && leaf != null && leaf.tag != null && myUseInteractiveSelector) {
      int offset = leaf.tag.getTextOffset();
      if (offset != -1) {
        Editor editor = myEditor.getEditor();

        myIgnoreListener = true;
        try {
          if (leaf != null) {
            setSelectedViews(Collections.singletonList(leaf));
          }
          editor.getCaretModel().moveToOffset(offset);
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        } finally {
          myIgnoreListener = false;
        }
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null && !myIgnoreListener && myUseInteractiveSelector) {
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      if (hierarchy != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          List<RenderedView> views = hierarchy.findByOffset(offset);
          if (views != null && views.size() == 1 && views.get(0).isRoot()) {
            views = null;
          }
          setSelectedViews(views);
        }
      }
    }
  }

  public void setRenderResult(@NotNull final RenderResult renderResult, @Nullable final TextEditor editor) {
    super.setRenderResult(renderResult);

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

      if (editor != null) {
        myCaretModel = myEditor.getEditor().getCaretModel();
        myCaretModel.addCaretListener(myCaretListener);
      }
    }
  }

  public void setOverlayContainer(OverlayContainer overlayContainer) {
    myOverlayContainer = overlayContainer;
  }

  public boolean isSelected(@NotNull XmlTag tag) {
    if (mySelectedViews != null) {
      for (RenderedView view : mySelectedViews) {
        if (view.tag == tag) {
          return true;
        }
      }
    }
    return false;
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }
}
