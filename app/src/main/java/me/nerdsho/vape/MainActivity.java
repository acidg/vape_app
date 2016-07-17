package me.nerdsho.vape;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    /**
     * The minimum temperature to be settable.
     */
    private static final int MINIMUM_TEMPERATURE = 30;

    /**
     * The maximum temperature to be settable.
     */
    private static final int MAXIMUM_TEMPERATURE = 330;

    /**
     * The initial time in milliseconds, after which the first button pressed event is fired.
     */
    private static final int INITIAL_INTERVAL = 400;

    /**
     * The time in milliseconds, after which each subsequent button pressed event is fired.
     */
    private static final int SUBSEQUENT_INTERVAL = 50;

    /**
     * The time in milliseconds, after which each subsequent button pressed event is fired.
     */
    private static String DESIRED_TEMPERATURE_BUNDLE_KEY = "vape_desired_temperature";

    /**
     * The TextView used to display the desired temperature.
     */
    private TextView tvDesiredTemperature;

    /**
     * The up button to increase the temperature.
     */
    ImageView btnTempUp;

    /**
     * The down button to decrease the temperature.
     */
    ImageView btnTempDown;

    /**
     * The desired temperature.
     */
    private int desiredTemperature = MINIMUM_TEMPERATURE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            desiredTemperature = savedInstanceState.getInt(DESIRED_TEMPERATURE_BUNDLE_KEY);
        }

        tvDesiredTemperature = (TextView) findViewById(R.id.tvDesiredTemperature);
        tvDesiredTemperature.setText(Integer.toString(desiredTemperature));

        btnTempUp = (ImageView) findViewById(R.id.btnTempUp);
        btnTempDown = (ImageView) findViewById(R.id.btnTempDown);

        btnTempUp.setOnTouchListener(new RepeatListener(INITIAL_INTERVAL, SUBSEQUENT_INTERVAL, new OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.increaseTemp();
            }
        }));

        btnTempDown.setOnTouchListener(new RepeatListener(INITIAL_INTERVAL, SUBSEQUENT_INTERVAL, new OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.lowerTemp();
            }
        }));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(DESIRED_TEMPERATURE_BUNDLE_KEY, desiredTemperature);
    }

    /**
     * Increases the desired temperature of the Vape.
     */
    private void increaseTemp() {
        if (desiredTemperature < MAXIMUM_TEMPERATURE) {
            desiredTemperature++;
        }

        tvDesiredTemperature.setText(Integer.toString(desiredTemperature));
    }

    /**
     * Lowers the desired temperature of the Vape.
     */
    private void lowerTemp() {
        if (desiredTemperature > MINIMUM_TEMPERATURE) {
            desiredTemperature--;
        }

        tvDesiredTemperature.setText(Integer.toString(desiredTemperature));
    }
}
