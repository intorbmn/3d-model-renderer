package com.shiroha.mmdskin.ui.wheel;

import com.shiroha.mmdskin.ui.chrome.TranslucentTrayChrome;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/** 轮盘界面基类，负责原生 GuiGraphicsExtractor 轮盘渲染与交互动画。 */
public abstract class AbstractWheelScreen extends Screen {

    public record WheelStyle(
            float screenRatio, float innerRatio,
            int lineColor, int lineColorDim, int highlightColor,
            int centerBg, int centerBorder, int textShadow
    ) {}

    public record WheelEntry(String primaryText, String secondaryText) {}

    private static final int TEXT_PRIMARY = TranslucentTrayChrome.TITLE_TEXT;
    private static final int TEXT_SECONDARY = TranslucentTrayChrome.BODY_TEXT;
    private static final int TEXT_MUTED = TranslucentTrayChrome.SUBTITLE_TEXT;
    private static final int OUTLINE_DIM = 0x26FFFFFF;
    private static final int BACKDROP_DIM = TranslucentTrayChrome.OVERLAY;
    private static final float SEGMENT_GAP_DEGREES = 1.8f;
    private static final float CENTER_BUBBLE_SCALE = 0.74f;
    /** Labels are shrunk no further than this before being truncated. */
    private static final float MIN_LABEL_SCALE = 0.65f;
    private static final int LABEL_LINE_HEIGHT = 10;

    protected final WheelStyle style;
    protected int centerX;
    protected int centerY;
    protected int outerRadius;
    protected int innerRadius;
    protected int selectedSlot = -1;

    private float[] slotPop = new float[0];
    private float[] slotVelocity = new float[0];
    private float centerPop;
    private float centerVelocity;
    private float openProgress;
    private float openVelocity;

    protected AbstractWheelScreen(Component title, WheelStyle style) {
        super(title);
        this.style = style;
    }

    protected static WheelStyle createTranslucentWheelStyle(float screenRatio, float innerRatio) {
        return new WheelStyle(
                screenRatio,
                innerRatio,
                0xFFF2F5F8,
                0xB8D5DCE3,
                0x30FFFFFF,
                0xFF20262D,
                0xFFDDE3E9,
                0xD0000000
        );
    }

    protected abstract int getSlotCount();

    protected void initWheelLayout() {
        this.centerX = this.width / 2;
        this.centerY = this.height / 2 + Math.round(this.height * 0.02f);
        int minDim = Math.min(this.width, this.height);
        this.outerRadius = (int) (minDim * style.screenRatio() / 2);
        this.innerRadius = (int) (this.outerRadius * style.innerRatio());
    }

    protected void updateSelectedSlot(int mouseX, int mouseY) {
        int count = getSlotCount();
        if (count <= 0) {
            selectedSlot = -1;
            return;
        }

        int dx = mouseX - centerX;
        int dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        float minRadius = innerRadius * 0.72f;
        float maxRadius = outerRadius + 50.0f;
        if (distance < minRadius || distance > maxRadius) {
            selectedSlot = -1;
            return;
        }

        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360;
        }
        angle = (angle + 90.0) % 360.0;

