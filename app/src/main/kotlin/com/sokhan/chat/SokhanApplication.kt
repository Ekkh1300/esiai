package com.sokhan.chat

import android.app.Application
import com.sokhan.chat.core.AppLog
import com.sokhan.chat.di.appModule
import com.sokhan.chat.di.viewModelModule
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.AssistantSeed
import com.sokhan.chat.domain.repository.ChatEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application bootstrap: starts Koin and kicks off the first-run assistant
 * seeding plus the orphan stream sweep (leftover STREAMING rows from a
 * previous process are finalized as partial replies).
 *
 * Bundled assistants are seeded with the *system* locale on first run — at
 * that point the user has not chosen an in-app language yet. They remain
 * fully editable afterwards.
 */
class SokhanApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val assistantRepository: AssistantRepository by inject()
    private val chatEngine: ChatEngine by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SokhanApplication)
            modules(appModule, viewModelModule)
        }

        appScope.launch(Dispatchers.IO) {
            runCatching { chatEngine.recoverOrphans() }
                .onFailure { AppLog.w(TAG, "orphan sweep failed", it) }
        }

        appScope.launch(Dispatchers.IO) {
            runCatching { assistantRepository.seedIfEmpty(seed()) }
                .onFailure { AppLog.w(TAG, "assistant seeding failed", it) }
        }
    }

    private fun seed() = AssistantSeed(
        generalName = getString(R.string.assistant_bundled_general),
        generalPrompt = getString(R.string.assistant_bundled_general_prompt),
        coderName = getString(R.string.assistant_bundled_coder),
        coderPrompt = getString(R.string.assistant_bundled_coder_prompt),
        translatorName = getString(R.string.assistant_bundled_translator),
        translatorPrompt = getString(R.string.assistant_bundled_translator_prompt),
        researcherName = getString(R.string.assistant_bundled_researcher),
        researcherPrompt = getString(R.string.assistant_bundled_researcher_prompt)
    )

    private companion object {
        const val TAG = "App"
    }
}
