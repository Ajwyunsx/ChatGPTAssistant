package com.cstdr.chatgpt.controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cstdr.chatgpt.R;
import com.cstdr.chatgpt.controller.adapter.ChatListAdapter;
import com.cstdr.chatgpt.model.ChatMessageData;
import com.cstdr.chatgpt.model.Constant;
import com.cstdr.chatgpt.model.IChatMessageData;
import com.cstdr.chatgpt.model.IJSONMessage;
import com.cstdr.chatgpt.model.JSONMessage;
import com.cstdr.chatgpt.util.ClipboardUtil;
import com.cstdr.chatgpt.util.JsonParser;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;
import net.gotev.speech.ui.SpeechProgressView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Context mContext;

    // Model层
    private IChatMessageData mChatMessageData;
    private IJSONMessage mJSONMessage;

    private RecyclerView mRvChatList;
    private EditText mEtQuestion;
    private Button mBtnSend;
    private Button mBtnRecord;

    private ChatListAdapter mListAdapter;

    public OkHttpClient client;
    private SpeechProgressView mSPVRecord;
    private LinearLayout mLlRecord;
    private Button mBtnStopSpeech;

    // ===============科大讯飞语音转写相关===================

    // 语音听写UI
    private RecognizerDialog mIatDialog;
    // 引擎类型
    private String mEngineType = SpeechConstant.TYPE_CLOUD;
    private String resultType = "json";
    private String language = "zh_cn";
    // 用HashMap存储听写结果
    private HashMap<String, String> mIatResults = new LinkedHashMap<>();

    // ===============科大讯飞语音合成相关===================
    /**
     * 可以去官网找自己喜欢的声音
     * https://console.xfyun.cn/services/tts
     */
    private String voicer = "aisxping";
    private SpeechSynthesizer mTts;
    private File pcmFile;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ClipboardUtil.init();

        // 将“12345678”替换成您申请的APPID，申请地址：http://www.xfyun.cn
        // 请勿在“=”与appid之间添加任何空字符或者转义符
        SpeechUtility.createUtility(this, SpeechConstant.APPID + "=febcf088");

        // 初始化识别无UI识别对象
        // 使用SpeechRecognizer对象，可根据回调消息自定义界面；
//        mIat = SpeechRecognizer.createRecognizer(this, mInitListener);

        mTts = SpeechSynthesizer.createSynthesizer(this, mInitListener);


        // ======================

