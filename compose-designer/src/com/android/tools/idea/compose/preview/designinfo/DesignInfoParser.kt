/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.designinfo

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.compose.DesignInfo
import com.android.tools.idea.compose.preview.util.findComposeViewAdapter
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger

/**
 * From the ComposeViewAdapter object, find and parse the result of 'getDesignInfoList', which
 * should return a list of JSON objects that have information that can be parsed into [DesignInfo].
 */
internal fun parseDesignInfoList(viewInfo: ViewInfo): List<DesignInfo> {
  val logger = Logger.getInstance(DesignInfo::class.java)
  val viewObj =
    try {
      findComposeViewAdapter(viewInfo.viewObject) ?: return emptyList()
    } catch (e: Throwable) {
      logger.warn("Error finding the ComposeViewAdapter", e)
      return emptyList()
    }

  val designInfoList =
    try {
      val designInfoListMethod =
        viewObj.javaClass.declaredMethods.firstOrNull { method ->
          method.name.contains("getDesignInfoList")
        }
      if (designInfoListMethod == null) {
        logger.warn("Missing designInfoList property from ComposeViewAdapter")
        return emptyList()
      } else {
        designInfoListMethod.invoke(viewObj) as List<*>
      }
    } catch (e: Throwable) {
      logger.warn("Error while obtaining DesignInfo list object", e)
      return emptyList()
    }

  return designInfoList.mapNotNull { designInfoJson ->
    try {
      parseDesignInfo(designInfoJson as String)
    } catch (e: Throwable) {
      logger.warn("Error while parsing", e)
      null
    }
  }
}

private fun parseDesignInfo(string: String) = Gson().fromJson(string, DesignInfo::class.java)
