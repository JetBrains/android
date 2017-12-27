package com.android.tools.idea.rendering;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.FinalizablePhantomReference;
import com.google.common.base.FinalizableReferenceQueue;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ForwardingQueue;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Class that offers a pool of {@link BufferedImage}s. The returned {@link Image} do not offer a direct access
 * to the underlying {@link BufferedImage} to avoid clients holding references to it.
 * Once the {@link Image} is not being referenced anymore, it will be automatically returned to the pool.
 */
@SuppressWarnings("ALL")
public class ImagePool {
  public static final Image NULL_POOLED_IMAGE = new Image() {

    @Override
    public int getWidth() {
      return 0;
    }

    @Override
    public int getHeight() {
      return 0;
    }

    @Override
    public void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {}

    @Override
    public void paint(Consumer<Graphics2D> command) {}

    @Override
    @Nullable
    public BufferedImage getCopy(int x, int y, int w, int h) {
      return null;
    }

    @Override
    public void dispose() {}
  };
  private static final boolean DEBUG = false;
  private static final Bucket NULL_BUCKET = new Bucket(0, 0, 0);
  private final int[] myBucketSizes;
  private final HashMap<String, Bucket> myPool = new HashMap<>();
  private final BiFunction<Integer, Integer, Function<Integer, Integer>> myBucketSizingPolicy;
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private final FinalizableReferenceQueue myFinalizableReferenceQueue = new FinalizableReferenceQueue();
  private final Set<Reference<?>> myReferences = Sets.newConcurrentHashSet();

