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
package com.android.tools.idea.gradle.editor.parser;

import com.android.tools.idea.gradle.editor.entity.ExternalDependencyGradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorParserTestUtil.text;

public class ExternalDependencyChecker {

  private final ScopeChecker myScopeExpectations = new ScopeChecker();
  private final GroupChecker myGroupExpectations = new GroupChecker();
  private final ArtifactChecker myArtifactExpectations = new ArtifactChecker();
  private final VersionChecker myVersionExpectations = new VersionChecker();
  private final Set<GradleEditorEntityMetaData> myExpectedMetaData = Sets.newHashSet();

  @Nullable private String myExpectedEntityText;

  private ExternalDependencyChecker() {
  }

  @NotNull
  public static ExternalDependencyChecker create() {
    return new ExternalDependencyChecker();
  }

  @NotNull
  public ExternalDependencyChecker scope(@NotNull String value) {
    myScopeExpectations.setExpectedValue(value);
    return this;
  }

  @SuppressWarnings("UnusedDeclaration")
  @NotNull
  public ExternalDependencyChecker scopeBinding(@NotNull String... bindingsText) {
    myScopeExpectations.setExpectedDefinitionValueBindings(bindingsText);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker group(@NotNull String value) {
    myGroupExpectations.setExpectedValue(value);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker groupBinding(@NotNull String ... bindingsText) {
    myGroupExpectations.setExpectedDefinitionValueBindings(bindingsText);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker artifact(@NotNull String value) {
    myArtifactExpectations.setExpectedValue(value);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker artifactBinding(@NotNull String ... bindingsText) {
    myArtifactExpectations.setExpectedDefinitionValueBindings(bindingsText);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker version(@NotNull String value) {
    myVersionExpectations.setExpectedValue(value);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker versionBinding(@NotNull String ... bindingsText) {
    myVersionExpectations.setExpectedDefinitionValueBindings(bindingsText);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker versionDeclarationValue(@NotNull String text) {
    myVersionExpectations.setExpectedDeclarationValue(text);
    return this;
  }

  @NotNull
  public ExternalDependencyChecker entityText(@NotNull String text) {
    myExpectedEntityText = text;
    return this;
  }

  @NotNull
  public ExternalDependencyChecker metaData(@NotNull GradleEditorEntityMetaData... metaData) {
    Collections.addAll(myExpectedMetaData, metaData);
    return this;
  }

  @Nullable
  public String matches(@NotNull ExternalDependencyGradleEditorEntity actual) {
    if (myExpectedEntityText != null && !myExpectedEntityText.equals(text(actual.getEntityLocation()))) {
      return String.format("expected entity text mismatch - expected: '%s', actual: '%s'",
                           myExpectedEntityText, text(actual.getEntityLocation()));
    }
    String reason = myScopeExpectations.apply(actual);
    if (reason != null) {
      return reason;
    }
    reason = myGroupExpectations.apply(actual);
    if (reason != null) {
      return reason;
    }
    reason = myArtifactExpectations.apply(actual);
    if (reason != null) {
      return reason;
    }
    reason = myVersionExpectations.apply(actual);
    if (reason != null) {
      return reason;
    }
    if (!myExpectedMetaData.equals(actual.getMetaData())) {
      return String.format("expected meta-data %s but found %s", myExpectedMetaData, actual.getMetaData());
    }
    return null;
  }

  @Override
  public String toString() {
    return String.format("%s %s:%s:%s", myScopeExpectations, myGroupExpectations, myArtifactExpectations, myVersionExpectations);
  }

  private static class ScopeChecker extends AbstractPropertyChecker<ExternalDependencyGradleEditorEntity> {

    @Nullable
    @Override
    protected String deriveActualValue(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getScope();
    }

    @NotNull
    @Override
    protected List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getScopeBindings();
    }
  }

  private static class GroupChecker extends AbstractPropertyChecker<ExternalDependencyGradleEditorEntity> {

    @Nullable
    @Override
    protected String deriveActualValue(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getGroupId();
    }

    @NotNull
    @Override
    protected List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getGroupIdSourceBindings();
    }
  }

  private static class ArtifactChecker extends AbstractPropertyChecker<ExternalDependencyGradleEditorEntity> {

    @Nullable
    @Override
    protected String deriveActualValue(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getArtifactId();
    }

    @NotNull
    @Override
    protected List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getArtifactIdSourceBindings();
    }
  }

  private static class VersionChecker extends AbstractPropertyChecker<ExternalDependencyGradleEditorEntity> {

    @Nullable
    @Override
    protected String deriveActualValue(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getVersion();
    }

    @NotNull
    @Override
    protected List<GradleEditorSourceBinding> deriveDefinitionValueSourceBindings(@NotNull ExternalDependencyGradleEditorEntity entity) {
      return entity.getVersionSourceBindings();
    }
  }
}
