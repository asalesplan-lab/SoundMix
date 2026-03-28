package com.soundmix.app;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
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
import org.json.JSONArray;
import org.json.JSONObject;
public class MainActivity extends AppCompatActivity {
    private static final int PICK_AUDIO = 1;
    private static final int PERM_REQUEST = 2;
    private static final String BASE_URL = "https://alexsales-soundmix-backend.hf.space";
    private TextView tvFileName, tvStatus;
    private Button btnSelectAudio, btnSeparate, btnDownload, btnPlay;
    private ProgressBar progressBar;
    private SeekBar seekBar;
    private CheckBox cbVocals, cbGuitar, cbBass, cbDrums, cbPiano, cbOther;
    private Uri selectedAudioUri;
    private byte[] resultBytes;
    private OkHttpClient client;
    private MediaPlayer mediaPlayer;
    private File tempMixFile;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();
        tvFileName = findViewById(R.id.tvFileName);
        tvStatus = findViewById(R.id.tvStatus);
        btnSelectAudio = findViewById(R.id.btnSelectAudio);
        btnSeparate = findViewById(R.id.btnSeparate);
        btnDownload = findViewById(R.id.btnDownload);
        btnPlay = findViewById(R.id.btnPlay);
        seekBar = findViewById(R.id.seekBar);
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
        btnPlay.setOnClickListener(v -> togglePlay());
        btnDownload.setOnClickListener(v -> saveMix());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }
    private void togglePlay() {
        if (mediaPlayer == null || tempMixFile == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlay.setText("▶️ OUVIR MIX");
        } else {
            mediaPlayer.start();
            btnPlay.setText("⏸️ PAUSAR");
            updateSeekBar();
        }
    }
    private void updateSeekBar() {
        if (mediaPlayer == null) return;
        seekBar.setMax(mediaPlayer.getDuration());
        new Thread(() -> {
            while (mediaPlayer != null && mediaPlayer.isPlaying()) {
                runOnUiThread(() -> seekBar.setProgress(mediaPlayer.getCurrentPosition()));
                try { Thread.sleep(500); } catch (Exception e) { break; }
            }
        }).start();
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
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
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
        tvStatus.setText("Enviando audio...");
        btnDownload.setVisibility(View.GONE);
        btnPlay.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(selectedAudioUri);
                byte[] audioBytes = readBytes(is);
                is.close();
                runOnUiThread(() -> tvStatus.setText("Fazendo upload..."));
                RequestBody uploadBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("files", "audio.mp3",
                        RequestBody.create(audioBytes, MediaType.parse("audio/mpeg")))
                    .build();
                Request uploadRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/upload")
                    .post(uploadBody)
                    .build();
                Response uploadResponse = client.newCall(uploadRequest).execute();
                String uploadStr = uploadResponse.body().string();
                if (!uploadResponse.isSuccessful()) {
                    throw new Exception("Upload falhou: " + uploadStr);
                }
                JSONArray uploadedFiles = new JSONArray(uploadStr);
                String uploadedPath = uploadedFiles.getString(0);
                runOnUiThread(() -> tvStatus.setText("Processando stems..."));
                JSONArray stemsArray = new JSONArray(stems);
                JSONObject fileData = new JSONObject();
                fileData.put("path", uploadedPath);
                fileData.put("meta", new JSONObject("{\"_type\":\"gradio.FileData\"}"));
                JSONArray dataArray = new JSONArray();
                dataArray.put(fileData);
                dataArray.put(stemsArray);
                JSONObject body = new JSONObject();
                body.put("data", dataArray);
                Request predictRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/process")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
                Response predictResponse = client.newCall(predictRequest).execute();
                String predictStr = predictResponse.body().string();
                if (!predictResponse.isSuccessful()) {
                    throw new Exception("Predict falhou: " + predictStr);
                }
                JSONObject predictJson = new JSONObject(predictStr);
                if (!predictJson.has("event_id")) {
                    throw new Exception("Resposta: " + predictStr.substring(0, Math.min(200, predictStr.length())));
                }
                String eventId = predictJson.getString("event_id");
                runOnUiThread(() -> tvStatus.setText("Aguardando resultado..."));
                Request resultRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/process/" + eventId)
                    .get()
                    .build();
                Response resultResponse = client.newCall(resultRequest).execute();
                String resultStr = resultResponse.body().string();
                String dataLine = null;
                for (String line : resultStr.split("\n")) {
                    if (line.startsWith("data: ")) {
                        String candidate = line.substring(6).trim();
                        if (candidate.startsWith("[")) dataLine = candidate;
                    }
                }
                if (dataLine == null) {
                    throw new Exception("SSE: " + resultStr.substring(0, Math.min(300, resultStr.length())));
                }
                JSONArray resultData = new JSONArray(dataLine);
                JSONObject audioResult = resultData.getJSONObject(0);
                String resultPath = audioResult.optString("path", "");
                String resultUrl = audioResult.optString("url", "");
                if (resultUrl.isEmpty()) {
                    resultUrl = BASE_URL + "/gradio_api/file=" + resultPath;
                }
                Request downloadRequest = new Request.Builder()
                    .url(resultUrl).get().build();
                Response downloadResponse = client.newCall(downloadRequest).execute();
                resultBytes = downloadResponse.body().bytes();
                tempMixFile = File.createTempFile("soundmix", ".mp3", getCacheDir());
                FileOutputStream fos = new FileOutputStream(tempMixFile);
                fos.write(resultBytes);
                fos.close();
                if (mediaPlayer != null) mediaPlayer.release();
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(tempMixFile.getAbsolutePath());
                mediaPlayer.prepare();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Pronto! Ouça antes de baixar.");
                    btnPlay.setVisibility(View.VISIBLE);
                    btnPlay.setText("▶️ OUVIR MIX");
                    seekBar.setVisibility(View.VISIBLE);
                    seekBar.setMax(mediaPlayer.getDuration());
                    btnDownload.setVisibility(View.VISIBLE);
                    btnSeparate.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("Erro: " + e.getMessage());
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
