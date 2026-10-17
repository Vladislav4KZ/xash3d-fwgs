package su.xash.engine.ui.settings

import android.content.Context
import android.os.Bundle
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import su.xash.engine.BuildConfig
import su.xash.engine.MainActivity
import su.xash.engine.R
import su.xash.engine.model.Game
import su.xash.engine.util.GameDataDirectory
import java.io.File

class AppSettingsPreferenceFragment() : PreferenceFragmentCompat() {
	private val appPreferences
		get() = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)

	private val gameDirectoryPicker = registerForActivityResult(
		ActivityResultContracts.OpenDocumentTree()
	) { uri ->
		if (uri == null)
			return@registerForActivityResult

		val directory = GameDataDirectory.resolve(requireContext(), uri)
		if (directory == null) {
			showGameDirectoryError(R.string.game_data_location_unavailable_message)
			return@registerForActivityResult
		}

		// The library lists game directories below the selected root. Do not
		// accept a single game's directory as the root itself.
		if (Game.checkIfGamedir(directory) != null) {
			showGameDirectoryError(R.string.game_data_location_select_parent)
			return@registerForActivityResult
		}

		appPreferences.edit()
			.putString("game_path", directory.absolutePath)
			.apply()
		findPreference<Preference>("game_path")?.summary = directory.absolutePath
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		preferenceManager.sharedPreferencesName = "app_preferences";
		setPreferencesFromResource(R.xml.app_preferences, rootKey);

		findPreference<Preference>("game_path")?.apply {
			summary = appPreferences.getString("game_path", null)
				?: File(Environment.getExternalStorageDirectory(), "xash").absolutePath
			setOnPreferenceClickListener {
				gameDirectoryPicker.launch(null)
				true
			}
		}

		findPreference<Preference>("crash_logs")?.setOnPreferenceClickListener {
			findNavController().navigate(R.id.action_appSettingsFragment_to_crashLogsFragment)
			true
		}

		findPreference<Preference>("check_updates")?.apply {
			// Auto-update is only compiled into the continuous build; hide the
			// manual check elsewhere, since the updater is a no-op there.
			isVisible = BuildConfig.ENABLE_AUTO_UPDATE
			setOnPreferenceClickListener {
				(requireActivity() as MainActivity).checkForUpdatesManually()
				true
			}
		}
	}

	private fun showGameDirectoryError(messageRes: Int) {
		com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
			.setTitle(R.string.game_data_location)
			.setMessage(messageRes)
			.setPositiveButton(android.R.string.ok, null)
			.show()
	}
}
