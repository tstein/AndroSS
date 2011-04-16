package net.tedstein.AndroSS;

import android.app.Activity;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.TextView;

public class DebugInfo extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String param_string = AndroSSService.getParamString();
        if (param_string.equals("")) {
            TextView err_text = new TextView(this);
            err_text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15F);
            err_text.setText(R.string.empty_param_error);
            setContentView(err_text.getId());
            return;
        }

        setContentView(R.layout.debug_info);

        TextView width = (TextView)findViewById(R.id.tv_width_value);
        TextView height = (TextView)findViewById(R.id.tv_height_value);
        TextView depth = (TextView)findViewById(R.id.tv_depth_value);
        TextView red = (TextView)findViewById(R.id.tv_red);
        TextView green = (TextView)findViewById(R.id.tv_green);
        TextView blue = (TextView)findViewById(R.id.tv_blue);
        TextView alpha = (TextView)findViewById(R.id.tv_alpha);

        String[] params = param_string.split(" ");
        width.setText(params[0]);
        height.setText(params[1]);
        depth.setText(params[2]);
        red.setText(getString(R.string.color_param,
                getString(R.string.red) + ":\t",
                params[8],
                params[7]));
        green.setText(getString(R.string.color_param,
                getString(R.string.green) + ":",
                params[6],
                params[5]));
        blue.setText(getString(R.string.color_param,
                getString(R.string.blue) + ":\t",
                params[4],
                params[3]));
        alpha.setText(getString(R.string.color_param,
                getString(R.string.alpha) + ":",
                params[10],
                params[9]));
    }
}
