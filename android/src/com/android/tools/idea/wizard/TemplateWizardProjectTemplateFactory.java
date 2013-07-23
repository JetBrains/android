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
package com.android.tools.idea.wizard;

import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.project.Project;
import com.intellij.platform.ProjectTemplate;
import com.intellij.platform.ProjectTemplatesFactory;
import com.intellij.platform.templates.BuilderBasedTemplate;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TemplateWizardProjectTemplateFactory extends ProjectTemplatesFactory {

  public static final String ANDROID_GRADLE_GROUP = "Android Gradle";
  public static final ProjectTemplate[] EMPTY_PROJECT_TEMPLATES = new ProjectTemplate[]{};

  @NotNull
  @Override
  public String[] getGroups() {
    return new String[] {ANDROID_GRADLE_GROUP};
  }

  @Override
  public Icon getGroupIcon(String group) {
    return AndroidIcons.Android;
  }

  @NotNull
  @Override
  public ProjectTemplate[] createTemplates(String group, WizardContext context) {
    if (!Projects.isGradleProject(context.getProject())) {
      return EMPTY_PROJECT_TEMPLATES;
    }
    TemplateManager manager = TemplateManager.getInstance();
    List<File> templates = manager.getTemplates("projects");
    List<ProjectTemplate> tt = new ArrayList<ProjectTemplate>();
    for (int i = 0, n = templates.size(); i < n; i++) {
      File template = templates.get(i);
      TemplateMetadata metadata = manager.getTemplate(template);
      if (metadata == null || !metadata.isSupported()) {
        continue;
      }
      tt.add(new AndroidProjectTemplate(template, metadata, context.getProject()));
    }
    return tt.toArray(EMPTY_PROJECT_TEMPLATES);
  }

  private static class AndroidProjectTemplate extends BuilderBasedTemplate {
    private final TemplateMetadata myTemplateMetadata;

    private AndroidProjectTemplate(File templateFile, TemplateMetadata metadata, Project project) {
      super(new TemplateWizardModuleBuilder(templateFile, metadata, project, null, new ArrayList<ModuleWizardStep>(), true));
      myTemplateMetadata = metadata;
    }

    @NotNull
    @Override
    public String getName() {
      return myTemplateMetadata.getTitle();
    }

    @Nullable
    @Override
    public String getDescription() {
      return myTemplateMetadata.getDescription();
    }
  }

}
