package com.teletalker.app.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;

public class BlurImageView extends androidx.appcompat.widget.AppCompatImageView {
    private RenderScript mRenderScript;
    private Allocation mInAllocation;
    private Allocation mOutAllocation;
    private ScriptIntrinsicBlur mScriptBlur;

    public BlurImageView(Context context) {
        super(context);
        init();
    }

    public BlurImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlurImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mRenderScript = RenderScript.create(getContext());
        mScriptBlur = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
        mScriptBlur.setRadius(25);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mInAllocation.destroy();
        mOutAllocation.destroy();
        mScriptBlur.destroy();
        mRenderScript.destroy();
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        mInAllocation = Allocation.createFromBitmap(mRenderScript, bm);
        mOutAllocation = Allocation.createTyped(mRenderScript, mInAllocation.getType());
        mScriptBlur.setInput(mInAllocation);
        mScriptBlur.forEach(mOutAllocation);
        Bitmap output = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), bm.getConfig());
        mOutAllocation.copyTo(output);
        super.setImageBitmap(output);
    }
}
