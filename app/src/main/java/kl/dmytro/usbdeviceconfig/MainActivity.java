package kl.dmytro.usbdeviceconfig;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends Activity {

    private TextView mResult;
    private UsbDevice usbDevice;
    private Integer vendorId, productId;
    private UsbIso usbIso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mResult = (TextView)findViewById(R.id.textResult);
        getResourceValues();
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver, filter);
        if(checkDeviceConnect()){
            getDeviceInfo();
        }else{
            mResult.setText("Device is disabled.");
        }
    }

    public void DoTransfer(View view) {
        if(usbDevice==null){
            Toast.makeText(this,
                    "Device is disabled.", Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        UsbInterface intf = usbDevice.getInterface(5);
        UsbEndpoint endpoint = intf.getEndpoint(0);
        UsbManager mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection connection = mUsbManager.openDevice(usbDevice);
        /*boolean resClaim = connection.claimInterface(intf, true);
       if(resClaim){
            Toast.makeText(this,
                    "Inteface accessed!!!" + " id = " + intf.getId() + "alt = " + intf.getAlternateSetting() , Toast.LENGTH_SHORT)
                    .show();
        }*/
        int poolReq = 10;
        usbIso = new UsbIso(connection.getFileDescriptor(),1,640);
/*
... set streaming parameters via control channel (SET_CUR VS_COMMIT_CONTROL, etc.) ...
   usbIso.preallocateRequests(n);
   usbIso.setInterface(interfaceId, altSetting);       // enable streaming
   for (int i = 0; i < n; i++) {                       // submit initial transfer requests
      Request req = usbIso.getRequest();
      req.initialize(endpointAddr);
      req.submit(); }
   while (...) {                                       // streaming loop
      Request req = usbIso.reapRequest(true);          // wait for next request completion
      .. process received data ...
      req.initialize(endpointAddr);                    // re-use the request
      req.submit(); }                                  // re-submit the request
   usbIso.setInterface(interfaceId, 0);                // disable streaming
   usbIso.flushRequests();                             // remove pending requests
 */



        usbIso.preallocateRequests(poolReq);
        try {
            usbIso.setInterface(intf.getId(), intf.getAlternateSetting());
        } catch (IOException e) {
            Toast.makeText(this,
                    "Error: " + e.toString(), Toast.LENGTH_LONG)
                    .show();
            return;
        }

        for (int i = 0; i < poolReq; i++) {                       // submit initial transfer requests
            UsbIso.Request req = usbIso.getRequest();
            req.initialize(endpoint.getAddress());
            try {
                req.submit();
            } catch (IOException e) {
                Toast.makeText(this,
                        "Error: " + e.toString(), Toast.LENGTH_LONG)
                        .show();
                return;
            }

        }

        UsbIso.Request req = null;
        String result = "";
        for(int i=0; i<poolReq*2; i++) {                                       // streaming loop
            try {
                req = usbIso.reapRequest(true);  // wait for next request completion
            } catch (IOException e) {
                Toast.makeText(this,
                        "Error: " + e.toString(), Toast.LENGTH_LONG)
                        .show();
                return;
            }
            //.. process received data ...

            result = result+ "Cou: " + req.getPacketCount() + ", Len = " + req.getPacketActualLength(0) + ";";

            //int cou = req.getPacketActualLength(0);
            //mResult.setText(packCou);
            /*
            byte [] buffer = new byte[cou];
            req.getPacketData(0, buffer, cou);*/

            req.initialize(endpoint.getAddress());                    // re-use the request
            try {
                req.submit();
            } catch (IOException e) {
                Toast.makeText(this,
                        "Error: " + e.toString(), Toast.LENGTH_LONG)
                        .show();
                return;
            }
        }

        try {
            usbIso.setInterface(intf.getId(), 0);
        } catch (IOException e) {
            Toast.makeText(this,
                    "Error: " + e.toString(), Toast.LENGTH_LONG)
                    .show();
            return;
        }

        try {
            usbIso.dispose();
            usbIso=null;
        } catch (IOException e) {
            Toast.makeText(this,
                    "Error: " + e.toString(), Toast.LENGTH_LONG)
                    .show();
            return;
        }

        connection.releaseInterface(intf);
        connection.close();
        mResult.setText(result);
        Toast.makeText(this,
                "Complete!", Toast.LENGTH_SHORT)
                .show();
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action) && usbDevice != null)
        {
            UsbDevice detachedDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(detachedDevice !=null && detachedDevice.getVendorId()== vendorId && detachedDevice.getProductId() == productId) {
                usbDevice = null;
                mResult.setText("Device is disabled.");
            }
        }
        }
    };

    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        Intent intent = getIntent();
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action))
        {
            usbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            getDeviceInfo();
        }
    }

    private void getResourceValues(){
        Resources res = getResources();
        XmlResourceParser parser  = res.getXml(R.xml.device_filter);

        try {
            while (parser.getEventType()!= XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG
                        && parser.getName().equals("usb-device")) {
                    vendorId = Integer.parseInt(parser.getAttributeValue(null, "vendor-id"));
                    productId = Integer.parseInt(parser.getAttributeValue(null, "product-id"));
                }
                parser.next();
            }
        } catch (Throwable t) {
            Toast.makeText(this,
                    "Error: " + t.toString(), Toast.LENGTH_LONG)
                    .show();
        }
        parser.close();
    }

    public void getDeviceInfo() {
        if (usbDevice == null)
            return;
        String result="";
        result+= "ManufacturerName = " + usbDevice.getManufacturerName()+ ";\tProductName = " + usbDevice.getProductName()+ ";\tDeviceClass = " + usbDevice.getDeviceClass();
        Integer intfCount = usbDevice.getInterfaceCount();
        result+="\tCouInterface = " + intfCount.toString()+ System.lineSeparator();
        for(int i =0; i< intfCount; i++){
            result+="\tInterface #" + i + "\t";
            UsbInterface intf = usbDevice.getInterface(i);
            Integer enptCount = intf.getEndpointCount();
            result+="CouEndpoints = " + enptCount.toString()+ "; InterfaceClass = " + intf.getInterfaceClass() + System.lineSeparator();
            for(int e =0; e< enptCount; e++){
                result+="\t\tEndpoint #" + e +": ";
                UsbEndpoint endpoint = intf.getEndpoint(e);
                result+="Type = " + getEndpointType(endpoint)
                    + "; Direction = " + getEndpointDirection(endpoint)
                    + "; MaxPacketSize = " + getEndpointMaxPacketSize(endpoint)
                    + System.lineSeparator();
            }
        }
         mResult.setText(result);
    }

    private boolean checkDeviceConnect() {
        if(usbDevice != null)
            return false;
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            if(device.getVendorId()== vendorId && device.getProductId() == productId){
                usbDevice = device;
                return true;
            }
        }
       return false;
    }

    private String getEndpointType(UsbEndpoint endpoint){
        String result="";
        int type = endpoint.getType();
        switch(type){
            case UsbConstants.USB_ENDPOINT_XFER_CONTROL:
                result = "Control";
                break;
            case UsbConstants.USB_ENDPOINT_XFER_ISOC:
                result = "Isochronous";
                break;
            case UsbConstants.USB_ENDPOINT_XFER_BULK:
                result = "Bulk";
                break;
            case  UsbConstants.USB_ENDPOINT_XFER_INT:
                result = "Interrupt";
                break;
            default:
                result = "Unknown";
        }
        return result;
    }

    private String getEndpointDirection(UsbEndpoint endpoint){
        String result="";
        int direction = endpoint.getDirection();
        switch(direction){
            case UsbConstants.USB_DIR_IN:
                result = "IN";
                break;
            case UsbConstants.USB_DIR_OUT:
                result = "OUT";
                break;
            default:
                result = "Unknown";
        }
        return result;
    }

    private String getEndpointMaxPacketSize(UsbEndpoint endpoint){
        Integer maxPacketSize = endpoint.getMaxPacketSize();
        return maxPacketSize.toString();
    }


}
