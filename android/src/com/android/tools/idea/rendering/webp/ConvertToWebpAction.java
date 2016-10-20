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


import com.android.resources.ResourceFolderType;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;

/**
 * Action which converts source PNG and JPEG images into WEBP
 */
public class ConvertToWebpAction extends DumbAwareAction {
  @Nls(capitalization = Nls.Capitalization.Title) public static final String TITLE = "Converting Images to WebP";

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    Module module = e.getData(LangDataKeys.MODULE);
    if (module == null) {
      module = e.getData(LangDataKeys.MODULE_CONTEXT);
    }
    int minSdkVersion = Integer.MAX_VALUE;
    if (module != null) {
      AndroidModuleInfo info = AndroidModuleInfo.get(module);
      if (info != null) {
        minSdkVersion = Math.min(minSdkVersion, info.getMinSdkVersion().getFeatureLevel());
      }
    }
    else {
      Module[] modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY);
      if (modules != null) {
        for (Module m : modules) {
          AndroidModuleInfo info = AndroidModuleInfo.get(m);
          if (info != null) {
            minSdkVersion = Math.min(minSdkVersion, info.getMinSdkVersion().getFeatureLevel());
          }
        }
      }
    }

    VirtualFile[] files = e.getRequiredData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    perform(project, minSdkVersion, files);
  }

  public void perform(@NotNull Project project, int minSdkVersion, VirtualFile[] files) {
    // TODO: check if the images converted are in values-vNN folders (or offer to do that?)
    // and if so use that to infer higher minSdkVersion

    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = minSdkVersion < 18;
    settings.allowLossless = minSdkVersion >= 1;

    boolean singleFile = files.length == 1 && isEligibleForConversion(files[0], null);
    WebpConversionDialog dialog = new WebpConversionDialog(project, minSdkVersion, settings, singleFile);
    if (!dialog.showAndGet()) {
      return;
    }

    dialog.toSettings(settings);

    convert(project, settings, true, Arrays.asList(files));
  }

  public static boolean isResourceDirectory(@NotNull VirtualFile file, @NotNull Project project) {
    if (file.isDirectory()) {
      ResourceFolderType folderType = ResourceFolderType.getFolderType(file.getName());
      if (folderType != null) {
        return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
      }

      return AndroidResourceUtil.isLocalResourceDirectory(file, project);
    }

    return false;
  }

  @Override
  public void update(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
    if (files != null && e.getProject() != null) {
      for (VirtualFile file : files) {
        boolean directory = file.isDirectory();
        if (directory && isResourceDirectory(file, e.getProject()) ||
            !directory &&
            isEligibleForConversion(file, null)) {
          e.getPresentation().setEnabledAndVisible(true);
          return;
        }
      }
    }

    e.getPresentation().setEnabledAndVisible(false);
  }

  public void convert(@NotNull Project project,
                      @NotNull WebpConversionSettings settings,
                      boolean showBalloon,
                      @NotNull Collection<VirtualFile> files) {
    ProgressManager.getInstance().run(new WebpConversionTask(project, settings, showBalloon, files));
  }

  public static boolean isEligibleForConversion(@Nullable VirtualFile file, @Nullable WebpConversionSettings settings) {
    if (file != null && !file.isDirectory()) {
      String name = file.getName();
      if (name.endsWith(DOT_PNG)) {
        if (settings != null && settings.skipNinePatches && endsWithIgnoreCase(name, DOT_9PNG)) {
          return false;
        }
        if (settings != null && settings.skipTransparentImages) {
          try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image != null && ImageUtils.isNonOpaque(image)) {
              return false;
            }
          } catch (IOException ignore) {
          }
        }
        return true;
      }
      if (endsWithIgnoreCase(name, DOT_JPG) || SdkUtils.endsWith(name, DOT_JPEG)) {
        return true;
      }
      if (endsWithIgnoreCase(name, DOT_BMP)) {
        // Really?
        return true;
      }
      if (endsWithIgnoreCase(name, DOT_GIF)) {
        // Can convert only if not an animated gif (TODO: Support animated webp!)
        ImageReader is = ImageIO.getImageReadersBySuffix("GIF").next();
        ImageInputStream iis;
        try {
          iis = ImageIO.createImageInputStream(file.getInputStream());
          is.setInput(iis);
          return is.getNumImages(true) == 1;
        }
        catch (IOException ignore) {
          return false;
        }
      }
    }
    return false;
  }

  public static boolean isNinePatchFile(@NotNull VirtualFile file) {
    return file != null && !file.isDirectory() && file.getName().endsWith(DOT_9PNG);
  }

  // From OptimizePngAction
  static String formatSize(long bytes) {
    int unit = 1024;
    if (bytes < unit) {
      return bytes + " bytes";
    }
    int exp = (int) (Math.log(bytes) / Math.log(unit));
    String pre = Character.toString("KMGTPE".charAt(exp-1));
    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
  }

  static class WebpConversionTask extends Task.Backgroundable {
    private final Project myProject;
    private final boolean myShowBalloon;
    private final Collection<VirtualFile> myFiles;
    private final WebpConversionSettings mySettings;

    private int myNinePatchCount;
    private int myTransparentCount;
    private int myFileCount;
    private long mySaved;
    private int mySkipped;

    private List<VirtualFile> myParentFolders;
    private List<WebpConvertedFile> myConvertedFiles;

    public WebpConversionTask(Project project,
                              WebpConversionSettings settings,
                              boolean showBalloon,
                              Collection<VirtualFile> files) {
      super(project, TITLE, true);
      mySettings = settings;
      myProject = project;
      myShowBalloon = showBalloon;
      myFiles = files;
    }

    @Override
    public void onFinished() {
      if (mySettings.previewConversion &&
          // Doesn't apply in lossless mode - nothing to preview, all conversions are exact
          !mySettings.lossless &&
          !myConvertedFiles.isEmpty()) {
        WebpPreviewDialog dialog = new WebpPreviewDialog(this, myProject, mySettings, myConvertedFiles);
        if (!dialog.showAndGet()) {
          return;
        }
        encode(myConvertedFiles, true);
      } else {
        encode(myConvertedFiles, false);
      }
      writeImages(this, myProject, myConvertedFiles);

      if (myShowBalloon) {
        StringBuilder sb = new StringBuilder();
        if (myFiles.size() > 1 || myFileCount == 0) {
          sb.append(Integer.toString(myFileCount)).append(" files were converted");
        }
        if (mySaved > 0 || myTransparentCount == 0 && myNinePatchCount == 0 && mySkipped == 0) {
          sb.append("<br/>").append(formatSize(mySaved)).append(" saved");
        }
        if (myNinePatchCount > 0) {
          sb.append("<br>").append(Integer.toString(myNinePatchCount)).append(" 9-patch files were skipped");
        }
        if (myTransparentCount > 0) {
          sb.append("<br>").append(Integer.toString(myTransparentCount)).append(" transparent images were skipped");
        }
        if (mySkipped > 0) {
          sb.append("<br>").append(Integer.toString(mySkipped)).append(" files were skipped because there was no net space savings");
        }
        String message = sb.toString();
        new NotificationGroup("Convert to WebP", NotificationDisplayType.BALLOON, true)
          .createNotification(message, NotificationType.INFORMATION)
          .notify(myProject);
      }

      refreshFolders(myParentFolders);
    }

    private void writeImages(Object requestor, Project project, List<WebpConvertedFile> files) {
      WriteCommandAction.runWriteCommandAction(project, () -> {
        for (WebpConvertedFile convertedFile : files) {
          try {
            if (convertedFile.encoded == null) {
              myTransparentCount++;
            } else {
              if (mySettings.skipLargerImages && convertedFile.saved < 0) {
                mySkipped++;
              } else if (convertedFile.saved > 0) {
                mySaved += convertedFile.saved;
                myFileCount++;
                convertedFile.apply(requestor);
              }
            }
          }
          catch (IOException e) {
            Logger.getInstance(ConvertToWebpAction.class).warn(e);
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

    void encode(@NotNull List<WebpConvertedFile> files, boolean skipAlreadyEncoded) {
      for (WebpConvertedFile file : files) {
        if (skipAlreadyEncoded && file.encoded != null) {
          continue;
        }

        if (mySettings.skipNinePatches && isNinePatchFile(file.sourceFile)) {
          // Shouldn't have gotten here: isEligibleForConversion should have filtered it out
          assert false : file;
          continue;
        }

        if (!file.convert(mySettings)) {
          // Shouldn't have gotten here: isEligibleForConversion should have filtered it out
          assert false : file;
        } else {
          if (mySettings.skipLargerImages && file.saved < 0) {
            mySkipped++;
          } else if (file.saved > 0) {
            mySaved += file.saved;
            myFileCount++;
          }
        }
      }
    }

    @NotNull
    private List<WebpConvertedFile> findImages(@NotNull ProgressIndicator progressIndicator, @NotNull LinkedList<VirtualFile> images) {
      List<WebpConvertedFile> files = Lists.newArrayList();

      while (!images.isEmpty()) {
        progressIndicator.checkCanceled();
        VirtualFile file = images.pop();
        progressIndicator.setText(file.getPath());
        if (file.isDirectory()) {
          for (VirtualFile f : file.getChildren()) {
            images.push(f);
          }
        }
        else if (isEligibleForConversion(file, null)) { // null settings: don't skip transparent/nine patches etc: we want to count those
          if (isEligibleForConversion(file, mySettings)) {
            WebpConvertedFile convertedFile = WebpConvertedFile.create(file, mySettings);
            if (convertedFile != null) {
              files.add(convertedFile);
            }
          }
          else if (mySettings.skipNinePatches && isNinePatchFile(file)) {
            myNinePatchCount++;
          } else {
            myTransparentCount++;
          }
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
  private static List<VirtualFile> computeParentFolders(@NotNull List<WebpConvertedFile> files) {
    List<VirtualFile> toRefresh = Lists.newArrayList();
    for (WebpConvertedFile file : files) {
      VirtualFile parent = file.sourceFile.getParent();
      if (parent != null && !toRefresh.contains(parent)) {
        toRefresh.add(parent);
      }
    }
    return toRefresh;
  }
}