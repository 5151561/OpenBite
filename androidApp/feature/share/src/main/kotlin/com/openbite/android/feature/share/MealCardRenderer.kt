package com.openbite.android.feature.share

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.openbite.android.core.designsystem.R as DsR
import com.openbite.core.domain.MealCard
import com.openbite.core.model.formatMoney
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** 卡片颜色（ARGB）。 */
data class CardPalette(val background: Int, val content: Int, val photoPlaceholder: Int)

/**
 * 把设计稿 16 的分享卡片画成位图：300×384 的比例，按 [scale] 放大（3.6 倍 = 1080×1382，适合发图）。
 * 屏幕上的预览和分享出去的 PNG 都用它，保证看到的就是发出去的。
 */
object MealCardRenderer {
    const val WIDTH_DP = 300f
    const val HEIGHT_DP = 384f
    const val SHARE_SCALE = 3.6f

    fun render(res: Resources, card: MealCard, photoJpeg: ByteArray?, palette: CardPalette, scale: Float = SHARE_SCALE): Bitmap {
        val w = (WIDTH_DP * scale).toInt()
        val h = (HEIGHT_DP * scale).toInt()
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        fun dp(v: Float) = v * scale
        val radius = dp(28f)
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawColor(palette.background)

        // 照片区：上半部分；没有照片就不留这一块。
        var top = 0f
        val photo = photoJpeg?.let { decodeScaled(it, w) }
        if (photo != null) {
            val photoHeight = dp(168f)
            drawCropped(canvas, photo, RectF(0f, 0f, w.toFloat(), photoHeight))
            photo.recycle()
            top = photoHeight
        }

        val pad = dp(20f)
        var y = top + dp(18f)
        val content = palette.content
        val textWidth = (w - pad * 2).toInt()

        card.placeName?.let { name ->
            y += drawText(canvas, name, pad, y, textWidth, dp(22f), content, bold = true, maxLines = 1)
            val sub = listOfNotNull(card.city, fullDate(res, card)).joinToString(" · ")
            y += drawText(canvas, sub, pad, y + dp(2f), textWidth, dp(13f), withAlpha(content, 0.8f), maxLines = 1) + dp(2f)
        } ?: run {
            y += drawText(canvas, fullDate(res, card), pad, y, textWidth, dp(13f), withAlpha(content, 0.8f), maxLines = 1)
        }

        card.stars?.let { stars ->
            y += dp(8f)
            val size = dp(22f)
            for (i in 0 until 5) drawStar(canvas, pad + i * (size + dp(2f)) + size / 2, y + size / 2, size / 2, content, filled = i < stars)
            y += size
        }

        card.note?.takeIf { it.isNotBlank() }?.let {
            y += dp(10f)
            y += drawText(canvas, res.getString(DsR.string.ds_quote, it), pad, y, textWidth, dp(16f), content, maxLines = if (photo != null) 2 else 4)
        }

        val footer = h - pad - dp(16f)
        if (card.dishNames.isNotEmpty()) {
            y += dp(10f)
            val names = card.dishNames + listOfNotNull(card.moreDishes.takeIf { it > 0 }?.let { res.getString(R.string.card_more_dishes, it) })
            val lineHeight = dp(18f)
            for (name in names) {
                if (y + lineHeight > footer - dp(8f)) break
                y += drawText(canvas, "· $name", pad, y, textWidth, dp(13f), withAlpha(content, 0.9f), maxLines = 1)
            }
        }

        // 底部：品牌 + 金额 · 人数。
        val brandPaint = textPaint(dp(14f), content, bold = true)
        canvas.drawText(res.getString(R.string.card_brand), pad, footer + dp(12f), brandPaint)
        val amount = card.amount
        if (amount != null && card.headcount != null) {
            val right = textPaint(dp(13f), withAlpha(content, 0.85f), bold = false).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(res.getString(R.string.card_amount_people, formatMoney(amount), card.headcount), w - pad, footer + dp(12f), right)
        }
        return bitmap
    }

    private fun fullDate(res: Resources, card: MealCard): String =
        res.getString(DsR.string.ds_year_month_day, card.date.year, card.date.month.ordinal + 1, card.date.day)

    private fun textPaint(size: Float, color: Int, bold: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    /** 画一段会换行的字，返回占用的高度。 */
    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, width: Int, size: Float, color: Int, bold: Boolean = false, maxLines: Int): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint(size, color, bold), max(width, 1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, filled: Boolean) {
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.45f
            val angle = -PI / 2 + i * PI / 5
            val px = cx + (radius * cos(angle)).toFloat()
            val py = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (filled) color else withAlpha(color, 0.35f)
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, paint)
    }

    /** 先按目标宽度降采样再解码，避免把大照片整张读进内存。 */
    private fun decodeScaled(jpeg: ByteArray, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** 像 CSS 的 object-fit: cover。 */
    private fun drawCropped(canvas: Canvas, bitmap: Bitmap, target: RectF) {
        val scale = max(target.width() / bitmap.width, target.height() / bitmap.height)
        val srcW = (target.width() / scale).toInt()
        val srcH = (target.height() / scale).toInt()
        val left = (bitmap.width - srcW) / 2
        val top = (bitmap.height - srcH) / 2
        canvas.drawBitmap(bitmap, Rect(left, top, left + srcW, top + srcH), target, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun withAlpha(color: Int, alpha: Float): Int = (color and 0x00FFFFFF) or ((255 * alpha).toInt() shl 24)
}
