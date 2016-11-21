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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.palette.NlPaletteDefinition;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class NlPreviewForm implements Disposable, CaretListener {
  private final NlPreviewManager myManager;
  private final DesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final MergingUpdateQueue myRenderingQueue =
    new MergingUpdateQueue("android.layout.preview.caret", 250/*ms*/, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD);
  private boolean myUseInteractiveSelector = true;
  private boolean myIgnoreListener;
  private RenderResult myRenderResult;
  private XmlFile myFile;
  private boolean isActive = true;
  private final NlActionsToolbar myActionsToolbar;

  /**
   * When {@link #deactivate()} is called, the file will be saved here and the preview will not be rendered anymore.
   * On {@link #activate()} the file will be restored to {@link #myFile} and the preview will be rendered again.
   */
  private XmlFile myInactiveFile;
  /**
   * Contains the file that is currently being loaded (it might take a while to get a preview rendered).
   * Once the file is loaded, myPendingFile will be null.
   */
  private Pending myPendingFile;
  private TextEditor myEditor;
  private CaretModel myCaretModel;

  public NlPreviewForm(NlPreviewManager manager) {
    myManager = manager;
    Project project = myManager.getProject();
    mySurface = new DesignSurface(project, true);
    Disposer.register(this, mySurface);
    mySurface.setCentered(true);
    mySurface.setScreenMode(DesignSurface.ScreenMode.SCREEN_ONLY, false);
    mySurface.addListener(new DesignSurfaceListener() {
      @Override
      public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
        assert surface == mySurface; // We're maintaining the listener per surface
        // Allow only one component
        NlComponent component = newSelection.size() == 1 ? newSelection.get(0) : null;
        selectComponent(component);
      }

      @Override
      public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
      }

      @Override
      public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
      }

      @Override
      public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
        return false;
      }
    });

    myRenderingQueue.setRestartTimerOnAdd(true);


    myActionsToolbar = new NlActionsToolbar(mySurface);
    JPanel contentPanel = new JPanel(new BorderLayout());
    contentPanel.add(myActionsToolbar.getToolbarComponent(), BorderLayout.NORTH);
    contentPanel.add(mySurface, BorderLayout.CENTER);

    myWorkBench = new WorkBench<>(project, "Preview", null);
    myWorkBench.init(contentPanel, mySurface, Collections.singletonList(
      new NlPaletteDefinition(project, Side.LEFT, Split.TOP, AutoHide.AUTO_HIDE)));
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;
      mySurface.setFileEditorDelegate(editor);

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(this);
        myCaretModel = null;
      }

      if (editor != null) {
        myCaretModel = myEditor.getEditor().getCaretModel();
        myCaretModel.addCaretListener(this);
      }
    }
  }

  private void selectComponent(@Nullable NlComponent component) {
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    if (myEditor != null && component != null && component.getTag().isValid() && myUseInteractiveSelector && !myIgnoreListener) {
      int offset = component.getTag().getTextOffset();
      if (offset != -1) {
        Editor editor = myEditor.getEditor();
        myIgnoreListener = true;
        try {
          screenView.getSelectionModel().setSelection(Collections.singletonList(component));
          editor.getCaretModel().moveToOffset(offset);
          editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        }
        finally {
          myIgnoreListener = false;
        }
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null && !myIgnoreListener && myUseInteractiveSelector) {
      ScreenView screenView = mySurface.getCurrentScreenView();
      if (screenView != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          List<NlComponent> views = screenView.getModel().findByOffset(offset);
          if (views == null || views.isEmpty()) {
            views = screenView.getModel().getComponents();
          }
          try {
            myIgnoreListener = true;
            SelectionModel selectionModel = screenView.getSelectionModel();
            selectionModel.setSelection(views);
            myRenderingQueue.queue(new Update("Preview update") {
              @Override
              public void run() {
                mySurface.repaint();
              }

              @Override
              public boolean canEat(Update update) {
                return true;
              }
            });
          }
          finally {
            myIgnoreListener = false;
          }
        }
      }
    }
  }

  @Nullable
  public XmlFile getFile() {
    if (myFile == null && myPendingFile != null) {
      return myPendingFile.file;
    }

    return myFile;
  }

  @NotNull
  public JComponent getComponent() {
    return myWorkBench;
  }

  @Override
  public void dispose() {
    deactivate();
    myInactiveFile = null;
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }

  private class Pending implements ModelListener, Runnable {
    public final XmlFile file;
    public final NlModel model;
    public boolean valid = true;

    public Pending(XmlFile file, NlModel model) {
      this.file = file;
      this.model = model;
      model.addListener(this);
      model.requestRender(); // on file switches, render as soon as possible; the delay is for edits
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      // This won't be called in the dispatch thread so, to avoid a 10ms delay in requestRender
      model.render();
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      // do nothing
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      model.removeListener(this);
      if (valid) {
        valid = false;
        ApplicationManager.getApplication().invokeLater(this, model.getProject().getDisposed());
      }
    }

    public void invalidate() {
      valid = false;
    }

    @Override
    public void run() {
      // This method applies the given pending update to the UI thread; this must be done from a read thread
      ApplicationManager.getApplication().assertIsDispatchThread();
      setActiveModel(model);
    }
  }

  public boolean setFile(@Nullable PsiFile file) {
    if (!isActive) {
      // The form is not active so we just save the file to show once activate() is called
      myInactiveFile = (XmlFile)file;

      if (file != null) {
        return false;
      }
    }

    if (myPendingFile != null) {
      if (file == myPendingFile.file) {
        return false;
      }
      myPendingFile.invalidate();
      // Set the model to null so the progressbar is displayed
      mySurface.setModel(null);
    }
    else if (file == myFile) {
      return false;
    }

    AndroidFacet facet = file instanceof XmlFile ? AndroidFacet.getInstance(file) : null;
    if (facet == null || file.getVirtualFile() == null) {
      myPendingFile = null;
      myFile = null;
      setActiveModel(null);
    }
    else {
      XmlFile xmlFile = (XmlFile)file;
      NlModel model = NlModel.create(mySurface, null, facet, xmlFile);
      myPendingFile = new Pending(xmlFile, model);
    }
    return true;
  }

  public void setActiveModel(@Nullable NlModel model) {
    myPendingFile = null;
    ScreenView currentScreenView = mySurface.getCurrentScreenView();
    if (currentScreenView != null) {
      currentScreenView.getModel().deactivate();
      Disposer.dispose(currentScreenView.getModel());
    }

    if (model == null) {
      setEditor(null);
      myWorkBench.setToolContext(null);
    }
    else {
      myFile = model.getFile();
      mySurface.setModel(model);
      if (!mySurface.isCanvasResizing() && mySurface.isZoomFitted()) {
        // If we are resizing, keep the zoom level constant
        // only if the zoom was previously set to FIT
        mySurface.zoomToFit();
      }
      setEditor(myManager.getActiveLayoutXmlEditor());
      model.activate();
      myWorkBench.setToolContext(mySurface);
      myWorkBench.setFileEditor(myEditor);
      myActionsToolbar.setModel(model);
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myRenderResult;
  }

  public void setRenderResult(@NotNull RenderResult renderResult) {
    myRenderResult = renderResult;
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  // ---- Implements CaretListener ----

  @Override
  public void caretPositionChanged(CaretEvent e) {
    if (!myIgnoreListener) {
      updateCaret();
      // TODO: implement
      //ActionBarHandler.showMenu(false, myContext, true);
    }
  }

  @Override
  public void caretAdded(CaretEvent e) {

  }

  @Override
  public void caretRemoved(CaretEvent e) {

  }

  /**
   * Re-enables updates for this preview form. See {@link #deactivate()}
   */
  public void activate() {
    if (isActive) {
      return;
    }

    isActive = true;
    if (myFile == null && myPendingFile == null) {
      setFile(myInactiveFile);
    }
    myInactiveFile = null;
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until {@link #activate()} is called.
   */
  public void deactivate() {
    if (!isActive) {
      return;
    }

    if (myFile != null) {
      myInactiveFile = myFile;
    }
    else {
      // The file might still be rendering
      myInactiveFile = myPendingFile != null ? myPendingFile.file : null;
    }
    setFile(null);
    isActive = false;
  }
}
