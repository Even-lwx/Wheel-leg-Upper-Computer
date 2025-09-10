//
// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE.md file in the project root for full license information.
//
package com.microsoft.cognitiveservices.speech.samples.sdkdemo;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import androidx.core.content.FileProvider;
import android.content.Intent;
import android.provider.MediaStore;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognitionResult;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.KeywordRecognitionModel;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;
import com.microsoft.cognitiveservices.speech.Connection;
import com.microsoft.cognitiveservices.speech.AudioDataStream;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.Manifest.permission.INTERNET;
import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.RECORD_AUDIO;

public class MainActivity extends AppCompatActivity {
    private static final String logTag = "keyword";
    private SpeakingRunnable speakingRunnable;
    private ExecutorService singleThreadExecutor;
    private SpeechSynthesizer synthesizer;
    private Connection connection;
    private AudioTrack audioTrack;
    private final Object synchronizedObj;
    private boolean stopped = false;
    //
    // Configuration for speech recognition
    //

    // Replace below with your own subscription key
    private static final String SpeechSubscriptionKey = "4zJKGra9pMHLReCDqpyaCROK7LO6wT9Bcx56tmc8LBtuVHj5H6r8JQQJ99BFACYeBjFXJ3w3AAAYACOGNOWg";

    private static final String SpeechRegion = "eastus";
    final SpeechConfig speechConfig=SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
    static int no_reg_count=0;
    //
    // Configuration for intent recognition
    //


    // Replace below with your own Keyword model file, kws.table model file is configured for "Computer" keyword
    private static final String KwsModelFile = "nihao.table";
    private KeywordRecognitionModel kwsModel;

        // 已移除 recognizedTextView 和 actionTextView
    private final Object lock = new Object();
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private String currentPhotoPath;
    private Future<SpeechRecognitionResult> task;

    private MicrophoneStream microphoneStream;

    public MainActivity() {
        synchronizedObj = new Object();
    }

