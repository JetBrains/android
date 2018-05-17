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
package com.android.tools.idea.gradle.project.sync.issues;

import com.android.tools.idea.gradle.model.IdeSyncIssue;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessages;
import com.android.tools.idea.project.messages.SyncMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import java.util.Map;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for issue reporters, defines base behaviour for classes to report different types of notifications that are obtained from
 * syncing a Gradle project with Android Studio.
 */
abstract class BaseSyncIssuesReporter {
  /**
   * @return the type of messages this reporter should be run against. This reporter will only be invoked for sync issues of this type.
   */
  @MagicConstant(valuesFromClass = IdeSyncIssue.class)
  abstract int getSupportedIssueType();

  /**
   * @param syncIssues    list of sync issues to be reported.
   * @param moduleMap     provides the origin module of each sync issue, this map MUST contain every sync issue provided in syncIssues.
   * @param buildFileMap  map of build files per module, this map provides information to each of the reporters to support quick links to
   *                      the build.gradle files, entries in this map are optional.
   */
  abstract @NotNull List<? extends SyncMessage> reportAll(@NotNull List<IdeSyncIssue> syncIssues,
                                                                   @NotNull Map<IdeSyncIssue, Module> moduleMap,
                                                                   @NotNull Map<Module, VirtualFile> buildFileMap);

}
