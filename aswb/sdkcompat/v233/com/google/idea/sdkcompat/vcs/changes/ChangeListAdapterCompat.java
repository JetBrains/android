package com.google.idea.sdkcompat.vcs.changes;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.changes.ChangeListAdapter;
import java.util.Collection;

/** Compat class for ChangeListAdapter. */
public abstract class ChangeListAdapterCompat extends ChangeListAdapter {
  @Override
  public void changesMoved(
      Collection<? extends Change> changes, ChangeList fromList, ChangeList toList) {
    doChangesMoved(changes, fromList, toList);
  }

  protected abstract void doChangesMoved(
      Collection<? extends Change> changes, ChangeList fromList, ChangeList toList);
}
