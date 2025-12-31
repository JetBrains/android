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
package com.google.idea.blaze.base.run.producers;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.dependencies.TargetInfo;
import com.google.idea.blaze.base.execution.BlazeParametersListUtil;
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration;
import com.google.idea.blaze.base.run.BlazeConfigurationNameBuilder;
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState;
import com.google.idea.blaze.base.run.state.RunConfigurationFlagsState;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** A context related to a blaze test target, used to configure a run configuration. */
public abstract class TestContext implements RunConfigurationContext {
  final PsiElement sourceElement;
  final ImmutableList<BlazeFlagsModification> blazeFlags;
  @Nullable final String description;

  TestContext(
      PsiElement sourceElement,
      ImmutableList<BlazeFlagsModification> blazeFlags,
      @Nullable String description) {
    this.sourceElement = sourceElement;
    this.blazeFlags = blazeFlags;
    this.description = description;
  }

  /** The {@link PsiElement} relevant to this test context (e.g. a method, class, file, etc.). */
  @Override
  public final PsiElement getSourceElement() {
    return sourceElement;
  }

  /** Returns true if the run configuration was successfully configured. */
  @Override
  public final boolean setupRunConfiguration(BlazeCommandRunConfiguration config) {
    if (!setupTarget(config)) {
      return false;
    }
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    commonState.getCommandState().setCommand(BlazeCommandName.TEST);

    List<String> flags = new ArrayList<>(commonState.getBlazeFlagsState().getRawFlags());
    blazeFlags.forEach(m -> m.modifyFlags(flags));
    commonState.getBlazeFlagsState().setRawFlags(flags);

    if (description != null) {
      BlazeConfigurationNameBuilder nameBuilder = new BlazeConfigurationNameBuilder(config);
      nameBuilder.setTargetString(description);
      config.setName(nameBuilder.build());
      config.setNameChangedByUser(true); // don't revert to generated name
    } else {
      config.setGeneratedName();
    }
    return true;
  }

  /** Returns true if the run configuration matches this {@link TestContext}. */
  @Override
  public boolean matchesRunConfiguration(BlazeCommandRunConfiguration config) {
    BlazeCommandRunConfigurationCommonState commonState =
        config.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState.class);
    if (commonState == null) {
      return false;
    }
    if (!Objects.equals(commonState.getCommandState().getCommand(), BlazeCommandName.TEST)) {
      return false;
    }
    RunConfigurationFlagsState flagsState = commonState.getBlazeFlagsState();
    return matchesTarget(config)
        && blazeFlags.stream().allMatch(m -> m.matchesConfigState(flagsState));
  }

  /** Returns true if the target is successfully set up. */
  abstract boolean setupTarget(BlazeCommandRunConfiguration config);

  /** Returns true if the run configuration target matches this {@link TestContext}. */
  abstract boolean matchesTarget(BlazeCommandRunConfiguration config);

  static class KnownTargetTestContext extends TestContext {
    final TargetInfo target;

    KnownTargetTestContext(
        TargetInfo target,
        PsiElement sourceElement,
        ImmutableList<BlazeFlagsModification> blazeFlags,
        @Nullable String description) {
      super(sourceElement, blazeFlags, description);
      this.target = target;
    }

    @Override
    boolean setupTarget(BlazeCommandRunConfiguration config) {
      config.setTargetInfo(target);
      return true;
    }

    @Override
    boolean matchesTarget(BlazeCommandRunConfiguration config) {
      return target.label().equals(config.getSingleTarget());
    }
  }

  /**
   * A modification to the blaze flags list for a run configuration. For example, setting a test
   * filter.
   */
  public interface BlazeFlagsModification {
    void modifyFlags(List<String> flags);

    boolean matchesConfigState(RunConfigurationFlagsState state);

    BlazeFlagsModification NOOP = new BlazeFlagsModification() {
      @Override
      public void modifyFlags(List<String> flags) {}
      @Override
      public boolean matchesConfigState(RunConfigurationFlagsState state) { return true; }
    };

    static BlazeFlagsModification addFlagIfNotPresent(String flag) {
      return new BlazeFlagsModification() {
        @Override
        public void modifyFlags(List<String> flags) {
          if (!flags.contains(flag)) {
            flags.add(flag);
          }
        }

        @Override
        public boolean matchesConfigState(RunConfigurationFlagsState state) {
          return state.getRawFlags().contains(flag);
        }
      };
    }

    static BlazeFlagsModification testFilter(@Nullable String filter) {
      if (filter == null || filter.isEmpty()) {
        return NOOP;
      }
      return new BlazeFlagsModification() {
        @Override
        public void modifyFlags(List<String> flags) {
          // remove old test filter flag if present
          flags.removeIf((flag) -> flag.startsWith(BlazeFlags.TEST_FILTER));
          if (filter != null) {
            flags.add(BlazeFlags.TEST_FILTER + "=" + BlazeParametersListUtil.encodeParam(filter));
          }
        }

        @Override
        public boolean matchesConfigState(RunConfigurationFlagsState state) {
          return state
              .getRawFlags()
              .contains(BlazeFlags.TEST_FILTER + "=" + BlazeParametersListUtil.encodeParam(filter));
        }
      };
    }
  }

  public static TestContext create(
      PsiElement sourceElement,
      TargetInfo target,
      @Nullable String description,
      BlazeFlagsModification... blazeFlags) {
    return new KnownTargetTestContext(
        target, sourceElement, ImmutableList.copyOf(blazeFlags), description);
  }


}
