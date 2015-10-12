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
package com.android.tools.idea.gradle.dsl;

import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.android.tools.idea.gradle.dsl.parser.*;
import com.android.tools.idea.gradle.dsl.parser.java.JavaProjectElementParser;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class GradleBuildModel extends GradleDslPropertiesElement {
  @NotNull private final VirtualFile myFile;
  @NotNull private final Project myProject;

  @NotNull private Dependencies myDependencies = new Dependencies(this);

  // TODO Get the parsers from an extension point.
  private final GradleDslElementParser[] myParsers = {new GradleDslParser(), new JavaProjectElementParser()};

  /**
   * Extra DSL elements in addition to dependencies provided by extension, e.g. Java extension and Android extension.
   */
  private final List<OldGradleDslElement> myExtendedDslElements = Lists.newArrayList();

  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject(), module.getName()) : null;
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleBuildModel buildFile = new GradleBuildModel(file, project, moduleName);
    buildFile.parse();
    return buildFile;
  }

  private GradleBuildModel(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    super(null, null, moduleName);
    myFile = file;
    myProject = project;
  }

  /**
   * Parses the build.gradle file again. This is a convenience method to avoid calling {@link #parseBuildFile(VirtualFile, Project)} if
   * an already parsed build.gradle file needs to be parsed again (for example, after making changes to the PSI elements.)
   */
  public void reparse() {
    myDependencies = new Dependencies(this);
    clear();
    parse();
  }

  private void parse() {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(myFile);

    GroovyFile myPsiFile = null;
    if (psiFile instanceof GroovyFile) {
      myPsiFile = (GroovyFile)psiFile;
    }

    myDependencies.setPsiFile(psiFile);
    if (myPsiFile == null) {
      return;
    }
    setPsiElement(myPsiFile);

    myPsiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression e) {
        if (myDependencies.parse(e)) {
          return;
        }
        process(e);
      }

      @Override
      public void visitAssignmentExpression(GrAssignmentExpression e) {
        process(e);
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement e) {
        process(e);
      }

      void process(GroovyPsiElement e) {
        for (GradleDslElementParser parser : myParsers) {
          // If a parser was able to parse the given PSI element, stop. Otherwise give another parser the chance to parse the PSI element.
          if (parser.parse(e, GradleBuildModel.this)) {
            break;
          }
        }
      }
    }));
  }

  @Override
  protected void reset() {
    super.reset();
    myDependencies.resetState();
    myExtendedDslElements.clear();
  }

  @Nullable
  public AndroidElement android() {
    return getProperty(AndroidElement.NAME, AndroidElement.class);
  }

  @NotNull
  public GradleBuildModel addAndroidElement() {
    if (android() != null) {
      return this;
    }
    AndroidElement androidElement = new AndroidElement(this);
    return (GradleBuildModel)setNewElement(AndroidElement.NAME, androidElement);
  }

  @Nullable
  public ExtModel ext() {
    return getProperty(ExtModel.NAME, ExtModel.class);
  }

  @NotNull
  public GradleBuildModel addExtModel() {
    if (ext() != null) {
      return this;
    }
    ExtModel extModel = new ExtModel(this);
    return (GradleBuildModel)setNewElement(ExtModel.NAME, extModel);
  }

  @NotNull
  public Dependencies dependencies() {
    return myDependencies;
  }

  /**
   * Get the DSL element provided by extension parsers (e.g. Java parser, android parser)
   *
   * @param clazz the type of the DSL element
   * @return the extension data
   */
  @Nullable
  public <T extends OldGradleDslElement> T getExtendedDslElement(@NotNull Class<T> clazz) {
    for (OldGradleDslElement element : myExtendedDslElements) {
      if (element.getClass().equals(clazz)) {
        @SuppressWarnings("unchecked")
        T result = (T)element;
        return result;
      }
    }
    return null;
  }

  public void addExtendedDslElement(@NotNull OldGradleDslElement element) {
    myExtendedDslElements.add(element);
  }

  @Override
  protected void apply() {
    super.apply();
    myDependencies.applyChanges();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }
}
