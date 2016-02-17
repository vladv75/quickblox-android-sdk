package com.quickblox.sample.groupchatwebrtc.fragments;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.*;
import android.os.Process;
import android.support.annotation.DimenRes;
import android.support.annotation.NonNull;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.quickblox.sample.groupchatwebrtc.activities.CallActivity;
import com.quickblox.sample.groupchatwebrtc.activities.ListUsersActivity;
import com.quickblox.sample.groupchatwebrtc.adapters.OpponentsFromCallAdapter;
import com.quickblox.sample.groupchatwebrtc.R;
import com.quickblox.sample.groupchatwebrtc.definitions.Consts;
import com.quickblox.sample.groupchatwebrtc.holder.DataHolder;
import com.quickblox.sample.groupchatwebrtc.util.CameraUtils;
import com.quickblox.sample.groupchatwebrtc.view.RTCGLVideoView;
import com.quickblox.sample.groupchatwebrtc.view.RTCGLVideoView.RendererConfig;
import com.quickblox.users.model.QBUser;
import com.quickblox.videochat.webrtc.QBMediaStreamManager;
import com.quickblox.videochat.webrtc.exception.QBRTCException;
import com.quickblox.videochat.webrtc.QBRTCSession;
import com.quickblox.videochat.webrtc.QBRTCTypes;
import com.quickblox.videochat.webrtc.callbacks.QBRTCClientVideoTracksCallbacks;
import com.quickblox.videochat.webrtc.callbacks.QBRTCSessionConnectionCallbacks;
import com.quickblox.videochat.webrtc.view.QBRTCVideoTrack;

import org.webrtc.VideoRenderer;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * QuickBlox team
 */
public class ConversationFragment extends Fragment implements Serializable, QBRTCClientVideoTracksCallbacks, QBRTCSessionConnectionCallbacks, CallActivity.QBRTCSessionUserCallback, OpponentsFromCallAdapter.OnAdapterEventListener {

    public static final String CALLER_NAME = "caller_name";
    public static final String SESSION_ID = "sessionID";
    public static final String START_CONVERSATION_REASON = "start_conversation_reason";

    private static final int DEFAULT_ROWS_COUNT = 2;
    private static final int DEFAULT_COLS_COUNT = 3;
    private static final long TOGGLE_CAMERA_DELAY = 1000;
    private static final long LOCAL_TRACk_INITIALIZE_DELAY = 500;

    // Bluetooth audio SCO states. Example of valid state sequence:
    // SCO_INVALID -> SCO_TURNING_ON -> SCO_ON -> SCO_TURNING_OFF -> SCO_OFF.
    private static final int STATE_BLUETOOTH_SCO_INVALID = -1;
    private static final int STATE_BLUETOOTH_SCO_OFF = 0;
    private static final int STATE_BLUETOOTH_SCO_ON = 1;
    private static final int STATE_BLUETOOTH_SCO_TURNING_ON = 2;
    private static final int STATE_BLUETOOTH_SCO_TURNING_OFF = 3;

    private String TAG = ConversationFragment.class.getSimpleName();
    private ArrayList<QBUser> opponents;
    private int qbConferenceType;
    private int startReason;
    private String sessionID;

    private ToggleButton cameraToggle;
    private ToggleButton switchCameraToggle;
    private ToggleButton dynamicToggleVideoCall;
    private ToggleButton micToggleVideoCall;
    private ToggleButton bluetoothAudioToggle;
    private ImageButton handUpVideoCall;
    private View myCameraOff;
    private TextView incUserName;
    private View view;
    private Map<String, String> userInfo;
    private boolean isVideoEnabled = false;
    private boolean isAudioEnabled = true;
    private List<QBUser> allUsers = new ArrayList<>();
    private LinearLayout actionVideoButtonsLayout;
    private String callerName;
    private boolean isMessageProcessed;
    private RTCGLVideoView localVideoView;
    private IntentFilter intentFilter;
    private AudioStreamReceiver audioStreamReceiver;
    private CameraState cameraState = CameraState.NONE;
    private RecyclerView recyclerView;
    private SparseArray<OpponentsFromCallAdapter.ViewHolder> opponentViewHolders;
    private boolean isPeerToPeerCall;
    private QBRTCVideoTrack localVideoTrack;
    private Handler mainHandler;
    private AudioManager localAudioManager;
    private BroadcastReceiver mBluetoothHeadsetReceiver;
    private boolean DEVICE_BLUETOOTH_HEADSET_PLAGGED;
    private BroadcastReceiver mBluetoothScoReceiver;
    private Context mContext;
    private AudioManager mAudioManager;
    private int mBluetoothScoState = STATE_BLUETOOTH_SCO_INVALID;

