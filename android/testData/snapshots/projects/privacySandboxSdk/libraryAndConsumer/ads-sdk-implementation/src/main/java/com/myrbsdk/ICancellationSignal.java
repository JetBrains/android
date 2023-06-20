/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.myrbsdk;

public interface ICancellationSignal extends android.os.IInterface {
    /** Default implementation for ICancellationSignal. */
    public static class Default implements ICancellationSignal {
        @Override
        public void cancel() throws android.os.RemoteException {}

        @Override
        public android.os.IBinder asBinder() {
            return null;
        }
    }
    /** Local-side IPC implementation stub class. */
    public abstract static class Stub extends android.os.Binder implements ICancellationSignal {
        /** Construct the stub at attach it to the interface. */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }
        /**
         * Cast an IBinder object into a com.myrbsdk.ICancellationSignal interface, generating a
         * proxy if needed.
         */
        public static ICancellationSignal asInterface(android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof ICancellationSignal))) {
                return ((ICancellationSignal) iin);
            }
            return new Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(
                int code, android.os.Parcel data, android.os.Parcel reply, int flags)
                throws android.os.RemoteException {
            String descriptor = DESCRIPTOR;
            if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION
                    && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
                data.enforceInterface(descriptor);
            }
            switch (code) {
                case INTERFACE_TRANSACTION:
                    {
                        reply.writeString(descriptor);
                        return true;
                    }
            }
            switch (code) {
                case TRANSACTION_cancel:
                    {
                        this.cancel();
                        break;
                    }
                default:
                    {
                        return super.onTransact(code, data, reply, flags);
                    }
            }
            return true;
        }

        private static class Proxy implements ICancellationSignal {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void cancel() throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    boolean _status =
                            mRemote.transact(
                                    Stub.TRANSACTION_cancel,
                                    _data,
                                    null,
                                    android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_cancel = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    }

    public static final String DESCRIPTOR = "com$myrbsdk$ICancellationSignal".replace('$', '.');

    public void cancel() throws android.os.RemoteException;
}
