/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.gradle.eclipse.ImportModule;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import java.io.File;
import java.util.*;

import static com.android.ide.common.repository.GradleCoordinate.COMPARE_PLUS_LOWER;

/**
 * Utility class to help with merging Gradle files into one another
 */
public class GradleFileMerger {
  private static final String DEPENDENCIES = "dependencies";
  private static final String COMPILE = "compile";
  public static final String COMPILE_FORMAT = "compile '%s'\n";
  private static String mSupportLibVersionFilter;

  /**
   * Merges the given source build.gradle content into the given destination build.gradle content,
   * and resolves and dynamic Gradle dependencies into specific versions. If a support library
   * filter is provided, the support libraries will be limited to match that filter. This is
   * typically set to the compileSdkVersion, such that you don't end up mixing and matching
   * compileSdkVersions and support libraries from different versions, which is not supported.
   */
  public static String mergeGradleFiles(@NotNull String source, @NotNull String dest, @Nullable Project project,
                                        @Nullable String supportLibVersionFilter) {
    mSupportLibVersionFilter = supportLibVersionFilter;
    source = source.replace("\r", "");
    dest = dest.replace("\r", "");
    final Project project2;
    boolean projectNeedsCleanup = false;
    if (project != null && !project.isDefault()) {
      project2 = project;
    } else {
      project2 = ((ProjectManagerImpl)ProjectManager.getInstance()).newProject("MergingOnly", "", false, true);
      assert project2 != null;
      ((StartupManagerImpl)StartupManager.getInstance(project2)).runStartupActivities();
      projectNeedsCleanup = true;
    }


    final GroovyFile templateBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                      GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                      source);
    final GroovyFile existingBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                      GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                      dest);
    String result = (new WriteCommandAction<String>(project2, "Merge Gradle Files", existingBuildFile) {
      @Override
      protected void run(@NotNull Result<String> result) throws Throwable {
        mergePsi(templateBuildFile, existingBuildFile, project2);
        PsiElement formatted = CodeStyleManager.getInstance(project2).reformat(existingBuildFile);
        result.setResult(formatted.getText());
      }
    }).execute().getResultObject();

    if (projectNeedsCleanup) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          Disposer.dispose(project2);
        }
      });
    }
    return result;
  }

  private static void mergePsi(@NotNull PsiElement fromRoot, @NotNull PsiElement toRoot, @NotNull Project project) {
    Set<PsiElement> destinationChildren = new HashSet<PsiElement>();
    destinationChildren.addAll(Arrays.asList(toRoot.getChildren()));

    // First try and do a string literal replacement.
    // If both toRoot and fromRoot are call expressions
    if (toRoot instanceof GrCallExpression && fromRoot instanceof GrCallExpression) {
      PsiElement[] fromArguments = fromRoot.getLastChild().getChildren();
      PsiElement[] toArguments = toRoot.getLastChild().getChildren();
      // and both have only one argument and that argument is a literal
      if (toArguments.length == 1 && fromArguments.length == 1 &&
          toArguments[0] instanceof GrLiteral && fromArguments[0] instanceof GrLiteral) {
        // End this branch by replacing the old literal with the new
        toArguments[0].replace(fromArguments[0]);
        return;
      }
    }

    // Do an element-wise (disregarding order) child comparison
    for (PsiElement child : fromRoot.getChildren()) {
      PsiElement destination = findEquivalentElement(destinationChildren, child);
      if (destination == null) {
        if (destinationChildren.isEmpty()) {
          toRoot.add(child);
        } else {
          toRoot.addBefore(child, toRoot.getLastChild());
        }
        // And we're done for this branch
      } else if (child.getFirstChild() != null && child.getFirstChild().getText().equalsIgnoreCase(DEPENDENCIES) &&
                 destination.getFirstChild() != null && destination.getFirstChild().getText().equalsIgnoreCase(DEPENDENCIES)) {
        // Special case dependencies
        // The last child of the dependencies method call is the closable block
        mergeDependencies(child.getLastChild(), destination.getLastChild(), project);
      } else {
        mergePsi(child, destination, project);
      }
    }
  }

  private static void mergeDependencies(@NotNull PsiElement fromRoot, @NotNull PsiElement toRoot, @NotNull Project project) {
    Multimap<String, GradleCoordinate> dependencies = LinkedListMultimap.create();
    List<String> unparseableDependencies = new ArrayList<String>();

    // Load existing dependencies into the map for the existing build.gradle
    pullDependenciesIntoMap(toRoot, dependencies, null);

    // Load dependencies into the map for the new build.gradle
    pullDependenciesIntoMap(fromRoot, dependencies, unparseableDependencies);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    for (String key : dependencies.keySet()) {
      GradleCoordinate highest = Collections.max(dependencies.get(key), COMPARE_PLUS_LOWER);

      // For test consistency, don't depend on installed SDK state while testing
      if (!ApplicationManager.getApplication().isUnitTestMode() || Boolean.getBoolean("force.gradlemerger.repository.check")) {
        // If this coordinate points to an artifact in one of our repositories, check to see if there is a static version
        // that we can add instead of a plus revision.
        if (RepositoryUrlManager.supports(highest.getArtifactId())) {
          String filter = highest.getGroupId() != null && ImportModule.SUPPORT_GROUP_ID.equals(highest.getGroupId())
                          ? mSupportLibVersionFilter : null;
          String libraryCoordinate = urlManager.getLibraryCoordinate(highest.getArtifactId(), filter, false /* No previews */);
          if (libraryCoordinate == null && filter != null) {
            // No library found at the support lib version filter level, so look for any match
            libraryCoordinate = urlManager.getLibraryCoordinate(highest.getArtifactId(), null, false /* No previews */);
          }
          if (libraryCoordinate != null) {
            GradleCoordinate available = GradleCoordinate.parseCoordinateString(libraryCoordinate);
            if (available != null) {
              File archiveFile = urlManager.getArchiveForCoordinate(available);
              if (archiveFile != null && archiveFile.exists() && COMPARE_PLUS_LOWER.compare(available, highest) >= 0) {
                highest = available;
              }
            }
          }
        }
      }
      PsiElement dependencyElement = factory.createStatementFromText(String.format(COMPILE_FORMAT, highest.toString()));
      toRoot.addBefore(dependencyElement, toRoot.getLastChild());
    }
    for (String unparseableDependency : unparseableDependencies) {
      PsiElement dependencyElement = factory.createStatementFromText(unparseableDependency);
      toRoot.addBefore(dependencyElement, toRoot.getLastChild());
    }
  }

  /**
   * Looks for 'compile "*"' statements and tries to parse them into Gradle coordinates. If successful,
   * adds the new coordinate to the map and removes the corresponding PsiElement from the tree.
   * @return true if new items were added to the map
   */
  private static boolean pullDependenciesIntoMap(@NotNull PsiElement root, Multimap<String, GradleCoordinate> map,
                                                 @Nullable List<String> unparseableDependencies) {
    boolean wasMapUpdated = false;
    for (PsiElement existingElem : root.getChildren()) {
      if (existingElem instanceof GrCall) {
        PsiElement reference = existingElem.getFirstChild();
        if (reference instanceof GrReferenceExpression && reference.getText().equalsIgnoreCase(COMPILE)) {
          boolean parsed = false;
          GrCall call = (GrCall)existingElem;
          GrArgumentList arguments = call.getArgumentList();
          // Don't try merging dependencies if one of them has a closure block attached.
          if (arguments != null && call.getClosureArguments().length == 0) {
            GrExpression[] expressionArguments = arguments.getExpressionArguments();
            if (expressionArguments.length == 1 && expressionArguments[0] instanceof GrLiteral) {
              Object value = ((GrLiteral)expressionArguments[0]).getValue();
              if (value instanceof String) {
                String coordinateText = (String)value;
                GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateText);
                if (coordinate != null) {
                  parsed = true;
                  if (!map.get(coordinate.getId()).contains(coordinate)) {
                    map.put(coordinate.getId(), coordinate);
                    existingElem.delete();
                    wasMapUpdated = true;
                  }
                }
              }
            }
          }
          if (!parsed && unparseableDependencies != null) {
            unparseableDependencies.add(existingElem.getText());
          }
        }
      }
    }
    return wasMapUpdated;
  }

  /**
   * Finds an exact match if possible (and returns it) otherwise, looks for a unique "close" match (Defined as a matching
   * reference expression). If only one "close" match is found, then that match gets returned. Otherwise returns null.
   */
  @Nullable
  private static PsiElement findEquivalentElement(@NotNull Collection<PsiElement> collection, @NotNull PsiElement element) {
    List<PsiElement> matchingItems = Lists.newArrayListWithExpectedSize(1);
    for (PsiElement item : collection) {
      if (item.getText() != null && item.getText().equals(element.getText())) {
        return item;
      } else if (item.getFirstChild() != null && element.getFirstChild() != null) {
        if (item.getFirstChild().getText().equals(element.getFirstChild().getText())) {
          matchingItems.add(item);
        }
      }
    }
    if (matchingItems.size() == 1) {
      return matchingItems.get(0);
    } else {
      return null;
    }
  }
}
