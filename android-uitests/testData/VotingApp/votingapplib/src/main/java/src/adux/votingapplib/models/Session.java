package src.adux.votingapplib.models;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class Session {

    private SharedPreferences prefs;

    public Session(Context cntx) {
        // TODO Auto-generated constructor stub
        prefs = PreferenceManager.getDefaultSharedPreferences(cntx);
    }

    public void setUserID(String uid) {
        prefs.edit().putString("userid", uid).commit();
    }

    public String getUserID() {
        String userid = prefs.getString("userid","");
        return userid;
    }
}
