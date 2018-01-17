/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * GradleSettingsFile uses PSI to parse settings.gradle files and provides high-level methods to read and mutate the file.
 *
 * Note that if you do any mutations on the PSI structure you must be inside a write action. See
 * {@link com.intellij.util.ActionRunner#runInsideWriteAction}.
 */
public class GradleSettingsFile extends GradleGroovyFile {
  private static final Logger LOG = Logger.getInstance(GradleGroovyFile.class.getName());

  public static final String INCLUDE_METHOD = "include";
  public static final String CUSTOM_LOCATION_FORMAT = "project('%1$s').projectDir = new File('%2$s')";
  private static final Iterable<String> EMPTY_ITERABLE = Arrays.asList(new String[] {});

  /**
   * Returns a handle to settings.gradle in project root or creates a new file if one does not already exist.
   * <p/>
   * This function should be called from within a write action even if the file exists. Use
   * {@link #get(com.intellij.openapi.project.Project)} to open a file for reading.
   */
  @NotNull
  public static GradleSettingsFile getOrCreate(Project project) throws IOException {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (project.isDefault()) {
      throw new IOException("Not a real project");
    }
    final VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    VirtualFile settingsFile = baseDir.findFileByRelativePath(SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFile == null) {
      settingsFile = baseDir.createChildData(project, SdkConstants.FN_SETTINGS_GRADLE);
      VfsUtil.saveText(settingsFile, "");
    }
    return new GradleSettingsFile(settingsFile, project);
  }

  @Nullable
  public static GradleSettingsFile get(Project project) {
    if (project.isDefault()) {
      return null;
    }

    VirtualFile baseDir = project.getBaseDir();
    VirtualFile settingsFile = baseDir == null ? null : baseDir.findFileByRelativePath(SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFile == null) {
      LOG.warn("Unable to find settings.gradle file for project " + project.getName());
      return null;
    }
    else {
      return new GradleSettingsFile(settingsFile, project);
    }
  }

  public GradleSettingsFile(@NotNull VirtualFile file, @NotNull Project project) {
    super(file, project);
  }

  /**
   * Adds a reference to the module to the settings file, if there is not already one. Must be run inside a write action.
   */
  public void addModule(@NotNull Module module) {
    checkInitialized();
    String moduleGradlePath = getModuleGradlePath(module);
    if (moduleGradlePath != null) {
      VirtualFile moduleFile = module.getModuleFile();
      assert moduleFile != null;
      addModule(moduleGradlePath, VfsUtilCore.virtualToIoFile(moduleFile.getParent()));
    }
  }

  /**
   * Adds a reference to the module to the settings file, if there is not already one. The module path must be colon separated, with a
   * leading colon, e.g. ":project:subproject". Must be run inside a write action.
   *
   * If the file does not match the default module location, this method will override the location.
   */
  public void addModule(@NotNull String modulePath, @NotNull File location) {
    checkInitialized();
    commitDocumentChanges();
    for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
      for (GrLiteral lit : getLiteralArguments(includeStatement)) {
        if (modulePath.equals(lit.getValue())) {
          return;
        }
      }
    }
    GrMethodCall includeStatement = getMethodCall(myGroovyFile, INCLUDE_METHOD);
    if (includeStatement != null) {
      GrArgumentList argList = includeStatement.getArgumentList();
      GrLiteral literal = GroovyPsiElementFactory.getInstance(myProject).createLiteralFromValue(modulePath);
      argList.addAfter(literal, argList.getLastChild());
    } else {
      GrStatement statement =
        GroovyPsiElementFactory.getInstance(myProject).createStatementFromText(INCLUDE_METHOD + " '" + modulePath + "'");
      myGroovyFile.add(statement);
    }
    // We get location relative to this file parent
    VirtualFile parent = getFile().getParent();
    File defaultLocation = GradleUtil.getModuleDefaultPath(parent, modulePath);
    if (!FileUtil.filesEqual(defaultLocation, location)) {
      final String path;
      File parentFile = VfsUtilCore.virtualToIoFile(parent);
      if (FileUtil.isAncestor(parentFile, location, true)) {
        path = PathUtil.toSystemIndependentName(FileUtil.getRelativePath(parentFile, location));
      }
      else {
        path = PathUtil.toSystemIndependentName(location.getAbsolutePath());
      }
      String locationAssignment = String.format(CUSTOM_LOCATION_FORMAT, modulePath, path);
      GrStatement locationStatement = GroovyPsiElementFactory.getInstance(myProject).createStatementFromText(locationAssignment);
      myGroovyFile.add(locationStatement);
    }
  }

  /**
   * Removes the reference to the module from the settings file, if present. Must be run inside a write action.
   */
  public void removeModule(@NotNull Module module) {
    checkInitialized();
    String moduleGradlePath = getModuleGradlePath(module);
    if (moduleGradlePath != null) {
      removeModule(moduleGradlePath);
    }
  }

  /**
   * Removes the reference to the module from the settings file, if present. The module path must be colon separated, with a
   * leading colon, e.g. ":project:subproject". Must be run inside a write action.
   */
  public void removeModule(@NotNull String modulePath) {
    checkInitialized();
    commitDocumentChanges();
    boolean removedAnyIncludes = false;
    for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
      for (GrLiteral lit : getLiteralArguments(includeStatement)) {
        if (modulePath.equals(lit.getValue())) {
          lit.delete();
          removedAnyIncludes = true;
          if (getArguments(includeStatement).length == 0) {
            includeStatement.delete();
            // If this happens we will fall through both for loops before we get into iteration trouble. We want to keep iterating in
            // case the module is added more than once (via hand-editing of the file).
          }
        }
      }
    }
    if (removedAnyIncludes) {
      for (Pair<String, GrAssignmentExpression> pair : getAllProjectLocationStatements()) {
        if (modulePath.equals(pair.first)) {
          pair.second.delete();
        }
      }
    }
  }


  /**
   * Get the reference to the module from the settings file with new name if present.
   */
  @Nullable
  public GrLiteral findModuleReference(@NotNull Module module) {
    checkInitialized();
    String moduleGradlePath = getModuleGradlePath(module);
    if (moduleGradlePath != null) {
      commitDocumentChanges();
      for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
        for (GrLiteral lit : getLiteralArguments(includeStatement)) {
          if (moduleGradlePath.equals(lit.getValue())) {
            return lit;
          }
        }
      }
    }
    return null;
  }

  private Iterable<Pair<String, GrAssignmentExpression>> getAllProjectLocationStatements() {
    List<PsiElement> allStatements = Arrays.asList(myGroovyFile.getChildren());
    Iterable<GrAssignmentExpression> assignments = Iterables.filter(allStatements, GrAssignmentExpression.class);
    return FluentIterable.from(assignments).transform(new Function<GrAssignmentExpression, Pair<String, GrAssignmentExpression>>() {
      @Override
      public Pair<String, GrAssignmentExpression> apply(GrAssignmentExpression assignment) {
        String projectName = getProjectName(assignment.getLValue());
        return projectName == null ? null : Pair.create(projectName, assignment);
      }
    }).filter(Predicates.notNull());
  }

  /**
   * Returns all of the literal-typed arguments of all include statements in the file.
   */
  public Iterable<String> getModules() {
    checkInitialized();
    return Iterables.concat(Iterables.transform(getMethodCalls(myGroovyFile, INCLUDE_METHOD),
                                                new Function<GrMethodCall, Iterable<String>>() {
      @Override
      public Iterable<String> apply(@Nullable GrMethodCall input) {
        if (input != null) {
          return Iterables.transform(getLiteralArgumentValues(input), new Function<Object, String>() {
            @Override
            public String apply(@Nullable Object input) {
              if (input == null) {
                return null;
              }
              String value = input.toString();
              // We treat all paths in settings.gradle as being absolute.
              if (!value.startsWith(SdkConstants.GRADLE_PATH_SEPARATOR)) {
                value = SdkConstants.GRADLE_PATH_SEPARATOR + value;
              }
              return value;
            }
          });
        } else {
          return EMPTY_ITERABLE;
        }
      }
    }));
  }

  /**
   * Parses settings.gradle and obtains module locations. Paths are not validated (e.g. may point to non-existing location or to file
   * instead of directory) and may be relative or absolute.
   *
   * @return a {@link java.util.Map} mapping module names to module locations.
   */
  @NotNull
  public Map<String, File> getModulesWithLocation() {
    checkInitialized();
    Map<String, File> moduleLocations = Maps.newHashMap();
    for (String module : getModules()) {
      Iterable<String> segments = Splitter.on(SdkConstants.GRADLE_PATH_SEPARATOR).omitEmptyStrings().split(module);
      String defaultLocation = Joiner.on(File.separator).join(segments);
      moduleLocations.put(module, new File(defaultLocation));
    }
    for (Pair<String, GrAssignmentExpression> pair : getAllProjectLocationStatements()) {
      if (moduleLocations.containsKey(pair.first)) {
        GrExpression value = pair.second.getRValue();
        File location = getProjectLocation(value);
        if (location != null) {
          moduleLocations.put(pair.first, location);
        }
      }
    }
    return moduleLocations;
  }

  /**
   * Obtains custom module location from the Gradle script. Currently it only recognizes File ctor invocation with a string constant.
   */
  @Nullable
  private static File getProjectLocation(@Nullable GrExpression rValue) {
    if (rValue instanceof GrNewExpression) {
      PsiType type = rValue.getType();
      String typeName = type != null ? type.getCanonicalText() : null;
      if (File.class.getName().equals(typeName)
          || File.class.getSimpleName().equals(typeName)) {
        String path = getSingleStringArgumentValue(((GrNewExpression)rValue));
        return path == null ? null : new File(path);
      }
    }
    return null;
  }

  @Nullable
  private static String getProjectName(GrExpression lValue) {
    if (lValue instanceof GrReferenceExpression) {
      GrReferenceExpression reference = (GrReferenceExpression)lValue;
      if ("projectDir".equals(reference.getCanonicalText())) {
        GrExpression qualifier = reference.getQualifier();
        if (qualifier instanceof GrMethodCall) {
          GrMethodCall methodCall = (GrMethodCall)qualifier;
          if ("project".equals(getMethodCallName(methodCall))) {
            return getSingleStringArgumentValue(methodCall);
          }
        }
      }
    }
    return null;
  }

  /**
   * Given a module, returns that module's path in Gradle colon-delimited format.
   */
  @Nullable
  public static String getModuleGradlePath(@NotNull Module module) {
    GradleFacet gradleFacet = GradleFacet.getInstance(module);
    if (gradleFacet == null) {
      return null;
    }
    return gradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
  }

  /**
   * Given a module path in Gradle colon-delimited format, returns the build.gradle file for that module
   * if it can be found, or null if it cannot.
   */
  @Nullable
  public GradleBuildFile getModuleBuildFile(@NotNull String moduleGradlePath) {
    Module module = GradleUtil.findModuleByGradlePath(myProject, moduleGradlePath);
    if (module != null) {
      VirtualFile buildFile = GradleUtil.getGradleBuildFile(module);
      if (buildFile != null) {
        return new GradleBuildFile(buildFile, myProject);
      }
    }
    return null;
  }

  /**
   * Returns true if there exists a build.gradle file for a module identified by the given module path.
   */
  public boolean hasBuildFile(@NotNull String moduleGradlePath) {
    Module module = GradleUtil.findModuleByGradlePath(myProject, moduleGradlePath);
    if (module == null) {
      return false;
    }
    VirtualFile gradleBuildFile = GradleUtil.getGradleBuildFile(module);
    return gradleBuildFile != null && gradleBuildFile.exists();
  }
}
