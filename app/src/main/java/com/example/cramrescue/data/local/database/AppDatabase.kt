package com.example.cramrescue.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cramrescue.data.local.dao.CampaignDao
import com.example.cramrescue.data.local.dao.ChatMessageDao
import com.example.cramrescue.data.local.dao.DocumentTextDao
import com.example.cramrescue.data.local.dao.MicroCardDao
import com.example.cramrescue.data.local.dao.MicroMissionDao
import com.example.cramrescue.data.local.dao.OcrDao
import com.example.cramrescue.data.local.dao.SourceDocumentDao
import com.example.cramrescue.data.local.dao.StudyAnalysisDao
import com.example.cramrescue.data.local.dao.StudyAttemptDao
import com.example.cramrescue.data.local.entity.AnalysisPriorityEntity
import com.example.cramrescue.data.local.entity.AnalysisSourceRefEntity
import com.example.cramrescue.data.local.entity.CampaignAnalysisEntity
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.ChatMessageEntity
import com.example.cramrescue.data.local.entity.DocumentTextBlockEntity
import com.example.cramrescue.data.local.entity.MicroCardEntity
import com.example.cramrescue.data.local.entity.MicroCardSourceRefEntity
import com.example.cramrescue.data.local.entity.MicroMissionEntity
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.data.local.entity.StudyAttemptEntity

import com.example.cramrescue.data.local.dao.QuizDao
import com.example.cramrescue.data.local.entity.QuizAttemptEntity
import com.example.cramrescue.data.local.entity.QuizEntity
import com.example.cramrescue.data.local.entity.QuizQuestionEntity

