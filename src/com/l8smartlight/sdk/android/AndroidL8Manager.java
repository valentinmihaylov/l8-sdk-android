package com.l8smartlight.sdk.android;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.l8smartlight.sdk.android.bluetooth.AndroidBluetoothL8;
import com.l8smartlight.sdk.android.bluetooth.BluetoothClient;
import com.l8smartlight.sdk.android.bluetooth.DeviceListActivity;
import com.l8smartlight.sdk.android.bluetooth.Preferences;
import com.l8smartlight.sdk.android.rest.AndroidRESTfulL8;
import com.l8smartlight.sdk.core.BaseL8Manager;
import com.l8smartlight.sdk.core.L8;
import com.l8smartlight.sdk.core.L8Exception;

public class AndroidL8Manager extends BaseL8Manager {

	public static interface OnL8ManagerReadyListener {
		public void onL8ManagerReady(boolean success);
	}
	
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BLUETOOTH = 3;	
    
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
	
	protected Context context;
	protected Activity activity;
	
	protected Preferences preferences;
	
	protected L8 bluetoothL8;
	
	protected BluetoothAdapter bluetoothAdapter;
	protected BluetoothClient bluetoothClient;
	private boolean destroyed = false; 
	
	protected Handler bluetoothHandler = new Handler(new Handler.Callback() {
		@Override
		public boolean handleMessage(Message msg) {
            switch (msg.what) {
	            case MESSAGE_STATE_CHANGE:
	                switch (msg.arg1) {
	                	case BluetoothClient.STATE_NONE:

	                	break;
	                	case BluetoothClient.STATE_CONNECTING:

	                    break;
	                	case BluetoothClient.STATE_CONNECTED:
		                	preferences.setLastConnectedDevice(bluetoothClient.getConnectedDevice().getAddress());
		                	bluetoothL8 = new AndroidBluetoothL8(bluetoothClient);
		                	onInitialized(true);
	                    break;
	                	case BluetoothClient.STATE_FAILED:
	                		//preferences.setLastConnectedDevice(null);
	                		onInitialized(false);
	                    break;
	                }
	            break;
	            case MESSAGE_READ:
	            	if (bluetoothL8 != null) {
	            		((AndroidBluetoothL8)bluetoothL8).received(msg.arg1, (byte[])msg.obj);
	            	}
	            break;
	            case MESSAGE_DEVICE_NAME:

                break;
	            case MESSAGE_TOAST:
	            	// TODO:
	            	/*
	            	if(m_bClosing==false)
	            		Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
	            	*/
                break;
            }
            return true;
		}
	});
	
	protected OnL8ManagerReadyListener onL8ManagerReadyListener;	
	
	public void setOnL8ManagerReadyListener(OnL8ManagerReadyListener onL8ManagerReadyListener) {
		this.onL8ManagerReadyListener = onL8ManagerReadyListener;
	}

	public AndroidL8Manager(Context context, Activity activity) {
		super();
		this.context = context;
		this.activity = activity;
		this.preferences = new Preferences(context);
	}
	
	public void init() {
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			onInitialized(false);
			return;
		}
		bluetoothClient = new BluetoothClient(context, bluetoothHandler);
		
