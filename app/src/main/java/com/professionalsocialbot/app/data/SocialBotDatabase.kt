package com.professionalsocialbot.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

enum class PostStatus {
    PENDING_APPROVAL,
    APPROVED_QUEUED,
    PUBLISHED,
    SKIPPED
}

@Entity(tableName = "social_posts")
data class SocialPostEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileUriString: String,
    val mimeType: String,
    val totalPdfPages: Int = 8,
    val trimPagesToFive: Boolean = true,
    val captionText: String,
    val personalTakeAdded: Boolean = false,
    val status: PostStatus = PostStatus.PENDING_APPROVAL,
    val linkedInPostUrn: String? = null,
    val impressions: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
    val saves: Int = 0,
    val velocityStatus: String = "WAITING",
    val createdAt: Long = System.currentTimeMillis(),
    val publishedAt: Long? = null
)

@Dao
interface SocialPostDao {
    @Query("SELECT * FROM social_posts ORDER BY createdAt DESC")
    fun getAllPostsFlow(): Flow<List<SocialPostEntity>>

    @Query("SELECT * FROM social_posts WHERE status = 'APPROVED_QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextApprovedPost(): SocialPostEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM social_posts WHERE fileName = :fileName)")
    suspend fun isFileAlreadyProcessed(fileName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPost(post: SocialPostEntity)

    @Update
    suspend fun updatePost(post: SocialPostEntity)

    @Query("UPDATE social_posts SET impressions = :impressions, likes = :likes, comments = :comments, saves = :saves, velocityStatus = :velocity WHERE id = :postId")
    suspend fun updateAnalytics(postId: Int, impressions: Int, likes: Int, comments: Int, saves: Int, velocity: String)
}

@Database(entities = [SocialPostEntity::class], version = 1, exportSchema = false)
abstract class SocialBotDatabase : RoomDatabase() {
    abstract fun postDao(): SocialPostDao

    companion object {
        @Volatile private var INSTANCE: SocialBotDatabase? = null

        fun getInstance(context: Context): SocialBotDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SocialBotDatabase::class.java,
                    "professional_social_bot.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

object BrewAlgorithmScorer {
    fun calculateScore(post: SocialPostEntity): Int {
        var score = 55
        val firstLine = post.captionText.lineSequence().firstOrNull() ?: ""
        // Properly escaped backslash for regex whitespace matching
        val firstLineWords = firstLine.trim().split("\\s+".toRegex()).size

        if (firstLineWords in 4..14) score += 15
        if (!post.captionText.contains("http://") && !post.captionText.contains("https://")) score += 10
        val hashtagCount = Regex("#\\w+").findAll(post.captionText).count()
        if (hashtagCount <= 3) score += 5
        if (post.trimPagesToFive || post.totalPdfPages <= 5) score += 10
        if (post.personalTakeAdded) score += 5

        return score.coerceIn(0, 100)
    }
}
