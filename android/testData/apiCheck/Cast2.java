<error descr="The SDK platform-tools version ((16.0.2)) is too old  to check APIs compiled with API 17; please update">import android.database.Cursor;</error>
import android.os.ParcelFileDescriptor;

import java.io.Closeable;
import java.io.IOException;

@SuppressWarnings("unused")
public class Class {
    // https://code.google.com/p/android/issues/detail?id=174535
    @SuppressWarnings("UnnecessaryLocalVariable")
    public void testImplicitCast(Cursor c) {
        Closeable closeable = <error descr="Cast from Cursor to Closeable requires API level 16 (current min is 1)">c</error>;
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Like the above, but with assignment instead of initializer
    public void testImplicitCast2(Cursor c) {
        @SuppressWarnings("UnnecessaryLocalVariable")
        Closeable closeable;
        closeable = <error descr="Cast from Cursor to Closeable requires API level 16 (current min is 1)">c</error>;
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // https://code.google.com/p/android/issues/detail?id=191120
    public void testImplicitCast(ParcelFileDescriptor pfd) {
        safeClose(<error descr="Cast from ParcelFileDescriptor to Closeable requires API level 16 (current min is 1)">pfd</error>);
    }

    private static void safeClose(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignore) {
        }
    }
}