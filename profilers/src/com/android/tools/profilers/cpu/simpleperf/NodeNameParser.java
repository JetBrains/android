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
package com.android.tools.profilers.cpu.simpleperf;

import com.android.tools.profilers.cpu.nodemodel.*;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Responsible for parsing full method/function names (String) obtained from symbol tables collected when profiling using simpleperf.
 * The names are parsed into {@link CaptureNodeModel} instances containing the class name, method name and signature.
 */
public class NodeNameParser {

  private static final Pattern NATIVE_SEPARATOR_PATTERN = Pattern.compile("::");

  private static final Pattern JAVA_SEPARATOR_PATTERN = Pattern.compile("\\.");

  /**
   * Parses a string representing a full symbol name into its corresponding model. For example:
   * "namespace::Class::Fun<int>(params)" is parsed into a {@link CppFunctionModel}
   * "java.util.String.toString" is parsed into a {@link JavaMethodModel}
   * "ioctl" is parsed into a {@link SyscallModel}
   *
   * @param fullName      name to be parsed into a {@link CaptureNodeModel}.
   * @param isUserWritten whether the symbol is part of the user-written code
   */
  static CaptureNodeModel parseNodeName(String fullName, boolean isUserWritten) {
    // C/C++ methods are represented as Namespace::Class::MethodName() in simpleperf. Check for the presence of "(".
    if (fullName.contains("(")) {
      return parseCppFunctionName(fullName, isUserWritten);
    }
    else if (fullName.contains(".")) {
      // Method is in the format java.package.Class.method. Parse it into a CaptureNodeModel.
      ModelInfo modelInfo = createModelInfo(fullName, ".", JAVA_SEPARATOR_PATTERN);
      return new JavaMethodModel(modelInfo.getName(), modelInfo.getClassOrNamespace());
    }
    else {
      // Node represents a syscall.
      return new SyscallModel(fullName);
    }
  }

  /**
   * C++ function names are usually in the format namespace::Class::Fun(params). Sometimes, they also include
   * return type and template information, e.g. void namespace::Class::Fun<int>(params). We need to handle all the cases and parse
   * the function name into a {@link CppFunctionModel}.
   */
  @NotNull
  public static CppFunctionModel parseCppFunctionName(String functionFullName, boolean isUserWritten) {
    // First, extract the function parameters, which should be between the matching '(' and  the last index of ')' parentheses.
    String parameters = "";
    int paramsEndIndex = functionFullName.lastIndexOf(')');
    if (paramsEndIndex != -1) {
      int paramsStartIndex = findMatchingOpeningCharacterIndex(functionFullName, '(', ')', paramsEndIndex);
      // Make sure not to include the indexes of "(" and ")" when creating the parameters substring.
      parameters = functionFullName.substring(paramsStartIndex + 1, paramsEndIndex);
      // Remove the parameters and everything that comes after it, such as const/volatile modifiers.
      functionFullName = functionFullName.substring(0, paramsStartIndex);
    }

    // Then, strip out the return type, we assume that the return type is separated by the first space,
    // however the space in "operator bool()" or "someNamespace::operator bool()" is an exception.
    int returnTypeSeparatorIndex = separatorIndexOutsideOfTemplateInfo(functionFullName, " ", false);
    if (returnTypeSeparatorIndex >= 0) {
      String returnType = functionFullName.substring(0, returnTypeSeparatorIndex);
      if (!returnType.equals("operator") && !returnType.endsWith("::operator")) {
        functionFullName = functionFullName.substring(returnTypeSeparatorIndex + 1);
      }
    }

    String name = functionFullName;
    String classOrNamespace = "";

    int methodNameSeparatorIndex = separatorIndexOutsideOfTemplateInfo(functionFullName, "::", true);
    if (methodNameSeparatorIndex != -1) {
      classOrNamespace = functionFullName.substring(0, methodNameSeparatorIndex);
      name = functionFullName.substring(methodNameSeparatorIndex + 2);
    }

    return new CppFunctionModel.Builder(isOperatorOverload(name) ? name : removeTemplateInfo(name))
      .setClassOrNamespace(removeTemplateInfo(classOrNamespace))
      .setIsUserCode(isUserWritten)
      .setParameters(removeTemplateInfo(parameters))
      .build();
  }