  /**
   * Constructs a new {@link ImagePool} with a custom queue sizing policy. The passed bucketSizingPolicy will be called
   * every time that a new cache is needed for a given (width, height) -> (imageType).
   * The return value from calling that function will be the size of the EvictingQueue used for caching the pooled
   * images.
   * @param bucketSizes Array containing a list of the allowed bucket sizes. The images will be allocated into a bucket that fits its two
   *                    dimensions. If an image contains one dimension bigger than the biggest given bucket size, the image won't be
   *                    allocated into the pool.
   * @param bucketSizingPolicy Function that returns the maximum size for a given bucket. The bucket is defined by width, height and image
   *                           type. If the returned size is 0, no pooling will be done for that bucket size.
   */
  public ImagePool(@NotNull int[] bucketSizes, @NotNull BiFunction<Integer, Integer, Function<Integer, Integer>> bucketSizingPolicy) {
    if (DEBUG) {
      System.out.println("New ImagePool");
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
    return String.format("%dx%d-%d", w, h, type);
  }

  public ImagePool() {
    this(new int[]{50, 500, 1000, 1500, 2000, 5000}, (w, h) -> (type) -> {
      // Images below 1k, do not pool
      if (w * h < 1000) {
        return 0;
      }

      return 50_000_000 / (w * h);
    });
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

      return new Bucket(finalWidthBucket, finalHeightBucket, size);
    });
  }

  @VisibleForTesting
  @NotNull
  ImageImpl create(final int w, final int h, final int type, @Nullable Consumer<BufferedImage> freedCallback) {
    assert !isDisposed : "ImagePool already disposed";

    // To avoid creating a large number of EvictingQueues, we distribute the images in buckets and use that
    Bucket bucket = getTypeBucket(w, h, type);
    if (DEBUG) {
      System.out.printf("create(%dx%d-%d) in bucket (%dx%d)\n", w, h, type, bucket.myMinWidth, bucket.myMinHeight);
    }

    BufferedImage image;
    SoftReference<BufferedImage> imageRef;
    try {
      imageRef = bucket.remove();
      while ((image = imageRef.get()) == null) {
        imageRef = bucket.remove();
      }
      if (DEBUG) {
        long totalSize = image.getWidth() * image.getHeight();
        double wasted = (totalSize - w * h);
        System.out.printf("  Re-used image %dx%d - %d\n  pool buffer %dx%d\n  wasted %d%%\n",
                          w, h, type,
                          image.getWidth(), image.getHeight(),
                          (int)((wasted / totalSize) * 100));
      }
      // Clear the image
      Graphics2D g = image.createGraphics();
      g.setComposite(AlphaComposite.Clear);
      g.fillRect(0, 0, w, h);
      g.dispose();
    }
    catch (NoSuchElementException e) {
      if (DEBUG) {
        System.out.printf("  New image %dx%d - %d\n", w, h, type);
      }
      //noinspection UndesirableClassUsage
      image = new BufferedImage(Math.max(bucket.myMinWidth, w), Math.max(bucket.myMinHeight, h), type);
    }

    ImageImpl pooledImage = new ImageImpl(w, h, image);
    final BufferedImage imagePointer = image;
    FinalizablePhantomReference<Image> reference = new FinalizablePhantomReference<Image>(pooledImage, myFinalizableReferenceQueue) {
      @Override
      public void finalizeReferent() {
        // This method might be called twice if the user has manually called the free() method. The second call will have no effect.
        if (myReferences.remove(this)) {
          boolean accepted = bucket.offer(new SoftReference<>(imagePointer));
          if (DEBUG) {
            System.out.printf("%s image (%dx%d-%d) in bucket (%dx%d)\n",
                              accepted ? "Released" : "Rejected",
                              w, h, type, bucket.myMinWidth, bucket.myMinHeight);
          }
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

  private static class Bucket extends ForwardingQueue<SoftReference<BufferedImage>> {
    private final Queue<SoftReference<BufferedImage>> myDelegate;
    private final AtomicLong myLastAccess = new AtomicLong(System.currentTimeMillis());
    private final int myMinWidth;
    private final int myMinHeight;

    public Bucket(int minWidth, int minHeight, int maxSize) {
      myMinWidth = minWidth;
      myMinHeight = minHeight;
      myDelegate = maxSize == 0 ?
                   EvictingQueue.create(0)
                   : new ArrayBlockingQueue<SoftReference<BufferedImage>>(maxSize);
    }

    @Override
    protected Queue<SoftReference<BufferedImage>> delegate() {
      myLastAccess.set(System.currentTimeMillis());
      return myDelegate;
    }
  }

  /**
   * Returns a new image of width w and height h.
   */
  @NotNull
  public Image create(final int w, final int h, final int type) {
    return create(w, h, type, null);
  }

  /**
   * Returns a pooled image with a copy of the passed {@link BufferedImage}
   */
  @NotNull
  public Image copyOf(@Nullable BufferedImage origin) {
    if (origin == null) {
      return NULL_POOLED_IMAGE;
    }

    int w = origin.getWidth();
    int h = origin.getHeight();
    int type = origin.getType();

    ImageImpl image = create(w, h, type, null);
    image.drawFrom(origin);

    return image;
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

  /**
   * Interface that represents an image from the pool. Clients can not access the inner BufferedImage directly and
   * can only get copies of it.
   */
  public interface Image {
    /**
     * Returns the width of the image
     */
    int getWidth();

    /**
     * Returns the height of the image
     */
    int getHeight();

    /**
     * Draws the current image to the given {@link Graphics} context.
     * See {@link Graphics#drawImage(java.awt.Image, int, int, int, int, int, int, int, int, ImageObserver)}
     */
    void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2);

    /**
     * Allows painting into the {@link Image}. The passed {@link Graphics2D} context will be disposed right after this call finishes so
     * do not keep a reference to it.
     */
    void paint(Consumer<Graphics2D> command);

    /**
     * Draws the current image to the given {@link Graphics} context.
     * See {@link Graphics#drawImage(java.awt.Image, int, int, int, int, ImageObserver)}
     */
    default void drawImageTo(@NotNull Graphics g, int x, int y, int w, int h) {
      drawImageTo(g, x, y, x + w, y + h, 0, 0, getWidth(), getHeight());
    }

    /**
     * Draws the current image to the given {@link BufferedImage}. If the passed destination buffer size does not match the pooled image
     * width an height, the image will be scaled.
     */
    default void drawImageTo(@NotNull BufferedImage destination) {
      Graphics g = destination.getGraphics();
      try {
        drawImageTo(g, 0, 0, destination.getWidth(), destination.getHeight());
      } finally {
        g.dispose();
      }
    }

    /**
     * Returns a {@link BufferedImage} with a copy of a sub-image of the pooled image.
     */
    @SuppressWarnings("SameParameterValue")
    @Nullable
    BufferedImage getCopy(int x, int y, int w, int h);

    /**
     * Returns a {@link BufferedImage} with a copy of the pooled image
     */
    @Nullable
    default BufferedImage getCopy() {
      return getCopy(0, 0, getWidth(), getHeight());
    }

    /**
     * Manually disposes the current image. After calling this method, the image can not be used anymore.
     * <p>
     * This method does not need to be called directly as the images will be eventually collected anyway. However, using this method, you can
     * speed up the collection process to avoid generating extra images.
     */
    void dispose();
  }

  public static class ImageImpl implements Image {
    private FinalizablePhantomReference<Image> myOwnReference = null;

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

    @Override
    public int getWidth() {
      assert myBuffer != null : "Image was already disposed";
      return myWidth;
    }

    @Override
    public int getHeight() {
      assert myBuffer != null : "Image was already disposed";
      return myHeight;
    }

    @Override
    public void drawImageTo(@NotNull Graphics g, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2) {
      assert myBuffer != null : "Image was already disposed";
      g.drawImage(myBuffer, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
    }

    @Override
    public void paint(@NotNull Consumer<Graphics2D> command) {
      assert myBuffer != null : "Image was already disposed";
      Graphics2D g = myBuffer.createGraphics();
      try {
        command.accept(g);
      } finally {
        g.dispose();
      }
    }

    @Override
    @NotNull
    public BufferedImage getCopy(int x, int y, int w, int h) {
      assert myBuffer != null : "Image was already disposed";

      if (x + w > myWidth) {
        throw new IndexOutOfBoundsException(String.format("x + y is out bounds (image width is = %d)", h));
      }

      if (y + h > myHeight) {
        throw new IndexOutOfBoundsException(String.format("y + h is out bounds (image height is = %d)", h));
      }

      WritableRaster raster = myBuffer.copyData(myBuffer.getRaster().createCompatibleWritableRaster(x, y, w, h));
      //noinspection UndesirableClassUsage
      return new BufferedImage(myBuffer.getColorModel(), raster, myBuffer.isAlphaPremultiplied(), null);
    }

    @Override
    @NotNull
    public BufferedImage getCopy() {
      assert myBuffer != null : "Image was already disposed";
      WritableRaster raster = myBuffer.copyData(myBuffer.getRaster().createCompatibleWritableRaster(0, 0, myWidth, myHeight));
      //noinspection UndesirableClassUsage
      return new BufferedImage(myBuffer.getColorModel(), raster, myBuffer.isAlphaPremultiplied(), null);
    }

    @Override
    public void dispose() {
      assert myBuffer != null : "Image was already disposed";
      myBuffer = null;
      if (myOwnReference != null) {
        myOwnReference.finalizeReferent();
      }
    }

    /**
     * Copies the content from the origin {@link BufferedImage} into the pooled image.
     */
    void drawFrom(@NotNull BufferedImage origin) {
      assert myBuffer != null : "Image was already disposed";
      Graphics g = myBuffer.getGraphics();
      try {
        g.drawImage(origin, 0, 0, null);
      } finally {
        g.dispose();
      }
    }
  }
}