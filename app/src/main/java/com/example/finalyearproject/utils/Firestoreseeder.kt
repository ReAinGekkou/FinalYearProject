package com.example.finalyearproject.utils

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

/**
 * FirestoreSeeder
 *
 * Call once from HomeFragment when the recipes collection is empty.
 * Seeds realistic recipe data so the home screen is never blank.
 *
 * Usage (in HomeFragment.onViewCreated):
 *   FirestoreSeeder.seedIfEmpty()
 */
object FirestoreSeeder {

    private val db = FirebaseFirestore.getInstance()
    private const val TAG = "FirestoreSeeder"

    fun seedIfEmpty() {
        db.collection("recipes")
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d(TAG, "Recipes collection empty — seeding data")
                    seedRecipes()
                } else {
                    Log.d(TAG, "Recipes already exist — skipping seed")
                }
            }
    }

    private fun seedRecipes() {
        val recipes = listOf(
            recipe(
                id          = "recipe_001",
                title       = "Phở Bò Hà Nội",
                description = "Classic Hanoi beef noodle soup with rich broth, tender slices of beef and fresh herbs.",
                imageUrl    = "https://images.unsplash.com/photo-1555126634-323283e090fa?w=800",
                category    = "Soup",
                cuisineType = "Vietnamese",
                tags        = listOf("beef", "noodle", "soup", "vietnamese", "comfort"),
                ingredients = listOf("beef bones", "rice noodles", "star anise", "cinnamon", "ginger", "onion", "fish sauce"),
                prepTime    = 30,
                cookTime    = 180,
                servings    = 4,
                calories    = 420,
                difficulty  = "MEDIUM",
                likes       = 245,
                views       = 1820,
                comments    = 38,
                authorName  = "Chef Minh",
                authorId    = "seed_author_1"
            ),
            recipe(
                id          = "recipe_002",
                title       = "Bún Bò Huế",
                description = "Spicy lemongrass beef noodle soup from Hue with thick round noodles.",
                imageUrl    = "https://images.unsplash.com/photo-1565557623262-b51c2513a641?w=800",
                category    = "Soup",
                cuisineType = "Vietnamese",
                tags        = listOf("beef", "spicy", "noodle", "lemongrass", "vietnamese"),
                ingredients = listOf("beef shank", "pork knuckle", "lemongrass", "shrimp paste", "chili", "round rice noodles"),
                prepTime    = 45,
                cookTime    = 120,
                servings    = 6,
                calories    = 510,
                difficulty  = "HARD",
                likes       = 189,
                views       = 1340,
                comments    = 27,
                authorName  = "Chef Lan",
                authorId    = "seed_author_2"
            ),
            recipe(
                id          = "recipe_003",
                title       = "Bánh Mì Thịt",
                description = "Vietnamese baguette sandwich with grilled pork, pickled vegetables and fresh herbs.",
                imageUrl    = "https://images.unsplash.com/photo-1600891964092-4316c288032e?w=800",
                category    = "Sandwich",
                cuisineType = "Vietnamese",
                tags        = listOf("pork", "bread", "quick", "street food", "vietnamese"),
                ingredients = listOf("baguette", "grilled pork", "pate", "pickled daikon", "cucumber", "cilantro", "chili"),
                prepTime    = 15,
                cookTime    = 20,
                servings    = 2,
                calories    = 380,
                difficulty  = "EASY",
                likes       = 312,
                views       = 2150,
                comments    = 54,
                authorName  = "Chef Hoa",
                authorId    = "seed_author_3"
            ),
            recipe(
                id          = "recipe_004",
                title       = "Cơm Tấm Sườn Nướng",
                description = "Broken rice with grilled pork ribs, a Saigon street food classic.",
                imageUrl    = "https://images.unsplash.com/photo-1504674900247-0877df9cc836?w=800",
                category    = "Rice",
                cuisineType = "Vietnamese",
                tags        = listOf("pork", "rice", "grilled", "street food", "saigon"),
                ingredients = listOf("broken rice", "pork ribs", "fish sauce", "lemongrass", "garlic", "shallots"),
                prepTime    = 30,
                cookTime    = 45,
                servings    = 2,
                calories    = 620,
                difficulty  = "MEDIUM",
                likes       = 278,
                views       = 1950,
                comments    = 41,
                authorName  = "Chef Tuan",
                authorId    = "seed_author_4"
            ),
            recipe(
                id          = "recipe_005",
                title       = "Gỏi Cuốn Tôm Thịt",
                description = "Fresh spring rolls with shrimp, pork, vermicelli and vegetables wrapped in rice paper.",
                imageUrl    = "https://images.unsplash.com/photo-1564671165093-20688ff1fffa?w=800",
                category    = "Appetizer",
                cuisineType = "Vietnamese",
                tags        = listOf("shrimp", "pork", "healthy", "fresh", "vegan-friendly", "low-cal"),
                ingredients = listOf("rice paper", "shrimp", "pork belly", "vermicelli", "lettuce", "mint", "hoisin sauce"),
                prepTime    = 25,
                cookTime    = 15,
                servings    = 4,
                calories    = 210,
                difficulty  = "EASY",
                likes       = 195,
                views       = 1420,
                comments    = 23,
                authorName  = "Chef Mai",
                authorId    = "seed_author_5"
            ),
            recipe(
                id          = "recipe_006",
                title       = "Canh Chua Cá Lóc",
                description = "Vietnamese sour fish soup with tamarind, tomatoes and vegetables.",
                imageUrl    = "https://images.unsplash.com/photo-1547592180-85f173990554?w=800",
                category    = "Soup",
                cuisineType = "Vietnamese",
                tags        = listOf("fish", "sour", "soup", "tamarind", "healthy"),
                ingredients = listOf("snakehead fish", "tamarind", "tomatoes", "pineapple", "bean sprouts", "okra"),
                prepTime    = 20,
                cookTime    = 30,
                servings    = 4,
                calories    = 285,
                difficulty  = "EASY",
                likes       = 143,
                views       = 980,
                comments    = 19,
                authorName  = "Chef Nga",
                authorId    = "seed_author_1"
            ),
            recipe(
                id          = "recipe_007",
                title       = "Bò Lúc Lắc",
                description = "Shaking beef stir-fried with garlic butter, served with rice and salad.",
                imageUrl    = "https://images.unsplash.com/photo-1529042410759-befb1204b468?w=800",
                category    = "Stir Fry",
                cuisineType = "Vietnamese",
                tags        = listOf("beef", "quick", "protein", "garlic", "stir-fry"),
                ingredients = listOf("beef tenderloin", "garlic", "butter", "oyster sauce", "lime", "watercress"),
                prepTime    = 15,
                cookTime    = 10,
                servings    = 2,
                calories    = 480,
                difficulty  = "EASY",
                likes       = 367,
                views       = 2680,
                comments    = 62,
                authorName  = "Chef Duc",
                authorId    = "seed_author_2"
            ),
            recipe(
                id          = "recipe_008",
                title       = "Chè Ba Màu",
                description = "Three-colour Vietnamese dessert with mung bean, pandan jelly and coconut milk.",
                imageUrl    = "https://images.unsplash.com/photo-1551024709-8f23befc6f87?w=800",
                category    = "Dessert",
                cuisineType = "Vietnamese",
                tags        = listOf("sweet", "dessert", "coconut", "vegan", "cold"),
                ingredients = listOf("mung beans", "pandan jelly", "red beans", "coconut milk", "sugar", "ice"),
                prepTime    = 30,
                cookTime    = 40,
                servings    = 6,
                calories    = 290,
                difficulty  = "MEDIUM",
                likes       = 224,
                views       = 1560,
                comments    = 35,
                authorName  = "Chef Linh",
                authorId    = "seed_author_3"
            ),
            recipe(
                id          = "recipe_009",
                title       = "Mì Quảng",
                description = "Quang Nam style noodles with turmeric broth, shrimp, pork and rice crackers.",
                imageUrl    = "https://images.unsplash.com/photo-1569050467447-ce54b3bbc37d?w=800",
                category    = "Noodle",
                cuisineType = "Vietnamese",
                tags        = listOf("noodle", "shrimp", "pork", "turmeric", "central vietnam"),
                ingredients = listOf("wide rice noodles", "shrimp", "pork belly", "turmeric", "peanuts", "sesame crackers"),
                prepTime    = 35,
                cookTime    = 50,
                servings    = 4,
                calories    = 540,
                difficulty  = "MEDIUM",
                likes       = 156,
                views       = 1120,
                comments    = 28,
                authorName  = "Chef Phong",
                authorId    = "seed_author_4"
            ),
            recipe(
                id          = "recipe_010",
                title       = "Bánh Xèo Miền Nam",
                description = "Crispy Vietnamese sizzling crepes filled with shrimp, pork and bean sprouts.",
                imageUrl    = "https://images.unsplash.com/photo-1498654896293-37aacf113fd9?w=800",
                category    = "Crepe",
                cuisineType = "Vietnamese",
                tags        = listOf("shrimp", "pork", "crispy", "crepe", "southern vietnam"),
                ingredients = listOf("rice flour", "coconut milk", "turmeric", "shrimp", "pork belly", "bean sprouts", "green onion"),
                prepTime    = 20,
                cookTime    = 25,
                servings    = 4,
                calories    = 390,
                difficulty  = "MEDIUM",
                likes       = 298,
                views       = 2040,
                comments    = 47,
                authorName  = "Chef Thu",
                authorId    = "seed_author_5"
            )
        )

        val batch = db.batch()
        recipes.forEach { r ->
            val ref = db.collection("recipes").document(r["id"] as String)
            batch.set(ref, r)
        }
        batch.commit()
            .addOnSuccessListener { Log.d(TAG, "Seeded ${recipes.size} recipes successfully") }
            .addOnFailureListener { e -> Log.e(TAG, "Seed failed: ${e.message}", e) }
    }

    @Suppress("SameParameterValue")
    private fun recipe(
        id: String, title: String, description: String,
        imageUrl: String, category: String, cuisineType: String,
        tags: List<String>, ingredients: List<String>,
        prepTime: Int, cookTime: Int, servings: Int, calories: Int,
        difficulty: String, likes: Int, views: Int, comments: Int,
        authorName: String, authorId: String
    ): Map<String, Any> = mapOf(
        "id"            to id,
        "recipeId"      to id,
        "title"         to title,
        "searchTitle"   to title.lowercase(),
        "description"   to description,
        "imageUrl"      to imageUrl,
        "category"      to category,
        "cuisineType"   to cuisineType,
        "tags"          to tags,
        "ingredients"   to ingredients,
        "instructions"  to listOf("Prepare all ingredients.", "Follow the cooking steps.", "Serve hot and enjoy!"),
        "prepTimeMinutes"  to prepTime,
        "cookTimeMinutes"  to cookTime,
        "servings"      to servings,
        "calories"      to calories,
        "difficulty"    to difficulty,
        "likeCount"     to likes,
        "viewCount"     to views,
        "commentCount"  to comments,
        "reviewCount"   to 0,
        "averageRating" to 4.2,
        "authorName"    to authorName,
        "authorId"      to authorId,
        "authorImageUrl" to "",
        "isPublished"   to true,
        "isFeatured"    to (likes > 250),
        "trendingScore" to (likes * 0.6 + comments * 0.2 + views * 0.2),
        "createdAt"     to Timestamp.now(),
        "updatedAt"     to Timestamp.now()
    )
}