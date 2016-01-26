/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.repo.ExternalRepository;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

/**
 * https://code.google.com/p/android/issues/detail?id=80441
 */
public class UpgradeAppenginePluginVersionHyperlink extends NotificationHyperlink {
  public static final GradleVersion DEFAULT_APPENGINE_PLUGIN_VERSION = GradleVersion.parse("1.9.17");

  @NonNls public static final String APPENGINE_PLUGIN_GROUP_ID = "com.google.appengine";
  @NonNls public static final String APPENGINE_PLUGIN_ARTIFACT_ID = "gradle-appengine-plugin";

  @NotNull private final ArtifactDependencyModel myDependency;
  @NotNull private final GradleBuildModel myBuildModel;

  public UpgradeAppenginePluginVersionHyperlink(@NotNull ArtifactDependencyModel dependency, @NotNull GradleBuildModel buildModel) {
    super("gradle.plugin.appengine.version.upgrade", AndroidBundle.message("android.gradle.link.appengine.outdated"));
    myDependency = dependency;
    myBuildModel = buildModel;
  }

  @Override
  protected void execute(@NotNull Project project) {
    ExternalRepository repository = ServiceManager.getService(ExternalRepository.class);
    GradleVersion latest = repository.getLatest(APPENGINE_PLUGIN_GROUP_ID, APPENGINE_PLUGIN_ARTIFACT_ID);
    if (latest == null) {
      latest = DEFAULT_APPENGINE_PLUGIN_VERSION;
    }
    myDependency.setVersion(latest.toString());
    runWriteCommandAction(project, new Runnable() {
      @Override
      public void run() {
        myBuildModel.applyChanges();
      }
    });
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }
}
