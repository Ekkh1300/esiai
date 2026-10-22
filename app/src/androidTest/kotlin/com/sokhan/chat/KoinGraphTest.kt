package com.sokhan.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sokhan.chat.core.NetworkMonitor
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.AttachmentRepository
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.provider.ProviderClientFactory
import com.sokhan.chat.ui.screens.assistants.AssistantEditorViewModel
import com.sokhan.chat.ui.screens.assistants.AssistantsViewModel
import com.sokhan.chat.ui.screens.chat.ChatViewModel
import com.sokhan.chat.ui.screens.chats.ChatsViewModel
import com.sokhan.chat.ui.screens.providers.ProviderEditorViewModel
import com.sokhan.chat.ui.screens.providers.ProvidersViewModel
import com.sokhan.chat.ui.screens.settings.SettingsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf

/**
 * Dependency-injection graph regression test.
 *
 * A missing Koin definition is a *runtime* failure: the app starts, the first
 * screen resolves its ViewModel and the process dies with
 * `NoDefinitionFoundException`. Unit tests never saw it because they use
 * fakes, so this test walks the real graph (the application class already
 * started Koin) and forces every definition to be instantiated.
 */
@RunWith(AndroidJUnit4::class)
class KoinGraphTest {

    private val koin get() = GlobalContext.get()

    @Test
    fun startupSingletonsResolve() {
        assertNotNull(koin.get<ConversationRepository>())
        assertNotNull(koin.get<ProviderRepository>())
        assertNotNull(koin.get<ModelRepository>())
        assertNotNull(koin.get<AssistantRepository>())
        assertNotNull(koin.get<AttachmentRepository>())
        assertNotNull(koin.get<SettingsRepository>())
        assertNotNull(koin.get<ChatEngine>())
        assertNotNull(koin.get<ProviderClientFactory>())
        assertNotNull(koin.get<NetworkMonitor>())
    }

    @Test
    fun viewModelsResolve() {
        assertNotNull(koin.get<ChatsViewModel>())
        assertNotNull(koin.get<AssistantsViewModel>())
        assertNotNull(koin.get<ProvidersViewModel>())
        assertNotNull(koin.get<SettingsViewModel>())
    }

    @Test
    fun parameterisedViewModelsResolve() {
        assertNotNull(koin.get<ChatViewModel>(parameters = { parametersOf("") }))
        assertNotNull(koin.get<ProviderEditorViewModel>(parameters = { parametersOf("") }))
        assertNotNull(koin.get<AssistantEditorViewModel>(parameters = { parametersOf("") }))
    }
}
