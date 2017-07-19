package me.nerdsho.vape;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.FloatRange;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYPlot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    /**
     * The minimum temperature to be settable.
     */
    private static final int MINIMUM_TEMPERATURE = 80;

    /**
     * The maximum temperature to be settable.
     */
    private static final int MAXIMUM_TEMPERATURE = 230;

    /**
     * The time in milliseconds, after which each subsequent button pressed event is fired.
     */
    private static final String DESIRED_TEMPERATURE_BUNDLE_KEY = "vape_desired_temperature";

    /**
     * The SPP UUID for secure rfcomm sockets.
     */
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * Number of values to sample for the plot.
     */
    private static final int HISTORY_SIZE = 100;

    /**
     * Maximum value for the plot.
     */
    private static final int MAXIMUM_HEATING_VALUE = 255;

    /**
     * Series containing temperature data for the plot.
     */
    private SimpleXYSeries temperatureSeries;

    /**
     * Series containing heating data for the plot.
     */
    private SimpleXYSeries heatingSeries;

    /**
     * The temperature plot.
     */
    private XYPlot temperaturePlot;

    /**
     * The TextView used to display the current temperature.
     */
    private TextView tvCurrentTemperature;

    /**
     * The TextView used to display the current heating power.
     */
    private TextView tvCurrentHeat;

    /**
     * The TextView used to display the desired temperature.
     */
    private TextView tvDesiredTemperature;

    /**
     * The TextView used to display the battery voltage.
     */
    private TextView tvBatteryVoltage;

    /**
     * The TextView used to display the battery percentage.
     */
    private TextView tvBatteryPercentage;

    /**
     * Textfield used for changing the P value
     */
    private EditText tfdPidP;

    /**
     * Textfield used for changing the I value
     */
    private EditText tfdPidI;

    /**
     * Textfield used for changing the D value
     */
    private EditText tfdPidD;

    /**
     * The button used to send the PID values to the device
     */
    private Button btnSet;

    /**
     * Bluetooth socket to communicate with the Vape.
     */
    private BluetoothSocket socket;

    /**
     * The desired temperature.
     */
    private int desiredTemperature = MINIMUM_TEMPERATURE;

    /**
     * Whether the desired temp should be updated, with the next data from the Vape.
     */
    private boolean updateDesiredTemp = true;

    /**
     * The background thread used to update the plot.
     */
    private Thread plotUpdater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            desiredTemperature = savedInstanceState.getInt(DESIRED_TEMPERATURE_BUNDLE_KEY);
        }

        tvCurrentTemperature = (TextView) findViewById(R.id.tvCurrentTemperature);
        tvCurrentHeat = (TextView) findViewById(R.id.tvCurrentHeat);
        tvDesiredTemperature = (TextView) findViewById(R.id.tvDesiredTemperature);
        tvBatteryVoltage = (TextView) findViewById(R.id.tvBatteryVoltage);
        tvBatteryPercentage = (TextView) findViewById(R.id.tvBatteryPercentage);
        tfdPidP = (EditText) findViewById(R.id.tfdPidP);
        tfdPidI = (EditText) findViewById(R.id.tfdPidI);
        tfdPidD = (EditText) findViewById(R.id.tfdPidD);

        initButtons();
        initPlot();
        initBluetoothConnection();
        loadValues();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(DESIRED_TEMPERATURE_BUNDLE_KEY, desiredTemperature);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    /**
     * Sets up the bluetooth connection
     */
    private void initBluetoothConnection() {
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("20:15:06:01:08:72");
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (socket.isConnected()) {
            Toast.makeText(MainActivity.this, "Connected!", Toast.LENGTH_SHORT).show();
            startPlotUpdater();
        } else {
            Toast.makeText(MainActivity.this, "Connection could not be established", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Sets up the button listeners.
     */
    private void initButtons() {
        ImageView btnTempUp = (ImageView) findViewById(R.id.btnTempUp);
        ImageView btnTempDown = (ImageView) findViewById(R.id.btnTempDown);
        Button btnSet = (Button) findViewById(R.id.btnSet);

        btnTempUp.setOnTouchListener(new TemperatureButtonListener(new TemperatureButtonListener.TemperatureButtonCallback() {
            @Override
            public void onTick() {
                updateDesiredTemp = false;
                MainActivity.this.increaseTemp();
            }

            @Override
            public void onFinal() {
                MainActivity.this.sendTemperature();
            }
        }));

        btnTempDown.setOnTouchListener(new TemperatureButtonListener(new TemperatureButtonListener.TemperatureButtonCallback() {
            @Override
            public void onTick() {
                updateDesiredTemp = false;
                MainActivity.this.lowerTemp();
            }

            @Override
            public void onFinal() {
                MainActivity.this.sendTemperature();
            }
        }));

        btnSet.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.sendPID();
            }
        });
    }

    /**
     * Sets up the plot and registers handlers for plot updates.
     */
    private void initPlot() {
        temperaturePlot = (XYPlot) findViewById(R.id.plotTemperature);

        temperatureSeries = new SimpleXYSeries(getString(R.string.plot_temperature));
        heatingSeries = new SimpleXYSeries(getString(R.string.plot_heating));

        temperatureSeries.useImplicitXVals();
        heatingSeries.useImplicitXVals();

        temperaturePlot.getGraphWidget().setDomainValueFormat(new DecimalFormat("0"));
        temperaturePlot.getGraphWidget().setRangeValueFormat(new DecimalFormat("0"));
        temperaturePlot.setRangeBoundaries(0, MAXIMUM_HEATING_VALUE, BoundaryMode.FIXED);
        temperaturePlot.setDomainBoundaries(0, HISTORY_SIZE + 10, BoundaryMode.FIXED);
        temperaturePlot.addSeries(temperatureSeries, new LineAndPointFormatter(Color.BLUE, null, null, null));
        temperaturePlot.addSeries(heatingSeries, new LineAndPointFormatter(Color.RED, null, null, null));
    }

    /**
     * Loads the values for PID and chosen temperature from the device
     */
    private void loadValues() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket.isConnected()) {
                    try {
                        System.out.println("Loading values");
                        socket.getOutputStream().write(new byte[]{'t', '?'});
                        Thread.sleep(300);
                        socket.getOutputStream().write(new byte[]{'p', '?'});
                        Thread.sleep(300);
                    } catch (IOException e) {
                        System.out.println("Could not send temp to Vape " + e.getMessage());
                        Toast.makeText(MainActivity.this, "Could not send temp to Vape " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    } catch (InterruptedException e) {
                        // ignore for now
                    }
                }

            }
        }).start();
    }

    /**
     * Starts a background thread listening for new data on the bluetooth connection and updating the plot.
     */
    private void startPlotUpdater() {
        this.plotUpdater = new Thread(new Runnable() {
            public void run() {
                BufferedReader connection;
                try {
                    connection = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this, "Could not get Input Stream:\n" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }

                while (!Thread.interrupted() && socket.isConnected()) {
                    final String[] values;
                    final int savedTemperature;

                    try {
                        String input = connection.readLine();
                        System.out.println("Got: " + input);

                        values = input.split(";");
                        switch (values[0]) {
                            case "s":
                                final float temp = Float.parseFloat(values[1]);
                                final float heat = Float.parseFloat(values[2]);

                                if (temperatureSeries.size() > HISTORY_SIZE) {
                                    temperatureSeries.removeFirst();
                                    heatingSeries.removeFirst();
                                }

                                temperatureSeries.addLast(null, temp);
                                heatingSeries.addLast(null, heat);

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        temperaturePlot.redraw();
                                        tvCurrentTemperature.setText(values[1]);
                                        tvCurrentHeat.setText(String.valueOf((int) (Float.parseFloat(values[2]) / MAXIMUM_HEATING_VALUE * 100)));
                                        tvBatteryVoltage.setText(values[3]);
                                        tvBatteryPercentage.setText(values[4]);
                                    }
                                });

                                continue;
                            case "p":
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tfdPidP.setText(values[1]);
                                        tfdPidI.setText(values[2]);
                                        tfdPidD.setText(values[3]);
                                    }
                                });
                                continue;
                            case "t":
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvDesiredTemperature.setText(values[1]);
                                    }
                                });
                                continue;
                            default:
                                // ignore
                        }
                    } catch (IOException | NumberFormatException e) {
                        // Could not parse value, ignore
                    }
                }
            }
        });

        this.plotUpdater.start();
    }

    /**
     * Increases the desired temperature of the Vape.
     */

    private void increaseTemp() {
        if (desiredTemperature < MAXIMUM_TEMPERATURE) {
            desiredTemperature++;
        } else {
            desiredTemperature = MAXIMUM_TEMPERATURE;
        }

        tvDesiredTemperature.setText(Integer.toString(desiredTemperature));
    }

    /**
     * Lowers the desired temperature of the Vape.
     */
    private void lowerTemp() {
        if (desiredTemperature > MINIMUM_TEMPERATURE) {
            desiredTemperature--;
        } else {
            desiredTemperature = MINIMUM_TEMPERATURE;
        }

        tvDesiredTemperature.setText(Integer.toString(desiredTemperature));
    }

    /**
     * Sends the new temperature to the Vape.
     */
    private void sendTemperature() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket.isConnected()) {
                    try {
                        System.out.println("Sending temp: " + desiredTemperature);
                        socket.getOutputStream().write(new byte[]{'t', '=', (byte) desiredTemperature});
                        try {
                            Thread.sleep(300); // why?
                        } catch (InterruptedException e) {
                            // ignore for now
                        }
                    } catch (IOException e) {
                        System.out.println("Could not send temp to Vape " + e.getMessage());
                    }
                }
            }
        }).start();
    }

    /**
     * Sends the new PID values to the Vape.
     */
    private void sendPID() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (socket.isConnected()) {
                    try {
                        Double p = Double.parseDouble(tfdPidP.getText().toString());
                        Double i = Double.parseDouble(tfdPidI.getText().toString());
                        Double d = Double.parseDouble(tfdPidD.getText().toString());

                        if (p < 0 || p > 25.5 || i < 0 || i > 25.5 || d < 0 || d > 25.5) {
                            Toast.makeText(MainActivity.this, "PID values can only be between 0 and 25.5", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        System.out.println("Sending PID: " + p + ", " + i + ", " + d);
                        socket.getOutputStream().write(new byte[]{'p', '=', (byte) Math.round(p * 10), (byte) Math.round(i * 10), (byte) Math.round(d * 10)});
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            // ingore for now
                        }
                    } catch (IOException e) {
                        System.out.println("Could not send temp to Vape " + e.getMessage());
                    }
                }
            }
        }).start();
    }
}
