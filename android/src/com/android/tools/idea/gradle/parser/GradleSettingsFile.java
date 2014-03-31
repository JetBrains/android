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
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.io.File;
import java.util.Arrays;
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
  private static final Iterable<String> EMPTY_ITERABLE = Arrays.asList(new String[] {});

  @Nullable
  public static GradleSettingsFile get(Project project) {
    if (project.isDefault()) {
      return null;
    }
    VirtualFile baseDir = project.getBaseDir();
    assert baseDir != null;
    VirtualFile settingsFile = baseDir.findFileByRelativePath(SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsFile != null) {
      return new GradleSettingsFile(settingsFile, project);
    } else {
      LOG.warn("Unable to find settings.gradle file for project " + project.getName());
      return null;
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
      addModule(moduleGradlePath);
    }
  }

  /**
   * Adds a reference to the module to the settings file, if there is not already one. The module path must be colon separated, with a
   * leading colon, e.g. ":project:subproject". Must be run inside a write action.
   */
  public void addModule(@NotNull String modulePath) {
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
      if (argList != null) {
        GrLiteral literal = GroovyPsiElementFactory.getInstance(myProject).createLiteralFromValue(modulePath);
        argList.addAfter(literal, argList.getLastChild());
        return;
      }
    }
    GrStatement statement =
      GroovyPsiElementFactory.getInstance(myProject).createStatementFromText(INCLUDE_METHOD + " '" + modulePath + "'");
    myGroovyFile.add(statement);
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
    for (GrMethodCall includeStatement : getMethodCalls(myGroovyFile, INCLUDE_METHOD)) {
      for (GrLiteral lit : getLiteralArguments(includeStatement)) {
        if (modulePath.equals(lit.getValue())) {
          lit.delete();
          if (getArguments(includeStatement).length == 0) {
            includeStatement.delete();
            // If this happens we will fall through both for loops before we get into iteration trouble. We want to keep iterating in
            // case the module is added more than once (via hand-editing of the file).
          }
        }
      }
    }
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
    Iterable<GrAssignmentExpression> assignments = Iterables.filter(Arrays.asList(myGroovyFile.getChildren()), GrAssignmentExpression.class);
    for (GrAssignmentExpression assignment : assignments) {
      String project = getProjectName(assignment.getLValue());
      GrExpression value = assignment.getRValue();
      if (project != null && moduleLocations.containsKey(project)) {
        File location = getProjectLocation(value);
        if (location != null) {
          moduleLocations.put(project, location);
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
    AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
    if (androidGradleFacet == null) {
      return null;
    }
    return androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
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
