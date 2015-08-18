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
package com.android.tools.idea.gradle.editor.entity;

import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

import static com.android.tools.idea.gradle.editor.value.GradleEditorEntityValueManager.NO_OP;

public class GradleEditorRepositoryEntity extends AbstractSimpleGradleEditorEntity {

  public static final String MAVEN_CENTRAL_HELP_ID = "http://gradle.org/docs/current/userguide/userguide_single.html#sub:maven_central";
  public static final String JCENTER_HELP_ID = "http://gradle.org/docs/current/userguide/userguide_single.html#mavenJcenter";
  public static final String MAVEN_GENERIC_HELP_ID = "http://gradle.org/docs/current/userguide/userguide_single.html#sub:maven_repo";
  public static final String MAVEN_CENTRAL_URL = "http://central.maven.org/maven2";
  public static final String JCENTER_URL = "https://jcenter.bintray.com";

  public GradleEditorRepositoryEntity(@NotNull String name,
                                      @NotNull String currentValue,
                                      @NotNull Collection<GradleEditorSourceBinding> definitionValueSourceBindings,
                                      @NotNull GradleEditorSourceBinding entityLocation,
                                      @NotNull Set<GradleEditorEntityMetaData> metaData,
                                      @NotNull GradleEditorSourceBinding declarationValueLocation,
                                      @NotNull String helpId) {
    super(name, currentValue, definitionValueSourceBindings, entityLocation, metaData, declarationValueLocation, NO_OP, helpId);
  }
}
