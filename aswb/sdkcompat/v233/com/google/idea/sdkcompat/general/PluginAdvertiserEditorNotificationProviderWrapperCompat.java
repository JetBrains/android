package com.google.idea.sdkcompat.general;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.PlainTextLikeFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginAdvertiserEditorNotificationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationProvider;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * #api213: remove this class and make EditorNotificationProvider a direct parent of
 * PluginAdvertiserEditorNotificationProviderWrapper. Inline the functionality of this class in
 * PluginAdvertiserEditorNotificationProviderWrapper
 */
public abstract class PluginAdvertiserEditorNotificationProviderWrapperCompat
    implements EditorNotificationProvider {

  // #api213: change to private when inline to PluginAdvertiserEditorNotificationProviderWrapper
  protected final PluginAdvertiserEditorNotificationProvider
      pluginAdvertiserEditorNotificationProvider;

  public PluginAdvertiserEditorNotificationProviderWrapperCompat(
      PluginAdvertiserEditorNotificationProvider pluginAdvertiserEditorNotificationProvider) {

    this.pluginAdvertiserEditorNotificationProvider = pluginAdvertiserEditorNotificationProvider;
  }

  @Override
  public Function<? super FileEditor, ? extends JComponent> collectNotificationData(
      Project project, VirtualFile file) {
    boolean alreadySupported = !(file.getFileType() instanceof PlainTextLikeFileType);
    if (alreadySupported) {
      return EditorNotificationProvider.CONST_NULL;
    }
    return pluginAdvertiserEditorNotificationProvider.collectNotificationData(project, file);
  }

  public static void reregisterExtension(
      Project project, PluginAdvertiserEditorNotificationProviderWrapperCompat replacement) {
    ExtensionPoint<EditorNotificationProvider> ep =
        EditorNotificationProvider.EP_NAME.getPoint(project);
    ep.unregisterExtension(PluginAdvertiserEditorNotificationProvider.class);
    ep.registerExtension(replacement, project);
  }
}
