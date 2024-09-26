/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.bazel.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;

/** Helper to show a confirmation dialog for whether to remove integration records. */
public class RemoveIntegrationRecordsConfirmationOption implements VcsShowConfirmationOption {

  private Value value = Value.SHOW_CONFIRMATION;

  public RemoveIntegrationRecordsConfirmationOption(Project unusedProject, AbstractVcs unusedVcs) {}

  @Override
  public Value getValue() {
    // TODO(b/266785029): Read the value from storage. Maybe via the PiperVcs instance?
    return value;
  }

  @Override
  public void setValue(Value value) {
    this.value = value;
    if (value.equals(Value.DO_NOTHING_SILENTLY)) {
      // This happens if the user checks "don't ask again", but clicks No or cancels.
      // We don't want to persist that, since the action would just become useless.
      return;
    }
    // TODO(b/266785029): Store the value into storage. Maybe via the PiperVcs instance?
  }

  @Override
  public boolean isPersistent() {
    return true;
  }
}
