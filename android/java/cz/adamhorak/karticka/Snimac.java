package cz.adamhorak.karticka;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Celoobrazovkový skener kódu z kamery. Vrací extra "format" a "text", při chybě "chyba". */
public class Snimac extends Activity implements TextureView.SurfaceTextureListener {
    private TextureView nahled;
    private TextView svetloTl;
    private CameraDevice kamera;
    private CameraCaptureSession relace;
    private CaptureRequest.Builder pozadavek;
    private ImageReader ctecka;
    private HandlerThread kVlakno, dVlakno;
    private Handler kh, dh;
    private Size velNahledu, velAnalyzy;
    private int orientace = 90;
    private boolean svetlo, maSvetlo, zadano;
    private float zoom = 1f;
    private volatile boolean hotovo;
    private int cisloSnimku;
    private final MultiFormatReader reader = Kod.ctecka(false);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout koren = new FrameLayout(this);
        koren.setBackgroundColor(Color.BLACK);
        nahled = new TextureView(this);
        koren.addView(nahled, new FrameLayout.LayoutParams(-1, -1));
        koren.addView(new Ramecek(this), new FrameLayout.LayoutParams(-1, -1));

        TextView zavrit = tlacitko("Zavřít");
        zavrit.setOnClickListener(v -> konec(null));
        FrameLayout.LayoutParams lz = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lz.setMargins(dp(16), dp(28), 0, 0);
        koren.addView(zavrit, lz);

        svetloTl = tlacitko("Světlo");
        svetloTl.setVisibility(View.GONE);
        svetloTl.setOnClickListener(v -> { svetlo = !svetlo; svetloTl.setText(svetlo ? "Zhasnout" : "Světlo"); opakuj(); });
        FrameLayout.LayoutParams ls = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        ls.setMargins(0, 0, 0, dp(48));
        koren.addView(svetloTl, ls);

