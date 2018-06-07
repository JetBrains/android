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
import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.sdk.AndroidSdks;
import com.google.common.base.CharMatcher;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.templates.GradleFileMergers.CONFIGURATION_ORDERING;
import static com.android.tools.idea.templates.GradleFileMergers.removeExistingDependencies;

/**
 * Utility class to help with merging Gradle files into one another
 */
public class GradleFilePsiMerger {

  /**
   * Merges the given source build.gradle content into the given destination build.gradle content,
   * and resolves and dynamic Gradle dependencies into specific versions. If a support library
   * filter is provided, the support libraries will be limited to match that filter. This is
   * typically set to the compileSdkVersion, such that you don't end up mixing and matching
   * compileSdkVersions and support libraries from different versions, which is not supported.
   */
  public static String mergeGradleFiles(@NotNull String source, @NotNull String dest, @Nullable Project project,
                                        @Nullable final String supportLibVersionFilter) {
    source = source.replace("\r", "");
    dest = dest.replace("\r", "");
    final Project project2;
    boolean projectNeedsCleanup = false;
    if (project != null && !project.isDefault()) {
      project2 = project;
    }
    else {
      project2 = ((ProjectManagerImpl)ProjectManager.getInstance()).newProject("MergingOnly", "", false, true);
      assert project2 != null;
      ((StartupManagerImpl)StartupManager.getInstance(project2)).runStartupActivities();
      projectNeedsCleanup = true;
    }

    source = source.trim();
    dest = dest.trim();

    final GroovyFile templateBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                             GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                             source);
    final GroovyFile existingBuildFile = (GroovyFile)PsiFileFactory.getInstance(project2).createFileFromText(SdkConstants.FN_BUILD_GRADLE,
                                                                                                             GroovyFileType.GROOVY_FILE_TYPE,
                                                                                                             dest);
    String result = (new WriteCommandAction<String>(project2, "Merge Gradle Files", existingBuildFile) {
      @Override
      protected void run(@NotNull Result<String> result) throws Throwable {
        // Make sure that the file we are merging in to has a trailing new line. This ensures that
        // any added elements appear at the buttom of the file, it also keeps consistency with
        // how projects created with the Wizards look.
        addTrailingNewLine(existingBuildFile);
        mergePsi(templateBuildFile, existingBuildFile, project2, supportLibVersionFilter);
        PsiElement formatted = CodeStyleManager.getInstance(project2).reformat(existingBuildFile);
        result.setResult(formatted.getText());
      }
    }).execute().getResultObject();

