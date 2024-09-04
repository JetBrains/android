/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.idea.blaze.base.logging.utils;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.sync.autosync.ProjectTargetManager.SyncStatus;

/** Highlight information for one file. */
@AutoValue
public abstract class FileHighlights {

  public abstract String fileName();

  public abstract String fileType();

  public abstract String filePath();

  public abstract boolean isProjectSource();

  public abstract SyncStatus syncStatus();

  public abstract ImmutableList<HighlightInfo> highlightInfos();

  public static Builder builder() {
    return new AutoValue_FileHighlights.Builder();
  }

  /** Builder for {@link FileHighlights} */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setFileName(String value);

    public abstract Builder setFileType(String value);

    public abstract Builder setFilePath(String value);

    public abstract Builder setIsProjectSource(boolean value);

    public abstract Builder setSyncStatus(SyncStatus value);

    public abstract Builder setHighlightInfos(ImmutableList<HighlightInfo> value);

    public abstract FileHighlights build();
  }
}
