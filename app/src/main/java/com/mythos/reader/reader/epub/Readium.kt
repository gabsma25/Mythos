package com.mythos.reader.reader.epub

import android.content.Context
import java.io.File
import java.io.IOException
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Componentes do Readium para abrir publicações locais. */
class Readium(context: Context) {

    // O Readium exige um cliente HTTP, mas o app não declara a permissão INTERNET:
    // ele só seria usado para recursos remotos referenciados dentro de um EPUB.
    private val httpClient = DefaultHttpClient()

    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    suspend fun open(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false))
            .getOrElse { return Result.failure(IOException(it.message)) }

        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return Result.failure(IOException(it.message))
            }

        return Result.success(publication)
    }
}
