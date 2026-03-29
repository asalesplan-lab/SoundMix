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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
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
    private TabLayout tabLayout;
    private View scrollStemSeparator, scrollVideoMix;
    private Button btnSelectBacking, btnRecord, btnExportVideo, btnFlipCamera, btnLatencyTest;
    private TextView tvBackingName, tvRecordStatus, tvLatency, tvLatencyTest;
    private SeekBar seekBackingVolume, seekLatency;
    private PreviewView cameraPreview;
    private SwitchCompat switchMonitor;
    private Uri backingTrackUri;
    private MediaPlayer backingPlayer;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private ExecutorService cameraExecutor;
    private boolean isRecording = false;
    private Uri lastVideoUri;
    private int latencyMs = 0;
    private boolean useFrontCamera = false;
    private AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .build();
        cameraExecutor = Executors.newSingleThreadExecutor();
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
        tabLayout = findViewById(R.id.tabLayout);
        scrollStemSeparator = findViewById(R.id.scrollStemSeparator);
        scrollVideoMix = findViewById(R.id.scrollVideoMix);
        btnSelectBacking = findViewById(R.id.btnSelectBacking);
        btnRecord = findViewById(R.id.btnRecord);
        btnExportVideo = findViewById(R.id.btnExportVideo);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnLatencyTest = findViewById(R.id.btnLatencyTest);
        tvBackingName = findViewById(R.id.tvBackingName);
        tvRecordStatus = findViewById(R.id.tvRecordStatus);
        tvLatency = findViewById(R.id.tvLatency);
        tvLatencyTest = findViewById(R.id.tvLatencyTest);
        seekBackingVolume = findViewById(R.id.seekBackingVolume);
        seekLatency = findViewById(R.id.seekLatency);
        cameraPreview = findViewById(R.id.cameraPreview);
        switchMonitor = findViewById(R.id.switchMonitor);
        requestPermissions();
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
                }
            }
            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });
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
        btnSelectBacking.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("audio/*");
            startActivityForResult(intent, PICK_BACKING);
        });
        btnRecord.setOnClickListener(v -> toggleRecording());
        btnFlipCamera.setOnClickListener(v -> {
            useFrontCamera = !useFrontCamera;
            startCamera();
        });
        btnLatencyTest.setOnClickListener(v -> runLatencyTest());
        seekLatency.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                latencyMs = progress;
                tvLatency.setText(progress + " ms");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        seekBackingVolume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (backingPlayer != null) {
                    float vol = progress / 100f;
                    backingPlayer.setVolume(vol, vol);
                }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
    }
    private void startMonitoring() {
        if (isMonitoring.get()) return;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            BUFFER_SIZE, AudioTrack.MODE_STREAM);
        isMonitoring.set(true);
        audioRecord.startRecording();
        audioTrack.play();
        new Thread(() -> {
            byte[] buffer = new byte[BUFFER_SIZE];
            while (isMonitoring.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) audioTrack.write(buffer, 0, read);
            }
            audioRecord.stop();
            audioRecord.release();
            audioTrack.stop();
            audioTrack.release();
            audioRecord = null;
            audioTrack = null;
        }).start();
    }
    private void stopMonitoring() {
        isMonitoring.set(false);
    }
    private void runLatencyTest() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissao de audio necessaria!", Toast.LENGTH_SHORT).show();
            return;
        }
        tvLatencyTest.setText("🔬 Testando... use fone de ouvido!");
        btnLatencyTest.setEnabled(false);
        new Thread(() -> {
            try {
                int sr = 44100;
                int bufSize = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sr,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 4);
                AudioTrack player = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufSize, AudioTrack.MODE_STREAM);
                short[] beep = new short[sr / 10];
                for (int i = 0; i < beep.length; i++) {
                    beep[i] = (short) (Short.MAX_VALUE * Math.sin(2 * Math.PI * 1000 * i / sr));
                }
                recorder.startRecording();
                player.play();
                long startTime = System.currentTimeMillis();
                player.write(beep, 0, beep.length);
                short[] inBuf = new short[bufSize];
                long detectedTime = -1;
                long timeout = System.currentTimeMillis() + 2000;
                while (System.currentTimeMillis() < timeout) {
                    int read = recorder.read(inBuf, 0, inBuf.length);
                    for (int i = 0; i < read; i++) {
                        if (Math.abs(inBuf[i]) > 8000) {
                            detectedTime = System.currentTimeMillis();
                            break;
                        }
                    }
                    if (detectedTime > 0) break;
                }
                recorder.stop();
                recorder.release();
                player.stop();
                player.release();
                if (detectedTime > 0) {
                    int measuredLatency = (int)(detectedTime - startTime);
                    runOnUiThread(() -> {
                        seekLatency.setProgress(measuredLatency);
                        latencyMs = measuredLatency;
                        tvLatency.setText(measuredLatency + " ms");
                        tvLatencyTest.setText("✅ Latência detectada: " + measuredLatency + " ms");
                        btnLatencyTest.setEnabled(true);
                    });
                } else {
                    runOnUiThread(() -> {
                        tvLatencyTest.setText("❌ Nao detectado. Use fone de ouvido e tente novamente.");
                        btnLatencyTest.setEnabled(true);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvLatencyTest.setText("Erro: " + e.getMessage());
                    btnLatencyTest.setEnabled(true);
                });
            }
        }).start();
    }
    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD)).build();
                videoCapture = VideoCapture.withOutput(recorder);
                CameraSelector cameraSelector = useFrontCamera ?
                    CameraSelector.DEFAULT_FRONT_CAMERA :
                    CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
            } catch (Exception e) {
                tvRecordStatus.setText("Erro camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void toggleRecording() {
        if (!isRecording) startRecording();
        else stopRecording();
    }
    private void startRecording() {
        if (videoCapture == null) {
            Toast.makeText(this, "Camera nao iniciada!", Toast.LENGTH_SHORT).show();
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "soundmix_" + System.currentTimeMillis() + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SoundMix");
        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(
            getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values).build();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permissao de audio necessaria!", Toast.LENGTH_SHORT).show();
            return;
        }
        currentRecording = videoCapture.getOutput()
            .prepareRecording(this, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this), event -> {
                if (event instanceof VideoRecordEvent.Start) {
                    isRecording = true;
                    btnRecord.setText("⏹️ PARAR GRAVACAO");
                    btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF607D8B));
                    tvRecordStatus.setText("🔴 Gravando...");
                    btnFlipCamera.setEnabled(false);
                    startBackingTrack();
                    if (switchMonitor.isChecked()) startMonitoring();
                } else if (event instanceof VideoRecordEvent.Finalize) {
                    VideoRecordEvent.Finalize finalize = (VideoRecordEvent.Finalize) event;
                    if (!finalize.hasError()) {
                        lastVideoUri = finalize.getOutputResults().getOutputUri();
                        tvRecordStatus.setText("✅ Video salvo em Movies/SoundMix!");
                        btnExportVideo.setVisibility(View.VISIBLE);
                    } else {
                        tvRecordStatus.setText("Erro: " + finalize.getError());
                    }
                    isRecording = false;
                    stopMonitoring();
                    btnRecord.setText("⏺️ INICIAR GRAVACAO");
                    btnRecord.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFF44336));
                    btnFlipCamera.setEnabled(true);
                }
            });
    }
    private void stopRecording() {
        if (currentRecording != null) { currentRecording.stop(); currentRecording = null; }
        stopBackingTrack();
        stopMonitoring();
    }
    private void startBackingTrack() {
        if (backingTrackUri == null) return;
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
            } catch (Exception e) {
                tvRecordStatus.setText("Erro backing: " + e.getMessage());
            }
        }, latencyMs);
    }
    private void stopBackingTrack() {
        if (backingPlayer != null) { backingPlayer.stop(); backingPlayer.release(); backingPlayer = null; }
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.CAMERA);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            perms.add(Manifest.permission.RECORD_AUDIO);
        if (!perms.isEmpty())
            ActivityCompat.requestPermissions(this, perms.toArray(new String[0]), PERM_REQUEST);
    }
    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = is.read(chunk)) != -1) buffer.write(chunk, 0, n);
        return buffer.toByteArray();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode == PICK_AUDIO) {
            selectedAudioUri = data.getData();
            tvFileName.setText(selectedAudioUri.getLastPathSegment());
        } else if (requestCode == PICK_BACKING) {
            backingTrackUri = data.getData();
            tvBackingName.setText(backingTrackUri.getLastPathSegment());
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
        if (backingPlayer != null) { backingPlayer.release(); backingPlayer = null; }
        cameraExecutor.shutdown();
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
                    .post(uploadBody).build();
                Response uploadResponse = client.newCall(uploadRequest).execute();
                String uploadStr = uploadResponse.body().string();
                if (!uploadResponse.isSuccessful()) throw new Exception("Upload falhou: " + uploadStr);
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
                if (!predictResponse.isSuccessful()) throw new Exception("Predict falhou: " + predictStr);
                JSONObject predictJson = new JSONObject(predictStr);
                if (!predictJson.has("event_id")) throw new Exception("Resposta: " + predictStr.substring(0, Math.min(200, predictStr.length())));
                String eventId = predictJson.getString("event_id");
                runOnUiThread(() -> tvStatus.setText("Aguardando resultado..."));
                Request resultRequest = new Request.Builder()
                    .url(BASE_URL + "/gradio_api/call/process/" + eventId)
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
                if (dataLine == null) throw new Exception("SSE: " + resultStr.substring(0, Math.min(300, resultStr.length())));
                JSONArray resultData = new JSONArray(dataLine);
                JSONObject audioResult = resultData.getJSONObject(0);
                String resultPath = audioResult.optString("path", "");
                String resultUrl = audioResult.optString("url", "");
                if (resultUrl.isEmpty()) resultUrl = BASE_URL + "/gradio_api/file=" + resultPath;
                Request downloadRequest = new Request.Builder().url(resultUrl).get().build();
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
