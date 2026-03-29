package com.example.finalyearproject.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.finalyearproject.databinding.FragmentProfileBinding
import com.example.finalyearproject.ui.MainActivity
import com.example.finalyearproject.utils.LanguageManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db   = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadProfile()
        setupClicks()
    }

    // ── Profile data ──────────────────────────────────────────────────────────

    private fun loadProfile() {
        val user = auth.currentUser ?: return

        // Display name + email from Auth
        binding.tvProfileName.text  = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@") ?: "User"
        binding.tvProfileEmail.text = user.email ?: ""

        // Firestore stats
        db.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { doc ->
                if (!isAdded) return@addOnSuccessListener
                binding.tvRecipeCount.text   = (doc.getLong("recipeCount")   ?: 0).toString()
                binding.tvFollowerCount.text = (doc.getLong("followerCount") ?: 0).toString()
                binding.tvFollowingCount.text = (doc.getLong("followingCount") ?: 0).toString()
            }
    }

    // ── Clicks ────────────────────────────────────────────────────────────────

    private fun setupClicks() {
        binding.rowSettings.setOnClickListener {
            showSettingsDialog()
        }

        // ── LOGOUT BUTTON ────────────────────────────────────────────────────
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    (activity as? MainActivity)?.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val currentLang = LanguageManager.getSavedLanguage(requireContext())
        val isVietnamese = currentLang == LanguageManager.LANG_VI

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Settings")
            .setItems(
                arrayOf(
                    if (isVietnamese) "🌐 Switch to English" else "🌐 Switch to Vietnamese",
                    "🔔 Notifications",
                    "🛡️ Privacy"
                )
            ) { _, which ->
                when (which) {
                    0 -> toggleLanguage()
                }
            }
            .show()
    }

    private fun toggleLanguage() {
        val current = LanguageManager.getSavedLanguage(requireContext())
        val next    = LanguageManager.toggle(current)
        LanguageManager.setLanguage(requireActivity(), next)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}