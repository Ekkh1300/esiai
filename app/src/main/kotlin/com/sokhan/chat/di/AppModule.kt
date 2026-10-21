package com.sokhan.chat.di

import android.content.Context
import androidx.room.Room
import com.sokhan.chat.core.DefaultDispatcherProvider
import com.sokhan.chat.core.DispatcherProvider
import com.sokhan.chat.core.ConnectivityNetworkMonitor
import com.sokhan.chat.core.NetworkMonitor
import com.sokhan.chat.data.datastore.SettingsStore
import com.sokhan.chat.data.db.SokhanDatabase
import com.sokhan.chat.data.db.dao.AssistantDao
import com.sokhan.chat.data.db.dao.AttachmentDao
import com.sokhan.chat.data.db.dao.ConversationDao
import com.sokhan.chat.data.db.dao.MessageDao
import com.sokhan.chat.data.db.dao.ModelDao
import com.sokhan.chat.data.db.dao.ProviderDao
import com.sokhan.chat.data.export.BackupCodec
import com.sokhan.chat.data.export.BackupService
import com.sokhan.chat.data.repo.AssistantRepositoryImpl
import com.sokhan.chat.data.repo.AttachmentRepositoryImpl
import com.sokhan.chat.data.repo.ConversationRepositoryImpl
import com.sokhan.chat.data.repo.ModelRepositoryImpl
import com.sokhan.chat.data.repo.ProviderRepositoryImpl
import com.sokhan.chat.domain.repository.AssistantRepository
import com.sokhan.chat.domain.repository.AttachmentRepository
import com.sokhan.chat.domain.repository.ChatEngine
import com.sokhan.chat.domain.repository.ConversationRepository
import com.sokhan.chat.domain.repository.ModelRepository
import com.sokhan.chat.domain.repository.ProviderRepository
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.domain.service.StreamingChatEngine
import com.sokhan.chat.provider.DefaultProviderClientFactory
import com.sokhan.chat.provider.ProviderClientFactory
import com.sokhan.chat.security.KeystoreSecureStore
import com.sokhan.chat.security.SecureStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private fun buildOkHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .pingInterval(30, TimeUnit.SECONDS)
    .build()

private fun buildDatabase(context: Context): SokhanDatabase =
    Room.databaseBuilder(context, SokhanDatabase::class.java, "sokhan.db")
        .build()

/**
 * Constructor-injected singletons. ViewModels are declared separately in
 * `viewModelModule` (see SokhanApplication.kt) with parameter injection.
 */
val appModule = module {

    single<CoroutineScope>(qualifier = named("appScope")) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    single<DispatcherProvider> { DefaultDispatcherProvider() }

    single { buildOkHttp() }

    single<SecureStore> { KeystoreSecureStore(androidContext()) }

    single { buildDatabase(androidContext()) }
    single<ConversationDao> { get<SokhanDatabase>().conversationDao() }
    single<MessageDao> { get<SokhanDatabase>().messageDao() }
    single<ProviderDao> { get<SokhanDatabase>().providerDao() }
    single<ModelDao> { get<SokhanDatabase>().modelDao() }
    single<AssistantDao> { get<SokhanDatabase>().assistantDao() }
    single<AttachmentDao> { get<SokhanDatabase>().attachmentDao() }

    single<SettingsRepository> { SettingsStore(androidContext(), get(qualifier = named("appScope"))) }

    single<ConversationRepository> {
        ConversationRepositoryImpl(
            db = get(),
            conversationDao = get(),
            messageDao = get(),
            attachmentDao = get()
        )
    }

    single<ProviderRepository> {
        ProviderRepositoryImpl(
            dao = get(),
            modelDao = get(),
            secureStore = get(),
            settings = get()
        )
    }

    single<ModelRepository> { ModelRepositoryImpl(get()) }

    single<AttachmentRepository> { AttachmentRepositoryImpl(androidContext(), get()) }

    single<AssistantRepository> { AssistantRepositoryImpl(get()) }

    single { BackupCodec() }
    single { BackupService(get(), get(), get(), get()) }

    single<ProviderClientFactory> { DefaultProviderClientFactory(get()) }

    single<NetworkMonitor> { ConnectivityNetworkMonitor(androidContext()) }

    single<ChatEngine> {
        StreamingChatEngine(
            conversations = get(),
            providers = get(),
            models = get(),
            attachments = get(),
            settings = get(),
            clientFactory = get(),
            appScope = get(qualifier = named("appScope")),
        )
    }
}
