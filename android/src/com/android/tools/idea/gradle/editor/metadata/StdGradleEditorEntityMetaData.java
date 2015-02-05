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

/**
 * It's assumed that the whole 'enhanced gradle editor' framework is pluggable and customizable, i.e. different plugins
 * might provide their own {@link GradleEditorEntityMetaData meta-data}, but there is still a core part which does a lot of stuff.
 * <p/>
 * Shared meta-data instances used by that core part are hold within the current class.
 */
public class StdGradleEditorEntityMetaData {

  /**
   * Indicates that particular {@link GradleEditorEntity} is injected from elsewhere, e.g. 'subprojects'/'allprojects' parent
   * project's <code>build.gradle</code>.
   */
  public static final GradleEditorEntityMetaData INJECTED = new MarkerGradleEditorEntityMetaData("INJECTED");

  /**
   * Indicates that particular {@link GradleEditorEntity} declared at the target <code>build.gradle</code> will be injected
   * to another projects as well, e.g. declared at 'subprojects'/'allprojects' section.
   */
  public static final GradleEditorEntityMetaData OUTGOING = new MarkerGradleEditorEntityMetaData("OUTGOING");

  /**
   * Indicates that particular {@link GradleEditorEntity} declared at the target <code>build.gradle</code> file might be remove
   * via our 'enhanced gradle editor' UI.
   * <p/>
   * E.g. an external dependency might be removed but not, say, compile sdk version declaration.
   */
  public static final GradleEditorEntityMetaData REMOVABLE = new MarkerGradleEditorEntityMetaData("REMOVABLE");

  /**
   * Indicates that particular entity is read-only.
   * <p/>
   * Example: <code>build.gradle</code> might contain an expression like <code>'mavenCentral()'</code> - it constructs an entity
   * which uses maven central url. However, we don't want to allow url modification via UI, so, we might mark the entity with
   * this value for that.
   */
  public static final GradleEditorEntityMetaData READ_ONLY = new MarkerGradleEditorEntityMetaData("READ_ONLY");

  private StdGradleEditorEntityMetaData() {
  }
}
