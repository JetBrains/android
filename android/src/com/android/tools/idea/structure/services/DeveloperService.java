/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Iterator;
import java.util.List;

/**
 * A class that wraps the contents and {@link ServiceContext} of a 'service.xml' file. Each
 * {@link Module} has its own copy of a service, as settings of the module may affect its final
 * output, and when {@link #install()} is called, it copies the service's files into the project.
 */
public final class DeveloperService {

  @NotNull private final ServiceXmlParser myServiceParser;

  public DeveloperService(@NotNull final ServiceXmlParser serviceParser) {
    myServiceParser = serviceParser;
  }

  @NotNull
  public Module getModule() {
    return myServiceParser.getModule();
  }

  @NotNull
  public ServiceCategory getCategory() {
    return myServiceParser.getServiceCategory();
  }

  @NotNull
  public DeveloperServiceMetadata getMetadata() {
    return myServiceParser.getDeveloperServiceMetadata();
  }

  @NotNull
  public ServiceContext getContext() {
    return myServiceParser.getContext();
  }

  @NotNull
  public JPanel getPanel() {
    return myServiceParser.getServicePanel();
  }

  /**
   * Execute instructions which will install this service.
   */
  public void install() {
    Project project = myServiceParser.getModule().getProject();
    new WriteCommandAction.Simple(project, "Install " + getMetadata().getName()) {
      @Override
      protected void run() throws Throwable {
        myServiceParser.createRecipe(true);
      }
    }.execute();
    getContext().snapshot();
    getContext().installed().set(true);

    UsageTracker.getInstance()
      .trackEvent(UsageTracker.CATEGORY_DEVELOPER_SERVICES, UsageTracker.ACTION_DEVELOPER_SERVICES_INSTALLED, getMetadata().getName(),
                  null);
  }

  public void uninstall() {
    if (!getContext().installed().get()) {
      return;
    }

    final GradleBuildFile gradleFile = GradleBuildFile.get(getModule());
    if (gradleFile != null) {
      boolean dependenciesChanged = false;
      final List<BuildFileStatement> dependencies = gradleFile.getDependencies();
      Iterator<BuildFileStatement> iterator = dependencies.iterator();
      while (iterator.hasNext()) {
        BuildFileStatement statement = iterator.next();
        if (!(statement instanceof Dependency)) {
          continue;
        }

        Dependency dependency = (Dependency)statement;
        for (String dependencyValue : getMetadata().getDependencies()) {
          if (dependency.getValueAsString().equals(dependencyValue)) {
            iterator.remove();
            dependenciesChanged = true;
            break;
          }
        }
      }

      if (dependenciesChanged) {
        new WriteCommandAction.Simple(getModule().getProject(), "Uninstall " + getMetadata().getName()) {
          @Override
          public void run() {
            gradleFile.setValue(BuildFileKey.DEPENDENCIES, dependencies);
          }
        }.execute();
      }
      GradleProjectImporter.getInstance().requestProjectSync(getModule().getProject(), null);
    }

    getContext().installed().set(false);

    UsageTracker.getInstance()
      .trackEvent(UsageTracker.CATEGORY_DEVELOPER_SERVICES, UsageTracker.ACTION_DEVELOPER_SERVICES_REMOVED, getMetadata().getName(), null);
  }
}
