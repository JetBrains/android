package com.google.idea.sdkcompat.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value;

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
