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
package com.android.tools.profilers.cpu.simpleperf

import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel
import com.android.tools.profilers.cpu.nodemodel.SyscallModel
import com.intellij.openapi.diagnostic.Logger
import java.util.regex.Pattern
import java.util.stream.IntStream

/**
 * Responsible for parsing full method/function names (String) obtained from symbol tables collected when profiling using simpleperf.
 * The names are parsed into [CaptureNodeModel] instances containing the class name, method name and signature.
 */
object NodeNameParser {
  private val JAVA_SEPARATOR_PATTERN = Pattern.compile("\\.")
  private val COMMON_PATH_PREFIXES = arrayOf("/apex/", "/system/", "/vendor/")
  private val COMMON_PATH_PREFIXES_DISPLAY = arrayOf("/apex/*", "/system/*", "/vendor/*")

  private val logger = Logger.getInstance(NodeNameParser::class.java)

  /**
   * Parses a string representing a full symbol name into its corresponding model. For example:
   * "namespace::Class::Fun<int>(params)" is parsed into a [CppFunctionModel]
   * "java.util.String.toString" is parsed into a [JavaMethodModel]
   * "ioctl" is parsed into a [SyscallModel]
   *
   * @param fullName      name to be parsed into a [CaptureNodeModel].
   * @param isUserWritten whether the symbol is part of the user-written code.
   * @param fileName      name of the ELF file containing the instruction corresponding to the function. Null if it doesn't apply.
   * @param vAddress      virtual address of the instruction in `fileName`.
  </int> */
  @JvmStatic
  @JvmOverloads
  fun parseNodeName(fullName: String, isUserWritten: Boolean, fileName: String? = null, vAddress: Long = -1): CaptureNodeModel {
    // C/C++ methods are represented as "Namespace::Class::MethodName()" in simpleperf. Check for the presence of "(".
    return if (fullName.contains("(")) {
      createCppFunctionModel(fullName, isUserWritten, fileName, vAddress)
    } else if (fullName.contains(".")) {
      // Method is in the format "java.package.Class.method". Parse it into a JavaMethodModel.
      createJavaMethodModel(fullName)
    } else if (fileName == null) {
      // Node represents a syscall.
      SyscallModel(null, fullName)
    } else {
      // Node represents a syscall.
      SyscallModel(tagFromFileName(fileName), fullName)
    }
  }

  /**
   * C++ function names are usually in the format namespace::Class::Fun(params). Sometimes, they also include
   * return type and template information, e.g. void namespace::Class::Fun<int>(params). We need to handle all the cases and parse
   * the function name into a [CppFunctionModel].
  </int> */
  @JvmStatic
  @JvmOverloads
  fun createCppFunctionModel(
    functionFullName: String,
    isUserWritten: Boolean,
    fileName: String? = null,
    vAddress: Long = -1
  ): CppFunctionModel {
    val paramsEndIndex = functionFullName.lastIndexOf(')')
    val paramsStartIndex = if (paramsEndIndex < 0) -1 else findMatchingOpeningParenthesisIndex(functionFullName, paramsEndIndex)

    // Most of this function is dedicated to slowly cutting down functionFullName to just be the function name (no parameters, no class,
    // etc.).
    var functionName: String
    val parameters: String

    if (paramsStartIndex in 0 until paramsEndIndex) {
      // Make sure not to include the indexes of "(" and ")" when creating the parameters substring.
      parameters = functionFullName.substring(paramsStartIndex + 1, paramsEndIndex)
      // Remove the parameters and everything that comes after it, such as const/volatile modifiers.
      functionName = functionFullName.substring(0, paramsStartIndex)
    } else {
      functionName = functionFullName
      parameters = ""
    }

    // Strip out the return type. We assume that the return type is separated by the first space, however the space in "operator bool()" or
    // "someNamespace::operator bool()" is an exception.
    separatorIndexOutsideOfTemplateInfo(functionName, " ", false).also {
      if (it >= 0) {
        val returnType = functionFullName.substring(0, it)

        if (returnType != "operator" && !returnType.endsWith("::operator")) {
          functionName = functionName.substring(it + 1)
        }
      }
    }

    val name: String
    val classOrNamespace: String

    separatorIndexOutsideOfTemplateInfo(functionName, "::", true).also {
      if (it >= 0) {
        classOrNamespace = functionName.substring(0, it)
        name = functionName.substring(it + 2)
      } else {
        classOrNamespace = ""
        name = functionName
      }
    }

    return CppFunctionModel.Builder(if (isOperatorOverload(name)) name else removeTemplateInfo(name))
      .setClassOrNamespace(removeTemplateInfo(classOrNamespace))
      .setIsUserCode(isUserWritten)
      .setParameters(removeTemplateInfo(parameters))
      .setFileName(fileName)
      .setTag(fileName?.let { tagFromFileName(it) })
      .setVAddress(vAddress)
      .build()
  }

