/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.editor.metadata;

import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Holds meta-information about particular {@link GradleEditorEntity}, e.g. there is a possible case that there is a multi-project
 * and root project defines particular dependency(ise) in <code>'subprojects'</code> section. That dependencies might be marked
 * as <code>'outgoing'</code> for the root project's <code>build.gradle</code> and <code>'injected'</code> for child project's
 * <code>build.gradle</code>.
 */
public interface GradleEditorEntityMetaData {

  /**
   * @return    current meta-information's type. This might be just a string id for marker meta-data (like <code>'injected'</code>
   *            or <code>'outgoing'</code> - see above) or a string which identifies a class of meta-data for meta-data with value
   */
  @NotNull
  String getType();
}
