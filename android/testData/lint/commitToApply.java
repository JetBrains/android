package test.pkg;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

@SuppressWarnings("UnusedDeclaration")
public class CommitToApply extends Activity {
    public void commitWarning1() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        <warning descr="Consider using `apply()` instead; `commit` writes its data to persistent storage immediately, whereas `apply` will handle it in the background">editor.com<caret>mit()</warning>;
    }
}