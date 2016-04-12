/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.actions;

import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.ResourceNameValidator;
import com.intellij.CommonBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.Resources;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;

/**
 * @author Eugene.Kudelevsky
 */
public class CreateXmlResourceDialog extends DialogWrapper {

  final CreateXmlResourcePanel myPanel;

  public CreateXmlResourceDialog(@NotNull Module module,
                                 @NotNull final ResourceType resourceType,
                                 @Nullable String predefinedName,
                                 @Nullable String predefinedValue,
                                 boolean chooseName,
                                 @Nullable VirtualFile defaultFile,
                                 @Nullable VirtualFile contextFile) {
    super(module.getProject());

    Function<Module, ResourceNameValidator> nameValidatorFactory =
      selectedModule -> ResourceNameValidator.create(true, AppResourceRepository.getAppResources(selectedModule, true), resourceType);
    myPanel = new CreateXmlResourcePanel(module, resourceType, ResourceFolderType.VALUES,
                                         predefinedName, predefinedValue, chooseName, true, defaultFile, contextFile,
                                         nameValidatorFactory);

    init();
  }

  @Override
  protected ValidationInfo doValidate() {
    return myPanel.doValidate();
  }

  @Override
  protected void doOKAction() {
    final String resourceName = myPanel.getResourceName();
    final String fileName = myPanel.getFileName();
    final List<String> dirNames = myPanel.getDirNames();
    final Module module = myPanel.getModule();
    final JComponent panel = myPanel.getPanel();

    if (resourceName.length() == 0) {
      Messages.showErrorDialog(panel, "Resource name is not specified", CommonBundle.getErrorTitle());
    }
    else if (!AndroidResourceUtil.isCorrectAndroidResourceName(resourceName)) {
      Messages.showErrorDialog(panel, resourceName + " is not correct resource name", CommonBundle.getErrorTitle());
    }
    else if (fileName.length() == 0) {
      Messages.showErrorDialog(panel, "File name is not specified", CommonBundle.getErrorTitle());
    }
    else if (dirNames.size() == 0) {
      Messages.showErrorDialog(panel, "Directories are not selected", CommonBundle.getErrorTitle());
    }
    else if (module == null) {
      Messages.showErrorDialog(panel, "Module is not specified", CommonBundle.getErrorTitle());
    }
    else {
      super.doOKAction();
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidCreateXmlResourceDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel.getPanel();
  }

  @Nullable
  public VirtualFile getResourceDirectory() {
    return myPanel.getResourceDirectory();
  }

  public String getResourceName() {
    return myPanel.getResourceName();
  }

  public String getFileName() {
    return myPanel.getFileName();
  }

  public List<String> getDirNames() {
    return myPanel.getDirNames();
  }

  public String getValue() {
    return myPanel.getValue();
  }

  @Nullable
  public static ValidationInfo checkIfResourceAlreadyExists(@NotNull Project project,
                                                            @NotNull VirtualFile resourceDir,
                                                            @NotNull String resourceName,
                                                            @NotNull ResourceType resourceType,
                                                            @NotNull List<String> dirNames,
                                                            @NotNull String fileName) {
    if (resourceName.length() == 0 ||
        dirNames.size() == 0 ||
        fileName.length() == 0) {
      return null;
    }

    for (String directoryName : dirNames) {
      final VirtualFile resourceSubdir = resourceDir.findChild(directoryName);
      if (resourceSubdir == null) {
        continue;
      }

      final VirtualFile resFile = resourceSubdir.findChild(fileName);
      if (resFile == null) {
        continue;
      }

      if (resFile.getFileType() != StdFileTypes.XML) {
        return new ValidationInfo("File " + FileUtil.toSystemDependentName(resFile.getPath()) + " is not XML file");
      }

      final Resources resources = AndroidUtils.loadDomElement(project, resFile, Resources.class);
      if (resources == null) {
        return new ValidationInfo(AndroidBundle.message("not.resource.file.error", FileUtil.toSystemDependentName(resFile.getPath())));
      }

      for (ResourceElement element : AndroidResourceUtil.getValueResourcesFromElement(resourceType, resources)) {
        if (resourceName.equals(element.getName().getValue())) {
          return new ValidationInfo("resource '" + resourceName + "' already exists in " + FileUtil.toSystemDependentName(
            resFile.getPath()));
        }
      }
    }
    return null;
  }
}
