package com.quickblox.q_municate.ui.activities.chats;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.google.android.exoplayer.MediaCodecUtil;
import com.mopub.mobileads.MoPubErrorCode;
import com.mopub.mobileads.MoPubInterstitial;
import com.quickblox.content.model.QBFile;
import com.quickblox.core.exception.QBResponseException;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.ui.adapters.chats.PublicGroupDialogMessagesAdapter;
import com.quickblox.q_municate.ui.views.videochat.FleekPlayer;
import com.quickblox.q_municate_core.qb.helpers.QBGroupChatHelper;
import com.quickblox.q_municate_core.service.QBService;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_db.models.Dialog;
import com.quickblox.q_municate_db.models.User;
import com.quickblox.q_municate_db.utils.ErrorUtils;
import com.quickblox.users.model.QBUser;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersAdapter;
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.rtsp.RtspClient;
import net.majorkernelpanic.streaming.video.VideoQuality;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PublicGroupDialogActivity extends BaseDialogActivity implements
        Session.Callback,
        RtspClient.Callback,
        SurfaceHolder.Callback,
        MoPubInterstitial.InterstitialAdListener {

    private static final String TAG = "PublicGroupAct";
    private FleekPlayer fleekPlayer;
    private FleekPlayer fleekPlayer2;
    private Boolean premium;
    private Session mSession;
    private RtspClient mClient;
    private SurfaceView mSurfaceView;
    public static final int MY_PERMISSIONS_REQUEST_CAMERA = 42;
    public static final int MY_PERMISSIONS_REQUEST_RECORD_AUDIO = 43;
    private QBUser currentUser;
    private Boolean canH264 = false;
    private MoPubInterstitial mInterstitial;

    public static void start(Context context, Dialog dialog) {
        Intent intent = new Intent(context, PublicGroupDialogActivity.class);
        intent.putExtra(QBServiceConsts.EXTRA_DIALOG, dialog);
        context.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initFields();

        if (dialog == null) {
            finish();
        }

        setUpActionBarWithUpButton();

        if (isNetworkAvailable()) {
            deleteTempMessages();
        }
        ImageButton attachButton = (ImageButton) findViewById(R.id.attach_button);
        attachButton.setVisibility(View.GONE);
        initMessagesRecyclerView();
        //setupVideoChat();

    }

    @Override
    protected void initMessagesRecyclerView() {
        super.initMessagesRecyclerView();
        messagesAdapter = new PublicGroupDialogMessagesAdapter(this, combinationMessagesList, this, dialog);
        messagesRecyclerView.addItemDecoration(
                new StickyRecyclerHeadersDecoration((StickyRecyclerHeadersAdapter) messagesAdapter));
        messagesRecyclerView.setAdapter(messagesAdapter);

        scrollMessagesToBottom();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateData();

        if (isNetworkAvailable()) {

            startLoadDialogMessages();
            //startVideoChat();

        }

        checkMessageSendingPossibility();
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");

        if (mInterstitial!= null && mInterstitial.isReady()) {
            mInterstitial.show();
        } else {
            // Caching is likely already in progress if `isReady()` is false.
            // Avoid calling `load()` here and instead rely on the callbacks as suggested below.

        }
        stopVideoChat();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.group_dialog_menu, menu);
        return true;
    }

    @Override
    protected void onConnectServiceLocally(QBService service) {
        onConnectServiceLocally();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (GroupDialogDetailsActivity.UPDATE_DIALOG_REQUEST_CODE == requestCode && GroupDialogDetailsActivity.RESULT_LEAVE_GROUP == resultCode) {
            finish();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onFileLoaded(QBFile file) {
        try {
            ((QBGroupChatHelper) baseChatHelper).sendGroupMessageWithAttachImage(dialog.getRoomJid(), file);
        } catch (QBResponseException e) {
            ErrorUtils.showError(this, e);
        }
    }

    @Override
    protected Bundle generateBundleToInitDialog() {
        return null;
    }

    @Override
    protected void updateMessagesList() {
        int oldMessagesCount = messagesAdapter.getAllItems().size();

        this.combinationMessagesList = createCombinationMessagesList();
        messagesAdapter.setList(combinationMessagesList);

        checkForScrolling(oldMessagesCount);
    }

    @Override
    protected void checkMessageSendingPossibility() {
        checkMessageSendingPossibility(isNetworkAvailable());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_group_details:
                GroupDialogDetailsActivity.start(this, dialog.getDialogId());
                break;
            default:
                super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    protected void updateActionBar() {
        if (isNetworkAvailable() && dialog != null) {
            setActionBarTitle(dialog.getTitle());
            checkActionBarLogo(dialog.getPhoto(), R.drawable.placeholder_group);
        }
    }

    private void initFields() {
        chatHelperIdentifier = QBService.GROUP_CHAT_HELPER;
        dialog = (Dialog) getIntent().getExtras().getSerializable(QBServiceConsts.EXTRA_DIALOG);
        combinationMessagesList = createCombinationMessagesList();
        if (dialog != null)
        title = dialog.getTitle();
    }

    public void sendMessage(View view) {
        sendMessage(false);
    }

    private boolean hasCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED ) {
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST_CAMERA);

            return false;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
        } else {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    MY_PERMISSIONS_REQUEST_RECORD_AUDIO);
            return false;
        }
        return true;
    }

    private void setupVideoChat() {


        //getURL();
        premium = true;

        canH264 = false;
        try {
            int maxh264 = MediaCodecUtil.maxH264DecodableFrameSize();
            canH264 = true;
            //Log.e(TAG, "maxH264DecodableFrameSize: " + maxh264);

        } catch (MediaCodecUtil.DecoderQueryException e) {
            e.printStackTrace();
        }
        if (canH264) {
            LinearLayout video_layout = (LinearLayout) findViewById(R.id.videos);
            video_layout.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            mSurfaceView = new SurfaceView(this, null);
            video_layout.addView(mSurfaceView);
            mSurfaceView.setLayoutParams(lp);
            mSurfaceView.setKeepScreenOn(true);
            mSurfaceView.getHolder().addCallback(this);
            fleekPlayer = new FleekPlayer(this, 1, true);
            fleekPlayer.setLayoutParams(lp);
            video_layout.addView(fleekPlayer);
            fleekPlayer2 = new FleekPlayer(this, 2, premium);
            fleekPlayer2.setLayoutParams(lp);
            video_layout.addView(fleekPlayer2);
        }

    }
    public void startVideoChat() {
        if (hasCameraPermissions()) {
            Camera camera = null;
            int numCams = Camera.getNumberOfCameras();
            int selectedCamera = 0;
            if(numCams > 1) {
                selectedCamera = 1;
            }
            try {
                camera = Camera.open(selectedCamera);
            } catch (RuntimeException ex){

                return;
            }
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedVideoSizes();

            VideoQuality quality = VideoQuality.determineClosestSupportedResolution(parameters, new VideoQuality(480, 640, 15,700*1000));

            mSession = SessionBuilder.getInstance()
                    .setContext(getApplicationContext())
                    .setAudioEncoder(SessionBuilder.AUDIO_AAC)
                    .setAudioQuality(new AudioQuality(8000, 16000))
                    .setVideoEncoder(SessionBuilder.VIDEO_H264)
                    .setVideoQuality(quality)
                    .setSurfaceView(mSurfaceView)
                    .setPreviewOrientation(90)
                    .setCallback(this)
                    .build();
            mSession.getVideoTrack().setStreamingMethod(MediaStream.MODE_MEDIACODEC_API_2);


            // Configures the RTSP client
            mClient = new RtspClient();
            mClient.setSession(mSession);
            mClient.setCallback(this);
            if (!mClient.isStreaming()) {
                String ip,port,path;



                // We parse the URI written in the Editext
                Pattern uri = Pattern.compile("rtsp://(.+):(\\d*)/(.+)");
                Matcher m = uri.matcher("rtsp://ec2-54-208-221-222.compute-1.amazonaws.com:1935/live/"); m.find();
                ip = m.group(1);
                port = m.group(2);
                path = m.group(3) + currentUser.getId().toString() + ".stream";

                mClient.setCredentials("fleek", "lolhaha247");
                Log.e(TAG, ip + " " + Integer.parseInt(port));
                mClient.setServerAddress(ip, Integer.parseInt(port));
                mClient.setStreamPath("/" + path);
                mClient.startStream();

            }

        }
        if (canH264) {
            LinearLayout video_layout = (LinearLayout) findViewById(R.id.videos);
            video_layout.setVisibility(View.VISIBLE);

            ViewGroup.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1);

            // Create our Preview view and set it as the content of our activity.
            //preview = new CameraPreview(this);
            //video_layout.addView(preview);
            //preview.setLayoutParams(lp);
            //preview.setKeepScreenOn(true);
            //mSurfaceView = (SurfaceView) findViewById(R.id.previewSurface);

            Answers.getInstance().logCustom(new CustomEvent("H264")
                    .putCustomAttribute("CanH264", 1));
        } else {
            Answers.getInstance().logCustom(new CustomEvent("H264")
                    .putCustomAttribute("CanH264", 0));
        }
    }
    public void stopVideoChat() {
        fleekPlayer.releasePlayer();
        fleekPlayer2.releasePlayer();


    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        //Log.e(TAG, "bitrate: "+bitrate/1000+" kbps");

    }

    @Override
    public void onPreviewStarted() {
        Log.e(TAG, "onPreviewStarted");


    }

    @Override
    public void onSessionConfigured() {
        Log.e(TAG, "onSessionConfigured");

    }

    @Override
    public void onSessionStarted() {

        Log.e(TAG, "onSessionStarted");

    }

    @Override
    public void onSessionStopped() {
        Log.e(TAG, "onSessionStopped");
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
        //ProgressBar.setVisibility(View.GONE);
        switch (reason) {
            case Session.ERROR_CAMERA_ALREADY_IN_USE:
                Log.e(TAG, "ERROR_CAMERA_ALREADY_IN_USE");

                break;
            case Session.ERROR_CAMERA_HAS_NO_FLASH:
                Log.e(TAG, "ERROR_CAMERA_HAS_NO_FLASH");

                //mButtonFlash.setImageResource(R.drawable.ic_flash_on_holo_light);
                //mButtonFlash.setTag("off");
                break;
            case Session.ERROR_INVALID_SURFACE:
                Log.e(TAG, "ERROR_INVALID_SURFACE");

                break;
            case Session.ERROR_STORAGE_NOT_READY:
                break;
            case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
                VideoQuality quality = mSession.getVideoTrack().getVideoQuality();
                Log.e(TAG, "The following settings are not supported on this phone: "+
                        quality.toString()+" "+
                        "("+e.getMessage()+")");
                e.printStackTrace();
                return;
            case Session.ERROR_OTHER:
                Log.e(TAG, "ERROR_OTHER");

                break;
        }

        if (e != null) {
            Log.e(TAG, "onSessionError: "  +  e.getMessage());
            Log.e(e.getClass().getName(), "onSessionError: exception", e);

            e.printStackTrace();
        }
    }

    @Override
    public void onRtspUpdate(int message, Exception e) {
        switch (message) {
            case RtspClient.ERROR_CONNECTION_FAILED:
            case RtspClient.ERROR_WRONG_CREDENTIALS:
                Log.e(TAG, "onRtspUpdate: " + e.getMessage());

                e.printStackTrace();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CAMERA: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    startVideoChat();
                } else {
                }
            }
            case MY_PERMISSIONS_REQUEST_RECORD_AUDIO: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                    startVideoChat();
                } else {
                }
            }
        }
    }
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mSession != null) {
            mSession.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mClient != null) {
            mClient.stopStream();
        }
    }



    // InterstitialAdListener methods
    @Override
    public void onInterstitialLoaded(MoPubInterstitial interstitial) {
        // The interstitial has been cached and is ready to be shown.
        Log.e(TAG, "onInterstitialLoaded");

    }

    @Override
    public void onInterstitialFailed(MoPubInterstitial interstitial, MoPubErrorCode errorCode) {
        // The interstitial has failed to load. Inspect errorCode for additional information.
        // This is an excellent place to load more ads.
        Log.e(TAG, "onInterstitialFailed");

    }

    @Override
    public void onInterstitialShown(MoPubInterstitial interstitial) {
        // The interstitial has been shown. Pause / save state accordingly.
    }

    @Override
    public void onInterstitialClicked(MoPubInterstitial interstitial) {}

    @Override
    public void onInterstitialDismissed(MoPubInterstitial interstitial) {
        // The interstitial has being dismissed. Resume / load state accordingly.
        // This is an excellent place to load more ads.
    }
}