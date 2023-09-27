/*
 * Copyright ©️2023 www.touchair.cn
 * Create by <de.liu@touchair.cn>
 */

package cn.touchair.iotooth.central;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

import cn.touchair.iotooth.configuration.CentralConfiguration;

public class IoToothCentral extends ScanCallback{
    private static final String TAG = IoToothCentral.class.getSimpleName();
    private static final int MSG_WHAT_STOP_SCAN = 0;
    private final Context mContext;
    private final CentralConfiguration mConfiguration;
    private final CentralStateListener mListener;
    private final BluetoothManager mBluetoothManager;
    private final BluetoothLeScanner mLeScanner;
    private ScanResultCallback mScanCallback;

    private final Handler mH = new Handler(Objects.requireNonNull(Looper.myLooper())) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MSG_WHAT_STOP_SCAN:
                    stopScanService();
                    break;
                default:
            }
        }
    };

    private final HashMap<String, GattCallbackImpl> mGattHandlersMap = new HashMap<>();

    @SuppressLint("MissingPermission")
    public IoToothCentral(@NonNull Context context, @NonNull CentralStateListener listener, @NonNull CentralConfiguration configuration) {
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(context);
        Objects.requireNonNull(listener);
        mContext = context;
        mListener = listener;
        mConfiguration = configuration;
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mLeScanner = mBluetoothManager.getAdapter().getBluetoothLeScanner();
        BluetoothAdapter adapter = mBluetoothManager.getAdapter();
        if (!adapter.isEnabled() && !adapter.enable()) {
            Log.d(TAG, "Bluetooth not enable.");
        }
    }

    public void connect(@NonNull BluetoothDevice remote) {
        openGatt(remote);
    }

    public void scanWithDuration(long sec, ScanResultCallback callback) {
        mScanCallback = callback;
        scanService();
        mH.sendEmptyMessageDelayed(MSG_WHAT_STOP_SCAN,
                sec);
    }

    public void disconnect(@NonNull String addr) {
        GattCallbackImpl handler = mGattHandlersMap.remove(addr);
        if (handler != null) {
            handler.close();
        }
    }

    public void disconnectAll() {
        mGattHandlersMap.forEach((address, handler) -> {
            handler.close();
        });
        mGattHandlersMap.clear();
    }

    public void send(@NonNull String address, String msg) {
        GattCallbackImpl handler = mGattHandlersMap.get(address);
        if (handler != null) {
            handler.send(msg);
        }
    }

    public void send(@NonNull String address, byte[] data) {
        GattCallbackImpl handler = mGattHandlersMap.get(address);
        if (handler != null) {
            handler.send(data);
        }
    }

    @SuppressLint("MissingPermission")
    private void scanService() {
        mScanCallback.onScanStarted();
        ArrayList<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(mConfiguration.serviceUuid))
                .build();
        filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder().build();
        mLeScanner.startScan(filters, settings, this);
    }

    @SuppressLint("MissingPermission")
    private void stopScanService() {
        mLeScanner.stopScan(this);
        mScanCallback.onScanStopped();
    }

    @SuppressLint("MissingPermission")
    private void openGatt(@NonNull BluetoothDevice device) {
        GattCallbackImpl handler = new GattCallbackImpl(mConfiguration, mListener);
        BluetoothGatt gatt = device.connectGatt(mContext, false, handler);
        if (gatt != null) {
            mGattHandlersMap.put(device.getAddress(), handler);
            mListener.onEvent(CentralState.OPENED_GATT, gatt.getDevice().getAddress());
        }
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
        super.onScanResult(callbackType, result);
        mScanCallback.onScanResult(result);
    }

    @Override
    public void onScanFailed(int errorCode) {
        super.onScanFailed(errorCode);
        Log.e(TAG, "Scan failed. errorCode=" + errorCode);
    }

}
