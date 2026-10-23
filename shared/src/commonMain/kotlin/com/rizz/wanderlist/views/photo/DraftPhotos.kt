package com.rizz.wanderlist.views.photo

import com.rizz.wanderlist.domain.model.MAX_PHOTOS_PER_SAVE
import com.rizz.wanderlist.domain.repository.PhotoCompressor
import com.rizz.wanderlist.util.pluralize
import com.rizz.wanderlist.util.resultOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The picking half of a photo form, shared by Add Place and Place detail: turns what the photo
 * picker returned into draft photos, compresses them, and deletes compressed files nobody needs.
 *
 * The form keeps the photos in its own state; [updatePhotos] changes them there (it may be a
 * no-op when the form has closed meanwhile). [showMessage] tells the user what went wrong.
 */
internal class DraftPhotos(
    private val compressor: PhotoCompressor,
    private val updatePhotos: (transform: (List<DraftPhoto>) -> List<DraftPhoto>) -> Unit,
    private val showMessage: (String) -> Unit
) {

    /** A picked photo waiting to be compressed: its draft (shown with a spinner) and the picker's source. */
    class Pending internal constructor(internal val photo: DraftPhoto, internal val source: String)

    /**
     * Adds up to [room] of the picked [sources] to the form straight away, each with a spinner.
     * Returns them for [compress], which the form runs in its own (cancellable) work.
     */
    fun accept(sources: List<String>, room: Int): List<Pending> {
        if (sources.isEmpty()) return emptyList()
        val picked = sources.distinct()
        val accepted = picked.take(room.coerceAtLeast(0)).map { Pending(DraftPhoto(id = newPhotoId()), it) }
        if (accepted.isNotEmpty()) updatePhotos { photos -> photos + accepted.map { it.photo } }
        if (accepted.size < picked.size) {
            showMessage("Added ${accepted.size} of ${picked.size} photos: you can add up to $MAX_PHOTOS_PER_SAVE at a time.")
        }
        return accepted
    }

    /**
     * Compresses [pending] photos one at a time, to keep memory low. Each one gets its file when
     * done; one that can't be opened is removed from the form, and the user is told how many.
     */
    suspend fun compress(pending: List<Pending>) {
        var failed = 0
        pending.forEach { item ->
            resultOf { compressor.compress(item.source) }.fold(
                onSuccess = { path -> attach(item.photo.id, path) },
                onFailure = {
                    failed++
                    updatePhotos { photos -> photos.filterNot { it.id == item.photo.id } }
                }
            )
        }
        if (failed > 0) showMessage("${pluralize(failed, "photo")} couldn't be opened.")
    }

    /** Deletes the compressed files of [photos] (photos already on a place have none). */
    fun discard(photos: List<DraftPhoto>) {
        photos.forEach { photo -> photo.localPath?.let(compressor::discard) }
    }

    /** Puts a compressed file on its draft photo, or deletes it if the photo was removed meanwhile. */
    private fun attach(photoId: String, path: String) {
        var attached = false
        updatePhotos { photos ->
            // Set on every attempt: the last one is what was actually stored.
            attached = photos.any { it.id == photoId }
            photos.map { if (it.id == photoId) it.copy(localPath = path) else it }
        }
        if (!attached) compressor.discard(path)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newPhotoId(): String = Uuid.random().toHexString()
}
