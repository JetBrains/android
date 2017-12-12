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
class NodeNameParser {

  private static final Pattern NATIVE_SEPARATOR_PATTERN = Pattern.compile("::");

  private static final Pattern JAVA_SEPARATOR_PATTERN = Pattern.compile("\\.");

  /**
   * Parses a string representing a full symbol name into its corresponding model. For example:
   *    "namespace::Class::Fun<int>(params)" is parsed into a {@link CppFunctionModel}
   *    "java.util.String.toString" is parsed into a {@link JavaMethodModel}
   *    "ioctl" is parsed into a {@link SyscallModel}
   * @param fullName       name to be parsed into a {@link CaptureNodeModel}.
   * @param isUserWritten  whether the symbol is part of the user-written code
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
   * the function name into a {@link CaptureNodeModel}.
   */
  @NotNull
  private static CaptureNodeModel parseCppFunctionName(String functionFullName, boolean isUserWritten) {
    // First, remove template information.
    functionFullName = removeTemplateInformation(functionFullName);

    // Then, extract the function parameters, which should be between parentheses
    int paramsStartIndex = functionFullName.lastIndexOf('(');
    int paramsEndIndex = findMatchingClosingCharacterIndex(functionFullName, '(', ')', paramsStartIndex);
    // Make sure not to include the indexes of "(" and ")" when creating the parameters substring.
    String parameters = functionFullName.substring(paramsStartIndex + 1, paramsEndIndex);
    // Remove the parameters and everything that comes after it, such as const/volatile modifiers.
    functionFullName = functionFullName.substring(0, paramsStartIndex);

    // If the string still contains a whitespace, it's the separator between the return type and the function name.
    int returnTypeSeparatorIndex = functionFullName.indexOf(' ');
    if (returnTypeSeparatorIndex >= 0) {
      functionFullName = functionFullName.substring(returnTypeSeparatorIndex + 1);
    }
    ModelInfo modelInfo;
    // If there is not a "::" separator in the function name, it's part of the global namespace
    boolean isGlobalNamespace = !functionFullName.contains("::");
    if (isGlobalNamespace) {
      modelInfo = new ModelInfo(functionFullName,  "");
    }
    else {
      modelInfo = createModelInfo(functionFullName, "::", NATIVE_SEPARATOR_PATTERN);
    }
    return new CppFunctionModel.Builder(modelInfo.getName())
      .setClassOrNamespace(modelInfo.getClassOrNamespace())
      .setIsUserCode(isUserWritten)
      .setParameters(parameters)
      .build();
  }

  /**
   * Simplifies a C++ function name by removing the templates. Essentially, removes angle brackets and everything between them. For example:
   *    "Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)" -> "Type1 Type2::FuncTemplate(Type4)"
   */
  private static String removeTemplateInformation(String functionFullName) {
    int currentIndex = functionFullName.indexOf('<');
    if (currentIndex < 0) {
      // The function name doesn't contain any template
      return functionFullName;
    }
    StringBuilder filteredName = new StringBuilder(functionFullName.substring(0, currentIndex));

    while (currentIndex < functionFullName.length()) {
      char currentChar = functionFullName.charAt(currentIndex);
      if (currentChar == '<') {
        // Skip template.
        currentIndex = findMatchingClosingCharacterIndex(functionFullName, '<', '>', currentIndex);
      }
      else {
        // If not reading a template, just include the char in the function name.
        filteredName.append(currentChar);
      }
      currentIndex++;
    }
    return filteredName.toString();
  }

  /**
   * Given the opening and closing characters (e.g. '<' and '>', or '(' and ')'), returns the index of the closing character
   * that matches the opening character of a string representing a function name.
   */
  private static int findMatchingClosingCharacterIndex(String functionName, char opening, char closing, int startIndex) {
    // Counter to keep track of the characters we read. If we read an opening character, increment the counter. If we read a closing one,
    // decrement it. Start the counter as 1 to take the first opening character into account. If the counter gets to 0, it means we have
    // found the target index.
    int count = 1;

    int index = startIndex;
    assert functionName.charAt(index) == opening;

    // Iterate backwards until we reach the matching opening parenthesis.
    while (index++ < functionName.length() - 1) {
      if (functionName.charAt(index) == opening) {
        count++;
      }
      else if (functionName.charAt(index) == closing) {
        count--;
      }
      if (count == 0) {
        return index;
      }
    }
    throw new IllegalStateException("Native function signature must have matching parentheses and brackets.");
  }

  /**
   * Receives a full method/function name and returns a {@link ModelInfo} containing its class name (or namespace), and its (simple) name.
   * @param fullName The method's (or function's) full qualified name (e.g. java.lang.Object.equals)
   * @param separator The namespace/package separator (e.g. "." or "::")
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
