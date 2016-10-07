import android.support.annotation.DimenRes;

public class InferParameterFromUsage {
    public void inferParameterFromMethodCall(int dummy, @DimenRes int id) {
        // Here we can infer that parameter id must be @DimenRes from the below method call
        getDimensionPixelSize(id);
    }

    private void getDimensionPixelSize(@DimenRes int id) {
    }
}
