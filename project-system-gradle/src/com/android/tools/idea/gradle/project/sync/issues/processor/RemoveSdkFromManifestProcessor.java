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
package com.android.tools.idea.gradle.project.sync.issues.processor;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;
import static com.android.tools.idea.projectsystem.ModuleSystemUtil.getMainModule;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_SDK_REMOVED_FROM_MANIFEST;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.issues.SdkInManifestIssuesReporter.SdkProperty;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import org.jetbrains.android.dom.AndroidAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.manifest.UsesSdk;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class RemoveSdkFromManifestProcessor extends BaseRefactoringProcessor {
  @NotNull private final Collection<Module> myModules;
  @NotNull private final SdkProperty myProperty;

  public RemoveSdkFromManifestProcessor(@NotNull Project project, @NotNull Collection<Module> modules, @NotNull SdkProperty property) {
    super(project);
    myModules = modules;
    myProperty = property;
  }

  @NotNull
  @Override
  public UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptor() {
      @Override
      public String getCodeReferencesText(int usagesCount, int filesCount) {
        return String.format("Propert%s to move/remove %s", usagesCount > 1 ? "ies" : "y",
                             UsageViewBundle.getReferencesString(usagesCount, filesCount));
      }

      @NotNull
      @Override
      public PsiElement[] getElements() {
        return PsiElement.EMPTY_ARRAY;
      }

      @Override
      public String getProcessedElementsHeader() {
        return String.format("Remove %s from manifest%s", myProperty.getPropertyName(), myModules.size() > 1 ? "s" : "");
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    List<UsageInfo> usages = new ArrayList<>();
    runOverSdkManifestElements((module, usesSdk) -> {
      XmlElement element = myProperty.getManifestFunction().apply(usesSdk).getXmlElement();
      if (element != null) {
        usages.add(new UsageInfo(element));
      }
    });
    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(myProject);
    Ref<Boolean> xmlChanged = new Ref<>(false);
    Ref<Boolean> buildFileChanged = new Ref<>(false);
    runOverSdkManifestElements((module, usesSdk) -> {
      int defaultVersion = myProperty.getDefaultValue();
      AndroidAttributeValue<String> androidAttributeValue = myProperty.getManifestFunction().apply(usesSdk);
      // Read and remove the value of the property from manifest.
      try {
        defaultVersion = Integer.parseInt(nullToEmpty(androidAttributeValue.getStringValue()));
      }
      catch (NumberFormatException ignored) {
        // Invalid value, use default value.
      }

      XmlElement element = androidAttributeValue.getXmlElement();
      if (element != null) {
        xmlChanged.set(true);
        element.delete();
        // Remove usesSdk if it's empty after removing the property.
        if (usesSdk.getXmlTag().getAttributes().length == 0) {
          usesSdk.getXmlTag().delete();
        }
      }

      // Write the property to build file if it is not already defined there.
      GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(module);
      if (buildModel != null) {
        ResolvedPropertyModel propertyInBuildModel = getSdkPropertyInBuildModel(buildModel);
        if (propertyInBuildModel.getValueType() == NONE) {
          buildFileChanged.set(true);
          propertyInBuildModel.setValue(defaultVersion);
        }
      }
    });

    if (buildFileChanged.get()) {
      projectBuildModel.applyChanges();
    }
    if (xmlChanged.get()) {
      FileDocumentManager.getInstance().saveAllDocuments();
    }

    if (xmlChanged.get() || buildFileChanged.get()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        GradleSyncInvoker.getInstance()
          .requestProjectSync(myProject, new GradleSyncInvoker.Request(TRIGGER_QF_SDK_REMOVED_FROM_MANIFEST), null);
      }, myProject.getDisposed());
    }
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return String.format("Remove %s from manifest%s", myProperty.getPropertyName(), myModules.size() > 1 ? "s" : "");
  }

  @NotNull
  private ResolvedPropertyModel getSdkPropertyInBuildModel(@NotNull GradleBuildModel buildModel) {
    return myProperty.getBuildFileFunction().apply(buildModel.android().defaultConfig());
  }

  private void runOverSdkManifestElements(@NotNull BiConsumer<Module, UsesSdk> func) {
    for (AndroidFacet androidFacet : ProjectSystemUtil.getAndroidFacets(myProject)) {
      Manifest manifest = Manifest.getMainManifest(androidFacet);
      if (manifest != null) {
        // Read and remove the value of the property from manifest.
        for (UsesSdk usesSdk : manifest.getUsesSdks()) {
          func.accept(getMainModule(androidFacet.getModule()), usesSdk);
        }
      }
    }
  }
}
