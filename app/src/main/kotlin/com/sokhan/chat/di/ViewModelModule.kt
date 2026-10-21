package com.sokhan.chat.di

import com.sokhan.chat.ui.screens.assistants.AssistantEditorViewModel
import com.sokhan.chat.ui.screens.assistants.AssistantsViewModel
import com.sokhan.chat.ui.screens.chat.ChatViewModel
import com.sokhan.chat.ui.screens.chats.ChatsViewModel
import com.sokhan.chat.ui.screens.providers.ProviderEditorViewModel
import com.sokhan.chat.ui.screens.providers.ProvidersViewModel
import com.sokhan.chat.ui.screens.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * ViewModel definitions. Route-parameterized ones (chat / provider editor /
 * assistant editor) receive their id through `parametersOf(...)` at the
 * navigation call site.
 */
val viewModelModule = module {
    single { com.sokhan.chat.domain.service.AssistantChatStarter(get(), get()) }

    viewModel { (id: String?) ->
        ChatViewModel(
            conversationId = id.orEmpty(),
            conversations = get(),
            engine = get(),
            models = get(),
            providers = get(),
            attachments = get(),
            settings = get()
        )
    }

    viewModel { ChatsViewModel(get(), get(), get(), get()) }

    viewModel { ProvidersViewModel(get(), get()) }

    viewModel { (id: String?) ->
        ProviderEditorViewModel(
            providerId = id,
            providers = get(),
            models = get(),
            settings = get(),
            clientFactory = get()
        )
    }

    viewModel { AssistantsViewModel(get(), get()) }

    viewModel { (id: String?) ->
        AssistantEditorViewModel(
            assistantId = id,
            assistants = get(),
            providers = get(),
            models = get(),
            chatStarter = get()
        )
    }

    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
}
