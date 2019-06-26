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
package com.android.tools.idea.room;

import com.android.tools.idea.room.bundle.DatabaseBundle;
import com.android.tools.idea.room.bundle.SchemaBundle;
import com.android.tools.idea.room.update.DatabaseUpdate;
import org.jetbrains.annotations.NotNull;

/**
 * Class responsible for generating migrations between different versions of a Room database.
 */
public class MigrationGenerator {

  /**
   * Compares two versions of the same database schema.
   * @param oldSchema - holds a previous version of the schema
   * @param newSchema - holds the current version  of the schema
   * @return - human readable description of the changes between the two schema version.
   */
  @NotNull
  public static String compareSchemas(@NotNull SchemaBundle oldSchema, @NotNull SchemaBundle newSchema) {
    DatabaseBundle oldDatabase = oldSchema.getDatabase();
    DatabaseBundle newDatabase = newSchema.getDatabase();

    return new DatabaseUpdate(oldDatabase, newDatabase).getDiff();
  }
}


