<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">import android.content.res.AssetFileDescriptor;</error>
import android.database.Cursor;
import android.database.CursorWindow;
import android.os.Parcelable;
import android.view.KeyCharacterMap;

import java.io.Closeable;
import java.io.IOException;

@SuppressWarnings({"RedundantCast", "unused"})
public class Class {
    public void test(AssetFileDescriptor descriptor) throws IOException {
        descriptor.close(); // OK, also defined on the class
        Closeable closeable = <error descr="Cast from AssetFileDescriptor to Closeable requires API level 19 (current min is 1)">(Closeable)descriptor</error>;
        closeable.close(); // OK, since 1
    }

    public void test(Cursor cursor) throws IOException {
        cursor.close();
        Closeable closeable = <error descr="Cast from Cursor to Closeable requires API level 16 (current min is 1)">(Closeable) cursor</error>; // Requires 16
        closeable.close();
    }

    public void test(CursorWindow window, KeyCharacterMap map) {
        Parcelable parcelable1 = (Parcelable)window; // OK
        Parcelable parcelable2 = <error descr="Cast from KeyCharacterMap to Parcelable requires API level 16 (current min is 1)">(Parcelable)map</error>; // Requires API 16
    }
}