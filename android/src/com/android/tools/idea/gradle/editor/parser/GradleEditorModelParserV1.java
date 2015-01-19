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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.editor.entity.*;
import com.android.tools.idea.gradle.editor.metadata.GradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.metadata.StdGradleEditorEntityMetaData;
import com.android.tools.idea.gradle.editor.value.BuildToolsValueManager;
import com.android.tools.idea.gradle.editor.value.LibraryVersionsManager;
import com.android.tools.idea.gradle.editor.value.SdkValueManager;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.tools.idea.gradle.editor.parser.GradleEditorDsl.*;
import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelParseContext.*;
import static com.android.tools.idea.gradle.editor.parser.GradleEditorModelUtil.buildSourceBinding;

public class GradleEditorModelParserV1 implements GradleEditorModelParser {

  private static final Logger LOG = Logger.getInstance(GradleEditorModelParserV1.class);

  /**
   * Qualifier for a top-level variable.
   */
  private static final List<String> PROJECT_QUALIFIER = Collections.emptyList();

  @NotNull
  @Override
  public GradleCoordinate getMinSupportedAndroidGradlePluginVersion() {
    return GradleCoordinate.parseVersionOnly("0");
  }

  @NotNull
  @Override
  public GradleCoordinate getMaxSupportedAndroidGradlePluginVersion() {
    return GradleCoordinate.parseVersionOnly("100");
  }

  @NotNull
  @Override
  public List<GradleEditorEntityGroup> buildEntities(@NotNull GradleEditorModelParseContext context) {
    List<GradleEditorEntityGroup> result = Lists.newArrayList();

    GradleEditorEntityGroup buildConfiguration = buildConfiguration(context);
    if (buildConfiguration != null) {
      result.add(buildConfiguration);
    }

    GradleEditorEntityGroup dependencies = dependencies(context);
    if (dependencies != null) {
      result.add(dependencies);
    }
    GradleEditorEntityGroup repositories = repositories(context);
    if (repositories != null) {
      result.add(repositories);
    }
    for (GradleEditorModelCustomizer customizer : GradleEditorModelCustomizer.EP_NAME.getExtensions()) {
      result = customizer.postProcess(result, context);
    }
    return result;
  }

  @Nullable
  protected static GradleEditorEntityGroup combine(@NotNull String sectionText, @NotNull GradleEditorEntity... childEntities) {
    GradleEditorEntityGroup result = null;
    for (GradleEditorEntity childEntity : childEntities) {
      if (childEntity == null) {
        continue;
      }
      if (result == null) {
        result = new GradleEditorEntityGroup(sectionText);
      }
      result.addEntity(childEntity);
    }
    return result;
  }

  @Nullable
  protected GradleEditorEntityGroup buildConfiguration(@NotNull GradleEditorModelParseContext context) {
    return combine(AndroidBundle.message("android.gradle.editor.header.build"), buildGradlePluginVersion(context),
                   buildCompileSdkVersion(context), buildBuildSdkVersion(context));
  }

  @Nullable
  public static VersionGradleEditorEntity buildGradlePluginVersion(@NotNull final GradleEditorModelParseContext context) {
    final Variable variable = new Variable(CLASSPATH_CONFIGURATION, PROJECT_QUALIFIER);
    GradleEditorDependencyParser dependencyParser = new GradleEditorDependencyParser();
    for (Assignment assignment : context.getAssignments(variable)) {
      GradleEditorEntity entity = dependencyParser.parse(assignment, context);
      if (!(entity instanceof ExternalDependencyGradleEditorEntity)) {
        continue;
      }
      ExternalDependencyGradleEditorEntity e = (ExternalDependencyGradleEditorEntity)entity;
      int i = SdkConstants.GRADLE_PLUGIN_NAME.indexOf(':');
      String groupId = SdkConstants.GRADLE_PLUGIN_NAME.substring(0, i);
      String artifactId = SdkConstants.GRADLE_PLUGIN_NAME.substring(i + 1);
      if (artifactId.endsWith(":")) {
        artifactId = artifactId.substring(0, artifactId.length() - 1);
      }
      if (!groupId.equals(e.getGroupId()) || !artifactId.equals(e.getArtifactId())) {
        continue;
      }
      final Set<GradleEditorEntityMetaData> metaData = Sets.newHashSet(e.getMetaData());
      if (!e.getMetaData().contains(StdGradleEditorEntityMetaData.INJECTED)) {
        // We consider that gradle plugin entity is injected if it's declared in a parent file.
        metaData.add(StdGradleEditorEntityMetaData.OUTGOING);
      }
      metaData.remove(StdGradleEditorEntityMetaData.REMOVABLE);
      return new VersionGradleEditorEntity(AndroidBundle.message("android.gradle.editor.version.gradle.plugin"),
                                           e.getVersionSourceBindings(), e.getEntityLocation(), metaData, e.getDeclarationValueLocation(),
                                           e.getVersion(), new LibraryVersionsManager(groupId, artifactId),
                                           "http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Simple-build-files");
    }
    return null;
  }