    if (projectNeedsCleanup) {
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project2));
    }
    return result;
  }

  private static void mergePsi(@NotNull PsiElement fromRoot,
                               @NotNull PsiElement toRoot,
                               @NotNull Project project,
                               @Nullable String supportLibVersionFilter) {
    Set<PsiElement> destinationChildren = new HashSet<>();
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
        }
        else if (child instanceof GrAssignmentExpression && child.getChildren().length == 2 &&
                 child.getChildren()[1] instanceof GrLiteral && child.getText().startsWith("ext.")) {
          // Put assignment expressions like, ext.kotlin_version = 'x.x.x', at the top, as they may be used later on the file
          child = toRoot.addAfter(child, toRoot.getFirstChild());
          ensureCorrectSpacing(child);
        }
        else {
          child = toRoot.addBefore(child, toRoot.getLastChild());
          ensureCorrectSpacing(child);
        }
        // And we're done for this branch
      }
      else if (child.getFirstChild() != null && child.getFirstChild().getText().equalsIgnoreCase(GradleFileMergers.DEPENDENCIES) &&
               destination.getFirstChild() != null && destination.getFirstChild().getText().equalsIgnoreCase(GradleFileMergers.DEPENDENCIES)) {
        // Special case dependencies
        // The last child of the dependencies method call is the closable block
        mergeDependencies(child.getLastChild(), destination.getLastChild(), project, supportLibVersionFilter);
      }
      else {
        mergePsi(child, destination, project, supportLibVersionFilter);
      }
    }
  }

  private static void mergeDependencies(@NotNull PsiElement fromRoot,
                                        @NotNull PsiElement toRoot,
                                        @NotNull Project project,
                                        @Nullable String supportLibVersionFilter) {
    Map<String, Multimap<String, GradleCoordinate>> dependencies = new TreeMap<>(CONFIGURATION_ORDERING);
    final List<String> unparsedDependencies = Lists.newArrayList();

    // Load existing dependencies into a map for the existing build.gradle
    Map<String, Multimap<String, GradleCoordinate>> originalDependencies = Maps.newHashMap();
    final List<String> originalUnparsedDependencies = Lists.newArrayList();
    pullDependenciesIntoMap(toRoot, originalDependencies, originalUnparsedDependencies);

    // Load dependencies into a map for the new build.gradle
    pullDependenciesIntoMap(fromRoot, dependencies, unparsedDependencies);

    // filter out dependencies already met by existing build.gradle
    removeExistingDependencies(dependencies, originalDependencies);

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    RepositoryUrlManager urlManager = RepositoryUrlManager.get();

    AndroidSdkData sdk = AndroidSdks.getInstance().tryToChooseAndroidSdk();
    if (sdk != null) {
      dependencies.forEach((configurationName, unresolvedDependencies) -> {
        List<GradleCoordinate> resolved = urlManager.resolveDynamicSdkDependencies(unresolvedDependencies,
                                                                                   supportLibVersionFilter,
                                                                                   sdk,
                                                                                   FileOpUtils.create());
        PsiElement nextElement = findInsertionPoint(toRoot, configurationName);
        for (GradleCoordinate dependency : resolved) {
          PsiElement dependencyElement = factory.createStatementFromText(String.format("%s '%s'\n",
                                                                                       configurationName,
                                                                                       dependency.toString()));
          PsiElement newElement = toRoot.addBefore(dependencyElement, nextElement);
          toRoot.addAfter(factory.createLineTerminator(1), newElement);
        }
      });
    }

    // Unfortunately the "from" and "to" dependencies may not have the same white space formatting
    Set<String> originalSet = originalUnparsedDependencies.stream().map(CharMatcher.WHITESPACE::removeFrom).collect(Collectors.toSet());
    for (String dependency : unparsedDependencies) {
      if (!originalSet.contains(CharMatcher.WHITESPACE.removeFrom(dependency))) {
        PsiElement dependencyElement = factory.createStatementFromText(dependency);
        toRoot.addBefore(dependencyElement, toRoot.getLastChild());
      }
    }
  }

  private static void addTrailingNewLine(@NotNull final GroovyFile file) {
    PsiElement newLineElement = getNewLineElement(file, 1);
    file.addAfter(newLineElement, file.getLastChild());
  }

  /**
   * Returns a {@code PsiElement} representing the line terminator in the context of a given
   * {@code PsiElement}.
   */
  @NotNull
  private static PsiElement getNewLineElement(@NotNull PsiElement context, int length) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(context.getProject());
    return factory.createLineTerminator(length);
  }

  private static boolean scanForNewLineOrNull(@NotNull PsiElement element , boolean searchForward) {
    return scanAndCountNewLinesOrNulls(element, searchForward, 1) > 0;
  }

  /**
   * Scans forwards or backwards along the siblings of a given {@code PsiElement} until a given amount of new lines,
   * a non whitespace element or null are found. Returns the number of new lines found, or Integer.MAX_VALUE
   * if there was a cycle in the {@code PsiElement}s or we hit the end of the siblings. {@code numberOfNewLines} can
   * be set to terminate as soon as we find that many lines.
   */
  private static int scanAndCountNewLinesOrNulls(@NotNull PsiElement element , boolean searchForward, int numberOfNewLines) {
    int foundNewLines = 0;

    Set<PsiElement> seen = new HashSet<>();
    while (!seen.contains(element)) {
      if (element == null) {
        return Integer.MAX_VALUE;
      } else if (element.getNode().getElementType().equals(GroovyTokenTypes.mNLS)) {
        foundNewLines += element.getTextLength();
      } else if (!(element instanceof PsiWhiteSpace)) {
        return foundNewLines;
      }

      if (foundNewLines >= numberOfNewLines) {
        return foundNewLines;
      }

      seen.add(element);
      if (!searchForward) {
        element = element.getPrevSibling();
      } else {
        element = element.getNextSibling();
      }
    }
    return Integer.MAX_VALUE;
  }

  /**
   * Returns whether or not an element should be wrapped by new lines in the gradle build file.
   */
  private static boolean shouldBeWrappedByNewLines(@NotNull PsiElement element) {
    return element.getParent() != null &&
           (element instanceof GrMethodCall || element instanceof GrAssignmentExpression) &&
           (element.getParent() instanceof GroovyFile || element.getParent() instanceof GrClosableBlock);
  }

  private static void ensureCorrectSpacing(@NotNull PsiElement element) {
    // We only care about elements that should be wrapped with whitespace
    if (!shouldBeWrappedByNewLines(element)) {
      return;
    }

    PsiElement newLineElement = getNewLineElement(element, 1);
    if (element.getParent() instanceof GroovyFile) {
      // If we are a top level element being added to the end of a file then make sure to add two new lines to
      // keep formatting consistent with the project wizards generated files.
      int newLines = scanAndCountNewLinesOrNulls(element.getPrevSibling(), false, 2);
      if (newLines >= 0 && newLines < 2) {
        PsiElement doubleNewLineElement = getNewLineElement(element, 2 - newLines);
        element.getParent().addBefore(doubleNewLineElement, element);
      }
    } else if (!scanForNewLineOrNull(element.getPrevSibling(), false)) {
      element.getParent().addBefore(newLineElement, element);
    }
    if (!scanForNewLineOrNull(element.getNextSibling(), true)) {
      element.getParent().addAfter(newLineElement, element);
    }
  }

  /** Finds the {@link PsiElement} that a new dependency (in the given configuration) should be inserted before. */
  @NotNull
  private static PsiElement findInsertionPoint(@NotNull PsiElement root, String configurationName) {
    GrMethodCall current = PsiTreeUtil.getChildOfType(root, GrMethodCall.class);

    // Try to find a method call element that needs to be after our configurationName.
    while (current != null) {
      String currentConfigurationName = getConfigurationName(current);
      if (currentConfigurationName != null && CONFIGURATION_ORDERING.compare(currentConfigurationName, configurationName) > 0) {
        break;
      }

      current = PsiTreeUtil.getNextSiblingOfType(current, GrMethodCall.class);
    }

    return current != null ? current : root.getLastChild();
  }

  /** Tries to get the configuration name from a dependency declaration. */
  @Nullable
  private static String getConfigurationName(@NotNull GrMethodCall element) {
    GrExpression invokedExpression = element.getInvokedExpression();
    if (invokedExpression instanceof GrReferenceExpression) {
      PsiElement referenceNameElement = ((GrReferenceExpression)invokedExpression).getReferenceNameElement();
      if (referenceNameElement instanceof LeafPsiElement) {
        IElementType elementType = ((LeafPsiElement)referenceNameElement).getElementType();
        if (elementType == GroovyTokenTypes.mIDENT) {
          return referenceNameElement.getText();
        }
      }
    }

    return null;
  }

  /**
   * Looks for statements adding dependencies to different configurations (which look like 'configurationName "dependencyCoordinate"')
   * and tries to parse them into Gradle coordinates. If successful, adds the new coordinate to the map.
   */
  private static void pullDependenciesIntoMap(@NotNull PsiElement root,
                                              @NotNull Map<String, Multimap<String, GradleCoordinate>> allConfigurations,
                                              @Nullable List<String> unparsedDependencies) {
    for (PsiElement existingElem : root.getChildren()) {
      if (existingElem instanceof GrCall) {
        PsiElement reference = existingElem.getFirstChild();
        if (reference instanceof GrReferenceExpression) {
          final String configurationName = reference.getText();
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
                  Multimap<String, GradleCoordinate> map =
                    allConfigurations.computeIfAbsent(configurationName, k -> LinkedListMultimap.create());
                  if (!map.get(coordinate.getId()).contains(coordinate)) {
                    map.put(coordinate.getId(), coordinate);
                  }
                }
              }
            }
            if (!parsed && unparsedDependencies != null) {
              unparsedDependencies.add(existingElem.getText());
            }
          }
        }
      }
    }
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
      }
      else if (item.getFirstChild() != null && element.getFirstChild() != null) {
        if (item.getFirstChild().getText().equals(element.getFirstChild().getText())) {
          matchingItems.add(item);
        }
      }
    }
    if (matchingItems.size() == 1) {
      return matchingItems.get(0);
    }
    else {
      return null;
    }
  }
}
