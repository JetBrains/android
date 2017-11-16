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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.project.AndroidProjectInfo;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeListener;

public class StringResourceEditor extends UserDataHolderBase implements FileEditor {

  public static final Icon ICON = AndroidIcons.Globe;
  public static final String NAME = "String Resource Editor";
  private static final String TOOL_WINDOW_ID = "StringPreview";

  private StringResourceViewPanel myPanel;

  private final PreviewFeature myPreviewFeature;

  StringResourceEditor(@NotNull StringsVirtualFile file) {
    AndroidFacet facet = file.getFacet();
    // Post startup activities (such as when reopening last open editors) are run from a background thread
    UIUtil.invokeAndWaitIfNeeded(() -> myPanel = new StringResourceViewPanel(facet, this));

    myPreviewFeature = new PreviewFeature(this, facet.getModule());

    myPanel.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent event) {
        Document document = event.getDocument();

        try {
          myPreviewFeature.setText(document.getText(0, document.getLength()));
        }
        catch (BadLocationException exception) {
          myPreviewFeature.setText("");
        }
      }
    });

    myPanel.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        myPreviewFeature.setText(((JTextComponent)event.getSource()).getText());
      }
    });
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel.getLoadingPanel();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return NAME;
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    myPreviewFeature.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myPreviewFeature.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public String toString() {
    return "StringResourceEditor " + myPanel.getFacet() + " " + System.identityHashCode(this);
  }

  static class PreviewFeature extends SelectedEditorFeature {
    private final Module myModule;
    private StringPreview myPreview;
    private String myPreviewString;

    public PreviewFeature(@NotNull FileEditor editor, @NotNull Module module) {
      super(editor, module.getProject());
      myModule = module;
    }

    @Override
    public boolean isReady() {
      return !DumbService.getInstance(myModule.getProject()).isDumb() &&
             !AndroidProjectInfo.getInstance(myModule.getProject()).requiredAndroidModelMissing() &&
             ToolWindowManager.getInstance(myModule.getProject()).getToolWindow(TOOL_WINDOW_ID) == null;
    }

    @Override
    public void open() {
      myPreview = new StringPreview(myModule);
      if (myPreviewString != null) {
        myPreview.setText(myPreviewString);
      }

      ToolWindow toolWindow = ToolWindowManager.getInstance(myModule.getProject())
        .registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, myModule.getProject(), true);
      toolWindow.setIcon(AndroidIcons.AndroidPreview);
      toolWindow.setStripeTitle("Preview");
      toolWindow.getContentManager().addContent(ContentFactory.SERVICE.getInstance().createContent(myPreview.getComponent(), "", false));
    }

    @Override
    public void close() {
      ToolWindowManager.getInstance(myModule.getProject()).unregisterToolWindow(TOOL_WINDOW_ID);
      myPreview = null;
    }

    public void setText(@NotNull String text) {
      myPreviewString = text;
      if (myPreview != null) {
        myPreview.setText(text);
      }
    }
  }
}
