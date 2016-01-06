/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FileTreeDependencyModel extends DependencyModel {
  private static final Logger LOG = Logger.getInstance(FileTreeDependencyModel.class);

  @NonNls private static final String FILE_TREE_METHOD_NAME = "fileTree";
  @NonNls private static final String DIR_ATTRIBUTE = "dir";
  @NonNls private static final String INCLUDE_ATTRIBUTE = "include";

  @NotNull private final String myConfigurationName;
  @NotNull private final GradleDslMethodCall myDslElement;
  @NotNull private GradleDslExpression myDir;
  @Nullable private GradleDslElement myIncludeElement;

  public FileTreeDependencyModel(@NotNull String configurationName,
                                 @NotNull GradleDslMethodCall dslElement,
                                 @NotNull GradleDslExpression dir,
                                 @Nullable GradleDslElement includeElement) {
    myConfigurationName = configurationName;
    myDslElement = dslElement;
    myDir = dir;
    myIncludeElement = includeElement;
  }

  @Override
  @NotNull
  protected GradleDslElement getDslElement() {
    return myDslElement;
  }

  @NotNull
  public String dir() {
    String dir = myDir.getValue(String.class);
    assert dir != null;
    return dir;
  }

  public void setDir(@NotNull String dir) {
    myDir.setValue(dir);
  }

  @NotNull
  public List<String> include() {
    if (myIncludeElement instanceof GradleDslExpressionList) {
      return ((GradleDslExpressionList)myIncludeElement).getValues(String.class);
    }

    if (myIncludeElement instanceof GradleDslExpression) {
      String value = ((GradleDslExpression)myIncludeElement).getValue(String.class);
      if (value != null) {
        return Collections.singletonList(value);
      }
    }

    return Collections.emptyList();
  }

  public static Collection<? extends FileTreeDependencyModel> create(@NotNull String configurationName,
                                                                     @NotNull GradleDslMethodCall methodCall) {
    List<FileTreeDependencyModel> result = Lists.newArrayList();
    if (FILE_TREE_METHOD_NAME.equals(methodCall.getName())) {
      List<GradleDslElement> arguments = methodCall.getArguments();
      for (GradleDslElement argument : arguments) {
        if (argument instanceof GradleDslExpressionMap) {
          GradleDslExpressionMap dslMap = (GradleDslExpressionMap)argument;
          GradleDslExpression dirElement = dslMap.getProperty(DIR_ATTRIBUTE, GradleDslExpression.class);
          if (dirElement == null) {
            assert methodCall.getPsiElement() != null;
            String msg = String.format("'%1$s' is not a valid file tree dependency", methodCall.getPsiElement().getText());
            LOG.warn(msg);
            continue;
          }
          GradleDslElement includeElement = dslMap.getPropertyElement(INCLUDE_ATTRIBUTE);
          if (includeElement instanceof GradleDslExpression || includeElement instanceof GradleDslExpressionList) {
            result.add(new FileTreeDependencyModel(configurationName, methodCall, dirElement, includeElement));
          }
        }
      }
    }
    return result;
  }
}
