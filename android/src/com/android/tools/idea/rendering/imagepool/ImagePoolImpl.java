/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering.imagepool;

import static com.android.tools.idea.rendering.imagepool.ImagePoolUtil.stackTraceToAssertionString;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.FinalizablePhantomReference;
import com.google.common.base.FinalizableReferenceQueue;
import com.google.common.base.Preconditions;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ForwardingQueue;
import com.google.common.collect.Sets;
import com.intellij.openapi.diagnostic.Logger;
import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.WritableRaster;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class that offers a pool of {@link BufferedImage}s. The returned {@link Image} do not offer a direct access
 * to the underlying {@link BufferedImage} to avoid clients holding references to it.
 * Once the {@link Image} is not being referenced anymore, it will be automatically returned to the pool.
 */
@SuppressWarnings("ALL")
class ImagePoolImpl implements ImagePool {
  private static final Logger LOG = Logger.getInstance(ImagePoolImpl.class);

  private static final Bucket NULL_BUCKET = new Bucket();
  private final int[] myBucketSizes;
  private final HashMap<String, Bucket> myPool = new HashMap<>();
  private final IdentityHashMap<Bucket, BucketStatsImpl> myBucketStats = new IdentityHashMap<>();
  private final BiFunction<Integer, Integer, Function<Integer, Integer>> myBucketSizingPolicy;
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private final FinalizableReferenceQueue myFinalizableReferenceQueue = new FinalizableReferenceQueue();
  private final Set<Reference<?>> myReferences = Sets.newConcurrentHashSet();

  private final LongAdder myTotalAllocatedBytes = new LongAdder();
  private final LongAdder myTotalInUseBytes = new LongAdder();

  private final Stats myStats = new Stats() {
    @Override
    public long totalBytesAllocated() {
      return myTotalAllocatedBytes.sum();
    }

    @Override
    public long totalBytesInUse() {
      return myTotalInUseBytes.sum();
    }

    @Override
    public BucketStats[] getBucketStats() {
      return myBucketStats.values().stream()
        .toArray(BucketStats[]::new);
    }
  };

