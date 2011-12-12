package net.tedstein.AndroSS;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.TextView;

public class DebugInfo extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String param_string = AndroSSService.getParamString(this);
        TextView board;

        switch (AndroSSService.getDeviceType(this)) {
        case GENERIC:
            setContentView(R.layout.debug_info);

            board = (TextView)findViewById(R.id.tv_board);
            board.setText(getString(R.string.board) + " " + Build.BOARD);
            if (param_string.equals("")) {
                TextView err_text = new TextView(this);
                err_text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15F);
                err_text.setText(R.string.debug_info_error);

                LinearLayout ll = (LinearLayout)findViewById(R.id.ll_debug_info);
                ll.removeAllViews();
                ll.addView(board);
                ll.addView(err_text);
                return;
            }

            TextView width = (TextView)findViewById(R.id.tv_width_value);
            TextView height = (TextView)findViewById(R.id.tv_height_value);
            TextView depth = (TextView)findViewById(R.id.tv_depth_value);
            TextView stride = (TextView)findViewById(R.id.tv_stride_value);
            TextView red = (TextView)findViewById(R.id.tv_red);
            TextView green = (TextView)findViewById(R.id.tv_green);
            TextView blue = (TextView)findViewById(R.id.tv_blue);
            TextView alpha = (TextView)findViewById(R.id.tv_alpha);

            String[] params = param_string.split(" ");
            Log.d("AndroSS", String.format("Param string has %d pieces.", params.length));
            width.setText(params[0]);
            height.setText(params[1]);
            depth.setText(params[2]);
            stride.setText(params[11]);
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

            break;
        case TEGRA_2:
            setContentView(R.layout.debug_info_tegra);

            board = (TextView)findViewById(R.id.tv_board_tegra);
            board.setText(getString(R.string.board) + " " + Build.BOARD);

            TextView fbread_string = (TextView)findViewById(R.id.tv_framebuffer_string_tegra);
            fbread_string.setText(param_string);
            break;
        }
    }
}
