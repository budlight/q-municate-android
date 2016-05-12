package com.quickblox.q_municate.ui.views.videochat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecUtil;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.util.Util;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.ui.activities.purchase.InAppPurchaseActivity;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


/**
 SGG
 * Created by ben on 3/6/16.

 */
public class FleekPlayer extends SurfaceView implements SurfaceHolder.Callback, DemoPlayer.Listener, View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "FleekPlayer";
    private DemoPlayer player;
    private final SurfaceView mSurfaceView;
    private final SurfaceHolder mHolder;
    private final Boolean premiumEnabled;

    private long playerPosition;
    private boolean playerNeedsPrepare;
    private Handler messageHandler;
    private Uri contentUri;
    private final Integer videoPos;
    private final Context ctx;
    private final String userAgent;
    private final Bitmap premiumImage;


    public FleekPlayer(Context context, int vp, Boolean premium) {
        super(context);
        ctx = context;
        videoPos = vp;
        premiumEnabled = premium;

        userAgent = Util.getUserAgent(ctx, "ExoPlayerDemo");
        mSurfaceView = this;

        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        this.setOnClickListener(this);
        this.setOnLongClickListener(this);

        playerPosition = 0;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        premiumImage = BitmapFactory.decodeResource(getResources(),
                R.drawable.premium_unlock_video, options);
        Log.e(TAG, "Fleek Player init");

    }



    private void preparePlayer(boolean playWhenReady) {
        if (player == null) {
            player = new DemoPlayer(new HlsRendererBuilder(ctx, userAgent, contentUri.toString()));
            player.addListener(this);

            player.seekTo(playerPosition);
            playerNeedsPrepare = true;

        }
        if (playerNeedsPrepare) {
            player.prepare();
            playerNeedsPrepare = false;
        }
        mHolder.setSizeFromLayout();
        player.setSurface(mSurfaceView.getHolder().getSurface());
        player.setPlayWhenReady(true);

    }

    public void releasePlayer() {
        if (player != null) {
            player.stop();
            playerPosition = player.getCurrentPosition();
            player.release();
            player = null;
        }
    }


    private Uri getVideoUri()  {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {

                URL url = new URL("http://panel.socialgamegroup.com/fleek/video_test/?pos=" + videoPos.toString());


                HttpURLConnection ucon = (HttpURLConnection) url.openConnection();
                ucon.setInstanceFollowRedirects(false);
                ucon.connect();
                String periscopeURL = ucon.getHeaderField("Location");
                if (periscopeURL != null) {
                    Uri resultUri = Uri.parse(periscopeURL);
                    return resultUri;
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void getVideo(){
        Log.e(TAG, "getVideo");
        messageHandler = new Handler(new Handler.Callback() {

            @Override
            public boolean handleMessage(Message msg) {
                if(msg.arg1==1)
                {
                    releasePlayer();
                    preparePlayer(true);
                }
                return false;
            }
        });

        Thread thread = new Thread() {
            @Override
            public void run() {
                contentUri = getVideoUri();
                if (contentUri != null) {
                    Message msg = new Message();
                    msg.arg1 = 1;
                    messageHandler.sendMessage(msg);
                }
            }
        };
        thread.start();

    }
    //Surface Methods



    // SurfaceHolder.Callback implementation
    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG, "surfaceCreated");

        if (premiumEnabled) {
            getVideo();
        } else{
            tryDrawPremium(holder);

        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.e(TAG, "surface changed");
        if (premiumEnabled) {
            getVideo();
        } else {
            tryDrawPremium(holder);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(TAG, "surface destroyed");
        if (player != null) {
            player.blockingClearSurface();
            player.release();

        }
        player = null;
    }

    private void tryDrawPremium(SurfaceHolder holder) {
        Log.i(TAG, "Trying to draw...");

        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            Log.e(TAG, "Cannot draw onto the canvas as it's null");
        } else {
            drawPremium(canvas);
            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawPremium(final Canvas canvas) {
        Log.i(TAG, "Drawing...");

        int width = canvas.getWidth();
        int height = canvas.getHeight();


        Rect dest = new Rect(0, 0, width-1, height-1);
        canvas.drawColor(Color.WHITE);
        Paint myPaint = new Paint();
        myPaint.setAntiAlias(false);
        myPaint.setFilterBitmap(true);
        canvas.drawBitmap(premiumImage, null, dest, myPaint);
    }
    // DemoPlayer.Listener implementation

    @Override
    public void onStateChanged(boolean playWhenReady, int playbackState) {

        String text = "playWhenReady=" + playWhenReady + ", playbackState=";
        switch(playbackState) {
            case ExoPlayer.STATE_BUFFERING:
                text += "buffering";
                break;
            case ExoPlayer.STATE_ENDED:
                text += "ended";
                getVideo();
                break;
            case ExoPlayer.STATE_IDLE:
                text += "idle";
                getVideo();
                break;
            case ExoPlayer.STATE_PREPARING:
                text += "preparing";
                break;
            case ExoPlayer.STATE_READY:
                text += "ready";
                break;
            default:
                text += "unknown";
                break;
        }
        Log.i(TAG, text);
    }

    @Override
    public void onError(Exception e) {
        String errorString = null;

        if (e instanceof UnsupportedDrmException) {
            // Special case DRM failures.
            UnsupportedDrmException unsupportedDrmException = (UnsupportedDrmException) e;
            errorString = ctx.getString(Util.SDK_INT < 18 ? R.string.error_drm_not_supported
                    : unsupportedDrmException.reason == UnsupportedDrmException.REASON_UNSUPPORTED_SCHEME
                    ? R.string.error_drm_unsupported_scheme : R.string.error_drm_unknown);
        } else if (e instanceof ExoPlaybackException
                && e.getCause() instanceof MediaCodecTrackRenderer.DecoderInitializationException) {
            // Special case for decoder initialization failures.
            MediaCodecTrackRenderer.DecoderInitializationException decoderInitializationException =
                    (MediaCodecTrackRenderer.DecoderInitializationException) e.getCause();
            if (decoderInitializationException.decoderName == null) {
                if (decoderInitializationException.getCause() instanceof MediaCodecUtil.DecoderQueryException) {
                    errorString = ctx.getString(R.string.error_querying_decoders);
                } else if (decoderInitializationException.secureDecoderRequired) {
                    errorString = ctx.getString(R.string.error_no_secure_decoder,
                            decoderInitializationException.mimeType);
                } else {
                    errorString = ctx.getString(R.string.error_no_decoder,
                            decoderInitializationException.mimeType);
                }
            } else {
                errorString = ctx.getString(R.string.error_instantiating_decoder,
                        decoderInitializationException.decoderName);
            }
        }
        if (errorString != null) {
            Toast.makeText(ctx.getApplicationContext(), errorString, Toast.LENGTH_LONG).show();
        }
        playerNeedsPrepare = true;
    }

    @Override
    public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees,
                                   float pixelWidthAspectRatio) {

    }

    public void onClick(View v) {
        // do something when the view is clicked
        if(premiumEnabled) {
            getVideo();
        } else {
            Answers.getInstance().logCustom(new CustomEvent("PremiumStartMoreVideo"));
            Intent intent = new Intent(ctx, InAppPurchaseActivity.class);
            ctx.startActivity(intent);
        }
    }

    public boolean onLongClick(View v) {
        // do something when the view is clicked
        if (player != null) {
            player.setMuted(true);
        }
        return true;
    }
}