    private MicrophoneStream createMicrophoneStream() {
        this.releaseMicrophoneStream();

        microphoneStream = new MicrophoneStream();
        return microphoneStream;
    }
    private void releaseMicrophoneStream() {
        if (microphoneStream != null) {
            microphoneStream.close();
            microphoneStream = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

    // recognizedTextView 和 actionTextView 已移除，对应UI逻辑请迁移到Fragment

    // resetButton已移除，无需再查找和设置监听

        // 底部导航栏点击事件
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        // 默认显示语音助手Fragment（可根据实际情况调整）
        replaceFragment(new PlaceholderFragment());
        bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            if (item.getItemId() == R.id.navigation_bluetooth) {
                fragment = new BluetoothRemoteFragment();
            } else if (item.getItemId() == R.id.navigation_voice_assistant) {
                fragment = new PlaceholderFragment(); // 这里用占位Fragment，后续可替换为语音助手Fragment
            }
            if (fragment != null) {
                replaceFragment(fragment);
                return true;
            }
            return false;
        });

    }

    private void replaceFragment(Fragment fragment) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();


        // Initialize SpeechSDK and request required permissions.
        try {
            // a unique number within the application to allow
            // correlating permission request responses with the request.
            int permissionRequestId = 5;

            // Request permissions needed for speech recognition
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{RECORD_AUDIO, INTERNET, READ_EXTERNAL_STORAGE}, permissionRequestId);
        }
        catch(Exception ex) {
            Log.e("SpeechSDK", "could not init sdk, " + ex.toString());
            // recognizedTextView.setText("Could not initialize: " + ex.toString());
        }

        // create config

        try {
            //speechConfig = SpeechConfig.fromSubscription(SpeechSubscriptionKey, SpeechRegion);
            kwsModel = KeywordRecognitionModel.fromFile(copyAssetToCacheAndGetFilePath(KwsModelFile));
            singleThreadExecutor = Executors.newSingleThreadExecutor();


            audioTrack = new AudioTrack(
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(24000)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    AudioTrack.getMinBufferSize(
                            24000,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT) * 2,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE);
            speechConfig.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.Raw24Khz16BitMonoPcm);
            speechConfig.setSpeechRecognitionLanguage("zh-CN");
            speechConfig.setSpeechSynthesisLanguage("zh-CN");
            // Set voice name.
            speechConfig.setSpeechSynthesisVoiceName("zh-CN-XiaoxiaoMultilingualNeural");//en-US-AvaMultilingualNeural"
            synthesizer = new SpeechSynthesizer(speechConfig, null);
            connection = Connection.fromSpeechSynthesizer(synthesizer);
            connection.openConnection(true);
            synthesizer.SynthesisCompleted.addEventListener((o, e) -> {
                Log.i(logTag, "Synthesis finished.\n");
                Log.i( logTag, e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFirstByteLatencyMs) + " ms.\n");
                Log.i( logTag,  e.getResult().getProperties().getProperty(PropertyId.SpeechServiceResponse_SynthesisFinishLatencyMs) + " ms.\n");
                e.close();

            });

            String hello = "您好，很高兴为您服务，您可以唤醒我说：你好！";
            // setRecognizedText(hello);
            speak(hello,()->{
                //speechConfig.setSpeechRecognitionLanguage("zh-CN");
                RegnizeKeyword( speechConfig, kwsModel);
            });

            // 找到按钮


        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            // displayException(ex);
            return;
        }

    }
    private void RegnizeKeyword(SpeechConfig speechConfig, KeywordRecognitionModel kwsModel){
        // setActionText("等待唤醒...");

        boolean continuousListeningStarted = false;
        SpeechRecognizer reco = null;
        AudioConfig audioInput = null;
        String buttonText = "";
        ArrayList<String> content = new ArrayList<>();
        try {
            content.clear();

            audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
            reco = new SpeechRecognizer(speechConfig, audioInput);

            reco.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String s = speechRecognitionResultEventArgs.getResult().getText();
                Log.i(logTag, "Intermediate result received: " + s);
                //content.add(s);
                //setRecognizedText(TextUtils.join(" ", content));
                //content.remove(content.size() - 1);
            });

            reco.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
                final String s;
                final String word;
                if (speechRecognitionResultEventArgs.getResult().getReason() == ResultReason.RecognizedKeyword)
                {
                    word=speechRecognitionResultEventArgs.getResult().getText();
                    s = "Keyword: " + word;
                    Log.i(logTag, "Keyword recognized result received: " + s);

                // setActionText("已唤醒!");
                    String welcome="我在听请讲。";
                // setRecognizedText(welcome);
                    speak(welcome,()->{
                        Regnize(speechConfig);
                    });

                }
                else
                {
                    word=speechRecognitionResultEventArgs.getResult().getText();
                    s = "Recognized: " + word;
                    Log.i(logTag, "Final result received: " + s);

                }

            });

            final Future<Void> task = reco.startKeywordRecognitionAsync(kwsModel);
            /*setOnTaskCompletedListener(task, result -> {

            });
            */
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            // displayException(ex);
        }
    }
    private void Regnize(SpeechConfig speechConfig){
        final String logTag = "reco 1";

        try {
            // In general, if the device default microphone is used then it is enough
            // to either have AudioConfig.fromDefaultMicrophoneInput or omit the audio
            // config altogether.
            // AudioConfig.fromStreamInput is specifically needed if you want to use an
            // external microphone (including Bluetooth that couldn't be otherwise used)
            // or mix audio from some other source to microphone audio.
            final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
            final SpeechRecognizer reco = new SpeechRecognizer(speechConfig, audioInput);
            // setActionText("倾听中...");
             task = reco.recognizeOnceAsync();
            setOnTaskCompletedListener(task, result -> {
                String s = "";
                if (result.getReason() == ResultReason.RecognizedSpeech) {
                    s = result.getText();
                // setRecognizedText(s);
                    // setActionText("已识别.");
                    //String errorDetails = (result.getReason() == ResultReason.Canceled) ? CancellationDetails.fromResult(result).getErrorDetails() : "";
                    //s = "Recognition failed with " + result.getReason() + ". Did you enter your subscription?" + System.lineSeparator() + errorDetails;
                }else if (result.getReason()==ResultReason.Canceled){
                    reco.close();
                    no_reg_count=0;
                // setActionText("重置...");
                    Log.i(logTag, "Reset");
                    String quit="Let start from begin. Please wait a moment!";
                // setRecognizedText(quit);
                    speak(quit,()->{
                        RegnizeKeyword( speechConfig, kwsModel);
                    });
                    return;
                }

                reco.close();
                Log.i(logTag, "Recognizer returned: " + s);
                if(s==""){
                    no_reg_count+=1;
                    Log.i(logTag, "no_reg_count: " + no_reg_count+"  s:"+s);
                // setActionText("未能识别.");
                    if (no_reg_count>=3){
                        String quit="我先退下了，您可以再次唤醒我说：你好！";
                // setRecognizedText(quit);
                        speak(quit,()->{
                            RegnizeKeyword( speechConfig, kwsModel);
                        });
                    }else{
                        String nohear="我没有听清，您还在说话吗？";
                // setRecognizedText(nohear);
                        speak(nohear,()->{
                            Regnize(speechConfig);
                        });
                    }
                }else{
                    no_reg_count=0;
                    String res = ChatAPI.generateText(s);
                // setRecognizedText(res);
                    speak(res,()->{
                        Regnize(speechConfig);
                    });
                }

            });
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            // displayException(ex);
        }
    }
