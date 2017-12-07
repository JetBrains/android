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

import com.android.tools.idea.Projects;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.model.GradleSettingsModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleStringInjection;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement.getStandardProjectKey;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Represents an expression element.
 */
public abstract class GradleDslExpression extends GradleDslElement {
  @Nullable protected PsiElement myExpression;

  protected GradleDslExpression(@Nullable GradleDslElement parent,
                                @Nullable PsiElement psiElement,
                                @NotNull String name,
                                @Nullable PsiElement expression) {
    super(parent, psiElement, name);
    myExpression = expression;
  }

  @Nullable
  public PsiElement getExpression() {
    return myExpression;
  }

  public void setExpression(@NotNull PsiElement expression) {
    myExpression = expression;
  }

  @Nullable
  public abstract Object getValue();

  @Nullable
  public abstract <T> T getValue(@NotNull Class<T> clazz);

  public abstract void setValue(@NotNull Object value);

  @Override
  @NotNull
  public Collection<GradleStringInjection> getResolvedVariables() {
    if (myExpression == null) {
      return Collections.emptyList();
    }
    return getDslFile().getParser().getInjections(this, myExpression);
  }

  @Nullable
  public GradleDslElement resolveReference(@NotNull String referenceText) {
    GradleDslElement searchStartElement = this;
    String searchReferenceText = referenceText;

    List<String> referenceTextSegments = Splitter.on('.').trimResults().omitEmptyStrings().splitToList(referenceText);
    int index = 0;
    int segmentCount = referenceTextSegments.size();
    for (; index < segmentCount; index++) {
      // Resolve the project reference elements like parent, rootProject etc.
      GradleDslFile dslFile = resolveProjectReference(searchStartElement, referenceTextSegments.get(index));
      if (dslFile == null) {
        break;
      }
      // Now lets search in that project build.gradle file.
      searchStartElement = dslFile;
    }

    /* For a project with the below hierarchy ...

    | <GRADLE_USER_HOME>/gradle.properties
    | RootProject
    | - - build.gradle
    | - - gradle.properties
    | - - FirstLevelChildProject
    | - - - - build.gradle
    | - - - - gradle.properties
    | - - - - SecondLevelChildProject
    | - - - - - - build.gradle
    | - - - - - - gralde.properties
    | - - - - - - ThirdLevelChildProject
    | - - - - - - - - build.gradle
    | - - - - - - - - gradle.properties

    the resolution path for a property defined in ThirdLevelChildProject's build.gradle file will be ...

      1. ThirdLevelChildProject/build.gradle
      2. <GRADLE_USER_HOME>/gradle.properties
      3. ThirdLevelChildProject/gradle.properties
      4. RootProject/gradle.properties
      5. SecondLevelChildProject/build.gradle
      6. SecondLevelChildProject/gradle.properties
      7. FirstLevelChildProject/build.gradle
      8. FirstLevelChildProject/gradle.properties
      9. RootProject/build.gradle
    */

    GradleDslElement resolvedElement;
    if (index >= segmentCount) {
      // the reference text is fully resolved by now. ex: if the while text itself is "rootProject" etc.
      resolvedElement = searchStartElement;
    }
    else {
      // Search in the file that searchStartElement belongs to.
      searchReferenceText = Joiner.on('.').join(referenceTextSegments.subList(index, segmentCount));
      resolvedElement = resolveReferenceInSameModule(searchStartElement, searchReferenceText);
    }

    GradleDslFile dslFile = searchStartElement.getDslFile();
    if (resolvedElement == null) {
      // Now look in the parent projects ext blocks.
      resolvedElement = resolveReferenceInParentModules(dslFile, searchReferenceText);
    }

    if ("rootDir".equals(searchReferenceText)) { // resolve the rootDir reference to project root directory.
      return new GradleDslGlobalValue(dslFile, Projects.getBaseDirPath(dslFile.getProject()).getPath());
    }
    if ("projectDir".equals(searchReferenceText)) { // resolve the projectDir reference to module directory.
      return new GradleDslGlobalValue(dslFile, dslFile.getDirectoryPath().getPath());
    }

    return resolvedElement;
  }

