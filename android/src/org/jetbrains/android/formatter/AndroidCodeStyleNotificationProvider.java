package org.jetbrains.android.formatter;

import static org.jetbrains.android.util.AndroidBundle.message;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.XmlLanguageCodeStyleSettingsProvider;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationsConfiguration;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.ui.EditorNotifications;
import java.util.function.Function;
import javax.swing.JComponent;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidCodeStyleNotificationProvider implements EditorNotificationProvider {

  @NonNls private static final String ANDROID_XML_CODE_STYLE_NOTIFICATION_GROUP = "Android XML code style notification";

  private final Project myProject;
  private final EditorNotifications myNotifications;

  public AndroidCodeStyleNotificationProvider(Project project) {
    myProject = project;
    myNotifications = EditorNotifications.getInstance(project);
  }

  @Override
  public @Nullable Function<FileEditor, JComponent> collectNotificationData(@NotNull Project project, @NotNull VirtualFile file) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, XmlFileType.INSTANCE)) return null;
    final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
    if (module == null) return null;
    final AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) return null;
    final AndroidXmlCodeStyleSettings androidSettings = AndroidXmlCodeStyleSettings.getInstance(CodeStyle.getSettings(myProject));
    if (androidSettings.USE_CUSTOM_SETTINGS) return null;
    if (NotificationsConfigurationImpl.getSettings(ANDROID_XML_CODE_STYLE_NOTIFICATION_GROUP)
          .getDisplayType() == NotificationDisplayType.NONE) {
      return null;
    }

    final VirtualFile parent = file.getParent();
    final VirtualFile resDir = parent != null ? parent.getParent() : null;
    if (resDir == null || !ModuleResourceManagers.getInstance(facet).getLocalResourceManager().isResourceDir(resDir)) {
      return null;
    }

    return this::createNotificationPanel;
  }

  @Nullable
  private MyPanel createNotificationPanel(FileEditor fileEditor) {
    if (!(fileEditor instanceof TextEditor)) return null;
    NotificationsConfiguration.getNotificationsConfiguration()
      .register(ANDROID_XML_CODE_STYLE_NOTIFICATION_GROUP, NotificationDisplayType.BALLOON, false);
    return new MyPanel(fileEditor);
  }

  public class MyPanel extends EditorNotificationPanel {

    MyPanel(@NotNull FileEditor fileEditor) {
      super(fileEditor, Status.Info);

      setText(message("android.xml.code.style.notification"));

      createActionLabel(message("action.label.code.style.notification.open.settings"), () -> {
        ShowSettingsUtilImpl.showSettingsDialog(
          myProject, "preferences.sourceCode." + XmlLanguageCodeStyleSettingsProvider.getConfigurableDisplayNameText(), "");
        myNotifications.updateAllNotifications();
      });

      createActionLabel(message("action.label.code.style.notification.disable"), () -> {
        NotificationsConfiguration.getNotificationsConfiguration()
          .changeSettings(ANDROID_XML_CODE_STYLE_NOTIFICATION_GROUP, NotificationDisplayType.NONE, false, false);
        myNotifications.updateAllNotifications();
      });
    }
  }
}