        double segmentAngle = 360.0 / count;
        selectedSlot = (int) (angle / segmentAngle) % count;
    }

    protected void renderWheelBase(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, List<WheelEntry> entries) {
        updateSelectedSlot(mouseX, mouseY);
        updateAnimations(entries.size());
        renderBackdrop(guiGraphics);
        if (!entries.isEmpty()) {
            renderSegmentWheel(guiGraphics, entries);
        }
        renderCenterDecor(guiGraphics);
    }

    protected void renderCenterBubble(GuiGraphicsExtractor guiGraphics, String text, int textColor) {
        float bubbleScale = 0.98f + centerPop * 0.06f;
        int bubbleRadius = Math.max(30, Math.round(innerRadius * CENTER_BUBBLE_SCALE * bubbleScale));
        fillCircle(guiGraphics, centerX, centerY, bubbleRadius + 2, withAlpha(style.centerBorder(), 72));
        drawCircleOutline(guiGraphics, centerX, centerY, bubbleRadius + 1, withAlpha(0xFFFFFF, 16));

        // Keep the text inside the bubble it is drawn on.
        renderFittedLabel(guiGraphics, text, centerX, centerY, Math.max(24, bubbleRadius * 2 - 10), textColor);
    }

    protected void renderEmptyState(GuiGraphicsExtractor guiGraphics, Component hint) {
        int width = Math.max(180, this.font.width(hint) + 48);
        int height = 36;
        int x = centerX - width / 2;
        int y = centerY + outerRadius / 2 + 18;
        fillRoundedRect(guiGraphics, x, y, width, height, 12, withAlpha(style.centerBorder(), 228));
        fillRoundedRect(guiGraphics, x + 1, y + 1, width - 2, height - 2, 11, withAlpha(style.centerBg(), 196));
        guiGraphics.centeredText(this.font, hint, centerX, y + 13, TEXT_PRIMARY);
    }

    protected Button createWheelIconButton(Component label, Button.OnPress onPress) {
        return Button.builder(label, onPress)
                .bounds(this.width - 36, this.height - 34, 28, 24)
                .build();
    }

    private void renderBackdrop(GuiGraphicsExtractor guiGraphics) {
        if (this.minecraft == null || this.minecraft.level == null) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xFF101318);
        }
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP_DIM);
    }

    private void renderSegmentWheel(GuiGraphicsExtractor guiGraphics, List<WheelEntry> entries) {
        int count = entries.size();
        float segmentAngle = 360.0f / count;
        int ringInner = innerRadius;
        int ringOuter = outerRadius;

        drawCircleArcOutline(guiGraphics, centerX, centerY, ringOuter + 3, OUTLINE_DIM, 2.1f);
        drawCircleArcOutline(guiGraphics, centerX, centerY, ringInner - 2, withAlpha(style.lineColorDim(), 190), 1.8f);

        for (int i = 0; i < count; i++) {
            WheelEntry entry = entries.get(i);
            float pop = slotPop[i];
            float start = i * segmentAngle - 90.0f + SEGMENT_GAP_DEGREES * 0.5f;
            float sweep = Math.max(8.0f, segmentAngle - SEGMENT_GAP_DEGREES);
            float expandedOuter = ringOuter + pop * 5.0f + openProgress * 2.5f;
            float expandedInner = Math.max(12.0f, ringInner - pop * 2.0f);

            int fillColor = i == selectedSlot
                    ? blendColors(withAlpha(style.centerBg(), 106), withAlpha(0xFFFFFF, 24), 0.12f + pop * 0.16f)
                    : blendColors(withAlpha(style.centerBg(), 92), withAlpha(style.lineColorDim(), 28), 0.06f + openProgress * 0.08f);
            int edgeColor = i == selectedSlot
                    ? blendColors(withAlpha(style.centerBorder(), 138), withAlpha(0xFFFFFF, 92), pop * 0.20f)
                    : withAlpha(style.lineColorDim(), 88);

            drawAnnularSegment(guiGraphics, centerX, centerY, expandedInner, expandedOuter, start, sweep, fillColor);
            drawAnnularSegmentOutline(guiGraphics, centerX, centerY, expandedInner, expandedOuter, start, sweep, edgeColor, 1.8f);
            drawSeparator(guiGraphics, centerX, centerY, expandedInner + 4.0f, expandedOuter - 4.0f, start, withAlpha(0xFFFFFF, 34));

            double angle = Math.toRadians(i * segmentAngle + segmentAngle / 2.0 - 90.0);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            float textRadius = expandedInner + (expandedOuter - expandedInner) * 0.54f + pop * 2.0f;
            int cx = Math.round(centerX + cos * textRadius);
            int cy = Math.round(centerY + sin * textRadius);
            float halfSweepRad = (float) Math.toRadians(sweep * 0.5f);
            // The width really available at the label's radius. It used to be clamped to at least 86 px,
            // which let longer (non-CJK) labels spill into the neighbouring segments on small wheels.
            int maxTextWidth = Math.round(Mth.clamp(2.0f * textRadius * (float) Math.sin(halfSweepRad) * 0.90f, 24.0f, 210.0f));

            if (entry.secondaryText() == null || entry.secondaryText().isEmpty()) {
                renderSingleEntry(guiGraphics, entry.primaryText(), cx, cy, pop, maxTextWidth);
            } else {
                renderDualEntry(guiGraphics, entry.primaryText(), entry.secondaryText(), cx, cy, pop, maxTextWidth);
            }
        }
    }

    private void renderCenterDecor(GuiGraphicsExtractor guiGraphics) {
        int haloRadius = Math.max(18, Math.round(innerRadius * 0.86f + openProgress * 4.0f));
        drawCircleOutline(guiGraphics, centerX, centerY, haloRadius, withAlpha(style.lineColorDim(), 42));
        drawCircleOutline(guiGraphics, centerX, centerY, Math.max(12, haloRadius - 6), withAlpha(0xFFFFFF, 10));
    }

    private void renderSingleEntry(GuiGraphicsExtractor guiGraphics, String text, int centerTextX, int centerTextY, float pop, int maxWidth) {
        int color = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.36f + pop * 0.64f);
        renderFittedLabel(guiGraphics, text, centerTextX, centerTextY, maxWidth, color);
    }

    private void renderDualEntry(GuiGraphicsExtractor guiGraphics, String icon, String label, int centerTextX, int centerTextY, float pop, int maxWidth) {
        int iconColor = blendColors(TEXT_MUTED, TEXT_PRIMARY, 0.42f + pop * 0.58f);
        renderScaledLine(guiGraphics, fitText(icon == null ? "" : icon, maxWidth),
                centerTextX, centerTextY - 7, 1.0f + pop * 0.08f, iconColor);

        int labelColor = blendColors(TEXT_MUTED, TEXT_SECONDARY, 0.52f + pop * 0.38f);
        float labelScale = 0.90f + pop * 0.02f;
        renderScaledLine(guiGraphics, fitText(label == null ? "" : label, Math.round(maxWidth / labelScale)),
                centerTextX, centerTextY + 7, labelScale, labelColor);
    }

    /**
     * Draws {@code text} centred on (cx, cy) without exceeding {@code maxWidth}: on one line if it
     * fits, otherwise wrapped onto two lines, then shrunk, and only as a last resort truncated.
     */
    protected void renderFittedLabel(GuiGraphicsExtractor guiGraphics, String text, int cx, int cy, int maxWidth, int color) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }
        String first = text;
        String second = null;
        if (this.font.width(text) > maxWidth) {
            int split = findBalancedSplit(text);
            if (split > 0) {
                first = text.substring(0, split).trim();
                second = text.substring(split).trim();
            }
        }

        int widest = Math.max(this.font.width(first), second == null ? 0 : this.font.width(second));
        float scale = widest > maxWidth ? Math.max(MIN_LABEL_SCALE, (float) maxWidth / widest) : 1.0f;
        int unscaledLimit = Math.round(maxWidth / scale);
        first = fitText(first, unscaledLimit);

        if (second == null) {
            renderScaledLine(guiGraphics, first, cx, cy, scale, color);
            return;
        }
        second = fitText(second, unscaledLimit);
        float halfGap = LABEL_LINE_HEIGHT * scale * 0.5f;
        renderScaledLine(guiGraphics, first, cx, Math.round(cy - halfGap), scale, color);
        renderScaledLine(guiGraphics, second, cx, Math.round(cy + halfGap), scale, color);
    }

    /** Index of the whitespace that splits {@code text} into the two most evenly sized lines, or -1. */
    private int findBalancedSplit(String text) {
        int best = -1;
        int bestWidth = Integer.MAX_VALUE;
        for (int i = 1; i < text.length() - 1; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                continue;
            }
            int width = Math.max(this.font.width(text.substring(0, i).trim()), this.font.width(text.substring(i).trim()));
            if (width < bestWidth) {
                bestWidth = width;
                best = i;
            }
        }
        return best;
    }

    /** Draws one line with a drop shadow, centred on (cx, cy) and scaled about that point. */
    private void renderScaledLine(GuiGraphicsExtractor guiGraphics, String line, int cx, int cy, float scale, int color) {
        if (line == null || line.isEmpty()) {
            return;
        }
        int width = this.font.width(line);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate((float) cx, (float) cy);
        guiGraphics.pose().scale(scale, scale);
        // Coordinates are relative to the translated origin.
        guiGraphics.text(this.font, line, -width / 2 + 1, -3, style.textShadow(), false);
        guiGraphics.text(this.font, line, -width / 2, -4, color, false);
        guiGraphics.pose().popMatrix();
    }

    private void updateAnimations(int count) {
        ensureAnimationCapacity(count);
        float openTarget = 1.03f;
        openVelocity += (openTarget - openProgress) * 0.24f;
        openVelocity *= 0.74f;
        openProgress = Mth.clamp(openProgress + openVelocity, 0.0f, 1.22f);

        for (int i = 0; i < count; i++) {
            float target = i == selectedSlot ? 1.08f : -0.05f;
            slotVelocity[i] += (target - slotPop[i]) * 0.40f;
            slotVelocity[i] *= 0.80f;
            slotPop[i] = Mth.clamp(slotPop[i] + slotVelocity[i], -0.22f, 1.42f);
        }

        float centerTarget = selectedSlot >= 0 ? 1.06f : -0.04f;
        centerVelocity += (centerTarget - centerPop) * 0.34f;
        centerVelocity *= 0.78f;
        centerPop = Mth.clamp(centerPop + centerVelocity, -0.20f, 1.30f);
    }

    private void ensureAnimationCapacity(int count) {
        if (slotPop.length == count) {
            return;
        }
        slotPop = new float[count];
        slotVelocity = new float[count];
        centerPop = 0.0f;
        centerVelocity = 0.0f;
        openProgress = 0.0f;
        openVelocity = 0.0f;
    }

    protected String fitText(String text, int maxWidth) {
        if (text == null || text.isEmpty() || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && this.font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    protected int blendColors(int from, int to, float progress) {
        float clamped = Mth.clamp(progress, 0.0f, 1.0f);
        int a = Mth.floor(Mth.lerp(clamped, alpha(from), alpha(to)));
        int r = Mth.floor(Mth.lerp(clamped, red(from), red(to)));
        int g = Mth.floor(Mth.lerp(clamped, green(from), green(to)));
        int b = Mth.floor(Mth.lerp(clamped, blue(from), blue(to)));
        return a << 24 | r << 16 | g << 8 | b;
    }

    protected int withAlpha(int color, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private void fillRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int width, int height, int radius, int color) {
        int clampedRadius = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            int inset = row < clampedRadius
                    ? roundedInset(clampedRadius, row)
                    : row >= height - clampedRadius
                    ? roundedInset(clampedRadius, height - 1 - row)
                    : 0;
            guiGraphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private void fillCircle(GuiGraphicsExtractor guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int span = (int) Math.sqrt(radius * radius - dy * dy);
            guiGraphics.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleOutline(GuiGraphicsExtractor guiGraphics, int cx, int cy, int radius, int color) {
        if (radius <= 1) {
            return;
        }
        for (int dy = -radius; dy <= radius; dy++) {
            int outer = (int) Math.sqrt(radius * radius - dy * dy);
            int innerRadius = Math.max(0, radius - 2);
            int inner = innerRadius == 0 ? 0 : (int) Math.sqrt(Math.max(0, innerRadius * innerRadius - dy * dy));
            guiGraphics.fill(cx - outer, cy + dy, cx - inner, cy + dy + 1, color);
            guiGraphics.fill(cx + inner + 1, cy + dy, cx + outer + 1, cy + dy + 1, color);
        }
    }

    private void drawCircleArcOutline(GuiGraphicsExtractor guiGraphics, float cx, float cy, float radius, int color, float width) {
        drawRing(guiGraphics, cx, cy, Math.max(0.0f, radius - width), radius, 0.0f, 360.0f, color);
    }

    private void drawSeparator(GuiGraphicsExtractor graphics, float cx, float cy,
                                float inner, float outer, float angleDeg, int color) {
        float radians = (float) Math.toRadians(angleDeg);
        float x1 = cx + Mth.cos(radians) * inner;
        float y1 = cy + Mth.sin(radians) * inner;
        float x2 = cx + Mth.cos(radians) * outer;
        float y2 = cy + Mth.sin(radians) * outer;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(x2 - x1, y2 - y1)));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int x = Math.round(Mth.lerp(t, x1, x2));
            int y = Math.round(Mth.lerp(t, y1, y2));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
        }
    }

    private void drawAnnularSegment(GuiGraphicsExtractor graphics, float cx, float cy,
                                    float inner, float outer, float startDeg, float sweepDeg, int color) {
        drawRing(graphics, cx, cy, inner, outer, startDeg, sweepDeg, color);
    }

    private void drawAnnularSegmentOutline(GuiGraphicsExtractor graphics, float cx, float cy,
                                           float inner, float outer, float startDeg, float sweepDeg,
                                           int color, float width) {
        drawRing(graphics, cx, cy, outer - width, outer, startDeg, sweepDeg, color);
        drawRing(graphics, cx, cy, inner, inner + width, startDeg, sweepDeg, color);
        drawSeparator(graphics, cx, cy, inner, outer, startDeg, color);
        drawSeparator(graphics, cx, cy, inner, outer, startDeg + sweepDeg, color);
    }

    /**
     * Rasterizes an annular sector through the GUI extractor. 26.3 no longer
     * exposes the immediate-mode vertex uploader used by the old wheel renderer.
     */
    private void drawRing(GuiGraphicsExtractor graphics, float cx, float cy,
                          float inner, float outer, float startDeg, float sweepDeg, int color) {
        if (outer <= 0.0f || sweepDeg <= 0.0f) {
            return;
        }

        int minY = (int) Math.floor(cy - outer);
        int maxY = (int) Math.ceil(cy + outer);
        float innerSq = inner * inner;
        float outerSq = outer * outer;
        boolean fullCircle = sweepDeg >= 359.9f;
        double startRad = Math.toRadians(startDeg);
        double endRad = Math.toRadians(startDeg + sweepDeg);
        float startCos = (float) Math.cos(startRad);
        float startSin = (float) Math.sin(startRad);
        float endCos = (float) Math.cos(endRad);
        float endSin = (float) Math.sin(endRad);

        for (int y = minY; y <= maxY; y++) {
            float dy = y + 0.5f - cy;
            float dySq = dy * dy;
            if (dySq > outerSq) {
                continue;
            }

            float outerSpan = (float) Math.sqrt(outerSq - dySq);
            float minDx = -outerSpan;
            float maxDx = outerSpan;

            if (!fullCircle) {
                // Intersect the horizontal scanline with the two half-planes
                // that define this counter-clockwise sector (sweep < 180°).
                if (Math.abs(startSin) < 1.0e-6f) {
                    if (startCos * dy < 0.0f) {
                        continue;
                    }
                } else {
                    float bound = startCos * dy / startSin;
                    if (startSin > 0.0f) {
                        maxDx = Math.min(maxDx, bound);
                    } else {
                        minDx = Math.max(minDx, bound);
                    }
                }

                if (Math.abs(endSin) < 1.0e-6f) {
                    if (-endCos * dy < 0.0f) {
                        continue;
                    }
                } else {
                    float bound = endCos * dy / endSin;
                    if (endSin > 0.0f) {
                        minDx = Math.max(minDx, bound);
                    } else {
                        maxDx = Math.min(maxDx, bound);
                    }
                }
            }

            if (minDx > maxDx) {
                continue;
            }

            int rowMinX = (int) Math.ceil(cx + minDx - 0.5f);
            int rowMaxX = (int) Math.floor(cx + maxDx - 0.5f);
            if (rowMinX > rowMaxX) {
                continue;
            }

            if (dySq < innerSq) {
                float innerSpan = (float) Math.sqrt(innerSq - dySq);
                int leftMaxX = (int) Math.floor(cx - innerSpan - 0.5f);
                int rightMinX = (int) Math.ceil(cx + innerSpan - 0.5f);
                if (rowMinX <= leftMaxX) {
                    graphics.fill(rowMinX, y, Math.min(rowMaxX + 1, leftMaxX + 1), y + 1, color);
                }
                if (rowMaxX >= rightMinX) {
                    graphics.fill(Math.max(rowMinX, rightMinX), y, rowMaxX + 1, y + 1, color);
                }
            } else {
                graphics.fill(rowMinX, y, rowMaxX + 1, y + 1, color);
            }
        }
    }

    private int roundedInset(int radius, int row) {
        if (radius <= 0) {
            return 0;
        }
        double dy = radius - row - 0.5;
        double inside = Math.max(0.0, radius * radius - dy * dy);
        return Math.max(0, radius - (int) Math.floor(Math.sqrt(inside)) - 1);
    }

    private int alpha(int color) {
        return color >>> 24;
    }

    private int red(int color) {
        return color >> 16 & 0xFF;
    }

    private int green(int color) {
        return color >> 8 & 0xFF;
    }

    private int blue(int color) {
        return color & 0xFF;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
