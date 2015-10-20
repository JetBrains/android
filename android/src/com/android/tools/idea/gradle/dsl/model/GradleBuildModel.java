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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.model.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtPsiElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaPsiElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaProjectElementParser;
import com.android.tools.idea.gradle.dsl.dependencies.Dependencies;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.parser.GradleDslElementParser;
import com.android.tools.idea.gradle.dsl.parser.GradlePsiFile;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidPsiElement;
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

public class GradleBuildModel extends GradleFileModel {
  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject(), module.getName()) : null;
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleBuildPsiFile buildPsiFile = new GradleBuildPsiFile(file, project, moduleName);
    buildPsiFile.parse();
    return new GradleBuildModel(buildPsiFile);
  }

  private GradleBuildModel(@NotNull GradleBuildPsiFile buildPsiFile) {
    super(buildPsiFile);
  }

  @Nullable
  public AndroidModel android() {
    AndroidPsiElement androidPsiElement = myGradlePsiFile.getProperty(AndroidPsiElement.NAME, AndroidPsiElement.class);
    return androidPsiElement != null ? new AndroidModel(androidPsiElement) : null;
  }

  @NotNull
  public GradleBuildModel addAndroidModel() {
    if (android() != null) {
      return this;
    }
    AndroidPsiElement androidPsiElement = new AndroidPsiElement(myGradlePsiFile);
    myGradlePsiFile.setNewElement(AndroidPsiElement.NAME, androidPsiElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeAndroidModel() {
    myGradlePsiFile.removeProperty(AndroidPsiElement.NAME);
    return this;
  }

  @NotNull
  public Dependencies dependencies() {
    return ((GradleBuildPsiFile)myGradlePsiFile).myDependencies;
  }

  @Nullable
  public ExtModel ext() {
    ExtPsiElement extPsiElement = myGradlePsiFile.getProperty(ExtPsiElement.NAME, ExtPsiElement.class);
    return extPsiElement != null ? new ExtModel(extPsiElement) : null;
  }

  @NotNull
  public GradleBuildModel addExtModel() {
    if (ext() != null) {
      return this;
    }
    ExtPsiElement extPsiElement = new ExtPsiElement(myGradlePsiFile);
    myGradlePsiFile.setNewElement(ExtPsiElement.NAME, extPsiElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeExtModel() {
    myGradlePsiFile.removeProperty(ExtPsiElement.NAME);
    return this;
  }

  @Nullable
  public JavaModel java() {
    JavaPsiElement javaPsiElement = myGradlePsiFile.getProperty(JavaPsiElement.NAME, JavaPsiElement.class);
    return javaPsiElement != null ? new JavaModel(javaPsiElement) : null;
  }

  @NotNull
  public GradleBuildModel addJavaModel() {
    if (java() != null) {
      return this;
    }
    JavaPsiElement javaPsiElement = new JavaPsiElement(myGradlePsiFile);
    myGradlePsiFile.setNewElement(JavaPsiElement.NAME, javaPsiElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeJavaModel() {
    myGradlePsiFile.removeProperty(JavaPsiElement.NAME);
    return this;
  }

  private static class GradleBuildPsiFile extends GradlePsiFile {
    @NotNull private Dependencies myDependencies = new Dependencies(this);

    private final GradleDslElementParser[] myParsers = {new JavaProjectElementParser()};


    private GradleBuildPsiFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
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
            if (parser.parse(e, GradleBuildPsiFile.this)) {
              parsed = true;
              break;
            }
          }
          if (!parsed) {
            GradleDslParser.parse(e, GradleBuildPsiFile.this);
          }
        }
      }));
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
}
