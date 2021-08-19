/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.npw.project

import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.observable.core.StringProperty
import com.android.tools.idea.observable.expressions.Expression

/**
 * This Expression takes the Company Domain (eg: "mycompany.com"), and the Application Name (eg: "My App") and returns a valid Java package
 * name (eg: "com.mycompany.myapp"). Besides reversing the Company Name, taking spaces, a lower casing, it also takes care of
 * invalid java keywords (eg "new", "switch", "if", etc).
 */
class DomainToPackageExpression(
  private val companyDomain: StringProperty,
  private val applicationName: StringProperty
) : Expression<String>(companyDomain, applicationName) {
  override fun get(): String = sequence {
    yieldAll(companyDomain.get().split(".").asReversed())
    yield(applicationName.get())
  }.map { NewProjectModel.nameToJavaPackage(it) }
    .filter(String::isNotEmpty)
    .joinToString(".")
}