  @Nullable
  protected GradleEditorEntity buildCompileSdkVersion(@NotNull GradleEditorModelParseContext context) {
    SimpleEntityData entityData = buildSimpleEntityData(new Variable(COMPILE_SDK_VERSION, PROJECT_QUALIFIER), context, null);
    if (entityData == null) {
      return null;
    }
    return new VersionGradleEditorEntity(AndroidBundle.message("android.gradle.editor.version.sdk.compile"), entityData.definitionValueSourceBindings,
                                         entityData.wholeEntityLocation, entityData.metaData, entityData.declarationValueLocation,
                                         entityData.value, new SdkValueManager(), null);
  }

  @Nullable
  protected GradleEditorEntity buildBuildSdkVersion(@NotNull GradleEditorModelParseContext context) {
    SimpleEntityData entityData = buildSimpleEntityData(new Variable(BUILD_TOOLS_VERSION, PROJECT_QUALIFIER), context, null);
    if (entityData == null) {
      return null;
    }
    return new VersionGradleEditorEntity(AndroidBundle.message("android.gradle.editor.version.build.tools"), entityData.definitionValueSourceBindings,
                                         entityData.wholeEntityLocation, entityData.metaData, entityData.declarationValueLocation,
                                         entityData.value, new BuildToolsValueManager(), null);
  }

  /**
   * Enhances {@link GradleEditorModelUtil#collectInfo(Collection, GradleEditorModelParseContext, GradleEditorModelUtil.AssignmentFilter)}
   * in a way to add meta-data to it.
   *
   * @param lValue           target lvalue
   * @param context          current context
   * @param assignmentFilter assignment filter to use
   * @return target entity data holder if the data is successfully extracted; <code>null</code> otherwise
   */
  @Nullable
  protected static SimpleEntityData buildSimpleEntityData(@NotNull Variable lValue,
                                                          @NotNull GradleEditorModelParseContext context,
                                                          @Nullable GradleEditorModelUtil.AssignmentFilter assignmentFilter) {
    Assignment targetAssignment = null;
    for (Assignment assignment : context.getAssignments(lValue)) {
      Assignment assignmentToUse = assignmentFilter == null ? assignment : assignmentFilter.check(assignment);
      if (assignmentToUse == null) {
        continue;
      }
      if (targetAssignment == null) {
        targetAssignment = assignmentToUse;
      }
      else {
        LOG.warn(String
                   .format("More than one assignment for the same l-value (%s) is found at the target file (%s): '%s' and '%s',", lValue,
                           context.getTargetFile().getPath(), targetAssignment, assignmentToUse));
        return null;
      }
    }
    if (targetAssignment == null) {
      return null;
    }
    GradleEditorSourceBinding declarationValueLocation = buildSourceBinding(targetAssignment.rValueLocation, context.getProject());
    if (declarationValueLocation == null) {
      return null;
    }
    GradleEditorSourceBinding wholeEntityLocation = buildSourceBinding(targetAssignment, context.getProject());
    if (wholeEntityLocation == null) {
      return null;
    }
    GradleEditorModelUtil.EntityInfo entityInfo =
      GradleEditorModelUtil.collectInfo(Collections.singleton(lValue), context, assignmentFilter);
    if (entityInfo.sourceBindings.isEmpty()) {
      return null;
    }
    Set<GradleEditorEntityMetaData> metaData = buildMetaData(targetAssignment, context);
    return new SimpleEntityData(entityInfo.sourceBindings, metaData, entityInfo.value, wholeEntityLocation, declarationValueLocation);
  }

