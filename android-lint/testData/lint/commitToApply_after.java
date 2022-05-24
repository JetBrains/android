package test.pkg;

import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

@SuppressWarnings("UnusedDeclaration")
public class CommitToApply extends Activity {
    public void commitWarning1() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.apply();
    }
}