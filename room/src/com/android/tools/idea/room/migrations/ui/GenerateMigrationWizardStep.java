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
package com.android.tools.idea.room.migrations.ui;

import com.intellij.ide.wizard.Step;

/**
 * Common interface for the steps of {@link GenerateMigrationWizard}.
 */
public interface GenerateMigrationWizardStep extends Step {

  /**
   * Specifies whether a step should be skipped or not.
   *
   * <p>Certain steps may be skipped in certain situations. For instance, there is no need to display the table or column renaming
   * steps if the database update does not feature any deleted tables or columns.</p>
   */
  boolean shouldBeSkipped();
}
