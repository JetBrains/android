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
import com.android.tools.idea.gradle.editor.entity.GradleEditorEntity;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.value.GradleEditorEntityValueManager;
import com.android.tools.idea.gradle.editor.value.LibraryVersionsManager;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelParseContext.*;
import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelUtil.buildSourceBinding;

/**
 * Encapsulates functionality of parsing dependencies declared at <code>build.gradle</code>.
 *
 * @see #parse(Assignment, GradleEditorModelParseContext)
 */
public class GradleEditorDependencyParser {

  private static final Pattern EXTERNAL_DEPENDENCY_PATTERN = Pattern.compile("([^:\\s]+):([^:\\s()]+):([^:()]+)");

  /**
   * Tries to build a {@link GradleEditorEntity} assuming that given assignment hold dependency definition
   * (like <code>'compile "my-group:my-value:my-version"'</code>).
   *
   * @param assignment  assignment that might hold external dependency definition
   * @param context     current context
   * @return            an entity for the dependency extracted from the given assignment (if any);
   *                    <code>null</code> otherwise
   */
  @Nullable
  public GradleEditorEntity parse(@NotNull Assignment assignment, @NotNull GradleEditorModelParseContext context) {
    if (assignment.value == null) {
      return null;
    }
    String dependencyText = assignment.value.value;
    if (dependencyText.isEmpty() && assignment.rValueString != null) {
      dependencyText = assignment.rValueString;
    }
    Matcher matcher = EXTERNAL_DEPENDENCY_PATTERN.matcher(dependencyText);
    if (!matcher.matches()) {
      return null;
    }
    GradleEditorSourceBinding scopeSourceBinding = buildSourceBinding(assignment.lValueLocation, context.getProject());
    if (scopeSourceBinding == null) {
      return null;
    }
    DependencyDimension group = parseDimension(TextRange.create(matcher.start(1), matcher.end(1)), assignment, dependencyText, context);
    if (group == null) {
      return null;
    }
    DependencyDimension artifact = parseDimension(TextRange.create(matcher.start(2), matcher.end(2)), assignment, dependencyText, context);
    if (artifact == null) {
      return null;
    }
    DependencyDimension version = parseDimension(TextRange.create(matcher.start(3), matcher.end(3)), assignment, dependencyText, context);
    if (version == null) {
      return null;
    }
    GradleEditorSourceBinding entityLocation = buildSourceBinding(assignment, context.getProject());
    if (entityLocation == null) {
      return null;
    }

    Set<GradleEditorEntityMetaData> metaData = Sets.newHashSet();
    metaData.add(StdGradleEditorEntityMetaData.REMOVABLE);
    if (context.getTargetFile().equals(assignment.lValueLocation.file)) {
      if (!assignment.codeStructure.isEmpty()) {
        String s = assignment.codeStructure.get(0);
        if (GradleEditorDsl.ALL_PROJECTS_SECTION.equals(s) || GradleEditorDsl.SUB_PROJECT_SECTION.equals(s)) {
          metaData.add(StdGradleEditorEntityMetaData.OUTGOING);
        }
      }
    }
    else {
      metaData.add(StdGradleEditorEntityMetaData.INJECTED);
    }

    final GradleEditorEntityValueManager versionValueManager;
    if (group.value.isEmpty() || artifact.value.isEmpty()) {
      versionValueManager = GradleEditorEntityValueManager.NO_OP;
    }
    else {
      versionValueManager = new LibraryVersionsManager(group.value, artifact.value);
    }
    return new ExternalDependencyGradleEditorEntity(assignment.lValue.name,
                                                    Arrays.asList(scopeSourceBinding),
                                                    group.value,
                                                    group.sourceBindings,
                                                    artifact.value,
                                                    artifact.sourceBindings,
                                                    version.value,
                                                    version.sourceBindings,
                                                    entityLocation,
                                                    version.declarationValueLocation,
                                                    versionValueManager,
                                                    metaData);
  }