  /**
   * @param functionFullName - a function full name where to search for the occurrence index.
   * @param separator        - a separator which should be searched.
   * @param lastIndex        - whether to return the last occurrence index or the first.
   * @return occurrence index of {@param separator} which is outside of all CPP templates in the given {@param functionFullName}.
   */
  private fun separatorIndexOutsideOfTemplateInfo(functionFullName: String, separator: String, lastIndex: Boolean): Int {
    var open = 0

    val instances = mutableListOf<Int>()

    var index = 0
    while (index < functionFullName.length) {
      // Because indexOf will return -1, we need to change it to the end so our coerceAtMost() will work.
      val nextOpen = functionFullName.indexOf('<', index).let { if (it == -1) functionFullName.length else it }
      val nextClose = functionFullName.indexOf('>', index).let { if (it == -1) functionFullName.length else it }
      val nextSeparator = functionFullName.indexOf(separator, index).let { if (it == -1) functionFullName.length else it }

      if (nextOpen < nextClose.coerceAtMost(nextSeparator)) {
        index = nextOpen + 1
        open++
        continue
      }

      if (nextClose < nextOpen.coerceAtMost(nextSeparator)) {
        index = nextClose + 1
        open--
        continue
      }

      if (open == 0 && nextSeparator < functionFullName.length) {
        instances.add(nextSeparator)
      }

      // Always move pass the next separator. If there were no more, this will move the index pass the end which will end the loop.
      index = nextSeparator + 1
    }

    // It is possible that we didn't find any separators.
    if (instances.isEmpty()) {
      return -1;
    }

    return if (lastIndex) instances.last() else instances.first()
  }

  /**
   * @param functionName - the given function name, i.e "myMethod", "my_method", "myMethod<int>", "operator<<", "my_operator"
   * @return true, if the given {@param functionName} describes an operator overloading.
  </int> */
  private fun isOperatorOverload(functionName: String): Boolean {
    val operator = "operator"
    return functionName == operator || functionName.startsWith(operator) && !isCppIdentifierChar(functionName[operator.length])
  }

  private fun isCppIdentifierChar(ch: Char): Boolean {
    // See: http://en.cppreference.com/w/cpp/language/identifiers
    return ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '_'
  }

  /**
   * Simplifies a C++ symbol (e.g. function name, or namespace) by removing the template instantiation information including template
   * arguments. Essentially, removes angle brackets and everything between them. For example:
   * "Type1<int> Type2<float>::FuncTemplate<Type3></Type3><2>>(Type4<bool>)" -> "Type1 Type2::FuncTemplate(Type4)"
   *
   * If it can't find matching angle brackets, falls back to the full symbol string.
  </bool></float></int> */
  private fun removeTemplateInfo(fullSymbol: String): String {
    val filteredName = StringBuilder()

    var open = 0

    fullSymbol.forEach {ch ->
      // Start of template info section
      if (ch == '<') {
        open++
        return@forEach
      }

      // End of template info section
      if (ch == '>') {
        open--
        return@forEach
      }

      // If we are in a template info section, we don't want to include that character.
      if (open == 0) {
        filteredName.append(ch)
      }
    }

    if (open != 0) {
      logger.warn("Native function signature $fullSymbol without matching angle brackets.")
      return fullSymbol
    }

    return filteredName.toString()
  }

  /**
   * Returns the index of the opening parenthesis, i.e. '(', that matches the closing one, i.e. ')' of a string representing a function
   * name, or -1 if a corresponding opening parenthesis can't be found.
   */
  private fun findMatchingOpeningParenthesisIndex(functionName: String, endIndex: Int): Int {
    assert(functionName[endIndex] == ')')
    var count = 0

    for (i in endIndex downTo 0) {
      val ch = functionName[i]

      if (ch == ')') {
        count++
        continue
      }

      if (ch == '(') {
        count--
        continue
      }

      // If we have reached count == 0, we need to return the index but with +1 because we are interested in where the "(" is, not
      // characters.
      if (count == 0) {
        return i + 1
      }
    }

    logger.warn("Native function signature $functionName without matching parentheses.")
    return -1
  }

  /**
   * Receives a full method name and returns a [JavaMethodModel] containing its class name and its (simple) name.
   * @param fullName The method's full qualified name (e.g. java.lang.Object.equals)
   */
  private fun createJavaMethodModel(fullName: String): JavaMethodModel {
    val fullPath = JAVA_SEPARATOR_PATTERN.split(fullName).toList()

    // We need to separate the class pat from the method name. The class path is everything exception the method name.
    val classPath = fullPath.subList(0, fullPath.size - 1).joinToString(".")
    val methodName = fullPath.last()

    return JavaMethodModel(methodName, classPath, "")
  }

  private fun tagFromFileName(fileName: String): String {
    val index = IntStream.range(0, COMMON_PATH_PREFIXES.size).toArray().find { fileName.startsWith(COMMON_PATH_PREFIXES[it]) }
    return if (index == null) fileName else COMMON_PATH_PREFIXES_DISPLAY[index]
  }
}
