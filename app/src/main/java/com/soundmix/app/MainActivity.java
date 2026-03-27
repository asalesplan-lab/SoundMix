package com.soundmix.app;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
public class MainActivity extends AppCompatActivity {
    private static final int PICK_AUDIO = 1;
    private static final int PERM_REQUEST = 2;
    private TextView tvFileName, tvStatus;
    private Button btnSelectAudio, btnSeparate, btnDownload;
    private ProgressBar progressBar;
    private CheckBox cbVocals, cbGuitar, cbBass, cbDrums, cbPiano, cbOther;
    private Uri selectedAudioUri;
    private byte[] resultBytes;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvFileName = findViewById(R.id.tvFileName);
        tvStatus = findViewById(R.id.tvStatus);
        btnSelectAudio = findViewById(R.id.btnSelectAudio);
        btnSeparate = findViewById(R.id.btnSeparate);
        btnDownload = findViewById(R.id.btnDownload);
        progressBar = findViewById(R.id.progressBar);
        cbVocals = findViewById(R.id.cbVocals);
        cbGuitar = findViewById(R.id.cbGuitar);
        cbBass = findViewById(R.id.cbBass);
        cbDrums = findViewById(R.id.cbDrums);
        cbPiano = findViewById(R.id.cbPiano);
        cbOther = findViewById(R.id.cbOther);
        requestPermissions();
        btnSelectAudio.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            startActivityForResult(intent, PICK_AUDIO);
        });
        btnSeparate.setOnClickListener(v -> {
            if (selectedAudioUri == null) {
                Toast.makeText(this, "Selecione uma musica primeiro!", Toast.LENGTH_SHORT).show();
                return;
            }
            separateStems();
        });
        btnDownload.setOnClickListener(v -> saveMix());
    }
    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQUEST);
    }
    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return buffer.toByteArray();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO && resultCode == Activity.RESULT_OK && data != null) {
            selectedAudioUri = data.getData();
            tvFileName.setText(selectedAudioUri.getLastPathSegment());
        }
    }
    private void separateStems() {
        List<String> stems = new ArrayList<>();
        if (cbVocals.isChecked()) stems.add("vocals");
        if (cbGuitar.isChecked()) stems.add("guitar");
        if (cbBass.isChecked()) stems.add("bass");
        if (cbDrums.isChecked()) stems.add("drums");
        if (cbPiano.isChecked()) stems.add("piano");
        if (cbOther.isChecked()) stems.add("other");
        progressBar.setVisibility(View.VISIBLE);
        btnSeparate.setEnabled(false);
        tvStatus.setText("Enviando para processamento...");
        btnDownload.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(selectedAudioUri);
                byte[] audioBytes = readBytes(is);
                is.close();
                String stemsJson = "[\"" + String.join("\",\"", stems) + "\"]";
                RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("audio", "audio.mp3",
                        RequestBody.create(audioBytes, MediaType.parse("audio/mpeg")))
                    .addFormDataPart("selected_stems", stemsJson)
                    .build();
                Request request = new Request.Builder()
                    .url("https://alexsales-soundmix-backend.hf.space/run/predict")
                    .post(requestBody)
                    .build();
                OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(300, TimeUnit.SECONDS)
                    .build();
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    resultBytes = response.body().bytes();
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        tvStatus.setText("Pronto! Toque em Baixar Mix.");
                        btnDownload.setVisibility(View.VISIBLE);
                        btnSeparate.setEnabled(true);
                    });
                } else {
                    String errBody = response.body() != null ? response.body().string() : "sem detalhe";
                    throw new Exception("HTTP " + response.code() + ": " + errBody);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Erro: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    btnSeparate.setEnabled(true);
                });
            }
        }).start();
    }
    private void saveMix() {
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "soundmix_" + System.currentTimeMillis() + ".mp3");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(resultBytes);
            fos.close();
            tvStatus.setText("Salvo em: " + file.getName());
            Toast.makeText(this, "Mix salvo na pasta Music!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