    public static ConversationFragment newInstance(List<QBUser> opponents, String callerName,
                                                   QBRTCTypes.QBConferenceType qbConferenceType,
                                                   Map<String, String> userInfo, CallActivity.StartConversetionReason reason,
                                                   String sesionnId) {

        ConversationFragment fragment = new ConversationFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(Consts.CONFERENCE_TYPE, qbConferenceType.getValue());
        bundle.putString(CALLER_NAME, callerName);
        bundle.putSerializable(Consts.OPPONENTS, (Serializable) opponents);
        if (userInfo != null) {
            for (String key : userInfo.keySet()) {
                bundle.putString("UserInfo:" + key, userInfo.get(key));
            }
        }
        bundle.putInt(START_CONVERSATION_REASON, reason.ordinal());
        if (sesionnId != null) {
            bundle.putString(SESSION_ID, sesionnId);
        }
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_conversation, container, false);
        Log.d(TAG, "Fragment. Thread id: " + Thread.currentThread().getId());

        ((CallActivity) getActivity()).initActionBarWithTimer();

        if (getArguments() != null) {
            opponents = (ArrayList<QBUser>) getArguments().getSerializable(Consts.OPPONENTS);
            qbConferenceType = getArguments().getInt(Consts.CONFERENCE_TYPE);
            startReason = getArguments().getInt(CallActivity.START_CONVERSATION_REASON);
            sessionID = getArguments().getString(CallActivity.SESSION_ID);
            callerName = getArguments().getString(CallActivity.CALLER_NAME);

            isPeerToPeerCall = opponents.size() == 1;
            isVideoEnabled = (qbConferenceType ==
                    QBRTCTypes.QBConferenceType.QB_CONFERENCE_TYPE_VIDEO.getValue());
            Log.d(TAG, "CALLER_NAME: " + callerName);
            Log.d(TAG, "opponents: " + opponents.toString());
        }

        initViews(view);
        initButtonsListener();
        initSessionListener();
        setUpUiByCallType(qbConferenceType);

