import android.content.res.Resources;
import android.support.annotation.DrawableRes;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;

import java.util.Random;

@SuppressWarnings("UnusedDeclaration")
public class X {
    public void testLiterals(Resources resources) {
        resources.getDrawable(0); // OK
        resources.getDrawable(-1); // OK
        resources.getDrawable(10); // ERROR
    }

    public void testConstants(Resources resources) {
        resources.getDrawable(R.drawable.my_drawable); // OK
        resources.getDrawable(R.string.my_string); // ERROR
    }

    public void testFields(String fileExt, Resources resources) {
        int mimeIconId = MimeTypes.styleAndDrawable;
        resources.getDrawable(mimeIconId); // OK

        int s1 = MimeTypes.style;
        resources.getDrawable(s1); // ERROR
        int s2 = MimeTypes.styleAndDrawable;
        resources.getDrawable(s2); // OK
        int w3 = MimeTypes.drawable;
        resources.getDrawable(w3); // OK

        // Direct reference
        resources.getDrawable(MimeTypes.style); // ERROR
        resources.getDrawable(MimeTypes.styleAndDrawable); // OK
        resources.getDrawable(MimeTypes.drawable); // OK
    }

    public void testCalls(String fileExt, Resources resources) {
        int mimeIconId = MimeTypes.getIconForExt(fileExt);
        resources.getDrawable(mimeIconId); // OK
        resources.getDrawable(MimeTypes.getInferredString()); // OK (wrong but can't infer type)
        resources.getDrawable(MimeTypes.getInferredDrawable()); // OK
        resources.getDrawable(MimeTypes.getAnnotatedString()); // Error
        resources.getDrawable(MimeTypes.getAnnotatedDrawable()); // OK
        resources.getDrawable(MimeTypes.getUnknownType()); // OK (unknown/uncertain)
    }

    private static class MimeTypes {
        @StyleRes
        @DrawableRes
        public static int styleAndDrawable;

        @StyleRes
        public static int style;

        @DrawableRes
        public static int drawable;

        @DrawableRes
        public static int getIconForExt(String ext) {
            return R.drawable.my_drawable;
        }

        public static int getInferredString() {
            // Implied string - can we handle this?
            return R.string.my_string;
        }

        public static int getInferredDrawable() {
            // Implied drawable - can we handle this?
            return R.drawable.my_drawable;
        }

        @StringRes
        public static int getAnnotatedString() {
            return R.string.my_string;
        }

        @DrawableRes
        public static int getAnnotatedDrawable() {
            return R.drawable.my_drawable;
        }

        public static int getUnknownType() {
            return new Random(1000).nextInt();
        }
    }

    public static final class R {
        public static final class drawable {
            public static final int my_drawable =0x7f020057;
        }
        public static final class string {
            public static final int my_string =0x7f0a000e;
        }
    }
}
