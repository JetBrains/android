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
package com.android.tools.idea.gradle.dsl.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class GradleBuildModel {
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final List<DependenciesElement> myDependenciesBlocks = Lists.newArrayList();

  // TODO Get the parsers from an extension point.
  private final GradleDslElementParser[] myParsers = {new DependenciesElementParser()};

  @Nullable private PsiFile myPsiFile;

  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject()) : null;
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    GradleBuildModel buildFile = new GradleBuildModel(file, project);
    buildFile.reparse();
    return buildFile;
  }

  private GradleBuildModel(@NotNull VirtualFile file, @NotNull Project project) {
    myFile = file;
    myProject = project;
  }

  /**
   * Parses the build.gradle file again. This is a convenience method to avoid calling {@link #parseBuildFile(VirtualFile, Project)} if
   * an already parsed build.gradle file needs to be parsed again (for example, after making changes to the PSI elements.)
   */
  public void reparse() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    reset();
    myPsiFile = PsiManager.getInstance(myProject).findFile(myFile);
    if (myPsiFile != null) {
      myPsiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
        @Override
        public void visitMethodCallExpression(GrMethodCallExpression e) {
          for (GradleDslElementParser parser : myParsers) {
            // If a parser was able to parse the given PSI element, stop. Otherwise give another parser the chance to parse the PSI element.
            if (parser.parse(e, GradleBuildModel.this)) {
              break;
            }
          }
        }

        @Override
        public void visitAssignmentExpression(GrAssignmentExpression e) {
          for (GradleDslElementParser parser : myParsers) {
            // If a parser was able to parse the given PSI element, stop. Otherwise give another parser the chance to parse the PSI element.
            if (parser.parse(e, GradleBuildModel.this)) {
              break;
            }
          }
        }
      }));
    }
  }

  private void reset() {
    myDependenciesBlocks.clear();
  }

  void add(@NotNull DependenciesElement dependencies) {
    myDependenciesBlocks.add(dependencies);
  }

  @NotNull
  public ImmutableList<DependenciesElement> getDependenciesBlocks() {
    return ImmutableList.copyOf(myDependenciesBlocks);
  }

  /**
   * Adds a new external dependency to the build.gradle file. If there are more than one "dependencies" block, this method will add the new
   * dependency to the first one. If the build.gradle file does not have a "dependencies" block, this method will create one.
   * <p>
   * Check that {@link #hasPsiFile()} returns {@code true} before invoking this method.
   * </p>
   * <p>
   * Please note the new dependency will <b>not</b> be included in
   * {@link DependenciesElement#getExternalDependencies()} (obtained through {@link #getDependenciesBlocks()}, unless you invoke
   * {@link GradleBuildModel#reparse()}.
   * </p>
   *
   * @param configurationName the name of the configuration (e.g. "compile", "compileTest", "runtime", etc.)
   * @param compactNotation the dependency in "compact" notation: "group:name:version:classifier@extension".
   * @throws AssertionError if this method is invoked and this {@code GradleBuildFile} does not have a {@link PsiFile}.
   */
  public void addExternalDependency(@NotNull String configurationName, @NotNull String compactNotation) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    assert myPsiFile != null;
    if (myDependenciesBlocks.isEmpty()) {
      // There are no dependency blocks. Add one.
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

      // We need to add line separators, otherwise reformatting won't work.
      String lineSeparator = SystemProperties.getLineSeparator();
      String text = "dependencies {" + lineSeparator + configurationName + " '" + compactNotation + "'" + lineSeparator +  "}";
      GrExpression expression = factory.createExpressionFromText(text);

      myPsiFile.add(expression);
      CodeStyleManager.getInstance(myProject).reformat(expression);
    }
    else {
      DependenciesElement dependenciesBlock = myDependenciesBlocks.get(0);
      dependenciesBlock.addExternalDependency(configurationName, compactNotation);
    }
  }

  /**
   * Indicates whether this {@code GradleBuildFile} has an underlying {@link PsiFile}. A {@code PsiFile} is necessary to update the contents
   * of the build.gradle file.
   * @return {@code true} if this {@code GradleBuildFile} has a {@code PsiFile}; {@code false} otherwise.
   */
  public boolean hasPsiFile() {
    return myPsiFile != null;
  }
}
