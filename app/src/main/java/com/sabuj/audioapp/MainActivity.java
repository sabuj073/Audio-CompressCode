package com.sabuj.audioapp;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.ReturnCode;
import com.arthenica.ffmpegkit.StreamInformation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private static final int PICK_AUDIO_REQUEST_CODE = 1;
    private ProgressBar progressBar;
    EditText editText_compression_percentage;
    Button button_apply;
    int reduce;
    TextView durationtext;

    String selectedFilePath="";

    private static void apply(com.arthenica.ffmpegkit.Session session) {
        final ReturnCode returnCode = session.getReturnCode();
        if (ReturnCode.isSuccess(returnCode)) {
            Log.d("FFmpeg", "Compression successful");
        } else if (ReturnCode.isCancel(returnCode)) {
            Log.d("FFmpeg", "Compression cancelled");
        } else {
            Log.d("FFmpeg", "Compression failed");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PICK_AUDIO_REQUEST_CODE);
        }

        editText_compression_percentage = findViewById(R.id.editText_compression_percentage);
        button_apply = findViewById(R.id.button_apply);
        progressBar = findViewById(R.id.progressBar);
        progressBar.setVisibility(View.GONE);
        durationtext = findViewById(R.id.durationtext);


        findViewById(R.id.button_select_audio).setOnClickListener(v -> pickAudioFromGallery());

        button_apply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                reduce = Integer.parseInt(editText_compression_percentage.getText().toString());
                compressAudioFile(selectedFilePath,reduce);
            }
        });
    }

    private void calculateAndDisplayDuration(String filePath) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(filePath);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = Long.parseLong(durationStr); // Duration in milliseconds
            String durationFormatted = formatDuration(durationMs);
            durationtext.setText("Duration: " + durationFormatted);
        } catch (Exception e) {
            durationtext.setText("Duration: Unknown");
            Log.e("DurationError", "Could not calculate duration", e);
        } finally {
            retriever.release();
        }
    }

    private String formatDuration(long durationMs) {
        long seconds = (durationMs / 1000) % 60;
        long minutes = (durationMs / (1000 * 60)) % 60;
        long hours = (durationMs / (1000 * 60 * 60)) % 24;

        if (hours > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }



    private void pickAudioFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(intent, PICK_AUDIO_REQUEST_CODE);
    }

    private String copyFileFromUri(Uri contentUri) {
        String filePath = getExternalFilesDir(Environment.DIRECTORY_MUSIC) + "/temp_audio_file";
        try {
            InputStream inputStream = getContentResolver().openInputStream(contentUri);
            OutputStream outputStream = new FileOutputStream(filePath);
            byte[] buf = new byte[1024];
            int len;
            while ((inputStream != null) && ((len = inputStream.read(buf)) != -1)) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (Exception e) {
            Log.e("MainActivity", "Error copying file: " + e.getMessage());
            filePath = "";
        }
        return filePath;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_AUDIO_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                Uri audioUri = data.getData();
                selectedFilePath = copyFileFromUri(audioUri);
                try {
                    calculateAndDisplayDuration(selectedFilePath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

//    private void compressAudioFile(String inputFilePath, int reduce) {
//
//        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));
//        // First, we need to get the bitrate of the original file
//        FFprobeKit.getMediaInformationAsync(inputFilePath, info -> {
//            MediaInformation mediaInformation = info.getMediaInformation();
//            if (mediaInformation != null) {
//                StreamInformation audioStream = mediaInformation.getStreams().get(0);
//                String originalBitrateString = audioStream.getBitrate();
//
//                if (originalBitrateString != null) {
//                    int originalBitrate = Integer.parseInt(originalBitrateString);
//                    originalBitrate = originalBitrate/1000;
//                    float reduce_temp = 1 - (reduce / 100.0f);
//                    int targetBitrate = (int) (originalBitrate * reduce_temp);
//
//                    String fileName = "compressed_audio.mp3";
//                    deleteExistingFileInMusicDirectory(fileName);
//                    ContentValues values = new ContentValues();
//                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
//                    values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3");
//                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC);
//
//                    // Get URI for the file
//                    Uri audioUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
//
//                    if (audioUri != null) {
//                        try {
//                            OutputStream outputStream = getContentResolver().openOutputStream(audioUri);
//                            if (outputStream != null) {
//                                File outputFile = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName);
//                                Log.d("Output File", String.valueOf(outputFile));
//                                if (outputFile.exists()) {
//                                    Log.d("Output File exists", String.valueOf(outputFile));
//                                    outputFile.delete();
//                                }
//                                String outputFilePath = outputFile.getAbsolutePath();
//                                String cmd = "-y -i " + inputFilePath + " -b:a " + reduce + "k -ar 22050 -ac 1 " + outputFilePath;
//                                Log.d("CMD",cmd);
//                                FFmpegKit.executeAsync(cmd, session -> {
//                                    if (ReturnCode.isSuccess(session.getReturnCode())) {
//                                        try {
//                                            FileInputStream inputStream = new FileInputStream(outputFilePath);
//                                            byte[] buffer = new byte[1024];
//                                            int length;
//                                            while ((length = inputStream.read(buffer)) > 0) {
//                                                outputStream.write(buffer, 0, length);
//                                            }
//                                            outputStream.flush();
//                                            inputStream.close();
//                                            outputStream.close();
//
//                                            // Delete the temporary compressed file
//                                            new File(outputFilePath).delete();
//
//                                            // Log and Toast message
//                                            Log.d("CompressedFilePath", "File saved at: " + audioUri.toString());
//                                            runOnUiThread(() -> Toast.makeText(MainActivity.this, "File saved at: " + audioUri.toString(), Toast.LENGTH_LONG).show());
//                                            runOnUiThread(() -> progressBar.setVisibility(View.GONE));
//                                        } catch (IOException e) {
//                                            e.printStackTrace();
//                                            runOnUiThread(() -> progressBar.setVisibility(View.GONE));
//                                        }
//                                    }
//                                });
//                            }
//                        } catch (FileNotFoundException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            }
//        });
//    }

    private void compressAudioFile(String inputFilePath, int reduce) {
        runOnUiThread(() -> progressBar.setVisibility(View.VISIBLE));

        // Custom directory within the app's external files directory
        String customDirectoryName = "Audio";
        File customDirectory = new File(getExternalFilesDir(null), customDirectoryName);
        if (!customDirectory.exists() && !customDirectory.mkdirs()) {
            Log.e("compressAudioFile", "Could not create custom directory");
            return;
        }
        String fileName = "compressed_audio.mp3";
        File outputFile = new File(customDirectory, fileName);

        // Check and delete existing file
        if (outputFile.exists()) {
            boolean deleted = outputFile.delete();
            if (!deleted) {
                Log.e("compressAudioFile", "Could not delete existing file");
                return;
            }
        }

        FFprobeKit.getMediaInformationAsync(inputFilePath, info -> {
            MediaInformation mediaInformation = info.getMediaInformation();
            if (mediaInformation != null && mediaInformation.getStreams() != null && !mediaInformation.getStreams().isEmpty()) {
                StreamInformation audioStream = mediaInformation.getStreams().get(0);
                String originalBitrateString = audioStream.getBitrate();

                if (originalBitrateString != null) {
                    int originalBitrate = Integer.parseInt(originalBitrateString) / 1000; // Convert to kbps
                    float reduceFactor = 1 - (reduce / 100.0f);
                    int targetBitrate = (int) (originalBitrate * reduceFactor);

                    // FFmpeg command to compress and save the audio
                    String outputFilePath = outputFile.getAbsolutePath();
                    String cmd = "-y -i " + inputFilePath + " -b:a " + targetBitrate + "k " + outputFilePath;

                    FFmpegKit.executeAsync(cmd, session -> {
                        if (ReturnCode.isSuccess(session.getReturnCode())) {
                            // Compression successful
                            Log.d("CompressAudio", "Audio compressed successfully: " + outputFilePath);
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Audio saved in custom directory.", Toast.LENGTH_SHORT).show();
                                progressBar.setVisibility(View.GONE);
                            });
                        } else {
                            // Compression failed
                            Log.e("CompressAudio", "Audio compression failed.");
                            runOnUiThread(() -> progressBar.setVisibility(View.GONE));
                        }
                    });
                }
            }
        });
    }


    private void deleteExistingFileInMusicDirectory(String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentResolver contentResolver = getContentResolver();
            Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
            String selection = MediaStore.Audio.Media.DISPLAY_NAME + "=?";
            String[] selectionArgs = new String[]{fileName};

            try {
                contentResolver.delete(collection, selection, selectionArgs);
                Log.d("Deletion", "Existing file deleted successfully");
            } catch (Exception e) {
                Log.d("Deletion", "Failed to delete existing file", e);
            }
        } else {
            // Implement for Android versions below 10 if necessary, noting scoped storage considerations
        }
    }

}