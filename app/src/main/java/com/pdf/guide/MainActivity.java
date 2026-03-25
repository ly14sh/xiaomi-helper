package com.pdf.guide;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.cardview.widget.CardView;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String MIUI_BLUE = "#1E88E5";
    private static final String MIUI_ORANGE = "#FF6900";
    private static final String MIUI_ORANGE_LIGHT = "#FFF3E0";
    private static final String MIUI_GRAY_LIGHT = "#F5F5F5";
    private static final String MIUI_WHITE = "#FFFFFF";
    private static final String MIUI_TEXT_PRIMARY = "#212121";
    private static final String MIUI_TEXT_SECONDARY = "#757575";
    
    private PdfRenderer pdfRenderer;
    private ParcelFileDescriptor fileDescriptor;
    private int pageCount = 0;
    private int currentPage = 0;
    
    private FrameLayout rootLayout;
    private RelativeLayout topBar;
    private LinearLayout bottomBar;
    private FrameLayout contentContainer;
    private PageTurnView pageTurnView;
    private TextView pageText;
    private LinearLayout pageIndicator;
    private Button prevBtn;
    private Button nextBtn;
    private ListView tocListView;
    
    private boolean showingTOC = false;
    private boolean barsVisible = true;
    private List<String> tocItems = new ArrayList<>();
    private int[] tocPages;
    
    private Bitmap[] pageCache = new Bitmap[3]; // prev, current, next
    private static final int CACHE_SIZE = 3;
    
    private int screenWidth;
    private int screenHeight;
    
    private Handler hideHandler = new Handler(Looper.getMainLooper());
    private static final int AUTO_HIDE_DELAY = 3000;
    private Runnable hideRunnable = this::hideBarsAnimated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        hideSystemUI();
        
        screenWidth = getResources().getDisplayMetrics().widthPixels;
        screenHeight = getResources().getDisplayMetrics().heightPixels;
        
        createUI();
        loadPDF();
        
        hideHandler.postDelayed(() -> hideBarsAnimated(), 2000);
    }
    
    private void hideSystemUI() {
        Window window = getWindow();
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        );
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        );
    }
    
    private int dp(float dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
    
    private void createUI() {
        rootLayout = new FrameLayout(this);
        rootLayout.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        rootLayout.setBackgroundColor(Color.parseColor(MIUI_GRAY_LIGHT));
        
        contentContainer = createContentContainer();
        tocListView = createTOCList();
        topBar = createTopBar();
        bottomBar = createBottomBar();
        
        rootLayout.addView(contentContainer);
        rootLayout.addView(tocListView);
        rootLayout.addView(topBar);
        rootLayout.addView(bottomBar);
        
        setContentView(rootLayout);
    }
    
    private FrameLayout createContentContainer() {
        FrameLayout container = new FrameLayout(this);
        container.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        container.setBackgroundColor(Color.parseColor(MIUI_GRAY_LIGHT));
        
        pageTurnView = new PageTurnView(this);
        pageTurnView.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageTurnView.setBackgroundColor(Color.parseColor(MIUI_WHITE));
        
        pageTurnView.setOnPageTurnListener(new PageTurnView.OnPageTurnListener() {
            @Override
            public void onNextPage() {
                if (currentPage < pageCount - 1) {
                    currentPage++;
                    updatePageCache();
                    updateNavButtons();
                    updatePageInfo();
                    scheduleHide();
                }
            }
            
            @Override
            public void onPrevPage() {
                if (currentPage > 0) {
                    currentPage--;
                    updatePageCache();
                    updateNavButtons();
                    updatePageInfo();
                    scheduleHide();
                }
            }
            
            @Override
            public void onPageChanged(int page) {
                // Not used
            }
        });
        
        // Touch to show/hide bars
        pageTurnView.setOnClickListener(v -> toggleBars());
        
        container.addView(pageTurnView);
        return container;
    }
    
    private RelativeLayout createTopBar() {
        RelativeLayout topBar = new RelativeLayout(this);
        topBar.setBackgroundColor(Color.parseColor(MIUI_BLUE));
        
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        topBar.setPadding(dp(16), statusBarHeight / 2, dp(16), dp(10));
        topBar.setElevation(dp(4));
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        topBar.setLayoutParams(params);
        
        TextView title = new TextView(this);
        title.setText("智能手机使用指南");
        title.setTextColor(Color.parseColor(MIUI_WHITE));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        RelativeLayout.LayoutParams titleParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        title.setLayoutParams(titleParams);
        topBar.addView(title);
        
        Button tocBtn = createTopButton("目录", MIUI_ORANGE);
        tocBtn.setOnClickListener(v -> toggleTOC());
        RelativeLayout.LayoutParams tocParams = new RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        tocParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        tocParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tocBtn.setLayoutParams(tocParams);
        topBar.addView(tocBtn);
        
        return topBar;
    }
    
    private Button createTopButton(String text, String bgColor) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(Color.parseColor(MIUI_WHITE));
        btn.setAllCaps(false);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(bgColor));
        drawable.setCornerRadius(dp(6));
        btn.setBackground(drawable);
        btn.setPadding(dp(16), dp(6), dp(16), dp(6));
        
        return btn;
    }
    
    private LinearLayout createBottomBar() {
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setBackgroundColor(Color.parseColor(MIUI_WHITE));
        bottomBar.setPadding(dp(12), dp(10), dp(12), dp(10) + dp(8));
        bottomBar.setElevation(dp(6));
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(params);
        
        prevBtn = createNavButton("上一页");
        prevBtn.setOnClickListener(v -> {
            goToPrevPage();
            scheduleHide();
        });
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        prevBtn.setLayoutParams(prevParams);
        
        pageIndicator = new LinearLayout(this);
        pageIndicator.setOrientation(LinearLayout.VERTICAL);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setClickable(true);
        pageIndicator.setFocusable(true);
        pageIndicator.setBackgroundColor(Color.TRANSPARENT);
        pageIndicator.setPadding(dp(16), dp(6), dp(16), dp(6));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f);
        pageIndicator.setLayoutParams(indicatorParams);
        
        pageText = new TextView(this);
        pageText.setText("1 / 1");
        pageText.setTextSize(16);
        pageText.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
        pageText.setGravity(Gravity.CENTER);
        pageText.setTypeface(null, android.graphics.Typeface.BOLD);
        
        TextView tapHint = new TextView(this);
        tapHint.setText("点击跳转");
        tapHint.setTextSize(10);
        tapHint.setTextColor(Color.parseColor(MIUI_BLUE));
        tapHint.setGravity(Gravity.CENTER);
        
        pageIndicator.addView(pageText);
        pageIndicator.addView(tapHint);
        pageIndicator.setOnClickListener(v -> showJumpDialog());
        
        nextBtn = createNavButton("下一页");
        nextBtn.setOnClickListener(v -> {
            goToNextPage();
            scheduleHide();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nextBtn.setLayoutParams(nextParams);
        
        bottomBar.addView(prevBtn);
        bottomBar.addView(pageIndicator);
        bottomBar.addView(nextBtn);
        
        return bottomBar;
    }
    
    private void showJumpDialog() {
        hideHandler.removeCallbacks(hideRunnable);
        
        CardView cardView = new CardView(this);
        cardView.setCardBackgroundColor(Color.parseColor(MIUI_WHITE));
        cardView.setCardElevation(dp(16));
        cardView.setRadius(dp(20));
        cardView.setUseCompatPadding(true);
        
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.VERTICAL);
        dialogView.setPadding(dp(28), dp(24), dp(28), dp(24));
        
        TextView title = new TextView(this);
        title.setText("跳转到指定页");
        title.setTextSize(20);
        title.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        dialogView.addView(title);
        
        TextView currentPageText = new TextView(this);
        currentPageText.setText("当前第 " + (currentPage + 1) + " 页，共 " + pageCount + " 页");
        currentPageText.setTextSize(14);
        currentPageText.setTextColor(Color.parseColor(MIUI_TEXT_SECONDARY));
        currentPageText.setGravity(Gravity.CENTER);
        currentPageText.setPadding(0, dp(6), 0, dp(16));
        dialogView.addView(currentPageText);
        
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER);
        
        TextView label = new TextView(this);
        label.setText("第 ");
        label.setTextSize(18);
        label.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
        inputRow.addView(label);
        
        EditText input = new EditText(this);
        input.setHint(String.valueOf(currentPage + 1));
        input.setTextSize(20);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(20), dp(12), dp(20), dp(12));
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor(MIUI_GRAY_LIGHT));
        inputBg.setCornerRadius(dp(12));
        input.setBackground(inputBg);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
        input.setLayoutParams(inputParams);
        inputRow.addView(input);
        
        TextView label2 = new TextView(this);
        label2.setText(" 页");
        label2.setTextSize(18);
        label2.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
        inputRow.addView(label2);
        
        dialogView.addView(inputRow);
        
        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, dp(24), 0, 0);
        
        Button cancelBtn = new Button(this);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(16);
        cancelBtn.setTextColor(Color.parseColor(MIUI_TEXT_SECONDARY));
        cancelBtn.setAllCaps(false);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(Color.parseColor(MIUI_GRAY_LIGHT));
        cancelBg.setCornerRadius(dp(24));
        cancelBtn.setBackground(cancelBg);
        cancelBtn.setPadding(dp(32), dp(12), dp(32), dp(12));
        
        Button confirmBtn = new Button(this);
        confirmBtn.setText("跳转");
        confirmBtn.setTextSize(16);
        confirmBtn.setTextColor(Color.parseColor(MIUI_WHITE));
        confirmBtn.setAllCaps(false);
        GradientDrawable confirmBg = new GradientDrawable();
        confirmBg.setColor(Color.parseColor(MIUI_BLUE));
        confirmBg.setCornerRadius(dp(24));
        confirmBtn.setBackground(confirmBg);
        confirmBtn.setPadding(dp(32), dp(12), dp(32), dp(12));
        
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cancelParams.setMargins(0, 0, dp(12), 0);
        cancelBtn.setLayoutParams(cancelParams);
        
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        confirmParams.setMargins(dp(12), 0, 0, 0);
        confirmBtn.setLayoutParams(confirmParams);
        
        btnRow.addView(cancelBtn);
        btnRow.addView(confirmBtn);
        dialogView.addView(btnRow);
        
        cardView.addView(dialogView, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(cardView);
        AlertDialog dialog = builder.create();
        
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().setDimAmount(0.4f);
        
        cancelBtn.setOnClickListener(v -> {
            dialog.dismiss();
            scheduleHide();
        });
        
        confirmBtn.setOnClickListener(v -> {
            String text = input.getText().toString();
            if (text != null && !text.trim().isEmpty()) {
                try {
                    int pageNum = Integer.parseInt(text.trim());
                    int pageIndex = pageNum - 1;
                    if (pageIndex >= 0 && pageIndex < pageCount) {
                        goToPage(pageIndex);
                        dialog.dismiss();
                        scheduleHide();
                    } else {
                        Toast.makeText(this, "页码超出范围 (1-" + pageCount + ")", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "请输入有效数字", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请输入页码", Toast.LENGTH_SHORT).show();
            }
        });
        
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(d -> scheduleHide());
        
        dialog.show();
    }
    
    private Button createNavButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setTextColor(Color.parseColor(MIUI_BLUE));
        btn.setAllCaps(false);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(MIUI_ORANGE_LIGHT));
        drawable.setCornerRadius(dp(20));
        btn.setBackground(drawable);
        btn.setPadding(dp(16), dp(10), dp(16), dp(10));
        return btn;
    }
    
    private ListView createTOCList() {
        ListView listView = new ListView(this);
        
        int statusBarHeight = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId);
        }
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        );
        params.topMargin = statusBarHeight + dp(50);
        listView.setLayoutParams(params);
        
        listView.setBackgroundColor(Color.parseColor(MIUI_WHITE));
        listView.setVisibility(View.GONE);
        listView.setDivider(getResources().getDrawable(android.R.color.darker_gray));
        listView.setDividerHeight(dp(1));
        listView.setPadding(0, dp(8), 0, dp(80));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            goToPage(tocPages[position]);
            toggleTOC();
            scheduleHide();
        });
        return listView;
    }
    
    private void toggleBars() {
        if (showingTOC) return;
        
        if (barsVisible) {
            hideBarsAnimated();
        } else {
            showBarsAnimated();
        }
    }
    
    private void showBarsAnimated() {
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        topBar.animate().alpha(1f).setDuration(200).start();
        bottomBar.animate().alpha(1f).setDuration(200).start();
        scheduleHide();
    }
    
    private void hideBarsAnimated() {
        barsVisible = false;
        topBar.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!barsVisible) topBar.setVisibility(View.INVISIBLE);
        }).start();
        bottomBar.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            if (!barsVisible) bottomBar.setVisibility(View.INVISIBLE);
        }).start();
    }
    
    private void scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, AUTO_HIDE_DELAY);
    }
    
    private void toggleTOC() {
        if (showingTOC) {
            tocListView.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            showingTOC = false;
            scheduleHide();
        } else {
            if (tocItems.isEmpty()) {
                generateSimpleTOC();
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_list_item_1, tocItems) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView textView = (TextView) view.findViewById(android.R.id.text1);
                    textView.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
                    textView.setTextSize(15);
                    textView.setPadding(dp(20), dp(14), dp(20), dp(14));
                    return view;
                }
            };
            tocListView.setAdapter(adapter);
            tocListView.setVisibility(View.VISIBLE);
            contentContainer.setVisibility(View.GONE);
            showingTOC = true;
            hideHandler.removeCallbacks(hideRunnable);
        }
    }
    
    private void generateSimpleTOC() {
        tocItems.clear();
        List<Integer> pages = new ArrayList<>();
        
        String[] sections = {
            "首页",
            "日常篇",
            "出行篇",
            "拍照篇",
            "软件篇",
            "支付篇",
            "信息安全篇"
        };
        
        int[] pageStarts = {0, 5, 37, 44, 51, 61, 66};
        
        for (int i = 0; i < sections.length; i++) {
            int pageNum = pageStarts[i] + 1;
            String item = sections[i] + "  (第" + pageNum + "页)";
            tocItems.add(item);
            pages.add(pageStarts[i]);
        }
        
        tocPages = new int[pages.size()];
        for (int i = 0; i < pages.size(); i++) {
            tocPages[i] = pages.get(i);
        }
    }
    
    private void loadPDF() {
        try {
            File pdfFile = new File(getCacheDir(), "guide.pdf");
            
            // If PDF doesn't exist, extract from ZIP in assets
            if (!pdfFile.exists() || pdfFile.length() == 0) {
                extractFromZip();
            }
            
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            pageCount = pdfRenderer.getPageCount();
            
            // Initialize page cache
            for (int i = 0; i < CACHE_SIZE; i++) {
                pageCache[i] = null;
            }
            
            updatePageCache();
            updateNavButtons();
            updatePageInfo();
            
        } catch (Exception e) {
            Toast.makeText(this, "加载PDF失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void extractFromZip() {
        try {
            File zipFile = new File(getCacheDir(), "guide.zip");
            File pdfFile = new File(getCacheDir(), "guide.pdf");
            
            // Copy ZIP from assets to cache
            InputStream is = getAssets().open("guide.zip");
            OutputStream os = new FileOutputStream(zipFile);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = is.read(buffer)) != -1) {
                os.write(buffer, 0, count);
            }
            is.close();
            os.close();
            
            // Extract PDF from ZIP using ZipInputStream
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFile));
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".pdf")) {
                    FileOutputStream fos = new FileOutputStream(pdfFile);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                    }
                    fos.close();
                    break;
                }
            }
            zis.close();
            
            // Delete ZIP file after extraction
            zipFile.delete();
            
        } catch (Exception e) {
            Toast.makeText(this, "解压PDF失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private Bitmap renderPageSync(int index) {
        if (index < 0 || index >= pageCount) return null;
        
        try {
            android.graphics.pdf.PdfRenderer.Page page = pdfRenderer.openPage(index);
            
            int availableWidth = screenWidth - dp(8);
            int availableHeight = screenHeight - dp(80);
            
            float scaleX = (float) availableWidth / page.getWidth();
            float scaleY = (float) availableHeight / page.getHeight();
            float scale = Math.min(scaleX, scaleY);
            scale = Math.min(scale, 3.0f);
            
            int width = (int) (page.getWidth() * scale);
            int height = (int) (page.getHeight() * scale);
            
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.parseColor(MIUI_WHITE));
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            
            return bitmap;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void updatePageCache() {
        // Render pages for page turn effect
        // pageCache[0] = prev page, [1] = current, [2] = next
        
        // Recycle old bitmaps
        for (int i = 0; i < CACHE_SIZE; i++) {
            if (pageCache[i] != null) {
                // Don't recycle immediately, let GC handle it
            }
        }
        
        // Render current page
        pageCache[1] = renderPageSync(currentPage);
        
        // Render prev page
        if (currentPage > 0) {
            pageCache[0] = renderPageSync(currentPage - 1);
        } else {
            pageCache[0] = null;
        }
        
        // Render next page
        if (currentPage < pageCount - 1) {
            pageCache[2] = renderPageSync(currentPage + 1);
        } else {
            pageCache[2] = null;
        }
        
        pageTurnView.setCurrentPage(pageCache[1]);
        pageTurnView.setPrevPage(pageCache[0]);
        pageTurnView.setNextPage(pageCache[2]);
    }
    
    private void goToPage(int index) {
        if (index < 0 || index >= pageCount) return;
        currentPage = index;
        updatePageCache();
        updateNavButtons();
        updatePageInfo();
    }
    
    private void goToPrevPage() {
        if (currentPage > 0) {
            currentPage--;
            updatePageCache();
            updateNavButtons();
            updatePageInfo();
        }
    }
    
    private void goToNextPage() {
        if (currentPage < pageCount - 1) {
            currentPage++;
            updatePageCache();
            updateNavButtons();
            updatePageInfo();
        }
    }
    
    private void updateNavButtons() {
        prevBtn.setEnabled(currentPage > 0);
        prevBtn.setAlpha(currentPage > 0 ? 1.0f : 0.4f);
        nextBtn.setEnabled(currentPage < pageCount - 1);
        nextBtn.setAlpha(currentPage < pageCount - 1 ? 1.0f : 0.4f);
    }
    
    private void updatePageInfo() {
        pageText.setText((currentPage + 1) + " / " + pageCount);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideHandler.removeCallbacksAndMessages(null);
        try {
            if (pdfRenderer != null) pdfRenderer.close();
            if (fileDescriptor != null) fileDescriptor.close();
        } catch (Exception e) {}
    }
}
