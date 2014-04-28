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

import com.android.tools.idea.rendering.RenderedPanel;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderedView;
import com.android.tools.idea.rendering.RenderedViewHierarchy;
import com.google.common.base.Objects;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretAdapter;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    }
  };

  public AndroidLayoutPreviewPanel() {
    super(true);
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
  protected void selectView(@Nullable RenderedView leaf) {
    if (myEditor != null && leaf != null && leaf.tag != null) {
      int offset = leaf.tag.getTextOffset();
      if (offset != -1) {
        Editor editor = myEditor.getEditor();
        editor.getCaretModel().moveToOffset(offset);
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null) {
      RenderedViewHierarchy hierarchy = myRenderResult.getHierarchy();
      if (hierarchy != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          List<RenderedView> views = hierarchy.findByOffset(offset);
          if (views != null && views.size() == 1 && views.get(0).isRoot()) {
            views = null;
          }
          if (!Objects.equal(views, mySelectedViews)) {
            mySelectedViews = views;
            repaint();
          }
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
}
