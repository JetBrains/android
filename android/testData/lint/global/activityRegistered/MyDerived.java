package p1.p2;

import android.app.Service;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;

public class MyDerived extends MyActivity {
  private static class MyInner extends Service {
    @Override
    public IBinder onBind(Intent intent) {
      return null;
    }
  }
  private abstract static class MyInner2 extends ContentProvider {
  }
  private static class MyInner3 extends ContentProvider {
    @Override
    public boolean onCreate() {
      return false;
    }

    @Override
    public Cursor query(Uri uri, String[] strings, String s, String[] strings2, String s2) {
      return null;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
      return null;
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
      return 0;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
      return 0;
    }
  }
}
