package org.mz.killrs.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mz.killrs.categories.component.CategoryCard
import org.mz.killrs.shared.Exo2FontRegular
import org.mz.killrs.shared.domain.ProductCategory

private val CollectionBackground = Color(0xFFF7F5EC)
private val CollectionGreen = Color(0xFF124735)
private val CollectionMuted = Color(0xFF777A71)

@Composable
fun CategoriesScreen(
    navigateToCategories: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CollectionBackground),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 18.dp,
            end = 24.dp,
            bottom = 28.dp
        )
    ) {
        item {
            Text(
                text = "THE COLLECTION",
                color = CollectionMuted,
                fontFamily = Exo2FontRegular(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.2.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Find your\nnext favorite.",
                color = CollectionGreen,
                fontFamily = FontFamily.Serif,
                fontSize = 43.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 45.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Explore genetics, your way.",
                color = CollectionMuted,
                fontFamily = Exo2FontRegular(),
                fontSize = 16.sp,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        items(
            items = ProductCategory.entries,
            key = { it.name }
        ) { category ->
            CategoryCard(
                modifier = Modifier.fillMaxWidth(),
                category = category,
                onClick = { navigateToCategories(category.name) }
            )
            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
