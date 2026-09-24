package org.mz.killrs.shared.domain

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import org.mz.killrs.shared.CategoryGreen
import org.mz.killrs.shared.CategoryRed
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class Product @OptIn(ExperimentalTime::class) constructor(
    val id: String,
    val title: String,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val description: String,
    val price: Double,
    val thumbnail: String,
    val category: String,
    val strains: List<String>? = null,
    val amountOfSeeds: String? = null,
    val isPopular: Boolean = false,
    val isNew: Boolean = false,
    val isDiscounted: Boolean = false,
    val revision: Long = 0
)

enum class ProductCategory(
    val title: String,
    val color: Color
){
    Indica(
        title = "Indica",
        color = CategoryGreen
    ),
    Sativa(
        title = "Sativa",
        color = CategoryRed
    ),
    Hybrid(
        title = "Hybrid",
        color = Color.Yellow
    ),

}
