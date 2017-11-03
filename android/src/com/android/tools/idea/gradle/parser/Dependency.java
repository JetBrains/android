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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringContent;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.parser.BuildFileKey.escapeLiteralString;

/**
 * Represents a dependency statement in a Gradle build file. Dependencies have a scope (which defines what types of compiles the
 * dependency is relevant for), a type (e.g. Maven, local jar file, etc.), and dependency-specific data.
 */
public class Dependency extends BuildFileStatement {
  private static final Logger LOG = Logger.getInstance(Dependency.class);

  @NonNls private static final String FILE_TREE_BASE_DIR_PROPERTY = "dir";
  @NonNls private static final String FILE_TREE_INCLUDE_PATTERN_PROPERTY = "include";

  public enum Scope {
    // Compatibility scopes (Gradle < 4)
    COMPILE("Compile", "compile", true, true, true),
    PROVIDED("Provided", "provided", true, false, true),
    APK("APK", "apk", true, false, true),
    ANDROID_TEST_COMPILE("Test compile", "androidTestCompile", true, false, true),
    DEBUG_COMPILE("Debug compile", "debugCompile", true, false, true),
    RELEASE_COMPILE("Release compile", "releaseCompile", true, false, true),
    RUNTIME("Runtime", "runtime", false, true, true),
    TEST_COMPILE("Test compile", "testCompile", false, true, true),
    TEST_RUNTIME("Test runtime", "testRuntime", false, true, true),

    // New scopes (Gradle >= 4)
    IMPLEMENTATION("Implementation", "implementation", true, true, false),
    API("API", "api", true, true, false),
    COMPILE_ONLY("Compile only", "compileOnly", true, false, false),
    RUNTIME_ONLY("Runtime only", "runtimeOnly", true, false, false),
    UNIT_TEST_IMPLEMENTATION("Unit Test implementation", "testImplementation", true, false, false),
    ANDROID_TEST_IMPLEMENTATION("Test implementation", "androidTestImplementation", true, false, false),
    DEBUG_IMPLEMENTATION("Debug implementation", "debugImplementation", true, false, false),
    RELEASE_IMPLEMENTATION("Release implementation", "releaseImplementation", true, false, false),
    TEST_COMPILE_ONLY("Test compile only", "testCompileOnly", false, true, false),
    TEST_RUNTIME_ONLY("Test runtime only", "testRuntimeOnly", false, true, false);

    private final String myGroovyMethodCall;
    private final String myDisplayName;

    private final boolean myAndroidScope; // True if this is used in Android modules
    private final boolean myJavaScope; // True if this is used in plain Java modules
    private final boolean myCompat; // True if this is used in compat mode (Gradle version < 4)

    Scope(@NotNull String displayName, @NotNull String groovyMethodCall, boolean androidScope, boolean javaScope, boolean compat) {
      myDisplayName = displayName;
      myGroovyMethodCall = groovyMethodCall;
      myAndroidScope = androidScope;
      myJavaScope = javaScope;
      myCompat = compat;
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

    public boolean isAndroidScope() {
      return myAndroidScope;
    }

    public boolean isJavaScope() {
      return myJavaScope;
    }

    public boolean isCompat() {
      return myCompat;
    }

    @Override
    @NotNull
    public String toString() {
      return myDisplayName;
    }

    @NotNull
    public static Scope getDefaultScope(@NotNull Project project) {
      return GradleUtil.useCompatibilityConfigurationNames(project) ? COMPILE : IMPLEMENTATION;
    }
  }

  public enum Type {
    FILES,
    FILETREE,
    EXTERNAL,
    MODULE
  }

  public Scope scope;
  public Type type;
  public Object data;
  public String extraClosure;

  public Dependency(@NotNull Scope scope, @NotNull Type type, @NotNull Object data, @Nullable String extraClosure) {
    this.scope = scope;
    this.type = type;
    this.data = data;
    this.extraClosure = extraClosure;
  }

  public Dependency(@NotNull Scope scope, @NotNull Type type, @NotNull Object data) {
    this(scope, type, data, null);
  }

  @Override
  @NotNull
  public List<PsiElement> getGroovyElements(@NotNull GroovyPsiElementFactory factory) {
    String extraGroovyCode;
    switch (type) {
      case EXTERNAL:
        if (extraClosure != null) {
          extraGroovyCode = "(" + escapeAndQuote(data) + ")";
        } else {
          extraGroovyCode =  " " + escapeAndQuote(data);
        }
        break;
      case MODULE:
        if (data instanceof Map) {
          //noinspection unchecked
          extraGroovyCode = " project(" + GradleGroovyFile.convertMapToGroovySource((Map<String, Object>)data) + ")";
        } else {
          extraGroovyCode = " project(" + escapeAndQuote(data) + ")";
        }
        if (extraClosure != null) {
          // If there's a closure with exclusion rules, then we need extra parentheses:
          // compile project(':foo') { ... } is not valid Groovy syntax
          // compile(project(':foo')) { ... } is correct
          extraGroovyCode = "(" + extraGroovyCode.substring(1) + ")";
        }
        break;
      case FILES:
        extraGroovyCode = " files(" + escapeAndQuote(data) + ")";
        break;
      case FILETREE:
        //noinspection unchecked
        extraGroovyCode = " fileTree(" + GradleGroovyFile.convertMapToGroovySource((Map<String, Object>)data) + ")";
        break;
      default:
        extraGroovyCode = "";
        break;
    }
    GrStatement statement = factory.createStatementFromText(scope.getGroovyMethodCall() + extraGroovyCode);
    if (statement instanceof GrMethodCall && extraClosure != null) {
      statement.add(factory.createClosureFromText(extraClosure));
    }
    return ImmutableList.of(statement);
  }

