package com.example.ericb.bluetoothobd;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import android.widget.TextView;

import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class BTOBDCommunication extends Thread {

    private BluetoothDevice device;
    private final TextView info;
    private BluetoothSocket socket = null;
    //Using "well-known" bluetooth device Universal Unique IDentifier(UUID) from https://www.bluetooth.com/specifications/assigned-numbers/service-discovery
    //This UUID is overwritten in the getBTDevice() method though it will likely be the same.
    private UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");




    /*
     * Constructor
     * Gets the default device from the bluetooth adapter upon construction
     */
    public BTOBDCommunication(BluetoothAdapter ba, TextView info)
    {
        this.info = info;
        this.device = this.getBTDevice(ba);

        //Cancel discovery after getting a bluetooth device since discovery for the bluetooth adapter is intensive
        ba.cancelDiscovery();
    }





    /*
     * Run is the method called when "start()" is called in the driver for this thread.
     *
     * Run() attempts to create then connect to a BluetoothSocket.
     * Once connected it then uses several OBD commands to set communication parameters.
     * After the parameters are set it attempts to get information from the vehicle's sensors.
     */

    public void run() {

        while(socket == null) {
            try {
                info.append("Attempting to create socket...\n");
                Thread.sleep(2000); //wait two seconds before retrying if socket creation fails
                socket = device.createInsecureRfcommSocketToServiceRecord(BT_UUID);
            } catch (Exception e) {
                Log.e("Exception", e.toString());
            }
        }

        while (true) { //Keep attempting to connect if it fails
            try {
                info.append("Attempting to connect...\n");
                Thread.sleep(2000); //wait two seconds before retrying if socket connection fails
                socket.connect();
                info.append("Connected" + "\n");
                break; //finished, no need to loop due to exceptions
            }
            catch (Exception e) {
                Log.e("Exception", e.toString());
            }
        }

        try{
            //The following lines are from using the open source OBD communication library "com.github.pires.obd"
            //From what I understood of the library, it handles race conditions and will wait for messages before sending/receiving
            new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
            new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
            new TimeoutCommand(10).run(socket.getInputStream(), socket.getOutputStream());
            new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());

            RPMCommand engineRpmCommand = new RPMCommand();
            SpeedCommand speedCommand = new SpeedCommand();
            FuelLevelCommand fuelCommand = new FuelLevelCommand();

            while (!Thread.currentThread().isInterrupted()) {
                engineRpmCommand.run(socket.getInputStream(), socket.getOutputStream());
                info.append( "RPM: " + engineRpmCommand.getFormattedResult());
                speedCommand.run(socket.getInputStream(), socket.getOutputStream());
                info.append( "Speed: " + speedCommand.getFormattedResult());
                fuelCommand.run(socket.getInputStream(), socket.getOutputStream());
                info.append( "Fuel: " + fuelCommand.getFormattedResult());



                break;//Just show the RPM, speed, and fuel level once instead of continually.
                // Note we could just log the data here and continually loop since the while goes until thread is interrupted.
            }

            socket.close(); // close the socket after we're done

            } catch (Exception e) {
                Log.e("Exception", e.toString());
            }


    }





    /*
     * This method returns the last paired device that an adapter has, assuming bluetooth is now turned on and there are paired devices to choose from.
     */
    private BluetoothDevice getBTDevice(BluetoothAdapter ba)
    {
        info.append("Getting bluetooth device...\n");
        String deviceHardwareAddress;
        String deviceName;
        BluetoothDevice temp = null;
        Set<BluetoothDevice> pairedDevices = ba.getBondedDevices(); //getBondedDevices returns a set

        if (pairedDevices.size() > 0) // If there are paired devices, get the name and address of each paired device.
        {
            for (BluetoothDevice device : pairedDevices)
            {
                deviceName = device.getName();
                deviceHardwareAddress = device.getAddress(); // MAC address
                temp = device;
            }
            info.append("Using device: " + temp.getName() + "\nMAC:" + temp.getAddress() +"\n");
            info.append("UUID: " + temp.getUuids()[0].toString() +"\n");

            BT_UUID = temp.getUuids()[0].getUuid(); //Set universal unique identifier according to device
        }
        else// No paired devices
        {
            info.append("There are no paired devices to select\n");
        }

        //Return the device selected
        return temp;
    }




    /*
     * The below two methods "readRawData()" and "sendCommand(String cmd)" are both unused
     * and purely left here for informational purposes or if the OBD library becomes unusable in the future
     */

    private InputStream in;
    private OutputStream out;

    private String readRawData() throws IOException {
        byte b = 0;
        StringBuilder res = new StringBuilder();

        // read until '>' arrives OR end of stream reached
        char c;
        // -1 if the end of the stream is reached
        while (((b = (byte) in.read()) > -1)) {
            c = (char) b;
            if (c == '>') // read until '>' arrives
            {
                break;
            }
            res.append(c);
        }
        return res.toString();
    }

    private void sendCommand(String cmd) throws IOException, InterruptedException {
        // write to OutputStream (i.e.: a BluetoothSocket) with an added carriage return
        out.write((cmd + "\r").getBytes());
        out.flush();
        //thread sleep for response delay here
    }


}
