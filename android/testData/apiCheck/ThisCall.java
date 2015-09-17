<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">package p1.p2;</error>

import android.app.Activity;
import android.app.Service;

public class Class extends Activity {
  public void test(final Activity activity, WebView webView) {
    if (activity.<error descr="Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus">hasWindowFocus</error>()) {
      return;
    }

    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public void onProgressChanged(WebView view, int newProgress) {
        if (<error descr="Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus">hasWindowFocus</error>()) {
          return;
        }

        if (Class.super.<error descr="Call requires API level 3 (current min is 1): android.app.Activity#hasWindowFocus">hasWindowFocus</error>()) {
          return;
        }
        foo();
      }
    });
  }

  public void foo() {
  }

  private static abstract class WebView extends Service {
    public abstract void setWebChromeClient(WebChromeClient client);
  }

  private static abstract class WebChromeClient {
    public abstract void onProgressChanged(WebView view, int newProgress);
  }
}