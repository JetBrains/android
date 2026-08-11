/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run.producers

import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.command.BlazeFlags
import com.google.idea.blaze.base.dependencies.TargetInfo
import com.google.idea.blaze.base.execution.BlazeParametersListUtil
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/** A context related to a blaze test target, used to configure a run configuration. */
abstract class TestContext
internal constructor(override val sourceElement: PsiElement, val blazeFlags: List<BlazeFlagsModification>, val description: String?) :
  RunConfigurationContext {

  /** Returns true if the run configuration was successfully configured. */
  override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    if (!setupTarget(config)) {
      return false
    }
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    commonState.commandState.command = BlazeCommandName.TEST

    val flags = ArrayList(commonState.blazeFlagsState.rawFlags)
    blazeFlags.forEach { it.modifyFlags(flags) }
    commonState.blazeFlagsState.rawFlags = flags

    if (description != null) {
      val nameBuilder = BlazeConfigurationNameBuilder(config)
      nameBuilder.setTargetString(description)
      config.name = nameBuilder.build()
      config.setNameChangedByUser(true) // don't revert to generated name
    } else {
      config.setGeneratedName()
    }
    return true
  }

  override fun setupConfigurationName(config: BlazeCommandRunConfiguration) {
    if (description != null) {
      val nameBuilder = BlazeConfigurationNameBuilder(config)
      nameBuilder.setTargetString(description)
      config.name = nameBuilder.build()
      config.setNameChangedByUser(true)
    } else {
      config.setGeneratedName()
    }
  }

  override suspend fun refine(context: ConfigurationContext): RunConfigurationContext = this

  override suspend fun resolve(project: Project): RunConfigurationContext = this

  /** Returns true if the run configuration matches this [TestContext]. */
  override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
    val commonState = config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    if (commonState.commandState.command != BlazeCommandName.TEST) {
      return false
    }
    val flagsState = commonState.blazeFlagsState
    return matchesTarget(config) && blazeFlags.all { it.matchesConfigState(flagsState) }
  }

  /** Returns true if the target is successfully set up. */
  internal abstract fun setupTarget(config: BlazeCommandRunConfiguration): Boolean

  /** Returns true if the run configuration target matches this [TestContext]. */
  internal abstract fun matchesTarget(config: BlazeCommandRunConfiguration): Boolean

  internal class KnownTargetTestContext(
    val target: TargetInfo,
    sourceElement: PsiElement,
    blazeFlags: List<BlazeFlagsModification>,
    description: String?,
  ) : TestContext(sourceElement, blazeFlags, description) {

    override fun setupTarget(config: BlazeCommandRunConfiguration): Boolean {
      config.setTargetInfo(target)
      return true
    }

    override fun matchesTarget(config: BlazeCommandRunConfiguration): Boolean {
      return target.label().toString() == config.singleTargetPattern
    }
  }

  /** A modification to the blaze flags list for a run configuration. For example, setting a test filter. */
  interface BlazeFlagsModification {
    fun modifyFlags(flags: MutableList<String>)

    fun matchesConfigState(state: RunConfigurationFlagsState): Boolean

    companion object {
      @JvmField
      val NOOP: BlazeFlagsModification =
        object : BlazeFlagsModification {
          override fun modifyFlags(flags: MutableList<String>) {}

          override fun matchesConfigState(state: RunConfigurationFlagsState): Boolean = true
        }

      @JvmStatic
      fun addFlagIfNotPresent(flag: String): BlazeFlagsModification {
        return object : BlazeFlagsModification {
          override fun modifyFlags(flags: MutableList<String>) {
            if (!flags.contains(flag)) {
              flags.add(flag)
            }
          }

          override fun matchesConfigState(state: RunConfigurationFlagsState): Boolean {
            return state.rawFlags.contains(flag)
          }
        }
      }

      @JvmStatic
      fun testFilter(filter: String?): BlazeFlagsModification {
        if (filter.isNullOrEmpty()) {
          return NOOP
        }
        return object : BlazeFlagsModification {
          override fun modifyFlags(flags: MutableList<String>) {
            // remove old test filter flag if present
            flags.removeAll { it.startsWith(BlazeFlags.TEST_FILTER) }
            flags.add("${BlazeFlags.TEST_FILTER}=${BlazeParametersListUtil.encodeParam(filter)}")
          }

          override fun matchesConfigState(state: RunConfigurationFlagsState): Boolean {
            return state.rawFlags.contains("${BlazeFlags.TEST_FILTER}=${BlazeParametersListUtil.encodeParam(filter)}")
          }
        }
      }
    }
  }

  companion object {
    @JvmStatic
    fun create(
      sourceElement: PsiElement,
      target: TargetInfo,
      description: String?,
      vararg blazeFlags: BlazeFlagsModification,
    ): TestContext {
      return KnownTargetTestContext(target, sourceElement, blazeFlags.toList(), description)
    }
  }
}
