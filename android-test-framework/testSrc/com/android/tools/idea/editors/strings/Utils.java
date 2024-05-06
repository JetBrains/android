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

import static com.android.testutils.AsyncTestUtils.waitForCondition;

import com.android.tools.idea.editors.strings.model.StringResourceRepository;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.ResourcesTestsUtil;
import com.android.tools.res.LocalResourceRepository;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.EdtExecutorService;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

final class Utils {
  private Utils() {
  }

  static void loadResources(@NotNull StringResourceViewPanel panel, @NotNull Collection<Path> resPaths) {
    AndroidFacet facet = panel.getFacet();
    LocalFileSystem system = LocalFileSystem.getInstance();

    Collection<VirtualFile> resVirtualFiles = resPaths.stream()
      .map(path -> system.findFileByIoFile(path.toFile()))
      .collect(Collectors.toList());

    LocalResourceRepository<VirtualFile> repository = ResourcesTestsUtil.createTestModuleRepository(facet, resVirtualFiles);
    StringResourceRepository stringRepository = createStringRepository(repository);
    panel.getTable().setModel(new StringResourceTableModel(stringRepository, facet.getModule().getProject()));
  }

  static @NotNull StringResourceRepository createStringRepository(@NotNull LocalResourceRepository<VirtualFile> repository) {
    StringResourceRepository stringResourceRepository = StringResourceRepository.create(repository);
    try {
      AtomicBoolean updatesFinished = new AtomicBoolean();
      repository.invokeAfterPendingUpdatesFinish(EdtExecutorService.getInstance(),
                                                 () -> updatesFinished.set(true));
      waitForCondition(2, TimeUnit.SECONDS, updatesFinished::get);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    return stringResourceRepository;
  }
}
