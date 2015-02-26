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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.gradle.service.repo.ExternalRepository;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

/**
 * https://code.google.com/p/android/issues/detail?id=80441
 */
public class UpgradeAppenginePluginVersionHyperlink extends NotificationHyperlink {

  private static final String DEFAULT_APPENGINE_PLUGIN_VERSION = "1.9.17";
  public static final String APPENGINE_PLUGIN_GROUP_ID = "com.google.appengine";
  public static final String APPENGINE_PLUGIN_ARTIFACT_ID = "gradle-appengine-plugin";
  public static final String APPENGINE_PLUGIN_NAME = APPENGINE_PLUGIN_GROUP_ID + ":" + APPENGINE_PLUGIN_ARTIFACT_ID + ":";
  public static final GradleCoordinate REFERENCE_APPENGINE_COORDINATE =
    GradleCoordinate.parseCoordinateString(APPENGINE_PLUGIN_NAME + DEFAULT_APPENGINE_PLUGIN_VERSION);

  @NotNull private final VirtualFile myConfigToCorrect;

  public UpgradeAppenginePluginVersionHyperlink(@NotNull VirtualFile configToCorrect) {
    super("gradle.plugin.appengine.version.upgrade", AndroidBundle.message("android.gradle.link.appengine.outdated"));
    myConfigToCorrect = configToCorrect;
  }

  @Override
  protected void execute(@NotNull Project project) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    final Document document = fileDocumentManager.getDocument(myConfigToCorrect);
    if (document == null) {
      return;
    }
    boolean updated = GradleUtil.updateGradleDependencyVersion(project, document, APPENGINE_PLUGIN_NAME, new Computable<String>() {
      @Override
      public String compute() {
        ExternalRepository repository = ServiceManager.getService(ExternalRepository.class);
        FullRevision latest = repository.getLatest(APPENGINE_PLUGIN_GROUP_ID, APPENGINE_PLUGIN_ARTIFACT_ID);
        return latest == null ? DEFAULT_APPENGINE_PLUGIN_VERSION : latest.toString();
      }
    });
    if (updated) {
      GradleProjectImporter.getInstance().requestProjectSync(project, null);
    }
  }
}