        mainHandler = new FragmentLifeCycleHandler();
        return view;

    }

    private void initSessionListener() {
        ((CallActivity) getActivity()).addVideoTrackCallbacksListener(this);
    }

    private void setUpUiByCallType(int qbConferenceType) {
        if (!isVideoEnabled) {
            cameraToggle.setVisibility(View.GONE);
            if (switchCameraToggle != null) {
                switchCameraToggle.setVisibility(View.INVISIBLE);
            }

        }
    }

    public void actionButtonsEnabled(boolean enability) {

        cameraToggle.setEnabled(enability);
        micToggleVideoCall.setEnabled(enability);
        dynamicToggleVideoCall.setEnabled(enability);

        // inactivate toggle buttons
        cameraToggle.setActivated(enability);
        micToggleVideoCall.setActivated(enability);
        dynamicToggleVideoCall.setActivated(enability);

        if (switchCameraToggle != null) {
            switchCameraToggle.setEnabled(enability);
            switchCameraToggle.setActivated(enability);
        }
    }


    @Override
    public void onStart() {

        getActivity().registerReceiver(audioStreamReceiver, intentFilter);
        registerForBluetoothHeadsetIntentBroadcast();
        registerForBluetoothScoIntentBroadcast();

        super.onStart();
        QBRTCSession session = ((CallActivity) getActivity()).getCurrentSession();
        if (!isMessageProcessed) {
            if (startReason == CallActivity.StartConversetionReason.INCOME_CALL_FOR_ACCEPTION.ordinal()) {
                session.acceptCall(session.getUserInfo());
            } else {
                session.startCall(session.getUserInfo());
            }
            isMessageProcessed = true;
        }
        ((CallActivity) getActivity()).addTCClientConnectionCallback(this);
        ((CallActivity) getActivity()).addRTCSessionUserCallback(this);
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate() from " + TAG);
        super.onCreate(savedInstanceState);
        mContext = getActivity();

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        checkBluetoothPermission();

        intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManager.ACTION_HEADSET_PLUG);
        intentFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);

        audioStreamReceiver = new AudioStreamReceiver();
    }

    private void initViews(View view) {

        opponentViewHolders = new SparseArray<>(opponents.size());

        recyclerView = (RecyclerView) view.findViewById(R.id.grid_opponents);
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity(), R.dimen.grid_item_divider));
        recyclerView.setHasFixedSize(true);
        final int columnsCount = defineColumnsCount();
        final int rowsCount = defineRowCount();
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), columnsCount));
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                setGrid(columnsCount, rowsCount);
                recyclerView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            }
        });

        cameraToggle = (ToggleButton) view.findViewById(R.id.cameraToggle);
        if (!isPeerToPeerCall) {
            initLocalViewUI(view);
        }
        dynamicToggleVideoCall = (ToggleButton) view.findViewById(R.id.dynamicToggleVideoCall);
        bluetoothAudioToggle = (ToggleButton) view.findViewById(R.id.bluetoothAudioToggle);

        micToggleVideoCall = (ToggleButton) view.findViewById(R.id.micToggleVideoCall);
        actionVideoButtonsLayout = (LinearLayout) view.findViewById(R.id.element_set_video_buttons);
        handUpVideoCall = (ImageButton) view.findViewById(R.id.handUpVideoCall);
        incUserName = (TextView) view.findViewById(R.id.incUserName);
        incUserName.setText(callerName);
        incUserName.setBackgroundResource(ListUsersActivity.selectBackgrounForOpponent((
                DataHolder.getUserIndexByFullName(callerName)) + 1));

        actionButtonsEnabled(false);
    }

    private void setGrid(int columnsCount, int rowsCount) {
        int gridWidth = recyclerView.getMeasuredWidth();
        float itemMargin = getResources().getDimension(R.dimen.grid_item_divider);
        int cellSize = defineMinSize(gridWidth, recyclerView.getMeasuredHeight(),
                columnsCount, rowsCount, itemMargin);
        Log.i(TAG, "onGlobalLayout : cellSize=" + cellSize);

        OpponentsFromCallAdapter opponentsAdapter = new OpponentsFromCallAdapter(getActivity(), opponents, cellSize,
                cellSize, gridWidth, columnsCount, (int) itemMargin,
                isVideoEnabled);
        opponentsAdapter.setAdapterListener(ConversationFragment.this);
        recyclerView.setAdapter(opponentsAdapter);
    }

    private int defineMinSize(int measuredWidth, int measuredHeight, int columnsCount, int rowsCount, float padding) {
        int cellWidth = measuredWidth / columnsCount - (int) (padding * 2);
        int cellHeight = measuredHeight / rowsCount - (int) (padding * 2);
        return Math.min(cellWidth, cellHeight);
    }

    private int defineRowCount() {
        int result = DEFAULT_ROWS_COUNT;
        int opponentsCount = opponents.size();
        if (opponentsCount < 3) {
            result = opponentsCount;
        }
        return result;

    }

    private int defineColumnsCount() {
        int result = DEFAULT_COLS_COUNT;
        int opponentsCount = opponents.size();
        if (opponentsCount == 1 || opponentsCount == 2) {
            result = 1;
        } else if (opponentsCount == 4) {
            result = 2;
        }
        return result;
    }

    @Override
    public void onResume() {
        super.onResume();

        // If user changed camera state few times and last state was CameraState.ENABLED_FROM_USER // Жень, глянь здесь, смысл в том, что мы здесь включаем камеру, если юзер ее не выключал
        // than we turn on cam, else we nothing change
        if (cameraState != CameraState.DISABLED_FROM_USER
                && isVideoEnabled) {
            toggleCamera(true);
        }
    }

    @Override
    public void onPause() {
        // If camera state is CameraState.ENABLED_FROM_USER or CameraState.NONE
        // than we turn off cam
        if (cameraState != CameraState.DISABLED_FROM_USER && isVideoEnabled) {
            toggleCamera(false);
        }

        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mContext.unregisterReceiver(audioStreamReceiver);
        unregisterForBluetoothHeadsetIntentBroadcast();
        unregisterForBluetoothScoIntentBroadcast();
        ((CallActivity) getActivity()).removeRTCClientConnectionCallback(this);
        ((CallActivity) getActivity()).removeRTCSessionUserCallback(this);
    }

    private void initSwitchCameraButton(View view) {
        switchCameraToggle = (ToggleButton) view.findViewById(R.id.switchCameraToggle);
        switchCameraToggle.setOnClickListener(getCameraSwitchListener());
        switchCameraToggle.setActivated(false);
        switchCameraToggle.setVisibility(isVideoEnabled ?
                View.VISIBLE : View.INVISIBLE);
    }

    private void initButtonsListener() {
        cameraToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                cameraState = isChecked ? CameraState.ENABLED_FROM_USER : CameraState.DISABLED_FROM_USER;
                toggleCamera(isChecked);
            }
        });

        dynamicToggleVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((CallActivity) getActivity()).getCurrentSession() != null) {
                    Log.d(TAG, "Dynamic switched!");
                    if (!mAudioManager.isBluetoothScoOn())
                    ((CallActivity) getActivity()).getCurrentSession().switchAudioOutput();
                }
            }
        });

