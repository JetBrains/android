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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.android.tools.idea.gradle.dsl.model.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.parser.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.getStandardProjectKey;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Represents a {@link GrExpression} element.
 */
public abstract class GradleDslExpression extends GradleDslElement {
  @Nullable protected GrExpression myExpression;

  protected GradleDslExpression(@Nullable GradleDslElement parent,
                                @Nullable GroovyPsiElement psiElement,
                                @NotNull String name,
                                @Nullable GrExpression expression) {
    super(parent, psiElement, name);
    myExpression = expression;
  }

  @Nullable
  public GrExpression getExpression() {
    return myExpression;
  }

  @Nullable
  public abstract Object getValue();

  @Nullable
  public abstract <T> T getValue(@NotNull Class<T> clazz);

  public abstract void setValue(@NotNull Object value);

  /**
   * Returns the resolved value of the given {@code referenceText} of type {@code clazz} when the {@code referenceText} is referring to
   * an element with the value of that type, or {@code null} otherwise.
   */
  @Nullable
  protected <T> T resolveReference(@NotNull String referenceText, @NotNull Class<T> clazz) {
    GradleDslElement searchStartElement = this;
    String searchReferenceText = referenceText;

    List<String> referenceTextSegments = Splitter.on('.').trimResults().omitEmptyStrings().splitToList(referenceText);
    int index = 0;
    int segmentCount = referenceTextSegments.size();
    for (; index < segmentCount; index++) {
      GradleDslFile dslFile = resolveProjectReference(searchStartElement, referenceTextSegments.get(index));
      if (dslFile == null) {
        break;
      }
      searchStartElement = dslFile;
    }

    GradleDslElement resolvedElement;
    if (index >= segmentCount) {
      resolvedElement = searchStartElement;
    }
    else {
      searchReferenceText = Joiner.on('.').join(referenceTextSegments.subList(index, segmentCount));
      resolvedElement = resolveReference(searchStartElement, searchReferenceText);
    }

    if (resolvedElement != null) {
      T result = null;
      if (clazz.isInstance(resolvedElement)) {
        result = clazz.cast(resolvedElement);
      }
      else if (resolvedElement instanceof GradleDslExpression) {
        result = ((GradleDslExpression)resolvedElement).getValue(clazz);
      }
      if (result != null) {
        setResolvedVariables(ImmutableList.of(new GradleResolvedVariable(referenceText, result, resolvedElement)));
        return result;
      }
    }

    GradleDslFile dslFile = searchStartElement.getDslFile();
    if (clazz.isAssignableFrom(String.class)) {
      if ("rootDir".equals(searchReferenceText)) { // resolve the rootDir reference to project root directory.
        return clazz.cast(Projects.getBaseDirPath(dslFile.getProject()).getPath());
      }
      if ("projectDir".equals(searchReferenceText)) { // resolve the projectDir reference to module directory.
        return clazz.cast(dslFile.getDirectoryPath().getPath());
      }
      return clazz.cast(referenceText);
    }

    return null;
  }

  @Nullable
  private static GradleDslFile resolveProjectReference(GradleDslElement startElement, @NotNull String projectReference) {
    GradleDslFile dslFile = startElement.getDslFile();
    if ("project".equals(projectReference)) {
      return dslFile;
    }

    if ("parent".equals(projectReference)) {
      return dslFile.getParentModuleDslFile();
    }

    if ("rootProject".equals(projectReference)) {
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      return dslFile;
    }

    String standardProjectKey = getStandardProjectKey(projectReference);
    if (standardProjectKey != null) { // project(':project:path')
      String modulePath = standardProjectKey.substring(standardProjectKey.indexOf("'") + 1, standardProjectKey.lastIndexOf("'"));
      GradleSettingsModel model = GradleSettingsModel.get(dslFile.getProject());
      if (model == null) {
        return null;
      }
      File moduleDirectory = model.moduleDirectory(modulePath);
      if (moduleDirectory == null) {
        return null;
      }
      while (dslFile != null && !filesEqual(dslFile.getDirectoryPath(), virtualToIoFile(dslFile.getProject().getBaseDir()))) {
        dslFile = dslFile.getParentModuleDslFile();
      }
      if (dslFile == null) {
        return null;
      }
      return findDslFile(dslFile, moduleDirectory); // root module dsl File.
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReference(GradleDslElement startElement, @NotNull String referenceText) {
    GradleDslFile dslFile = null;
    GradleDslElement element = startElement;
    while (dslFile == null && element != null) { // First search in the file this element belongs to.
      if (element instanceof GradlePropertiesDslElement) {
        GradleDslElement propertyElement = ((GradlePropertiesDslElement)element).getPropertyElement(referenceText);
        if (propertyElement != null) {
          return propertyElement;
        }
        if (element instanceof GradleDslFile) {
          dslFile = (GradleDslFile)element;
          ExtDslElement extDslElement = dslFile.getProperty(EXT_BLOCK_NAME, ExtDslElement.class);
          if (extDslElement != null) {
            GradleDslElement extPropertyElement = extDslElement.getPropertyElement(referenceText);
            if (extPropertyElement != null) {
              return extPropertyElement;
            }
          }
        }
      }
      element = element.getParent();
    }

    GradleDslFile parentDslFile = null;
    if (dslFile != null) {
      parentDslFile = dslFile.getParentModuleDslFile();
    }

    while (parentDslFile != null) { // Now look in the parent projects ext blocks.
      ExtDslElement extDslElement = parentDslFile.getProperty(EXT_BLOCK_NAME, ExtDslElement.class);
      if (extDslElement != null) {
        GradleDslElement extPropertyElement = extDslElement.getPropertyElement(referenceText);
        if (extPropertyElement != null) {
          return extPropertyElement;
        }
      }
      parentDslFile = parentDslFile.getParentModuleDslFile();
    }

    return null;
  }

  @Nullable
  private static GradleDslFile findDslFile(GradleDslFile rootModuleDslFile, File moduleDirectory) {
    if (filesEqual(rootModuleDslFile.getDirectoryPath(), moduleDirectory)) {
      return rootModuleDslFile;
    }

    for (GradleDslFile dslFile : rootModuleDslFile.getChildModuleDslFiles()) {
      if (filesEqual(dslFile.getDirectoryPath(), moduleDirectory)) {
        return dslFile;
      }
      GradleDslFile childDslFile = findDslFile(dslFile, moduleDirectory);
      if (childDslFile != null) {
        return dslFile;
      }
    }
    return null;
  }
}
