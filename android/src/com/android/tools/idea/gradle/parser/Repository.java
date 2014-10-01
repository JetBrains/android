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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;

import java.util.List;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;

/**
 * Represents a Maven repository where the Android plugin or library dependencies may be found.
 */
public class Repository extends BuildFileStatement {
  public enum Type {
    MAVEN_CENTRAL("mavenCentral"),
    MAVEN_LOCAL("mavenLocal"),
    JCENTER("jcenter"),
    URL("maven");

    private final String myCallName;

    Type(String callName) {
      myCallName = callName;
    }

    public String getCallName() {
      return myCallName;
    }
  }

  public final String myUrl;
  public final Type myType;

  public static BuildFileStatement parse(@NotNull String s, @NotNull Project project) {
    String methodCallName = s;
    if (methodCallName.endsWith("()")) {
      methodCallName = methodCallName.substring(0, methodCallName.length() - 2);
    }
    if (methodCallName.equalsIgnoreCase(Type.MAVEN_CENTRAL.getCallName())) {
      return new Repository(Type.MAVEN_CENTRAL, null);
    } else if (methodCallName.equalsIgnoreCase(Type.MAVEN_LOCAL.getCallName())) {
      return new Repository(Type.MAVEN_LOCAL, null);
    } else if (methodCallName.equalsIgnoreCase(Type.JCENTER.getCallName())) {
      return new Repository(Type.JCENTER, null);
    } else if (methodCallName.equalsIgnoreCase(Type.URL.getCallName())) {
      return new Repository(Type.URL, s);
    } else if (s.startsWith("'") && s.endsWith("'")) {
      return new Repository(Type.URL, s.substring(1, s.length() - 1));
    } else if (s.indexOf('.') >= 0 && s.indexOf('{') == -1) {
      return new Repository(Type.URL, s);
    } else {
      return new UnparseableStatement(s, project);
    }
  }

  public Repository(@NotNull Type type, @Nullable String url) {
    myType = type;
    myUrl = url;
  }

  @NotNull
  @Override
  public List<PsiElement> getGroovyElements(@NotNull GroovyPsiElementFactory factory) {
    String callName = myType.getCallName();
    String extraGroovyCode;
    switch(myType) {
      case MAVEN_CENTRAL:
      case MAVEN_LOCAL:
      case JCENTER:
      default:
        extraGroovyCode = "()";
        break;
      case URL:
        extraGroovyCode = " { url '" + escapeLiteralString(myUrl) + "' }";
        break;
    }
    return ImmutableList.of((PsiElement)factory.createStatementFromText(callName + extraGroovyCode));
  }

  @Override
  public String toString() {
    switch (myType) {
      case MAVEN_CENTRAL:
      case MAVEN_LOCAL:
      case JCENTER:
        return myType.getCallName();
      case URL:
        return "'" + myUrl + "'";
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }

    Repository that = (Repository)o;

    if (myType != that.myType) { return false; }
    if (myUrl != null ? !myUrl.equals(that.myUrl) : that.myUrl != null) { return false; }

    return true;
  }

  @Override
  public int hashCode() {
    int result = myUrl != null ? myUrl.hashCode() : 0;
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }

  public static Factory getFactory() {
    return new Factory();
  }

  public static class Factory extends BuildFileStatementFactory {
    @Nullable
    @Override
    public List<BuildFileStatement> getValues(@NotNull PsiElement statement) {
      if (!(statement instanceof GrMethodCall)) {
        return getUnparseableStatements(statement);
      }
      GrMethodCall methodCall = (GrMethodCall)statement;
      List<BuildFileStatement> list = Lists.newArrayList();
      String callName = GradleGroovyFile.getMethodCallName(methodCall);
      if (Type.MAVEN_CENTRAL.getCallName().equals(callName)) {
        list.add(new Repository(Type.MAVEN_CENTRAL, null));
      } else if (Type.MAVEN_LOCAL.getCallName().equals(callName)) {
        list.add(new Repository(Type.MAVEN_LOCAL, null));
      } else if (Type.JCENTER.getCallName().equals(callName)) {
        list.add(new Repository(Type.JCENTER, null));
      } else if (Type.URL.getCallName().equals(callName)) {
        // Handles repositories of the form maven('www.foo.com', 'www.fee.com')
        Iterable<Object> literals = GradleGroovyFile.getLiteralArgumentValues(methodCall);
        if (!Iterables.isEmpty(literals)) {
          for (Object literal : literals) {
            list.add(new Repository(Type.URL, literal.toString()));
          }
        } else {
          // Handles repositories of the form:
          //
          // maven {
          //  url 'www.foo.com'
          //  url 'www.fee.com'
          // }
          GrClosableBlock cc = GradleGroovyFile.getMethodClosureArgument(methodCall);
          if (cc != null) {
            Iterable<GrMethodCall> methodCalls = GradleGroovyFile.getMethodCalls(cc);
            Iterable<GrMethodCall> urlMethodCalls = GradleGroovyFile.getMethodCalls(cc, "url");
            if (Iterables.size(methodCalls) != Iterables.size(urlMethodCalls)) {
              // If there's something other than a "url" in there, that can mean something like credentials:
              // maven {
              //   url 'www.foo.com'
              //   credentials {
              //     username 'user'
              //     password 'password'
              //   }
              // }
              // Just punt and treat the statement as unparseable.
              return getUnparseableStatements(statement);
            }

            for (GrMethodCall call : methodCalls) {
              Iterable<Object> values = GradleGroovyFile.getLiteralArgumentValues(call);
              for (Object value : values) {
                list.add(new Repository(Type.URL, value.toString()));
              }
            }
          }
        }
        if (list.isEmpty()) {
          return getUnparseableStatements(statement);
        }
      } else {
        return getUnparseableStatements(statement);
      }
      return list;
    }

    @NotNull
    @Override
    public BuildFileStatement parse(@NotNull String s, Project project) {
      return Repository.parse(s, project);
    }
  }
}
