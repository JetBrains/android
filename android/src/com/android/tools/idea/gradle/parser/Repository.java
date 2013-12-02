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

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.List;

/**
 * Represents a Maven repository where the Android plugin or library dependencies may be found.
 */
public class Repository {
  public enum Type {
    MAVEN_CENTRAL("mavenCentral"),
    MAVEN_LOCAL("mavenLocal"),
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

  public Repository(@NotNull String s) {
    if (s.endsWith("()")) {
      s = s.substring(0, s.length() - 2);
    }
    if (s.equalsIgnoreCase(Type.MAVEN_CENTRAL.getCallName())) {
      myType = Type.MAVEN_CENTRAL;
      myUrl = null;
    } else if (s.equalsIgnoreCase(Type.MAVEN_LOCAL.getCallName())) {
      myType = Type.MAVEN_LOCAL;
      myUrl = null;
    } else {
      myType = Type.URL;
      myUrl = s;
    }
  }

  public Repository(@NotNull Type type, @Nullable String url) {
    myType = type;
    myUrl = url;
  }

  @Override
  public String toString() {
    switch (myType) {
      case MAVEN_CENTRAL:
      case MAVEN_LOCAL:
        return myType.getCallName();
      case URL:
        return myUrl;
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

  public static class Factory implements BuildFileKey.ValueFactory<Repository> {
    @NotNull
    @Override
    public List<Repository> getValues(@NotNull GrStatementOwner closure) {
      List<Repository> list = Lists.newArrayList();
      for (GrMethodCall methodCall : GradleGroovyFile.getMethodCalls(closure)) {
        String callName = GradleGroovyFile.getMethodCallName(methodCall);
        if (Type.MAVEN_CENTRAL.getCallName().equals(callName) || Type.MAVEN_LOCAL.getCallName().equals(callName)) {
          list.add(new Repository(callName));
        } else if (Type.URL.getCallName().equals(callName)) {
          // Handles repositories of the form maven('www.foo.com', 'www.fee.com')
          Iterable<Object> literals = GradleGroovyFile.getLiteralArgumentValues(methodCall);
          if (!Iterables.isEmpty(literals)) {
            for (Object literal : literals) {
              list.add(new Repository(literal.toString()));
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
              Iterable<GrMethodCall> methodCalls = GradleGroovyFile.getMethodCalls(cc, "url");
              for (GrMethodCall call : methodCalls) {
                Iterable<Object> values = GradleGroovyFile.getLiteralArgumentValues(call);
                for (Object value : values) {
                  list.add(new Repository(value.toString()));
                }
              }
            }
          }
        }
      }
      return list;
    }

    @Override
    public void setValues(@NotNull GrStatementOwner closure, @NotNull List<Repository> value) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(closure.getProject());
      closure = (GrStatementOwner)closure.replace(factory.createClosureFromText("{}"));
      for (Repository repository : value) {
        String callName = repository.myType.getCallName();
        switch(repository.myType) {
          case MAVEN_CENTRAL:
          case MAVEN_LOCAL:
            closure.addStatementBefore(factory.createStatementFromText(callName + "()"), null);
            break;
          case URL:
            closure.addStatementBefore(factory.createStatementFromText(callName + " { url '" + repository.myUrl + "' }"), null);
            break;
        }
      }
      GradleGroovyFile.reformatClosure(closure);
    }

    @Override
    public boolean canParseValue(@NotNull GrStatementOwner closure) {
      // TODO: Implement
      return true;
    }
  }
}
