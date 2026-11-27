package br.com.dosari.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.webkit.JavascriptInterface;
import androidx.activity.OnBackPressedCallback;
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
    private static final int MAX_PENDING = 5;                 // no máximo 5 conteúdos aguardando
    private static final int MAX_PENDING_CHARS = 30 * 1024 * 1024; // e ~30 MB no total
    private final List<String> pending = new ArrayList<>();

    /** Ponte exposta ao JavaScript (classe pública e nomeada, exigida pelo WebView). */
    public class Native {
        @JavascriptInterface
        public String take() {
            synchronized (pending) { return pending.isEmpty() ? "" : pending.remove(0); }
        }

        /** Abre o app Relógio do celular com um temporizador já ajustado. Retorna "ok" ou "err:motivo". */
        @JavascriptInterface
        public String timer(int seconds, String label) {
            final int len = Math.max(1, Math.min(seconds, 86400));
            final String msg = label == null ? "Dosari" : (label.length() > 100 ? label.substring(0, 100) : label);
            final String[] res = { "err:sem resposta" };
            final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            runOnUiThread(() -> {
                try {
                    res[0] = launchTimer(len, msg);
                } finally {
                    done.countDown();
                }
            });
            try { done.await(3, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            return res[0];
        }
    }

    private Intent timerIntent(int len, String msg) {
        return new Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, len)
            .putExtra(AlarmClock.EXTRA_MESSAGE, msg)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false);
    }

    /** Tenta o relógio padrão, depois os relógios conhecidos (Samsung, Google), sem depender de resolveActivity. */
    private String launchTimer(int len, String msg) {
        String[] pkgs = { null, "com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock" };
        String last = "nenhum app de relógio respondeu";
        for (String pkg : pkgs) {
            try {
                Intent i = timerIntent(len, msg);
                if (pkg != null) i.setPackage(pkg);
                startActivity(i);
                return "ok";
            } catch (SecurityException e) {
                last = "permissão negada (" + e.getMessage() + ")";
            } catch (Exception e) {
                last = e.getClass().getSimpleName() + (pkg != null ? " em " + pkg : "");
            }
        }
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_TIMERS));
            return "err:abri o Relógio, mas sem tempo ajustado (" + last + ")";
        } catch (Exception ignored) { }
        return "err:" + last;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        collect(getIntent());
        getBridge().getWebView().addJavascriptInterface(new Native(), "DosariNative");

        // Gesto/botão "voltar": primeiro volta dentro do app; só sai (minimiza) se já estiver no Início.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                getBridge().getWebView().evaluateJavascript(
                    "(function(){try{return window.__dosariBack?window.__dosariBack():false}catch(e){return false}})()",
                    value -> {
                        if (!"true".equals(value)) moveTaskToBack(true);
                    });
            }
        });
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
        if (text.length() > MAX_BYTES) return false;
        synchronized (pending) {
            long total = text.length();
            for (String p : pending) total += p.length();
            if (pending.size() >= MAX_PENDING || total > MAX_PENDING_CHARS) return false; // fila cheia: ignora o novo
            pending.add(text);
        }
        intent.setAction(null); // evita reprocessar ao girar a tela
        return true;
    }

    private String readUri(Uri uri) throws Exception {
        // só aceita conteúdo entregue pelo Android (content://); file:// poderia apontar para arquivos privados do próprio app
        if (uri == null || !"content".equals(uri.getScheme())) return null;
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
