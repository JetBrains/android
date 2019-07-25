/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import static com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup.removeExistingContentEntries;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class ContentRootsModuleSetupStep extends AndroidModuleSetupStep {
  @NotNull private final AndroidContentEntriesSetup.Factory myContentEntriesSetupFactory;

  public ContentRootsModuleSetupStep() {
    this(new AndroidContentEntriesSetup.Factory());
  }

  @VisibleForTesting
  ContentRootsModuleSetupStep(@NotNull AndroidContentEntriesSetup.Factory contentEntriesSetupFactory) {
    myContentEntriesSetupFactory = contentEntriesSetupFactory;
  }

  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull AndroidModuleModel androidModel) {
    ModifiableRootModel moduleModel = context.getModifiableRootModel();
    boolean hasNativeModel = context.hasNativeModel();
    AndroidContentEntriesSetup setup = myContentEntriesSetupFactory.create(androidModel, moduleModel, hasNativeModel);
    List<ContentEntry> contentEntries = findContentEntries(moduleModel, androidModel, hasNativeModel);
    setup.execute(contentEntries);
  }

  @NotNull
  private static List<ContentEntry> findContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                       @NotNull AndroidModuleModel androidModel,
                                                       boolean hasNativeModel) {
    if (!hasNativeModel) {
      removeExistingContentEntries(moduleModel);
    }

    List<ContentEntry> contentEntries = new ArrayList<>();
    VirtualFile roootVirtualFile = VfsUtil.findFileByIoFile(androidModel.getRootDirPath(), true);
    assert roootVirtualFile != null;
    ContentEntry contentEntry = moduleModel.addContentEntry(roootVirtualFile);
    contentEntries.add(contentEntry);

    return contentEntries;
  }

  @Override
  public boolean invokeOnBuildVariantChange() {
    return true;
  }
}
