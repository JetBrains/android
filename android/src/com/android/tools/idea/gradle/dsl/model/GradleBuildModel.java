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

import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.android.tools.idea.gradle.dsl.parser.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslReference;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class GradleBuildModel extends GradleFileModel {
  @Nullable
  public static GradleBuildModel get(@NotNull Module module) {
    VirtualFile file = getGradleBuildFile(module);
    return file != null ? parseBuildFile(file, module.getProject(), module.getName()) : null;
  }

  @NotNull
  public static GradleBuildModel parseBuildFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
    GradleBuildDslFile buildDslFile = new GradleBuildDslFile(file, project, moduleName);
    populateWithParentModuleSubProjectsProperties(buildDslFile);
    buildDslFile.parse();
    return new GradleBuildModel(buildDslFile);
  }

  private static void populateWithParentModuleSubProjectsProperties(@NotNull GradleBuildDslFile buildDslFile) {
    GradleSettingsModel gradleSettingsModel = GradleSettingsModel.get(buildDslFile.getProject());
    if (gradleSettingsModel == null) {
      return;
    }

    String modulePath = gradleSettingsModel.moduleWithDirectory(virtualToIoFile(buildDslFile.getFile().getParent()));
    if (modulePath == null) {
      return;
    }

    GradleBuildModel parentModuleModel = gradleSettingsModel.getParentModuleModel(modulePath);
    if (parentModuleModel == null) {
      return;
    }

    SubProjectsDslElement subProjectsDslElement =
      parentModuleModel.myGradleDslFile.getProperty(SubProjectsDslElement.NAME, SubProjectsDslElement.class);
    if (subProjectsDslElement == null) {
      return;
    }

    buildDslFile.setParsedElement(SubProjectsDslElement.NAME, subProjectsDslElement);
    for (String property : subProjectsDslElement.getProperties()) {
      GradleDslElement propertyElement = subProjectsDslElement.getPropertyElement(property);
      assert propertyElement != null;
      buildDslFile.setParsedElement(property, propertyElement);
    }
  }

  private GradleBuildModel(@NotNull GradleBuildDslFile buildDslFile) {
    super(buildDslFile);
  }

  @Nullable
  public AndroidModel android() {
    AndroidDslElement androidDslElement = myGradleDslFile.getProperty(AndroidDslElement.NAME, AndroidDslElement.class);
    return androidDslElement != null ? new AndroidModel(androidDslElement) : null;
  }

  @NotNull
  public GradleBuildModel addAndroidModel() {
    if (android() != null) {
      return this;
    }
    AndroidDslElement androidDslElement = new AndroidDslElement(myGradleDslFile);
    myGradleDslFile.setNewElement(AndroidDslElement.NAME, androidDslElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeAndroidModel() {
    myGradleDslFile.removeProperty(AndroidDslElement.NAME);
    return this;
  }

  @Contract("true -> !null")
  @Nullable
  public DependenciesModel dependencies(boolean createIfNeeded) {
    DependenciesModel dependenciesModel = dependencies();
    if (createIfNeeded && dependenciesModel == null) {
      addDependenciesModel();
      dependenciesModel = dependencies();
      assert dependenciesModel != null;
    }
    return dependenciesModel;
  }

  @Nullable
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myGradleDslFile.getProperty(DependenciesDslElement.NAME, DependenciesDslElement.class);
    return dependenciesDslElement != null ? new DependenciesModel(dependenciesDslElement) : null;
  }

  @NotNull
  public GradleBuildModel addDependenciesModel() {
    if (dependencies() != null) {
      return this;
    }
    DependenciesDslElement dependenciesDslElement = new DependenciesDslElement(myGradleDslFile);
    myGradleDslFile.setNewElement(DependenciesDslElement.NAME, dependenciesDslElement);
    return this;
  }

  @Nullable
  public ExtModel ext() {
    ExtDslElement extDslElement = myGradleDslFile.getProperty(ExtDslElement.NAME, ExtDslElement.class);
    return extDslElement != null ? new ExtModel(extDslElement) : null;
  }

  @NotNull
  public GradleBuildModel addExtModel() {
    if (ext() != null) {
      return this;
    }
    ExtDslElement extDslElement = new ExtDslElement(myGradleDslFile);
    myGradleDslFile.setNewElement(ExtDslElement.NAME, extDslElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeExtModel() {
    myGradleDslFile.removeProperty(ExtDslElement.NAME);
    return this;
  }

  @Nullable
  public JavaModel java() {
    JavaDslElement javaDslElement = myGradleDslFile.getProperty(JavaDslElement.NAME, JavaDslElement.class);
    return javaDslElement != null ? new JavaModel(javaDslElement) : null;
  }

  @NotNull
  public GradleBuildModel addJavaModel() {
    if (java() != null) {
      return this;
    }
    JavaDslElement javaDslElement = new JavaDslElement(myGradleDslFile);
    myGradleDslFile.setNewElement(JavaDslElement.NAME, javaDslElement);
    return this;
  }

  @NotNull
  public GradleBuildModel removeJavaModel() {
    myGradleDslFile.removeProperty(JavaDslElement.NAME);
    return this;
  }

  private static class GradleBuildDslFile extends GradleDslFile {
    private GradleBuildDslFile(@NotNull VirtualFile file, @NotNull Project project, @NotNull String moduleName) {
      super(file, project, moduleName);
    }

    @Override
    public void reparse() {
      super.reparse();
    }

    @Override
    protected void parse(@NotNull GroovyFile psiFile) {
      psiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
        @Override
        public void visitMethodCallExpression(GrMethodCallExpression e) {
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
          GradleDslParser.parse(e, GradleBuildDslFile.this);
        }
      }));
    }

    @Override
    public void setParsedElement(@NotNull String property, @NotNull GradleDslElement element) {
      if ((SOURCE_COMPATIBILITY_FIELD.equals(property) || TARGET_COMPATIBILITY_FIELD.equals(property)) &&
          (element instanceof GradleDslLiteral || element instanceof GradleDslReference)) {
        JavaDslElement javaDslElement = getProperty(JavaDslElement.NAME, JavaDslElement.class);
        if (javaDslElement == null) {
          javaDslElement = new JavaDslElement(this);
          super.setParsedElement(JavaDslElement.NAME, javaDslElement);
        }
        javaDslElement.setParsedElement(property, element);
        return;
      }
      super.setParsedElement(property, element);
    }

    @Override
    protected void reset() {
      super.reset();
    }

    @Override
    protected void apply() {
      super.apply();
    }
  }
}
