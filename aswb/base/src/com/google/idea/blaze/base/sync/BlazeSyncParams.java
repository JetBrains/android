/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.sync;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import java.util.Collection;

/** Parameters that control the sync. */
@AutoValue
public abstract class BlazeSyncParams {

  public abstract String title();

  public abstract SyncMode syncMode();

  /** A string describing what triggered the sync (e.g. on startup, auto-sync, etc.). */
  public abstract String syncOrigin();

  public abstract boolean backgroundSync();

  public abstract boolean addProjectViewTargets();

  public abstract boolean addWorkingSet();

  public abstract ImmutableSet<TargetExpression> targetExpressions();

  /**
   * Source files to be partially synced. Like 'targetExpressions', may reference files in the
   * workspace, but outside the project directories / targets.
   */
  public abstract ImmutableSet<WorkspacePath> sourceFilesToSync();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_BlazeSyncParams.Builder()
        .setBackgroundSync(false)
        .setAddProjectViewTargets(false)
        .setAddWorkingSet(false);
  }

  /** Builder for {@link BlazeSyncParams}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTitle(String value);

    public abstract Builder setSyncMode(SyncMode value);

    public abstract Builder setSyncOrigin(String syncOrigin);

    public abstract Builder setBackgroundSync(boolean value);

    public abstract Builder setAddProjectViewTargets(boolean value);

    public abstract Builder setAddWorkingSet(boolean value);

    public abstract Builder setTargetExpressions(Collection<? extends TargetExpression> targets);

    abstract ImmutableSet.Builder<TargetExpression> targetExpressionsBuilder();

    @CanIgnoreReturnValue
    public Builder addTargetExpression(TargetExpression targetExpression) {
      targetExpressionsBuilder().add(targetExpression);
      return this;
    }

    @CanIgnoreReturnValue
    public Builder addTargetExpressions(Collection<? extends TargetExpression> targets) {
      targetExpressionsBuilder().addAll(targets);
      return this;
    }

    abstract ImmutableSet.Builder<WorkspacePath> sourceFilesToSyncBuilder();

    @CanIgnoreReturnValue
    public Builder addSourceFilesToSync(Collection<WorkspacePath> targets) {
      sourceFilesToSyncBuilder().addAll(targets);
      return this;
    }

    public abstract BlazeSyncParams build();
  }

  /** Combine {@link BlazeSyncParams} from multiple build phases. */
  public static BlazeSyncParams combine(BlazeSyncParams first, BlazeSyncParams second) {
    BlazeSyncParams base =
        first.syncMode().ordinal() > second.syncMode().ordinal() ? first : second;
    return builder()
        .setTitle(base.title())
        .setSyncMode(base.syncMode())
        .setSyncOrigin(base.syncOrigin())
        .setBackgroundSync(first.backgroundSync() && second.backgroundSync())
        .addTargetExpressions(first.targetExpressions())
        .addTargetExpressions(second.targetExpressions())
        .addSourceFilesToSync(first.sourceFilesToSync())
        .addSourceFilesToSync(second.sourceFilesToSync())
        .setAddProjectViewTargets(first.addProjectViewTargets() || second.addProjectViewTargets())
        .setAddWorkingSet(first.addWorkingSet() || second.addWorkingSet())
        .build();
  }

  @Override
  public final String toString() {
    return String.format("%s (%s)", title(), syncMode().name().toLowerCase());
  }
}
