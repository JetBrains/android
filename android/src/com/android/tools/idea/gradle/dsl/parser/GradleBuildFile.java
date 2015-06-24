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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

public class GradleBuildFile {
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;
  @NotNull private final List<DependenciesElement> myDependenciesBlocks = Lists.newArrayList();

  // TODO Get the parsers from an extension point.
  private final List<? extends GradleDslElementParser> myParsers = Lists.newArrayList(new DependenciesElementParser());

  @NotNull
  public static GradleBuildFile parseFile(@NotNull VirtualFile file, @NotNull Project project) {
    GradleBuildFile buildFile = new GradleBuildFile(file, project);
    buildFile.reparse();
    return buildFile;
  }

  private GradleBuildFile(@NotNull VirtualFile file, @NotNull Project project) {
    myFile = file;
    myProject = project;
  }

  /**
   * Parses the build.gradle file again. This is a convenience method to avoid calling {@link #parseFile(VirtualFile, Project)} if
   * an already parsed build.gradle file needs to be parsed again (for example, after making changes to the PSI elements.)
   */
  public void reparse() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    reset();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);
    if (psiFile != null) {
      psiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
        @Override
        public void visitMethodCallExpression(GrMethodCallExpression e) {
          for (GradleDslElementParser parser : myParsers) {
            // If a parser was able to parse the given PSI element, stop. Otherwise give another parser the chance to parse the PSI element.
            if (parser.parse(e, GradleBuildFile.this)) {
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
  public ImmutableList<DependenciesElement> getDependenciesBlocksView() {
    return ImmutableList.copyOf(myDependenciesBlocks);
  }
}
