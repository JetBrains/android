package androidx.annotation;

import android.os.Build;
import java.lang.annotation.Repeatable;

@Repeatable(RequiresSdkVersions.class)
public @interface RequiresSdkVersion {
  int sdk();
  int version();
}
@interface RequiresApi {
  int value();
}
@interface RequiresSdkVersions {
  RequiresSdkVersion[] value();
}

class SdkExtensionsTest {
    public void test() {
        <error descr="Call requires version 4 of the R SDK (current min is 0): `requiresExtRv4`">requires<caret>ExtRv4</error>();
    }

  @RequiresSdkVersion(sdk = Build.VERSION_CODES.R, version=4)
  @RequiresSdkVersion(sdk = Build.VERSION_CODES.S, version=5)
  @RequiresApi(34)
  public void requiresExtRv4() {
  }
}