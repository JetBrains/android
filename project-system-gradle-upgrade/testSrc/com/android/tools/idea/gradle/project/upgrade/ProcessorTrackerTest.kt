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
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.testing.IdeComponents
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantComponentInfo.UpgradeAssistantComponentKind.AGP_CLASSPATH_DEPENDENCY
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.EXECUTE
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.FIND_USAGES
import com.google.wireless.android.sdk.stats.UpgradeAssistantEventInfo.UpgradeAssistantEventKind.SYNC_SKIPPED
import com.google.wireless.android.sdk.stats.UpgradeAssistantProcessorEvent
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase.assertSize
import org.junit.Before
import org.junit.Test

@RunsInEdt
class ProcessorTrackerTest : UpgradeGradleFileModelTestCase() {
  @Before
  fun replaceSyncInvoker() {
    // At the moment, the AgpUpgrade refactoring processor itself runs sync, which means that we need to
    // replace the invoker with a fake one here (because we are not working on complete projects which
    // sync correctly).
    //
    // The location of the sync invoker might move out from here, perhaps to a subscriber to a message bus
    // listening for refactoring events, at which point this would no longer be necessary (though we might
    // need to make sure that there is no listener triggering sync while running unit tests).
    val ideComponents = IdeComponents(projectRule.fixture)
    ideComponents.replaceApplicationService(GradleSyncInvoker::class.java, GradleSyncInvoker.FakeInvoker())
  }

  @Test
  fun testVersionInLiteralUsageTracker() {
    writeToBuildFile(TestFileName("AgpVersion/VersionInLiteral"))
    val processor = AgpUpgradeRefactoringProcessor(project, AgpVersion.parse("3.5.0"), AgpVersion.parse("4.1.0"))
    processor.componentRefactoringProcessors.forEach { it.isEnabled = it is AgpVersionRefactoringProcessor }
    processor.run()

    checkProcessorEvents(
      UpgradeAssistantProcessorEvent.newBuilder()
        .setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("4.1.0")
        .addComponentInfo(0, UpgradeAssistantComponentInfo.newBuilder().setIsEnabled(true).setKind(AGP_CLASSPATH_DEPENDENCY))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(FIND_USAGES).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantProcessorEvent.newBuilder()
        .setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("4.1.0")
        .addComponentInfo(0, UpgradeAssistantComponentInfo.newBuilder().setIsEnabled(true).setKind(AGP_CLASSPATH_DEPENDENCY))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(EXECUTE).setUsages(1).setFiles(2))
        .build(),
      UpgradeAssistantProcessorEvent.newBuilder()
        .setUpgradeUuid(processor.uuid).setCurrentAgpVersion("3.5.0").setNewAgpVersion("4.1.0")
        .addComponentInfo(0, UpgradeAssistantComponentInfo.newBuilder().setIsEnabled(true).setKind(AGP_CLASSPATH_DEPENDENCY))
        .setEventInfo(UpgradeAssistantEventInfo.newBuilder().setKind(SYNC_SKIPPED).setUsages(1).setFiles(2))
        .build(),
    )
  }

  private fun checkProcessorEvents(vararg expectedEvents: UpgradeAssistantProcessorEvent) {
    val events = tracker.usages
      .filter { it.studioEvent.kind == AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_COMPONENT_EVENT || it.studioEvent.kind == AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_PROCESSOR_EVENT }
      .sortedBy { it.timestamp }
      .map { it.studioEvent }
    val processorEvents = events.filter { it.kind == AndroidStudioEvent.EventKind.UPGRADE_ASSISTANT_PROCESSOR_EVENT }
    assertSize(expectedEvents.size, processorEvents)
    fun simplifyComponentInfo(event: UpgradeAssistantProcessorEvent): UpgradeAssistantProcessorEvent {
      val builder = UpgradeAssistantProcessorEvent.newBuilder(event)
      val infoList = builder.componentInfoList
      builder.clearComponentInfo()
      infoList.forEach { if(it.isEnabled) builder.addComponentInfo(it) }
      return builder.build()
    }

    assertThat(processorEvents.map { it.upgradeAssistantProcessorEvent }.map(::simplifyComponentInfo).toList())
      .isEqualTo(expectedEvents.toList())
  }
}