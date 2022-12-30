/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.room.generators;

import com.android.tools.idea.room.migrations.json.DatabaseBundle;
import com.android.tools.idea.room.migrations.json.EntityBundle;
import com.android.tools.idea.room.migrations.json.FieldBundle;
import com.android.tools.idea.room.migrations.json.FtsEntityBundle;
import com.android.tools.idea.room.migrations.json.FtsOptionsBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import java.util.Arrays;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functions for testing schema updates.
 */
public final class TestUtils {
  @NotNull
  public static DatabaseBundle createDatabaseBundle(int version, @NotNull EntityBundle... entities) {
    return new DatabaseBundle(version, "", Arrays.asList(entities), Collections.emptyList(), null);
  }

  @NotNull
  public static FieldBundle createFieldBundle(@NotNull String columnName, @NotNull String affinity, @Nullable String defaultValue) {
    return new FieldBundle("", columnName, affinity, false, defaultValue);
  }

  @NotNull
  public static FieldBundle createFieldBundle(@NotNull String columnName) {
    return createFieldBundle(columnName, "", null);
  }

  @NotNull
  public static EntityBundle createEntityBundle(@NotNull String tableName, @NotNull FieldBundle... fields) {
    return new EntityBundle(tableName,
                            "",
                            Arrays.asList(fields),
                            new PrimaryKeyBundle(false, Collections.singletonList(fields[0].getColumnName())),
                            Collections.emptyList(),
                            Collections.emptyList());
  }
  @NotNull
  public static FtsEntityBundle createFtsEntityBundle(@NotNull String tableName,
                                                      @NotNull String createSql,
                                                      @NotNull FieldBundle... fields) {
    return new FtsEntityBundle(tableName,
                               createSql,
                               Arrays.asList(fields),
                               new PrimaryKeyBundle(false, Collections.singletonList(fields[0].getColumnName())),
                               "",
                               null,
                               Collections.emptyList());
  }

  public static FtsEntityBundle createFtsEntityBundle(@NotNull String tableName,
                                                      @NotNull String createSql,
                                                      @NotNull String contentTableName,
                                                      @NotNull FieldBundle... fields) {
    FtsOptionsBundle optionsBundle = new FtsOptionsBundle("",
                                                          Collections.emptyList(),
                                                          contentTableName,
                                                          "",
                                                          "",
                                                          Collections.emptyList(),
                                                          Collections.emptyList(),
                                                          "");
    return new FtsEntityBundle(tableName,
                               createSql,
                               Arrays.asList(fields),
                               new PrimaryKeyBundle(false, Collections.singletonList(fields[0].getColumnName())),
                               "",
                               optionsBundle,
                               Collections.emptyList());
  }
}
