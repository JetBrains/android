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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleVersionCatalogFile;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Dependencies model for groovy and kts
public class ScriptDependenciesModelImpl extends AbstractDependenciesModel {
  public ScriptDependenciesModelImpl(@NotNull DependenciesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  public void addModule(@NotNull String configurationName, @NotNull String path, @Nullable String config) {
    ScriptModuleDependencyModelImpl.createNew(myDslElement, configurationName, path, config);
  }

  @Override
  protected Fetcher<ArtifactDependencyModel> getArtifactFetcher() {
    return (configurationName, element, resolved, configurationElement, maintainer, dest) -> {
      // We can only create ArtifactDependencyModels from expressions -- if for some reason we don't have an expression here (e.g. from a
      // parser bug) then don't create anything.
      if (!(element instanceof GradleDslExpression)) {
        return;
      }

      String methodName = null;

      if (element instanceof GradleDslMethodCall) {
        List<GradleDslExpression> arguments = ((GradleDslMethodCall)element).getArguments();
        methodName = ((GradleDslMethodCall)element).getMethodName();
        // We can handle single-argument method calls to specific functions, for example
        // `implementation platform('org.springframework.boot:spring-boot-dependencies:1.5.8.RELEASE')
        // or
        // `implementation enforcedPlatform([group: 'org.springframework.boot', name: 'spring-boot-dependencies', version: '1.5.8.RELEASE'])
        if (arguments.size() == 1 && Arrays.asList("platform", "enforcedPlatform").contains(methodName)) {
          element = arguments.get(0);
          resolved = resolveElement(element);
        }
        // Can't do anything else with method calls.
        else {
          return;
        }
      }
      if (element instanceof GradleDslSimpleExpression || resolved instanceof GradleDslExpressionMap) {
        ArtifactDependencyModel notation = ArtifactDependencyModelImpl.DynamicNotation.create(
          configurationName, (GradleDslExpression)element, configurationElement, maintainer, methodName);
        if (notation != null) {
          dest.add(notation);
          // cannot do extract variables for version catalog dependencies for now
          if (isInVersionCatalogFile(resolved)) {
            notation.markAsVersionCatalogDependency();
            notation.enableSetThrough();
          }
        }
      }
    };
  }

  private static boolean isInVersionCatalogFile(GradleDslElement element){
    return element.getDslFile() instanceof GradleVersionCatalogFile;
  }

  @Override
  protected Fetcher<ModuleDependencyModel> getModuleFetcher() {
    return (configurationName, element, resolved, configurationElement, maintainer, dest) -> {
      if (resolved instanceof GradleDslMethodCall) {
        String platformMethodName = null;
        GradleDslMethodCall methodCall = (GradleDslMethodCall)resolved;
        if (Arrays.asList("platform", "enforcedPlatform").contains(methodCall.getMethodName()) &&
            methodCall.getArguments().size() == 1 &&
            methodCall.getArguments().get(0) instanceof GradleDslMethodCall) {
          platformMethodName = methodCall.getMethodName();
          methodCall = (GradleDslMethodCall)methodCall.getArguments().get(0);
        }
        if (methodCall.getMethodName().equals(ScriptModuleDependencyModelImpl.PROJECT)) {
          ModuleDependencyModel model = ScriptModuleDependencyModelImpl.create(configurationName, methodCall, maintainer, platformMethodName);
          if (model != null && model.path().getValueType() != NONE) {
            dest.add(model);
          }
        }
      }
    };
  }

  @Override
  protected Fetcher<FileTreeDependencyModel> getFileTreeFetcher() {
    return (configurationName, element, resolved, configurationElement, maintainer, dest) -> {
      if (resolved instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)resolved;
        if (methodCall.getMethodName().equals(FileTreeDependencyModelImpl.FILE_TREE)) {
          FileTreeDependencyModel model = FileTreeDependencyModelImpl.create(methodCall, configurationName, maintainer);
          if (model != null && model.dir().getValueType() != NONE) {
            dest.add(model);
          }
        }
      }
    };
  }