  @Nullable
  protected static SimpleEntityData buildSimpleEntityData(@NotNull Assignment assignment, @NotNull GradleEditorModelParseContext context) {
    GradleEditorSourceBinding declarationValueLocation = buildSourceBinding(assignment.rValueLocation, context.getProject());
    if (declarationValueLocation == null) {
      return null;
    }
    GradleEditorSourceBinding wholeEntityLocation = buildSourceBinding(assignment, context.getProject());
    if (wholeEntityLocation == null) {
      return null;
    }
    Set<GradleEditorEntityMetaData> metaData = buildMetaData(assignment, context);
    if (assignment.dependencies.isEmpty()) {
      return new SimpleEntityData(Collections.singleton(declarationValueLocation), metaData,
                                  assignment.value == null ? "" : assignment.value.value, wholeEntityLocation, declarationValueLocation);
    }
    GradleEditorModelUtil.EntityInfo info = GradleEditorModelUtil.collectInfo(assignment.dependencies.asMap().keySet(), context, null);
    List<GradleEditorSourceBinding> definitionValueSourceBindings = Lists.newArrayList(info.sourceBindings);
    if (assignment.value != null && assignment.value.value.isEmpty()) {
      definitionValueSourceBindings.add(declarationValueLocation);
    }
    String valueToUse = assignment.value == null ? info.value : assignment.value.value;
    return new SimpleEntityData(definitionValueSourceBindings, metaData, valueToUse , wholeEntityLocation, declarationValueLocation);
  }

  @NotNull
  protected static Set<GradleEditorEntityMetaData> buildMetaData(@NotNull Assignment assignment,
                                                                 @NotNull GradleEditorModelParseContext context) {
    Set<GradleEditorEntityMetaData> result = Sets.newHashSet();
    if (context.getTargetFile().equals(assignment.lValueLocation.file)) {
      if (!assignment.codeStructure.isEmpty()) {
        String topSection = assignment.codeStructure.get(0);
        if (ALL_PROJECTS_SECTION.equals(topSection) || SUB_PROJECT_SECTION.equals(topSection)) {
          result.add(StdGradleEditorEntityMetaData.OUTGOING);
        }
      }
    }
    else {
      result.add(StdGradleEditorEntityMetaData.INJECTED);
    }
    return result;
  }

  @Nullable
  protected GradleEditorEntityGroup dependencies(@NotNull GradleEditorModelParseContext context) {
    List<GradleEditorEntity> dependencies = Lists.newArrayList();

    GradleEditorDependencyParser dependencyParser = new GradleEditorDependencyParser();
    Collection<Assignment> currentDependencies = context.getAssignments(Collections.singletonList(DEPENDENCIES_SECTION));
    Collection<Assignment> subProjectDependencies = context.getAssignments(Lists.newArrayList(SUB_PROJECT_SECTION, DEPENDENCIES_SECTION));
    Collection<Assignment> allProjectDependencies = context.getAssignments(Lists.newArrayList(ALL_PROJECTS_SECTION, DEPENDENCIES_SECTION));
    for (Assignment assignment : Iterables.concat(currentDependencies, subProjectDependencies, allProjectDependencies)) {
      GradleEditorEntity entity = dependencyParser.parse(assignment, context);
      if (entity != null) {
        dependencies.add(entity);
      }
    }

    if (dependencies.isEmpty()) {
      return null;
    }
    String headerText = AndroidBundle.message("android.gradle.editor.header.dependencies");
    return combine(headerText, Iterables.toArray(dependencies, GradleEditorEntity.class));
  }

