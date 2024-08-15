package com.google.idea.sdkcompat.platform;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.local.FileWatcherNotificationSink;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/** A compatibility wrapper for {@link FileWatcherNotificationSink}. */
public interface FileWatcherNotificationSinkCompat extends FileWatcherNotificationSink {
  void notifyMappingCompat(Collection<Pair<String, String>> collection);

  @Override
  default void notifyMapping(@NotNull Collection<? extends Pair<String, String>> collection) {
    notifyMappingCompat(ImmutableList.copyOf(collection));
  }
}
