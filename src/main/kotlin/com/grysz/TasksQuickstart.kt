package com.grysz

import arrow.core.*
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.TaskLists
import java.io.*
import java.lang.RuntimeException

object TasksQuickstart {
    private val applicationName = "Google Tasks API Java Quickstart"
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val tokensDirectoryPath = "tokens"

    private val scopes = listOf(TasksScopes.TASKS_READONLY)
    private val credentialsFilePath = "/credentials.json"

    private fun getCredential(httpTransport: HttpTransport): Either<Throwable, Credential> {
        return serializedCredential()
            .flatMap(this::googleClientSecrets)
            .flatMap { clientSecrets -> googleAuthorizationCodeFlow(httpTransport, clientSecrets) }
            .flatMap { flow ->
                Try {
                    val receiver = LocalServerReceiver.Builder().setPort(8888).build()
                    AuthorizationCodeInstalledApp(flow, receiver).authorize("user")
                }.toEither()
            }
    }

    private fun serializedCredential(): Either<Throwable, InputStream> =
        TasksQuickstart::class.java.getResourceAsStream(credentialsFilePath).right()
            .leftIfNull { FileNotFoundException("Resource not found: $credentialsFilePath") }

    private fun googleClientSecrets(inputStream: InputStream): Either<Throwable, GoogleClientSecrets> =
        Try { GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream)) }.toEither()

    private fun httpTransport(): Either<Throwable, NetHttpTransport> =
        Try { GoogleNetHttpTransport.newTrustedTransport() }.toEither()

    private fun googleAuthorizationCodeFlow(httpTransport: HttpTransport, clientSecrets: GoogleClientSecrets): Either<Throwable, GoogleAuthorizationCodeFlow> =
         fileDataStoreFactory().flatMap { fileDataStoreFactory ->
             Try(GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, scopes)
                 .setDataStoreFactory(fileDataStoreFactory)
                 .setAccessType("offline")::build
             ).toEither()
         }

    private fun fileDataStoreFactory(): Either<Throwable, FileDataStoreFactory> =
        Try { FileDataStoreFactory(File(tokensDirectoryPath)) }.toEither()

    private fun tasksService(httpTransport: NetHttpTransport, credential: Credential): Tasks {
        return Tasks.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(applicationName)
            .build()
    }

    private fun taskLists(tasks: Tasks): Either<Throwable, TaskLists> =
        Try(tasks.tasklists().list().setMaxResults(10L)::execute).toEither()

    @JvmStatic
    fun main(args: Array<String>) {
        val tasksList = httpTransport()
            .flatMap { httpTransport -> getCredential(httpTransport)
                .map { credential -> tasksService(httpTransport, credential) }
            }
            .flatMap { service ->  taskLists(service).map { it.items } }
            .flatMap { t ->
                if (t.isEmpty()) RuntimeException("No task lists found.").left() else t.right()
            }.map { tasksList ->
                tasksList.joinToString("\n") { "${it.title} (${it.id})" }
            }.fold({ t -> "Error: $t" }, { tasksList -> "Task lists:\n$tasksList" } )

        println(tasksList)
    }

}
