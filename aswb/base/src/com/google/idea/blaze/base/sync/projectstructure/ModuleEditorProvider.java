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
package com.google.idea.blaze.base.sync.projectstructure;

import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

/**
 * Provides a ModuleEditor. This indirection is required to avoid committing modules during
 * integration tests of the sync process, as this is not allowed by LightPlatformTestCase.
 */
public interface ModuleEditorProvider {

  static ModuleEditorProvider getInstance() {
    return ApplicationManager.getApplication().getService(ModuleEditorProvider.class);
  }

  ModuleEditorImpl getModuleEditor(Project project, BlazeImportSettings importSettings);
}