  /**
   * Constructs a new {@link ImagePoolImpl} with a custom queue sizing policy. The passed bucketSizingPolicy will be called
   * every time that a new cache is needed for a given (width, height) -> (imageType).
   * The return value from calling that function will be the size of the EvictingQueue used for caching the pooled
   * images.
   *
   * @param bucketSizes        Array containing a list of the allowed bucket sizes. The images will be allocated into a bucket that fits its two
   *                           dimensions. If an image contains one dimension bigger than the biggest given bucket size, the image won't be
   *                           allocated into the pool.
   * @param bucketSizingPolicy Function that returns the maximum size for a given bucket. The bucket is defined by width, height and image
   *                           type. If the returned size is 0, no pooling will be done for that bucket size.
   */
  ImagePoolImpl(@NotNull int[] bucketSizes, @NotNull BiFunction<Integer, Integer, Function<Integer, Integer>> bucketSizingPolicy) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("New ImagePool " + Arrays.toString(bucketSizes));
    }
    myBucketSizes = bucketSizes;
    Arrays.sort(myBucketSizes);
    myBucketSizingPolicy = bucketSizingPolicy;
  }

  private boolean isDisposed = false;

  /**
   * Returns the key to be used for indexing the {@link EvictingQueue}.
   */
  @NotNull
  private static String getPoolKey(int w, int h, int type) {
    return new StringBuilder()
      .append(w)
      .append('x')
      .append(h)
      .append('-')
      .append(type)
      .toString();
  }

  /**
   * Returns the queue to be used to store images of the given width, height and type.
   *
   * @param type See {@link BufferedImage} types
   */
  @NotNull
  private Bucket getTypeBucket(int w, int h, int type) {
    if (myBucketSizingPolicy.apply(w, h).apply(type) == 0) {
      // Do not cache
      return NULL_BUCKET;
    }

    // Find the bucket sizes for both dimensions
    int widthBucket = -1;
    int heightBucket = -1;

    for (int bucketMinSize : myBucketSizes) {
      if (widthBucket == -1 && w < bucketMinSize) {
        widthBucket = bucketMinSize;

        if (heightBucket != -1) {
          break;
        }
      }
      if (heightBucket == -1 && h < bucketMinSize) {
        heightBucket = bucketMinSize;

        if (widthBucket != -1) {
          break;
        }
      }
    }

    if (widthBucket == -1 || heightBucket == -1) {
      return NULL_BUCKET;
    }

    String poolKey = getPoolKey(widthBucket, heightBucket, type);

    int finalWidthBucket = widthBucket;
    int finalHeightBucket = heightBucket;
    return myPool.computeIfAbsent(poolKey, (k) -> {
      int size = myBucketSizingPolicy.apply(finalWidthBucket, finalHeightBucket).apply(type);

      if (size == 0) {
        // For size 0, do not allocate extra memory for a new EvictingQueue.
        return NULL_BUCKET;
      }

      Bucket newBucket = new Bucket(finalWidthBucket, finalHeightBucket, size);
      myBucketStats.put(newBucket, new BucketStatsImpl(newBucket));

      return newBucket;
    });
  }

  @VisibleForTesting
  @NotNull
  ImageImpl create(final int w, final int h, final int type, @Nullable Consumer<BufferedImage> freedCallback) {
    assert !isDisposed : "ImagePool already disposed";

    // To avoid creating a large number of EvictingQueues, we distribute the images in buckets and use that
    Bucket bucket = getTypeBucket(w, h, type);
    BucketStatsImpl bucketStats = myBucketStats.get(bucket);
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("create(%dx%d-%d) in bucket (%dx%d) hasStats=%b\n", w, h, type, bucket.myMinWidth, bucket.myMinHeight,
                              bucketStats != null));
    }

    BufferedImage image;
    Bucket.Element element;
    try {
      element = bucket.remove();
      while ((image = element.get()) == null) {
        myTotalAllocatedBytes.add(-element.getImageEstimatedSize());
        element = bucket.remove();
      }

      long totalSize = image.getWidth() * image.getHeight();
      if (bucketStats != null) {
        bucketStats.bucketHit();
      }
      if (LOG.isDebugEnabled()) {
        double wasted = (totalSize - w * h);
        LOG.debug(String.format("  Re-used image %dx%d - %d\n  pool buffer %dx%d\n  wasted %d%%\n",
                                w, h, type,
                                image.getWidth(), image.getHeight(),
                                (int)((wasted / totalSize) * 100)));
      }
      myTotalInUseBytes.add(element.getImageEstimatedSize());
      // Clear the image
      if (image.getRaster().getDataBuffer().getDataType() == java.awt.image.DataBuffer.TYPE_INT) {
        Arrays.fill(((DataBufferInt)image.getRaster().getDataBuffer()).getData(), 0);
      }
      else {
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, w, h);
        g.dispose();
      }
    }
    catch (NoSuchElementException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("  New image %dx%d - %d\n", w, h, type));
      }
      if (bucketStats != null) {
        bucketStats.bucketMiss();
      }
      int newImageWidth = Math.max(bucket.myMinWidth, w);
      int newImageHeight = Math.max(bucket.myMinHeight, h);
      //noinspection UndesirableClassUsage
      image = new BufferedImage(newImageWidth, newImageHeight, type);
      // Set acceleration priority to 0.9 out of 1.0. We reserve 1.0 for the shared buffers
      // that we paint to screen.
      image.setAccelerationPriority(0.9f);
      long estimatedSize = newImageWidth * newImageHeight * 4;
      myTotalAllocatedBytes.add(estimatedSize);
      myTotalInUseBytes.add(estimatedSize);

    }

    ImageImpl pooledImage = new ImageImpl(w, h, image);
    final BufferedImage imagePointer = image;
    FinalizablePhantomReference<ImagePool.Image> reference =
      new FinalizablePhantomReference<ImagePool.Image>(pooledImage, myFinalizableReferenceQueue) {
      @Override
      public void finalizeReferent() {
        // This method might be called twice if the user has manually called the free() method. The second call will have no effect.
        if (myReferences.remove(this)) {
          Bucket.Element element = new Bucket.Element(imagePointer);
          boolean accepted = bucket.offer(element);
          if (bucketStats != null) {
            if (accepted) {
              bucketStats.returnedImageAccepted();
            }
            else {
              bucketStats.returnedImageRejected();
            }
          }
          if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s image (%dx%d-%d) in bucket (%dx%d)\n",
                                    accepted ? "Released" : "Rejected",
                                    w, h, type, bucket.myMinWidth, bucket.myMinHeight));
          }

          if (!accepted) {
            myTotalAllocatedBytes.add(-element.getImageEstimatedSize());
          }
          myTotalInUseBytes.add(-element.getImageEstimatedSize());
          if (freedCallback != null) {
            freedCallback.accept(imagePointer);
          }
        }
      }
    };
    pooledImage.myOwnReference = reference;
    myReferences.add(reference);

    return pooledImage;
  }

  private static final class BucketStatsImpl implements BucketStats {
    private final Bucket myBucket;
    private final AtomicLong myLastAccessMs = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong myBucketMiss = new AtomicLong(0);
    private final AtomicLong myBucketHit = new AtomicLong(0);
    private final AtomicLong myBucketFull = new AtomicLong(0);
    private final AtomicLong myBucketHadSpace = new AtomicLong(0);

    BucketStatsImpl(@NotNull Bucket bucket) {
      myBucket = bucket;
    }

    @Override
    public int getMinWidth() {
      return myBucket.myMinWidth;
    }

    @Override
    public int getMinHeight() {
      return myBucket.myMinHeight;
    }

    @Override
    public int maxSize() {
      return myBucket.getMaxSize();
    }

    @Override
    public long getLastAccessTimeMs() {
      return myLastAccessMs.get();
    }

    @Override
    public long bucketHits() {
      return myBucketHit.get();
    }

    @Override
    public long bucketMisses() {
      return myBucketMiss.get();
    }

    @Override
    public long bucketWasFull() {
      return myBucketFull.get();
    }

    @Override
    public long imageWasReturned() {
      return myBucketHadSpace.get();
    }

    void bucketHit() {
      myLastAccessMs.set(System.currentTimeMillis());
      myBucketHit.incrementAndGet();
    }

    void bucketMiss() {
      myLastAccessMs.set(System.currentTimeMillis());
      myBucketMiss.incrementAndGet();
    }

    void returnedImageAccepted() {
      myBucketHadSpace.incrementAndGet();
    }

    void returnedImageRejected() {
      myBucketFull.incrementAndGet();
    }
  }

  private static class Bucket extends ForwardingQueue<Bucket.Element> {
    /**
     * A wrapper for a soft-referenced {@link BufferedImage}.
     */
    private static class Element {
      private final long myImageEstimatedSize;
      private final SoftReference<BufferedImage> myReference;

      private Element(@NotNull BufferedImage image) {
        myImageEstimatedSize = image.getWidth() * image.getHeight() * 4;
        myReference = new SoftReference<>(image);
      }

      @Nullable
      private BufferedImage get() {
        return myReference.get();
      }

      /**
       * Returns the estimated image size referenced by this element. This is just
       * for stats purposes.
       */
      private long getImageEstimatedSize() {
        return myImageEstimatedSize;
      }
    }

    private final Queue<Element> myDelegate;
    private final int myMinWidth;
    private final int myMinHeight;
    private final int myMaxSize;

    Bucket(int minWidth, int minHeight, int maxSize) {
      Preconditions.checkArgument(maxSize > 0);
      myMinWidth = minWidth;
      myMinHeight = minHeight;
      myMaxSize = maxSize;
      myDelegate = new ArrayBlockingQueue<>(maxSize);
    }

    Bucket() {
      myMinWidth = 0;
      myMinHeight = 0;
      myMaxSize = 0;
      myDelegate = EvictingQueue.create(0);
    }

    @Override
    protected Queue<Element> delegate() {
      return myDelegate;
    }

    int getMaxSize() {
      return myMaxSize;
    }
  }

  /**
   * Returns a new image of width w and height h.
   */
  @NotNull
  public ImagePool.Image create(final int w, final int h, final int type) {
    return create(w, h, type, null);
  }

  /**
   * Returns a pooled image with a copy of the passed {@link BufferedImage}
   */
  @NotNull
  public ImagePool.Image copyOf(@Nullable BufferedImage origin) {
    if (origin == null) {
      return ImagePool.NULL_POOLED_IMAGE;
    }

    int w = origin.getWidth();
    int h = origin.getHeight();
    int type = origin.getType();

    ImageImpl image = create(w, h, type, null);
    image.drawFrom(origin);

    return image;
  }

  @Nullable
  @Override
  public Stats getStats() {
    return myStats;
  }

  /**
   * Disposes the image pool
   */
  public void dispose() {
    isDisposed = true;
    myFinalizableReferenceQueue.close();
    myReferences.clear();
    myPool.clear();
  }

  static class ImageImpl implements ImagePool.Image, DisposableImage {
    // Track dispose call when assertions are enabled
    private static final boolean ourTrackDisposeCall = ImageImpl.class.desiredAssertionStatus();

    private FinalizablePhantomReference<ImagePool.Image> myOwnReference = null;
    private ReadWriteLock myLock = new ReentrantReadWriteLock();
    /**
     * If we are tracking the dispose calls, this will contain the stack trace of the first caller to dispose
     */
    private StackTraceElement[] myDisposeStackTrace = null;

    @VisibleForTesting
    @Nullable
    BufferedImage myBuffer;

    final int myWidth;
    final int myHeight;

    private ImageImpl(int w, int h, @NotNull BufferedImage image) {
      assert w <= image.getWidth() && h <= image.getHeight();

      myWidth = w;
      myHeight = h;
      myBuffer = image;
    }

    private void assertIfDisposed() {
      if (myDisposeStackTrace != null) {
        LOG.warn("Accessing already disposed image\nDispose trace: \n" + stackTraceToAssertionString(myDisposeStackTrace));
      }
    }

    @Override
    public int getWidth() {
      return myWidth;
    }

    @Override
    public int getHeight() {
      return myHeight;
    }

    @Override
    public void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
      assertIfDisposed();
      myLock.readLock().lock();
      try {
        g.drawImage(myBuffer, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    @Override
    public void paint(@NotNull Consumer<Graphics2D> command) {
      assertIfDisposed();
      myLock.readLock().lock();
      try {
        Graphics2D g = myBuffer.createGraphics();
        try {
          command.accept(g);
        }
        finally {
          g.dispose();
        }
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    @Override
    @Nullable
    public BufferedImage getCopy(@Nullable GraphicsConfiguration gc, int x, int y, int w, int h) {
      myLock.readLock().lock();

      if (myBuffer == null) {
        // The image was already disposed
        LOG.debug("getCopy for already disposed image");
        return null;
      }

      try {
        if (x + w > myWidth) {
          throw new IndexOutOfBoundsException(String.format("x (%d) + y (%d) is out bounds (image width is = %d)", x, y, myWidth));
        }

        if (y + h > myHeight) {
          throw new IndexOutOfBoundsException(String.format("y (%d) + h (%d) is out bounds (image height is = %d)", y, h, myHeight));
        }

        BufferedImage newImage;
        if (gc != null) {
          newImage = gc.createCompatibleImage(w, h);
        }
        else {
          newImage = new BufferedImage(w, h, myBuffer.getType());
        }

        Graphics2D g = newImage.createGraphics();
        try {
          g.drawImage(myBuffer, 0, 0, w, h, x, y, x + w, y + h, null);
        }
        finally {
          g.dispose();
        }

        return newImage;
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    @Override
    @Nullable
    public BufferedImage getCopy() {
      myLock.readLock().lock();

      if (myBuffer == null) {
        // The image was already disposed
        LOG.debug("getCopy for already disposed image");
        return null;
      }

      try {
        WritableRaster raster = myBuffer.copyData(myBuffer.getRaster().createCompatibleWritableRaster(0, 0, myWidth, myHeight));
        //noinspection UndesirableClassUsage
        return new BufferedImage(myBuffer.getColorModel(), raster, myBuffer.isAlphaPremultiplied(), null);
      }
      finally {
        myLock.readLock().unlock();
      }
    }

    @Override
    public void dispose() {
      assertIfDisposed();
      myLock.writeLock().lock();
      if (ourTrackDisposeCall) {
        myDisposeStackTrace = Thread.currentThread().getStackTrace();
      }
      try {
        myBuffer = null;
        if (myOwnReference != null) {
          myOwnReference.finalizeReferent();
        }
      }
      finally {
        myLock.writeLock().unlock();
      }
    }

    @Override
    public boolean isValid() {
      myLock.readLock().lock();
      try {
        return myBuffer != null;
      } finally {
        myLock.readLock().unlock();
      }
    }

    /**
     * Copies the content from the origin {@link BufferedImage} into the pooled image.
     */
    void drawFrom(@NotNull BufferedImage origin) {
      assertIfDisposed();
      myLock.readLock().lock();
      try {
        Graphics g = myBuffer.getGraphics();
        try {
          g.drawImage(origin, 0, 0, null);
        }
        finally {
          g.dispose();
        }
      }
      finally {
        myLock.readLock().unlock();
      }
    }
  }
}
