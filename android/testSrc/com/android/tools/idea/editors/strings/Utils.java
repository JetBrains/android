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
package com.android.tools.idea.editors.strings;

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;

final class Utils {
  private Utils() {
  }

  static void loadResources(@NotNull StringResourceViewPanel panel, @NotNull Path res) {
    AndroidFacet facet = panel.getFacet();

    VirtualFile resAsVirtualFile = LocalFileSystem.getInstance().findFileByIoFile(res.toFile());
    LocalResourceRepository repository = ResourcesTestsUtil.createTestModuleRepository(facet, Collections.singletonList(resAsVirtualFile));

    panel.getTable().setModel(new StringResourceTableModel(StringResourceRepository.create(repository), facet));
  }
}
