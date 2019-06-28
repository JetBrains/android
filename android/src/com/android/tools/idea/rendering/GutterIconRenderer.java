// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.rendering;

import com.android.ide.common.resources.ResourceResolver;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class GutterIconRenderer extends com.intellij.openapi.editor.markup.GutterIconRenderer implements DumbAware {
  private final PsiElement myElement;
  private final VirtualFile myFile;
  private final ResourceResolver myResourceResolver;

  public GutterIconRenderer(ResourceResolver resourceResolver, @NotNull PsiElement element, @NotNull VirtualFile file) {
    myResourceResolver = resourceResolver;
    myElement = element;
    myFile = file;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    Icon icon = GutterIconCache.getInstance().getIcon(myFile.getPath(), myResourceResolver);

    if (icon != null) {
      return icon;
    }

    return AllIcons.General.Error;
  }

  @Override
  public AnAction getClickAction() {
    return new GutterIconClickAction(myFile, myResourceResolver);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GutterIconRenderer that = (GutterIconRenderer)o;

    if (!myElement.equals(that.myElement)) return false;
    if (!myFile.equals(that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myElement.hashCode();
    result = 31 * result + myFile.hashCode();
    return result;
  }

  private static class GutterIconClickAction extends AnAction {
    private final static int PREVIEW_MAX_WIDTH = JBUIScale.scale(128);
    private final static int PREVIEW_MAX_HEIGHT = JBUIScale.scale(128);
    private final static String PREVIEW_TEXT = "Click Image to Open Resource";

    private final VirtualFile myFile;
    private final ResourceResolver myResourceResolver;

    public GutterIconClickAction(VirtualFile file, ResourceResolver resourceResolver) {
      myFile = file;
      myResourceResolver = resourceResolver;
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
      final Editor editor = event.getData(CommonDataKeys.EDITOR);

      if (editor != null) {
        final Project project = editor.getProject();

        if (project != null) {
          JBPopup preview = createPreview(() -> openImageResourceTab(project));

          if (preview == null) {
            openImageResourceTab(project);
          }
          else {
            // Show preview popup at location of mouse click
            preview.show(new RelativePoint((MouseEvent)event.getInputEvent()));
          }
        }
      }
    }

    @Nullable
    private JBPopup createPreview(Runnable onClick) {
      Icon icon = GutterIconFactory.createIcon(myFile.getPath(), myResourceResolver, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT);

      if (icon == null) {
        return null;
      }

      JBLabel label = new JBLabel(icon);
      ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(label, null);
      builder.setAdText(PREVIEW_TEXT, SwingConstants.CENTER);

      JBPopup popup = builder.createPopup();

      label.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent mouseEvent) {
          onClick.run();
          popup.cancel();
          label.removeMouseListener(this);
        }
      });

      return popup;
    }

    private void openImageResourceTab(@NotNull Project project) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, myFile, -1);
      FileEditorManager.getInstance(project).openEditor(descriptor, true);
    }
  }
}
