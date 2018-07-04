/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

public class RemoveSdkFromManifestHyperlink extends NotificationHyperlink {
  @NotNull private final Module myModule;

  public RemoveSdkFromManifestHyperlink(@NotNull Module module) {
    super("remove.sdk.from.manifest", getMessage(module));
    myModule = module;
  }

  @NotNull
  private static String getMessage(@NotNull Module module) {
    GradleBuildModel buildModel = getBuildModel(module);
    if (buildModel != null) {
      ResolvedPropertyModel minSdkInBuildFile = getMinSdkInBuildModel(buildModel);
      if (minSdkInBuildFile.getValueType() == NONE) {
        // minSdkVersion is not in build file.
        return "Move minSdkVersion to build file and sync project";
      }
    }
    // Build file doesn't exist, or minSdkVersion has been defined in build file.
    return "Remove minSdkVersion and sync project";
  }

  @Nullable
  private static GradleBuildModel getBuildModel(@NotNull Module module) {
    return ProjectBuildModel.get(module.getProject()).getModuleBuildModel(module);
  }

  @NotNull
  private static ResolvedPropertyModel getMinSdkInBuildModel(@NotNull GradleBuildModel buildModel) {
    return buildModel.android().defaultConfig().minSdkVersion();
  }

  @Override
  protected void execute(@NotNull Project project) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(myModule);
    if (androidFacet != null) {
      int minSdkInManifest = SdkVersionInfo.LOWEST_ACTIVE_API;
      Manifest manifest = androidFacet.getManifest();
      if (manifest != null) {
        // Read and remove the value of minSdkVersion from manifest.
        for (UsesSdk usesSdk : manifest.getUsesSdks()) {
          try {
            minSdkInManifest = Integer.parseInt(nullToEmpty(usesSdk.getMinSdkVersion().getStringValue()));
            XmlElement element = usesSdk.getMinSdkVersion().getXmlElement();
            if (element != null) {
              runWriteCommandAction(project, () -> {
                element.delete();
                // Remove usesSdk if it's empty after removing minSdkVersion.
                if (usesSdk.getXmlTag().getAttributes().length == 0) {
                  usesSdk.getXmlTag().delete();
                }
                FileDocumentManager.getInstance().saveAllDocuments();
              });
            }
          }
          catch (NumberFormatException ignored) {
            // Invalid value, use default value.
          }
        }

        // Write minSdkVersion to build file if it is not already defined there.
        GradleBuildModel buildModel = getBuildModel(myModule);
        if (buildModel != null) {
          ResolvedPropertyModel minSdkInBuildFile = getMinSdkInBuildModel(buildModel);
          if (minSdkInBuildFile.getValueType() == NONE) {
            minSdkInBuildFile.setValue(minSdkInManifest);
            runWriteCommandAction(project, buildModel::applyChanges);
          }
        }
      }
    }
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
  }
}
