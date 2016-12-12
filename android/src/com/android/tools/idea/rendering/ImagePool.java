package com.android.tools.idea.rendering;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.FinalizablePhantomReference;
import com.google.common.base.FinalizableReferenceQueue;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;
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
  private static final EvictingQueue<SoftReference<BufferedImage>> NULL_EVICTING_QUEUE = EvictingQueue.create(0);
  private static final BiFunction<Integer, Integer, Function<Integer, Integer>> DEFAULT_SIZING_POLICY = (w, h) -> (type) -> {
    // Images below 1k, do not pool
    if (w * h < 1000) {
      return 0;
    }

    return 50_000_000 / (w * h);
  };

  private final HashMap<String, EvictingQueue<SoftReference<BufferedImage>>> myPool = new HashMap<>();
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private final FinalizableReferenceQueue myFinalizableReferenceQueue = new FinalizableReferenceQueue();
  private final Set<Reference<?>> myReferences = Sets.newConcurrentHashSet();
  private final BiFunction<Integer, Integer, Function<Integer, Integer>> myQueueSizingPolicy;
  private boolean isDisposed = false;

  /**
   * Returns the key to be used for indexing the {@link EvictingQueue}.
   */
  @NotNull
  private static String getPoolKey(int w, int h, int type) {
    return String.format("%dx%d-%d", w, h, type);
  }

  /**
   * Returns the queue to be used to store images of the given width, height and type.
   *
   * @param type See {@link BufferedImage} types
   */
  @NotNull
  private EvictingQueue<SoftReference<BufferedImage>> getTypeQueue(int w, int h, int type) {
    String poolKey = getPoolKey(w, h, type);

    return myPool.computeIfAbsent(poolKey, k -> {
      int size = myQueueSizingPolicy.apply(w, h).apply(type);

      if (size == 0) {
        // For size 0, do not allocate extra memory for a new EvictingQueue.
        return NULL_EVICTING_QUEUE;
      }

      return EvictingQueue.create(size);
    });
  }

  /**
   * Constructs a new {@link ImagePool} with a custom queue sizing policy. The passed queueSizingPolicy will be called
   * every time that a new cache is needed for a given (width, height) -> (imageType).
   * The return value from calling that function will be the size of the EvictingQueue used for caching the pooled
   * images.
   */
  public ImagePool(@NotNull BiFunction<Integer, Integer, Function<Integer, Integer>> queueSizingPolicy) {
    if (DEBUG) {
      System.out.println("New ImagePool");
    }
    myQueueSizingPolicy = queueSizingPolicy;
  }

  public ImagePool() {
    this(DEFAULT_SIZING_POLICY);
  }

  @VisibleForTesting
  @NotNull
  ImageImpl create(final int w, final int h, final int type, @Nullable Consumer<BufferedImage> freedCallback) {
    assert !isDisposed : "ImagePool already disposed";
    EvictingQueue<SoftReference<BufferedImage>> queue = getTypeQueue(w, h, type);

    BufferedImage image;
    SoftReference<BufferedImage> imageRef;
    try {
      imageRef = queue.remove();
      while ((image = imageRef.get()) == null) {
        imageRef = queue.remove();
      }
      if (DEBUG) {
        System.out.printf("Re-used image %dx%d - %d\n", w, h, type);
      }
      // Clear the image
      Graphics2D g = image.createGraphics();
      g.setComposite(AlphaComposite.Clear);
      g.fillRect(0, 0, w, h);
      g.dispose();
    }
    catch (NoSuchElementException e) {
      if (DEBUG) {
        System.out.printf("New image %dx%d - %d\n", w, h, type);
      }
      //noinspection UndesirableClassUsage
      image = new BufferedImage(w, h, type);
    }

    ImageImpl pooledImage = new ImageImpl(image);
    final BufferedImage imagePointer = image;
    FinalizablePhantomReference<Image> reference = new FinalizablePhantomReference<Image>(pooledImage, myFinalizableReferenceQueue) {
      @Override
      public void finalizeReferent() {
        // This method might be called twice if the user has manually called the free() method. The second call will have no effect.
        if (myReferences.remove(this)) {
          if (DEBUG) {
            System.out.printf("Released image %dx%d - %d\n", w, h, type);
          }
          getTypeQueue(w, h, type).add(new SoftReference<>(imagePointer));
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

  /**
   * Returns a new image of width w and height h. Please note that the image might have some contents so it is the responsibility of the
   * caller to clean it.
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
      drawImageTo(g, x, y, w, h, 0, 0, getWidth(), getHeight());
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

    private ImageImpl(@NotNull BufferedImage image) {
      myBuffer = image;
    }

    @Override
    public int getWidth() {
      assert myBuffer != null : "Image was already disposed";
      return myBuffer.getWidth();
    }

    @Override
    public int getHeight() {
      assert myBuffer != null : "Image was already disposed";
      return myBuffer.getHeight();
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
      WritableRaster raster = myBuffer.copyData(myBuffer.getRaster().createCompatibleWritableRaster(x, y, w, h));
      //noinspection UndesirableClassUsage
      return new BufferedImage(myBuffer.getColorModel(), raster, myBuffer.isAlphaPremultiplied(), null);
    }

    @Override
    @NotNull
    public BufferedImage getCopy() {
      assert myBuffer != null : "Image was already disposed";
      WritableRaster raster = myBuffer.copyData(myBuffer.getRaster().createCompatibleWritableRaster());
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