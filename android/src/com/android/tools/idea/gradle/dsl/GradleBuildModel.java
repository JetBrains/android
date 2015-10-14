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
import com.android.tools.idea.gradle.dsl.parser.GradleDslElementParser;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;

public class GradleBuildModel extends GradleDslModel {
  @NotNull private Dependencies myDependencies = new Dependencies(this);

  private final GradleDslElementParser[] myParsers = {new JavaProjectElementParser()};

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
    super(file, project, moduleName);
  }

  @Override
  public void reparse() {
    myDependencies = new Dependencies(this);
    super.reparse();
  }

  @Override
  protected void parse(@NotNull GroovyFile psiFile) {
    myDependencies.setPsiFile(psiFile);
    psiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
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
        boolean parsed = false;
        for (GradleDslElementParser parser : myParsers) {
          // If a parser was able to parse the given PSI element, stop. Otherwise give another parser the chance to parse the PSI element.
          if (parser.parse(e, GradleBuildModel.this)) {
            parsed = true;
            break;
          }
        }
        if (!parsed) {
          GradleDslParser.parse(e, GradleBuildModel.this);
        }
      }
    }));
  }

  @Nullable
  public JavaElement java() {
    return getProperty(JavaElement.NAME, JavaElement.class);
  }

  @NotNull
  public GradleBuildModel addJavaElement() {
    if (java() != null) {
      return this;
    }
    JavaElement javaElement = new JavaElement(this);
    return (GradleBuildModel)setNewElement(JavaElement.NAME, javaElement);
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

  @Override
  protected void reset() {
    super.reset();
    myDependencies.resetState();
  }

  @Override
  protected void apply() {
    super.apply();
    myDependencies.applyChanges();
  }
}