  @Override
  protected Fetcher<FileDependencyModel> getFileFetcher() {
    return (configurationName, element, resolved, configurationElement, maintainer, dest) -> {
      if (resolved instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)resolved;
        if (methodCall.getMethodName().equals(FileDependencyModelImpl.FILES)) {
          dest.addAll(FileDependencyModelImpl.create(configurationName, methodCall, maintainer));
        }
      }
    };
  }

  public static final Logger LOG = Logger.getInstance(ScriptDependenciesModelImpl.class);

  enum Maintainers implements DependencyModelImpl.Maintainer {

    /**
     * Handles items in structures like:
     *
     * <p>implementation "group:artifact:version", [group: "group", name: "artifact": version: someVersion]
     */
    EXPRESSION_LIST_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        GradleDslExpressionList parentList;
        if (dslElement.getParent() instanceof GradleDslExpressionList list) {
          parentList = list;
        }
        else {
          parentList = (GradleDslExpressionList)dslElement.getParent().getParent();
        }
        List<GradleDslExpression> expressions = parentList.getExpressions();
        GradleDslElement nameHolder = parentList;

        if (expressions.size() == 1) {
          renameSingleElementConfiguration(nameHolder, newConfigurationName);
          return this;
        }

        DependenciesDslElement dependenciesElement = (DependenciesDslElement)(nameHolder.getParent());
        int index = dependenciesElement.getAllElements().indexOf(nameHolder);

        if (expressions.get(0) == dslElement && expressions.get(1) instanceof GradleDslExpressionMap) {
          Throwable t = new UnsupportedOperationException(
            "Changing the configuration name of a multi-entry dependency declaration containing map-notations is not supported.");
          LOG.warn(t);
          return null;
        }

        GradleDslElement copiedElement;
        copiedElement = ((GradleDslExpression)dslElement).copy();
        copiedElement.getNameElement().rename(newConfigurationName);
        dependencyModel.setDslElement(copiedElement);
        dependenciesElement.addNewElementAt(index, copiedElement);
        parentList.removeElement(dslElement);
        dependenciesElement.setModified();
        return SINGLE_ITEM_MAINTAINER;
      }
    },

    /**
     * Handles items in structures like:
     *
     * <p>implementation ([group: "group", name: "artifact": version: someVersion], "group:artifact:version")
     * <p> -=or=-
     * <p>implementation (group: "group", name: "artifact": version: someVersion)
     * <p> -=or=-
     * <p>implementation ("group:artifact:version")
     * <p>with an optional closure that may follow a single item.
     */
    ARGUMENT_LIST_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        GradleDslExpressionList parentList = (GradleDslExpressionList)(dslElement.getParent());
        List<GradleDslExpression> expressions = parentList.getExpressions();
        GradleDslElement nameHolder = parentList.getParent();
        GradleNameElement nameElement = nameHolder.getNameElement();

        if (expressions.size() == 1) {
          renameSingleElementConfiguration(nameHolder, newConfigurationName);
          return this;
        }

        DependenciesDslElement dependenciesElement = (DependenciesDslElement)(nameHolder.getParent());
        int index = dependenciesElement.getAllElements().indexOf(nameHolder);
        GradleDslElement copiedElement;
        copiedElement = ((GradleDslExpression)dslElement).copy();
        copiedElement.getNameElement().rename(newConfigurationName);
        dependencyModel.setDslElement(copiedElement);
        dependenciesElement.addNewElementAt(index, copiedElement);
        parentList.removeElement(dslElement);
        dependenciesElement.setModified();
        return SINGLE_ITEM_MAINTAINER;
      }
    },

    /**
     * Handles items in structures like:
     *
     * <p>implementation "group:artifact:$version"
     * <p> -=or=-
     * <p>implementation group: "group", name: "artifact", version: "1.0"
     * <p>with an optional closure that follows.
     */
    SINGLE_ITEM_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        renameSingleElementConfiguration(dslElement, newConfigurationName);
        return this;
      }
    },

    /**
     * Handles items in structures like:
     *
     * <p>implementation files("a"), files("b", "c")
     */
    DEEP_EXPRESSION_LIST_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        GradleDslExpressionList parentList = (GradleDslExpressionList)(dslElement.getParent());
        List<GradleDslExpression> expressions = parentList.getExpressions();
        GradleDslMethodCall methodCall = (GradleDslMethodCall)parentList.getParent();

        GradleDslExpressionList declarationExpressionList = (GradleDslExpressionList)(methodCall.getParent());
        GradleDslExpressionList nameHolder = declarationExpressionList;

        DependenciesDslElement dependenciesElement = (DependenciesDslElement)(nameHolder.getParent());
        int index = dependenciesElement.getAllElements().indexOf(nameHolder);

        if (expressions.size() == 1) {
          List<GradleDslExpression> declarationExpressions = declarationExpressionList.getExpressions();
          if (declarationExpressions.size() == 1) {
            renameSingleElementConfiguration(nameHolder, newConfigurationName);
            return this;
          }
        }

        GradleDslMethodCall copiedMethodElement;
        copiedMethodElement = new GradleDslMethodCall(dependenciesElement,
                                                      GradleNameElement.create(newConfigurationName),
                                                      methodCall.getMethodName());
        GradleDslExpression expressionCopy = ((GradleDslExpression)dslElement).copy();
        copiedMethodElement.addNewArgument(expressionCopy);
        dependencyModel.setDslElement(expressionCopy);
        dependenciesElement.addNewElementAt(index, copiedMethodElement);

        if (expressions.size() == 1) {
          declarationExpressionList.removeElement(methodCall);
        }
        else {
          parentList.removeElement(dslElement);
        }

        dependenciesElement.setModified();
        return DEEP_SINGLE_ITEM_MAINTAINER;
      }
    },

    /**
     * Handles items in structures like:
     *
     * <p>implementation(files("a"), files("b", "c"))
     */
    DEEP_ARGUMENT_LIST_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        GradleDslExpressionList parentList = (GradleDslExpressionList)(dslElement.getParent());
        List<GradleDslExpression> expressions = parentList.getExpressions();
        GradleDslMethodCall methodCall = (GradleDslMethodCall)parentList.getParent();

        GradleDslExpressionList declarationMethodArguments = (GradleDslExpressionList)(methodCall.getParent());
        GradleDslMethodCall nameHolder = (GradleDslMethodCall)declarationMethodArguments.getParent();

        DependenciesDslElement dependenciesElement = (DependenciesDslElement)(nameHolder.getParent());
        int index = dependenciesElement.getAllElements().indexOf(nameHolder);

        if (expressions.size() == 1) {
          List<GradleDslExpression> declarationMethodExpressions = declarationMethodArguments.getExpressions();
          if (declarationMethodExpressions.size() == 1) {
            renameSingleElementConfiguration(nameHolder, newConfigurationName);
            return this;
          }
        }

        GradleDslMethodCall copiedMethodElement;
        copiedMethodElement = new GradleDslMethodCall(dependenciesElement,
                                                      GradleNameElement.create(newConfigurationName),
                                                      methodCall.getMethodName());
        GradleDslExpression expressionCopy = ((GradleDslExpression)dslElement).copy();
        copiedMethodElement.addNewArgument(expressionCopy);
        dependencyModel.setDslElement(expressionCopy);
        dependenciesElement.addNewElementAt(index, copiedMethodElement);

        if (expressions.size() == 1) {
          declarationMethodArguments.removeElement(methodCall);
        }
        else {
          parentList.removeElement(dslElement);
        }

        dependenciesElement.setModified();
        return DEEP_SINGLE_ITEM_MAINTAINER;
      }
    },

    /**
     * Handles items in structures like:
     *
     * <p>implementation files("a")
     * <p> -=or=-
     * <p>implementation files("a", "b")
     */
    DEEP_SINGLE_ITEM_MAINTAINER {
      @Nullable
      @Override
      public DependencyModelImpl.Maintainer setConfigurationName(DependencyModelImpl dependencyModel, String newConfigurationName) {
        GradleDslElement dslElement = dependencyModel.getDslElement();
        GradleDslExpressionList parentList = (GradleDslExpressionList)(dslElement.getParent());
        List<GradleDslExpression> expressions = parentList.getExpressions();
        GradleDslMethodCall methodCall = (GradleDslMethodCall)parentList.getParent();

        if (expressions.size() == 1) {
          renameSingleElementConfiguration(methodCall, newConfigurationName);
          return this;
        }

        GradleDslMethodCall nameHolder = methodCall;
        DependenciesDslElement dependenciesElement = (DependenciesDslElement)(nameHolder.getParent());
        int index = dependenciesElement.getAllElements().indexOf(nameHolder);

        GradleDslMethodCall copiedMethodElement;
        copiedMethodElement = new GradleDslMethodCall(dependenciesElement,
                                                      GradleNameElement.create(newConfigurationName),
                                                      methodCall.getMethodName());
        GradleDslExpression expressionCopy = ((GradleDslExpression)dslElement).copy();
        copiedMethodElement.addNewArgument(expressionCopy);
        dependencyModel.setDslElement(expressionCopy);
        dependenciesElement.addNewElementAt(index, copiedMethodElement);

        parentList.removeElement(dslElement);

        dependenciesElement.setModified();
        return DEEP_SINGLE_ITEM_MAINTAINER;
      }
    };

    private static void renameSingleElementConfiguration(@NotNull GradleDslElement dslElement, @NotNull String newConfigurationName) {
      if (dslElement instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)dslElement;
        if (methodCall.getMethodName().equals(dslElement.getNameElement().name())) {
          methodCall.setMethodName(newConfigurationName);
        }
      }
      dslElement.getNameElement().rename(newConfigurationName);
      dslElement.setModified();
    }
  }

  protected DependencyReplacer getDependencyReplacer() {
    return new DependencyReplacer() {
      public void performDependencyReplace(@NotNull PsiElement psiElement,
                                           @NotNull GradleDslElement element,
                                           @NotNull ArtifactDependencySpec dependency) {
        if (element instanceof GradleDslLiteral) {
          ((GradleDslLiteral)element).setValue(dependency.compactNotation());
        }
        else if (element instanceof GradleDslExpressionMap) {
          updateGradleExpressionMapWithDependency((GradleDslExpressionMap)element, dependency);
        }
        else if (element instanceof GradleDslMethodCall) {
          // There may be multiple arguments here, check find the one with correct PsiElement.
          GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
          for (GradleDslElement e : methodCall.getArguments()) {
            if (e.getPsiElement() == psiElement) {
              performDependencyReplace(psiElement, e, dependency);
            }
          }
        }
        else if (element instanceof GradleDslExpressionList) {
          for (GradleDslSimpleExpression expression : ((GradleDslExpressionList)element).getSimpleExpressions()) {
            if (element.getPsiElement() == psiElement) {
              performDependencyReplace(psiElement, expression, dependency);
            }
          }
        }
      }
    };
  }

  protected <T extends DependencyModel> void collectFrom(@NotNull String configurationName,
                                                         @NotNull GradleDslElement element,
                                                         @NotNull Fetcher<T> byFetcher,
                                                         @NotNull List<T> dest) {
    GradleDslClosure configurationElement = element.getClosureElement();

    GradleDslElement resolved = resolveElement(element);
    if (resolved instanceof GradleDslExpressionList) {
      for (GradleDslExpression expression : ((GradleDslExpressionList)resolved).getExpressions()) {
        GradleDslElement resolvedExpression = resolveElement(expression);
        DependencyModelImpl.Maintainer maintener = ScriptDependenciesModelImpl.Maintainers.EXPRESSION_LIST_MAINTAINER;
        byFetcher
          .fetch(configurationName, expression, resolvedExpression, configurationElement, maintener, dest);
      }
      return;
    }
    if (resolved instanceof GradleDslMethodCall) {
      String name = ((GradleDslMethodCall)resolved).getMethodName();
      if (name.equals(configurationName)) {
        for (GradleDslElement argument : ((GradleDslMethodCall)resolved).getArguments()) {
          GradleDslElement resolvedArgument = resolveElement(argument);
          byFetcher.fetch(configurationName, argument, resolvedArgument, configurationElement,
                          ScriptDependenciesModelImpl.Maintainers.ARGUMENT_LIST_MAINTAINER, dest);
        }
        return;
      }
    }
    byFetcher.fetch(configurationName, element, resolved, configurationElement, ScriptDependenciesModelImpl.Maintainers.SINGLE_ITEM_MAINTAINER,
                    dest);
  }

  @Nullable
  @Override
  protected GradleDslElement findByPsiElement(@NotNull PsiElement child) {
    for (String configurationName : myDslElement.getProperties()) {
      for (GradleDslElement element : myDslElement.getPropertyElementsByName(configurationName)) {
        // For method calls we need to check each of the arguments individually.
        if (element instanceof GradleDslMethodCall) {
          GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
          for (GradleDslElement el : methodCall.getArguments()) {
            if (el.getPsiElement() != null && isChildOfParent(child, el.getPsiElement())) {
              return el;
            }
          }
        }
        else if (element instanceof GradleDslExpressionList list) {
          for (GradleDslSimpleExpression e : list.getSimpleExpressions()) {
            if (e.getPsiElement() != null && isChildOfParent(child, e.getPsiElement())) {
              return e;
            }
          }
        }
        else {
          if (element.getPsiElement() != null && isChildOfParent(child, element.getPsiElement())) {
            return element;
          }
        }
      }
    }
    return null;
  }
}
