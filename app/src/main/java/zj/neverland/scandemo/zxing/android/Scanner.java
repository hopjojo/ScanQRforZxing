package zj.neverland.scandemo.zxing.android;

import android.content.Context;
import android.util.TypedValue;

import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ResultParser;

public class Scanner {

    public static ParsedResult parseResult(Result rawResult) {
        if (rawResult == null) return null;
        return ResultParser.parseResult(rawResult);
    }

    public static int dp2px(Context context, float dpValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue
                , context.getResources().getDisplayMetrics());
    }

    public static int sp2px(Context context, float spValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spValue
                , context.getResources().getDisplayMetrics());
    }
}
