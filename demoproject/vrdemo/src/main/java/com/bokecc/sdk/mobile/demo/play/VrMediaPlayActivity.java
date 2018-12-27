package com.bokecc.sdk.mobile.demo.play;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.asha.vrlib.MDVRLibrary;
import com.bokecc.sdk.mobile.demo.play.Subtitle.OnSubtitleInitedListener;
import com.bokecc.sdk.mobile.demo.play.controller.VrConfig;
import com.bokecc.sdk.mobile.demo.play.controller.VrController;
import com.bokecc.sdk.mobile.demo.util.ConfigUtil;
import com.bokecc.sdk.mobile.demo.util.DataSet;
import com.bokecc.sdk.mobile.demo.util.ParamsUtil;
import com.bokecc.sdk.mobile.demo.view.CircleProgressBar;
import com.bokecc.sdk.mobile.demo.view.PlayChangeVideoPopupWindow;
import com.bokecc.sdk.mobile.demo.view.PlayTopPopupWindow;
import com.bokecc.sdk.mobile.demo.view.PopMenu;
import com.bokecc.sdk.mobile.demo.view.PopMenu.OnItemClickListener;
import com.bokecc.sdk.mobile.demo.view.VerticalSeekBar;
import com.bokecc.sdk.mobile.play.DWMediaPlayer;
import com.bokecc.sdk.mobile.vrdemo.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 视频播放界面
 * 
 * @author CC视频
 * 
 */
