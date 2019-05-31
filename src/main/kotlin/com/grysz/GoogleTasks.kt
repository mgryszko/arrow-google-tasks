package com.grysz

import arrow.Kind
import arrow.core.Either
import arrow.core.Id
import arrow.core.extensions.either.monadError.monadError
import arrow.core.extensions.id.monad.monad
import arrow.core.fix
import arrow.data.Kleisli
import arrow.typeclasses.Monad
import arrow.typeclasses.MonadError
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.TaskList
import com.google.api.services.tasks.model.TaskLists
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader

val jsonFactory = JacksonFactory.getDefaultInstance()

data class AuthConfig(val credentialsFilePath: String, val tokensDirectoryPath: String)
data class Config(val authConfig: AuthConfig, val applicationName: String)

class GoogleAuthentication<F>(ME: MonadError<F, Throwable>, val authConfig: AuthConfig): MonadError<F, Throwable> by ME {
    private val scopes = listOf(TasksScopes.TASKS_READONLY)

    fun httpTransport(): Kind<F, HttpTransport> = catch(GoogleNetHttpTransport::newTrustedTransport)

    fun serializedCredential(): Kind<F, InputStream> =
        just(GoogleAuthentication::class.java.getResourceAsStream(authConfig.credentialsFilePath))
            .flatMap { inputStream ->
                if (inputStream != null) {
                    just(inputStream)
                } else {
                    raiseError(FileNotFoundException("Resource not found: ${authConfig.credentialsFilePath}"))
                }
            }

    fun googleClientSecrets(inputStream: InputStream): Kind<F, GoogleClientSecrets> =
        catch { GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream))}

    fun fileDataStoreFactory(): Kind<F, FileDataStoreFactory> =
        catch { FileDataStoreFactory(File(authConfig.tokensDirectoryPath)) }

    fun flow(httpTransport: HttpTransport, clientSecrets: GoogleClientSecrets, fileDataStoreFactory: FileDataStoreFactory): Kind<F, GoogleAuthorizationCodeFlow> =
        catch(GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, scopes)
            .setDataStoreFactory(fileDataStoreFactory)
            .setAccessType("offline")::build
        )

    fun authorize(flow: GoogleAuthorizationCodeFlow): Kind<F, Credential> =
        catch {
            val receiver = LocalServerReceiver.Builder().setPort(8888).build()
            AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
        }
}


class GoogleCredential<F>(val authentication: GoogleAuthentication<F>, M: Monad<F>): Monad<F> by M {
    fun get(httpTransport: HttpTransport): Kind<F, Credential> {
        return binding {
            val (inputStream) = authentication.serializedCredential()
            val (clientSecrets) = authentication.googleClientSecrets(inputStream)
            val (flow) = googleAuthorizationCodeFlow(httpTransport, clientSecrets);
            val (credential) = authentication.authorize(flow)
            credential
        }
    }

    private fun googleAuthorizationCodeFlow(httpTransport: HttpTransport, clientSecrets: GoogleClientSecrets): Kind<F, GoogleAuthorizationCodeFlow> {
        return binding {
            val (fileDataStoreFactory) = authentication.fileDataStoreFactory()
            val (flow) = authentication.flow(httpTransport, clientSecrets, fileDataStoreFactory)
            flow
        }
    }
}

class GoogleTasksService<F>(MR: Monad<F>, val applicationName: String): Monad<F> by MR {
    fun create(httpTransport: HttpTransport, credential: Credential): Kind<F, Tasks> =
        just(Tasks.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(applicationName)
            .build())
}

class TaskLists<F>(
    val authentication: GoogleAuthentication<F>,
    val credential: GoogleCredential<F>,
    val tasksService: GoogleTasksService<F>,
    ME: MonadError<F, Throwable>
): MonadError<F, Throwable> by ME {
    fun execute(): Kind<F, List<TaskList>> {
        return binding {
            val (httpTransport) = authentication.httpTransport()
            val (credential) = credential.get(httpTransport)
            val (tasks) = tasksService.create(httpTransport, credential)
            val (taskLists) = taskLists(tasks)
            val (items) = if (taskLists.items.isNotEmpty()) just(taskLists.items) else raiseError(RuntimeException("No task lists found."))
            items
        }
    }

    private fun taskLists(tasks: Tasks): Kind<F, TaskLists> =
        catch(tasks.tasklists().list().setMaxResults(10L)::execute)
}

fun <F, G> authentication(MK: Monad<G>, ME: MonadError<F, Throwable>): Kleisli<G, Config, GoogleAuthentication<F>> {
    return Kleisli.ask<G, AuthConfig>(MK)
        .local(Config::authConfig).map(MK) { authConfig -> GoogleAuthentication(ME, authConfig) }
}

fun <F, G> tasksService(MK: Monad<G>, M: Monad<F>): Kleisli<G, Config, GoogleTasksService<F>> {
    return Kleisli.ask<G, String>(MK)
        .local(Config::applicationName).map(MK) { appName -> GoogleTasksService(M, appName) }
}

fun main() {
    val ME = Either.monadError<Throwable>() // or Try.monadError()
    val ID = Id.monad()

    val config = Config(
        AuthConfig(
            credentialsFilePath = "/credentials.json",
            tokensDirectoryPath = "tokens"
        ),
        applicationName = "Google Tasks API Java Quickstart"
    )

    val authentication = authentication(ID, ME).run(config)
    val tasksService = tasksService(ID, ME).run(config)
    ID.binding {
        val (dependencies) = authentication.product(tasksService)
        val (authentication, tasksService) = dependencies
        val useCase = TaskLists(
            authentication = authentication,
            credential = GoogleCredential(authentication, ME),
            tasksService = tasksService,
            ME = ME
        )
        ME.binding {
            val taskLists = useCase.execute().map(::format).fix()
            println(taskLists.fold({ t -> t.printStackTrace(); "Error: $t" }, { "Task lists:\n$it" }))
        }
    }
}


fun format(taskLists: List<TaskList>): String = taskLists.joinToString("\n") { "${it.title} (${it.id})" }


