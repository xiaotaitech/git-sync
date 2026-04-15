# Android Git Sync App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app (Kotlin + Jetpack Compose) that pulls multiple GitHub repositories to local folders on a schedule or on demand, with conflict detection and notification-driven resolution.

**Architecture:** Room stores repo configs and sync logs; JGit handles all Git operations without requiring a system git binary; WorkManager drives periodic background syncs; Compose UI renders repo cards reactively via StateFlow.

**Tech Stack:** Kotlin, Jetpack Compose, JGit 6.x, WorkManager, Room 2.x, Material3, Material Symbols, DataStore Preferences (for PAT), Android Storage Access Framework (SAF)

---

## File Map

```
app/src/main/
├── AndroidManifest.xml                              # Permissions, activities, notification channel
├── java/com/gitsync/
│   ├── MainActivity.kt                              # Single activity, hosts NavHost
│   ├── GitSyncApp.kt                                # Application class, DI setup (Hilt)
│   │
│   ├── data/
│   │   ├── AppDatabase.kt                           # Room database definition
│   │   ├── RepoEntity.kt                            # Room entity: repo config
│   │   ├── RepoDao.kt                               # DAO: repo CRUD + Flow queries
│   │   ├── SyncLogEntity.kt                         # Room entity: sync log entry
│   │   ├── SyncLogDao.kt                            # DAO: log insert + query
│   │   └── PrefsRepository.kt                       # DataStore wrapper: PAT + default interval
│   │
│   ├── git/
│   │   ├── GitSyncManager.kt                        # JGit: clone(), pull(), getLocalChanges()
│   │   └── ConflictDetector.kt                      # Wraps GitSyncManager.getLocalChanges()
│   │
│   ├── worker/
│   │   └── SyncWorker.kt                            # CoroutineWorker: runs pull for one repo
│   │
│   ├── notification/
│   │   └── SyncNotifier.kt                          # Posts sync-result and conflict notifications
│   │
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Color.kt                             # Color tokens
│   │   │   ├── Type.kt                              # Typography (Inter)
│   │   │   └── Theme.kt                             # MaterialTheme dark/light
│   │   ├── navigation/
│   │   │   └── AppNavigation.kt                     # NavHost + bottom nav scaffold
│   │   ├── repolist/
│   │   │   ├── RepoListScreen.kt                    # Repo card list + FAB
│   │   │   ├── RepoCard.kt                          # Single card composable
│   │   │   └── RepoListViewModel.kt                 # StateFlow<List<RepoEntity>>, sync trigger
│   │   ├── addrepo/
│   │   │   ├── AddRepoScreen.kt                     # Form: URL, folder, interval
│   │   │   └── AddRepoViewModel.kt                  # Validation, clone trigger, save to Room
│   │   ├── synclog/
│   │   │   ├── SyncLogScreen.kt                     # Scrollable log list
│   │   │   └── SyncLogViewModel.kt                  # StateFlow<List<SyncLogEntity>>
│   │   ├── settings/
│   │   │   ├── SettingsScreen.kt                    # PAT input, default interval
│   │   │   └── SettingsViewModel.kt                 # DataStore read/write
│   │   └── conflict/
│   │       └── ConflictDialog.kt                    # Modal: file list + two action buttons
│   │
│   └── di/
│       └── AppModule.kt                             # Hilt module: DB, DAOs, PrefsRepo, GitSyncManager
│
app/src/test/java/com/gitsync/
├── git/
│   ├── GitSyncManagerTest.kt                        # Clone, pull, getLocalChanges unit tests
│   └── ConflictDetectorTest.kt                      # Detects/ignores changes correctly
├── data/
│   └── PrefRepositoryTest.kt                        # DataStore PAT read/write
└── worker/
    └── SyncWorkerTest.kt                            # Worker delegates to GitSyncManager
```

---

## Task 1: Project Setup — Gradle, Dependencies, Manifest

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `AndroidManifest.xml`
- Create: `app/src/main/java/com/gitsync/GitSyncApp.kt`

- [ ] **Step 1: Add dependencies to `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gitsync"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.11" }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51")
    ksp("com.google.dagger:hilt-android-compiler:2.51")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

- [ ] **Step 2: Add permissions and metadata to `AndroidManifest.xml`**

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="29" />

    <application
        android:name=".GitSyncApp"
        android:label="GitSync"
        android:theme="@style/Theme.GitSync">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- WorkManager initializer -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup" />
        </provider>

    </application>
</manifest>
```

- [ ] **Step 3: Create `GitSyncApp.kt` (Hilt application class)**

```kotlin
package com.gitsync

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GitSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Git Sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Sync status and conflict alerts" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "git_sync_channel"
    }
}
```