//        bluetoothAudioToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//            @Override
//            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
//                toggleBluetoothSco(isChecked);
//            }
//        });

        micToggleVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (((CallActivity) getActivity()).getCurrentSession() != null) {
                    if (isAudioEnabled) {
                        Log.d(TAG, "Mic is off!");
                        ((CallActivity) getActivity()).getCurrentSession().setAudioEnabled(false);
                        isAudioEnabled = false;
                    } else {
                        Log.d(TAG, "Mic is on!");
                        ((CallActivity) getActivity()).getCurrentSession().setAudioEnabled(true);
                        isAudioEnabled = true;
                    }
                }
            }
        });

        handUpVideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                actionButtonsEnabled(false);
                handUpVideoCall.setEnabled(false);
                Log.d(TAG, "Call is stopped");

                ((CallActivity) getActivity()).hangUpCurrentSession();
                handUpVideoCall.setEnabled(false);
                handUpVideoCall.setActivated(false);

            }
        });
    }

    private View.OnClickListener getCameraSwitchListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                QBRTCSession currentSession = ((CallActivity) getActivity()).getCurrentSession();
                if (currentSession == null) {
                    return;
                }
                final QBMediaStreamManager mediaStreamManager = currentSession.getMediaStreamManager();
                if (mediaStreamManager == null) {
                    return;
                }
                boolean cameraSwitched = mediaStreamManager.switchCameraInput(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                toggleCamerainternal(mediaStreamManager);
                            }
                        });
                    }
                });
            }

        };
    }

    private void toggleCamerainternal(QBMediaStreamManager mediaStreamManager){
        toggleCameraOnUiThread(false);
        int currentCameraId = mediaStreamManager.getCurrentCameraId();
        Log.d(TAG, "Camera was switched!");
        RendererConfig config = new RendererConfig();
        config.mirror = CameraUtils.isCameraFront(currentCameraId);
        localVideoView.updateRenderer(isPeerToPeerCall ? RTCGLVideoView.RendererSurface.SECOND :
                RTCGLVideoView.RendererSurface.MAIN, config);
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                toggleCameraOnUiThread(true);
            }
        }, TOGGLE_CAMERA_DELAY);
    }

    private void toggleCameraOnUiThread(final boolean toggle){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleCamera(toggle);
            }
        });
    }

    private void runOnUiThread(Runnable runnable){
        mainHandler.post(runnable);
    }

    private void toggleCamera(boolean isNeedEnableCam) {
        QBRTCSession currentSession = ((CallActivity) getActivity()).getCurrentSession();
        if (currentSession != null && currentSession.getMediaStreamManager() != null){
            currentSession.getMediaStreamManager().setVideoEnabled(isNeedEnableCam);
            if (myCameraOff != null) {
                myCameraOff.setVisibility(isNeedEnableCam ? View.INVISIBLE : View.VISIBLE);
            }
            if (switchCameraToggle != null) {
                switchCameraToggle.setVisibility(isNeedEnableCam ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    @Override
    public void onLocalVideoTrackReceive(QBRTCSession qbrtcSession, final QBRTCVideoTrack videoTrack) {
        Log.d(TAG, "onLocalVideoTrackReceive() run");

        if (localVideoView != null) {
            fillVideoView(localVideoView, videoTrack, !isPeerToPeerCall);
        } else {
            //localVideoView hasn't been inflated yet. Will set track while OnBindLastViewHolder
            localVideoTrack = videoTrack;
        }
    }

    @Override
    public void onRemoteVideoTrackReceive(QBRTCSession session, QBRTCVideoTrack videoTrack, Integer userID) {
        Log.d(TAG, "onRemoteVideoTrackReceive for opponent= " + userID);
        OpponentsFromCallAdapter.ViewHolder itemHolder = getViewHolderForOpponent(userID);
        if (itemHolder == null) {
            return;
        }
        RTCGLVideoView remoteVideoView = itemHolder.getOpponentView();
        if (remoteVideoView != null) {
            fillVideoView(remoteVideoView, videoTrack);
        }
    }

    //last opponent view is bind
    @Override
    public void OnBindLastViewHolder(OpponentsFromCallAdapter.ViewHolder holder, int position) {
        Log.i(TAG, "OnBindLastViewHolder position=" + position);
        if (!isVideoEnabled) {
            return;
        }
        if (isPeerToPeerCall) {
            localVideoView = holder.getOpponentView();
            initLocalViewUI(holder.itemView);
        } else {
            //on group call we postpone initialization of localVideoView due to set it on Gui renderer.
            // Refer to RTCGlVIew
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    localVideoView = (RTCGLVideoView) ((ViewStub) getView().findViewById(R.id.localViewStub)).inflate();
                    if (localVideoTrack != null) {
                        fillVideoView(localVideoView, localVideoTrack, !isPeerToPeerCall);
                    }
                }
            }, LOCAL_TRACk_INITIALIZE_DELAY);
        }
    }

    private void initLocalViewUI(View localView) {
        initSwitchCameraButton(localView);
        myCameraOff = localView.findViewById(R.id.cameraOff);
    }

    private OpponentsFromCallAdapter.ViewHolder getViewHolderForOpponent(Integer userID) {
        OpponentsFromCallAdapter.ViewHolder holder = opponentViewHolders.get(userID);
        if (holder == null) {
            holder = findHolder(userID);
            if (holder != null) {
                opponentViewHolders.append(userID, holder);
            }
        }
        return holder;
    }

    private OpponentsFromCallAdapter.ViewHolder findHolder(Integer userID) {
        int childCount = recyclerView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childView = recyclerView.getChildAt(i);
            OpponentsFromCallAdapter.ViewHolder childViewHolder = (OpponentsFromCallAdapter.ViewHolder) recyclerView.getChildViewHolder(childView);
            Log.d(TAG, "getViewForOpponent holder user id is : " + childViewHolder.getUserId());
            if (userID.equals(childViewHolder.getUserId())) {
                return childViewHolder;
            }
        }
        return null;
    }

    private void fillVideoView(RTCGLVideoView videoView, QBRTCVideoTrack videoTrack, boolean remoteRenderer) {
        videoTrack.addRenderer(new VideoRenderer(remoteRenderer ?
                videoView.obtainVideoRenderer(RTCGLVideoView.RendererSurface.MAIN) :
                videoView.obtainVideoRenderer(RTCGLVideoView.RendererSurface.SECOND)));
        Log.d(TAG, (remoteRenderer ? "remote" : "local") + " Track is rendering");
    }

    private void fillVideoView(RTCGLVideoView videoView, QBRTCVideoTrack videoTrack) {
        fillVideoView(videoView, videoTrack, true);
    }

    private void setStatusForOpponent(int userId, final String status) {
        final OpponentsFromCallAdapter.ViewHolder holder = getViewHolderForOpponent(userId);
        if (holder == null) {
            return;
        }
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                holder.setStatus(status);
            }
        });
    }

    @Override
    public void onStartConnectToUser(QBRTCSession qbrtcSession, Integer userId) {
        setStatusForOpponent(userId, getString(R.string.checking));
    }

    @Override
    public void onConnectedToUser(QBRTCSession qbrtcSession,final Integer userId) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                actionButtonsEnabled(true);
            }
        });
        setStatusForOpponent(userId, getString(R.string.connected));
    }


    @Override
    public void onConnectionClosedForUser(QBRTCSession qbrtcSession, Integer integer) {
        setStatusForOpponent(integer, getString(R.string.closed));
    }

    @Override
    public void onDisconnectedFromUser(QBRTCSession qbrtcSession, Integer integer) {
        setStatusForOpponent(integer, getString(R.string.disconnected));
    }

    @Override
    public void onDisconnectedTimeoutFromUser(QBRTCSession qbrtcSession, Integer integer) {
        setStatusForOpponent(integer, getString(R.string.time_out));
    }

    @Override
    public void onConnectionFailedWithUser(QBRTCSession qbrtcSession, Integer integer) {
        setStatusForOpponent(integer, getString(R.string.failed));
    }

    @Override
    public void onError(QBRTCSession qbrtcSession, QBRTCException e) {

    }

    @Override
    public void onUserNotAnswer(QBRTCSession session, Integer userId) {
        setStatusForOpponent(userId, getString(R.string.noAnswer));
    }

    @Override
    public void onCallRejectByUser(QBRTCSession session, Integer userId, Map<String, String> userInfo) {
        setStatusForOpponent(userId, getString(R.string.rejected));
    }

    @Override
    public void onCallAcceptByUser(QBRTCSession session, Integer userId, Map<String, String> userInfo) {
        setStatusForOpponent(userId, getString(R.string.accepted));
    }

    @Override
    public void onReceiveHangUpFromUser(QBRTCSession session, Integer userId) {
        setStatusForOpponent(userId, getString(R.string.hungUp));
    }

    private class AudioStreamReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(AudioManager.ACTION_HEADSET_PLUG)) {
                Log.d(TAG, "ACTION_HEADSET_PLUG " + intent.getIntExtra("state", -1));
            } else if (intent.getAction().equals(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)) {
                Log.d(TAG, "ACTION_SCO_AUDIO_STATE_UPDATED " + intent.getIntExtra("EXTRA_SCO_AUDIO_STATE", -2));
            }

            dynamicToggleVideoCall.setChecked(intent.getIntExtra("state", -1) == 1);

        }
    }

    private enum CameraState {
        NONE,
        DISABLED_FROM_USER,
        ENABLED_FROM_USER
    }

    class FragmentLifeCycleHandler extends Handler{

        @Override
        public void dispatchMessage(Message msg) {
            if (isAdded() && getActivity() != null) {
                super.dispatchMessage(msg);
            } else {
                Log.d(TAG, "Fragment under destroying");
            }
        }
    }

    class DividerItemDecoration extends RecyclerView.ItemDecoration {

        private int space;

        public DividerItemDecoration(@NonNull Context context, @DimenRes int dimensionDivider) {
            this.space = context.getResources().getDimensionPixelSize(dimensionDivider);
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            outRect.set(space, space, space, space);
        }

    }

    public void setBluetoothEnabled(boolean enabled) {
        localAudioManager = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);

        if (enabled) {
            localAudioManager.setMode(0);
            localAudioManager.setBluetoothScoOn(true);
            localAudioManager.startBluetoothSco();
            localAudioManager.setMode(AudioManager.MODE_IN_CALL);
        } else {
            localAudioManager.setBluetoothScoOn(false);
            localAudioManager.stopBluetoothSco();
            localAudioManager.setMode(AudioManager.MODE_NORMAL);
        }
    }

    /**
     * Registers receiver for the broadcasted intent related to BT headset
     * availability or a change in connection state of the local Bluetooth
     * adapter. Example: triggers when the BT device is turned on or off.
     * BLUETOOTH permission is required to receive this one.
     */
    private void registerForBluetoothHeadsetIntentBroadcast() {
        IntentFilter filter = new IntentFilter(
                android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        /** Receiver which handles changes in BT headset availability. */
        mBluetoothHeadsetReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // A change in connection state of the Headset profile has
                // been detected, e.g. BT headset has been connected or
                // disconnected. This broadcast is *not* sticky.
                int profileState = intent.getIntExtra(
                        android.bluetooth.BluetoothHeadset.EXTRA_STATE,
                        android.bluetooth.BluetoothHeadset.STATE_DISCONNECTED);

                switch (profileState) {
                    case android.bluetooth.BluetoothProfile.STATE_DISCONNECTED:
                        // We do not have to explicitly call stopBluetoothSco()
                        // since BT SCO will be disconnected automatically when
                        // the BT headset is disabled.
//                        synchronized (mLock) {
//                            // Remove the BT device from the list of devices.
//                            mAudioDevices[DEVICE_BLUETOOTH_HEADSET] = false;
//                        }
                        DEVICE_BLUETOOTH_HEADSET_PLAGGED = false;
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_CONNECTED:
                            // Add the BT device to the list of devices.
                        DEVICE_BLUETOOTH_HEADSET_PLAGGED = true;
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_CONNECTING:
                        // Bluetooth service is switching from off to on.
                        DEVICE_BLUETOOTH_HEADSET_PLAGGED = false;
                        break;
                    case android.bluetooth.BluetoothProfile.STATE_DISCONNECTING:
                        // Bluetooth service is switching from on to off.
                        DEVICE_BLUETOOTH_HEADSET_PLAGGED = false;
                        break;
                    default:
//                        loge("Invalid state!");
                        break;
                }
            }
        };
        mContext.registerReceiver(mBluetoothHeadsetReceiver, filter);
    }

    private void unregisterForBluetoothHeadsetIntentBroadcast() {
        mContext.unregisterReceiver(mBluetoothHeadsetReceiver);
        mBluetoothHeadsetReceiver = null;
    }

    /**
     * Registers receiver for the broadcasted intent related the existence
     * of a BT SCO channel. Indicates if BT SCO streaming is on or off.
     */
    private void registerForBluetoothScoIntentBroadcast() {
        IntentFilter filter = new IntentFilter(
                AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        /** BroadcastReceiver implementation which handles changes in BT SCO. */
        mBluetoothScoReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED);

                switch (state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        mBluetoothScoState = STATE_BLUETOOTH_SCO_ON;
                        break;
                    case AudioManager.SCO_AUDIO_STATE_DISCONNECTED:
                        mBluetoothScoState = STATE_BLUETOOTH_SCO_OFF;
                        break;
                    case AudioManager.SCO_AUDIO_STATE_CONNECTING:
                        // do nothing
                        break;
                    default:
                }
            }
        };
        mContext.registerReceiver(mBluetoothScoReceiver, filter);
    }

    private void unregisterForBluetoothScoIntentBroadcast() {
        mContext.unregisterReceiver(mBluetoothScoReceiver);
        mBluetoothScoReceiver = null;
    }

    /**
     * Enables BT audio using the SCO audio channel.
     */
    private void startBluetoothSco() {
        Log.d(TAG, "startBluetoothSco()");
        if (!hasBluetoothPermission()) {
            Log.d(TAG, "!hasBluetoothPermission()");
            return;
        }
//        if (mBluetoothScoState == STATE_BLUETOOTH_SCO_ON ||
//                mBluetoothScoState == STATE_BLUETOOTH_SCO_TURNING_ON) {
//            // Unable to turn on BT in this state.
//            return;
//        }
//        // Check if audio is already routed to BT SCO; if so, just update
//        // states but don't try to enable it again.
//        if (mAudioManager.isBluetoothScoOn()) {
//            Log.d(TAG, "mAudioManager.isBluetoothScoOn()");
//            mBluetoothScoState = STATE_BLUETOOTH_SCO_ON;
//            return;
//        }
//        mBluetoothScoState = STATE_BLUETOOTH_SCO_TURNING_ON;
        mAudioManager.setMode(0);
        mAudioManager.setBluetoothScoOn(true);
        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
        Log.d(TAG, "startBluetoothSco() all done");
    }

    /**
     * Disables BT audio using the SCO audio channel.
     */
    private void stopBluetoothSco() {
        Log.d(TAG, "stopBluetoothSco()");
        if (!hasBluetoothPermission()) {
            Log.d(TAG, "!hasBluetoothPermission()");
            return;
        }
//        if (mBluetoothScoState != STATE_BLUETOOTH_SCO_ON &&
//                mBluetoothScoState != STATE_BLUETOOTH_SCO_TURNING_ON) {
//            // No need to turn off BT in this state.
//            return;
//        }
//        if (!mAudioManager.isBluetoothScoOn()) {
//            // TODO(henrika): can we do anything else than logging here?
//            return;
//        }
//        mBluetoothScoState = STATE_BLUETOOTH_SCO_TURNING_OFF;
        mAudioManager.setBluetoothScoOn(false);
        mAudioManager.stopBluetoothSco();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        Log.d(TAG, "stopBluetoothSco() all done");
    }

    /**
     * Checks if the process has BLUETOOTH permission or not.
     */
    private boolean hasBluetoothPermission() {
        boolean hasBluetooth = mContext.checkPermission(
                android.Manifest.permission.BLUETOOTH,
                android.os.Process.myPid(),
                Process.myUid()) == PackageManager.PERMISSION_GRANTED;
        return hasBluetooth;
    }

    private void checkBluetoothPermission() {
        boolean hasBluetooth = mContext.checkPermission(
                android.Manifest.permission.BLUETOOTH,
                android.os.Process.myPid(),
                Process.myUid()) == PackageManager.PERMISSION_GRANTED;

        if (hasBluetooth) {
            mAudioManager.setBluetoothScoOn(hasBluetooth);
            mAudioManager.setMode(AudioManager.MODE_IN_CALL);
            mAudioManager.startBluetoothSco();
        }

    }

    private void toggleBluetoothSco(boolean on) {
//        if (!DEVICE_BLUETOOTH_HEADSET_PLAGGED){
//            Log.d(TAG, )
//            return;
//        }



        if (on){
            Log.d(TAG, "bluetooth enable");
            startBluetoothSco();
            Log.d(TAG, "bluetooth enabled");
        } else {
            Log.d(TAG, "bluetooth disable");
            stopBluetoothSco();
            Log.d(TAG, "bluetooth disabled");
        }
    }

}


