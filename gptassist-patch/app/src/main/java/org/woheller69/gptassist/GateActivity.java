package org.woheller69.gptassist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class GateActivity extends Activity {

    private static final String PREFS = "gptassist_gate";
    private static final String KEY_EXPIRES = "expires_at";
    private static final String KEY_DEVICE  = "device_id";
    private static final String API_URL     = "https://shared-ai-pal.lovable.app/api/public/verify-password";
    private static final long DAY_MS        = 24L * 60L * 60L * 1000L;

    private EditText input;
    private Button btn;
    private TextView msg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        long expires = p.getLong(KEY_EXPIRES, 0L);
        if (expires > System.currentTimeMillis()) {
            // Sigue vigente → programa alarmas y abre la app.
            ExpiryScheduler.scheduleAll(this, expires);
            launchMain();
            return;
        }

        // Expirado o primera vez → pinta el formulario.
        buildUi(expires > 0);
    }

    private void buildUi(boolean wasExpired) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0F0F0F);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("ChatGuard");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText(wasExpired
            ? "Tu suscripción venció. Ingresa la contraseña para renovar 29 días más."
            : "Ingresa tu contraseña de acceso para activar la app por 29 días.");
        sub.setTextColor(0xFFBBBBBB);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8); subLp.bottomMargin = dp(20);
        sub.setLayoutParams(subLp);
        root.addView(sub);

        input = new EditText(this);
        input.setHint("Contraseña");
        input.setHintTextColor(0xFF666666);
        input.setTextColor(0xFFFFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setBackgroundColor(0xFF1A1A1A);
        input.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.addView(input);

        btn = new Button(this);
        btn.setText("Validar y entrar");
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundColor(0xFF10A37F);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.topMargin = dp(14);
        btn.setLayoutParams(btnLp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { submit(); }
        });
        root.addView(btn);

        msg = new TextView(this);
        msg.setTextColor(0xFFEF4444);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        msg.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgLp.topMargin = dp(12);
        msg.setLayoutParams(msgLp);
        root.addView(msg);

        TextView help = new TextView(this);
        help.setText("¿Sin contraseña? Escríbenos por WhatsApp: +51 978 143 465");
        help.setTextColor(0xFF888888);
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helpLp.topMargin = dp(24);
        help.setLayoutParams(helpLp);
        root.addView(help);

        setContentView(root);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private String deviceId() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String id = p.getString(KEY_DEVICE, null);
        if (id == null) {
            id = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (id == null || id.length() < 6) id = "and-" + System.currentTimeMillis();
            p.edit().putString(KEY_DEVICE, id).apply();
        }
        return id;
    }

    @SuppressWarnings("deprecation")
    private void submit() {
        final String pw = input.getText().toString().trim();
        if (pw.isEmpty()) return;
        btn.setEnabled(false);
        msg.setTextColor(0xFFBBBBBB);
        msg.setText("Verificando...");

        new AsyncTask<Void, Void, String>() {
            @Override protected String doInBackground(Void... voids) {
                HttpURLConnection c = null;
                try {
                    URL u = new URL(API_URL);
                    c = (HttpURLConnection) u.openConnection();
                    c.setRequestMethod("POST");
                    c.setDoOutput(true);
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(15000);
                    c.setRequestProperty("Content-Type", "application/json");
                    JSONObject body = new JSONObject();
                    body.put("password", pw);
                    body.put("device_id", deviceId());
                    body.put("device_label", "gptAssist Android");
                    OutputStream os = c.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();
                    int code = c.getResponseCode();
                    java.io.InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(is));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    return sb.toString();
                } catch (Exception e) {
                    return "{\"ok\":false,\"reason\":\"network\"}";
                } finally {
                    if (c != null) c.disconnect();
                }
            }

            @Override protected void onPostExecute(String s) {
                try {
                    JSONObject o = new JSONObject(s);
                    if (o.optBoolean("ok", false)) {
                        long exp = System.currentTimeMillis() + 29L * DAY_MS;
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putLong(KEY_EXPIRES, exp).apply();
                        ExpiryScheduler.scheduleAll(GateActivity.this, exp);
                        Toast.makeText(GateActivity.this, "Activado 29 días", Toast.LENGTH_SHORT).show();
                        launchMain();
                    } else {
                        String reason = o.optString("reason", "error");
                        msg.setTextColor(0xFFEF4444);
                        msg.setText(reason.equals("bad_password")
                            ? "Contraseña incorrecta"
                            : "Error: " + reason);
                        btn.setEnabled(true);
                    }
                } catch (Exception e) {
                    msg.setTextColor(0xFFEF4444);
                    msg.setText("Sin conexión a internet");
                    btn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void launchMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }
}