  /**
   * @param functionFullName - a function full name where to search for the occurrence index.
   * @param separator        - a separator which should be searched.
   * @param lastIndex        - whether to return the last occurrence index or the first.
   * @return occurrence index of {@param separator} which is outside of all CPP templates in the given {@param functionFullName}.
   */
  private static int separatorIndexOutsideOfTemplateInfo(String functionFullName, String separator, boolean lastIndex) {
    int open = 0;
    int lastOccurrenceIndex = -1;
    for (int index = 0; index <= functionFullName.length() - separator.length(); index++) {
      char ch = functionFullName.charAt(index);
      if (ch == '<') {
        ++open;
      }
      else if (ch == '>') {
        --open;
      }
      else if (open == 0 && functionFullName.startsWith(separator, index)) {
        if (!lastIndex) {
          return index;
        }
        lastOccurrenceIndex = index;
      }
    }
    return lastOccurrenceIndex;
  }

  /**
   * @param functionName - the given function name, i.e "myMethod", "my_method", "myMethod<int>", "operator<<", "my_operator"
   * @return true, if the given {@param functionName} describes an operator overloading.
   */
  private static boolean isOperatorOverload(@NotNull String functionName) {
    final String operator = "operator";
    if (!functionName.startsWith(operator)) {
      return false;
    }
    // whether function's name is operator.
    if (operator.length() == functionName.length()) {
      return true;
    }

    return !isCppIdentifierChar(functionName.charAt(operator.length()));
  }

  private static boolean isCppIdentifierChar(char ch) {
    // See: http://en.cppreference.com/w/cpp/language/identifiers
    return ('a' <= ch && ch >= 'z') || ('A' <= ch && ch >= 'Z') || Character.isDigit(ch) || ch == '_';
  }

  /**
   * Simplifies a C++ function name by removing the template instantiation information including template arguments.
   * Essentially, removes angle brackets and everything between them. For example:
   * "Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)" -> "Type1 Type2::FuncTemplate(Type4)"
   */
  @NotNull
  private static String removeTemplateInfo(@NotNull String functionFullName) {
    StringBuilder filteredName = new StringBuilder();
    int open = 0;
    for (int i = 0; i < functionFullName.length(); ++i) {
      char ch = functionFullName.charAt(i);
      if (ch == '<') {
        ++open;
      }
      else if (ch == '>') {
        --open;
      }
      else if (open == 0) {
        filteredName.append(ch);
      }
    }

    if (open != 0) {
      throw new IllegalStateException("Native function signature must have matching parentheses and brackets.");
    }
    return filteredName.toString();
  }

  /**
   * Given the opening and closing characters (e.g. '<' and '>', or '(' and ')'), returns the index of the opening character
   * that matches the closing character of a string representing a function name.
   */
  private static int findMatchingOpeningCharacterIndex(String functionName, char opening, char closing, int endIndex) {
    assert functionName.charAt(endIndex) == closing;
    int count = 0;
    for (int i = endIndex; i >= 0; --i) {
      Character ch = functionName.charAt(i);
      if (ch == closing) {
        count++;
      }
      else if (ch == opening) {
        count--;
      }
      if (count == 0) {
        return i;
      }
    }
    throw new IllegalStateException("Native function signature must have matching parentheses and brackets.");
  }

  /**
   * Receives a full method/function name and returns a {@link ModelInfo} containing its class name (or namespace), and its (simple) name.
   *
   * @param fullName         The method's (or function's) full qualified name (e.g. java.lang.Object.equals)
   * @param separator        The namespace/package separator (e.g. "." or "::")
   * @param separatorPattern The regex pattern used to split the method full name (e.g. "\\." or "::")
   */
  private static ModelInfo createModelInfo(String fullName, String separator, Pattern separatorPattern) {
    // First, we should extract the method name, which is the name after the last "." character.
    String[] splittedMethod = separatorPattern.split(fullName);
    int methodNameIndex = splittedMethod.length - 1;
    String methodName = splittedMethod[methodNameIndex];

    // Everything else composes the namespace and/or class name.
    StringBuilder classOrNamespace = new StringBuilder(splittedMethod[0]);
    for (int i = 1; i < methodNameIndex; i++) {
      classOrNamespace.append(separator);
      classOrNamespace.append(splittedMethod[i]);
    }
    return new ModelInfo(methodName, classOrNamespace.toString());
  }

  /**
   * Stores the name and the class/namespace of a Java method or native function model.
   */
  private static class ModelInfo {
    @NotNull private final String myName;
    @NotNull private final String myClassOrNamespace;

    public ModelInfo(@NotNull String name, @NotNull String classOrNamespace) {
      myName = name;
      myClassOrNamespace = classOrNamespace;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @NotNull
    public String getClassOrNamespace() {
      return myClassOrNamespace;
    }
  }
}