private void speak(String s,ICallback callback){
        // setActionText("说话中...");
    speakingRunnable = new SpeakingRunnable();
    speakingRunnable.setContent(s);
    if (callback!=null){
        speakingRunnable.setCallback(callback);
    }
    singleThreadExecutor.execute(speakingRunnable);
}


        // 已移除 setRecognizedText、setActionText、displayException 方法

    private <T> void setOnTaskCompletedListener(Future<T> task, OnTaskCompletedListener<T> listener) {
        s_executorService.submit(() -> {
            T result = task.get();
            listener.onCompleted(result);
            return null;
        });
    }

    private interface OnTaskCompletedListener<T> {
        void onCompleted(T taskResult) throws IOException;
    }

    private String copyAssetToCacheAndGetFilePath(String filename) {
        File cacheFile = new File(getCacheDir() + "/" + filename);
        if (!cacheFile.exists()) {
            try {
                InputStream is = getAssets().open(filename);
                int size = is.available();
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();
                FileOutputStream fos = new FileOutputStream(cacheFile);
                fos.write(buffer);
                fos.close();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return cacheFile.getPath();
    }

    private static ExecutorService s_executorService;
    static {
        s_executorService = Executors.newCachedThreadPool();
    }

    public class Word {
        public String word;
        public String errorType;
        public double accuracyScore;
        public long duration;
        public long offset;
        public Word(String word, String errorType) {
            this.word = word;
            this.errorType = errorType;
        }

        public Word(String word, String errorType, double accuracyScore, long duration, long offset) {
            this(word, errorType);
            this.accuracyScore = accuracyScore;
            this.duration = duration;
            this.offset = offset;
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Release speech synthesizer and its dependencies
        if (synthesizer != null) {
            synthesizer.close();
            connection.close();
        }
        if (speechConfig != null) {
            speechConfig.close();
        }

        if (audioTrack != null) {
            singleThreadExecutor.shutdownNow();
            audioTrack.flush();
            audioTrack.stop();
            audioTrack.release();
        }
    }
    interface ICallback {
        void Callback();
    }
    class SpeakingRunnable implements Runnable {
        private String content;
        private ICallback _callback;
        private ICallback _stopCallback;
        public void setContent(String content) {
            this.content = content;
        }
        public void setCallback(ICallback callback){
            _callback = callback;
        }
        public void stop(ICallback stopCallback){
            stopped=true;
            _stopCallback=stopCallback;
        }
        @Override
        public void run() {
            try {
                audioTrack.play();
                synchronized (synchronizedObj) {
                    stopped = false;
                }
                //SSML
                //SpeechSynthesisResult result = synthesizer.StartSpeakingTextAsync(content).get();
                String ssmlContent = "<speak xmlns=\"http://www.w3.org/2001/10/synthesis\" xmlns:mstts=\"http://www.w3.org/2001/mstts\" xmlns:emo=\"http://www.w3.org/2009/10/emotionml\" version=\"1.0\"  xml:lang=\"zh-CN\"><voice name=\"zh-CN-XiaoxiaoMultilingualNeural\"><lang xml:lang=\"zh-CN\"><prosody rate=\"17%\">"+content.replace("*"," ")+"</prosody></lang></voice></speak> ";

                SpeechSynthesisResult result = synthesizer.StartSpeakingSsmlAsync(ssmlContent).get();
                AudioDataStream audioDataStream = AudioDataStream.fromResult(result);

                // Set the chunk size to 50 ms. 24000 * 16 * 0.05 / 8 = 2400
                byte[] buffer = new byte[2400];
                while (!stopped) {
                    long len = audioDataStream.readData(buffer);
                    if (len == 0) {
                        break;
                    }
                    audioTrack.write(buffer, 0, (int) len);
                }

                audioDataStream.close();
                if (_stopCallback!=null){
                    _stopCallback.Callback();
                    _stopCallback=null;
                    return;
                }
                if (_callback!=null){
                    _callback.Callback();
                }
            } catch (Exception ex) {
                Log.e("Speech Synthesis Demo", "unexpected " + ex.getMessage());
                ex.printStackTrace();
                assert(false);
            }
        }
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 确保有摄像头应用可以处理该 Intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // 创建文件保存照片
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // 处理文件创建失败的情况
                ex.printStackTrace();
            }
            // 如果文件创建成功
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.android.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            // 获取照片文件
            File imgFile = new File(currentPhotoPath);
            if (imgFile.exists()) {
                // 处理照片
                // 例如，显示在 ImageView 中
                // ImageView imageView = findViewById(R.id.imageView);
                // imageView.setImageURI(Uri.fromFile(imgFile));
            }
        }
    }

    private File createImageFile() throws IOException {
        // 创建以时间戳命名的图像文件名
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* 前缀 */
                ".jpg",         /* 后缀 */
                storageDir      /* 目录 */
        );

        // 保存文件路径供后续使用
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }
}