//        Speech.init(this, getPackageName());

        mContext = this;
        mChatMessageData = ChatMessageData.getInstance();
        mJSONMessage = JSONMessage.getInstance();
        initView();
        initAdpater();
        initWelcomeContent();
    }

    /**
     * 语音识别参数设置
     *
     * @return
     */
    public void setRecognizerParam() {
        // 清空参数
        mIatDialog.setParameter(SpeechConstant.PARAMS, null);
        // 设置听写引擎
        mIatDialog.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置返回结果格式
        mIatDialog.setParameter(SpeechConstant.RESULT_TYPE, resultType);

        if (language.equals("zh_cn")) {
            String lag = "mandarin";
            // 设置语言
            Log.e(TAG, "language = " + language);
            mIatDialog.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
            // 设置语言区域
            mIatDialog.setParameter(SpeechConstant.ACCENT, lag);
        } else {
            mIatDialog.setParameter(SpeechConstant.LANGUAGE, language);
        }

        //此处用于设置dialog中不显示错误码信息
        //mIatDialog.setParameter("view_tips_plain","false");

        // 设置语音前端点:静音超时时间，即用户多长时间不说话则当做超时处理
        mIatDialog.setParameter(SpeechConstant.VAD_BOS, "4000");

        // 设置语音后端点:后端点静音检测时间，即用户停止说话多长时间内即认为不再输入， 自动停止录音
        mIatDialog.setParameter(SpeechConstant.VAD_EOS, "1000");

        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIatDialog.setParameter(SpeechConstant.ASR_PTT, "1");

        // 设置音频保存路径，保存音频格式支持pcm、wav.
        mIatDialog.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mIatDialog.setParameter(SpeechConstant.ASR_AUDIO_PATH, getExternalFilesDir("msc").getAbsolutePath() + "/iat.wav");
    }

    /**
     * 语音合成参数设置
     *
     * @return
     */
    private void setSpeechParam() {
        // 清空参数
        mTts.setParameter(SpeechConstant.PARAMS, null);
        // 根据合成引擎设置相应参数
        if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD);
            // 支持实时音频返回，仅在 synthesizeToUri 条件下支持
            mTts.setParameter(SpeechConstant.TTS_DATA_NOTIFY, "1");
            //	mTts.setParameter(SpeechConstant.TTS_BUFFER_TIME,"1");

            // 设置在线合成发音人
            mTts.setParameter(SpeechConstant.VOICE_NAME, voicer);
            //设置合成语速
            mTts.setParameter(SpeechConstant.SPEED, "50");
            //设置合成音调
            mTts.setParameter(SpeechConstant.PITCH, "50");
            //设置合成音量
            mTts.setParameter(SpeechConstant.VOLUME, "50");
        } else {
            mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_LOCAL);
            mTts.setParameter(SpeechConstant.VOICE_NAME, "");

        }

        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "false");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "pcm");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, getExternalFilesDir("msc").getAbsolutePath() + "/tts.pcm");
    }

    private void initWelcomeContent() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String question = mChatMessageData.addWelcomeMessage();
                        mListAdapter.notifyDataSetChanged();
                        mRvChatList.smoothScrollToPosition(mListAdapter.getItemCount());

                        speechStartSaying(question);
//
//
//                        Speech.getInstance().say(question, new TextToSpeechCallback() {
//                            @Override
//                            public void onStart() {
//
//                            }
//
//                            @Override
//                            public void onCompleted() {
//                                while (true) {
//                                    try {
//                                        Thread.sleep(1000);
//                                    } catch (InterruptedException e) {
//                                        throw new RuntimeException(e);
//                                    }
//                                    if (!Speech.getInstance().isSpeaking()) {
//                                        String owner = Constant.OWNER_BOT;
//                                        String question = "你可以让我讲一个冷笑话🤣";
//
//                                        ChatMessage chatMessage = new ChatMessage(owner, question);
//                                        mChatMessageList.add(chatMessage);
//                                        mListAdapter.notifyDataSetChanged();
//                                        mRvChatList.smoothScrollToPosition(mListAdapter.getItemCount());
//
//                                        speechStartSaying(question);

//                                        Speech.getInstance().say(question, new TextToSpeechCallback() {
//                                            @Override
//                                            public void onStart() {
//
//                                            }
//
//                                            @Override
//                                            public void onCompleted() {
//
//                                            }
//
//                                            @Override
//                                            public void onError() {
//
//                                            }
//                                        });

//                                        break;
//                                    }
//                                }
//                            }

