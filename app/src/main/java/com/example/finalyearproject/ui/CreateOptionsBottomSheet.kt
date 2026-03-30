package com.example.finalyearproject.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.finalyearproject.R
import com.example.finalyearproject.ui.create.CreateRecipeActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * CreateOptionsBottomSheet
 *
 * Opened by the centre FAB.
 * Options:
 *   1. Create Recipe → launches CreateRecipeActivity
 *   2. Upload Video  → placeholder (shows Toast for now)
 *   3. Write Blog    → placeholder (shows Toast for now)
 */
class CreateOptionsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_create, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Option 1: Create Recipe ───────────────────────────────────────────
        view.findViewById<View>(R.id.btn_upload_recipe)?.setOnClickListener {
            dismiss()
            startActivity(Intent(requireContext(), CreateRecipeActivity::class.java))
        }

        // ── Option 2: Upload Video ────────────────────────────────────────────
        view.findViewById<View>(R.id.btn_upload_video)?.setOnClickListener {
            dismiss()
            Toast.makeText(requireContext(),
                "Video upload coming soon! 🎬", Toast.LENGTH_SHORT).show()
        }

        // ── Option 3: Write Blog ──────────────────────────────────────────────
        view.findViewById<View>(R.id.btn_write_blog)?.setOnClickListener {
            dismiss()
            Toast.makeText(requireContext(),
                "Blog writing coming soon! ✍️", Toast.LENGTH_SHORT).show()
        }
    }
}