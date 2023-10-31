// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.sdk;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.sdk.AndroidPlatform;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.util.function.Function;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidSdkNotConfiguredNotificationProvider implements EditorNotificationProvider {
  private final Project myProject;

  public AndroidSdkNotConfiguredNotificationProvider(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  @Nullable
  public Function<FileEditor, EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    EditorNotifications notifications = EditorNotifications.getInstance(project);
    if (!FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE)) {
      return null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;

    if (facet == null) {
      return null;
    }
    if (!AndroidModel.isRequired(facet)
        && (IdeResourcesUtil.isResourceFile(file, facet) || file.equals(AndroidRootUtil.getPrimaryManifestFile(facet)))) {
      final AndroidPlatform platform = AndroidPlatforms.getInstance(module);

      if (platform == null) {
        return (fileEditor) -> new MySdkNotConfiguredNotificationPanel(notifications, fileEditor, module, myProject);
      }
    }
    return null;
  }

  private static class MySdkNotConfiguredNotificationPanel extends EditorNotificationPanel {

    MySdkNotConfiguredNotificationPanel(EditorNotifications myNotifications, @NotNull FileEditor fileEditor, @NotNull final Module module,
                                        Project project) {
      super(fileEditor, Status.Warning);

      setText(message("android.sdk.not.configured.notification", module.getName()));

      createActionLabel(message("action.label.open.project.structure"), new Runnable() {
        @Override
        public void run() {
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.getName());
          EditorNotifications.getInstance(project).updateAllNotifications();
        }
      });
    }
  }
}

