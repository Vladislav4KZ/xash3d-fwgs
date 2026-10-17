/*
 * Resolve a directory selected with ACTION_OPEN_DOCUMENT_TREE to a real
 * filesystem path. The engine uses native filesystem calls, so content:// URIs
 * from virtual documents providers cannot be used as game directories.
 */
package su.xash.engine.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

object GameDataDirectory {
	private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

	fun resolve(context: Context, treeUri: Uri): File? {
		if (treeUri.scheme != "content" || treeUri.authority != EXTERNAL_STORAGE_AUTHORITY)
			return null

		return try {
			val documentId = DocumentsContract.getTreeDocumentId(treeUri)
			val separator = documentId.indexOf(':')
			if (separator <= 0)
				return null

			val volumeId = documentId.substring(0, separator)
			val relativePath = documentId.substring(separator + 1).trim('/')
			if (relativePath.isEmpty() || relativePath.split('/').any {
					it.isEmpty() || it == "." || it == ".."
				})
				return null

			val volumeRoot = findVolumeRoot(context, volumeId)?.canonicalFile ?: return null
			if (!volumeRoot.isDirectory)
				return null

			val directory = File(volumeRoot, relativePath).canonicalFile
			val rootPath = volumeRoot.path
			if (!directory.path.startsWith(rootPath + File.separator))
				return null

			directory.takeIf { it.isDirectory && it.canRead() }
		} catch (_: Exception) {
			null
		}
	}

	private fun findVolumeRoot(context: Context, volumeId: String): File? {
		if (volumeId.equals("primary", ignoreCase = true))
			return Environment.getExternalStorageDirectory()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			val manager = context.getSystemService(StorageManager::class.java) ?: return null
			val volume = manager.storageVolumes.firstOrNull {
				it.uuid?.equals(volumeId, ignoreCase = true) == true
			} ?: return null
			if (volume.state != Environment.MEDIA_MOUNTED)
				return null
			return volume.directory
		}

		// Before Android 11, removable volumes are exposed under /storage/<UUID>.
		return File("/storage", volumeId)
	}
}
