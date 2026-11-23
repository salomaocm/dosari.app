package br.com.dosari.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Recebe arquivos/textos enviados ao app (VIEW e SEND) e entrega ao JavaScript do Dosari.
 * O JS pergunta por window.DosariNative.take() ao iniciar e quando recebe o evento "mc-incoming".
 */
public class MainActivity extends BridgeActivity {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    private final List<String> pending = new ArrayList<>();

    /** Ponte exposta ao JavaScript (classe pública e nomeada, exigida pelo WebView). */
    public class Native {
        @JavascriptInterface
        public String take() {
            synchronized (pending) { return pending.isEmpty() ? "" : pending.remove(0); }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        collect(getIntent());
        getBridge().getWebView().addJavascriptInterface(new Native(), "DosariNative");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (collect(intent)) {
            getBridge().getWebView().post(() ->
                getBridge().getWebView().evaluateJavascript(
                    "window.dispatchEvent(new Event('mc-incoming'))", null));
        }
    }

    /** Lê o conteúdo recebido e guarda na fila. Retorna true se havia algo. */
    @SuppressWarnings("deprecation")
    private boolean collect(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        String text = null;
        try {
            if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
                text = readUri(intent.getData());
            } else if (Intent.ACTION_SEND.equals(action)) {
                Uri stream = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) text = readUri(stream);
                else text = intent.getStringExtra(Intent.EXTRA_TEXT);
            }
        } catch (Exception ignored) { }
        if (text == null || text.trim().isEmpty()) return false;
        synchronized (pending) { pending.add(text); }
        intent.setAction(null); // evita reprocessar ao girar a tela
        return true;
    }

    private String readUri(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > MAX_BYTES) return null;
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}
