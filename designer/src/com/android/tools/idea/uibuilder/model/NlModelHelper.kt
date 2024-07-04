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
package com.android.tools.idea.uibuilder.model

import com.android.AndroidXConstants.CLASS_APP_COMPAT_ACTIVITY
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.projectsystem.getModuleSystem
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade

/*
 * Layout editor-specific helper methods and data for NlModel
 */

const val CUSTOM_DENSITY_ID: String = "Custom Density"

fun NlModel.currentActivityIsDerivedFromAppCompatActivity(): Boolean {
  var activityClassName: String? =
    configuration.activity
      ?: // The activity is not specified in the XML file.
      // We cannot know if the activity is derived from AppCompatActivity.
      // Assume we are since this is how the default activities are created.
      return true
  if (activityClassName!!.startsWith(".")) {
    val pkg = StringUtil.notNullize(facet.getModuleSystem().getPackageName())
    activityClassName = pkg + activityClassName
  }
  val facade = JavaPsiFacade.getInstance(project)
  var activityClass = facade.findClass(activityClassName, module.moduleScope)
  while (
    activityClass != null && !CLASS_APP_COMPAT_ACTIVITY.isEquals(activityClass.qualifiedName)
  ) {
    activityClass = activityClass.superClass
  }
  return activityClass != null
}
