/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.npw;

import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.ide.util.projectWizard.JavaModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.BuilderBasedTemplate;
import icons.AndroidIcons;
import org.jetbrains.android.newProject.AndroidProjectTemplatesFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TemplateWizardProjectTemplateFactory extends ProjectTemplatesFactory {
  public static final ProjectTemplate[] EMPTY_PROJECT_TEMPLATES = new ProjectTemplate[]{};
  private static final String IMPORT_EXISTING_PROJECT_TEMPLATE_NAME = "ImportExistingProject";

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[]{AndroidProjectTemplatesFactory.ANDROID};
  }

  @Override
  public Icon getGroupIcon(String group) {
    return AndroidIcons.Android;
  }

  @Override
  public String getParentGroup(String group) {
    return "Java";
  }

  @Override
  public int getGroupWeight(String group) {
    return JavaModuleBuilder.JAVA_MOBILE_WEIGHT;
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(@Nullable String group, WizardContext context) {
    Project project = context.getProject();
    if (project != null && !Projects.requiresAndroidModel(project)) {
      return EMPTY_PROJECT_TEMPLATES;
    }
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates(Template.CATEGORY_PROJECTS);
    List<ProjectTemplate> tt = new ArrayList<>();
    for (File template : templates) {
      final String templateName = template.getName();

      if (WizardConstants.PROJECT_TEMPLATE_NAME.equals(templateName) ||
          IMPORT_EXISTING_PROJECT_TEMPLATE_NAME.equals(templateName) ||
          project == null && !WizardConstants.MODULE_TEMPLATE_NAME.equals(templateName)) {
        continue;
      }
      TemplateMetadata metadata = manager.getTemplateMetadata(template);
      if (metadata == null || !metadata.isSupported()) {
        continue;
      }
      tt.add(new AndroidProjectTemplate(template, metadata, project, context.getDisposable()));
    }
    return tt.toArray(new ProjectTemplate[tt.size()]);
  }

  private static class AndroidProjectTemplate extends BuilderBasedTemplate {
    private final TemplateMetadata myTemplateMetadata;

    private AndroidProjectTemplate(File templateFile, TemplateMetadata metadata, Project project, Disposable parentDisposable) {
      super(new TemplateWizardModuleBuilder(templateFile,
                                            metadata,
                                            project,
                                            null,
                                            new ArrayList<>(),
                                            parentDisposable,
                                            true));
      myTemplateMetadata = metadata;
    }

    @NotNull
    @Override
    public String getName() {
      final String title = myTemplateMetadata.getTitle();
      assert title != null;
      return "Gradle: " + title;
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateMetadata.getDescription();
    }
  }

}
