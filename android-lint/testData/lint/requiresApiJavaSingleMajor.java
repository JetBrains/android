package androidx.annotation;

import android.os.Build;

public @interface RequiresApi {
    int value();
}

class RequiresApiTest {
    public void test() {
        <error descr="Call requires API level 35 (current min is 1): `requires_35`">requires<caret>_35</error>();
    }

    @RequiresApi(35)
    public void requires_35() {
    }
}