package com.grysz

import arrow.Kind
import arrow.core.*
import arrow.core.extensions.either.monad.monad
import arrow.typeclasses.Monad
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader

val credentialsFilePath = "/credentials.json"
val jsonFactory = JacksonFactory.getDefaultInstance()

interface GoogleTasks<out F> {
    fun httpTransport(): Kind<F, HttpTransport>
    fun serializedCredential(): Kind<F, InputStream>
    fun googleClientSecrets(inputStream: InputStream): Kind<F, GoogleClientSecrets>
}

class SafeGoogleTasks: GoogleTasks<EitherPartialOf<Throwable>> {
    override fun httpTransport(): Either<Throwable, HttpTransport> =
        Try { GoogleNetHttpTransport.newTrustedTransport() }.toEither()

    override fun serializedCredential(): Either<Throwable, InputStream> =
        SafeGoogleTasks::class.java.getResourceAsStream(credentialsFilePath).right()
            .leftIfNull { FileNotFoundException("Resource not found: $credentialsFilePath") }

    override fun googleClientSecrets(inputStream: InputStream): Either<Throwable, GoogleClientSecrets> =
        Try { GoogleClientSecrets.load(jsonFactory, InputStreamReader(inputStream)) }.toEither()
}

class GoogleTasksProgram<F>(val GT: GoogleTasks<F>) {
    fun Monad<F>.persistedSecrets(): Kind<F, GoogleClientSecrets> {
        // TODO Sequence operations with `binding`
        return GT.serializedCredential().flatMap { inputStream ->
            GT.googleClientSecrets(inputStream)
        }
    }
}

fun main() {
    Either.monad<Throwable>().run {
        GoogleTasksProgram(SafeGoogleTasks()).run {
            println(persistedSecrets())
        }
    }
}

