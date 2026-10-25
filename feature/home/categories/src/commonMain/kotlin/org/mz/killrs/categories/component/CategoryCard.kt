package org.mz.killrs.categories.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.domain.ProductCategory

@Composable
fun CategoryCard(
    modifier: Modifier = Modifier,
    category: ProductCategory,
    onClick: () -> Unit
) {
    val colors = when (category) {
        ProductCategory.Indica -> listOf(Color(0xFF0B6248), Color(0xFF197052))
        ProductCategory.Sativa -> listOf(Color(0xFF416A53), Color(0xFF5A7C65))
        ProductCategory.Hybrid -> listOf(Color(0xFF24574B), Color(0xFF386658))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(138.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.horizontalGradient(colors)
            )
            .clickable { onClick() }
            .padding(
                horizontal = 26.dp,
                vertical = 20.dp
            )
    ) {
        CategoryLeafArtwork(
            modifier = Modifier.matchParentSize(),
            variant = category.ordinal
        )

        androidx.compose.foundation.layout.Column(
            modifier = Modifier.matchParentSize()
        ) {
            Text(
                text = category.title,
                color = Color(0xFFF9F6EA),
                fontFamily = FontFamily.Serif,
                fontSize = 31.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Explore collection",
                    color = Color(0xFFF4F3E9),
                    fontFamily = Exo2FontRegular(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    modifier = Modifier.padding(start = 10.dp),
                    text = "↗",
                    color = Color(0xFFF4F3E9),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CategoryLeafArtwork(
    modifier: Modifier = Modifier,
    variant: Int
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val lineColor = Color.White.copy(alpha = 0.13f)
        val fillColor = Color.White.copy(alpha = 0.035f)
        val stroke = Stroke(width = 1.5.dp.toPx())

        val baseX = when (variant) {
            1 -> width * 0.68f
            2 -> width * 0.82f
            else -> width * 0.76f
        }
        val baseY = when (variant) {
            1 -> height * 0.96f
            2 -> height * 1.12f
            else -> height * 1.10f
        }
        val tipX = when (variant) {
            1 -> width * 1.04f
            2 -> width * 0.93f
            else -> width * 1.02f
        }
        val tipY = when (variant) {
            1 -> height * 0.20f
            2 -> height * -0.16f
            else -> height * -0.12f
        }

        val leaf = Path().apply {
            moveTo(baseX, baseY)
            cubicTo(
                width * 0.66f,
                height * 0.56f,
                width * 0.76f,
                height * 0.12f,
                tipX,
                tipY
            )
            cubicTo(
                width * 1.08f,
                height * 0.35f,
                width * 1.06f,
                height * 0.82f,
                baseX,
                baseY
            )
            close()
        }

        drawPath(path = leaf, color = fillColor)
        drawPath(path = leaf, color = lineColor, style = stroke)
        drawLine(
            color = lineColor,
            start = Offset(baseX, baseY),
            end = Offset(tipX, tipY),
            strokeWidth = 1.25.dp.toPx()
        )
    }
}
