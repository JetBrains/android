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
package com.android.tools.idea.templates

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.npw.platform.Language
import com.android.tools.idea.npw.template.TemplateValueInjector
import com.android.tools.idea.templates.KeystoreUtils.getOrCreateDefaultDebugKeystore
import com.android.tools.idea.templates.KeystoreUtils.sha1
import com.android.tools.idea.templates.Template.CATEGORY_PROJECTS
import com.android.tools.idea.templates.TemplateMetadata.ATTR_APP_TITLE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_DEBUG_KEYSTORE_SHA1
import com.android.tools.idea.templates.TemplateMetadata.ATTR_HAS_APPLICATION_THEME
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LAUNCHER
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_LIBRARY_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_IS_NEW_MODULE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_LANGUAGE
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API
import com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API_LEVEL
import com.android.tools.idea.templates.TemplateMetadata.ATTR_THEME_EXISTS
import com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT
import com.android.tools.idea.ui.wizard.WizardUtils
import com.android.tools.idea.wizard.WizardConstants
import com.intellij.openapi.diagnostic.logger

/**
 * Helper class that tracks the Project Wizard State (Project template, plus its Module and Activity State)
 */
class TestNewProjectWizardState(moduleTemplate: Template) {
  val projectTemplate: Template = Template.createFromName(CATEGORY_PROJECTS, WizardConstants.PROJECT_TEMPLATE_NAME)
  val moduleTemplateState = TestTemplateWizardState().apply {
    template = moduleTemplate
    TemplateValueInjector(templateValues)
      .setProjectDefaults(null, true)
      .setLanguage(Language.JAVA)
    put(ATTR_APP_TITLE, APPLICATION_NAME)
    put(ATTR_HAS_APPLICATION_THEME, true)
    put(ATTR_IS_LAUNCHER, true)
    put(ATTR_IS_NEW_MODULE, true)
    put(ATTR_THEME_EXISTS, true)
    put(ATTR_CREATE_ACTIVITY, true)
    put(ATTR_IS_LIBRARY_MODULE, false)
    put(ATTR_TOP_OUT, WizardUtils.getProjectLocationParent().path)
    put(ATTR_MIN_API_LEVEL, defaultMinApi)
    put(ATTR_MIN_API, defaultMinApi.toString())
    setParameterDefaults()
  }
  val activityTemplateState = TestTemplateWizardState()

  /**
   * Call this to have this state object propagate common parameter values to sub-state objects
   * (i.e. states for other template wizards that are part of the same dialog).
   */
  fun updateParameters() {
    activityTemplateState.templateValues.putAll(moduleTemplateState.templateValues)
  }

  init {
    try {
      activityTemplateState.put(ATTR_DEBUG_KEYSTORE_SHA1, sha1(getOrCreateDefaultDebugKeystore()))
    }
    catch (e: Exception) {
      logger<TestNewProjectWizardState>().info("Could not compute SHA1 hash of debug keystore.", e)
    }
    updateParameters()
  }
}

private const val APPLICATION_NAME = "My Application"
private const val defaultMinApi = SdkVersionInfo.LOWEST_ACTIVE_API
