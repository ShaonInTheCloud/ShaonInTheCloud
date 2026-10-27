package com.safenest.teststub;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Shows the app's name so a screenshot proves which stand-in was opened. */
public final class StubActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText(getApplicationInfo().loadLabel(getPackageManager()));
        text.setTextSize(28);
        setContentView(text);
    }
}
