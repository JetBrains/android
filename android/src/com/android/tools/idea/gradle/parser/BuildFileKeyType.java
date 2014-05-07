/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.parser;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;
import static com.android.tools.idea.gradle.parser.GradleBuildFile.UNRECOGNIZED_VALUE;

/**
 * Enumerates the type information for build file parameters located by {@linkplain BuildFileKey}. The individual types have code to
 * actually set, get, and determine validity of values.
 */
public enum BuildFileKeyType {
  STRING(String.class, "''") {
    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createLiteralFromValue(value.toString()));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return "'" + escapeLiteralString(value.toString()) + "'";
    }
  },
  INTEGER(Integer.class, "0"),
  BOOLEAN(Boolean.class, "false"),
  CLOSURE(List.class, "{}") {
    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      // Just create an empty closure. Don't try to populate it; that will be done elsewhere.
      return "{\n}";
    }
  },
  FILE(File.class, Constants.FILE_METHOD_CALL + "('')") { // Represented in Groovy as file('/path/to/file')

    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      if (!(arg instanceof GrMethodCall)) {
        return UNRECOGNIZED_VALUE;
      }
      GrMethodCall call = (GrMethodCall)arg;
      if (!(Constants.FILE_METHOD_CALL.equals(GradleGroovyFile.getMethodCallName(call)))) {
        return UNRECOGNIZED_VALUE;
      }
      Object path = GradleGroovyFile.getFirstLiteralArgumentValue(call);
      if (path == null) {
        return UNRECOGNIZED_VALUE;
      }
      return new File(path.toString());
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      String path = FileUtil.toSystemIndependentName(((File)value).getPath());
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject())
                    .createStatementFromText(Constants.FILE_METHOD_CALL + "('" + escapeLiteralString(path) + "')"));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return Constants.FILE_METHOD_CALL + "('" + escapeLiteralString(value.toString()) + "')";
    }
  },
  FILE_AS_STRING(File.class, "''") { // Represented in Groovy as '/path/to/file'

    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      if (!(arg instanceof GrLiteral)) {
        return UNRECOGNIZED_VALUE;
      }
      Object value = ((GrLiteral) arg).getValue();
      return value != null ? new File(value.toString()) : null;
    }

    @Override
    public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
      String path = FileUtil.toSystemIndependentName(((File)value).getPath());
      arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createLiteralFromValue(path));
    }

    @Override
    @NotNull
    public String convertValueToExpression(@NotNull Object value) {
      return "'" + escapeLiteralString(value.toString()) + "'";
    }
  },
  REFERENCE(String.class, "reference") {
    @Nullable
    @Override
    public Object getValue(@NotNull GroovyPsiElement arg) {
      return arg.getText();
    }
  };

  private final Class<?> myNativeType;
  private final String myDefaultValue;

  BuildFileKeyType(@NotNull Class<?> nativeType, @NotNull String defaultValue) {
    myNativeType = nativeType;
    myDefaultValue = defaultValue;
  }

  @NotNull
  public Class<?> getNativeType() {
    return myNativeType;
  }

  @NotNull
  public String convertValueToExpression(@NotNull Object value) {
    return value.toString();
  }

  @Nullable
  public Object getValue(@NotNull GroovyPsiElement arg) {
    if (!(arg instanceof GrLiteral)) {
      return UNRECOGNIZED_VALUE;
    }
    return ((GrLiteral) arg).getValue();
  }

  public void setValue(@NotNull GroovyPsiElement arg, @NotNull Object value) {
    arg.replace(GroovyPsiElementFactory.getInstance(arg.getProject()).createExpressionFromText(value.toString()));
  }

  private static class Constants {
    private static final String FILE_METHOD_CALL = "file";
  }
}