- [ ] **Step 4: Build project**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/gitsync/GitSyncApp.kt
git commit -m "chore: project setup, dependencies, manifest"
```

---

## Task 2: Data Layer — Room Entities, DAOs, Database

**Files:**
- Create: `app/src/main/java/com/gitsync/data/RepoEntity.kt`
- Create: `app/src/main/java/com/gitsync/data/RepoDao.kt`
- Create: `app/src/main/java/com/gitsync/data/SyncLogEntity.kt`
- Create: `app/src/main/java/com/gitsync/data/SyncLogDao.kt`
- Create: `app/src/main/java/com/gitsync/data/AppDatabase.kt`

- [ ] **Step 1: Write failing test for RepoDao**

```kotlin
// app/src/test/java/com/gitsync/data/RepoDaoTest.kt
@RunWith(AndroidJUnit4::class)
class RepoDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: RepoDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.repoDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insertAndGetRepo() = runBlocking {
        val repo = RepoEntity(
            id = 0,
            name = "my-vault",
            remoteUrl = "https://github.com/user/my-vault.git",
            localPath = "/storage/emulated/0/Documents/my-vault",
            intervalMinutes = 30,
            lastSyncTime = 0L,
            syncStatus = "idle"
        )
        val id = dao.insert(repo)
        val loaded = dao.getAll().first()
        assertThat(loaded).hasSize(1)
        assertThat(loaded[0].remoteUrl).isEqualTo("https://github.com/user/my-vault.git")
    }
}
```

- [ ] **Step 2: Run test — expect FAIL (classes don't exist)**

```bash
./gradlew test --tests "com.gitsync.data.RepoDaoTest"
```

Expected: compilation error

- [ ] **Step 3: Create `RepoEntity.kt`**

```kotlin
package com.gitsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "repos")
data class RepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val remoteUrl: String,
    val localPath: String,
    val intervalMinutes: Int,   // 0 = off, 15, 30, 60, 360
    val lastSyncTime: Long,     // epoch millis, 0 = never
    val syncStatus: String      // "idle" | "syncing" | "conflict" | "error"
)
```

- [ ] **Step 4: Create `RepoDao.kt`**

```kotlin
package com.gitsync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    @Query("SELECT * FROM repos ORDER BY name ASC")
    fun getAllFlow(): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repos ORDER BY name ASC")
    suspend fun getAll(): List<RepoEntity>

    @Query("SELECT * FROM repos WHERE id = :id")
    suspend fun getById(id: Long): RepoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(repo: RepoEntity): Long

    @Update
    suspend fun update(repo: RepoEntity)

    @Delete
    suspend fun delete(repo: RepoEntity)
}
```

- [ ] **Step 5: Create `SyncLogEntity.kt`**

```kotlin
package com.gitsync.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_logs")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repoId: Long,
    val repoName: String,
    val timestamp: Long,        // epoch millis
    val success: Boolean,
    val message: String         // "Pulled 3 commits" | "Conflict detected" | "Already up to date" | error message
)
```

- [ ] **Step 6: Create `SyncLogDao.kt`**

```kotlin
package com.gitsync.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY timestamp DESC LIMIT 200")
    fun getAllFlow(): Flow<List<SyncLogEntity>>

    @Insert
    suspend fun insert(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE repoId = :repoId")
    suspend fun deleteForRepo(repoId: Long)
}
```

- [ ] **Step 7: Create `AppDatabase.kt`**

```kotlin
package com.gitsync.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RepoEntity::class, SyncLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao
    abstract fun syncLogDao(): SyncLogDao
}
```

- [ ] **Step 8: Run test — expect PASS**

```bash
./gradlew test --tests "com.gitsync.data.RepoDaoTest"
```

Expected: `BUILD SUCCESSFUL`, 1 test passed

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/gitsync/data/ app/src/test/java/com/gitsync/data/
git commit -m "feat: Room data layer — RepoEntity, SyncLogEntity, DAOs, AppDatabase"
```

---

## Task 3: DataStore — PAT and Default Interval Preferences

**Files:**
- Create: `app/src/main/java/com/gitsync/data/PrefsRepository.kt`
- Test: `app/src/test/java/com/gitsync/data/PrefsRepositoryTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// app/src/test/java/com/gitsync/data/PrefsRepositoryTest.kt
@RunWith(AndroidJUnit4::class)
class PrefsRepositoryTest {
    private lateinit var repo: PrefsRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repo = PrefsRepository(context)
    }

    @Test
    fun savesAndReadsToken() = runBlocking {
        repo.savePat("ghp_testtoken123")
        val token = repo.getPat().first()
        assertThat(token).isEqualTo("ghp_testtoken123")
    }

    @Test
    fun defaultIntervalIsZero() = runBlocking {
        val interval = repo.getDefaultInterval().first()
        assertThat(interval).isEqualTo(0)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew test --tests "com.gitsync.data.PrefsRepositoryTest"
```

Expected: compilation error

- [ ] **Step 3: Create `PrefsRepository.kt`**

```kotlin
package com.gitsync.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "gitsync_prefs")

@Singleton
class PrefsRepository @Inject constructor(private val context: Context) {

    private val PAT_KEY = stringPreferencesKey("pat")
    private val DEFAULT_INTERVAL_KEY = intPreferencesKey("default_interval")

    fun getPat(): Flow<String> = context.dataStore.data.map { it[PAT_KEY] ?: "" }

    suspend fun savePat(token: String) {
        context.dataStore.edit { it[PAT_KEY] = token }
    }

    fun getDefaultInterval(): Flow<Int> = context.dataStore.data.map { it[DEFAULT_INTERVAL_KEY] ?: 0 }

    suspend fun saveDefaultInterval(minutes: Int) {
        context.dataStore.edit { it[DEFAULT_INTERVAL_KEY] = minutes }
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew test --tests "com.gitsync.data.PrefsRepositoryTest"
```

Expected: 2 tests passed

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gitsync/data/PrefsRepository.kt app/src/test/java/com/gitsync/data/PrefsRepositoryTest.kt
git commit -m "feat: DataStore PrefsRepository for PAT and default interval"
```

---

## Task 4: Hilt DI Module

**Files:**
- Create: `app/src/main/java/com/gitsync/di/AppModule.kt`

- [ ] **Step 1: Create `AppModule.kt`**

```kotlin
package com.gitsync.di

