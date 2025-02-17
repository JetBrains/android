import androidx.annotation.IdRes;

public class InferMethodAnnotationFromReturnValue {
    public int test(@IdRes int id, boolean useMipmap) {
        if (useMipmap) {
            // Here we should infer @MipMapRes on the method
            return R.mipmap.some_image;
        }
        // Here we should infer @DrawableRes on the method
        return id;
    }

    public static class R {
        public static class mipmap {
            public static final int some_image = 1;
        }
    }
}