		if (!bluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			activity.startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH);
		} else {
			lookForDevices();
		}
	}
	
	public void onInitialized(boolean success) {
		if (onL8ManagerReadyListener != null) {
			onL8ManagerReadyListener.onL8ManagerReady(success);
		}
	}
	
	@Override
	public L8 reconnectDevice(String deviceId) throws L8Exception {
		AndroidRESTfulL8 l8 = new AndroidRESTfulL8();
		return l8.reconnectSimulator(deviceId);
	}
	
	@Override
	public List<L8> discoverL8s() throws L8Exception {
		List<L8> l8s = new ArrayList<L8>();
        if (!Constants.NO_L8_MODE) {
			if (bluetoothL8 != null) {
				l8s.add(bluetoothL8);
			}
        }
		if (l8s.size() == 0) {
			String lastEmulatorId = preferences.getLastConnectedEmulator();
			L8 lastEmulator = null;
			if (lastEmulatorId != null) {
				lastEmulator = reconnectDevice(lastEmulatorId);
			} 
			if (lastEmulator != null) {
				l8s.add(lastEmulator);
			} else {
				AndroidRESTfulL8 l8 = new AndroidRESTfulL8();
				l8.createSimulator();
				lastEmulatorId = l8.getID();
				preferences.setLastConnectedEmulator(lastEmulatorId);
				l8s.add(l8);
			}
		}
		return l8s;
	}
	
	//
	public List<L8> discoverL8sAndLoadEmulator() throws L8Exception {
		List<L8> l8s = new ArrayList<L8>();
        if (!Constants.NO_L8_MODE) {
			if (bluetoothL8 != null) {
				l8s.add(bluetoothL8);
			}
        }
        try
        {
			String lastEmulatorId = preferences.getLastConnectedEmulator();
			L8 lastEmulator = null;
			if (lastEmulatorId != null) {
				lastEmulator = reconnectDevice(lastEmulatorId);
			} 
			if (lastEmulator != null) {
				l8s.add(lastEmulator);
			} else {
				AndroidRESTfulL8 l8 = new AndroidRESTfulL8();
				l8.createSimulator();
				lastEmulatorId = l8.getID();
				preferences.setLastConnectedEmulator(lastEmulatorId);
				l8s.add(l8);
			}
        }catch(Exception e){
        	Log.e("L8Manager", "Error :"+e);
        	if(l8s.size()==0){
        		throw new L8Exception("No bt devices and http connection fail");
        	}
        }
		return l8s;
	}
		
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        	case REQUEST_CONNECT_DEVICE_SECURE:
	            // When DeviceListActivity returns with a device to connect
	            if (resultCode != Activity.RESULT_OK) {
	            	onInitialized(false);
	            } else {
	            //	Toast.makeText(context, "DEBERIA CONECTAR", 0).show();
	            	connectDevice(data, true);
	            }
            break;
        	case REQUEST_ENABLE_BLUETOOTH:
        		//onInitialized(resultCode == Activity.RESULT_OK);
        		if (resultCode != Activity.RESULT_OK) {
        			onInitialized(false);
        		} else {
        			lookForDevices();
        		}
        	break;
        }
    }	
    
    public void lookForDevices() {
    	if (!reconnectLastDevice()) {
    		startConnectDeviceSecure();
    	}
    }
    
	private void startConnectDeviceSecure() {
        if (!Constants.NO_L8_MODE) {
        	Intent intent = new Intent(context, DeviceListActivity.class);
        	activity.startActivityForResult(intent, REQUEST_CONNECT_DEVICE_SECURE);
        }
	}
	
	//cambiado por CMA
	public void scan(Activity activity){
		 if (!Constants.NO_L8_MODE) {
	        	Intent intent = new Intent(context, DeviceListActivity.class);
	        	activity.startActivityForResult(intent, REQUEST_CONNECT_DEVICE_SECURE);
	        }
	}
	
    private void connectDevice(Intent data, boolean secure) {
    	// Get the device MAC address
    	String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
    	// Get the BLuetoothDevice object
    	BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
    	// Attempt to connect to the device
    	bluetoothClient.connect(device, secure);
    }

    private boolean reconnectLastDevice() {
        // Get the device MAC address
    	String address = preferences.getLastConnectedDevice();
        if (address != null && bluetoothAdapter != null) {
            // Get the BLuetoothDevice object
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
            // Attempt to connect to the device
            bluetoothClient.connect(device, true);
            return true;
        }
        return false;
    }
    
    public void onDestroy() {
    	destroyed = true;
        if (bluetoothClient != null) bluetoothClient.stop();
    }

	public boolean isDestroyed() {
		return destroyed;
	}
    
}
