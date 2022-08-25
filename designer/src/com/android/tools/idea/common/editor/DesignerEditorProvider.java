/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.editor;

import static com.android.tools.idea.common.model.NlModel.DELAY_AFTER_TYPING_MS;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.common.type.DesignerTypeRegistrar;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.text.QuickDefinitionProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider that accepts {@link XmlFile}s whose type belongs to {@link #myAcceptedTypes}.Subclasses are responsible for specifying the types
 * accepted, creating the editor using {@link #createEditor(Project, VirtualFile)}, and specifying their ID via {@link #getEditorTypeId()}.
 * This parent class in turn is responsible for registering the accepted types against {@link DesignerTypeRegistrar}.
 */
public abstract class DesignerEditorProvider implements FileEditorProvider, QuickDefinitionProvider, DumbAware {

  @NotNull
  private final List<DesignerEditorFileType> myAcceptedTypes;

  protected DesignerEditorProvider(@NotNull List<DesignerEditorFileType> acceptedTypes) {
    myAcceptedTypes = acceptedTypes;
    myAcceptedTypes.forEach(DesignerTypeRegistrar.INSTANCE::register);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(project, virtualFile);
    if (psiFile instanceof XmlFile) {
      XmlFile xmlFile = (XmlFile) psiFile;
      return myAcceptedTypes.stream().anyMatch(type -> type.isResourceTypeOf(xmlFile));
    }
    return false;
  }

  @NotNull
  @Override
  public final FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    DesignerEditor designEditor = createDesignEditor(project, file);
    DesignerEditorPanel editorPanel = designEditor.getComponent();
    TextEditor textEditor = (TextEditor)TextEditorProvider.getInstance().createEditor(project, file);
    addCaretListener(textEditor, designEditor);
    editorPanel.getSurface().setFileEditorDelegate(textEditor);
    DesignToolsSplitEditor splitEditor = new DesignToolsSplitEditor(textEditor, designEditor, project);
    editorPanel.getWorkBench().setFileEditor(splitEditor);
    DataManager.registerDataProvider(editorPanel, splitEditor);
    return splitEditor;
  }

  private void addCaretListener(@NotNull TextEditor editor, @NotNull DesignerEditor designEditor) {
    CaretModel caretModel = editor.getEditor().getCaretModel();
    MergingUpdateQueue updateQueue = new MergingUpdateQueue("split.editor.preview.edit", DELAY_AFTER_TYPING_MS,
                                                            true, null, designEditor, null, SWING_THREAD);
    updateQueue.setRestartTimerOnAdd(true);
    CaretListener caretListener = new CaretListener() {
      @Override
      public void caretAdded(@NotNull CaretEvent event) {
        caretPositionChanged(event);
      }

      @Override
      public void caretPositionChanged(@NotNull CaretEvent event) {
        DesignSurface<?> surface = designEditor.getComponent().getSurface();
        SceneView sceneView = surface.getFocusedSceneView();
        int offset = caretModel.getOffset();
        if (sceneView == null || offset == -1) {
          return;
        }

        NlModel model = sceneView.getSceneManager().getModel();
        ImmutableList<NlComponent> views = model.findByOffset(offset);
        if (views.isEmpty()) {
          views = model.getComponents();
        }
        handleCaretChanged(sceneView, views);
        updateQueue.queue(new Update("Design editor update") {
          @Override
          public void run() {
            surface.repaint();
          }

          @Override
          public boolean canEat(Update update) {
            return true;
          }
        });
      }
    };
    caretModel.addCaretListener(caretListener);
    // If the editor is just opening the SceneView may not be set yet. Register a listener so we get updated once we can get the model.
    designEditor.getComponent().getSurface().addListener(new DesignSurfaceListener() {
      @Override
      @UiThread
      public void modelChanged(@NotNull DesignSurface<?> surface,
                               @Nullable NlModel model) {
        surface.removeListener(this);
        CaretModel caretModel = editor.getEditor().getCaretModel();
        caretListener.caretPositionChanged(
          new CaretEvent(caretModel.getCurrentCaret(), caretModel.getLogicalPosition(), caretModel.getLogicalPosition()));
      }
    });
  }

  protected abstract void handleCaretChanged(@NotNull SceneView sceneView,
                                             @NotNull ImmutableList<NlComponent> views);

  @NotNull
  public abstract DesignerEditor createDesignEditor(@NotNull Project project, @NotNull VirtualFile file);

  @NotNull
  @Override
  public abstract String getEditorTypeId();

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    // We hide the default one since the split editor already includes the text-only view.
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
