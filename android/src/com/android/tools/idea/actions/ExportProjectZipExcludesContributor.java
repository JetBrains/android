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
package com.android.tools.idea.actions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Implementors of this extension point should declare themselves applicable to a project
 * if appropriate, and if so should be prepared to compute a collection of {@link File}s that
 * should be excluded from exporting the project as a zip (for example, intermediate or
 * generated files from some process acting on the project).  The exporter will exclude the union
 * of all such excluded files; there is no mechanism for explicitly <em>including</em> files
 * in the export or overriding the judgment of any contributor.
 */
public interface ExportProjectZipExcludesContributor {
  ExtensionPointName<ExportProjectZipExcludesContributor> EP_NAME =
    new ExtensionPointName<>("com.android.tools.idea.actions.exportProjectZipExcludesContributor");

  boolean isApplicable(@NotNull Project project);

  Collection<File> excludes(@NotNull Project project);
}
