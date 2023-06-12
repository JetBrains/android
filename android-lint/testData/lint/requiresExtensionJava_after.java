package androidx.annotation;

import android.os.Build;
import java.lang.annotation.Repeatable;

@Repeatable(RequiresExtensions.class)
public @interface RequiresExtension {
  int extension();
  int version();
}
@interface RequiresApi {
  int value();
}
@interface RequiresExtensions {
  RequiresExtension[] value();
}

class SdkExtensionsTest {
    @RequiresExtension(extension = Build.VERSION_CODES.R, version = 4)
    public void test() {
        <caret>requiresExtRv4();
    }

  @RequiresExtension(extension = Build.VERSION_CODES.R, version=4)
  @RequiresExtension(extension = Build.VERSION_CODES.S, version=5)
  @RequiresApi(34)
  public void requiresExtRv4() {
  }
}