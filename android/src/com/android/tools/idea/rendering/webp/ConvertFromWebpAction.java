/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering.webp;


import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.android.SdkConstants.DOT_PNG;
import static com.android.SdkConstants.DOT_WEBP;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

/**
 * Action which converts WEBP images to PNG
 */
public class ConvertFromWebpAction extends DumbAwareAction {
  @Nls(capitalization = Nls.Capitalization.Title) public static final String TITLE = "Convert from WebP to PNG";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    int answer = Messages.showYesNoCancelDialog(project, "Delete .webp files after saving as .png?", TITLE, null);
    if (answer == Messages.CANCEL) {
      return;
    }
    boolean delete = answer == Messages.YES;
    VirtualFile[] files = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    perform(project, files, delete);
  }

  public void perform(@NotNull Project project, @NotNull VirtualFile[] files, boolean deleteWebp) {
    convert(project, Arrays.asList(files), deleteWebp);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && e.getProject() != null) {
      for (VirtualFile file : files) {
        if (endsWithIgnoreCase(file.getName(), DOT_WEBP)) {
          e.getPresentation().setEnabledAndVisible(true);
          return;
        }
      }
    }

    e.getPresentation().setEnabledAndVisible(false);
  }

  public void convert(@NotNull Project project,
                      @NotNull Collection<VirtualFile> files,
                      boolean delete) {
    ProgressManager.getInstance().run(new WebpConversionTask(project, files, delete));
  }

  static class WebpConversionTask extends Task.Backgroundable {
    private final Project myProject;
    private final Collection<VirtualFile> myFiles;
    private final boolean myDelete;

    private List<VirtualFile> myParentFolders;
    private List<VirtualFile> myConvertedFiles;

    public WebpConversionTask(Project project,
                              Collection<VirtualFile> files,
                              boolean delete) {
      super(project, TITLE, true);
      myProject = project;
      myFiles = files;
      myDelete = delete;
    }

    @Override
    public void onFinished() {
      writeImages(this, myProject, myConvertedFiles);
      refreshFolders(myParentFolders);
    }

    private void writeImages(@NotNull Object requestor, @NotNull Project project, @NotNull List<VirtualFile> files) {
      WriteCommandAction.runWriteCommandAction(project, () -> {
        for (VirtualFile file : files) {
          try {
            if (!file.isValid()) {
              continue;
            }
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null) {
              ByteArrayOutputStream stream = new ByteArrayOutputStream(2048);
              ImageIO.write(image, "PNG", stream);
              byte[] encoded = stream.toByteArray();
              VirtualFile folder = file.getParent();
              VirtualFile output = folder.createChildData(requestor, file.getNameWithoutExtension() + DOT_PNG);
              try (OutputStream fos = new BufferedOutputStream(output.getOutputStream(this))) {
                fos.write(encoded);
              }
              if (myDelete) {
                file.delete(requestor);
              }
            }
          }
          catch (IOException e) {
            Logger.getInstance(ConvertFromWebpAction.class).warn(e.getMessage());
          }
        }
      });
    }

    @Override
    public void run(@NotNull ProgressIndicator progressIndicator) {
      LinkedList<VirtualFile> images = new LinkedList<>(myFiles);
      myConvertedFiles = findImages(progressIndicator, images);
      myParentFolders = computeParentFolders(myConvertedFiles);
    }

    @NotNull
    private static List<VirtualFile> findImages(@NotNull ProgressIndicator progressIndicator, LinkedList<VirtualFile> images) {
      List<VirtualFile> files = new ArrayList<>();

      while (!images.isEmpty()) {
        progressIndicator.checkCanceled();
        VirtualFile file = images.pop();
        progressIndicator.setText(file.getPresentableUrl());
        if (file.isDirectory()) {
          for (VirtualFile f : file.getChildren()) {
            images.push(f);
          }
        }
        else if (endsWithIgnoreCase(file.getName(), DOT_WEBP)) {
          files.add(file);
        }
      }
      return files;
    }
  }

  private static void refreshFolders(List<VirtualFile> toRefresh) {
    for (VirtualFile dir : toRefresh) {
      dir.refresh(true, true);
    }
  }

  @NotNull
  private static List<VirtualFile> computeParentFolders(@NotNull List<VirtualFile> files) {
    List<VirtualFile> toRefresh = new ArrayList<>();
    for (VirtualFile file : files) {
      VirtualFile parent = file.getParent();
      if (parent != null && !toRefresh.contains(parent)) {
        toRefresh.add(parent);
      }
    }
    return toRefresh;
  }
}