/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

data class LiveEditCompilerOutput private constructor (val className: String,
                                                  val methodName: String,
                                                  val methodDesc: String,
                                                  val classData: ByteArray,
                                                  val functionType: LiveEditFunctionType,
                                                  val hasGroupId : Boolean,
                                                  val groupId: Int ,
                                                  val supportClasses: Map<String, ByteArray>) {
  class Builder(
    var className: String = "",
    var methodName: String = "",
    var methodDesc: String = "",
    var classData: ByteArray = ByteArray(0),
    var functionType: LiveEditFunctionType = LiveEditFunctionType.NONE,
    var hasGroupId : Boolean = false,
    var groupId: Int = 0,
    var supportClasses: Map<String, ByteArray> = emptyMap()) {

    fun className(className: String) = apply { this.className = className }
    fun methodName(methodName: String) = apply { this.methodName = methodName }
    fun methodDesc(methodDesc: String) = apply { this.methodDesc = methodDesc }

    fun classData(classData: ByteArray) = apply { this.classData = classData }

    fun functionType(functionType: LiveEditFunctionType) = apply { this.functionType = functionType }

    fun groupId(groupId: Int) = apply {
      this.groupId = groupId
      this.hasGroupId = true
    }

    fun supportClasses(supportClasses: Map<String, ByteArray>) = apply { this.supportClasses = supportClasses }

    fun build() = LiveEditCompilerOutput(className, methodName, methodDesc, classData, functionType, hasGroupId, groupId, supportClasses)
  }
}