@Database(
    entities = [
        CampaignEntity::class,
        SourceDocumentEntity::class,
        OcrResultEntity::class,
        OcrTextBlockEntity::class,
        CampaignAnalysisEntity::class,
        AnalysisPriorityEntity::class,
        AnalysisSourceRefEntity::class,
        MicroCardEntity::class,
        MicroCardSourceRefEntity::class,
        StudyAttemptEntity::class,
        MicroMissionEntity::class,
        DocumentTextBlockEntity::class,
        QuizEntity::class,
        QuizQuestionEntity::class,
        QuizAttemptEntity::class,
        ChatMessageEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun sourceDocumentDao(): SourceDocumentDao
    abstract fun ocrDao(): OcrDao
    abstract fun studyAnalysisDao(): StudyAnalysisDao
    abstract fun microCardDao(): MicroCardDao
    abstract fun studyAttemptDao(): StudyAttemptDao
    abstract fun microMissionDao(): MicroMissionDao
    abstract fun documentTextDao(): DocumentTextDao
    abstract fun quizDao(): QuizDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        private const val DATABASE_NAME = "cramrescue.db"

        // Milestone 4: adds source_documents, owned by campaigns via a CASCADE foreign key.
        // Existing campaigns data is left untouched.
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_source_documents_campaignId` ON `source_documents` (`campaignId`)"
                )
            }
        }

        // Milestone 5: adds ocr_results (one-to-one with source_documents) and ocr_text_blocks
        // (one-to-many), both owned by source_documents via a CASCADE foreign key. Existing
        // campaigns/source_documents data is left untouched.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_results` (
                        `documentId` INTEGER NOT NULL,
                        `sourceWidthPx` INTEGER NOT NULL,
                        `sourceHeightPx` INTEGER NOT NULL,
                        `characterCount` INTEGER NOT NULL,
                        `isLowText` INTEGER NOT NULL,
                        `processedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`),
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_text_blocks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `left` INTEGER NOT NULL,
                        `top` INTEGER NOT NULL,
                        `right` INTEGER NOT NULL,
                        `bottom` INTEGER NOT NULL,
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ocr_text_blocks_documentId` ON `ocr_text_blocks` (`documentId`)"
                )
            }
        }

        // Milestone 6: PDF ingestion.
        //  - source_documents gains pageCount/displayName (nullable, so existing single-page
        //    image rows migrate without a backfill and read back as 1 page).
        //  - ocr_results becomes one row per (document, page) instead of one row per document,
        //    so each PDF page keeps its own render dimensions and its own low-text flag. SQLite
        //    can't change a primary key in place, so the table is recreated and existing rows are
        //    carried over as page 0. Campaign / SourceDocument / OCR block data is preserved.
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `source_documents` ADD COLUMN `pageCount` INTEGER")
                db.execSQL("ALTER TABLE `source_documents` ADD COLUMN `displayName` TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_results_new` (
                        `documentId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `sourceWidthPx` INTEGER NOT NULL,
                        `sourceHeightPx` INTEGER NOT NULL,
                        `characterCount` INTEGER NOT NULL,
                        `isLowText` INTEGER NOT NULL,
                        `processedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`, `pageIndex`),
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `ocr_results_new` (
                        `documentId`, `pageIndex`, `sourceWidthPx`, `sourceHeightPx`,
                        `characterCount`, `isLowText`, `processedAtEpochMillis`
                    )
                    SELECT `documentId`, 0, `sourceWidthPx`, `sourceHeightPx`,
                           `characterCount`, `isLowText`, `processedAtEpochMillis`
                    FROM `ocr_results`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `ocr_results`")
                db.execSQL("ALTER TABLE `ocr_results_new` RENAME TO `ocr_results`")
            }
        }

        // Milestone 7: AI study analysis. Adds three new tables owned (via CASCADE) by campaigns;
        // no existing table is altered, so Campaign / SourceDocument / per-page OCR data is untouched.
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `campaign_analyses` (
                        `campaignId` INTEGER NOT NULL,
                        `summary` TEXT NOT NULL,
                        `discardedGreenCount` INTEGER NOT NULL,
                        `analyzedPageCount` INTEGER NOT NULL,
                        `lowTextPageCount` INTEGER NOT NULL,
                        `sourceFingerprint` TEXT NOT NULL,
                        `modelId` TEXT NOT NULL,
                        `generatedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`campaignId`),
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_priorities` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `triage` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `bullets` TEXT NOT NULL,
                        `notes` TEXT,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaign_analyses`(`campaignId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_priorities_campaignId` ON `analysis_priorities` (`campaignId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `analysis_source_refs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `priorityId` INTEGER NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `blockOrderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`priorityId`) REFERENCES `analysis_priorities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_source_refs_priorityId` ON `analysis_source_refs` (`priorityId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_analysis_source_refs_documentId` ON `analysis_source_refs` (`documentId`)"
                )
            }
        }

        // Milestone 8: MicroCards. Adds two tables owned (via CASCADE) by campaigns / cards; no
        // existing table is altered, so all M2–M7 data (including analyses) is untouched.
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `micro_cards` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `analysisGeneratedAtEpochMillis` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `triage` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `bullets` TEXT NOT NULL,
                        `notes` TEXT,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_micro_cards_campaignId` ON `micro_cards` (`campaignId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `micro_card_source_refs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `microCardId` INTEGER NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `blockOrderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`microCardId`) REFERENCES `micro_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_micro_card_source_refs_microCardId` ON `micro_card_source_refs` (`microCardId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_micro_card_source_refs_documentId` ON `micro_card_source_refs` (`documentId`)"
                )
            }
        }

        // Milestone 9: Active Recall history. Adds study_attempts only; no existing table is
        // altered, so all M2–M8 data (including MicroCards) is untouched.
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `study_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `microCardId` INTEGER,
                        `triage` TEXT NOT NULL,
                        `cardTitle` TEXT NOT NULL,
                        `assessment` TEXT NOT NULL,
                        `attemptedAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`microCardId`) REFERENCES `micro_cards`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_attempts_campaignId` ON `study_attempts` (`campaignId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_attempts_microCardId` ON `study_attempts` (`microCardId`)")
            }
        }

        // Milestone 10: Micro-missions (FR-10 pause/resume). Adds micro_missions only; no existing
        // table is altered, so all M2–M9 data (including study_attempts) is untouched.
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `micro_missions` (
                        `campaignId` INTEGER NOT NULL,
                        `cardIds` TEXT NOT NULL,
                        `baselineAttemptId` INTEGER NOT NULL,
                        `startedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`campaignId`),
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        // Milestone 13: Word (.docx) text. Adds document_text_blocks only; no existing table is
        // altered, so all M2–M12 data is untouched. Word text has no pages or bounding boxes, so
        // it is not stored in the OCR tables (that would require inventing geometry).
        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `document_text_blocks` (
                        `documentId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `extractedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`, `orderIndex`),
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }

        // Milestone: Chatbot Q&A. Adds chat_messages owned by campaigns via a CASCADE foreign key.
        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `chat_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `sender` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `citationsJson` TEXT,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_chat_messages_campaignId` ON `chat_messages` (`campaignId`)"
                )
            }
        }

        // Milestone: Survival Quiz (FR Campaign Survival Quiz). Adds quizzes, quiz_questions,
        // and quiz_attempts owned via CASCADE foreign keys. Existing data is left untouched.
        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quizzes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `questionCount` INTEGER NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `bestScore` REAL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quizzes_campaignId` ON `quizzes` (`campaignId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quiz_questions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `quizId` INTEGER NOT NULL,
                        `questionOrder` INTEGER NOT NULL,
                        `stem` TEXT NOT NULL,
                        `optionA` TEXT NOT NULL,
                        `optionB` TEXT NOT NULL,
                        `optionC` TEXT NOT NULL,
                        `optionD` TEXT NOT NULL,
                        `correctOptionIndex` INTEGER NOT NULL,
                        `explanation` TEXT NOT NULL,
                        `sourceBlockIdsJson` TEXT NOT NULL,
                        FOREIGN KEY(`quizId`) REFERENCES `quizzes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_questions_quizId` ON `quiz_questions` (`quizId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `quiz_attempts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `quizId` INTEGER NOT NULL,
                        `attemptNumber` INTEGER NOT NULL,
                        `score` REAL NOT NULL,
                        `correctCount` INTEGER NOT NULL,
                        `totalQuestions` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `completedAtEpochMillis` INTEGER NOT NULL,
                        `answersJson` TEXT NOT NULL,
                        FOREIGN KEY(`quizId`) REFERENCES `quizzes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_quizId` ON `quiz_attempts` (`quizId`)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11
                    )
                    .build().also { instance = it }
            }
    }
}
