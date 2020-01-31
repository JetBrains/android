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
package com.android.tools.idea.room.migrations.json;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * This was copied from the Room Migration project. It is only a temporary solution and in the future we will try to use the real classes.
 */
public class PrimaryKeyBundle implements SchemaEquality<PrimaryKeyBundle> {
  @SerializedName("columnNames")
  private List<String> mColumnNames;
  @SerializedName("autoGenerate")
  private boolean mAutoGenerate;
  public PrimaryKeyBundle(boolean autoGenerate, List<String> columnNames) {
    mColumnNames = columnNames;
    mAutoGenerate = autoGenerate;
  }
  public List<String> getColumnNames() {
    return mColumnNames;
  }
  public boolean isAutoGenerate() {
    return mAutoGenerate;
  }
  @Override
  public boolean isSchemaEqual(PrimaryKeyBundle other) {
    return mColumnNames.equals(other.mColumnNames) && mAutoGenerate == other.mAutoGenerate;
  }
}
