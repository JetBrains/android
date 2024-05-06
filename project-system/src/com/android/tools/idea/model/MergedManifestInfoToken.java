/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.model;

import com.android.manifmerger.ManifestMerger2;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.Token;
import com.intellij.openapi.extensions.ExtensionPointName;

public interface MergedManifestInfoToken<P extends AndroidProjectSystem> extends Token {
  ExtensionPointName<MergedManifestInfoToken<AndroidProjectSystem>>
    EP_NAME = new ExtensionPointName<>("com.android.tools.idea.model.mergedManifestInfoToken");

  /**
   * Allows the {@param projectSystem} to affect the manifest merger invoker builder {@param invoker} according
   * to build-system-specific project aspects.
   * <p>
   * Note that this method cannot remove features from the invoker builder; if any features need to be conditionally
   * included in any build system, those features must be handled within these methods for all build systems.
   *
   * @param projectSystem
   * @param invoker
   * @return the modified invoker
   */
  ManifestMerger2.Invoker withProjectSystemFeatures(P projectSystem, ManifestMerger2.Invoker invoker);
}
