package android.print;

public class PrintDocumentAdapterHelper {
    public static abstract class LayoutCallback extends PrintDocumentAdapter.LayoutResultCallback {
        public LayoutCallback() {
            super();
        }
    }

    public static abstract class WriteCallback extends PrintDocumentAdapter.WriteResultCallback {
        public WriteCallback() {
            super();
        }
    }
}