  /**
   * Groovy has either plain strings or rich gstrings, i.e. it's possible to write <code>'position number $index'</code>
   * (single quotes - plain string) or <code>"position number $index"</code> (double quotes, 'index' variable value will be inserted
   * in runtime).
   * <p/>
   * This method escapes given string content and surrounds it by correct quotes (trying to guess if it's a pain string or gstring).
   */
  @NotNull
  private static String escapeAndQuote(@Nullable Object data) {
    if (data == null) {
      return "''";
    }
    String stringContent = data.toString();
    boolean gstring = false;
    // We assume that given string is a gstring if it has an unescaped dollar sign.
    for (int i = stringContent.indexOf('$'); i >= 0 && i < stringContent.length(); i = stringContent.indexOf('$', i + 1)) {
      if (i <= 0 || stringContent.charAt(i - 1) != '\\') {
        gstring = true;
        break;
      }
    }
    char quote = gstring ? '"' : '\'';
    return quote + escapeLiteralString(stringContent) + quote;
  }

  /**
   * Returns true if the given dependency "matches" or "is covered" by this dependency. It will return true if they are equal
   * in the {@link #equals(Object)}} sense, but it does some broader matching as well, in order to aid the use case of merging new
   * dependencies into existing build files.
   *
   * <ul>
   *   <li>For Maven-style dependencies, it only checks the group and name parts of the coordinate; it ignores version number
   *   and packaging. This allows us to gloss over differences between minor version numbers, or whether a version number uses
   *   + syntax, by giving up on it altogether. It also glosses over whether a dependency has explicit packaging (e.g. @aar)
   *   specified or not by also giving up on it.</li>
   *   <li>For module dependencies, it ignores the leading colon in the Gradle module specification when comparing.</li>
   *   <li>For files('...') dependencies, it will match a filetree dependency that includes the same file; e.g.
   *   files('libs/foo.jar') is matched by fileTree(dir: 'lib', include: ['*.jar', '*.aar'])</li>
   *   <li>It has hardcoded knowledge that the appcompat-v7 library includes support-v4.</li>
   * </ul>
   */
  public boolean matches(@NotNull Dependency dependency) {
    if (equals(dependency)) {
      return true;
    }
    if (scope != dependency.scope) {
      return false;
    }
    String s1 = data.toString();
    String s2 = dependency.data.toString();
    switch(type) {
      default:
      case MODULE:
        if (dependency.type != Type.MODULE) {
          return false;
        }

        if (data instanceof Map) {
          //noinspection unchecked
          s1 = GradleGroovyFile.convertMapToGroovySource((Map<String, Object>)data).replaceAll("path: ':", "path: '");
        }
        if (dependency.data instanceof Map) {
          //noinspection unchecked
          s2 = GradleGroovyFile.convertMapToGroovySource((Map<String, Object>)dependency.data).replaceAll("path: ':", "path: '");
        }

        s1 = StringUtil.trimStart(s1, ":");
        s2 = StringUtil.trimStart(s2, ":");
        return (s1.equals(s2));
      case EXTERNAL:
        if (dependency.type != Type.EXTERNAL) {
          return false;
        }

        // Special hardcoded case: com.android.support:appcompat-v7 includes com.android.support:support-v4
        if (s1.startsWith(SdkConstants.APPCOMPAT_LIB_ARTIFACT) && s2.startsWith(SdkConstants.SUPPORT_LIB_ARTIFACT)) {
          return true;
        }

        // Maven dependencies match if they share the same group and artifact. We ignore version and packaging.
        String[] tokens1 = s1.split(":");
        String[] tokens2 = s2.split(":");
        if (tokens1.length < 2 || tokens2.length < 2) {
          return false;
        }

        return tokens1[0].equals(tokens2[0]) && tokens1[1].equals(tokens2[1]);
      case FILES:
        if (dependency.type != Type.FILES) {
          return false;
        }
        return FileUtil.pathsEqual(s1, s2);
      case FILETREE:
        if (dependency.type != Type.FILES) {
          return false;
        }
        //noinspection unchecked
        Map<String, Object> values = (Map<String, Object>)data;
        String dir = (String)values.get(FILE_TREE_BASE_DIR_PROPERTY);
        Object value = values.get(FILE_TREE_INCLUDE_PATTERN_PROPERTY);

        if (value == null) {
          return false;
        }
        //noinspection unchecked
        List<String> includes = (value instanceof List) ? (List<String>)value : ImmutableList.of(value.toString());
        if (dir == null || includes == null) {
          return false;
        }
        File baseDir = new File(dir);
        File depFile = new File(s2);
        File depDir = depFile.getParentFile();
        if (depDir == null) {
          return false;
        }
        if (FileUtil.filesEqual(baseDir, depDir)) {
          for (String glob : includes) {
            Pattern pattern = Pattern.compile(FileUtil.convertAntToRegexp(glob));
            if (pattern.matcher(depFile.getName()).matches()) {
              return true;
            }
          }
        }
        return false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) { return true; }
    if (o == null || getClass() != o.getClass()) { return false; }

    Dependency that = (Dependency)o;

    if (data != null ? !data.equals(that.data) : that.data != null) { return false; }
    if (scope != that.scope) { return false; }
    if (type != that.type) { return false; }

    return true;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(scope, type, data);
  }