  @Nullable
  private static DependencyDimension parseDimension(@NotNull TextRange dimensionRange,
                                                    @NotNull Assignment assignment,
                                                    @NotNull String dependencyDeclarationString,
                                                    @NotNull GradleEditorModelParseContext context) {
    if (assignment.value == null) {
      return null;
    }
    TextRange dimensionRangeInFile = dimensionRange.shiftRight(assignment.value.location.range.getStartOffset());
    List<Variable> dependencies = Lists.newArrayList();
    for (Map.Entry<Variable, Location> entry : assignment.dependencies.entries()) {
      if (dimensionRangeInFile.contains(entry.getValue().range)) {
        dependencies.add(entry.getKey());
      }
    }
    String dimensionValue = dependencyDeclarationString.substring(dimensionRange.getStartOffset(), dimensionRange.getEndOffset());
    List<GradleEditorSourceBinding> sourceBindings = Lists.newArrayList();
    if (dependencies.isEmpty()
        || dependencies.size() > 1
        // There is a possible definition like 'my-group:my-artifact:$version' - we don't want to count referenced version as
        // an actual value definition here.
        || !GradleEditorModelUtil.isVariable(dimensionValue, dependencies.get(0).name)) {
      TextRange rangeToUse = assignment.value.location.range.cutOut(dimensionRange);
      if (dimensionValue.startsWith("${") && dimensionValue.endsWith("}") && !"${}".equals(dimensionValue)) {
        // There is a possible case that dependency coordinate is defined like "my-group:my-artifact:${var1 + var2}". We want
        // to point to the 'var1 + var2' instead of '${var1 + var2}' then.
        rangeToUse = TextRange.create(rangeToUse.getStartOffset() + "${".length(), rangeToUse.getEndOffset() - "}".length());
        dimensionValue = "";
      }
      Location dimensionLocation = new Location(assignment.value.location.file, rangeToUse);
      GradleEditorSourceBinding sourceBinding = buildSourceBinding(dimensionLocation, context.getProject());
      if (sourceBinding == null) {
        return null;
      }
      sourceBindings.add(sourceBinding);
    }
    else {
      dimensionValue = "";
    }
    GradleEditorSourceBinding dimensionLocation = buildSourceBinding(new Location(assignment.lValueLocation.file, dimensionRangeInFile),
                                                                     context.getProject());
    if (dimensionLocation == null) {
      return null;
    }
    Set<GradleEditorEntityMetaData> metaData = assignment.lValueLocation.file.equals(context.getTargetFile())
                                               ? Collections.<GradleEditorEntityMetaData>emptySet()
                                               : Collections.singleton(StdGradleEditorEntityMetaData.INJECTED);
    if (dependencies.isEmpty()) {
      return new DependencyDimension(dimensionValue, sourceBindings, dimensionLocation, metaData);
    }
    GradleEditorModelUtil.EntityInfo entityInfo = GradleEditorModelUtil.collectInfo(dependencies, context, null);
    String valueToUse = sourceBindings.isEmpty() ? entityInfo.value : "";
    sourceBindings.addAll(entityInfo.sourceBindings);
    return new DependencyDimension(valueToUse, sourceBindings, dimensionLocation, metaData);
  }

  private static class DependencyDimension {
    @NotNull public final String value;
    @NotNull public final List<GradleEditorSourceBinding> sourceBindings;
    @NotNull public final GradleEditorSourceBinding declarationValueLocation;
    @NotNull public final Set<GradleEditorEntityMetaData> metaData;

    DependencyDimension(@NotNull String value,
                        @NotNull List<GradleEditorSourceBinding> sourceBindings,
                        @NotNull GradleEditorSourceBinding declarationValueLocation,
                        @NotNull Set<GradleEditorEntityMetaData> metaData) {
      this.value = value;
      this.sourceBindings = sourceBindings;
      this.declarationValueLocation = declarationValueLocation;
      this.metaData = metaData;
    }
  }
}
