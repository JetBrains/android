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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.intellij.usages.impl.rules.UsageType
import org.jetbrains.android.util.AndroidBundle

val MIGRATE_LINT_OPTIONS_TO_LINT =
  PropertiesOperationsRefactoringInfo(
    optionalFromVersion = AgpVersion.parse("7.1.0-alpha06"),
    requiredFromVersion = AgpVersion.parse("9.0.0-alpha01"),
    commandNameSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToLintRefactoringProcessor.commandName"),
    shortDescriptionSupplier = { """
      Configuration related to lint is now performed using the
      lint block.
    """.trimIndent()
    },
    processedElementsHeaderSupplier = AndroidBundle.messagePointer("project.upgrade.migrateToLintRefactoringProcessor.usageView.header"),
    componentKind = UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.MIGRATE_TO_LINT,
    propertiesOperationInfos = listOf(
      MovePropertiesInfo(
        sourceToDestinationPropertyModelGetters = listOf(
          Pair({ android().lintOptions().abortOnError() }, { android().lint().abortOnError() }),
          Pair({ android().lintOptions().absolutePaths() }, { android().lint().absolutePaths() }),
          Pair({ android().lintOptions().baseline() }, { android().lint().baseline() }),
          Pair({ android().lintOptions().check() }, { android().lint().checkOnly() }),
          Pair({ android().lintOptions().checkAllWarnings() }, { android().lint().checkAllWarnings() }),
          Pair({ android().lintOptions().checkDependencies() }, { android().lint().checkDependencies() }),
          Pair({ android().lintOptions().checkGeneratedSources() }, { android().lint().checkGeneratedSources() }),
          Pair({ android().lintOptions().checkReleaseBuilds() }, { android().lint().checkReleaseBuilds() }),
          Pair({ android().lintOptions().checkTestSources() }, { android().lint().checkTestSources() }),
          Pair({ android().lintOptions().disable() }, { android().lint().disable() }),
          Pair({ android().lintOptions().enable() }, { android().lint().enable() }),
          Pair({ android().lintOptions().error() }, { android().lint().error() }),
          Pair({ android().lintOptions().explainIssues() }, { android().lint().explainIssues() }),
          Pair({ android().lintOptions().fatal() }, { android().lint().fatal() }),
          Pair({ android().lintOptions().htmlOutput() }, { android().lint().htmlOutput() }),
          Pair({ android().lintOptions().htmlReport() }, { android().lint().htmlReport() }),
          Pair({ android().lintOptions().ignore() }, { android().lint().ignore() }),
          Pair({ android().lintOptions().ignoreTestSources() }, { android().lint().ignoreTestSources() }),
          Pair({ android().lintOptions().ignoreWarnings() }, { android().lint().ignoreWarnings() }),
          Pair({ android().lintOptions().informational() }, { android().lint().informational() }),
          Pair({ android().lintOptions().lintConfig() }, { android().lint().lintConfig() }),
          Pair({ android().lintOptions().noLines() }, { android().lint().noLines() }),
          Pair({ android().lintOptions().quiet() }, { android().lint().quiet() }),
          Pair({ android().lintOptions().sarifOutput() }, { android().lint().sarifOutput() }),
          Pair({ android().lintOptions().sarifReport() }, { android().lint().sarifReport() }),
          Pair({ android().lintOptions().showAll() }, { android().lint().showAll() }),
          Pair({ android().lintOptions().textOutput() }, { android().lint().textOutput() }),
          Pair({ android().lintOptions().textReport() }, { android().lint().textReport() }),
          Pair({ android().lintOptions().warning() }, { android().lint().warning() }),
          Pair({ android().lintOptions().warningsAsErrors() }, { android().lint().warningsAsErrors() }),
          Pair({ android().lintOptions().xmlOutput() }, { android().lint().xmlOutput() }),
          Pair({ android().lintOptions().xmlReport() }, { android().lint().xmlReport() }),
        ),
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.lintOptionsUsageInfo.move.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToLintRefactoringProcessor.move.usageType")),
      ),
      RemovePropertiesInfo(
        propertyModelListGetter = { listOf(android().lintOptions()) },
        tooltipTextSupplier = AndroidBundle.messagePointer("project.upgrade.lintOptionsUsageInfo.remove.tooltipText"),
        usageType = UsageType(AndroidBundle.messagePointer("project.upgrade.migrateToLintRefactoringProcessor.remove.usageType")),
      ),
    )
  )