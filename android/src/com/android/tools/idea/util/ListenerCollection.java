package com.android.tools.idea.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * Class to handle listeners and calls. This class is thread-safe and handles the case where listeners add or remove listeners
 * during their execution.
 * @param <T> the listener type
 */
public class ListenerCollection<T> {
  /** Lock that guards the access to the instance state */
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private final Executor myExecutor;

  private final Set<T> myListenersSet = new HashSet<>();
  /** Cached copy of myListenersSet used to avoid extra copies when the listeners do not change */
  private ImmutableSet<T> myListenerSetCopy;

  private ListenerCollection(@NotNull Executor executor) {
    myExecutor = executor;
  }

  /**
   * Creates a ListenerHandler that will call listeners in the {@link #forEach(Consumer)} caller thread
   */
  @NotNull
  public static <T> ListenerCollection<T> createWithDirectExecutor() {
    return createWithExecutor(MoreExecutors.directExecutor());
  }

  /**
   * Creates a ListenerHandler that will call listeners in the given {@link Executor}
   */
  @NotNull
  public static <T> ListenerCollection<T> createWithExecutor(@NotNull Executor executor) {
    return new ListenerCollection<>(executor);
  }

  /**
   * Adds a listener from the handler. If this method returns false, the listener was already in the handler
   */
  public boolean add(@NotNull T listener) {
    myLock.writeLock().lock();
    try {
      myListenerSetCopy = null;
      return myListenersSet.add(listener);
    } finally {
      myLock.writeLock().unlock();
    }
  }

  /**
   * Removes a listener from the handler and returns whether the passed listener existed or not.
   */
  public boolean remove(@NotNull T listener) {
    myLock.writeLock().lock();
    try {
      myListenerSetCopy = null;
      return myListenersSet.remove(listener);
    } finally {
      myLock.writeLock().unlock();
    }
  }

  /**
   * Removes all the listeners from the handler
   */
  public void clear() {
    myLock.writeLock().lock();
    try {
      myListenerSetCopy = ImmutableSet.of();
      myListenersSet.clear();
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  /**
   * Iterates over all the listeners in the given {@link Executor}. This method returns a {@link ListenableFuture} to know when
   * the processing has finished.
   */
  public ListenableFuture<Void> forEach(@NotNull Consumer<T> runOnListener) {
    SettableFuture<Void> future = SettableFuture.create();
    ImmutableSet<T> listeners;
    myLock.readLock().lock();
    if (myListenerSetCopy == null) {
      // The cache was out-of-date. Rebuild it. First upgrade the lock to write
      myLock.readLock().unlock();
      myLock.writeLock().lock();
      try {
        // Cache current list of listeners
        myListenerSetCopy = ImmutableSet.copyOf(myListenersSet);
        myLock.readLock().lock(); // Downgrade the lock and keep it for reading
      } finally {
        myLock.writeLock().unlock();
      }
    }
    try {
      listeners = myListenerSetCopy;
    }
    finally {
      myLock.readLock().unlock();
    }
    if (listeners.isEmpty()) {
      return Futures.immediateFuture(null);
    }
    myExecutor.execute(() -> {
      listeners.forEach(runOnListener);
      future.set(null);
    });

    return future;
  }
}