public class VrMediaPlayActivity extends Activity implements
		DWMediaPlayer.OnBufferingUpdateListener,
		DWMediaPlayer.OnInfoListener,
		DWMediaPlayer.OnPreparedListener, DWMediaPlayer.OnErrorListener,
		DWMediaPlayer.OnVideoSizeChangedListener,
		DWMediaPlayer.OnCompletionListener {
	
	private boolean networkConnected = true;
	private DWMediaPlayer player;
	private Subtitle subtitle;
	private GLSurfaceView surfaceView;
	private Surface mSurface;
	private ProgressBar leftBufferProgressBar, rightBufferProgressBar;
	private SeekBar skbProgress;
	private ImageView backPlayList;
	private TextView videoIdText, playCurrentPosition, videoDuration;
	private TextView tvDefinition;
	private PopMenu definitionMenu;
	private LinearLayout playerTopLayout, volumeLayout;
	private LinearLayout playerBottomLayout;
	private AudioManager audioManager;
	private VerticalSeekBar volumeSeekBar;
	private int currentVolume;
	private int maxVolume;
	private TextView subtitleText;

	private boolean isLocalPlay;
	private boolean isPrepared;
	private Map<String, Integer> definitionMap;

	private Handler playerHandler;
	private Timer timer = new Timer();
	private TimerTask timerTask, networkInfoTimerTask;

	private int currentScreenSizeFlag = 1;
	private int currrentSubtitleSwitchFlag = 0;
	private int currentDefinitionIndex = 0;
	// 默认设置为普清
	private int defaultDefinition = DWMediaPlayer.NORMAL_DEFINITION;
	
	private boolean firstInitDefinition = true;
	private String path;

	private Boolean isPlaying;
	// 当player未准备好，并且当前activity经过onPause()生命周期时，此值为true
	private boolean isFreeze = false;

	int currentPosition;
	private Dialog dialog;

	private String[] definitionArray;
	private final String[] screenSizeArray = new String[] { "满屏", "100%", "75%", "50%" };
	private final String[] subtitleSwitchArray = new String[] { "开启", "关闭" };
	private final String subtitleExampleURL = "http://dev.bokecc.com/static/font/example.utf8.srt";

	private int lastPlayPosition, currentPlayPosition;
	private String videoId,verificationCode = "2";
	private RelativeLayout rlBelow, rlPlay;
	private WindowManager wm;
	private ImageView ivFullscreen;
	// 隐藏界面的线程
	private Runnable hidePlayRunnable = new Runnable() {
		@Override
		public void run() {
			setLayoutVisibility(View.GONE, false);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 隐藏标题
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// 屏幕常亮
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		setContentView(R.layout.vr_media_play);
		
		wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
		cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		
		initView();

		initPlayHander();

		initPlayInfo();

		initVrLib();

		if (!isLocalPlay) {
			initNetworkTimerTask();
		}
		
		preparePlayer();
	}
	
	ImageView lockView;
	ImageView ivCenterPlay;
	ImageView ivTopMenu;
	ImageView ivSwitchsd, ivSwitchmt;
	TextView tvChangeVideo, tvLeftTag, tvRightTag;
	ImageView ivBackVideo, ivNextVideo, ivPlay;
	CircleProgressBar leftCircleView, rightCircleView;
	private void initView() {
		leftCircleView = (CircleProgressBar) findViewById(R.id.left_circle_view);
		leftCircleView.setMax(VrConfig.EYE_CIRCLE_BAR_MAX_TIME);
		
		rightCircleView = (CircleProgressBar) findViewById(R.id.right_circle_view);
		rightCircleView.setMax(VrConfig.EYE_CIRCLE_BAR_MAX_TIME);
		
		tvLeftTag = (TextView) findViewById(R.id.tv_left_tag);
		tvRightTag = (TextView) findViewById(R.id.tv_right_tag);
		rlBelow = (RelativeLayout) findViewById(R.id.rl_below_info);
		rlPlay = (RelativeLayout) findViewById(R.id.rl_play);
		rlPlay.setClickable(true);
				
		ivTopMenu = (ImageView) findViewById(R.id.iv_top_menu);
		ivTopMenu.setOnClickListener(onClickListener);
		
		leftBufferProgressBar = (ProgressBar) findViewById(R.id.leftBufferProgressBar);
		rightBufferProgressBar = (ProgressBar) findViewById(R.id.rightBufferProgressBar);
		
		ivCenterPlay = (ImageView) findViewById(R.id.iv_center_play);
		ivCenterPlay.setOnClickListener(onClickListener);

		backPlayList = (ImageView) findViewById(R.id.backPlayList);
		videoIdText = (TextView) findViewById(R.id.videoIdText);

		surfaceView = (GLSurfaceView) findViewById(R.id.glSurfaceView);
		
		playCurrentPosition = (TextView) findViewById(R.id.playDuration);
		videoDuration = (TextView) findViewById(R.id.videoDuration);
		playCurrentPosition.setText(ParamsUtil.millsecondsToStr(0));
		videoDuration.setText(ParamsUtil.millsecondsToStr(0));
		
		ivBackVideo = (ImageView) findViewById(R.id.iv_video_back);
		ivNextVideo = (ImageView) findViewById(R.id.iv_video_next);
		ivPlay = (ImageView) findViewById(R.id.iv_play);
		
		ivBackVideo.setOnClickListener(onClickListener);
		ivNextVideo.setOnClickListener(onClickListener);
		ivPlay.setOnClickListener(onClickListener);
		
		tvChangeVideo = (TextView) findViewById(R.id.tv_change_video);
		tvChangeVideo.setOnClickListener(onClickListener);
		
		tvDefinition = (TextView) findViewById(R.id.tv_definition);

		audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

		volumeSeekBar = (VerticalSeekBar) findViewById(R.id.volumeSeekBar);
		volumeSeekBar.setThumbOffset(2);
		volumeSeekBar.setMax(maxVolume);
		volumeSeekBar.setProgress(currentVolume);
		volumeSeekBar.setOnSeekBarChangeListener(seekBarChangeListener);

		skbProgress = (SeekBar) findViewById(R.id.skbProgress);
		skbProgress.setOnSeekBarChangeListener(onSeekBarChangeListener);

		playerTopLayout = (LinearLayout) findViewById(R.id.playerTopLayout);
		volumeLayout = (LinearLayout) findViewById(R.id.volumeLayout);
		playerBottomLayout = (LinearLayout) findViewById(R.id.playerBottomLayout);
		
		ivFullscreen = (ImageView) findViewById(R.id.iv_fullscreen);
		
		ivFullscreen.setOnClickListener(onClickListener);
		backPlayList.setOnClickListener(onClickListener);
		tvDefinition.setOnClickListener(onClickListener);

		subtitleText = (TextView) findViewById(R.id.subtitleText);
		
		lockView = (ImageView) findViewById(R.id.iv_lock);
		lockView.setSelected(false);
		lockView.setOnClickListener(onClickListener);
		
		ivSwitchmt = (ImageView) findViewById(R.id.iv_switch_mt);
		ivSwitchmt.setOnClickListener(onClickListener);
		ivSwitchmt.setSelected(true);
		
		ivSwitchsd = (ImageView) findViewById(R.id.iv_switch_sd);
		ivSwitchsd.setOnClickListener(onClickListener);
//		ivSwitchsd.setSelected(true);
	}
	
	
	//--------------------------------------vr controller---------------------------------------------
	VrController vrController;
	// 初始化vrlib库
	private void initVrLib() {
		vrController = new VrController(this, player, R.id.glSurfaceView);

		vrController.setVrControllerListener(new VrController.VrControllerListener() {
			@Override
			public void onPlayerStatusChanged(int status) {
				changePlayStatus();
			}

			@Override
			public void onPlayerSoundChanged(int progress) {
				changeSound(progress);
			}

			@Override
			public void onDefinitionChanged(final int position) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        definitionMenu.setCheckedPosition(position);
                        try {
                            changeDefinition(position);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
			}

			@Override
			public void onViewClick() {

				if (!isPrepared) {
					return;
				}

				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (!isDisplay) {
							setLayoutVisibility(View.VISIBLE, true);
						} else {
							setLayoutVisibility(View.GONE, false);
						}
					}
				});
			}

			@Override
			public void onEyeHitProgressUpdate(float progress) {
				updateEyeHitProgress(progress);
			}

			@Override
			public void onBackPressed() {
				VrMediaPlayActivity.this.onBackPressed();
			}
		});
	}
	
	// 更新眼控圆形进度条
	public void updateEyeHitProgress(final float progress) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				leftCircleView.setProgress(progress);
				rightCircleView.setProgress(progress);
			}
		});
	}

	//--------------------------------------网络控制部分---------------------------------------------
	private void initNetworkTimerTask() {
		networkInfoTimerTask = new TimerTask() {
			@Override
			public void run() {
				parseNetworkInfo();
			}
		};
		timer.schedule(networkInfoTimerTask, 0, 600);
	}
	
	enum NetworkStatus {
		WIFI,
		MOBILEWEB,
		NETLESS,
	}
	
	private NetworkStatus currentNetworkStatus;
	ConnectivityManager cm;
	private void parseNetworkInfo() {
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo != null && networkInfo.isAvailable()) {
        	if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
        		if (currentNetworkStatus != null && currentNetworkStatus == NetworkStatus.WIFI) {
        			return;
        		} else {
        			currentNetworkStatus = NetworkStatus.WIFI;
        			showWifiToast();
        		}
        		
        	} else {
        		if (currentNetworkStatus != null && currentNetworkStatus == NetworkStatus.MOBILEWEB) {
        			return;
        		} else {
        			currentNetworkStatus = NetworkStatus.MOBILEWEB;
        			showMobileDialog();
        		}
        	}
        	
        	startPlayerTimerTask();
        	networkConnected = true;
        } else {
        	if (currentNetworkStatus != null && currentNetworkStatus == NetworkStatus.NETLESS) {
    			return;
    		} else {
    			currentNetworkStatus = NetworkStatus.NETLESS;
    			showNetlessToast();
    		}
        	
        	if (timerTask != null) {
        		timerTask.cancel();
        	}
        	
        	networkConnected = false;
        }
	}
	
	private void showWifiToast() {
		showUiThreadToast("已切换至wifi");
	}
	
	private void showNetlessToast() {
		showUiThreadToast("当前无网络信号，无法播放");
	}
	
	private void showUiThreadToast(final String text) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
			}
		});
	}
	
	private void showMobileDialog() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(VrMediaPlayActivity.this);
				AlertDialog dialog = builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						finish();
					}
				}).setPositiveButton("继续", new DialogInterface.OnClickListener() {
					
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				}).setMessage("当前为移动网络，是否继续播放？").create();
				
				dialog.show();
			}
		});
		
	}

	private void startPlayerTimerTask() {
		if (timerTask != null) {
			timerTask.cancel();
		}
		timerTask = new TimerTask() {
			@Override
			public void run() {

				if (!isPrepared) {
					return;
				}

				playerHandler.sendEmptyMessage(0);
			}
		};
		timer.schedule(timerTask, 0, 1000);
	}

	private void initPlayHander() {
		playerHandler = new Handler() {
			public void handleMessage(Message msg) {

				if (player == null) {
					return;
				}

				// 刷新字幕
				subtitleText.setText(subtitle.getSubtitleByTime(player
						.getCurrentPosition()));

				currentPlayPosition = (int)player.getCurrentPosition();
				updateCurrentPosition(currentPlayPosition);
			};
		};
	}

	// 更新当前播放进度
	private void updateCurrentPosition(final int currentPlayPosition) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				updatePlayCurrentPosition(currentPlayPosition);
				updateVrCurrentPosition(currentPlayPosition);
			}
		});
	}

	private void updatePlayCurrentPosition(int currentPlayPosition) {
		// 更新播放器上的播放进度
		int duration = (int)player.getDuration();

		if (duration > 0) {
			long pos = skbProgress.getMax() * currentPlayPosition / duration;
			playCurrentPosition.setText(ParamsUtil.millsecondsToStr(currentPlayPosition));
			skbProgress.setProgress((int) pos);
		}
	}

	// 更新vr的播放进度
	private void updateVrCurrentPosition(int currentPlayPosition) {
		vrController.updateCurrentTime(currentPlayPosition);
	}

	private void initPlayInfo() {
		// 通过定时器和Handler来更新进度
		isPrepared = false;
		player = new DWMediaPlayer();
		player.reset();
		player.setHttpsPlay(false);
		player.setOnErrorListener(this);
		player.setOnCompletionListener(this);
		player.setOnVideoSizeChangedListener(this);
		player.setOnInfoListener(this);
		
		videoId = getIntent().getStringExtra("videoId");
		videoIdText.setText(videoId);
		isLocalPlay = getIntent().getBooleanExtra("isLocalPlay", false);
		try {

			if (!isLocalPlay) {// 播放线上视频
				player.setVideoPlayInfo(videoId, ConfigUtil.USERID, ConfigUtil.API_KEY,verificationCode, this);
				// 设置默认清晰度
				player.setDefaultDefinition(defaultDefinition);

			} else {// 播放本地已下载视频
				
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

				if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
					path = Environment.getExternalStorageDirectory() + "/CCDownload/c.mp4";
					if (!new File(path).exists()) {
						return;
					}
				}
			}

		} catch (IllegalArgumentException e) {
			Log.e("player error", e.getMessage());
		} catch (SecurityException e) {
			Log.e("player error", e.getMessage());
		} catch (IllegalStateException e) {
			Log.e("player error", e + "");
		}

		// 设置视频字幕
		subtitle = new Subtitle(new OnSubtitleInitedListener() {

			@Override
			public void onInited(Subtitle subtitle) {
				// 初始化字幕控制菜单
				// TODO 看看是否有问题
			}
		});
		subtitle.initSubtitleResource(subtitleExampleURL);
	}

	private LayoutParams getScreenSizeParams(int position) {
		currentScreenSizeFlag = position;
		int width = 600;
		int height = 400;
		if (isPortrait()) {
			width = wm.getDefaultDisplay().getWidth();
			height = wm.getDefaultDisplay().getHeight() * 2 / 5; //TODO 根据当前布局更改
		} else {
			width = wm.getDefaultDisplay().getWidth();
			height = wm.getDefaultDisplay().getHeight();
		}
		
		String screenSizeStr = screenSizeArray[position];
		if (screenSizeStr.indexOf("%") > 0) {// 按比例缩放
			int vWidth = player.getVideoWidth();
			if (vWidth == 0) {
				vWidth = 600;
			}

			int vHeight = player.getVideoHeight();
			if (vHeight == 0) {
				vHeight = 400;
			}

			if (vWidth > width || vHeight > height) {
				float wRatio = (float) vWidth / (float) width;
				float hRatio = (float) vHeight / (float) height;
				float ratio = Math.max(wRatio, hRatio);

				width = (int) Math.ceil((float) vWidth / ratio);
				height = (int) Math.ceil((float) vHeight / ratio);
			} else {
				float wRatio = (float) width / (float) vWidth;
				float hRatio = (float) height / (float) vHeight;
				float ratio = Math.min(wRatio, hRatio);

				width = (int) Math.ceil((float) vWidth * ratio);
				height = (int) Math.ceil((float) vHeight * ratio);
			}

			
			int screenSize = ParamsUtil.getInt(screenSizeStr.substring(0, screenSizeStr.indexOf("%")));
			width = (width * screenSize) / 100;
			height = (height * screenSize) / 100;
		} 

		LayoutParams params = new LayoutParams(width, height);
		return params;
	}

	public void preparePlayer() {
		try {
			player.setAudioStreamType(AudioManager.STREAM_MUSIC);
			player.setOnBufferingUpdateListener(this);
			player.setOnPreparedListener(this);
			player.setScreenOnWhilePlaying(true);
			
			if (isLocalPlay) {
				player.setDataSource(path);
			}
			
			player.prepareAsync();
		} catch (Exception e) {
			Log.e("videoPlayer", "error", e);
		}
	}
	
	private void initTimerTask() {
		if (timerTask != null) {
			timerTask.cancel();
		}
		
		timerTask = new TimerTask() {
			@Override
			public void run() {

				if (!isPrepared) {
					return;
				}

				playerHandler.sendEmptyMessage(0);
			}
		};
		timer.schedule(timerTask, 0, 1000);
	}

	private void changeDefinition(int position) throws IOException {
        currentDefinitionIndex = position;
        defaultDefinition = definitionMap.get(definitionArray[position]);

        if (isPrepared) {
            currentPosition = (int)player.getCurrentPosition();
            if (player.isPlaying()) {
                isPlaying = true;
            } else {
                isPlaying = false;
            }
        }

        isPrepared = false;

        setLayoutVisibility(View.GONE, false);
        leftBufferProgressBar.setVisibility(View.VISIBLE);
        if (ivSwitchsd.isSelected()) {
			rightBufferProgressBar.setVisibility(View.VISIBLE);
		}
        
        player.reset();
        player.setDefinition(getApplicationContext(), defaultDefinition);
    }

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		skbProgress.setSecondaryProgress(percent);
	}

	OnClickListener onClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			resetHideDelayed();
			
			switch (v.getId()) {
//			case R.id.btnPlay:
//				if (!isPrepared) {
//					return;
//				}
//				changePlayStatus();
//				break;

			case R.id.backPlayList:
				if (isPortrait() || isLocalPlay) {
					finish();
				} else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				}
				break;
			case R.id.iv_fullscreen:
				if (isPortrait()) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				} else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				}
				break;
			case R.id.tv_definition:
				definitionMenu.showAsDropDown(v);
				break;
			case R.id.iv_lock:
				if (lockView.isSelected()) {
					lockView.setSelected(false);
					setLayoutVisibility(View.VISIBLE, true);
					showUiThreadToast("已解开屏幕");
				} else {
					lockView.setSelected(true);
					setLandScapeRequestOrientation();
					setLayoutVisibility(View.GONE, true);
					lockView.setVisibility(View.VISIBLE);
					showUiThreadToast("已锁定屏幕");
				}
				break;
			case R.id.iv_center_play:
			case R.id.iv_play:
				changePlayStatus();
				break;
			case R.id.iv_top_menu:
				setLayoutVisibility(View.GONE, false);
				showTopPopupWindow();
				break;
			case R.id.tv_change_video:
				setLayoutVisibility(View.GONE, false);
				showChangeVideoWindow();
				break;
			case R.id.iv_video_back:
				changeToBackVideo();
				break;
			case R.id.iv_video_next:
				changeToNextVideo(false);
				break;
			case R.id.iv_switch_mt:
				changeMotionTouch();
				break;
			case R.id.iv_switch_sd:
				changeNormalGlass();
				break;
			}
		}
	};
	
	// 更新播放状态
	private void changePlayStatus() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				changePlayerPlayStatus(!player.isPlaying());
				vrController.changeStartPausePlugins(player.isPlaying());
			}
		});
	}
	
	PlayTopPopupWindow playTopPopupWindow;
	private void showTopPopupWindow() {
		if (playTopPopupWindow == null) {
			initPlayTopPopupWindow();
		}
		playTopPopupWindow.showAsDropDown(rlPlay);
	}
	
	private void initPlayTopPopupWindow() {
		playTopPopupWindow = new PlayTopPopupWindow(this, surfaceView.getHeight());
		playTopPopupWindow.setSubtitleCheckLister(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				switch (checkedId) {
				case R.id.rb_subtitle_open:// 开启字幕
					currentScreenSizeFlag = 0;
					subtitleText.setVisibility(View.VISIBLE);
					break;
				case R.id.rb_subtitle_close:// 关闭字幕
					currentScreenSizeFlag = 1;
					subtitleText.setVisibility(View.GONE);
					break;
				}
			}
		});
		
		playTopPopupWindow.setScreenSizeCheckLister(new OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				
				int position = 0;
				switch (checkedId) {
					case R.id.rb_screensize_full:
						position = 0;
						break;
					case R.id.rb_screensize_100:
						position = 1;
						break;
					case R.id.rb_screensize_75:
						position = 2;
						break;
					case R.id.rb_screensize_50:
						position = 3;
						break;
				}
				
				Toast.makeText(getApplicationContext(),screenSizeArray[position], Toast.LENGTH_SHORT).show();
				LayoutParams params = getScreenSizeParams(position);
				params.addRule(RelativeLayout.CENTER_IN_PARENT);
				surfaceView.setLayoutParams(params);
			}
		});
		
	}
	
	PlayChangeVideoPopupWindow playChangeVideoPopupWindow;
	private void showChangeVideoWindow() {
		if (playChangeVideoPopupWindow == null) {
			initPlayChangeVideoPopupWindow();
		}
		playChangeVideoPopupWindow.setSelectedPosition(getCurrentVideoIndex()).showAsDropDown(rlPlay);
	}
	
	private void initPlayChangeVideoPopupWindow() {
		playChangeVideoPopupWindow = new PlayChangeVideoPopupWindow(this, surfaceView.getHeight());
		
		playChangeVideoPopupWindow.setItem(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				changeVideo(position, false);
				playChangeVideoPopupWindow.setSelectedPosition(position);
				playChangeVideoPopupWindow.refreshView();
			}
		});
	}
	
	private void changeToBackVideo() {
		int currentPosition = getCurrentVideoIndex();
		int length = PlayFragment.playVideoIds.length;
		int position = 0;
		if (currentPosition == 0) {
			position = length - 1;
		} else {
			position = --currentPosition;
		}
		changeVideo(position, false);
	}
	
	private void changeToNextVideo(boolean isCompleted) {
		int currentIndex = getCurrentVideoIndex();
		int length = PlayFragment.playVideoIds.length;
		int position = 0;
		if (currentIndex == length - 1) {
			position = 0;
		} else {
			position = ++currentIndex;
		}
		changeVideo(position, isCompleted);
	}
	
	//更新交互模式，手控还是重力感应
	private void changeMotionTouch() {
		
		if (!isVr) {
			Toast.makeText(this, "非vr视频，无法点击", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (ivSwitchmt.isSelected()) {
			ivSwitchmt.setSelected(false);
			vrController.switchInteractiveMode(MDVRLibrary.INTERACTIVE_MODE_TOUCH);
			ivSwitchmt.setImageDrawable(getResources().getDrawable(R.drawable.gyroscope_btn_off));
		} else {
			ivSwitchmt.setSelected(true);
			vrController.switchInteractiveMode(MDVRLibrary.INTERACTIVE_MODE_MOTION);
			ivSwitchmt.setImageDrawable(getResources().getDrawable(R.drawable.gyroscope_btn_on));
		}
	}
	
	// 更新展现模式，普通还是眼镜
	private void changeNormalGlass() {
		if (!isVr) {
			Toast.makeText(this, "非vr视频，无法点击", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (ivSwitchsd.isSelected()) {
			ivSwitchsd.setSelected(false);
			vrController.switchDisplayMode(MDVRLibrary.DISPLAY_MODE_NORMAL);
			ivSwitchsd.setImageDrawable(getResources().getDrawable(R.drawable.screen_btn_off));
			tvRightTag.setVisibility(View.GONE);
			rightCircleView.setVisibility(View.GONE);
			rightBufferProgressBar.setVisibility(View.GONE);
			
		} else {
			ivSwitchsd.setSelected(true);
			vrController.switchDisplayMode(MDVRLibrary.DISPLAY_MODE_GLASS);
			tvRightTag.setVisibility(View.VISIBLE);
			rightCircleView.setVisibility(View.VISIBLE);
			
			if (leftBufferProgressBar.isShown()) {
				rightBufferProgressBar.setVisibility(View.VISIBLE);
			}
			
			ivSwitchsd.setImageDrawable(getResources().getDrawable(R.drawable.screen_btn_on));
		}
	}
	
	// 设置横屏的固定方向，禁用掉重力感应方向
	private void setLandScapeRequestOrientation() {
		int rotation = wm.getDefaultDisplay().getRotation();
		// 旋转90°为横屏正向，旋转270°为横屏逆向
		if (rotation == Surface.ROTATION_90) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else if (rotation == Surface.ROTATION_270) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
		}
	}
	
	private int getCurrentVideoIndex() {
		return Arrays.asList(PlayFragment.playVideoIds).indexOf(videoId);
	}
	
	private void changeVideo(int position, boolean isCompleted) {
		if (isCompleted) {
			updateCompleteDataPosition();
		} else {
			updateDataPosition();
		}
		
		isPrepared = false;
		
		setLayoutVisibility(View.GONE, false);
		leftBufferProgressBar.setVisibility(View.VISIBLE);
		
		if (ivSwitchsd.isSelected()) {
			rightBufferProgressBar.setVisibility(View.VISIBLE);
		}
		
		ivCenterPlay.setVisibility(View.GONE);
		
		currentPosition = 0;
		currentPlayPosition = 0;
		
		timerTask.cancel();
		
		videoId = PlayFragment.playVideoIds[position];
		
		if (playChangeVideoPopupWindow != null) {
			playChangeVideoPopupWindow.setSelectedPosition(getCurrentVideoIndex()).refreshView();
		}
		
		player.pause();
		player.stop();
		player.reset();
		player.setDefaultDefinition(defaultDefinition);
		player.setVideoPlayInfo(videoId, ConfigUtil.USERID, ConfigUtil.API_KEY, verificationCode,VrMediaPlayActivity.this);
		player.setSurface(mSurface);
		player.prepareAsync();
	}
	
	private void updateCompleteDataPosition() {
		if (DataSet.getVideoPosition(videoId) > 0) {
			DataSet.updateVideoPosition(videoId, currentPlayPosition);
		} else {
			DataSet.insertVideoPosition(videoId, currentPlayPosition);
		}
	}
	
	OnSeekBarChangeListener onSeekBarChangeListener = new OnSeekBarChangeListener() {
		int progress = 0;

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			if (networkConnected || isLocalPlay) {
				player.seekTo(progress);
				playerHandler.postDelayed(hidePlayRunnable, 5000);
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			playerHandler.removeCallbacks(hidePlayRunnable);
		}

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			if (networkConnected || isLocalPlay) {
				this.progress = (int)(progress * player.getDuration() / seekBar.getMax());
			}
		}
	};

	OnSeekBarChangeListener seekBarChangeListener = new OnSeekBarChangeListener() {
		@Override
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
			//有声音的进度变化来控制vr的声音变化
			changeVrSound(progress);
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			playerHandler.removeCallbacks(hidePlayRunnable);
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			playerHandler.postDelayed(hidePlayRunnable, 5000);
		}

	};

	// 更新声音大小
	private void changeSound(final int progress) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				changePlayerControlSound(progress);
			}
		});
	}

	private void changeVrSound(final int progress) {

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, AudioManager.FLAG_PLAY_SOUND);
				vrController.changeSound(progress);
			}
		});
	}

	private void changePlayerControlSound(int progress) {
		currentVolume = progress;
		volumeSeekBar.setProgress(progress);
	}

	// 控制播放器面板显示
	private boolean isDisplay = false;

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// 监测音量变化
		if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN
				|| event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {

			int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			if (currentVolume != volume) {
				currentVolume = volume;
				changeSound(currentVolume);
			}

			if (isPrepared) {
				setLayoutVisibility(View.VISIBLE, true);
			}
		}
		return super.dispatchKeyEvent(event);
	}

	/**
	 * 
	 * @param visibility 显示状态
	 * @param isDisplay 是否延迟消失
	 */
	private void setLayoutVisibility(int visibility, boolean isDisplay) {
		if (player == null || player.getDuration() <= 0) {
			return;
		}

		playerHandler.removeCallbacks(hidePlayRunnable);
		
		this.isDisplay = isDisplay;
		
		if (definitionMenu != null && visibility == View.GONE) {
			definitionMenu.dismiss();
		}
		
		if (isDisplay) {
			playerHandler.postDelayed(hidePlayRunnable, 5000);
		}
		
		if (isPortrait()) {
			ivFullscreen.setVisibility(visibility);
			
			lockView.setVisibility(View.GONE);
			
			volumeLayout.setVisibility(View.GONE);
			tvDefinition.setVisibility(View.GONE);
			tvChangeVideo.setVisibility(View.GONE);
			ivTopMenu.setVisibility(View.GONE);
			ivBackVideo.setVisibility(View.GONE);
			ivNextVideo.setVisibility(View.GONE);
		} else {
			ivFullscreen.setVisibility(View.GONE);
			
			lockView.setVisibility(visibility);
			if (lockView.isSelected()) {
				visibility = View.GONE;
			}
			
			volumeLayout.setVisibility(visibility);
			tvDefinition.setVisibility(visibility);
			tvChangeVideo.setVisibility(visibility);
			ivTopMenu.setVisibility(visibility);
			ivBackVideo.setVisibility(visibility);
			ivNextVideo.setVisibility(visibility);
		}
		
		if (isLocalPlay) {
			ivTopMenu.setVisibility(View.GONE);
			
			ivBackVideo.setVisibility(View.GONE);
			ivNextVideo.setVisibility(View.GONE);
			tvChangeVideo.setVisibility(View.GONE);
			tvDefinition.setVisibility(View.GONE);
			ivFullscreen.setVisibility(View.INVISIBLE);
		}
		
		playerTopLayout.setVisibility(visibility);
		playerBottomLayout.setVisibility(visibility);
	}

	private Handler alertHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			showUiThreadToast("视频异常，无法播放。");
			super.handleMessage(msg);
		}
	};

	// 重置隐藏界面组件的延迟时间
	private void resetHideDelayed() {
		playerHandler.removeCallbacks(hidePlayRunnable);
		playerHandler.postDelayed(hidePlayRunnable, 5000);
	}

	private void changePlayerPlayStatus(boolean isCurrentPlaying) {
		if (isCurrentPlaying) {
			player.start();
			ivCenterPlay.setVisibility(View.GONE);
			ivPlay.setImageResource(R.drawable.smallstop_ic);
		} else {
			player.pause();
			ivCenterPlay.setVisibility(View.VISIBLE);
			ivPlay.setImageResource(R.drawable.smallbegin_ic);
		}
	}
	
	private Runnable backupPlayRunnable = new Runnable() {
		
		@Override
		public void run() {
			startBackupPlay();
		}
	};

	boolean isBackupPlay = false;
	private void startBackupPlay() {
		player.setBackupPlay(true);
		isBackupPlay = true;
		player.reset();
		player.prepareAsync();
	}

	// 获得当前屏幕的方向
	private boolean isPortrait() {
		int mOrientation = getApplicationContext().getResources().getConfiguration().orientation;
		if ( mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			return false;
		} else{
			return true;
		}
	}
	
	private void updateDataPosition() {
		if (isLocalPlay) {
			return;
		}
		
		if (currentPlayPosition > 0 ) {
			if (DataSet.getVideoPosition(videoId) > 0) {
				DataSet.updateVideoPosition(videoId, currentPlayPosition);
			} else {
				DataSet.insertVideoPosition(videoId, currentPlayPosition);
			}
		}
	}
	
	//-----------------------------------播放器回调---------------------------
	boolean isVr = false;
	@Override
	public void onPrepared(MediaPlayer mp) {
		initTimerTask();
		
		isPrepared = true;
		if (!isFreeze) {
			if(isPlaying == null || isPlaying.booleanValue()){
				player.start();
				ivPlay.setImageResource(R.drawable.smallstop_ic);
			}
		}

		if (!isLocalPlay) {
			if (currentPosition > 0) {
				player.seekTo(currentPosition);
			} else {
				lastPlayPosition = DataSet.getVideoPosition(videoId);
				if (lastPlayPosition > 0) {
					player.seekTo(lastPlayPosition);
				}
			}
		}
		
		definitionMap = player.getDefinitions();
		if (!isLocalPlay) {
			initDefinitionPopMenu();
			disposeVrController();
		}

		leftBufferProgressBar.setVisibility(View.GONE);
		rightBufferProgressBar.setVisibility(View.GONE);
		setSurfaceViewLayout();
		videoDuration.setText(ParamsUtil.millsecondsToStr((int)player.getDuration()));
	}
	
	// 处理vr控制面板的一些初始化参数
	private void disposeVrController() {
		int vrModeTag = player.getPlayInfo().getVrMode();
		if (vrModeTag == 1) {
			isVr = true;
			vrController.getVRLibrary().switchProjectionMode(this, MDVRLibrary.PROJECTION_MODE_SPHERE);
		} else {
			isVr = false;
			vrController.getVRLibrary().switchProjectionMode(this, MDVRLibrary.PROJECTION_MODE_PLANE_FIT);
		}
		
		vrController.initAudio(maxVolume, currentVolume)
					.setIsVr(isVr)
					.onPrepared();
		
		vrController.initDefinitionText(definitionMap, currentDefinitionIndex);
	}
	
	// 设置surfaceview的布局
	private void setSurfaceViewLayout() {
		LayoutParams params = getScreenSizeParams(currentScreenSizeFlag);
		params.addRule(RelativeLayout.CENTER_IN_PARENT);
		surfaceView.setLayoutParams(params);
	}

	private void initDefinitionPopMenu() {
		if(definitionMap.size() > 1){
			currentDefinitionIndex = 1;
			Integer[] definitions = new Integer[]{};
			definitions = definitionMap.values().toArray(definitions);
			// 设置默认为普清，所以此处需要判断一下
			for (int i=0; i<definitions.length; i++) {
				if (definitions[i].intValue() == defaultDefinition) {
					currentDefinitionIndex = i;
				}
			}
		}
		
		definitionMenu = new PopMenu(this, R.drawable.popdown, currentDefinitionIndex, getResources().getDimensionPixelSize(R.dimen.popmenu_height));
		// 设置清晰度列表
		definitionArray = new String[] {};
		definitionArray = definitionMap.keySet().toArray(definitionArray);

		definitionMenu.addItems(definitionArray);
		definitionMenu.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(int position) {
				try {
                    changeDefinition(position);
                    vrController.changeDefinitionView(position);
				} catch (IOException e) {
					Log.e("player error", e.getMessage());
				}

			}
		});
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Message msg = new Message();
		msg.what = what;
	
		if (!isBackupPlay) {
			startBackupPlay();
		} else {
			if (alertHandler != null) {
				alertHandler.sendMessage(msg);
			}
		}
		return false;
	}

	@Override
	public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
		vrController.onVideoSizeChanged(width, height);
	}
	
	@Override
	public boolean onInfo(MediaPlayer mp, int what, int extra) {
		switch(what) {
		case DWMediaPlayer.MEDIA_INFO_BUFFERING_START:
			if (player.isPlaying()) {
				leftBufferProgressBar.setVisibility(View.VISIBLE);
				
				if (ivSwitchsd.isSelected()) {
					rightBufferProgressBar.setVisibility(View.VISIBLE);
				}
			}
			
			if (!isBackupPlay) {
				playerHandler.postDelayed(backupPlayRunnable, 10 * 1000);
			}
			
			break;
		case DWMediaPlayer.MEDIA_INFO_BUFFERING_END:
			leftBufferProgressBar.setVisibility(View.GONE);
			rightBufferProgressBar.setVisibility(View.GONE);
			playerHandler.removeCallbacks(backupPlayRunnable);
			break;
		}
		return false;
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		if (isLocalPlay) {
			showUiThreadToast("播放完成！");
			finish();
			return;
		}
		
		if (isPrepared) {
			runOnUiThread(new Runnable() {
				
				@Override
				public void run() {
					showUiThreadToast("切换中，请稍候……");
					currentPlayPosition = 0;
					currentPosition = 0;
					changeToNextVideo(true);
				}
			});
		}
	}
	
    //-----------------------------activity生命周期---------------------------------
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		
		super.onConfigurationChanged(newConfig);
		vrController.onConfigurationChanged();
		
		if (isPrepared) {
			// 刷新界面
			setLayoutVisibility(View.GONE, false);
			setLayoutVisibility(View.VISIBLE, true);
		}
		
		lockView.setSelected(false);
		
		if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			rlBelow.setVisibility(View.VISIBLE);
			ivFullscreen.setImageResource(R.drawable.fullscreen_close);
			
			if (playChangeVideoPopupWindow != null) {
				playChangeVideoPopupWindow.dismiss();
			}
			
			if (playTopPopupWindow != null) {
				playTopPopupWindow.dismiss();
			}
			
		} else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE){
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
			rlBelow.setVisibility(View.GONE);
			ivFullscreen.setImageResource(R.drawable.fullscreen_open);
		}
		
		setSurfaceViewLayout();
	}
	
	@Override
	public void onBackPressed() {
		if (isPortrait() || isLocalPlay) {
			super.onBackPressed();
		} else {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}
	}
	
	@Override
	public void onResume() {
		vrController.onResume();
		if (isFreeze) {
			isFreeze = false;
			if (isPrepared) {
				player.start();
			}
		} else {
			if (isPlaying != null && isPlaying.booleanValue() && isPrepared) {
				player.start();
			}
		}
		super.onResume();
	}

	@Override
	public void onPause() {
		vrController.onPause();
		if (isPrepared) {
			// 如果播放器prepare完成，则对播放器进行暂停操作，并记录状态
			if (player.isPlaying()) {
				isPlaying = true;
			} else {
				isPlaying = false;
			}
			player.pause();
		} else {
			// 如果播放器没有prepare完成，则设置isFreeze为true
			isFreeze = true;
		}
		super.onPause();
	}
	
	@Override
	protected void onStop() {
		if (!isLocalPlay) {
			setLandScapeRequestOrientation();
		}
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		vrController.onDestroy();
		
		if (timerTask != null) {
			timerTask.cancel();
		}
		
		playerHandler.removeCallbacksAndMessages(null);
		playerHandler = null;
		
		alertHandler.removeCallbacksAndMessages(null);
		alertHandler = null;
		
		updateDataPosition();
		
		if (player != null) {
			player.reset();
			player.stop();
			player.release();
			player = null;
		}
		if (dialog != null) {
			dialog.dismiss();
		}
		if (!isLocalPlay) {
			networkInfoTimerTask.cancel();
		}
		super.onDestroy();
	}
}
