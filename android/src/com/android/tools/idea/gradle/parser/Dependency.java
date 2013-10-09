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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a dependency statement in a Gradle build file. Dependencies have a scope (which defines what types of compiles the
 * dependency is relevant for), a type (e.g. Maven, local jar file, etc.), and dependency-specific data.
 */
public class Dependency {
  public enum Scope {
    COMPILE("Compile", "compile"),
    INSTRUMENT_TEST_COMPILE("Test compile", "instrumentTestCompile"),
    DEBUG_COMPILE("Debug compile", "debugCompile"),
    RELEASE_COMPILE("Release compile", "releaseCompile");

    private final String myGroovyMethodCall;
    private final String myDisplayName;

    Scope(@NotNull String displayName, @NotNull String groovyMethodCall) {
      myDisplayName = displayName;
      myGroovyMethodCall = groovyMethodCall;
    }

    public String getGroovyMethodCall() {
      return myGroovyMethodCall;
    }

    @Nullable
    public static Scope fromMethodCall(@NotNull String methodCall) {
      for (Scope scope : values()) {
        if (scope.myGroovyMethodCall.equals(methodCall)) {
          return scope;
        }
      }
      return null;
    }

    @NotNull
    public String getDisplayName() {
      return myDisplayName;
    }

    @Override
    @NotNull
    public String toString() {
      return myDisplayName;
    }
  }

  public enum Type {
    FILES,
    EXTERNAL,
    MODULE
  }

  public Scope scope;
  public Type type;
  public String data;

  public Dependency(@NotNull Scope scope, @NotNull Type type, @NotNull String data) {
    this.scope = scope;
    this.type = type;
    this.data = data;
  }

  @Override
  public String toString() {
    return "Dependency {" +
           "myScope=" + scope +
           ", myType=" + type +
           ", myData='" + data + '\'' +
           '}';
  }
}
