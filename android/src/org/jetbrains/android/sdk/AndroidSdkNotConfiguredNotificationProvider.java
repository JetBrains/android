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

  @Override
  @Nullable
  public Function<FileEditor, EditorNotificationPanel> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE)) return null;
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) return null;
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) return null;
    if (AndroidModel.isRequired(facet)) return null;
    if (!IdeResourcesUtil.isResourceFile(file, facet) && !file.equals(AndroidRootUtil.getPrimaryManifestFile(facet))) {
      return null;
    }
    final AndroidPlatform platform = AndroidPlatforms.getInstance(module);
    if (platform != null) return null;

    return (fileEditor) -> new MySdkNotConfiguredNotificationPanel(project, fileEditor, module);
  }

  private static class MySdkNotConfiguredNotificationPanel extends EditorNotificationPanel {

    MySdkNotConfiguredNotificationPanel(Project myProject, @NotNull FileEditor fileEditor, @NotNull final Module module) {
      super(fileEditor, Status.Warning);

      setText(message("android.sdk.not.configured.notification", module.getName()));

      createActionLabel(message("action.label.open.project.structure"), () -> {
        ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.getName());
        EditorNotifications.getInstance(myProject).updateAllNotifications();
      });
    }
  }
}