  /**
   * Returns the resolved value of the given {@code referenceText} of type {@code clazz} when the {@code referenceText} is referring to
   * an element with the value of that type, or {@code null} otherwise.
   */
  @Nullable
  public <T> T resolveReference(@NotNull String referenceText, @NotNull Class<T> clazz) {
    GradleDslElement resolvedElement = resolveReference(referenceText);

    if (resolvedElement != null) {
      T result = null;
      if (clazz.isInstance(resolvedElement)) {
        result = clazz.cast(resolvedElement);
      }
      else if (resolvedElement instanceof GradleDslExpression) {
        result = ((GradleDslExpression)resolvedElement).getValue(clazz);
      }
      if (result != null) {
        return result;
      }
    }

    if (clazz.isAssignableFrom(String.class)) {
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
      String modulePath = standardProjectKey.substring(standardProjectKey.indexOf('\'') + 1, standardProjectKey.lastIndexOf('\''));
      GradleSettingsModel model = GradleSettingsModelImpl.get(dslFile.getProject());
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
  private static GradleDslElement resolveReferenceInSameModule(GradleDslElement startElement, @NotNull String referenceText) {
    // Try to resolve in the build.gradle file the startElement is belongs to.
    GradleDslElement element = startElement;
    while (element != null) {
      if (element instanceof GradlePropertiesDslElement) {
        GradleDslElement propertyElement = ((GradlePropertiesDslElement)element).resolveVariableElement(referenceText);
        if (propertyElement != null) {
          return propertyElement;
        }
        if (element instanceof GradleDslFile) {
          ExtDslElement extDslElement = ((GradleDslFile)element).getPropertyElement(EXT_BLOCK_NAME, ExtDslElement.class);
          if (extDslElement != null) {
            GradleDslElement extPropertyElement = extDslElement.resolveVariableElement(referenceText);
            if (extPropertyElement != null) {
              return extPropertyElement;
            }
          }
          break;
        }
      }
      element = element.getParent();
    }

    // TODO: Add support to look at <GRADLE_USER_HOME>/gradle.properties before looking at this module's gradle.properties file.

    // Try to resolve in the gradle.properties file of the startElement's module.
    GradleDslFile dslFile = startElement.getDslFile();
    GradleDslElement propertyElement = resolveReferenceInPropertiesFile(dslFile, referenceText);
    if (propertyElement != null) {
      return propertyElement;
    }

    if (dslFile.getParentModuleDslFile() == null) {
      return null; // This is the root project build.gradle file and there is no further path to look up.
    }

    // Try to resolve in the root project gradle.properties file.
    GradleDslFile rootProjectDslFile = dslFile;
    while (true) {
      GradleDslFile parentModuleDslFile = rootProjectDslFile.getParentModuleDslFile();
      if (parentModuleDslFile == null) {
        break;
      }
      rootProjectDslFile = parentModuleDslFile;
    }
    return resolveReferenceInPropertiesFile(rootProjectDslFile, referenceText);
  }

  @Nullable
  private static GradleDslElement resolveReferenceInParentModules(@NotNull GradleDslFile dslFile, @NotNull String referenceText) {
    GradleDslFile parentDslFile = dslFile.getParentModuleDslFile();
    while (parentDslFile != null) {
      ExtDslElement extDslElement = parentDslFile.getPropertyElement(EXT_BLOCK_NAME, ExtDslElement.class);
      if (extDslElement != null) {
        GradleDslElement extPropertyElement = extDslElement.getPropertyElement(referenceText);
        if (extPropertyElement != null) {
          return extPropertyElement;
        }
      }

      if (parentDslFile.getParentModuleDslFile() == null) {
        // This is the root project build.gradle file and the roo project's gradle.properties file is already looked in
        // resolveReferenceInSameModule method.
        return null;
      }

      GradleDslElement propertyElement = resolveReferenceInPropertiesFile(parentDslFile, referenceText);
      if (propertyElement != null) {
        return propertyElement;
      }

      parentDslFile = parentDslFile.getParentModuleDslFile();
    }
    return null;
  }

  @Nullable
  private static GradleDslElement resolveReferenceInPropertiesFile(@NotNull GradleDslFile buildDslFile, @NotNull String referenceText) {
    GradleDslFile propertiesDslFile = buildDslFile.getSiblingDslFile();
    return propertiesDslFile != null ? propertiesDslFile.getPropertyElement(referenceText) : null;
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
