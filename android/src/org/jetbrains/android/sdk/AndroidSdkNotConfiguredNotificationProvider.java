package org.jetbrains.android.sdk;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidSdkNotConfiguredNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("android.sdk.not.configured.notification");

  private final Project myProject;
  private final EditorNotifications myNotifications;

  public AndroidSdkNotConfiguredNotificationProvider(Project project, final EditorNotifications notifications) {
    myProject = project;
    myNotifications = notifications;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() != XmlFileType.INSTANCE) {
      return null;
    }
    final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
    final AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;

    if (facet == null) {
      return null;
    }
    if (!facet.requiresAndroidModel()
        && (AndroidResourceUtil.isResourceFile(file, facet) || file.equals(AndroidRootUtil.getPrimaryManifestFile(facet)))) {
      final AndroidPlatform platform = AndroidPlatform.getInstance(module);

      if (platform == null) {
        return new MySdkNotConfiguredNotificationPanel(module);
      }
    }
    return null;
  }

  private class MySdkNotConfiguredNotificationPanel extends EditorNotificationPanel {

    MySdkNotConfiguredNotificationPanel(@NotNull final Module module) {
      setText("Android SDK is not configured for module '" + module.getName() + "' or corrupted");

      createActionLabel("Open Project Structure", new Runnable() {
        @Override
        public void run() {
          ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME);
          myNotifications.updateAllNotifications();
        }
      });
    }
  }
}

