/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

/** Tag constants used by our rules. */
public final class Tags {
  /** Forces import of the target output. */
  public static final String TARGET_TAG_IMPORT_TARGET_OUTPUT = "intellij-import-target-output";

  public static final String TARGET_TAG_IMPORT_AS_LIBRARY_LEGACY = "aswb-import-as-library";

  /**
   * Signals to the import process that the output of this rule will be provided by the IntelliJ
   * SDK.
   */
  public static final String TARGET_TAG_PROVIDED_BY_SDK = "intellij-provided-by-sdk";

  /** Ignores the target. */
  public static final String TARGET_TAG_EXCLUDE_TARGET = "intellij-exclude-target";
}