  @Override
  public String toString() {
    return "Dependency {" +
           "myScope=" + scope +
           ", myType=" + type +
           ", myData='" + data + '\'' +
           '}';
  }

  public @NotNull String getValueAsString() {
    return data.toString();
  }

  public static ValueFactory getFactory() {
    return new DependencyFactory();
  }

  private static class DependencyFactory extends BuildFileStatementFactory {
    @NotNull
    @Override
    public List<BuildFileStatement> getValues(@NotNull PsiElement statement) {
      if (!(statement instanceof GrMethodCall)) {
        return getUnparseableStatements(statement);
      }

      GrMethodCall call = (GrMethodCall)statement;
      Dependency.Scope scope = Dependency.Scope.fromMethodCall(GradleGroovyFile.getMethodCallName(call));
      if (scope == null) {
        return getUnparseableStatements(statement);
      }

      String extraClosure = null;
      GrClosableBlock[] closureArguments = ((GrMethodCall)statement).getClosureArguments();
      if (closureArguments.length > 0) {
        extraClosure = closureArguments[0].getText();
      }

      GrArgumentList argumentList = call.getArgumentList();
      List<BuildFileStatement> dependencies = Lists.newArrayList();

      GroovyPsiElement[] allArguments = argumentList.getAllArguments();
      if (allArguments.length == 1) {
        GroovyPsiElement element = allArguments[0];
        if (element instanceof GrMethodCall) {
          GrMethodCall method = (GrMethodCall)element;
          String methodName = GradleGroovyFile.getMethodCallName(method);
          if ("project".equals(methodName)) {
            Object value = GradleGroovyFile.getFirstLiteralArgumentValue(method);
            if (value != null) {
              dependencies.add(new Dependency(scope, Dependency.Type.MODULE, value.toString(), extraClosure));
            } else {
              Map<String, Object> values = GradleGroovyFile.getNamedArgumentValues(method);
              if (!values.isEmpty()) {
                dependencies.add(new Dependency(scope, Type.MODULE, values, extraClosure));
              }
            }
          } else if ("files".equals(methodName)) {
            for (Object o : GradleGroovyFile.getLiteralArgumentValues(method)) {
              dependencies.add(new Dependency(scope, Dependency.Type.FILES, o.toString(), extraClosure));
            }
          } else if ("fileTree".equals(methodName)) {
            Map<String, Object> values = GradleGroovyFile.getNamedArgumentValues(method);
            dependencies.add(new Dependency(scope, Type.FILETREE, values, extraClosure));
          } else {
            // Oops, we didn't know how to parse this.
            LOG.warn("Didn't know how to parse dependency method call " + methodName);
          }
        } else if (element instanceof GrLiteral) {
          Object value = ((GrLiteral)element).getValue();
          if (value != null) {
            dependencies.add(new Dependency(scope, Dependency.Type.EXTERNAL, value.toString(), extraClosure));
          }
          else if (element instanceof GrString) {
            GroovyPsiElement[] contentParts = ((GrString)element).getAllContentParts();
            final StringBuilder buffer = new StringBuilder();
            for (GroovyPsiElement part : contentParts) {
              if (part instanceof GrStringContent || part instanceof GrStringInjection) {
                buffer.append(part.getText());
              }
            }
            if (buffer.length() > 0) {
              dependencies.add(new Dependency(scope, Dependency.Type.EXTERNAL, buffer.toString(), extraClosure));
            }
          }
        } else {
          return getUnparseableStatements(statement);
        }
      }
      else if (allArguments.length > 1) {
        Map<String, Object> attributes = GradleGroovyFile.getNamedArgumentValues(call);
        if (attributes.isEmpty()) {
          return getUnparseableStatements(statement);
        }
        Object groupId = attributes.get("group");
        Object artifactId = attributes.get("name");
        Object version = attributes.get("version");
        Object ext = attributes.get("ext");
        if (groupId == null || artifactId == null || version == null) {
          return getUnparseableStatements(statement);
        }
        String coordinate = Joiner.on(":").join(groupId, artifactId, version);
        if (ext != null) {
          coordinate = coordinate + "@" + ext;
        }
        dependencies.add(new Dependency(scope, Dependency.Type.EXTERNAL, coordinate, extraClosure));
      }
      return dependencies;
    }
  }
}
