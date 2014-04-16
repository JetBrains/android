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
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade;
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

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

  public static String mergeGradleFiles(@NotNull String source, @NotNull String dest, @Nullable Project project) {
    final Project project2 = project == null ? ProjectManager.getInstance().getDefaultProject() : project;

    final GroovyFile templateBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                      GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                      source);
    final GroovyFile existingBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                      GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                      dest);
    return (new WriteCommandAction<String>(project, "Merge Gradle Files", existingBuildFile) {
      @Override
      protected void run(@NotNull Result<String> result) throws Throwable {
        mergePsi(templateBuildFile, existingBuildFile, project2);
        result.setResult(CodeStyleManager.getInstance(project2).reformat(existingBuildFile).getText());
      }
    }).execute().getResultObject();
  }

  private static void mergePsi(@NotNull PsiElement fromRoot, @NotNull PsiElement toRoot, @NotNull Project project) {
    Set<PsiElement> destinationChildren = new HashSet<PsiElement>();
    destinationChildren.addAll(Arrays.asList(toRoot.getChildren()));

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

    // Load existing dependencies into the map for the existing build.gradle
    pullDependenciesIntoMap(toRoot, dependencies);

    // Load dependencies into the map for the new build.gradle
    boolean newDependenciesIncoming = pullDependenciesIntoMap(fromRoot, dependencies);

    if (!newDependenciesIncoming) {
      return;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    PsiElementFactory baseFactory = JavaPsiFacade.getElementFactory(project);

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    for (String key : dependencies.keySet()) {
      GradleCoordinate highest = Collections.max(dependencies.get(key), COMPARE_PLUS_LOWER);

      // If this coordinate points to an artifact in one of our repositories, mark it will a comment if they don't
      // have that repositiory available.
      if (RepositoryUrlManager.supports(highest.getArtifactId())) {
        GradleCoordinate available = GradleCoordinate.parseCoordinateString(
          urlManager.getLibraryCoordinate(highest.getArtifactId()));

        File archiveFile = urlManager.getArchiveForCoordinate(highest);
        if (archiveFile == null || !archiveFile.exists() || COMPARE_PLUS_LOWER.compare(available, highest) < 0) {
          PsiElement comment = baseFactory.createCommentFromText(RepositoryUrlManager.getHelpComment(highest), null);
          toRoot.addBefore(comment, toRoot.getLastChild());
        }

        PsiElement dependencyElement = factory.createStatementFromText(String.format(COMPILE_FORMAT, highest.toString()));
        toRoot.addBefore(dependencyElement, toRoot.getLastChild());
      }
    }
  }

  /**
   * Looks for 'compile "*"' statements and tries to parse them into Gradle coordinates. If successful,
   * adds the new coordinate to the map and removes the corresponding PsiElement from the tree.
   * @return true if new items were added to the map
   */
  private static boolean pullDependenciesIntoMap(@NotNull PsiElement root, Multimap<String, GradleCoordinate> map) {
    boolean wasMapUpdated = false;
    for (PsiElement existingElem : root.getChildren()) {
      if (existingElem instanceof GrCall) {
        PsiElement reference = existingElem.getFirstChild();
        if (reference instanceof GrReferenceExpression && reference.getText().equalsIgnoreCase(COMPILE)) {
          PsiElement arguments = existingElem.getLastChild();
          if (arguments.getChildren().length == 1 && arguments.getFirstChild() instanceof PsiLiteral) {
            String coordinateText = arguments.getFirstChild().getText();
            if (StringUtil.isQuotedString(coordinateText)) {
              coordinateText = StringUtil.stripQuotesAroundValue(coordinateText);
            }
            GradleCoordinate coordinate = GradleCoordinate.parseCoordinateString(coordinateText);
            if (coordinate != null && !map.get(coordinate.getId()).contains(coordinate)) {
              map.put(coordinate.getId(), coordinate);
              existingElem.delete();
              wasMapUpdated = true;
            }
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