//                            @Override
//                            public void onError() {
//
//                            }
//                        });
                    }
                });

            }
        }, 1000);
    }

    private void speechStartSaying(String question) {
        if (mTts.isSpeaking()) {
            mTts.stopSpeaking();
        }

        pcmFile = new File(getExternalCacheDir().getAbsolutePath(), "tts_pcmFile.pcm");
        pcmFile.delete();

        setSpeechParam();

        int code = mTts.startSpeaking(question, mTtsListener);
        if (code != ErrorCode.SUCCESS) {
            Toast.makeText(mContext, "语音合成失败,错误码: " + code, Toast.LENGTH_SHORT).show();
        }
    }

    private SynthesizerListener mTtsListener = new SynthesizerListener() {
        @Override
        public void onSpeakBegin() {
            mBtnStopSpeech.setVisibility(View.VISIBLE);
        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {

        }

        @Override
        public void onCompleted(SpeechError speechError) {
            mBtnStopSpeech.setVisibility(View.GONE);
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
//        Speech.getInstance().shutdown();
        if (mIatDialog != null) {
            // 退出时释放连接
            mIatDialog.cancel();
            mIatDialog.destroy();
        }
        if (null != mTts) {
            mTts.stopSpeaking();
            // 退出时释放连接
            mTts.destroy();
        }
    }

    private void initAdpater() {
        mListAdapter = new ChatListAdapter();
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        // 从底部加入聊天消息
        linearLayoutManager.setStackFromEnd(true);
        mRvChatList.setLayoutManager(linearLayoutManager);
        mRvChatList.setAdapter(mListAdapter);
    }

    private void initView() {
        mEtQuestion = findViewById(R.id.et_question);
        mBtnSend = findViewById(R.id.btn_send);
        mBtnSend.setOnClickListener((v) -> {
            sendQuestion();
        });

        mRvChatList = findViewById(R.id.rv_chatlist);

        mLlRecord = findViewById(R.id.ll_record);
        mSPVRecord = findViewById(R.id.spv_record);
        mBtnRecord = findViewById(R.id.btn_record);
        mBtnRecord.setOnClickListener((v) -> {
            record();
        });

        mIatDialog = new RecognizerDialog(this, mInitListener);

        mBtnStopSpeech = findViewById(R.id.btn_icon_stop_speech);
        mBtnStopSpeech.setOnClickListener(v -> {
            mTts.pauseSpeaking();
            mTts.stopSpeaking();
            mBtnStopSpeech.setVisibility(View.GONE);
        });
    }

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                Toast.makeText(mContext, "语音功能初始化失败 " + code, Toast.LENGTH_SHORT).show();
            }

        }
    };

    private void record() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        } else {

            // 科大讯飞
            startRecordByXF();

            // 谷歌自带语音识别
//            mLlRecord.setVisibility(View.VISIBLE);
//            startRecord();
        }
    }

    /**
     * 科大讯飞语音识别
     */
    private void startRecordByXF() {
        if (mIatDialog.isShowing()) {
            return;
        }
        setRecognizerParam();

        // 显示听写对话框
        mIatDialog.setListener(mRecognizerDialogListener);
        mIatDialog.show();
    }

    /**
     * 听写UI监听器
     */
    private RecognizerDialogListener mRecognizerDialogListener = new RecognizerDialogListener() {
        // 返回结果
        public void onResult(RecognizerResult results, boolean isLast) {
            printResult(results);
        }

        // 识别回调错误
        public void onError(SpeechError error) {
            Toast.makeText(mContext, "出错了:" + error.getMessage(), Toast.LENGTH_SHORT).show();
        }

    };

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());
        String sn = null;
        // 读取json结果中的sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mIatResults.put(sn, text);

        StringBuffer resultBuffer = new StringBuffer();
        for (String key : mIatResults.keySet()) {
            resultBuffer.append(mIatResults.get(key));
        }
        mEtQuestion.setText(resultBuffer.toString());
        mEtQuestion.setSelection(mEtQuestion.length());