  @Nullable
  protected GradleEditorEntityGroup repositories(@NotNull GradleEditorModelParseContext context) {
    List<GradleEditorEntity> repositories = Lists.newArrayList();

    Collection<List<String>> requestKeys = Lists.newArrayList();
    requestKeys.add(Arrays.asList(BUILD_SCRIPT_SECTION, REPOSITORIES_SECTION)); // Current public repo
    requestKeys.add(Arrays.asList(BUILD_SCRIPT_SECTION, REPOSITORIES_SECTION, MAVEN_REPO)); // Current third-party repo
    requestKeys.add(Arrays.asList(SUB_PROJECT_SECTION, REPOSITORIES_SECTION)); // Outgoing public repo
    requestKeys.add(Arrays.asList(SUB_PROJECT_SECTION, REPOSITORIES_SECTION, MAVEN_REPO)); // Outgoing third-party repo
    requestKeys.add(Arrays.asList(ALL_PROJECTS_SECTION, REPOSITORIES_SECTION)); // 'All-projects' public repo
    requestKeys.add(Arrays.asList(ALL_PROJECTS_SECTION, REPOSITORIES_SECTION, MAVEN_REPO)); // 'All-projects' third-party repo

    Collection<Assignment> assignments = Lists.newArrayList();
    for (List<String> key : requestKeys) {
      assignments.addAll(context.getAssignments(key));
    }
    for (Assignment assignment : assignments) {
      if (assignment.value == null) {
        continue;
      }
      if (!context.getTargetFile().equals(assignment.lValueLocation.file) && !assignment.codeStructure.isEmpty()) {
        String topSection = assignment.codeStructure.get(0);
        if (!ALL_PROJECTS_SECTION.equals(topSection) && !SUB_PROJECT_SECTION.equals(topSection)) {
          // Repositories declared at the parent's 'buildscript' section are not visible to the child projects.
          continue;
        }
      }
      if (NO_ARGS_METHOD_ASSIGNMENT_VALUE.equals(assignment.value.value)) {
        String name = null;
        String value = null;
        String helpId = null;
        if (MAVEN_CENTRAL.equals(assignment.lValue.name)) {
          name = AndroidBundle.message("android.gradle.editor.header.repository.maven");
          value = GradleEditorRepositoryEntity.MAVEN_CENTRAL_URL;
          helpId = GradleEditorRepositoryEntity.MAVEN_CENTRAL_HELP_ID;
        }
        else if (JCENTER.equals(assignment.lValue.name)) {
          name = AndroidBundle.message("android.gradle.editor.header.repository.jcenter");
          value = GradleEditorRepositoryEntity.JCENTER_URL;
          helpId = GradleEditorRepositoryEntity.JCENTER_HELP_ID;
        }
        if (name != null) {
          Set<GradleEditorEntityMetaData> metaData = buildMetaData(assignment, context);
          metaData.add(StdGradleEditorEntityMetaData.READ_ONLY);
          GradleEditorSourceBinding sourceBinding = buildSourceBinding(assignment, context.getProject());
          if (sourceBinding != null) {
            repositories.add(
              new GradleEditorRepositoryEntity(name, value, Collections.<GradleEditorSourceBinding>emptyList(), sourceBinding, metaData,
                                               sourceBinding, helpId));
          }
        }
        continue;
      }

      if (!MAVEN_REPO_URL.equals(assignment.lValue.name)) {
        continue;
      }
      SimpleEntityData data = buildSimpleEntityData(assignment, context);
      if (data != null) {
        repositories.add(
          new GradleEditorRepositoryEntity(AndroidBundle.message("android.gradle.editor.header.repository.third.party"), data.value,
                                           data.definitionValueSourceBindings, data.wholeEntityLocation, data.metaData, data.declarationValueLocation,
                                           GradleEditorRepositoryEntity.MAVEN_GENERIC_HELP_ID));
      }
    }

    if (repositories.isEmpty()) {
      return null;
    }
    String headerText = AndroidBundle.message("android.gradle.editor.header.repositories");
    return combine(headerText, Iterables.toArray(repositories, GradleEditorEntity.class));
  }

  /**
   * Parameter object used to hold intermediate data for building {@link GradleEditorEntity} object.
   */
  private static class SimpleEntityData {

    @NotNull final Collection<GradleEditorSourceBinding> definitionValueSourceBindings;
    @NotNull final Set<GradleEditorEntityMetaData> metaData;
    @NotNull final String value;
    @NotNull final GradleEditorSourceBinding wholeEntityLocation;
    @NotNull final GradleEditorSourceBinding declarationValueLocation;

    SimpleEntityData(@NotNull Collection<GradleEditorSourceBinding> definitionValueSourceBindings,
                     @NotNull Set<GradleEditorEntityMetaData> metaData,
                     @NotNull String value,
                     @NotNull GradleEditorSourceBinding wholeEntityLocation,
                     @NotNull GradleEditorSourceBinding declarationValueLocation) {
      this.definitionValueSourceBindings = definitionValueSourceBindings;
      this.metaData = metaData;
      this.value = value;
      this.wholeEntityLocation = wholeEntityLocation;
      this.declarationValueLocation = declarationValueLocation;
    }
  }
}
