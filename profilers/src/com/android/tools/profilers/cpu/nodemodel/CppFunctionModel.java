/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents characteristics of C/C++ functions.
 */
public class CppFunctionModel extends NativeNodeModel {

  /**
   * Pattern used to separate method parameters.
   */
  private static final Pattern PARAMETERS_SEPARATOR = Pattern.compile(", ");

  /**
   * Function's full class name (e.g. art::interpreter::SomeClass). For functions that don't belong to a particular class (e.g.
   * art::bla::Method), this field stores the full namespace (e.g. art::bla).
   */
  @NotNull private final String myClassOrNamespace;

  /**
   * List of the method's parameters (e.g. ["int", "float"]).
   */
  @NotNull private final List<String> myParameters;

  /**
   * Whether the function is part of user-written code.
   */
  private boolean myIsUserCode;

  private String myFullName;

  private String myId;

  private CppFunctionModel(Builder builder) {
    myName = builder.myName;
    myClassOrNamespace = builder.myClassOrNamespace;
    myParameters = buildParameters(builder.myParameters);
    myIsUserCode = builder.myIsUserCode;
  }

  @NotNull
  public String getClassOrNamespace() {
    return myClassOrNamespace;
  }

  @NotNull
  public List<String> getParameters() {
    return myParameters;
  }

  public boolean isUserCode() {
    return myIsUserCode;
  }

  @Override
  @NotNull
  public String getFullName() {
    // Separator is only needed if we have a class name or namespace, otherwise we're gonna end up with a leading separator.
    // We don't have a class name or a namespace, for instance, for global functions.
    if (myFullName == null) {
      String separator = StringUtil.isEmpty(myClassOrNamespace) ? "" : "::";
      myFullName = String.format("%s%s%s", myClassOrNamespace, separator, myName);
    }
    return myFullName;
  }

  @Override
  @NotNull
  public String getId() {
    if (myId == null) {
      myId = String.format("%s%s", getFullName(), myParameters.toString());
    }
    return myId;
  }

  private static List<String> buildParameters(@NotNull String parameters) {
    List<String> parsedParameters = new ArrayList<>();
    if (parameters.isEmpty()) {
      return parsedParameters;
    }
    Collections.addAll(parsedParameters, PARAMETERS_SEPARATOR.split(parameters));
    return parsedParameters;
  }

  public static class Builder {
    @NotNull private final String myName;
    @NotNull private String myClassOrNamespace;
    private boolean myIsUserCode;
    /**
     * List of the method's parameters, comma separated (e.g. "int, float").
     */
    @NotNull private String myParameters;

    public Builder(@NotNull String name) {
      myName = name;
      myClassOrNamespace = "";
      myParameters = "";
    }

    public Builder setClassOrNamespace(@NotNull String classOrNamespace) {
      myClassOrNamespace = classOrNamespace;
      return this;
    }

    public Builder setParameters(@NotNull String parameters) {
      myParameters = parameters;
      return this;
    }

    public Builder setIsUserCode(boolean isUserCode) {
      myIsUserCode = isUserCode;
      return this;
    }

    public CppFunctionModel build() {
      return new CppFunctionModel(this);
    }
  }
}