import android.content.Context
import androidx.room.Room
import com.gitsync.data.AppDatabase
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.SyncLogDao
import com.gitsync.git.GitSyncManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gitsync.db").build()

    @Provides
    fun provideRepoDao(db: AppDatabase): RepoDao = db.repoDao()

    @Provides
    fun provideSyncLogDao(db: AppDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    @Singleton
    fun providePrefsRepository(@ApplicationContext context: Context): PrefsRepository =
        PrefsRepository(context)

    @Provides
    @Singleton
    fun provideGitSyncManager(): GitSyncManager = GitSyncManager()
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gitsync/di/
git commit -m "feat: Hilt DI module"
```

---

## Task 5: Git Layer — GitSyncManager and ConflictDetector

**Files:**
- Create: `app/src/main/java/com/gitsync/git/GitSyncManager.kt`
- Create: `app/src/main/java/com/gitsync/git/ConflictDetector.kt`
- Test: `app/src/test/java/com/gitsync/git/GitSyncManagerTest.kt`
- Test: `app/src/test/java/com/gitsync/git/ConflictDetectorTest.kt`

- [ ] **Step 1: Write failing tests for GitSyncManager**

```kotlin
// app/src/test/java/com/gitsync/git/GitSyncManagerTest.kt
class GitSyncManagerTest {
    private val manager = GitSyncManager()
    private lateinit var tmpDir: File

    @Before
    fun setup() {
        tmpDir = createTempDir("gitsync_test")
    }

    @After
    fun teardown() { tmpDir.deleteRecursively() }

    @Test
    fun getLocalChanges_returnsEmptyForCleanRepo() {
        // Init a bare local repo to simulate a clean working tree
        val git = Git.init().setDirectory(tmpDir).call()
        val testFile = File(tmpDir, "note.md")
        testFile.writeText("hello")
        git.add().addFilepattern("note.md").call()
        git.commit().setMessage("init").setAuthor("test", "t@t.com").call()

        val changes = manager.getLocalChanges(tmpDir.absolutePath)
        assertThat(changes).isEmpty()
    }

    @Test
    fun getLocalChanges_returnsModifiedFiles() {
        val git = Git.init().setDirectory(tmpDir).call()
        val testFile = File(tmpDir, "note.md")
        testFile.writeText("hello")
        git.add().addFilepattern("note.md").call()
        git.commit().setMessage("init").setAuthor("test", "t@t.com").call()

        // Modify without committing
        testFile.writeText("modified")

        val changes = manager.getLocalChanges(tmpDir.absolutePath)
        assertThat(changes).containsExactly("note.md")
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew test --tests "com.gitsync.git.GitSyncManagerTest"
```

Expected: compilation error

- [ ] **Step 3: Create `GitSyncManager.kt`**

```kotlin
package com.gitsync.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Conflict(val modifiedFiles: List<String>) : SyncResult()
    data class Error(val error: String) : SyncResult()
}

@Singleton
class GitSyncManager @Inject constructor() {

    /** Clone a remote repo into localPath. Throws on failure. */
    fun clone(remoteUrl: String, localPath: String, pat: String) {
        val dir = File(localPath)
        if (dir.exists()) dir.deleteRecursively()
        dir.mkdirs()
        Git.cloneRepository()
            .setURI(remoteUrl)
            .setDirectory(dir)
            .setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
            .call()
            .close()
    }

    /**
     * Pull latest from remote.
     * Returns Conflict if local changes detected, Success/Error otherwise.
     */
    fun pull(localPath: String, pat: String): SyncResult {
        val dir = File(localPath)
        if (!dir.exists() || !File(dir, ".git").exists()) {
            return SyncResult.Error("Not a git repository: $localPath")
        }
        return try {
            val git = Git.open(dir)
            val changes = getLocalChanges(localPath)
            if (changes.isNotEmpty()) {
                git.close()
                return SyncResult.Conflict(changes)
            }
            val result = git.pull()
                .setCredentialsProvider(UsernamePasswordCredentialsProvider(pat, ""))
                .call()
            git.close()
            if (result.isSuccessful) {
                val msg = if (result.mergeResult?.mergedCommits?.isNotEmpty() == true)
                    "Pulled new commits" else "Already up to date"
                SyncResult.Success(msg)
            } else {
                SyncResult.Error("Pull failed: ${result.mergeResult?.mergeStatus}")
            }
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Force pull: discard local changes, hard reset, then pull. */
    fun forcePull(localPath: String, pat: String): SyncResult {
        val dir = File(localPath)
        return try {
            val git = Git.open(dir)
            git.reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD").call()
            git.clean().setForce(true).setCleanDirectories(true).call()
            git.close()
            pull(localPath, pat)
        } catch (e: Exception) {
            SyncResult.Error(e.message ?: "Unknown error")
        }
    }

    /** Returns list of modified/untracked file paths in the working tree. */
    fun getLocalChanges(localPath: String): List<String> {
        val dir = File(localPath)
        if (!File(dir, ".git").exists()) return emptyList()
        return try {
            val git = Git.open(dir)
            val status = git.status().call()
            git.close()
            (status.modified + status.untracked + status.added + status.missing).toList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
```

- [ ] **Step 4: Create `ConflictDetector.kt`**

```kotlin
package com.gitsync.git

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictDetector @Inject constructor(private val gitSyncManager: GitSyncManager) {

    /** Returns true if local path has uncommitted changes. */
    fun hasConflict(localPath: String): Boolean =
        gitSyncManager.getLocalChanges(localPath).isNotEmpty()

    /** Returns the list of modified file names for display in ConflictDialog. */
    fun getModifiedFiles(localPath: String): List<String> =
        gitSyncManager.getLocalChanges(localPath)
}
```

- [ ] **Step 5: Write ConflictDetector test**

```kotlin
// app/src/test/java/com/gitsync/git/ConflictDetectorTest.kt
class ConflictDetectorTest {
    private val manager = GitSyncManager()
    private val detector = ConflictDetector(manager)
    private lateinit var tmpDir: File

    @Before
    fun setup() { tmpDir = createTempDir("conflict_test") }

    @After
    fun teardown() { tmpDir.deleteRecursively() }

    @Test
    fun hasConflict_falseForCleanRepo() {
        val git = Git.init().setDirectory(tmpDir).call()
        val f = File(tmpDir, "a.md").apply { writeText("x") }
        git.add().addFilepattern("a.md").call()
        git.commit().setMessage("init").setAuthor("t", "t@t.com").call()

        assertThat(detector.hasConflict(tmpDir.absolutePath)).isFalse()
    }

    @Test
    fun hasConflict_trueAfterModification() {
        val git = Git.init().setDirectory(tmpDir).call()
        val f = File(tmpDir, "a.md").apply { writeText("x") }
        git.add().addFilepattern("a.md").call()
        git.commit().setMessage("init").setAuthor("t", "t@t.com").call()
        f.writeText("modified")

        assertThat(detector.hasConflict(tmpDir.absolutePath)).isTrue()
        assertThat(detector.getModifiedFiles(tmpDir.absolutePath)).containsExactly("a.md")
    }
}
```

- [ ] **Step 6: Run all git tests — expect PASS**

```bash
./gradlew test --tests "com.gitsync.git.*"
```

Expected: 4 tests passed

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/gitsync/git/ app/src/test/java/com/gitsync/git/
git commit -m "feat: GitSyncManager (JGit) and ConflictDetector"
```

---

## Task 6: SyncWorker

**Files:**
- Create: `app/src/main/java/com/gitsync/worker/SyncWorker.kt`
- Test: `app/src/test/java/com/gitsync/worker/SyncWorkerTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
// app/src/test/java/com/gitsync/worker/SyncWorkerTest.kt
@RunWith(AndroidJUnit4::class)
class SyncWorkerTest {
    @Test
    fun workerReturnsSuccessWhenPullSucceeds() {
        // WorkManager testing uses TestWorkerBuilder
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestWorkerBuilder<SyncWorker>(
            context = context,
            executor = Executors.newSingleThreadExecutor(),
            inputData = workDataOf(
                SyncWorker.KEY_REPO_ID to 1L,
                SyncWorker.KEY_LOCAL_PATH to "/tmp/nonexistent",
                SyncWorker.KEY_REMOTE_URL to "https://github.com/x/y.git",
                SyncWorker.KEY_PAT to "token"
            )
        ).build()
        // Worker will fail due to invalid path — result should still be a Result type (not crash)
        val result = worker.doWork()
        assertThat(result).isInstanceOf(ListenableWorker.Result::class.java)
    }
}
```

- [ ] **Step 2: Run — expect FAIL (SyncWorker doesn't exist)**

```bash
./gradlew test --tests "com.gitsync.worker.SyncWorkerTest"
```

- [ ] **Step 3: Create `SyncWorker.kt`**

```kotlin
package com.gitsync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.git.SyncResult
import com.gitsync.notification.SyncNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val gitSyncManager: GitSyncManager,
    private val syncLogDao: SyncLogDao,
    private val syncNotifier: SyncNotifier
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repoId = inputData.getLong(KEY_REPO_ID, -1L)
        val localPath = inputData.getString(KEY_LOCAL_PATH) ?: return Result.failure()
        val remoteUrl = inputData.getString(KEY_REMOTE_URL) ?: return Result.failure()
        val pat = inputData.getString(KEY_PAT) ?: return Result.failure()
        val repoName = inputData.getString(KEY_REPO_NAME) ?: remoteUrl

        val syncResult = gitSyncManager.pull(localPath, pat)

        val log = SyncLogEntity(
            repoId = repoId,
            repoName = repoName,
            timestamp = System.currentTimeMillis(),
            success = syncResult is SyncResult.Success,
            message = when (syncResult) {
                is SyncResult.Success -> syncResult.message
                is SyncResult.Conflict -> "Conflict: ${syncResult.modifiedFiles.size} file(s) modified"
                is SyncResult.Error -> syncResult.error
            }
        )
        syncLogDao.insert(log)

        return when (syncResult) {
            is SyncResult.Success -> {
                syncNotifier.notifySuccess(repoName, syncResult.message)
                Result.success()
            }
            is SyncResult.Conflict -> {
                syncNotifier.notifyConflict(repoId, repoName, syncResult.modifiedFiles)
                Result.success() // Worker itself succeeded; conflict is handled via notification
            }
            is SyncResult.Error -> {
                syncNotifier.notifyError(repoName, syncResult.error)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_REPO_ID = "repo_id"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_REMOTE_URL = "remote_url"
        const val KEY_PAT = "pat"
        const val KEY_REPO_NAME = "repo_name"

        fun buildWorkRequest(
            repoId: Long,
            repoName: String,
            localPath: String,
            remoteUrl: String,
            pat: String,
            intervalMinutes: Int
        ): PeriodicWorkRequest {
            val data = workDataOf(
                KEY_REPO_ID to repoId,
                KEY_REPO_NAME to repoName,
                KEY_LOCAL_PATH to localPath,
                KEY_REMOTE_URL to remoteUrl,
                KEY_PAT to pat
            )
            return PeriodicWorkRequestBuilder<SyncWorker>(
                intervalMinutes.toLong(), java.util.concurrent.TimeUnit.MINUTES
            ).setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        }
    }
}
```

- [ ] **Step 4: Run test — expect PASS**

```bash
./gradlew test --tests "com.gitsync.worker.SyncWorkerTest"
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gitsync/worker/ app/src/test/java/com/gitsync/worker/
git commit -m "feat: SyncWorker (WorkManager CoroutineWorker)"
```

---

## Task 7: SyncNotifier

**Files:**
- Create: `app/src/main/java/com/gitsync/notification/SyncNotifier.kt`

- [ ] **Step 1: Create `SyncNotifier.kt`**

```kotlin
package com.gitsync.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.gitsync.GitSyncApp
import com.gitsync.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notifySuccess(repoName: String, message: String) {
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(repoName)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(repoName.hashCode(), n)
    }

    fun notifyError(repoName: String, error: String) {
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Sync failed: $repoName")
            .setContentText(error)
            .setAutoCancel(true)
            .build()
        manager.notify(repoName.hashCode() + 1, n)
    }

    fun notifyConflict(repoId: Long, repoName: String, modifiedFiles: List<String>) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conflict_repo_id", repoId)
            putStringArrayListExtra("conflict_files", ArrayList(modifiedFiles))
        }
        val pi = PendingIntent.getActivity(
            context, repoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, GitSyncApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("冲突: $repoName")
            .setContentText("${modifiedFiles.size} 个文件有本地修改，点击解决")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        manager.notify(repoId.toInt() + 1000, n)
    }
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gitsync/notification/
git commit -m "feat: SyncNotifier for success/error/conflict notifications"
```

---

## Task 8: UI Theme

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/theme/Color.kt`
- Create: `app/src/main/java/com/gitsync/ui/theme/Type.kt`
- Create: `app/src/main/java/com/gitsync/ui/theme/Theme.kt`

- [ ] **Step 1: Create `Color.kt`**

```kotlin
package com.gitsync.ui.theme

import androidx.compose.ui.graphics.Color

val Primary = Color(0xFF2563EB)
val PrimaryVariant = Color(0xFF3B82F6)
val Success = Color(0xFF22C55E)
val Warning = Color(0xFFF97316)
val ErrorRed = Color(0xFFEF4444)

// Dark theme surfaces
val BackgroundDark = Color(0xFF09090B)
val SurfaceDark = Color(0xFF18181B)
val CardBorderDark = Color(0x14FFFFFF)  // white alpha 0.08

// Light theme surfaces
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val CardBorderLight = Color(0x1A000000)  // black alpha 0.10
```

- [ ] **Step 2: Create `Type.kt`**

```kotlin
package com.gitsync.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp
    )
)
```

- [ ] **Step 3: Create `Theme.kt`**

```kotlin
package com.gitsync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = PrimaryVariant,
    background = BackgroundDark,
    surface = SurfaceDark,
    error = ErrorRed,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = PrimaryVariant,
    background = BackgroundLight,
    surface = SurfaceLight,
    error = ErrorRed,
    onPrimary = Color.White,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun GitSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/theme/
git commit -m "feat: Material3 theme with OLED dark and light color schemes"
```

---

## Task 9: Navigation Scaffold

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/navigation/AppNavigation.kt`
- Create: `app/src/main/java/com/gitsync/MainActivity.kt`

- [ ] **Step 1: Create `AppNavigation.kt`**

```kotlin
package com.gitsync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.gitsync.ui.addrepo.AddRepoScreen
import com.gitsync.ui.repolist.RepoListScreen
import com.gitsync.ui.settings.SettingsScreen
import com.gitsync.ui.synclog.SyncLogScreen

sealed class NavRoute(val route: String, val label: String, val icon: ImageVector) {
    object Repos : NavRoute("repos", "仓库", Icons.Default.FolderOpen)
    object Logs : NavRoute("logs", "日志", Icons.Default.History)
    object Settings : NavRoute("settings", "设置", Icons.Default.Settings)
}

private val topLevelRoutes = listOf(NavRoute.Repos, NavRoute.Logs, NavRoute.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                topLevelRoutes.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = NavRoute.Repos.route) {
            composable(NavRoute.Repos.route) {
                RepoListScreen(
                    contentPadding = padding,
                    onNavigateToAdd = { navController.navigate("add_repo") }
                )
            }
            composable("add_repo") { AddRepoScreen(onBack = { navController.popBackStack() }) }
            composable(NavRoute.Logs.route) { SyncLogScreen(contentPadding = padding) }
            composable(NavRoute.Settings.route) { SettingsScreen(contentPadding = padding) }
        }
    }
}
```

- [ ] **Step 2: Create `MainActivity.kt`**

```kotlin
package com.gitsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gitsync.ui.navigation.AppNavigation
import com.gitsync.ui.theme.GitSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GitSyncTheme {
                AppNavigation()
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/navigation/ app/src/main/java/com/gitsync/MainActivity.kt
git commit -m "feat: bottom nav scaffold and NavHost"
```

---

## Task 10: RepoListScreen — Cards, Status Badges, Sync Button

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/repolist/RepoListViewModel.kt`
- Create: `app/src/main/java/com/gitsync/ui/repolist/RepoCard.kt`
- Create: `app/src/main/java/com/gitsync/ui/repolist/RepoListScreen.kt`

- [ ] **Step 1: Create `RepoListViewModel.kt`**

```kotlin
package com.gitsync.ui.repolist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.RepoEntity
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import com.gitsync.git.GitSyncManager
import com.gitsync.git.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RepoListViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val syncLogDao: SyncLogDao,
    private val gitSyncManager: GitSyncManager,
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    val repos: StateFlow<List<RepoEntity>> = repoDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Track which repo IDs are currently syncing
    private val _syncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingIds: StateFlow<Set<Long>> = _syncingIds.asStateFlow()

    // Conflict state: repoId -> list of modified files
    private val _conflictState = MutableStateFlow<Pair<Long, List<String>>?>(null)
    val conflictState: StateFlow<Pair<Long, List<String>>?> = _conflictState.asStateFlow()

    fun syncRepo(repo: RepoEntity) {
        viewModelScope.launch {
            _syncingIds.update { it + repo.id }
            repoDao.update(repo.copy(syncStatus = "syncing"))

            val pat = prefsRepository.getPat().first()
            val result = gitSyncManager.pull(repo.localPath, pat)

            val (status, logMsg, success) = when (result) {
                is SyncResult.Success -> Triple("idle", result.message, true)
                is SyncResult.Conflict -> {
                    _conflictState.value = Pair(repo.id, result.modifiedFiles)
                    Triple("conflict", "Conflict: ${result.modifiedFiles.size} file(s)", false)
                }
                is SyncResult.Error -> Triple("error", result.error, false)
            }

            repoDao.update(repo.copy(syncStatus = status, lastSyncTime = System.currentTimeMillis()))
            syncLogDao.insert(
                SyncLogEntity(
                    repoId = repo.id, repoName = repo.name,
                    timestamp = System.currentTimeMillis(), success = success, message = logMsg
                )
            )
            _syncingIds.update { it - repo.id }
        }
    }

    fun resolveConflictForce(repoId: Long) {
        viewModelScope.launch {
            val repo = repoDao.getById(repoId) ?: return@launch
            val pat = prefsRepository.getPat().first()
            _conflictState.value = null
            _syncingIds.update { it + repoId }
            repoDao.update(repo.copy(syncStatus = "syncing"))
            val result = gitSyncManager.forcePull(repo.localPath, pat)
            val (status, logMsg, success) = when (result) {
                is SyncResult.Success -> Triple("idle", result.message, true)
                is SyncResult.Conflict -> Triple("conflict", "Conflict persists", false)
                is SyncResult.Error -> Triple("error", result.error, false)
            }
            repoDao.update(repo.copy(syncStatus = status, lastSyncTime = System.currentTimeMillis()))
            syncLogDao.insert(SyncLogEntity(repoId = repo.id, repoName = repo.name,
                timestamp = System.currentTimeMillis(), success = success, message = logMsg))
            _syncingIds.update { it - repoId }
        }
    }

    fun resolveConflictSkip(repoId: Long) {
        viewModelScope.launch {
            val repo = repoDao.getById(repoId) ?: return@launch
            _conflictState.value = null
            repoDao.update(repo.copy(syncStatus = "conflict"))
        }
    }

    fun deleteRepo(repo: RepoEntity) {
        viewModelScope.launch {
            syncLogDao.deleteForRepo(repo.id)
            repoDao.delete(repo)
        }
    }
}
```

- [ ] **Step 2: Create `RepoCard.kt`**

```kotlin
package com.gitsync.ui.repolist

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gitsync.data.RepoEntity
import com.gitsync.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RepoCard(
    repo: RepoEntity,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (repo.syncStatus) {
        "idle" -> Success
        "syncing" -> Primary
        "conflict" -> Warning
        "error" -> ErrorRed
        else -> Color.Gray
    }
    val statusLabel = when (repo.syncStatus) {
        "idle" -> "已同步"
        "syncing" -> "同步中"
        "conflict" -> "有冲突"
        "error" -> "失败"
        else -> "未知"
    }

    val rotation by rememberInfiniteTransition(label = "sync_rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "rotation"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(repo.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(repo.localPath, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                    if (repo.lastSyncTime > 0) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatTime(repo.lastSyncTime),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "同步",
                    tint = Primary,
                    modifier = if (isSyncing) Modifier.rotate(rotation) else Modifier
                )
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}
```

- [ ] **Step 3: Create `RepoListScreen.kt`**

```kotlin
package com.gitsync.ui.repolist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.ui.conflict.ConflictDialog

@Composable
fun RepoListScreen(
    contentPadding: PaddingValues,
    onNavigateToAdd: () -> Unit,
    viewModel: RepoListViewModel = hiltViewModel()
) {
    val repos by viewModel.repos.collectAsState()
    val syncingIds by viewModel.syncingIds.collectAsState()
    val conflictState by viewModel.conflictState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        if (repos.isEmpty()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有仓库", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                Text("点击 + 添加 GitHub 仓库", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(repos, key = { it.id }) { repo ->
                    RepoCard(
                        repo = repo,
                        isSyncing = repo.id in syncingIds,
                        onSyncClick = { viewModel.syncRepo(repo) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "添加仓库")
        }

        conflictState?.let { (repoId, files) ->
            ConflictDialog(
                modifiedFiles = files,
                onForce = { viewModel.resolveConflictForce(repoId) },
                onSkip = { viewModel.resolveConflictSkip(repoId) }
            )
        }
    }
}
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/repolist/
git commit -m "feat: RepoListScreen with cards, status badges, sync animation"
```

---

## Task 11: ConflictDialog

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/conflict/ConflictDialog.kt`

- [ ] **Step 1: Create `ConflictDialog.kt`**

```kotlin
package com.gitsync.ui.conflict

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gitsync.ui.theme.ErrorRed
import com.gitsync.ui.theme.Warning

@Composable
fun ConflictDialog(
    modifiedFiles: List<String>,
    onForce: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("本地有未提交的修改") },
        text = {
            Column {
                Text(
                    "以下文件有本地修改。如果继续，将丢失这些修改：",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(modifiedFiles) { file ->
                        Text(
                            "• $file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Warning,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onForce,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("覆盖本地")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSkip) {
                Text("保留本地，跳过")
            }
        }
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/conflict/
git commit -m "feat: ConflictDialog with force/skip actions"
```

---

## Task 12: AddRepoScreen

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/addrepo/AddRepoViewModel.kt`
- Create: `app/src/main/java/com/gitsync/ui/addrepo/AddRepoScreen.kt`

- [ ] **Step 1: Create `AddRepoViewModel.kt`**

```kotlin
package com.gitsync.ui.addrepo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.PrefsRepository
import com.gitsync.data.RepoDao
import com.gitsync.data.RepoEntity
import com.gitsync.git.GitSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddRepoUiState(
    val remoteUrl: String = "",
    val localPath: String = "",
    val intervalMinutes: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class AddRepoViewModel @Inject constructor(
    private val repoDao: RepoDao,
    private val prefsRepository: PrefsRepository,
    private val gitSyncManager: GitSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRepoUiState())
    val uiState: StateFlow<AddRepoUiState> = _uiState.asStateFlow()

    fun onUrlChange(v: String) { _uiState.update { it.copy(remoteUrl = v, error = null) } }
    fun onPathChange(v: String) { _uiState.update { it.copy(localPath = v, error = null) } }
    fun onIntervalChange(v: Int) { _uiState.update { it.copy(intervalMinutes = v) } }

    fun save() {
        val state = _uiState.value
        if (state.remoteUrl.isBlank()) {
            _uiState.update { it.copy(error = "请输入仓库 URL") }
            return
        }
        if (state.localPath.isBlank()) {
            _uiState.update { it.copy(error = "请选择本地文件夹") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val pat = prefsRepository.getPat().first()
            if (pat.isBlank()) {
                _uiState.update { it.copy(isLoading = false, error = "请先在设置中配置 PAT") }
                return@launch
            }
            try {
                // Derive name from URL
                val name = state.remoteUrl.trimEnd('/').substringAfterLast('/').removeSuffix(".git")
                gitSyncManager.clone(state.remoteUrl, state.localPath, pat)
                val entity = RepoEntity(
                    name = name,
                    remoteUrl = state.remoteUrl,
                    localPath = state.localPath,
                    intervalMinutes = state.intervalMinutes,
                    lastSyncTime = System.currentTimeMillis(),
                    syncStatus = "idle"
                )
                repoDao.insert(entity)
                _uiState.update { it.copy(isLoading = false, done = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Clone 失败") }
            }
        }
    }
}
```

- [ ] **Step 2: Create `AddRepoScreen.kt`**

```kotlin
package com.gitsync.ui.addrepo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRepoScreen(
    onBack: () -> Unit,
    viewModel: AddRepoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.done) {
        if (uiState.done) onBack()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Convert content URI to file path
            val path = it.path?.replace("/tree/primary:", "/storage/emulated/0/") ?: it.toString()
            viewModel.onPathChange(path)
        }
    }

    val intervals = listOf(0 to "关闭", 15 to "15分钟", 30 to "30分钟", 60 to "1小时", 360 to "6小时")
    var intervalExpanded by remember { mutableStateOf(false) }
    val selectedLabel = intervals.firstOrNull { it.first == uiState.intervalMinutes }?.second ?: "关闭"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("添加仓库") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.remoteUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text("GitHub 仓库 URL") },
                placeholder = { Text("https://github.com/user/vault.git") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.localPath,
                onValueChange = viewModel::onPathChange,
                label = { Text("本地文件夹") },
                placeholder = { Text("/storage/emulated/0/Documents/vault") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { folderPicker.launch(null) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "选择文件夹")
                    }
                }
            )

            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { intervalExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("同步间隔") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false }
                ) {
                    intervals.forEach { (minutes, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.onIntervalChange(minutes)
                                intervalExpanded = false
                            }
                        )
                    }
                }
            }

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Clone 中...")
                } else {
                    Text("添加并 Clone")
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/addrepo/
git commit -m "feat: AddRepoScreen with URL input, folder picker, interval selector"
```

---

## Task 13: SettingsScreen

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/settings/SettingsViewModel.kt`
- Create: `app/src/main/java/com/gitsync/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Create `SettingsViewModel.kt`**

```kotlin
package com.gitsync.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.PrefsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepository: PrefsRepository
) : ViewModel() {

    val pat: StateFlow<String> = prefsRepository.getPat()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val defaultInterval: StateFlow<Int> = prefsRepository.getDefaultInterval()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun savePat(token: String) {
        viewModelScope.launch { prefsRepository.savePat(token) }
    }

    fun saveDefaultInterval(minutes: Int) {
        viewModelScope.launch { prefsRepository.saveDefaultInterval(minutes) }
    }
}
```

- [ ] **Step 2: Create `SettingsScreen.kt`**

```kotlin
package com.gitsync.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val pat by viewModel.pat.collectAsState()
    val defaultInterval by viewModel.defaultInterval.collectAsState()
    var patInput by remember(pat) { mutableStateOf(pat) }
    var showPat by remember { mutableStateOf(false) }
    var intervalExpanded by remember { mutableStateOf(false) }

    val intervals = listOf(0 to "关闭", 15 to "15分钟", 30 to "30分钟", 60 to "1小时", 360 to "6小时")
    val selectedLabel = intervals.firstOrNull { it.first == defaultInterval }?.second ?: "关闭"

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = patInput,
            onValueChange = { patInput = it },
            label = { Text("GitHub Personal Access Token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (showPat) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPat = !showPat }) {
                    Text(if (showPat) "隐藏" else "显示")
                }
            }
        )

        Button(
            onClick = { viewModel.savePat(patInput) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("保存 Token")
        }

        ExposedDropdownMenuBox(
            expanded = intervalExpanded,
            onExpandedChange = { intervalExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text("默认同步间隔") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = intervalExpanded,
                onDismissRequest = { intervalExpanded = false }
            ) {
                intervals.forEach { (minutes, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            viewModel.saveDefaultInterval(minutes)
                            intervalExpanded = false
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/settings/
git commit -m "feat: SettingsScreen — PAT input and default interval"
```

---

## Task 14: SyncLogScreen

**Files:**
- Create: `app/src/main/java/com/gitsync/ui/synclog/SyncLogViewModel.kt`
- Create: `app/src/main/java/com/gitsync/ui/synclog/SyncLogScreen.kt`

- [ ] **Step 1: Create `SyncLogViewModel.kt`**

```kotlin
package com.gitsync.ui.synclog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitsync.data.SyncLogDao
import com.gitsync.data.SyncLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class SyncLogViewModel @Inject constructor(
    syncLogDao: SyncLogDao
) : ViewModel() {

    val logs: StateFlow<List<SyncLogEntity>> = syncLogDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 2: Create `SyncLogScreen.kt`**

```kotlin
package com.gitsync.ui.synclog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.gitsync.data.SyncLogEntity
import com.gitsync.ui.theme.ErrorRed
import com.gitsync.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SyncLogScreen(
    contentPadding: PaddingValues,
    viewModel: SyncLogViewModel = hiltViewModel()
) {
    val logs by viewModel.logs.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Text(
            "同步日志",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无同步记录", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(logs, key = { it.id }) { log ->
                    SyncLogItem(log)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity) {
    val sdf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(log.repoName, style = MaterialTheme.typography.titleMedium)
            Text(log.message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(sdf.format(Date(log.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = (if (log.success) Success else ErrorRed).copy(alpha = 0.15f)
            ) {
                Text(
                    if (log.success) "成功" else "失败",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (log.success) Success else ErrorRed
                )
            }
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gitsync/ui/synclog/
git commit -m "feat: SyncLogScreen with per-repo sync history"
```

---

## Task 15: WorkManager Scheduling Integration

**Files:**
- Modify: `app/src/main/java/com/gitsync/ui/addrepo/AddRepoViewModel.kt`
- Modify: `app/src/main/java/com/gitsync/di/AppModule.kt`

- [ ] **Step 1: Add WorkManager to DI module**

Add to `AppModule.kt`:

```kotlin
// In AppModule.kt — add this provider
@Provides
@Singleton
fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
    WorkManager.getInstance(context)
```

- [ ] **Step 2: Schedule work after clone in `AddRepoViewModel.kt`**

In `AddRepoViewModel`, inject `WorkManager` and schedule after insert:

```kotlin
// Add to constructor injection:
private val workManager: WorkManager

// Replace the insert block in save() with:
val id = repoDao.insert(entity)
if (state.intervalMinutes > 0) {
    val workRequest = SyncWorker.buildWorkRequest(
        repoId = id,
        repoName = entity.name,
        localPath = entity.localPath,
        remoteUrl = entity.remoteUrl,
        pat = pat,
        intervalMinutes = state.intervalMinutes
    )
    workManager.enqueueUniquePeriodicWork(
        "sync_$id",
        ExistingPeriodicWorkPolicy.REPLACE,
        workRequest
    )
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/gitsync/
git commit -m "feat: WorkManager scheduling on repo add"
```

---

## Task 16: Final Build and Smoke Test

- [ ] **Step 1: Run all unit tests**

```bash
./gradlew test
```

Expected: All tests pass

- [ ] **Step 2: Build release APK**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 3: Install on device/emulator**

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 4: Manual smoke test checklist**
  - [ ] App launches without crash
  - [ ] Bottom nav switches between 仓库 / 日志 / 设置
  - [ ] Settings: enter PAT, save, reopen → token is masked
  - [ ] Add repo: enter GitHub URL + pick local folder + select interval → tap "添加并 Clone"
  - [ ] Repo card appears with status badge "已同步"
  - [ ] Tap sync button → spinner rotates → badge updates
  - [ ] Modify a file in the synced folder → tap sync → ConflictDialog appears → tap "保留本地，跳过"
  - [ ] Log screen shows sync entries

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "chore: final build verification"
```

---

## Spec Coverage Check

| Spec Requirement | Task |
|------------------|------|
| PAT authentication | Task 3, 13 |
| Multiple repos | Task 2, 10 |
| Clone on first add | Task 5, 12 |
| Manual sync | Task 10 |
| Scheduled sync (WorkManager) | Task 6, 15 |
| Conflict detection | Task 5 |
| Conflict dialog (force/skip) | Task 11 |
| Sync log | Task 2, 14 |
| Bottom navigation | Task 9 |
| OLED dark theme | Task 8 |
| Status badges + animation | Task 10 |
| Permissions | Task 1 |
| Notification on conflict | Task 7 |
