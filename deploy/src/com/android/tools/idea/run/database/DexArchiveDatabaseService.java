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
package com.android.tools.idea.run.database;

import com.android.tools.deploy.swapper.DexArchiveDatabase;
import com.android.tools.deploy.swapper.SQLiteDexArchiveDatabase;
import com.android.tools.deploy.swapper.WorkQueueDexArchiveDatabase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import java.io.File;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

public class DexArchiveDatabaseService {
  @NotNull
  private final DexArchiveDatabase myDb;

  private DexArchiveDatabaseService() {
    myDb = new WorkQueueDexArchiveDatabase(
      new SQLiteDexArchiveDatabase(new File(Paths.get(PathManager.getSystemPath(), ".deploy.db").toString())));
  }

  /**
   * Note: This method will throw an exception if called from unit tests.
   */
  @NotNull
  public static DexArchiveDatabaseService getInstance() {
    return ServiceManager.getService(DexArchiveDatabaseService.class);
  }

  @NotNull
  public DexArchiveDatabase getDb() {
    return myDb;
  }
}
