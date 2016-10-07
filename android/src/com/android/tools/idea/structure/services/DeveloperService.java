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

import com.android.tools.idea.stats.UsageTracker;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.stats.UsageTracker.*;
import static com.android.tools.idea.structure.services.BuildSystemOperationsLookup.getBuildSystemOperations;

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
        myServiceParser.install();
      }
    }.execute();
    getContext().snapshot();
    getContext().installed().set(true);
    trackEvent(ACTION_DEVELOPER_SERVICES_INSTALLED);
  }

  public void uninstall() {
    if (!getContext().installed().get()) {
      return;
    }

    Module module = getModule();
    getBuildSystemOperations(module.getProject()).removeDependencies(module, getMetadata());
    getContext().installed().set(false);
    trackEvent(ACTION_DEVELOPER_SERVICES_REMOVED);

  }

  public boolean isInstalled() {
    return getContext().installed().get();
  }

  private void trackEvent(@NotNull String event) {
    UsageTracker.getInstance().trackEvent(CATEGORY_DEVELOPER_SERVICES, event, getMetadata().getName(), null);
  }

  /**
   * We are not the authority of whether a service is installed or not - instead, we need to check
   * the build model to find out. This method should be triggered externally by a manager class
   * which listens to whenever the build model is modified.
   */
  void updateInstalledState() {
    Module module = getModule();
    boolean isInstalled = getBuildSystemOperations(module.getProject()).isServiceInstalled(module, getMetadata());
    getContext().installed().set(isInstalled);
  }
}
