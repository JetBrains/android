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
import com.android.tools.idea.room.migrations.json.ForeignKeyBundle;
import com.android.tools.idea.room.migrations.json.PrimaryKeyBundle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility functions for testing schema updates.
 */
public class TestUtils {
  @NotNull
  public static DatabaseBundle createDatabaseBundle(int version, @NotNull List<EntityBundle> entities) {
    return new DatabaseBundle(version, "", entities, Collections.emptyList(), null);
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
  public static EntityBundle createEntityBundle(@NotNull String tableName, @NotNull List<FieldBundle> fields) {
    return new EntityBundle(tableName,
                            "",
                            fields,
                            new PrimaryKeyBundle(false, Collections.singletonList(fields.get(0).getColumnName())),
                            null,
                            null);
  }
}
