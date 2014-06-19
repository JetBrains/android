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
package com.android.tools.idea.gradle.service.notification;

import com.android.SdkConstants;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class InstallPlatformHyperlink extends NotificationHyperlink {
  @NotNull private final AndroidVersion myAndroidVersion;

  InstallPlatformHyperlink(@NotNull AndroidVersion androidVersion) {
    super("install.android.platform", "Install missing platform and sync project");
    myAndroidVersion = androidVersion;
  }

  @Override
  protected void execute(@NotNull Project project) {
    List<IPkgDesc> requested = Lists.newArrayList();
    FullRevision minBuildToolsRev = FullRevision.parseRevision(SdkConstants.MIN_BUILD_TOOLS_VERSION);
    requested.add(PkgDesc.Builder.newPlatform(myAndroidVersion, new MajorRevision(1), minBuildToolsRev).create());
    SdkQuickfixWizard wizard = new SdkQuickfixWizard(project, null, requested);
    wizard.init();
    wizard.show();
    GradleProjectImporter.getInstance().requestProjectSync(project, null);
  }
}
