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

import com.android.tools.profilers.cpu.MethodModel;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Responsible for parsing full method names (String) obtained from symbol tables collected when profiling using simpleperf.
 * The names are parsed into {@link MethodModel} instances containing the class name, method name and signature.
 */
public class MethodNameParser {

  private static final Pattern NATIVE_SEPARATOR_PATTERN = Pattern.compile("::");

  private static final Pattern JAVA_SEPARATOR_PATTERN = Pattern.compile("\\.");

  static MethodModel parseMethodName(String methodFullName) {
    if (methodFullName.contains("::")) {
      return parseCppMethodName(methodFullName);
    }
    else if (methodFullName.contains(".")) {
      // Method is in the format java.package.Class.method. Parse it into a MethodModel.
      return createMethodModel(methodFullName, ".", JAVA_SEPARATOR_PATTERN, "");
    }
    else {
      // Method is a syscall. Create a simple method model passing the full name as method name
      return new MethodModel(methodFullName);
    }
  }

  /**
   * C++ method names are usually in the format namespace::Class::Method(params). Sometimes, they also include
   * return type and template information, e.g. void namespace::Class::Method<int>(params). We need to handle all the cases and parse
   * the method name into a {@link MethodModel}.
   */
  @NotNull
  private static MethodModel parseCppMethodName(String methodFullName) {
    // First, remove template information.
    methodFullName = removeTemplateInformation(methodFullName);

    // Then, extract the method's signature, which should be between parentheses
    int signatureStartIndex = methodFullName.lastIndexOf('(');
    int signatureEndIndex = findMatchingClosingCharacterIndex(methodFullName, '(', ')', signatureStartIndex);
    // Make sure not to include the indexes of "(" and ")" when creating the signature substring.
    String signature = methodFullName.substring(signatureStartIndex + 1, signatureEndIndex);
    // Remove the signature and everything that comes after it, such as const/volatile modifiers.
    methodFullName = methodFullName.substring(0, signatureStartIndex);

    // If the string still contains a whitespace, it's the separator between the return type and the method name.
    int returnTypeSeparatorIndex = methodFullName.indexOf(' ');
    if (returnTypeSeparatorIndex >= 0) {
      methodFullName = methodFullName.substring(returnTypeSeparatorIndex + 1);
    }
    MethodModel methodModel = createMethodModel(methodFullName, "::", NATIVE_SEPARATOR_PATTERN, signature);
    methodModel.setNative(true);
    return methodModel;
  }

  /**
   * Simplifies a C++ method name by removing the templates. Essentially, removes angle brackets and everything between them. For example:
   *    "Type1<int> Type2<float>::FuncTemplate<Type3<2>>(Type4<bool>)" -> "Type1 Type2::FuncTemplate(Type4)"
   */
  private static String removeTemplateInformation(String methodFullName) {
    int currentIndex = methodFullName.indexOf('<');
    if (currentIndex < 0) {
      // The method name doesn't contain any template
      return methodFullName;
    }
    StringBuilder filteredName = new StringBuilder(methodFullName.substring(0, currentIndex));

    while (currentIndex < methodFullName.length()) {
      char currentChar = methodFullName.charAt(currentIndex);
      if (currentChar == '<') {
        // Skip template.
        currentIndex = findMatchingClosingCharacterIndex(methodFullName, '<', '>', currentIndex);
      }
      else {
        // If not reading a template, just include the char in the method name.
        filteredName.append(currentChar);
      }
      currentIndex++;
    }
    return filteredName.toString();
  }

  /**
   * Given the opening and closing characters (e.g. '<' and '>', or '(' and ')'), returns the index of the closing character
   * that matches the opening character of a string representing a method name.
   */
  private static int findMatchingClosingCharacterIndex(String methodName, char opening, char closing, int startIndex) {
    // Counter to keep track of the characters we read. If we read an opening character, increment the counter. If we read a closing one,
    // decrement it. Start the counter as 1 to take the first opening character into account. If the counter gets to 0, it means we have
    // found the target index.
    int count = 1;

    int index = startIndex;
    assert methodName.charAt(index) == opening;

    // Iterate backwards until we reach the matching opening parenthesis.
    while (index++ < methodName.length() - 1) {
      if (methodName.charAt(index) == opening) {
        count++;
      }
      else if (methodName.charAt(index) == closing) {
        count--;
      }
      if (count == 0) {
        return index;
      }
    }
    throw new IllegalStateException("Native method signature must have matching parentheses and brackets.");
  }

  /**
   * Receives a full method name and returns a {@link MethodModel} passing the method class name and its (simple) name.
   * @param methodFullName The method's full qualified name (e.g. java.lang.Object.equals)
   * @param separator The namespace separator (e.g. ".")
   * @param separatorPattern The regex pattern used to split the method full name (e.g. "\\.")
   * @param signature The method's signature.
   */
  private static MethodModel createMethodModel(String methodFullName, String separator, Pattern separatorPattern, String signature) {
    // First, we should extract the method name, which is the name after the last "." character.
    String[] splittedMethod = separatorPattern.split(methodFullName);
    int methodNameIndex = splittedMethod.length - 1;
    String methodName = splittedMethod[methodNameIndex];

    // Everything else composes the class name.
    StringBuilder className = new StringBuilder(splittedMethod[0]);
    for (int i = 1; i < methodNameIndex; i++) {
      className.append(separator);
      className.append(splittedMethod[i]);
    }
    return new MethodModel(methodName, className.toString(), signature, separator);
  }

}
