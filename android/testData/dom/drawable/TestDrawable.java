package p1.p2;

import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class TestDrawable extends Drawable {
  @Override
  public void draw(@NonNull Canvas canvas) {
  }

  @Override
  public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
  }

  @Override
  public void setColorFilter(@Nullable ColorFilter colorFilter) {
  }

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }
}