        setContentView(koren);
        nahled.setSurfaceTextureListener(this);
    }

    private TextView tlacitko(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(Color.WHITE);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        v.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x66000000);
        g.setCornerRadius(dp(22));
        v.setBackground(g);
        return v;
    }

    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }

    @Override
    protected void onResume() {
        super.onResume();
        kVlakno = new HandlerThread("kamera"); kVlakno.start(); kh = new Handler(kVlakno.getLooper());
        dVlakno = new HandlerThread("cteni"); dVlakno.start(); dh = new Handler(dVlakno.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (!zadano) { zadano = true; requestPermissions(new String[]{Manifest.permission.CAMERA}, 1); }
            return;
        }
        if (nahled.isAvailable()) otevri();
    }

    @Override
    protected void onPause() {
        zavri();
        if (kVlakno != null) kVlakno.quitSafely();
        if (dVlakno != null) dVlakno.quitSafely();
        kVlakno = dVlakno = null;
        kh = dh = null;
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int kod, String[] p, int[] v) {
        if (v.length > 0 && v[0] == PackageManager.PERMISSION_GRANTED) {
            if (nahled.isAvailable()) otevri();
        } else {
            konec("kamera");
        }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { otevri(); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) { uprav(w, h); }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}

    private static Size vyber(Size[] moznosti, float pomer, int maxSirka) {
        Size nej = null;
        for (Size s : moznosti) {
            float p = s.getWidth() / (float) s.getHeight();
            if (Math.abs(p - pomer) > 0.02f || s.getWidth() > maxSirka) continue;
            if (nej == null || s.getWidth() * s.getHeight() > nej.getWidth() * nej.getHeight()) nej = s;
        }
        return nej;
    }

    private void otevri() {
        if (kamera != null || kh == null || hotovo) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            CameraCharacteristics ch = null;
            for (String c : cm.getCameraIdList()) {
                CameraCharacteristics x = cm.getCameraCharacteristics(c);
                Integer f = x.get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == CameraCharacteristics.LENS_FACING_BACK) { id = c; ch = x; break; }
                if (id == null) { id = c; ch = x; }
            }
            if (id == null) { konec("kamera"); return; }
            Integer o = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            orientace = o == null ? 90 : o;
            Boolean blesk = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            maSvetlo = blesk != null && blesk;
            if (Build.VERSION.SDK_INT >= 30) {
                Range<Float> r = ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                // lehký zoom: kód se dá držet dál od čočky a pořád je dost velký
                if (r != null && r.getUpper() >= 1.5f) zoom = 1.5f;
            }
            StreamConfigurationMap mapa = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] yuv = mapa.getOutputSizes(ImageFormat.YUV_420_888);
            Size[] nah = mapa.getOutputSizes(SurfaceTexture.class);
            float pomer = 4f / 3f;
            velAnalyzy = vyber(yuv, pomer, 1600);
            velNahledu = vyber(nah, pomer, 1920);
            if (velAnalyzy == null || velNahledu == null) {
                pomer = 16f / 9f;
                velAnalyzy = vyber(yuv, pomer, 1920);
                velNahledu = vyber(nah, pomer, 1920);
            }
            if (velAnalyzy == null) velAnalyzy = yuv[0];
            if (velNahledu == null) velNahledu = nah[0];

            ctecka = ImageReader.newInstance(velAnalyzy.getWidth(), velAnalyzy.getHeight(), ImageFormat.YUV_420_888, 2);
            ctecka.setOnImageAvailableListener(this::snimek, dh);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice c) { kamera = c; spust(); }
                @Override public void onDisconnected(CameraDevice c) { c.close(); kamera = null; }
                @Override public void onError(CameraDevice c, int e) { c.close(); kamera = null; runOnUiThread(() -> konec("kamera")); }
            }, kh);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            konec("kamera");
        }
    }

    private void spust() {
        try {
            SurfaceTexture st = nahled.getSurfaceTexture();
            if (st == null || kamera == null) return;
            st.setDefaultBufferSize(velNahledu.getWidth(), velNahledu.getHeight());
            Surface s = new Surface(st);
            pozadavek = kamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            pozadavek.addTarget(s);
            pozadavek.addTarget(ctecka.getSurface());
            pozadavek.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            pozadavek.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            if (Build.VERSION.SDK_INT >= 30 && zoom > 1f) pozadavek.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom);
            kamera.createCaptureSession(Arrays.asList(s, ctecka.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession r) {
                    if (kamera == null) return;
                    relace = r;
                    opakuj();
                    runOnUiThread(() -> {
                        uprav(nahled.getWidth(), nahled.getHeight());
                        if (maSvetlo) svetloTl.setVisibility(View.VISIBLE);
                    });
                }
                @Override public void onConfigureFailed(CameraCaptureSession r) { runOnUiThread(() -> konec("kamera")); }
            }, kh);
        } catch (CameraAccessException | IllegalStateException e) {
            runOnUiThread(() -> konec("kamera"));
        }
    }

    private void opakuj() {
        if (relace == null || pozadavek == null) return;
        try {
            pozadavek.set(CaptureRequest.FLASH_MODE, svetlo ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            relace.setRepeatingRequest(pozadavek.build(), null, kh);
        } catch (CameraAccessException | IllegalStateException e) {
            // relace se mezitím zavřela
        }
    }

    /** Náhled vyplní obrazovku bez deformace (ořízne přesah), jako systémový fotoaparát. */
    private void uprav(int vw, int vh) {
        if (velNahledu == null || vw == 0 || vh == 0) return;
        boolean nastojato = orientace == 90 || orientace == 270;
        float ac = nastojato ? velNahledu.getHeight() / (float) velNahledu.getWidth()
                : velNahledu.getWidth() / (float) velNahledu.getHeight();
        float av = vw / (float) vh;
        Matrix m = new Matrix();
        if (av < ac) m.setScale(ac / av, 1f, vw / 2f, vh / 2f);
        else m.setScale(1f, av / ac, vw / 2f, vh / 2f);
        nahled.setTransform(m);
    }

    private void snimek(ImageReader r) {
        Image img;
        try { img = r.acquireLatestImage(); } catch (IllegalStateException e) { return; }
        if (img == null) return;
        if (hotovo) { img.close(); return; }
        int w = img.getWidth(), h = img.getHeight();
        byte[] d = new byte[w * h];
        try {
            Image.Plane y = img.getPlanes()[0];
            ByteBuffer buf = y.getBuffer();
            int rs = y.getRowStride();
            for (int i = 0; i < h; i++) {
                buf.position(i * rs);
                buf.get(d, i * w, w);
            }
        } catch (RuntimeException e) {
            img.close();
            return;
        }
        img.close();

        // Na výšku vidí uživatel snímek otočený o orientaci senzoru; čárové kódy čte ZXing jen po řádcích.
        Result v;
        if (orientace == 90 || orientace == 270) {
            byte[] o = Kod.otoc(d, w, h);
            if (orientace == 270) o = Kod.otoc(Kod.otoc(o, h, w), w, h);
            v = Kod.precti(reader, new PlanarYUVLuminanceSource(o, h, w, 0, 0, h, w, false));
        } else {
            v = Kod.precti(reader, new PlanarYUVLuminanceSource(d, w, h, 0, 0, w, h, false));
        }
        if (v == null && (cisloSnimku++ & 1) == 1) {
            v = Kod.precti(reader, new PlanarYUVLuminanceSource(d, w, h, 0, 0, w, h, false));
        }
        if (v != null && !hotovo) {
            hotovo = true;
            final Result res = v;
            runOnUiThread(() -> {
                nahled.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Intent i = new Intent();
                i.putExtra("format", res.getBarcodeFormat().name());
                i.putExtra("text", res.getText());
                setResult(RESULT_OK, i);
                finish();
            });
        }
    }

    private void zavri() {
        try { if (relace != null) relace.close(); } catch (RuntimeException e) { }
        try { if (kamera != null) kamera.close(); } catch (RuntimeException e) { }
        try { if (ctecka != null) ctecka.close(); } catch (RuntimeException e) { }
        relace = null; kamera = null; ctecka = null;
    }

    private void konec(String chyba) {
        if (isFinishing()) return;
        hotovo = true;
        Intent i = new Intent();
        if (chyba != null) i.putExtra("chyba", chyba);
        setResult(RESULT_CANCELED, i);
        finish();
    }

    /** Ztmaví okolí, nechá světlé okno uprostřed a napíše nápovědu. */
    private static final class Ramecek extends View {
        private final Paint tma = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint okraj = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path cesta = new Path();
        private final float d;

        Ramecek(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            tma.setColor(0x99000000);
            okraj.setColor(Color.WHITE);
            okraj.setStyle(Paint.Style.STROKE);
            okraj.setStrokeWidth(3 * d);
            text.setColor(Color.WHITE);
            text.setTextSize(16 * d);
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float sw = w * 0.84f, sh = sw * 0.62f;
            RectF okno = new RectF((w - sw) / 2, h * 0.42f - sh / 2, (w + sw) / 2, h * 0.42f + sh / 2);
            cesta.reset();
            cesta.setFillType(Path.FillType.EVEN_ODD);
            cesta.addRect(0, 0, w, h, Path.Direction.CW);
            cesta.addRoundRect(okno, 18 * d, 18 * d, Path.Direction.CW);
            c.drawPath(cesta, tma);
            c.drawRoundRect(okno, 18 * d, 18 * d, okraj);
            c.drawText("Namiř na čárový nebo QR kód", w / 2, okno.bottom + 40 * d, text);
        }
    }
}
