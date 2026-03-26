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
import android.widget.ImageView;
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
    private CardView tocCard;  // CardView for TOC dialog
    
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
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN  // Hide status bar
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
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
        
        // Create TOC CardView (same style as jump dialog)
        tocCard = new CardView(this);
        tocCard.setCardBackgroundColor(Color.parseColor(MIUI_WHITE));
        tocCard.setCardElevation(dp(16));
        tocCard.setRadius(dp(20));
        tocCard.setUseCompatPadding(true);
        FrameLayout.LayoutParams tocCardParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        tocCardParams.gravity = Gravity.CENTER;
        tocCardParams.leftMargin = dp(32);
        tocCardParams.rightMargin = dp(32);
        tocCard.setLayoutParams(tocCardParams);
        tocCard.setVisibility(View.GONE);
        
        rootLayout.addView(contentContainer);
        rootLayout.addView(tocListView);
        rootLayout.addView(tocCard);  // Add TOC card
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
                // Center tap (-1) to toggle bars
                if (page == -1) {
                    toggleBars();
                }
            }
        });
        
        container.addView(pageTurnView);
        return container;
    }
    
    private RelativeLayout createTopBar() {
        RelativeLayout topBar = new RelativeLayout(this);
        
        // Simple gradient background
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        bgDrawable.setColors(new int[]{
            Color.argb(160, 0, 0, 0),
            Color.argb(80, 0, 0, 0),
            Color.TRANSPARENT
        });
        topBar.setBackground(bgDrawable);
        
        // Content row container - no status bar padding since it's hidden
        LinearLayout contentRow = new LinearLayout(this);
        contentRow.setOrientation(LinearLayout.HORIZONTAL);
        contentRow.setGravity(Gravity.CENTER_VERTICAL);
        contentRow.setPadding(dp(12), dp(12), 0, dp(12)); // Remove right padding for TOC button
        RelativeLayout.LayoutParams rowParams = new RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        contentRow.setLayoutParams(rowParams);
        
        // Xiaomi Logo
        ImageView logoView = new ImageView(this);
        logoView.setImageResource(R.drawable.xiaomi_logo);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        logoParams.setMargins(0, 0, dp(36), 0); // More spacing between logo and title
        logoView.setLayoutParams(logoParams);
        contentRow.addView(logoView);
        
        // Title - centered with weight
        TextView title = new TextView(this);
        title.setText("智能手机使用指南");
        title.setTextColor(Color.parseColor(MIUI_WHITE));
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setShadowLayer(dp(2), 0, dp(1), Color.argb(80, 0, 0, 0));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setGravity(Gravity.CENTER);
        title.setLayoutParams(titleParams);
        contentRow.addView(title);
        
        // TOC Button - compact padding, move right
        Button tocBtn = new Button(this);
        tocBtn.setText("≡ 目录");
        tocBtn.setTextSize(15);
        tocBtn.setTextColor(Color.parseColor(MIUI_WHITE));
        tocBtn.setAllCaps(false);
        tocBtn.setBackgroundColor(Color.TRANSPARENT);
        tocBtn.setPadding(dp(4), dp(4), dp(4), dp(4)); // Smaller padding
        tocBtn.setOnClickListener(v -> toggleTOC());
        LinearLayout.LayoutParams tocParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tocParams.setMargins(0, 0, dp(-8), 0); // Move right closer to edge
        tocBtn.setLayoutParams(tocParams);
        contentRow.addView(tocBtn);
        
        topBar.addView(contentRow);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        topBar.setLayoutParams(params);
        
        return topBar;
    }
    
    private Button createModernButton(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(13);
        btn.setTextColor(Color.parseColor(MIUI_WHITE));
        btn.setAllCaps(false);
        
        // Modern glassmorphism style
        GradientDrawable drawable = new GradientDrawable();
        drawable.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        drawable.setCornerRadius(dp(20));
        drawable.setColor(Color.argb(60, 255, 255, 255)); // Semi-transparent white
        drawable.setStroke(dp(1), Color.argb(40, 255, 255, 255)); // Subtle border
        btn.setBackground(drawable);
        btn.setPadding(dp(14), dp(6), dp(14), dp(6));
        
        // Shadow
        btn.setShadowLayer(dp(4), 0, dp(2), Color.argb(40, 0, 0, 0));
        
        return btn;
    }
    
    private LinearLayout createBottomBar() {
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        
        // Simple glassmorphism background
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setOrientation(GradientDrawable.Orientation.BOTTOM_TOP);
        bgDrawable.setColors(new int[]{
            Color.argb(220, 255, 255, 255),  // White at bottom
            Color.argb(180, 255, 255, 255)   // Slightly transparent at top
        });
        bgDrawable.setCornerRadii(new float[]{
            0, 0, 0, 0, dp(24), dp(24), dp(24), dp(24)
        });
        bottomBar.setBackground(bgDrawable);
        
        bottomBar.setPadding(dp(8), dp(6), dp(8), dp(6) + dp(4));
        bottomBar.setElevation(0);
        
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.BOTTOM;
        bottomBar.setLayoutParams(params);
        
        // Previous Button
        prevBtn = createPageButton("◀ 上一页", true);
        prevBtn.setOnClickListener(v -> {
            goToPrevPage();
            scheduleHide();
        });
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        prevParams.setMargins(0, 0, dp(32), 0); // 32dp right margin
        prevBtn.setLayoutParams(prevParams);
        
        // Page Indicator
        pageIndicator = new LinearLayout(this);
        pageIndicator.setOrientation(LinearLayout.VERTICAL);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setClickable(true);
        pageIndicator.setFocusable(true);
        pageIndicator.setPadding(dp(12), dp(6), dp(12), dp(6));
        
        GradientDrawable indicatorBg = new GradientDrawable();
        indicatorBg.setCornerRadius(dp(12));
        indicatorBg.setColor(Color.parseColor(MIUI_GRAY_LIGHT));
        pageIndicator.setBackground(indicatorBg);
        
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        indicatorParams.setMargins(dp(16), 0, dp(16), 0); // 32dp total spacing
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
        
        // Next Button
        nextBtn = createPageButton("下一页 ▶", false);
        nextBtn.setOnClickListener(v -> {
            goToNextPage();
            scheduleHide();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nextParams.setMargins(dp(32), 0, 0, 0); // 32dp left margin
        nextBtn.setLayoutParams(nextParams);
        
        bottomBar.addView(prevBtn);
        bottomBar.addView(pageIndicator);
        bottomBar.addView(nextBtn);
        
        return bottomBar;
    }
    
    private Button createPageButton(String text, boolean isPrev) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14); // Larger text
        btn.setAllCaps(false);
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(10));
        
        if (isPrev) {
            drawable.setColor(Color.parseColor(MIUI_WHITE));
            drawable.setStroke(dp(1), Color.parseColor(MIUI_BLUE));
            btn.setTextColor(Color.parseColor(MIUI_BLUE));
        } else {
            drawable.setColor(Color.parseColor(MIUI_BLUE));
            btn.setTextColor(Color.parseColor(MIUI_WHITE));
        }
        
        btn.setBackground(drawable);
        btn.setPadding(dp(10), dp(5), dp(10), dp(5));
        btn.setGravity(Gravity.CENTER);
        
        return btn;
    }
    
    private android.graphics.drawable.Drawable createTouchFeedback() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        drawable.setColor(Color.parseColor(MIUI_GRAY_LIGHT));
        return drawable;
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
    
    private void scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, AUTO_HIDE_DELAY);
    }
    
    private void toggleTOC() {
        if (showingTOC) {
            tocCard.setVisibility(View.GONE);
            contentContainer.setVisibility(View.VISIBLE);
            showingTOC = false;
            scheduleHide();
        } else {
            if (tocItems.isEmpty()) {
                generateSimpleTOC();
            }
            // Build TOC list in CardView style
            LinearLayout tocContent = new LinearLayout(this);
            tocContent.setOrientation(LinearLayout.VERTICAL);
            tocContent.setPadding(dp(20), dp(16), dp(20), dp(16));
            
            TextView titleView = new TextView(this);
            titleView.setText("目录");
            titleView.setTextSize(20);
            titleView.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, dp(16));
            tocContent.addView(titleView);
            
            // Add TOC items
            for (int i = 0; i < tocItems.size(); i++) {
                final int pageIndex = tocPages[i];
                
                TextView item = new TextView(this);
                item.setText(tocItems.get(i));
                item.setTextSize(16);
                item.setTextColor(Color.parseColor(MIUI_TEXT_PRIMARY));
                item.setPadding(dp(16), dp(14), dp(16), dp(14));
                item.setClickable(true);
                item.setFocusable(true);
                
                // Ripple background
                GradientDrawable itemBg = new GradientDrawable();
                itemBg.setColor(Color.TRANSPARENT);
                itemBg.setCornerRadius(dp(8));
                item.setBackground(itemBg);
                
                item.setOnClickListener(v -> {
                    tocCard.setVisibility(View.GONE);
                    contentContainer.setVisibility(View.VISIBLE);
                    showingTOC = false;
                    goToPage(pageIndex);
                    scheduleHide();
                });
                
                tocContent.addView(item);
            }
            
            tocCard.removeAllViews();
            tocCard.addView(tocContent);
            tocCard.setVisibility(View.VISIBLE);
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