//        mBtnSend.performClick();

    }

    /**
     * @deprecated 谷歌的语音组件，需要科技才能用
     */
    private void startRecord() {
        try {
            Speech.getInstance().startListening(mSPVRecord, new SpeechDelegate() {
                @Override
                public void onStartOfSpeech() {

                }

                @Override
                public void onSpeechRmsChanged(float value) {

                }

                @Override
                public void onSpeechPartialResults(List<String> results) {

                }

                @Override
                public void onSpeechResult(String result) {
                    mLlRecord.setVisibility(View.GONE);
                    Log.d(TAG, "onSpeechResult: === " + result);
                    if (!TextUtils.isEmpty(result)) {
                        mEtQuestion.setText(result.trim());
                        mBtnSend.performClick();
                    }
                }
            });
        } catch (SpeechRecognitionNotAvailable e) {
            throw new RuntimeException(e);
        } catch (GoogleVoiceTypingDisabledException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendQuestion() {
        String question = mEtQuestion.getText().toString().trim();
        if (TextUtils.isEmpty(question)) {
            Toast.makeText(this, "请先输入你的问题", Toast.LENGTH_SHORT).show();
            return;
        }

        mEtQuestion.setText("");
        // 发送文字到List里
        addChatMessage(Constant.OWNER_HUMAN, question);

        addChatMessage(Constant.OWNER_BOT_THINK, "正在思考中...");

        // 发送文字到API接口
        sendQuestionToAPI(question);
    }

    /**
     * #重要 存储消息，显示消息，播放消息内容，记录上下文
     *
     * @param owner
     * @param question
     */
    private void addChatMessage(String owner, String question) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChatMessageData.addChatMessage(owner, question);
                mListAdapter.notifyDataSetChanged();
                mRvChatList.smoothScrollToPosition(mChatMessageData.getSize());
                if (mChatMessageData.isBot(owner)) {
                    speechStartSaying(question);
                    mJSONMessage.addBotMessage(question); // 记录每次用户的上下文，这样AI就能实现多次对话

//                    Speech.getInstance().say(question, new TextToSpeechCallback() {
//                        @Override
//                        public void onStart() {
//
//                        }
//
//                        @Override
//                        public void onCompleted() {
//
//                        }
//
//                        @Override
//                        public void onError() {
//
//                        }
//                    });
                }
            }
        });
    }

    private void sendQuestionToAPI(String question) {

        JSONObject jsonBody = setRequestParam(question);
        // Request
        RequestBody requestBody = RequestBody.create(jsonBody.toString(), Constant.JSON);
        Request request = new Request.Builder().url(Constant.OPENAI_URL).header(Constant.AUTHORIZATION, Constant.AUTHORIZATION_API_KEY).post(requestBody).build();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS);
        // TODO 代理ip
//        builder.proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Constant.PROXY_HOST_NAME, Constant.PROXY_PORT)));
//        builder.proxyAuthenticator(new Authenticator() {
//            @Nullable
//            @Override
//            public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
//                String basic = Credentials.basic(Constant.PROXY_USER_NAME, Constant.PROXY_PASSWORD);
//                return response.request().newBuilder().header("Proxy-Authorization", basic).build();
//            }
//
//        });

        client = builder.build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息
                addChatMessage(Constant.OWNER_BOT, "出错了，错误信息是：" + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mChatMessageData.removeLastChatMessage(); // 删除"思考中"消息

                if (response.isSuccessful()) {
                    try {
                        JSONObject jsonObject = new JSONObject(response.body().string());
//                        Log.d(TAG, "onResponse: ===" + response.body().string());
                        JSONArray jsonArray = jsonObject.getJSONArray(Constant.RESPONSE_CHOICES);
                        JSONObject message = jsonArray.getJSONObject(0).getJSONObject(Constant.RESPONSE_CHOICES_MESSAGE);
                        String content = message.getString(Constant.MESSAGES_KEY_CONTENT);

                        addChatMessage(Constant.OWNER_BOT, content.trim());
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    addChatMessage(Constant.OWNER_BOT, "出错了，错误信息是：" + response.body().string());
                }

            }
        });
    }

    private JSONObject setRequestParam(String question) {
        // JSONObject
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put(Constant.MODEL, Constant.MODEL_GPT35);
            jsonBody.put(Constant.TEMPERATURE, Constant.TEMPERATURE_MIDDLE);

            mJSONMessage.removeNotNeededMessage(); // 一次传输Token的长度做限制
            mJSONMessage.addUserMessage(question); // 记录每次用户的上下文，这样AI就能实现多次对话

            jsonBody.put(Constant.MESSAGES, mJSONMessage.getArray());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return jsonBody;
    }
}