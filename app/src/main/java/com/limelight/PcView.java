package com.limelight;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import androidx.activity.ComponentActivity;

import com.limelight.binding.PlatformBinding;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.grid.assets.DiskAssetLoader;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.preferences.AddComputerManually;
import com.limelight.preferences.GlPreferences;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.home.PcHomeActions;
import com.limelight.ui.home.PcHomeHost;
import com.limelight.ui.home.PcHomeState;
import com.limelight.utils.Dialog;
import com.limelight.utils.HelpLauncher;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.ShortcutHelper;
import com.limelight.utils.UiHelper;

import org.xmlpull.v1.XmlPullParserException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PcView extends ComponentActivity implements PcHomeActions {
    private final PcHomeState homeState = new PcHomeState();
    private ShortcutHelper shortcutHelper;
    private ComputerManagerService.ComputerManagerBinder managerBinder;
    private boolean freezeUpdates, runningPolling, inForeground;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    (ComputerManagerService.ComputerManagerBinder) binder;
            new Thread(() -> {
                localBinder.waitForReady();
                managerBinder = localBinder;
                startComputerUpdates();
                new AndroidCryptoProvider(PcView.this).getClientCertificate();
            }).start();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inForeground = true;
        final GlPreferences glPrefs = GlPreferences.readPreferences(this);
        if (!glPrefs.savedFingerprint.equals(Build.FINGERPRINT) || glPrefs.glRenderer.isEmpty()) {
            GLSurfaceView surfaceView = new GLSurfaceView(this);
            surfaceView.setRenderer(new GLSurfaceView.Renderer() {
                @Override
                public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                    glPrefs.glRenderer = gl10.glGetString(GL10.GL_RENDERER);
                    glPrefs.savedFingerprint = Build.FINGERPRINT;
                    glPrefs.writePreferences();
                    LimeLog.info("Fetched GL Renderer: " + glPrefs.glRenderer);
                    runOnUiThread(PcView.this::completeOnCreate);
                }

                @Override
                public void onSurfaceChanged(GL10 gl10, int width, int height) {}

                @Override
                public void onDrawFrame(GL10 gl10) {}
            });
            setContentView(surfaceView);
        } else {
            LimeLog.info("Cached GL Renderer: " + glPrefs.glRenderer);
            completeOnCreate();
        }
    }

    private void completeOnCreate() {
        shortcutHelper = new ShortcutHelper(this);
        UiHelper.setLocale(this);
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);
        UiHelper.notifyNewComposeRootView(this);
        setShouldDockBigOverlays(false);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        PcHomeHost.attach(this, homeState, this);
    }

    private void startComputerUpdates() {
        if (managerBinder != null && !runningPolling && inForeground) {
            freezeUpdates = false;
            managerBinder.startPolling(new ComputerManagerListener() {
                @Override
                public void notifyComputerUpdated(final ComputerDetails details) {
                    if (!freezeUpdates) {
                        runOnUiThread(() -> updateComputer(details));
                        // Create shortcuts off the main thread to prevent ANRs.
                        if (details.pairState == PairState.PAIRED) {
                            shortcutHelper.createAppViewShortcutForOnlineHost(details);
                        }
                    }
                }
            });
            runningPolling = true;
        }
    }

    private void stopComputerUpdates(boolean wait) {
        if (managerBinder != null) {
            if (!runningPolling) return;
            freezeUpdates = true;
            managerBinder.stopPolling();
            if (wait) managerBinder.waitForPollingStopped();
            runningPolling = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (managerBinder != null) unbindService(serviceConnection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UiHelper.showDecoderCrashDialog(this);
        inForeground = true;
        startComputerUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        inForeground = false;
        stopComputerUpdates(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Dialog.closeDialogs();
        homeState.dismissAlert();
    }

    private boolean requireManager() {
        if (managerBinder != null) return true;
        homeState.toast(getString(R.string.error_manager_not_running));
        return false;
    }

    private void doPair(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            homeState.toast(getString(R.string.pair_pc_offline));
            return;
        }
        if (!requireManager()) return;
        homeState.toast(getString(R.string.pairing));
        new Thread(() -> {
            String message;
            boolean success = false;
            try {
                stopComputerUpdates(true);
                NvHTTP httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                        computer.httpsPort, managerBinder.getUniqueId(), computer.serverCert,
                        PlatformBinding.getCryptoProvider(PcView.this));
                if (httpConn.getPairState() == PairState.PAIRED) {
                    message = null;
                    success = true;
                } else {
                    final String pinStr = PairingManager.generatePinString();
                    runOnUiThread(() -> homeState.showPairing(
                            getString(R.string.pair_pairing_title), getString(R.string.pair_pairing_msg),
                            pinStr, getString(R.string.pair_pairing_help), getString(android.R.string.ok)));
                    PairingManager pm = httpConn.getPairingManager();
                    PairState pairState = pm.pair(httpConn.getServerInfo(true), pinStr);
                    if (pairState == PairState.PIN_WRONG) {
                        message = getString(R.string.pair_incorrect_pin);
                    } else if (pairState == PairState.FAILED) {
                        message = getString(computer.runningGameId != 0 ? R.string.pair_pc_ingame : R.string.pair_fail);
                    } else if (pairState == PairState.ALREADY_IN_PROGRESS) {
                        message = getString(R.string.pair_already_in_progress);
                    } else if (pairState == PairState.PAIRED) {
                        message = null;
                        success = true;
                        managerBinder.getComputer(computer.uuid).serverCert = pm.getPairedCert();
                        managerBinder.invalidateStateForComputer(computer.uuid);
                    } else {
                        message = null;
                    }
                }
            } catch (UnknownHostException e) {
                message = getString(R.string.error_unknown_host);
            } catch (FileNotFoundException e) {
                message = getString(R.string.error_404);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
                message = e.getMessage();
            }
            runOnUiThread(homeState::dismissAlert);
            final String toastMessage = message;
            final boolean toastSuccess = success;
            runOnUiThread(() -> {
                if (toastMessage != null) homeState.toast(toastMessage);
                if (toastSuccess) doAppList(computer, true, false);
                else startComputerUpdates();
            });
        }).start();
    }

    private void doWakeOnLan(final ComputerDetails computer) {
        if (computer.state == ComputerDetails.State.ONLINE) {
            homeState.toast(getString(R.string.wol_pc_online));
            return;
        }
        if (computer.macAddress == null) {
            homeState.toast(getString(R.string.wol_no_mac));
            return;
        }
        new Thread(() -> {
            String message;
            try {
                WakeOnLanSender.sendWolPacket(computer);
                message = getString(R.string.wol_waking_msg);
            } catch (IOException e) {
                message = getString(R.string.wol_fail);
            }
            final String toastMessage = message;
            runOnUiThread(() -> homeState.toast(toastMessage));
        }).start();
    }

    private void doAppList(ComputerDetails computer, boolean newlyPaired, boolean showHiddenGames) {
        if (computer.state == ComputerDetails.State.OFFLINE) {
            homeState.toast(getString(R.string.error_pc_offline));
            return;
        }
        if (!requireManager()) return;
        Intent intent = new Intent(this, AppView.class);
        intent.putExtra(AppView.NAME_EXTRA, computer.name);
        intent.putExtra(AppView.UUID_EXTRA, computer.uuid);
        intent.putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired);
        intent.putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, showHiddenGames);
        startActivity(intent);
    }

    @Override
    public void onConnect(String uuid) {
        final ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        if (!requireManager()) return;
        // Keep the binder used by the worker stable across a service disconnect.
        final ComputerManagerService.ComputerManagerBinder binder = managerBinder;
        homeState.setConnecting(uuid);
        new Thread(() -> {
            NvApp found = null;
            try {
                NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer),
                        computer.httpsPort, binder.getUniqueId(), computer.serverCert,
                        PlatformBinding.getCryptoProvider(PcView.this));
                found = http.getAppByName("Desktop");
            } catch (IOException | XmlPullParserException e) {
                LimeLog.warning("Desktop lookup failed: " + e);
            }
            final NvApp desktop = found;
            runOnUiThread(() -> {
                homeState.setConnecting(null);
                // Never launch a stream after the user leaves this activity.
                if (!inForeground || managerBinder == null) return;
                if (desktop != null) ServerHelper.doStart(this, desktop, computer, managerBinder);
                else doAppList(computer, false, false);
            });
        }).start();
    }

    @Override
    public void onResume(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        if (!requireManager()) return;
        ServerHelper.doStart(this, new NvApp("app", computer.runningGameId, false), computer, managerBinder);
    }

    @Override
    public void onPair(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        doPair(computer);
    }

    @Override
    public void onWake(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        doWakeOnLan(computer);
    }

    @Override
    public void onOpenApps(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        doAppList(computer, false, false);
    }

    @Override
    public void onOpenAllApps(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        doAppList(computer, false, true);
    }

    @Override
    public void onQuitSession(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        homeState.confirm(getString(R.string.applist_menu_quit), getString(R.string.applist_quit_confirmation),
                getString(R.string.yes), getString(R.string.no), true, () -> {
                    if (!requireManager()) return;
                    ServerHelper.doQuit(this, computer, new NvApp("app", 0, false), managerBinder, null);
                });
    }

    @Override
    public void onDeletePc(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        if (ActivityManager.isUserAMonkey()) {
            LimeLog.info("Ignoring delete PC request from monkey");
            return;
        }
        homeState.confirm(computer.name, getString(R.string.delete_pc_msg), getString(R.string.yes),
                getString(R.string.no), true, () -> {
                    if (!requireManager()) return;
                    removeComputer(computer);
                });
    }

    @Override
    public void onShowDetails(String uuid) {
        ComputerDetails computer = homeState.details(uuid);
        if (computer == null) return;
        homeState.showMessage(getString(R.string.title_details), computer.toString(), getString(android.R.string.ok));
    }

    @Override
    public void onTestNetwork() { ServerHelper.doNetworkTest(this); }

    @Override
    public void onGameStreamInfo() { HelpLauncher.launchGameStreamEolFaq(this); }

    @Override
    public void onOpenSettings() { startActivity(new Intent(this, StreamSettings.class)); }

    @Override
    public void onAddPc() { startActivity(new Intent(this, AddComputerManually.class)); }

    @Override
    public void onHelp() { HelpLauncher.launchSetupGuide(this); }

    private void removeComputer(ComputerDetails details) {
        managerBinder.removeComputer(details);
        new DiskAssetLoader(this).deleteAssetsForComputer(details.uuid);
        getSharedPreferences(AppView.HIDDEN_APPS_PREF_FILENAME, MODE_PRIVATE)
                .edit().remove(details.uuid).apply();
        if (homeState.details(details.uuid) != null) {
            shortcutHelper.disableComputerShortcut(details, getString(R.string.scut_deleted_pc));
            homeState.remove(details.uuid);
        }
    }

    private void updateComputer(ComputerDetails details) {
        homeState.update(details);
    }
}
