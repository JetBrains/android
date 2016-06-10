import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

@SuppressWarnings({"WeakerAccess", "unused"})
public class PutValue.java {
    public static final String EXTRA_SCREEN = "ExtraScreen";

    public void unrelated(Context context, Intent intent) {
        intent.putExtra(EXTRA_SCREEN, R.id.dummy);
    }

    public static Intent buildIntent(final Context context,
                                     final Intent intent,
                                     final Bundle args) {
        intent.putExtra(EXTRA_SCREEN, screen);
        return intent;
    }

    public static class R {
        public static class id {
            public static final int dummy = 1;
        }
    }
}

