package com.soundmix.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.tabs.TabLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private static final int PICK_BACKING = 2;
    private static final int PERM_REQUEST = 3;
    private static final String BASE_URL = "https://alexsales-soundmix-backend.hf.space";
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private static final int STEM_COUNT = 9;

    // Stem Separator UI
    private TextView tvFileName, tvStatus, tvMixStatus;
    private Button btnSelectAudio, btnSeparate, btnPreviewMix, btnDownload, btnPlay;
    private ProgressBar progressBar, progressMix;
    private SeekBar seekBar;
    private View panelStems;
    private Uri selectedAudioUri;
    private OkHttpClient client;

    // Stems arrays
    private MediaPlayer[] stemPlayers = new MediaPlayer[STEM_COUNT];
    private String[] stemUrls = new String[STEM_COUNT];
    private Button[] btnPlayStems = new Button[STEM_COUNT];
    private SeekBar[] seekStems = new SeekBar[STEM_COUNT];
    private CheckBox[] cbStems = new CheckBox[STEM_COUNT];
    private EditText[] etNameStems = new EditText[STEM_COUNT];

    // Mix
    private MediaPlayer mixPlayer;
    private byte[] mixBytes;
    private File tempMixFile;

    // VideoMix
    private TabLayout tabLayout;
    private View scrollStemSeparator, scrollVideoMix;
    private Button btnSelectBacking, btnRecord, btnExportVideo, btnFlipCamera, btnLatencyTest;
    private TextView tvBackingName, tvRecordStatus, tvLatency, tvLatencyTest, tvOffsetConfirm;
    private SeekBar seekBackingVolume, seekLatency;
    private PreviewView cameraPreview;
    private SwitchCompat switchMonitor;
    private RadioGroup radioGroupAudio;
    private Uri backingTrackUri;
    private MediaPlayer backingPlayer;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private ExecutorService cameraExecutor;
    private boolean isRecording = false;
    private Uri lastVideoUri;
    private int latencyMs = 0;        // offset real em ms (-2500 a +2500)
    private TextView tvOffsetConfirm;  // confirmador visual do offset
    private boolean useFrontCamera = false;
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private CountDownTimer countDownTimer;
    private boolean isPreparing = false;
    private int selectedAudioSource = MediaRecorder.AudioSource.MIC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();
        cameraExecutor = Executors.newSingleThreadExecutor();

        tvFileName    = findViewById(R.id.tvFileName);
        tvStatus      = findViewById(R.id.tvStatus);
        tvMixStatus   = findViewById(R.id.tvMixStatus);
        btnSelectAudio = findViewById(R.id.btnSelectAudio);
        btnSeparate   = findViewById(R.id.btnSeparate);
        btnPreviewMix = findViewById(R.id.btnPreviewMix);
        btnDownload   = findViewById(R.id.btnDownload);
        btnPlay       = findViewById(R.id.btnPlay);
        seekBar       = findViewById(R.id.seekBar);
        progressBar   = findViewById(R.id.progressBar);
        progressMix   = findViewById(R.id.progressMix);
        panelStems    = findViewById(R.id.panelStems);

        int[] cbIds   = {R.id.cbStem1,R.id.cbStem2,R.id.cbStem3,R.id.cbStem4,R.id.cbStem5,R.id.cbStem6,R.id.cbStem7,R.id.cbStem8,R.id.cbStem9};
        int[] btnIds  = {R.id.btnPlayStem1,R.id.btnPlayStem2,R.id.btnPlayStem3,R.id.btnPlayStem4,R.id.btnPlayStem5,R.id.btnPlayStem6,R.id.btnPlayStem7,R.id.btnPlayStem8,R.id.btnPlayStem9};
        int[] seekIds = {R.id.seekStem1,R.id.seekStem2,R.id.seekStem3,R.id.seekStem4,R.id.seekStem5,R.id.seekStem6,R.id.seekStem7,R.id.seekStem8,R.id.seekStem9};
        int[] etIds   = {R.id.etNameStem1,R.id.etNameStem2,R.id.etNameStem3,R.id.etNameStem4,R.id.etNameStem5,R.id.etNameStem6,R.id.etNameStem7,R.id.etNameStem8,R.id.etNameStem9};

        for (int i = 0; i < STEM_COUNT; i++) {
            cbStems[i]      = findViewById(cbIds[i]);
            btnPlayStems[i] = findViewById(btnIds[i]);
            seekStems[i]    = findViewById(seekIds[i]);
            etNameStems[i]  = findViewById(etIds[i]);
            final int idx   = i;
            btnPlayStems[i].setOnClickListener(v -> toggleStemPlay(idx));
            seekStems[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    if (fromUser && stemPlayers[idx] != null) stemPlayers[idx].seekTo(p);
                }
                public void onStartTrackingTouch(SeekBar s) {}
                public void onStopTrackingTouch(SeekBar s) {}
            });
        }

        tabLayout         = findViewById(R.id.tabLayout);
        scrollStemSeparator = findViewById(R.id.scrollStemSeparator);
        scrollVideoMix    = findViewById(R.id.scrollVideoMix);
        btnSelectBacking  = findViewById(R.id.btnSelectBacking);
        btnRecord         = findViewById(R.id.btnRecord);
        btnExportVideo    = findViewById(R.id.btnExportVideo);
        btnFlipCamera     = findViewById(R.id.btnFlipCamera);
        btnLatencyTest    = findViewById(R.id.btnLatencyTest);
        tvBackingName     = findViewById(R.id.tvBackingName);
        tvRecordStatus    = findViewById(R.id.tvRecordStatus);
        tvLatency         = findViewById(R.id.tvLatency);
        tvLatencyTest     = findViewById(R.id.tvLatencyTest);
        tvOffsetConfirm   = findViewById(R.id.tvOffsetConfirm);
        seekBackingVolume = findViewById(R.id.seekBackingVolume);
        seekLatency       = findViewById(R.id.seekLatency);
        cameraPreview     = findViewById(R.id.cameraPreview);
        switchMonitor     = findViewById(R.id.switchMonitor);
        radioGroupAudio   = findViewById(R.id.radioGroupAudio);

        requestPermissions();
        setupTabs();
        setupListeners();
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("🎛️ Stem Separator"));
        tabLayout.addTab(tabLayout.newTab().setText("🎬 VideoMix"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollStemSeparator.setVisibility(View.VISIBLE);
                    scrollVideoMix.setVisibility(View.GONE);
                    stopMonitoring();
                } else {
                    scrollStemSeparator.setVisibility(View.GONE);
                    scrollVideoMix.setVisibility(View.VISIBLE);
                    startCamera();
                    if (switchMonitor.isChecked()) startMonitoring();
                }
            }
            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupListeners() {
        btnSelectAudio.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            startActivityForResult(intent, PICK_AUDIO);
        });
        btnSeparate.setOnClickListener(v -> {
            if (selectedAudioUri == null) {
                Toast.makeText(this, "Selecione uma música primeiro!", Toast.LENGTH_SHORT).show();
                return;
            }
            separateStems();
        });
        btnPreviewMix.setOnClickListener(v -> generateMixPreview());
        btnDownload.setOnClickListener(v -> saveMix());
        btnPlay.setOnClickListener(v -> toggleMixPlay());
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mixPlayer != null) mixPlayer.seekTo(p);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        btnSelectBacking.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            startActivityForResult(intent, PICK_BACKING);
        });
        btnRecord.setOnClickListener(v -> {
            if (isPreparing) cancelPreparation();
            else if (!isRecording) startPreparation();
            else stopRecording();
        });
        btnFlipCamera.setOnClickListener(v -> { useFrontCamera = !useFrontCamera; startCamera(); });
        btnLatencyTest.setOnClickListener(v -> runDifferentialLatencyTest());
        radioGroupAudio.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioMicAndroid) { selectedAudioSource = MediaRecorder.AudioSource.MIC; tvLatencyTest.setText("🎤 Microfone do aparelho selecionado"); }
            else if (checkedId == R.id.radioMicBT) { selectedAudioSource = MediaRecorder.AudioSource.MIC; tvLatencyTest.setText("🎧 Bluetooth — use o Teste Diferencial!"); }
            else if (checkedId == R.id.radioMicWired) { selectedAudioSource = MediaRecorder.AudioSource.MIC; tvLatencyTest.setText("🔌 Entrada por fio selecionada"); }
            else if (checkedId == R.id.radioMicOTG) { selectedAudioSource = MediaRecorder.AudioSource.MIC; tvLatencyTest.setText("🎛️ OTG selecionado"); }
        });
        seekLatency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                latencyMs = p - 2500; // centralizado: 0-2500 = negativo, 2500-5000 = positivo
                String sinal = latencyMs > 0 ? "+" : "";
                tvLatency.setText(sinal + latencyMs + " ms");
                if (latencyMs == 0) {
                    tvOffsetConfirm.setText("✅ Sem offset aplicado");
                } else {
                    tvOffsetConfirm.setText("✅ Offset aplicado: " + sinal + latencyMs + "ms");
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        seekBackingVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (backingPlayer != null) { float v = p / 100f; backingPlayer.setVolume(v, v); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        switchMonitor.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) startMonitoring(); else stopMonitoring();
        });
    }

    // ===================== SEPARAÇÃO =====================

    private void separateStems() {
        progressBar.setVisibility(View.VISIBLE);
        btnSeparate.setEnabled(false);
        tvStatus.setText("Enviando áudio...");
        panelStems.setVisibility(View.GONE);
        btnPreviewMix.setVisibility(View.GONE);
        btnDownload.setVisibility(View.GONE);
        btnPlay.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        stopAllStemPlayers();

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
                    .post(uploadBody).build();
                Response uploadResponse = client.newCall(uploadRequest).execute();
                String uploadStr = uploadResponse.body().string();
                if (!uploadResponse.isSuccessful()) throw new Exception("Upload falhou: " + uploadStr);

                JSONArray uploadedFiles = new JSONArray(uploadStr);
                String uploadedPath = uploadedFiles.getString(0);

                runOnUiThread(() -> tvStatus.setText("Separando stems... (aguarde)"));

                JSONObject fileData = new JSONObject();
                fileData.put("path", uploadedPath);
                fileData.put("meta", new JSONObject("{\"_type\":\"gradio.FileData\"}"));
                JSONArray dataArray = new JSONArray();
                dataArray.put(fileData);
                JSONObject body = new JSONObject();
                body.put("data", dataArray);

                Request predictRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/separate")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
                Response predictResponse = client.newCall(predictRequest).execute();
                String predictStr = predictResponse.body().string();
                if (!predictResponse.isSuccessful()) throw new Exception("Separação falhou: " + predictStr);

                JSONObject predictJson = new JSONObject(predictStr);
                if (!predictJson.has("event_id")) throw new Exception("Sem event_id: " + predictStr);
                String eventId = predictJson.getString("event_id");

                runOnUiThread(() -> tvStatus.setText("Aguardando resultado..."));

                Request resultRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/separate/" + eventId)
                    .get().build();
                Response resultResponse = client.newCall(resultRequest).execute();
                String resultStr = resultResponse.body().string();

                String dataLine = null;
                for (String line : resultStr.split("\n")) {
                    if (line.startsWith("data: ")) {
                        String candidate = line.substring(6).trim();
                        if (candidate.startsWith("[")) dataLine = candidate;
                    }
                }
                if (dataLine == null) throw new Exception("SSE inválido: " + resultStr.substring(0, Math.min(300, resultStr.length())));

                JSONArray resultData = new JSONArray(dataLine);
                for (int i = 0; i < STEM_COUNT; i++) {
                    if (i < resultData.length() - 1) {
                        Object item = resultData.get(i);
                        if (item instanceof JSONObject) {
                            JSONObject stemObj = (JSONObject) item;
                            String url = stemObj.optString("url", "");
                            String path = stemObj.optString("path", "");
                            if (url.isEmpty() && !path.isEmpty()) url = BASE_URL + "/gradio_api/file=" + path;
                            stemUrls[i] = url.isEmpty() ? null : url;
                        } else {
                            stemUrls[i] = null;
                        }
                    } else {
                        stemUrls[i] = null;
                    }
                }

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    tvStatus.setText("✅ Stems prontos! Ouça, nomeie e escolha.");
                    panelStems.setVisibility(View.VISIBLE);
                    btnPreviewMix.setVisibility(View.VISIBLE);
                    btnSeparate.setEnabled(true);
                    for (int i = 0; i < STEM_COUNT; i++) {
                        boolean available = stemUrls[i] != null;
                        btnPlayStems[i].setEnabled(available);
                        cbStems[i].setEnabled(available);
                        cbStems[i].setChecked(available);
                        if (!available) etNameStems[i].setHint("(stem não disponível)");
                    }
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

    // ===================== PLAYERS DE STEMS =====================

    private void toggleStemPlay(int idx) {
        if (stemUrls[idx] == null) return;
        for (int i = 0; i < STEM_COUNT; i++) {
            if (i != idx && stemPlayers[i] != null && stemPlayers[i].isPlaying()) {
                stemPlayers[i].pause();
                btnPlayStems[i].setText("▶");
            }
        }
        if (mixPlayer != null && mixPlayer.isPlaying()) {
            mixPlayer.pause();
            btnPlay.setText("▶️ PLAY");
        }
        if (stemPlayers[idx] != null && stemPlayers[idx].isPlaying()) {
            stemPlayers[idx].pause();
            btnPlayStems[idx].setText("▶");
            return;
        }
        if (stemPlayers[idx] != null) {
            stemPlayers[idx].start();
            btnPlayStems[idx].setText("⏸");
            updateStemSeekBar(idx);
            return;
        }
        btnPlayStems[idx].setEnabled(false);
        btnPlayStems[idx].setText("...");
        new Thread(() -> {
            try {
                Request req = new Request.Builder().url(stemUrls[idx]).get().build();
                Response resp = client.newCall(req).execute();
                byte[] bytes = resp.body().bytes();
                File tmp = File.createTempFile("stem_" + idx, ".mp3", getCacheDir());
                FileOutputStream fos = new FileOutputStream(tmp);
                fos.write(bytes);
                fos.close();
                MediaPlayer mp = new MediaPlayer();
                mp.setDataSource(tmp.getAbsolutePath());
                mp.prepare();
                mp.setOnCompletionListener(m -> runOnUiThread(() -> {
                    btnPlayStems[idx].setText("▶");
                    seekStems[idx].setProgress(0);
                }));
                stemPlayers[idx] = mp;
                runOnUiThread(() -> {
                    seekStems[idx].setMax(mp.getDuration());
                    mp.start();
                    btnPlayStems[idx].setText("⏸");
                    btnPlayStems[idx].setEnabled(true);
                    updateStemSeekBar(idx);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnPlayStems[idx].setText("▶");
                    btnPlayStems[idx].setEnabled(true);
                    Toast.makeText(this, "Erro stem " + (idx+1) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateStemSeekBar(int idx) {
        new Thread(() -> {
            while (stemPlayers[idx] != null && stemPlayers[idx].isPlaying()) {
                final int pos = stemPlayers[idx].getCurrentPosition();
                runOnUiThread(() -> seekStems[idx].setProgress(pos));
                try { Thread.sleep(300); } catch (Exception e) { break; }
            }
        }).start();
    }

    private void stopAllStemPlayers() {
        for (int i = 0; i < STEM_COUNT; i++) {
            if (stemPlayers[i] != null) { stemPlayers[i].release(); stemPlayers[i] = null; }
            stemUrls[i] = null;
        }
    }

    // ===================== MIX =====================

    private void generateMixPreview() {
        List<String> selectedUrls = new ArrayList<>();
        for (int i = 0; i < STEM_COUNT; i++) {
            if (cbStems[i].isChecked() && stemUrls[i] != null) selectedUrls.add(stemUrls[i]);
        }
        if (selectedUrls.isEmpty()) {
            Toast.makeText(this, "Selecione pelo menos uma faixa!", Toast.LENGTH_SHORT).show();
            return;
        }
        progressMix.setVisibility(View.VISIBLE);
        btnPreviewMix.setEnabled(false);
        tvMixStatus.setText("Gerando mix...");
        btnPlay.setVisibility(View.GONE);
        seekBar.setVisibility(View.GONE);
        btnDownload.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                List<String> uploadedPaths = new ArrayList<>();
                for (int i = 0; i < selectedUrls.size(); i++) {
                    final int fi = i;
                    runOnUiThread(() -> tvMixStatus.setText("Preparando faixas... (" + (fi+1) + "/" + selectedUrls.size() + ")"));
                    Request req = new Request.Builder().url(selectedUrls.get(i)).get().build();
                    Response resp = client.newCall(req).execute();
                    byte[] bytes = resp.body().bytes();
                    RequestBody uploadBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("files", "stem_" + i + ".mp3",
                            RequestBody.create(bytes, MediaType.parse("audio/mpeg")))
                        .build();
                    Request uploadReq = new Request.Builder()
                        .url(BASE_URL + "/gradio_api/upload")
                        .post(uploadBody).build();
                    Response uploadResp = client.newCall(uploadReq).execute();
                    JSONArray arr = new JSONArray(uploadResp.body().string());
                    uploadedPaths.add(arr.getString(0));
                }

                runOnUiThread(() -> tvMixStatus.setText("Mixando faixas..."));

                JSONArray dataArray = new JSONArray();
                int uploadIdx = 0;
                for (int i = 0; i < STEM_COUNT; i++) {
                    if (cbStems[i].isChecked() && stemUrls[i] != null && uploadIdx < uploadedPaths.size()) {
                        JSONObject fileData = new JSONObject();
                        fileData.put("path", uploadedPaths.get(uploadIdx));
                        fileData.put("meta", new JSONObject("{\"_type\":\"gradio.FileData\"}"));
                        dataArray.put(fileData);
                        uploadIdx++;
                    } else {
                        dataArray.put(JSONObject.NULL);
                    }
                }
                JSONObject body = new JSONObject();
                body.put("data", dataArray);

                Request mixRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/mix")
                    .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                    .build();
                Response mixResponse = client.newCall(mixRequest).execute();
                String mixStr = mixResponse.body().string();
                if (!mixResponse.isSuccessful()) throw new Exception("Mix falhou: " + mixStr);

                JSONObject mixJson = new JSONObject(mixStr);
                if (!mixJson.has("event_id")) throw new Exception("Sem event_id mix");
                String eventId = mixJson.getString("event_id");

                Request resultRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/mix/" + eventId)
                    .get().build();
                Response resultResponse = client.newCall(resultRequest).execute();
                String resultStr = resultResponse.body().string();

                String dataLine = null;
                for (String line : resultStr.split("\n")) {
                    if (line.startsWith("data: ")) {
                        String candidate = line.substring(6).trim();
                        if (candidate.startsWith("[")) dataLine = candidate;
                    }
                }
                if (dataLine == null) throw new Exception("SSE mix inválido");

                JSONArray resultData = new JSONArray(dataLine);
                JSONObject audioResult = resultData.getJSONObject(0);
                String resultUrl = audioResult.optString("url", "");
                String resultPath = audioResult.optString("path", "");
                if (resultUrl.isEmpty() && !resultPath.isEmpty()) resultUrl = BASE_URL + "/gradio_api/file=" + resultPath;

                Request dlReq = new Request.Builder().url(resultUrl).get().build();
                mixBytes = client.newCall(dlReq).execute().body().bytes();

                tempMixFile = File.createTempFile("soundmix_preview", ".mp3", getCacheDir());
                FileOutputStream fos = new FileOutputStream(tempMixFile);
                fos.write(mixBytes);
                fos.close();

                if (mixPlayer != null) mixPlayer.release();
                mixPlayer = new MediaPlayer();
                mixPlayer.setDataSource(tempMixFile.getAbsolutePath());
                mixPlayer.prepare();
                mixPlayer.setOnCompletionListener(mp -> runOnUiThread(() -> {
                    btnPlay.setText("▶️ PLAY");
                    seekBar.setProgress(0);
                }));

                runOnUiThread(() -> {
                    progressMix.setVisibility(View.GONE);
                    tvMixStatus.setText("✅ Mix pronto! Ouça antes de salvar.");
                    btnPlay.setVisibility(View.VISIBLE);
                    btnPlay.setText("▶️ PLAY");
                    seekBar.setVisibility(View.VISIBLE);
                    seekBar.setMax(mixPlayer.getDuration());
                    btnDownload.setVisibility(View.VISIBLE);
                    btnPreviewMix.setEnabled(true);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressMix.setVisibility(View.GONE);
                    tvMixStatus.setText("Erro: " + e.getMessage());
                    btnPreviewMix.setEnabled(true);
                });
            }
        }).start();
    }

    private void toggleMixPlay() {
        if (mixPlayer == null) return;
        if (mixPlayer.isPlaying()) {
            mixPlayer.pause();
            btnPlay.setText("▶️ PLAY");
        } else {
            for (int i = 0; i < STEM_COUNT; i++) {
                if (stemPlayers[i] != null && stemPlayers[i].isPlaying()) {
                    stemPlayers[i].pause();
                    btnPlayStems[i].setText("▶");
                }
            }
            mixPlayer.start();
            btnPlay.setText("⏸️ PAUSAR");
            new Thread(() -> {
                while (mixPlayer != null && mixPlayer.isPlaying()) {
                    final int pos = mixPlayer.getCurrentPosition();
                    runOnUiThread(() -> seekBar.setProgress(pos));
                    try { Thread.sleep(300); } catch (Exception e) { break; }
                }
            }).start();
        }
    }

    private void saveMix() {
        if (mixBytes == null) return;
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "soundmix_" + System.currentTimeMillis() + ".mp3");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(mixBytes);
            fos.close();
            tvMixStatus.setText("💾 Salvo: " + file.getName());
            Toast.makeText(this, "Mix salvo na pasta Music!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ===================== VIDEOMIX =====================

    private void startPreparation() {
        isPreparing = true;
        btnRecord.setText("❌ CANCELAR");
        btnFlipCamera.setEnabled(false);
        countDownTimer = new CountDownTimer(15000, 1000) {
            public void onTick(long ms) { tvRecordStatus.setText("⏳ Prepare-se! " + (ms/1000+1) + "s"); }
            public void onFinish() { startPreviewPhase(); }
        }.start();
    }

    private void startPreviewPhase() {
        startBackingTrack();
        if (switchMonitor.isChecked()) startMonitoring();
        countDownTimer = new CountDownTimer(30000, 1000) {
            public void onTick(long ms) { tvRecordStatus.setText("🎵 Treine! Prévia tocando... " + (ms/1000+1) + "s"); }
            public void onFinish() { stopBackingTrack(); stopMonitoring(); startFinalCountdown(); }
        }.start();
    }

    private void startFinalCountdown() {
        playBeep(); // beep 880Hz avisa que gravação vai começar
        if (switchMonitor.isChecked() && !isMonitoring.get()) startMonitoring();
        countDownTimer = new CountDownTimer(5000, 1000) {
            public void onTick(long ms) { tvRecordStatus.setText("🔴 Gravando em " + (ms/1000+1) + "s..."); }
            public void onFinish() { isPreparing = false; startRecording(); }
        }.start();
    }

    private void cancelPreparation() {
        if (countDownTimer != null) { countDownTimer.cancel(); countDownTimer = null; }
        stopBackingTrack();
        stopMonitoring();
        isPreparing = false;
        isRecording = false;
        btnRecord.setText("⏺️ INICIAR GRAVAÇÃO");
        btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
        btnFlipCamera.setEnabled(true);
        tvRecordStatus.setText("❌ Cancelado.");
    }

    private void playBeep() {
        new Thread(() -> {
            try {
                int sr = 44100, duration = sr / 4;
                short[] samples = new short[duration];
                for (int i = 0; i < duration; i++)
                    samples[i] = (short)(Short.MAX_VALUE * Math.sin(2 * Math.PI * 880 * i / sr));
                AudioTrack beepTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, duration * 2, AudioTrack.MODE_STATIC);
                beepTrack.write(samples, 0, samples.length);
                beepTrack.play();
                Thread.sleep(300);
                beepTrack.stop();
                beepTrack.release();
            } catch (Exception e) {}
        }).start();
    }

    private void startMonitoring() {
        if (isMonitoring.get()) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        audioRecord = new AudioRecord(selectedAudioSource, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, AudioTrack.MODE_STREAM);
        isMonitoring.set(true);
        audioRecord.startRecording();
        audioTrack.play();
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (isMonitoring.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) audioTrack.write(buffer, 0, read);
            }
            audioRecord.stop(); audioRecord.release();
            audioTrack.stop(); audioTrack.release();
            audioRecord = null; audioTrack = null;
        }).start();
    }

    private void stopMonitoring() { isMonitoring.set(false); }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cp = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                Recorder recorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build();
                videoCapture = VideoCapture.withOutput(recorder);
                CameraSelector cs = useFrontCamera ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
                cp.unbindAll();
                cp.bindToLifecycle(this, cs, preview, videoCapture);
            } catch (Exception e) { tvRecordStatus.setText("Erro câmera: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void startRecording() {
        if (videoCapture == null) { Toast.makeText(this, "Câmera não iniciada!", Toast.LENGTH_SHORT).show(); return; }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "soundmix_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SoundMix");
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão de áudio necessária!", Toast.LENGTH_SHORT).show(); return;
        }
        currentRecording = videoCapture.getOutput().prepareRecording(this, options).withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this), event -> {
                if (event instanceof VideoRecordEvent.Start) {
                    isRecording = true;
                    btnRecord.setText("⏹️ PARAR GRAVAÇÃO");
                    btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF607D8B));
                    btnFlipCamera.setEnabled(false);
                    if (switchMonitor.isChecked() && !isMonitoring.get()) startMonitoring();

                    // 5s de vídeo gravando em silêncio como régua de referência
                    tvRecordStatus.setText("🔴 Gravando... (régua 5s)");
                    new CountDownTimer(5000, 1000) {
                        public void onTick(long ms) {
                            tvRecordStatus.setText("🔴 " + (ms/1000+1) + "s — Gravação de vídeo como régua para organização e edição de tempos no seu editor");
                        }
                        public void onFinish() {
                            // Beep NÃO gravável — avisa que backing track vai entrar
                            playBeepBacking();
                            // Backing track entra após o beep (~500ms)
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                startBackingTrack();
                                tvRecordStatus.setText("🔴 Gravando... 🎵 Backing ativa!");
                            }, 500);
                        }
                    }.start();

                } else if (event instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event;
                    if (!fin.hasError()) { lastVideoUri = fin.getOutputResults().getOutputUri(); tvRecordStatus.setText("✅ Vídeo salvo em Movies/SoundMix!"); btnExportVideo.setVisibility(View.VISIBLE); }
                    else tvRecordStatus.setText("Erro: " + fin.getError());
                    isRecording = false; stopMonitoring();
                    btnRecord.setText("⏺️ INICIAR GRAVAÇÃO");
                    btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
                    btnFlipCamera.setEnabled(true);
                }
            });
    }

    // Beep NÃO gravável — tom diferente (1200Hz) pra avisar entrada da backing track
    private void playBeepBacking() {
        new Thread(() -> {
            try {
                int sr = 44100, duration = sr / 3; // ~333ms
                short[] samples = new short[duration];
                for (int i = 0; i < duration; i++)
                    samples[i] = (short)(Short.MAX_VALUE * Math.sin(2 * Math.PI * 1200 * i / sr));
                AudioTrack beepTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    duration * 2, AudioTrack.MODE_STATIC);
                beepTrack.write(samples, 0, samples.length);
                beepTrack.play();
                Thread.sleep(400);
                beepTrack.stop();
                beepTrack.release();
            } catch (Exception e) {}
        }).start();
    }

    private void stopRecording() {
        if (currentRecording != null) { currentRecording.stop(); currentRecording = null; }
        stopBackingTrack();
        stopMonitoring();
    }

    private void startBackingTrack() {
        if (backingTrackUri == null) return;
        long delay = Math.max(0, latencyMs); // só atrasa se offset positivo
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                if (backingPlayer != null) backingPlayer.release();
                backingPlayer = new MediaPlayer();
                backingPlayer.setDataSource(this, backingTrackUri);
                backingPlayer.setLooping(true);
                float vol = seekBackingVolume.getProgress() / 100f;
                backingPlayer.setVolume(vol, vol);
                backingPlayer.prepare();
                backingPlayer.start();
                // Se offset negativo, adianta a posição da backing track
                if (latencyMs < 0) backingPlayer.seekTo(Math.abs(latencyMs));
            } catch (Exception e) { tvRecordStatus.setText("Erro backing: " + e.getMessage()); }
        }, delay);
    }

    private void stopBackingTrack() {
        if (backingPlayer != null) { backingPlayer.stop(); backingPlayer.release(); backingPlayer = null; }
    }



    // ===================== LATÊNCIA =====================

    private void runDifferentialLatencyTest() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissão de áudio necessária!", Toast.LENGTH_SHORT).show(); return;
        }
        tvLatencyTest.setText("🔬 Iniciando teste...");
        btnLatencyTest.setEnabled(false);
        new Thread(() -> {
            try {
                int sr = 44100;
                int bufSize = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 4;
                AudioRecord recA = new AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                AudioRecord recS = new AudioRecord(selectedAudioSource, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize);
                AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize, AudioTrack.MODE_STREAM);
                short[] beep = new short[sr / 5];
                for (int i = 0; i < beep.length; i++) beep[i] = (short)(Short.MAX_VALUE * 0.8 * Math.sin(2 * Math.PI * 1000 * i / sr));
                recA.startRecording(); recS.startRecording(); Thread.sleep(300);
                player.play();
                long beepStart = System.currentTimeMillis();
                player.write(beep, 0, beep.length);
                short[] bA = new short[bufSize], bS = new short[bufSize];
                long dA = -1, dS = -1, timeout = System.currentTimeMillis() + 3000;
                int threshold = 6000;
                while (System.currentTimeMillis() < timeout) {
                    if (dA < 0) { int r = recA.read(bA, 0, bA.length); for (int i = 0; i < r; i++) if (Math.abs(bA[i]) > threshold) { dA = System.currentTimeMillis(); break; } }
                    if (dS < 0) { int r = recS.read(bS, 0, bS.length); for (int i = 0; i < r; i++) if (Math.abs(bS[i]) > threshold) { dS = System.currentTimeMillis(); break; } }
                    if (dA > 0 && dS > 0) break;
                }
                recA.stop(); recA.release(); recS.stop(); recS.release(); player.stop(); player.release();
                long fA = dA, fS = dS;
                runOnUiThread(() -> {
                    btnLatencyTest.setEnabled(true);
                    if (fA > 0 && fS > 0) {
                        long diff = Math.abs((fS - beepStart) - (fA - beepStart));
                        seekLatency.setProgress((int) diff + 2500); latencyMs = (int) diff; tvLatency.setText("+" + diff + " ms");
                        tvOffsetConfirm.setText("✅ Offset aplicado: +" + diff + "ms");
                        tvLatencyTest.setText("✅ Android: " + (fA-beepStart) + "ms | Dispositivo: " + (fS-beepStart) + "ms | Diff: " + diff + "ms");
                    } else if (fA > 0) tvLatencyTest.setText("⚠️ Dispositivo não detectou sinal.");
                    else tvLatencyTest.setText("❌ Nenhum sinal detectado.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { tvLatencyTest.setText("Erro: " + e.getMessage()); btnLatencyTest.setEnabled(true); });
            }
        }).start();
    }

    // ===================== UTILITÁRIOS =====================

    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
        return buffer.toByteArray();
    }

    private void requestPermissions() {
        List<String> perms = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_AUDIO);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
        if (!perms.isEmpty()) ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == PICK_AUDIO) { selectedAudioUri = data.getData(); tvFileName.setText(selectedAudioUri.getLastPathSegment()); }
        else if (requestCode == PICK_BACKING) { backingTrackUri = data.getData(); tvBackingName.setText(backingTrackUri.getLastPathSegment()); }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        stopMonitoring();
        stopAllStemPlayers();
        if (mixPlayer != null) { mixPlayer.release(); mixPlayer = null; }
        if (backingPlayer != null) { backingPlayer.release(); backingPlayer = null; }
        cameraExecutor.shutdown();
    }
}
