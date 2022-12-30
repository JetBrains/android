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

import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.cpu.nodemodel.SyscallModel;
import com.intellij.openapi.diagnostic.Logger;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for parsing full method/function names (String) obtained from symbol tables collected when profiling using simpleperf.
 * The names are parsed into {@link CaptureNodeModel} instances containing the class name, method name and signature.
 */
public final class NodeNameParser {

  private static final Pattern JAVA_SEPARATOR_PATTERN = Pattern.compile("\\.");

  private static final String[] COMMON_PATH_PREFIXES = {"/apex/", "/system/", "/vendor/"};
  private static final String[] COMMON_PATH_PREFIXES_DISPLAY = {"/apex/*", "/system/*", "/vendor/*"};

  private static Logger getLogger() {
    return Logger.getInstance(NodeNameParser.class);
  }

  /**
   * Parses a string representing a full symbol name into its corresponding model. For example:
   * "namespace::Class::Fun<int>(params)" is parsed into a {@link CppFunctionModel}
   * "java.util.String.toString" is parsed into a {@link JavaMethodModel}
   * "ioctl" is parsed into a {@link SyscallModel}
   *
   * @param fullName      name to be parsed into a {@link CaptureNodeModel}.
   * @param isUserWritten whether the symbol is part of the user-written code.
   * @param fileName      name of the ELF file containing the instruction corresponding to the function. Null if it doesn't apply.
   * @param vAddress      virtual address of the instruction in {@code fileName}.
   */
  static CaptureNodeModel parseNodeName(@NotNull String fullName, boolean isUserWritten, @Nullable String fileName, long vAddress) {
    // C/C++ methods are represented as "Namespace::Class::MethodName()" in simpleperf. Check for the presence of "(".
    if (fullName.contains("(")) {
      return createCppFunctionModel(fullName, isUserWritten, fileName, vAddress);
    }
    else if (fullName.contains(".")) {
      // Method is in the format "java.package.Class.method". Parse it into a JavaMethodModel.
      return createJavaMethodModel(fullName);
    }
    else {
      // Node represents a syscall.
      return new SyscallModel(tagFromFileName(fileName), fullName);
    }
  }

  static CaptureNodeModel parseNodeName(@NotNull String fullName, boolean isUserWritten) {
    return parseNodeName(fullName, isUserWritten, null, -1);
  }

  /**
   * C++ function names are usually in the format namespace::Class::Fun(params). Sometimes, they also include
   * return type and template information, e.g. void namespace::Class::Fun<int>(params). We need to handle all the cases and parse
   * the function name into a {@link CppFunctionModel}.
   */
  @NotNull
  public static CppFunctionModel createCppFunctionModel(String functionFullName, boolean isUserWritten, String fileName, long vAddress) {
    // First, extract the function parameters, which should be between the matching '(' and  the last index of ')' parentheses.
    String parameters = "";
    int paramsEndIndex = functionFullName.lastIndexOf(')');
    if (paramsEndIndex != -1) {
      int paramsStartIndex = findMatchingOpeningParenthesisIndex(functionFullName, paramsEndIndex);
      if (paramsStartIndex > 0) {
        // Make sure not to include the indexes of "(" and ")" when creating the parameters substring.
        parameters = functionFullName.substring(paramsStartIndex + 1, paramsEndIndex);
        // Remove the parameters and everything that comes after it, such as const/volatile modifiers.
        functionFullName = functionFullName.substring(0, paramsStartIndex);
      }
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
      .setFileName(fileName)
      .setTag(tagFromFileName(fileName))
      .setVAddress(vAddress)
      .build();
  }

  @NotNull
  public static CppFunctionModel createCppFunctionModel(String functionFullName, boolean isUserWritten) {
    return createCppFunctionModel(functionFullName, isUserWritten, null, -1);
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
    return ('a' <= ch && ch <= 'z') || ('A' <= ch && ch <= 'Z') || Character.isDigit(ch) || ch == '_';
  }

  /**
   * Simplifies a C++ symbol (e.g. function name, or namespace) by removing the template instantiation information including template
   * arguments. Essentially, removes angle brackets and everything between them. For example:
   * "Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)" -> "Type1 Type2::FuncTemplate(Type4)"
   *
   * If it can't find matching angle brackets, falls back to the full symbol string.
   */
  @NotNull
  private static String removeTemplateInfo(@NotNull String fullSymbol) {
    StringBuilder filteredName = new StringBuilder();
    int open = 0;
    for (int i = 0; i < fullSymbol.length(); ++i) {
      char ch = fullSymbol.charAt(i);
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
      getLogger().warn(String.format("Native function signature (%s) without matching angle brackets.", fullSymbol));
      return fullSymbol;
    }
    return filteredName.toString();
  }

  /**
   * Returns the index of the opening parenthesis, i.e. '(', that matches the closing one, i.e. ')' of a string representing a function
   * name, or -1 if a corresponding opening parenthesis can't be found.
   */
  private static int findMatchingOpeningParenthesisIndex(String functionName, int endIndex) {
    assert functionName.charAt(endIndex) == ')';
    int count = 0;
    for (int i = endIndex; i >= 0; --i) {
      Character ch = functionName.charAt(i);
      if (ch == ')') {
        count++;
      }
      else if (ch == '(') {
        count--;
      }
      if (count == 0) {
        return i;
      }
    }
    getLogger().warn(String.format("Native function signature (%s) without matching parentheses.", functionName));
    return -1;
  }

  /**
   * Receives a full method name and returns a {@link JavaMethodModel} containing its class name and its (simple) name.
   * @param fullName The method's full qualified name (e.g. java.lang.Object.equals)
   */
  private static JavaMethodModel createJavaMethodModel(String fullName) {
    // First, we should extract the method name, which is the name after the last "." character.
    String[] splittedMethod = JAVA_SEPARATOR_PATTERN.split(fullName);
    int methodNameIndex = splittedMethod.length - 1;
    String methodName = splittedMethod[methodNameIndex];

    // Everything else composes the class name.
    StringBuilder className = new StringBuilder(splittedMethod[0]);
    for (int i = 1; i < methodNameIndex; i++) {
      className.append(".");
      className.append(splittedMethod[i]);
    }
    return new JavaMethodModel(methodName, className.toString(), "");
  }

  private static String tagFromFileName(String fileName) {
    return fileName == null ? null :
           IntStream.range(0, COMMON_PATH_PREFIXES.length)
             .filter(i -> fileName.startsWith(COMMON_PATH_PREFIXES[i]))
             .mapToObj(i -> COMMON_PATH_PREFIXES_DISPLAY[i])
             .findAny().orElse(fileName);
  }
}
