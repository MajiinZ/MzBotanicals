package org.mz.killrs.shared.domain

/** Presentation copy for the demo catalog; never written back as verified product data. */
val Product.displayDescription: String
    get() {
        val profile = when (title.trim().uppercase()) {
            "SUGA QUEEN" -> "A dessert-inspired profile of candied grape, dark berries, and vanilla cream, " +
                "rounded out by a subtle earthy finish. A rich, fruit-forward concept with the sweetness of a boutique confection."
            "OPPLEZ & BOONONOZ", "OPPLEZ AND BOONONOZ" -> "Crisp orchard apple meets ripe banana and silky tropical cream. " +
                "A playful fruit-bowl concept layered with a light pastry sweetness and a mellow, earthy finish."
            "00 RUNTZ", "OO RUNTZ" -> "A candy-inspired blend of tropical fruit, citrus zest, and creamy sweetness, " +
                "balanced by a touch of earth and a sharp fuel-style finish. Bright, layered, and distinctly dessert-forward."
            "SJDHS" -> "Sun-ripened peach, fresh citrus, and soft floral notes come together in a stone-fruit-inspired concept. " +
                "A gentle touch of earth adds depth to the sweet, juicy character."
            else -> null
        }
        val original = description.trim()
        val placeholder = original.lowercase() in setOf("", "hi", "add later", "test", "testing", "tbd", "description")
        return when {
            profile != null -> "Demo aroma & flavor profile · $profile" +
                if (placeholder) "" else "\n\n$original"
            !placeholder -> original
            else -> "Explore $title in the MZBotanicals collection. Review the listed product details and " +
                "choose a pack size to build your selection. A full aroma and flavor profile is coming soon."
        }
    }
