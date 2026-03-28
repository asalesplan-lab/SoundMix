package com.soundmix.app;
import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
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
    private Button btnSelectBacking, btnRecord, btnExportVideo, btnFlipCamera;
    private TextView tvBackingName, tvRecordStatus, tvLatency;
    private SeekBar seekBackingVolume, seekLatency;
    private PreviewView cameraPreview;
    private Uri backingTrackUri;
    private MediaPlayer backingPlayer;
    private VideoCapture<Recorder> videoCapture;
    private Recording currentRecording;
    private ExecutorService cameraExecutor;
    private boolean isRecording = false;
    private Uri lastVideoUri;
    private int latencyMs = 0;
    private boolean useFrontCamera = false;
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
        tvBackingName = findViewById(R.id.tvBackingName);
        tvRecordStatus = findViewById(R.id.tvRecordStatus);
        tvLatency = findViewById(R.id.tvLatency);
        seekBackingVolume = findViewById(R.id.seekBackingVolume);
        seekLatency = findViewById(R.id.seekLatency);
        cameraPreview = findViewById(R.id.cameraPreview);
        requestPermissions();
        tabLayout.addTab(tabLayout.newTab().setText("🎛️ Stem Separator"));
        tabLayout.addTab(tabLayout.newTab().setText("🎬 VideoMix"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    scrollStemSeparator.setVisibility(View.VISIBLE);
                    scrollVideoMix.setVisibility(View.GONE);
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
    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build();
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
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
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
            .setContentValues(values)
            .build();
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
                    btnRecord.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFF607D8B));
                    tvRecordStatus.setText("🔴 Gravando...");
                    btnFlipCamera.setEnabled(false);
                    startBackingTrack();
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
                    btnRecord.setText("⏺️ INICIAR GRAVACAO");
                    btnRecord.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0xFFF44336));
                    btnFlipCamera.setEnabled(true);
                }
            });
    }
    private void stopRecording() {
        if (currentRecording != null) {
            currentRecording.stop();
            currentRecording = null;
        }
        stopBackingTrack();
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
        if (backingPlayer != null) {
            backingPlayer.stop();
            backingPlayer.release();
            backingPlayer = null;
        }
    }
