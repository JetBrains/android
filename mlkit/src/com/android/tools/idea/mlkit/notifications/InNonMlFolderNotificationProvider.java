/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.notifications;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.mlkit.LoggingUtils;
import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.idea.mlkit.TfliteModelFileEditor;
import com.android.tools.idea.npw.template.components.ModuleTemplateComboProvider;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.wireless.android.sdk.stats.MlModelBindingEvent.EventType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import java.awt.Component;
import java.io.IOException;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Notifies users that a model file is not in the correct ml folder in order to use ML model binding feature.
 */
public class InNonMlFolderNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> {
  private static final Key<EditorNotificationPanel> KEY = Key.create("ml.incorrect.folder.notification.panel");
  private static final Key<String> HIDDEN_KEY = Key.create("ml.incorrect.folder.notification.panel.hidden");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    if (!StudioFlags.ML_MODEL_BINDING.get()
        || fileEditor.getUserData(HIDDEN_KEY) != null
        || !(fileEditor instanceof TfliteModelFileEditor)) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null && !MlkitUtils.isModelFileInMlModelsFolder(module, file)) {
      EditorNotificationPanel panel = new EditorNotificationPanel();
      panel.setText("This TensorFlow Lite model is not in a configured ml-model directory, model binding is disabled.");
      panel.createActionLabel("Move file", () -> ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          AndroidFacet androidFacet = AndroidFacet.getInstance(module);
          if (androidFacet == null) {
            Logger.getInstance(InNonMlFolderNotificationProvider.class).error("Can't find the Android Facet.");
            return;
          }

          MoveModelFileDialog moveModelFileDialog = new MoveModelFileDialog(module, file);
          if (moveModelFileDialog.showAndGet()) {
            VirtualFile mlVirtualDir = null;
            try {
              mlVirtualDir = VfsUtil.createDirectoryIfMissing(moveModelFileDialog.getSelectedMlDirectoryPath());
              if (mlVirtualDir != null) {
                PsiDirectory mlPsiDir = PsiDirectoryFactory.getInstance(project).createDirectory(mlVirtualDir);
                if (!CopyFilesOrDirectoriesHandler.checkFileExist(
                  mlPsiDir, null, PsiManager.getInstance(project).findFile(file), file.getName(), "Move")) {
                  file.move(this, mlVirtualDir);
                  LoggingUtils.logEvent(EventType.MODEL_IMPORT_FROM_MOVE_FILE_BUTTON, file);
                }
              }
            }
            catch (IOException e) {
              Logger.getInstance(InNonMlFolderNotificationProvider.class)
                .error(String.format("Error moving %s to %s.", file, mlVirtualDir), e);
            }
          }
        }
      }));
      panel.createActionLabel("Hide notification", () -> {
        fileEditor.putUserData(HIDDEN_KEY, "true");
        EditorNotifications.getInstance(project).updateNotifications(file);
      });
      return panel;
    }

    return null;
  }

  private static class MoveModelFileDialog extends DialogWrapper {
    private final Module myModule;
    private final VirtualFile myModelFile;
    private ComboBox<NamedModuleTemplate> myComboBox;

    private MoveModelFileDialog(@NotNull Module module, @NotNull VirtualFile modelFile) {
      super(true);
      myModule = module;
      myModelFile = modelFile;
      init();
      setTitle("Move TensorFlow Lite Model File");
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel dialogPanel = new JPanel();
      dialogPanel.setLayout(new BoxLayout(dialogPanel, BoxLayout.X_AXIS));
      dialogPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
      dialogPanel.setBorder(JBUI.Borders.empty(10));
      dialogPanel.add(new JBLabel("Move the model file to ml/ repository in "));

      myComboBox =
        new ModuleTemplateComboProvider(ProjectSystemUtil.getModuleSystem(myModule).getModuleTemplates(myModelFile))
          .createComponent();
      dialogPanel.add(myComboBox);

      return dialogPanel;
    }

    @NotNull
    private String getSelectedMlDirectoryPath() {
      return ((NamedModuleTemplate)myComboBox.getSelectedItem()).getPaths().getMlModelsDirectories().get(0).getPath();
    }
  }
}
